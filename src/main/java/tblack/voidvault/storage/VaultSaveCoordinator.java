package tblack.voidvault.storage;

import tblack.voidvault.model.VaultKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VaultSaveCoordinator {
    private static final String THREAD_NAME = "VoidVault-SaveWorker";
    private static final long RETRY_DELAY_MILLIS = 1000;

    private final DatabaseService database;
    private final ScheduledExecutorService executor;
    private final Map<VaultKey, PendingSave> pendingSaves = new HashMap<>();
    private final Map<VaultKey, Long> revisions = new HashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    private volatile long debounceMillis = 750;
    private volatile long maxDelayMillis = 5000;

    public VaultSaveCoordinator(DatabaseService database) {
        this.database = database;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void updateTimings(long debounceMillis, long maxDelayMillis) {
        this.debounceMillis = Math.max(100, Math.min(30_000, debounceMillis));
        this.maxDelayMillis = Math.max(this.debounceMillis, Math.min(30_000, maxDelayMillis));
    }

    public synchronized void markDirty(VaultKey key, String payload) {
        if (!accepting.get() || key == null || payload == null) return;

        long now = System.currentTimeMillis();
        PendingSave previous = pendingSaves.get(key);
        long firstDirtyAt = previous == null ? now : previous.firstDirtyAt();
        long revision = nextRevision(key);
        cancel(previous);

        long elapsed = now - firstDirtyAt;
        long delay = elapsed >= maxDelayMillis
                ? 0
                : Math.min(debounceMillis, maxDelayMillis - elapsed);
        ScheduledFuture<?> future = executor.schedule(
                () -> flushScheduled(key, revision),
                delay,
                TimeUnit.MILLISECONDS
        );
        pendingSaves.put(key, new PendingSave(payload, revision, firstDirtyAt, future));
    }

    public boolean saveNow(VaultKey key, String payload) {
        if (key == null || payload == null) return false;

        ImmediateSave save;
        synchronized (this) {
            save = prepareImmediateSave(key, payload);
        }
        return executeImmediateSave(save);
    }

    public void flushKey(VaultKey key) {
        if (key == null) return;

        ImmediateSave save;
        synchronized (this) {
            PendingSave pending = pendingSaves.get(key);
            if (pending == null) return;
            save = prepareImmediateSave(key, pending.payload());
        }
        executeImmediateSave(save);
    }

    public void flushAll() {
        flushAllInternal();
    }

    public synchronized boolean hasPending(VaultKey key) {
        return pendingSaves.containsKey(key);
    }

    public void shutdown() {
        if (!accepting.compareAndSet(true, false)) return;

        flushAllInternal();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void flushAllInternal() {
        List<VaultKey> keys;
        synchronized (this) {
            keys = new ArrayList<>(pendingSaves.keySet());
        }
        for (VaultKey key : keys) {
            flushKey(key);
        }
    }

    private void flushScheduled(VaultKey key, long expectedRevision) {
        PendingSave save;
        synchronized (this) {
            save = pendingSaves.get(key);
            if (save == null || save.revision() != expectedRevision || currentRevision(key) != expectedRevision) {
                return;
            }
            pendingSaves.remove(key);
        }

        if (writePayload(key, save.payload())) return;
        retryIfCurrent(key, save.payload(), expectedRevision);
    }

    private ImmediateSave prepareImmediateSave(VaultKey key, String payload) {
        PendingSave previous = pendingSaves.remove(key);
        cancel(previous);
        long revision = nextRevision(key);
        return new ImmediateSave(key, payload, revision);
    }

    private boolean executeImmediateSave(ImmediateSave save) {
        Future<Boolean> write = executor.submit(() -> writePayload(save.key(), save.payload()));
        boolean successful;
        try {
            successful = write.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            successful = false;
        } catch (ExecutionException exception) {
            System.err.println("[VoidVault] Save worker failed for vault "
                    + save.key().vaultId() + " of " + save.key().ownerUuid() + ": " + exception.getCause());
            successful = false;
        }

        if (!successful) {
            retryIfCurrent(save.key(), save.payload(), save.revision());
        }
        return successful;
    }

    private synchronized void retryIfCurrent(VaultKey key, String payload, long failedRevision) {
        if (!accepting.get() || currentRevision(key) != failedRevision || pendingSaves.containsKey(key)) return;

        long revision = nextRevision(key);
        long now = System.currentTimeMillis();
        ScheduledFuture<?> future = executor.schedule(
                () -> flushScheduled(key, revision),
                RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
        );
        pendingSaves.put(key, new PendingSave(payload, revision, now, future));
    }

    private boolean writePayload(VaultKey key, String payload) {
        try {
            if (!database.isConnected()) return false;
            database.saveInventory(key.ownerUuid(), key.vaultId(), payload);
            return true;
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to save vault " + key.vaultId()
                    + " for " + key.ownerUuid() + ": " + exception.getMessage());
            return false;
        }
    }

    private void cancel(PendingSave save) {
        if (save != null && save.future() != null) {
            save.future().cancel(false);
        }
    }

    private long nextRevision(VaultKey key) {
        long revision = revisions.getOrDefault(key, 0L) + 1L;
        revisions.put(key, revision);
        return revision;
    }

    private long currentRevision(VaultKey key) {
        return revisions.getOrDefault(key, 0L);
    }

    private record PendingSave(
            String payload,
            long revision,
            long firstDirtyAt,
            ScheduledFuture<?> future
    ) {
    }

    private record ImmediateSave(VaultKey key, String payload, long revision) {
    }
}

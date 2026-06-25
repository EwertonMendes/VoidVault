package tblack.voidvault.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.VaultKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VaultSaveCoordinatorTest {

    private DatabaseService database;
    private VaultSaveCoordinator coordinator;
    private Path tempDbPath;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        database = new DatabaseService();
        tempDbPath = Files.createTempFile("voidvault-coordinator-test-", ".db");
        Files.delete(tempDbPath);

        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "sqlite";
        config.database.file = tempDbPath.toString();
        database.connect(config);

        coordinator = new VaultSaveCoordinator(database);
        coordinator.updateTimings(100, 500);
    }

    @AfterEach
    void tearDown() {
        coordinator.shutdown();
        database.close();
        try {
            Files.deleteIfExists(tempDbPath);
            Files.deleteIfExists(Path.of(tempDbPath + "-wal"));
            Files.deleteIfExists(Path.of(tempDbPath + "-shm"));
        } catch (IOException ignored) {
        }
    }

    @Test
    void markDirtySchedulesSave() {
        VaultKey key = new VaultKey(UUID.randomUUID(), 1);
        coordinator.markDirty(key, "{}");
        assertTrue(coordinator.hasPending(key));
    }

    @Test
    void flushKeySavesImmediately() throws SQLException {
        UUID uuid = UUID.randomUUID();
        VaultKey key = new VaultKey(uuid, 1);
        coordinator.markDirty(key, "{\"0\":{\"id\":\"A\",\"amount\":1}}");
        coordinator.flushKey(key);

        assertFalse(coordinator.hasPending(key));
        String saved = database.getInventory(uuid, 1);
        assertNotNull(saved);
        assertTrue(saved.contains("\"id\""));
    }

    @Test
    void flushAllSavesAllPending() throws SQLException {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        VaultKey key1 = new VaultKey(uuid1, 1);
        VaultKey key2 = new VaultKey(uuid2, 1);

        coordinator.markDirty(key1, "{}");
        coordinator.markDirty(key2, "{}");
        coordinator.flushAll();

        assertFalse(coordinator.hasPending(key1));
        assertFalse(coordinator.hasPending(key2));
        assertNotNull(database.getInventory(uuid1, 1));
        assertNotNull(database.getInventory(uuid2, 1));
    }

    @Test
    void flushAllOnShutdown() throws SQLException {
        UUID uuid = UUID.randomUUID();
        VaultKey key = new VaultKey(uuid, 1);
        coordinator.markDirty(key, "{\"0\":{\"id\":\"A\",\"amount\":1}}");

        coordinator.shutdown();
        assertFalse(coordinator.hasPending(key));

        String saved = database.getInventory(uuid, 1);
        assertNotNull(saved);
    }

    @Test
    void shutdownIdempotent() {
        coordinator.shutdown();
        coordinator.shutdown();
    }

    @Test
    void markDirtyAfterShutdownIgnored() {
        coordinator.shutdown();
        VaultKey key = new VaultKey(UUID.randomUUID(), 1);
        coordinator.markDirty(key, "{}");
        assertFalse(coordinator.hasPending(key));
    }

    @Test
    void markDirtyNullKeyIgnored() {
        coordinator.markDirty(null, "{}");
    }

    @Test
    void markDirtyNullPayloadIgnored() {
        VaultKey key = new VaultKey(UUID.randomUUID(), 1);
        coordinator.markDirty(key, null);
        assertFalse(coordinator.hasPending(key));
    }

    @Test
    void debounceResetsTimer() throws InterruptedException {
        VaultKey key = new VaultKey(UUID.randomUUID(), 1);
        coordinator.markDirty(key, "{}");
        Thread.sleep(50);
        coordinator.markDirty(key, "{\"updated\":true}");
        assertTrue(coordinator.hasPending(key));
    }

    @Test
    void updateTimingsRespectsMinimums() {
        coordinator.updateTimings(10, 50);
        VaultKey key = new VaultKey(UUID.randomUUID(), 1);
        coordinator.markDirty(key, "{}");
        assertTrue(coordinator.hasPending(key));
    }

    @Test
    void flushKeyNonexistentNoError() {
        coordinator.flushKey(new VaultKey(UUID.randomUUID(), 999));
    }

    @Test
    void concurrentMarkDirty() throws InterruptedException, SQLException {
        UUID uuid = UUID.randomUUID();
        VaultKey key = new VaultKey(uuid, 1);
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int iteration = i;
            new Thread(() -> {
                try {
                    coordinator.markDirty(key, "{\"iteration\":" + iteration + "}");
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);
        coordinator.flushKey(key);

        String saved = database.getInventory(uuid, 1);
        assertNotNull(saved);
    }
}

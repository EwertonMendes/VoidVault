package tblack.voidvault.model;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class LoadedVault {
    private final VaultKey key;
    private final ItemContainer container;
    private final Map<Integer, SavedItem> overflow;
    private final AtomicLong revision = new AtomicLong(0);

    public LoadedVault(VaultKey key, ItemContainer container, Map<Integer, SavedItem> overflow) {
        this.key = key;
        this.container = container;
        this.overflow = overflow;
    }

    public VaultKey key() {
        return key;
    }

    public ItemContainer container() {
        return container;
    }

    public Map<Integer, SavedItem> overflow() {
        return overflow;
    }

    public UUID ownerUuid() {
        return key.ownerUuid();
    }

    public int vaultId() {
        return key.vaultId();
    }

    public long currentRevision() {
        return revision.get();
    }

    public long nextRevision() {
        return revision.incrementAndGet();
    }

    public long snapshotRevision() {
        return revision.get();
    }
}

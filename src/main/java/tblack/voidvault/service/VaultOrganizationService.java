package tblack.voidvault.service;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import tblack.voidvault.model.SortResult;
import tblack.voidvault.storage.DatabaseService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class VaultOrganizationService {
    @SuppressWarnings("unused")
    private final DatabaseService database;

    public VaultOrganizationService(DatabaseService database) {
        this.database = database;
    }

    public SortResult sortVisibleSlots(UUID uuid, int vaultId, ItemContainer container, int requestedCapacity) {
        if (uuid == null || vaultId < 1 || container == null || requestedCapacity <= 0) {
            return SortResult.empty();
        }

        int capacity = Math.min(requestedCapacity, container.getCapacity());
        List<ItemStack> snapshot = snapshot(container, capacity);
        List<SlotEntry> visibleItems = collectItems(snapshot);
        if (visibleItems.isEmpty() || isAlreadySortedAndCompact(visibleItems)) {
            return SortResult.empty();
        }

        visibleItems.sort(Comparator
                .comparing((SlotEntry entry) -> entry.stack(), this::compareItems)
                .thenComparingInt(SlotEntry::originalSlot));

        try {
            for (int slot = 0; slot < capacity; slot++) {
                setAndVerify(container, slot, null);
            }
            for (int index = 0; index < visibleItems.size(); index++) {
                setAndVerify(container, index, visibleItems.get(index).stack());
            }
            return new SortResult(visibleItems.size(), true);
        } catch (RuntimeException exception) {
            boolean restored = restoreSafely(container, snapshot);
            System.err.println("[VoidVault] Sort failed for vault " + vaultId + " of " + uuid
                    + "; rollback " + (restored ? "completed" : "was incomplete") + ": " + exception.getMessage());
            return SortResult.empty();
        }
    }

    private List<ItemStack> snapshot(ItemContainer container, int capacity) {
        List<ItemStack> snapshot = new ArrayList<>(capacity);
        for (int slot = 0; slot < capacity; slot++) {
            snapshot.add(container.getItemStack((short) slot));
        }
        return snapshot;
    }

    private List<SlotEntry> collectItems(List<ItemStack> snapshot) {
        List<SlotEntry> entries = new ArrayList<>();
        for (int slot = 0; slot < snapshot.size(); slot++) {
            ItemStack stack = snapshot.get(slot);
            if (stack != null && !stack.isEmpty()) entries.add(new SlotEntry(slot, stack));
        }
        return entries;
    }

    private boolean isAlreadySortedAndCompact(List<SlotEntry> items) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).originalSlot() != index) return false;
            if (index > 0 && compareItems(items.get(index - 1).stack(), items.get(index).stack()) > 0) return false;
        }
        return true;
    }

    private int compareItems(ItemStack first, ItemStack second) {
        int comparison = normalizeId(first.getItemId()).compareTo(normalizeId(second.getItemId()));
        if (comparison != 0) return comparison;

        String firstMetadata = first.getMetadata() == null ? "" : first.getMetadata().toString();
        String secondMetadata = second.getMetadata() == null ? "" : second.getMetadata().toString();
        comparison = firstMetadata.compareTo(secondMetadata);
        if (comparison != 0) return comparison;

        comparison = Double.compare(first.getDurability(), second.getDurability());
        if (comparison != 0) return comparison;
        return Integer.compare(second.getQuantity(), first.getQuantity());
    }

    private String normalizeId(String itemId) {
        return itemId == null ? "" : itemId.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean restoreSafely(ItemContainer container, List<ItemStack> snapshot) {
        try {
            restore(container, snapshot);
            return true;
        } catch (RuntimeException rollbackException) {
            System.err.println("[VoidVault] Failed to restore a vault after sort rollback: "
                    + rollbackException.getMessage());
            return false;
        }
    }

    private void restore(ItemContainer container, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            setAndVerify(container, slot, snapshot.get(slot));
        }
    }

    private void setAndVerify(ItemContainer container, int slot, ItemStack expected) {
        container.setItemStackForSlot((short) slot, expected);
        ItemStack actual = container.getItemStack((short) slot);
        if (sameStackState(expected, actual)) return;
        throw new IllegalStateException("Inventory rejected the item update for slot " + slot);
    }

    private boolean sameStackState(ItemStack expected, ItemStack actual) {
        boolean expectedEmpty = expected == null || expected.isEmpty();
        boolean actualEmpty = actual == null || actual.isEmpty();
        if (expectedEmpty) return actualEmpty;
        if (actualEmpty || expected.getQuantity() != actual.getQuantity()) return false;
        return expected.isStackableWith(actual);
    }

    private record SlotEntry(int originalSlot, ItemStack stack) {
    }
}

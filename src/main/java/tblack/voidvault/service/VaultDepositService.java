package tblack.voidvault.service;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import tblack.voidvault.model.DepositMatchingResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class VaultDepositService {

    public DepositMatchingResult depositSimilar(
            Player player,
            ItemContainer vaultContainer,
            int requestedCapacity,
            boolean includeHotbar
    ) {
        if (player == null || vaultContainer == null || requestedCapacity <= 0) {
            return DepositMatchingResult.empty();
        }

        int capacity = Math.min(requestedCapacity, vaultContainer.getCapacity());
        List<ItemStack> references = collectReferenceStacks(vaultContainer, capacity);
        if (references.isEmpty()) return DepositMatchingResult.noSimilar();

        Inventory inventory = getInventory(player);
        if (inventory == null) return DepositMatchingResult.empty();

        List<SourceContainer> sources = collectSourceContainers(inventory, vaultContainer, includeHotbar);
        if (sources.isEmpty()) return DepositMatchingResult.noSimilar();

        List<ItemStack> vaultSnapshot = snapshot(vaultContainer);
        List<ContainerSnapshot> sourceSnapshots = sources.stream()
                .map(source -> new ContainerSnapshot(source.container(), snapshot(source.container())))
                .toList();

        try {
            return transferMatching(references, sources, vaultContainer, capacity);
        } catch (RuntimeException exception) {
            boolean restored = restoreSafely(vaultContainer, vaultSnapshot);
            for (ContainerSnapshot snapshot : sourceSnapshots) {
                restored &= restoreSafely(snapshot.container(), snapshot.items());
            }
            System.err.println("[VoidVault] Deposit similar failed and rollback "
                    + (restored ? "completed" : "was incomplete") + ": " + exception.getMessage());
            return DepositMatchingResult.empty();
        }
    }

    private DepositMatchingResult transferMatching(
            List<ItemStack> references,
            List<SourceContainer> sources,
            ItemContainer vaultContainer,
            int capacity
    ) {
        int movedItems = 0;
        int skippedItems = 0;
        int affectedStacks = 0;

        for (SourceContainer sourceContainer : sources) {
            ItemContainer sourceInventory = sourceContainer.container();
            for (int sourceSlot = 0; sourceSlot < sourceInventory.getCapacity(); sourceSlot++) {
                if (sourceSlot == sourceContainer.excludedSlot()) continue;

                ItemStack source = sourceInventory.getItemStack((short) sourceSlot);
                if (isEmpty(source) || !matchesAnyReference(source, references)) continue;

                int originalQuantity = source.getQuantity();
                int remaining = fillExistingStacks(source, originalQuantity, vaultContainer, capacity);
                remaining = fillEmptySlots(source, remaining, vaultContainer, capacity);
                int moved = originalQuantity - remaining;

                if (moved > 0) {
                    movedItems += moved;
                    affectedStacks++;
                    setAndVerify(
                            sourceInventory,
                            sourceSlot,
                            remaining == 0 ? null : source.withQuantity(remaining)
                    );
                }
                skippedItems += remaining;
            }
        }

        boolean full = movedItems == 0 && !hasRoomForAnyReference(vaultContainer, capacity, references);
        return new DepositMatchingResult(movedItems, affectedStacks, skippedItems, full);
    }

    private int fillExistingStacks(ItemStack source, int remaining, ItemContainer vault, int capacity) {
        for (int vaultSlot = 0; vaultSlot < capacity && remaining > 0; vaultSlot++) {
            ItemStack destination = vault.getItemStack((short) vaultSlot);
            if (isEmpty(destination) || !isStackCompatible(source, destination)) continue;

            int maxStack = getMaxStackSize(destination);
            int freeSpace = Math.max(0, maxStack - destination.getQuantity());
            if (freeSpace == 0) continue;

            int moved = Math.min(remaining, freeSpace);
            setAndVerify(
                    vault,
                    vaultSlot,
                    destination.withQuantity(destination.getQuantity() + moved)
            );
            remaining -= moved;
        }
        return remaining;
    }

    private int fillEmptySlots(ItemStack source, int remaining, ItemContainer vault, int capacity) {
        int maxStack = getMaxStackSize(source);
        for (int vaultSlot = 0; vaultSlot < capacity && remaining > 0; vaultSlot++) {
            ItemStack destination = vault.getItemStack((short) vaultSlot);
            if (!isEmpty(destination)) continue;

            int moved = Math.min(remaining, maxStack);
            setAndVerify(vault, vaultSlot, source.withQuantity(moved));
            remaining -= moved;
        }
        return remaining;
    }

    private boolean hasRoomForAnyReference(ItemContainer vault, int capacity, List<ItemStack> references) {
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack destination = vault.getItemStack((short) slot);
            if (isEmpty(destination)) return true;
            for (ItemStack reference : references) {
                if (isStackCompatible(reference, destination)
                        && destination.getQuantity() < getMaxStackSize(destination)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ItemStack> collectReferenceStacks(ItemContainer container, int capacity) {
        List<ItemStack> references = new ArrayList<>();
        for (int slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack((short) slot);
            if (isEmpty(stack)) continue;
            boolean alreadyPresent = references.stream().anyMatch(reference -> isStackCompatible(reference, stack));
            if (!alreadyPresent) references.add(stack);
        }
        return List.copyOf(references);
    }

    private List<SourceContainer> collectSourceContainers(
            Inventory inventory,
            ItemContainer vaultContainer,
            boolean includeHotbar
    ) {
        List<SourceContainer> sources = new ArrayList<>(3);
        Set<ItemContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        addSource(sources, seen, inventory.getStorage(), vaultContainer, -1);
        addSource(sources, seen, inventory.getBackpack(), vaultContainer, -1);

        if (includeHotbar) {
            int activeSlot = inventory.getActiveHotbarSlot();
            addSource(sources, seen, inventory.getHotbar(), vaultContainer, activeSlot);
        }

        return List.copyOf(sources);
    }

    private void addSource(
            List<SourceContainer> sources,
            Set<ItemContainer> seen,
            ItemContainer candidate,
            ItemContainer vaultContainer,
            int excludedSlot
    ) {
        if (candidate == null || candidate == vaultContainer || !seen.add(candidate)) return;
        int safeExcludedSlot = excludedSlot >= 0 && excludedSlot < candidate.getCapacity() ? excludedSlot : -1;
        sources.add(new SourceContainer(candidate, safeExcludedSlot));
    }

    private boolean matchesAnyReference(ItemStack stack, List<ItemStack> references) {
        for (ItemStack reference : references) {
            if (isStackCompatible(stack, reference)) return true;
        }
        return false;
    }

    private boolean isStackCompatible(ItemStack first, ItemStack second) {
        return !isEmpty(first) && !isEmpty(second) && first.isStackableWith(second);
    }

    private int getMaxStackSize(ItemStack stack) {
        if (isEmpty(stack)) return 1;
        try {
            int maxStack = stack.getItem().getMaxStack();
            return maxStack > 0 ? maxStack : Math.max(1, stack.getQuantity());
        } catch (RuntimeException exception) {
            return Math.max(1, stack.getQuantity());
        }
    }

    private Inventory getInventory(Player player) {
        try {
            return player.getInventory();
        } catch (RuntimeException exception) {
            System.err.println("[VoidVault] Failed to access player inventory: " + exception.getMessage());
            return null;
        }
    }

    private List<ItemStack> snapshot(ItemContainer container) {
        List<ItemStack> snapshot = new ArrayList<>(container.getCapacity());
        for (int slot = 0; slot < container.getCapacity(); slot++) {
            snapshot.add(container.getItemStack((short) slot));
        }
        return Collections.unmodifiableList(snapshot);
    }

    private boolean restoreSafely(ItemContainer container, List<ItemStack> snapshot) {
        try {
            restore(container, snapshot);
            return true;
        } catch (RuntimeException rollbackException) {
            System.err.println("[VoidVault] Failed to restore an inventory after deposit rollback: "
                    + rollbackException.getMessage());
            return false;
        }
    }

    private void restore(ItemContainer container, List<ItemStack> snapshot) {
        int capacity = Math.min(container.getCapacity(), snapshot.size());
        for (int slot = 0; slot < capacity; slot++) {
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
        if (isEmpty(expected)) return isEmpty(actual);
        if (isEmpty(actual) || expected.getQuantity() != actual.getQuantity()) return false;
        return expected.isStackableWith(actual);
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private record SourceContainer(ItemContainer container, int excludedSlot) {
    }

    private record ContainerSnapshot(ItemContainer container, List<ItemStack> items) {
    }
}

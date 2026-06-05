package dev.voidvault.storage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.voidvault.config.VoidVaultConfig;
import dev.voidvault.model.SavedItem;
import dev.voidvault.permissions.PermissionService;
import dev.voidvault.util.VaultJson;
import org.bson.BsonDocument;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VaultManager {
    private final DatabaseService database;
    private PermissionService permissionService;
    private VoidVaultConfig config;
    private final Map<UUID, ItemContainer> loadedContainers = new ConcurrentHashMap<>();
    private final Set<UUID> openVaults = ConcurrentHashMap.newKeySet();

    public VaultManager(DatabaseService database, PermissionService permissionService, VoidVaultConfig config) {
        this.database = database;
        this.permissionService = permissionService;
        this.config = config;
    }

    public void reload(PermissionService permissionService, VoidVaultConfig config) {
        saveLoaded();
        this.permissionService = permissionService;
        this.config = config;
        this.loadedContainers.clear();
        this.openVaults.clear();
    }

    public void openVault(Player viewer) {
        openVaultInternal(viewer, playerUuid(viewer), 0, 0, 0, 0, null, true);
    }

    public void openVault(Player viewer, UUID ownerUuid) {
        openVaultInternal(viewer, ownerUuid, 0, 0, 0, 0, null, true);
    }

    public void openVault(Player viewer, int x, int y, int z, int rotationIndex, BlockType blockType) {
        openVaultInternal(viewer, playerUuid(viewer), x, y, z, rotationIndex, blockType, false);
    }

    public void openVault(Player viewer, UUID ownerUuid, int x, int y, int z, int rotationIndex, BlockType blockType) {
        openVaultInternal(viewer, ownerUuid, x, y, z, rotationIndex, blockType, false);
    }

    private void openVaultInternal(Player viewer, UUID ownerUuid, int x, int y, int z, int rotationIndex, BlockType blockType, boolean skipPermissionCheck) {
        if (viewer == null || ownerUuid == null) return;

        UUID viewerUuid = playerUuid(viewer);
        boolean sameOwner = viewerUuid != null && viewerUuid.equals(ownerUuid);
        if (!skipPermissionCheck && sameOwner && !permissionService.hasPermission(viewerUuid, config.commands.usePermission)) {
            return;
        }
        if (!skipPermissionCheck && !sameOwner && !permissionService.hasPermission(viewerUuid, config.commands.adminPermission)) {
            return;
        }

        if (config.safety.preventDoubleOpen && openVaults.contains(ownerUuid)) {
            unloadVault(ownerUuid);
        }

        ItemContainer container = loadedContainers.computeIfAbsent(ownerUuid, this::loadContainer);
        int currentSlots = container.getCapacity();
        int wantedSlots = permissionService.getSlots(ownerUuid);
        if (currentSlots != wantedSlots) {
            saveContainer(ownerUuid, container);
            loadedContainers.remove(ownerUuid);
            container = loadContainer(ownerUuid);
            loadedContainers.put(ownerUuid, container);
        }

        if (config.safety.saveOnEveryChange) {
            ItemContainer finalContainer = container;
            container.registerChangeEvent(event -> saveContainer(ownerUuid, finalContainer));
        }

        Window window;
        if (blockType != null && container.getCapacity() == config.slots.maxSlots) {
            window = new ContainerBlockWindow(x, y, z, rotationIndex, blockType, container);
        } else {
            window = new ContainerWindow(container);
        }

        ItemContainer finalContainer = container;
        window.registerCloseEvent(event -> {
            if (config.safety.saveOnClose) {
                saveContainer(ownerUuid, finalContainer);
            }
            openVaults.remove(ownerUuid);
        });

        openVaults.add(ownerUuid);
        Ref ref = viewer.getReference();
        Store store = ref.getStore();
        viewer.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    private ItemContainer loadContainer(UUID uuid) {
        short slots = (short) permissionService.getSlots(uuid);
        SimpleItemContainer container = new SimpleItemContainer(slots);

        try {
            Map<Integer, SavedItem> items = VaultJson.parse(database.getInventory(uuid));
            for (Map.Entry<Integer, SavedItem> entry : items.entrySet()) {
                Integer slot = entry.getKey();
                SavedItem saved = entry.getValue();
                if (slot == null || saved == null || !saved.isValid()) continue;
                if (slot < 0 || slot >= slots) continue;

                ItemStack stack = toItemStack(saved);
                if (stack != null) {
                    container.setItemStackForSlot(slot.shortValue(), stack);
                }
            }
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to load vault for " + uuid + ": " + exception.getMessage());
            exception.printStackTrace();
        }

        return container;
    }

    public void saveContainer(UUID uuid, ItemContainer container) {
        if (uuid == null || container == null || !database.isConnected()) return;

        try {
            int capacity = container.getCapacity();
            Map<Integer, SavedItem> merged = VaultJson.parse(database.getInventory(uuid));

            for (int slot = 0; slot < capacity; slot++) {
                merged.remove(slot);
            }

            for (int slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack((short) slot);
                if (stack == null || stack.isEmpty()) continue;
                merged.put(slot, fromItemStack(stack));
            }

            database.saveInventory(uuid, VaultJson.stringify(merged));
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to save vault for " + uuid + ": " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    public void saveLoaded() {
        for (Map.Entry<UUID, ItemContainer> entry : loadedContainers.entrySet()) {
            saveContainer(entry.getKey(), entry.getValue());
        }
    }

    public void discardLoaded() {
        loadedContainers.clear();
        openVaults.clear();
    }

    public void unloadVault(UUID uuid) {
        ItemContainer container = loadedContainers.remove(uuid);
        if (container != null) {
            saveContainer(uuid, container);
        }
        openVaults.remove(uuid);
    }

    public int getOverflowCount(UUID uuid) {
        try {
            int slots = permissionService.getSlots(uuid);
            Map<Integer, SavedItem> items = VaultJson.parse(database.getInventory(uuid));
            int count = 0;
            for (Integer slot : items.keySet()) {
                if (slot != null && slot >= slots) count++;
            }
            return count;
        } catch (SQLException exception) {
            return 0;
        }
    }

    @SuppressWarnings("removal")
    private UUID playerUuid(Player player) {
        return player == null ? null : player.getUuid();
    }

    private ItemStack toItemStack(SavedItem saved) {
        try {
            ItemStack stack = new ItemStack(saved.id, saved.amount);
            if (saved.metadata != null && !saved.metadata.isBlank()) {
                try {
                    BsonDocument metadata = BsonDocument.parse(saved.metadata);
                    stack = stack.withMetadata(metadata);
                } catch (Exception exception) {
                    System.err.println("[VoidVault] Invalid item metadata for item " + saved.id + ": " + exception.getMessage());
                }
            }
            return stack.withDurability(saved.durability);
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to create ItemStack for " + saved.id + ": " + exception.getMessage());
            return null;
        }
    }

    private SavedItem fromItemStack(ItemStack stack) {
        String metadata = null;
        try {
            if (stack.getMetadata() != null) {
                metadata = BsonUtil.toJson(stack.getMetadata());
            }
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to serialize item metadata for " + stack.getItemId() + ": " + exception.getMessage());
        }
        return new SavedItem(stack.getItemId(), stack.getQuantity(), metadata, stack.getDurability());
    }
}

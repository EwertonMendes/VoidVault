package tblack.voidvault.storage;

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
import org.bson.BsonDocument;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.SavedItem;
import tblack.voidvault.model.VaultInfo;
import tblack.voidvault.model.VaultKey;
import tblack.voidvault.permissions.PermissionService;
import tblack.voidvault.ui.VaultSelectorService;
import tblack.voidvault.util.VaultJson;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class VaultManager {
    private final DatabaseService database;
    private final Map<VaultKey, ItemContainer> loadedContainers = new ConcurrentHashMap<>();
    private final Map<UUID, VaultKey> openVaultsByOwner = new ConcurrentHashMap<>();
    private final Set<VaultKey> changeListeners = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();
    private final VaultSelectorService selectorService;
    private PermissionService permissionService;
    private VoidVaultConfig config;

    public VaultManager(DatabaseService database, PermissionService permissionService, VoidVaultConfig config) {
        this.database = database;
        this.permissionService = permissionService;
        this.config = config;
        this.selectorService = new VaultSelectorService(this);
    }

    public void reload(PermissionService permissionService, VoidVaultConfig config) {
        saveLoaded();
        generation.incrementAndGet();
        selectorService.clearSessions();
        this.permissionService = permissionService;
        this.config = config;
        this.loadedContainers.clear();
        this.openVaultsByOwner.clear();
        this.changeListeners.clear();
    }

    public void openVault(Player viewer) {
        openVault(viewer, playerUuid(viewer), DatabaseService.PRIMARY_VAULT_ID);
    }

    public void openVault(Player viewer, int vaultId) {
        openVault(viewer, playerUuid(viewer), vaultId);
    }

    public void openVault(Player viewer, UUID ownerUuid) {
        openVault(viewer, ownerUuid, DatabaseService.PRIMARY_VAULT_ID);
    }

    public void openVault(Player viewer, UUID ownerUuid, int vaultId) {
        openVaultInternal(viewer, ownerUuid, vaultId, 0, 0, 0, 0, null, true);
    }

    public void openVaultFromBlock(Player viewer, int x, int y, int z, int rotationIndex, BlockType blockType) {
        if (viewer == null) return;

        UUID ownerUuid = playerUuid(viewer);
        if (ownerUuid == null) return;

        if (!config.isMultiVaultEnabled()) {
            openVaultInternal(viewer, ownerUuid, DatabaseService.PRIMARY_VAULT_ID, x, y, z, rotationIndex, blockType, false);
            return;
        }

        int vaultCount = permissionService.getVaultCount(ownerUuid);
        if (vaultCount <= 1) {
            openVaultInternal(viewer, ownerUuid, DatabaseService.PRIMARY_VAULT_ID, x, y, z, rotationIndex, blockType, false);
            return;
        }

        selectorService.open(viewer, x, y, z, rotationIndex, blockType, vaultCount);
    }

    public void openVaultSelector(Player viewer) {
        if (viewer == null) return;

        UUID ownerUuid = playerUuid(viewer);
        if (ownerUuid == null) return;

        int vaultCount = permissionService.getVaultCount(ownerUuid);
        if (vaultCount <= 1) {
            openVaultInternal(viewer, ownerUuid, DatabaseService.PRIMARY_VAULT_ID, 0, 0, 0, 0, null, false);
            return;
        }

        selectorService.open(viewer, vaultCount);
    }

    public String getVaultName(UUID uuid, int vaultId) {
        if (uuid == null || vaultId < 1) return null;
        try {
            return database.getVaultName(uuid, vaultId);
        } catch (SQLException exception) {
            return null;
        }
    }

    public Map<Integer, String> getVaultNames(UUID uuid) {
        if (uuid == null) return Collections.emptyMap();
        try {
            return database.getVaultNames(uuid);
        } catch (SQLException exception) {
            return Collections.emptyMap();
        }
    }

    public boolean setVaultName(UUID uuid, int vaultId, String rawName) {
        if (uuid == null || vaultId < 1) return false;
        String name = normalizeVaultName(rawName);
        try {
            database.setVaultName(uuid, vaultId, name);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to rename vault " + vaultId + " for " + uuid + ": " + exception.getMessage());
            return false;
        }
    }

    public String normalizeVaultName(String rawName) {
        if (rawName == null) return null;
        String normalized = rawName.replace('_', ' ').replaceAll("\\p{Cntrl}", "").trim();
        if (normalized.equalsIgnoreCase("reset") || normalized.equalsIgnoreCase("clear") || normalized.equalsIgnoreCase("default") || normalized.equals("-")) {
            return null;
        }
        if (normalized.isBlank()) return null;
        return normalized.length() <= 15 ? normalized : normalized.substring(0, 15);
    }

    public void openVaultFromSelector(Player viewer, UUID ownerUuid, int vaultId, int x, int y, int z, int rotationIndex, BlockType blockType) {
        openVaultInternal(viewer, ownerUuid, vaultId, x, y, z, rotationIndex, blockType, false);
    }

    private void openVaultInternal(Player viewer, UUID ownerUuid, int vaultId, int x, int y, int z, int rotationIndex, BlockType blockType, boolean skipPermissionCheck) {
        if (viewer == null || ownerUuid == null || vaultId < 1) return;
        if (!canOpen(viewer, ownerUuid, vaultId, skipPermissionCheck)) return;

        VaultKey key = new VaultKey(ownerUuid, vaultId);
        unloadOpenVault(ownerUuid, key);

        ItemContainer container = loadedContainers.computeIfAbsent(key, this::loadContainer);
        int wantedSlots = permissionService.getSlots(ownerUuid);
        if (container.getCapacity() != wantedSlots) {
            saveContainer(key, container);
            loadedContainers.remove(key);
            changeListeners.remove(key);
            container = loadContainer(key);
            loadedContainers.put(key, container);
        }

        registerChangeListener(key, container);
        Window window = createWindow(container, x, y, z, rotationIndex, blockType);
        registerCloseListener(key, container, window);
        openVaultsByOwner.put(ownerUuid, key);

        Ref ref = viewer.getReference();
        Store store = ref.getStore();
        viewer.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window);
    }

    private boolean canOpen(Player viewer, UUID ownerUuid, int vaultId, boolean skipPermissionCheck) {
        UUID viewerUuid = playerUuid(viewer);
        boolean sameOwner = viewerUuid != null && viewerUuid.equals(ownerUuid);
        if (!skipPermissionCheck && sameOwner && !permissionService.hasPermission(viewerUuid, config.commands.usePermission)) {
            return false;
        }
        if (!skipPermissionCheck && !sameOwner && !permissionService.hasPermission(viewerUuid, config.commands.adminPermission)) {
            return false;
        }
        if (!sameOwner) {
            return isVaultIdInsideConfig(vaultId);
        }
        return permissionService.canAccessVault(ownerUuid, vaultId);
    }

    private boolean isVaultIdInsideConfig(int vaultId) {
        if (vaultId == DatabaseService.PRIMARY_VAULT_ID) {
            return true;
        }
        if (!config.isMultiVaultEnabled()) {
            return false;
        }
        return vaultId <= Math.max(1, Math.min(VoidVaultConfig.MAX_VAULTS_LIMIT, config.multiVaults.maxVaults));
    }

    private void unloadOpenVault(UUID ownerUuid, VaultKey nextKey) {
        if (!config.safety.preventDoubleOpen) {
            return;
        }

        VaultKey openKey = openVaultsByOwner.get(ownerUuid);
        if (openKey == null) {
            return;
        }

        unloadVault(openKey);
        if (openKey.equals(nextKey)) {
            openVaultsByOwner.remove(ownerUuid);
        }
    }

    private Window createWindow(ItemContainer container, int x, int y, int z, int rotationIndex, BlockType blockType) {
        if (blockType == null) {
            return new ContainerWindow(container);
        }
        return new ContainerBlockWindow(x, y, z, rotationIndex, blockType, container);
    }

    private void registerChangeListener(VaultKey key, ItemContainer container) {
        if (!config.safety.saveOnEveryChange) {
            return;
        }
        if (!changeListeners.add(key)) {
            return;
        }
        long activeGeneration = generation.get();
        container.registerChangeEvent(event -> {
            if (generation.get() != activeGeneration) {
                return;
            }
            saveContainer(key, container);
        });
    }

    private void registerCloseListener(VaultKey key, ItemContainer container, Window window) {
        long activeGeneration = generation.get();
        window.registerCloseEvent(event -> {
            if (generation.get() != activeGeneration) {
                return;
            }
            if (config.safety.saveOnClose) {
                saveContainer(key, container);
            }
            openVaultsByOwner.remove(key.ownerUuid(), key);
        });
    }

    private ItemContainer loadContainer(VaultKey key) {
        short slots = (short) permissionService.getSlots(key.ownerUuid());
        SimpleItemContainer container = new SimpleItemContainer(slots);

        try {
            Map<Integer, SavedItem> items = VaultJson.parse(database.getInventory(key.ownerUuid(), key.vaultId()));
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
            System.err.println("[VoidVault] Failed to load vault " + key.vaultId() + " for " + key.ownerUuid() + ": " + exception.getMessage());
            exception.printStackTrace();
        }

        return container;
    }

    public void saveContainer(UUID uuid, ItemContainer container) {
        saveContainer(new VaultKey(uuid, DatabaseService.PRIMARY_VAULT_ID), container);
    }

    public void saveContainer(UUID uuid, int vaultId, ItemContainer container) {
        saveContainer(new VaultKey(uuid, vaultId), container);
    }

    public void saveContainer(VaultKey key, ItemContainer container) {
        if (key == null || container == null || !database.isConnected()) return;

        try {
            int capacity = container.getCapacity();
            Map<Integer, SavedItem> merged = VaultJson.parse(database.getInventory(key.ownerUuid(), key.vaultId()));

            for (int slot = 0; slot < capacity; slot++) {
                merged.remove(slot);
            }

            for (int slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack((short) slot);
                if (stack == null || stack.isEmpty()) continue;
                merged.put(slot, fromItemStack(stack));
            }

            database.saveInventory(key.ownerUuid(), key.vaultId(), VaultJson.stringify(merged));
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to save vault " + key.vaultId() + " for " + key.ownerUuid() + ": " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    public void saveLoaded() {
        for (Map.Entry<VaultKey, ItemContainer> entry : loadedContainers.entrySet()) {
            saveContainer(entry.getKey(), entry.getValue());
        }
    }

    public void discardLoaded() {
        generation.incrementAndGet();
        loadedContainers.clear();
        openVaultsByOwner.clear();
        changeListeners.clear();
        selectorService.clearSessions();
    }

    public void unloadVault(UUID uuid) {
        if (uuid == null) return;

        VaultKey openKey = openVaultsByOwner.get(uuid);
        if (openKey != null) {
            unloadVault(openKey);
            return;
        }

        unloadVault(new VaultKey(uuid, DatabaseService.PRIMARY_VAULT_ID));
    }

    public void unloadVault(VaultKey key) {
        if (key == null) return;

        ItemContainer container = loadedContainers.remove(key);
        if (container != null) {
            saveContainer(key, container);
        }
        openVaultsByOwner.remove(key.ownerUuid(), key);
        changeListeners.remove(key);
    }

    public int getOverflowCount(UUID uuid) {
        return getOverflowCount(uuid, DatabaseService.PRIMARY_VAULT_ID);
    }

    public int getOverflowCount(UUID uuid, int vaultId) {
        try {
            int slots = permissionService.getSlots(uuid);
            Map<Integer, SavedItem> items = VaultJson.parse(database.getInventory(uuid, vaultId));
            int count = 0;
            for (Integer slot : items.keySet()) {
                if (slot != null && slot >= slots) count++;
            }
            return count;
        } catch (SQLException exception) {
            return 0;
        }
    }

    public int getOverflowCountAll(UUID uuid) {
        try {
            int total = 0;
            for (Integer vaultId : database.getStoredVaultIds(uuid)) {
                if (vaultId == null) continue;
                total += getOverflowCount(uuid, vaultId);
            }
            return total;
        } catch (SQLException exception) {
            return 0;
        }
    }

    public List<VaultInfo> listVaults(UUID uuid) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        int accessibleVaults = permissionService.getVaultCount(uuid);
        int maxVaults = config.isMultiVaultEnabled() ? Math.max(accessibleVaults, config.multiVaults.maxVaults) : 1;

        for (int vaultId = 1; vaultId <= accessibleVaults; vaultId++) {
            ids.add(vaultId);
        }

        try {
            ids.addAll(database.getStoredVaultIds(uuid));
        } catch (SQLException ignored) {
        }

        List<VaultInfo> result = new ArrayList<>();
        for (Integer vaultId : ids) {
            if (vaultId == null || vaultId < 1 || vaultId > maxVaults) continue;
            boolean accessible = vaultId <= accessibleVaults;
            boolean stored = isStored(uuid, vaultId);
            result.add(new VaultInfo(vaultId, accessible, stored, getOverflowCount(uuid, vaultId), getVaultName(uuid, vaultId)));
        }
        return result;
    }

    public int getVaultCount(UUID uuid) {
        return permissionService.getVaultCount(uuid);
    }

    public boolean canAccessVault(UUID uuid, int vaultId) {
        return permissionService.canAccessVault(uuid, vaultId);
    }

    public VoidVaultConfig getConfig() {
        return config;
    }

    private boolean isStored(UUID uuid, int vaultId) {
        try {
            return database.exists(uuid, vaultId);
        } catch (SQLException exception) {
            return false;
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

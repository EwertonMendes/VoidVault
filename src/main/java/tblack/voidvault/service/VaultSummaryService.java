package tblack.voidvault.service;

import tblack.voidvault.model.SavedItem;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.model.VaultSummary;
import tblack.voidvault.permissions.PermissionService;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.util.VaultJson;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VaultSummaryService {
    private final DatabaseService database;
    private final PermissionService permissionService;
    private final VaultMetadataService metadataService;

    public VaultSummaryService(DatabaseService database, PermissionService permissionService, VaultMetadataService metadataService) {
        this.database = database;
        this.permissionService = permissionService;
        this.metadataService = metadataService;
    }

    public List<VaultSummary> buildSummaries(UUID uuid) {
        if (uuid == null) return List.of();

        int accessibleCount = Math.max(1, permissionService.getVaultCount(uuid));
        int capacity = Math.max(1, permissionService.getSlots(uuid));
        List<Integer> vaultIds = new ArrayList<>(accessibleCount);
        for (int vaultId = 1; vaultId <= accessibleCount; vaultId++) {
            vaultIds.add(vaultId);
        }

        Map<Integer, VaultMetadata> metadataMap = metadataService.getAll(uuid);
        Map<Integer, String> inventories = loadInventories(uuid, vaultIds);
        int effectiveDefaultId = metadataMap.entrySet().stream()
                .filter(entry -> entry.getValue().isDefault())
                .mapToInt(Map.Entry::getKey)
                .findFirst()
                .orElse(DatabaseService.PRIMARY_VAULT_ID);
        if (effectiveDefaultId < 1 || effectiveDefaultId > accessibleCount) {
            effectiveDefaultId = DatabaseService.PRIMARY_VAULT_ID;
        }

        List<VaultSummary> summaries = new ArrayList<>(accessibleCount);
        for (int vaultId : vaultIds) {
            VaultMetadata metadata = metadataMap.getOrDefault(vaultId, VaultMetadata.empty(uuid, vaultId));
            Counts counts = countSlots(inventories.get(vaultId), capacity);
            summaries.add(new VaultSummary(
                    vaultId,
                    metadata.displayName(),
                    metadata.iconId(),
                    metadata.colorId(),
                    metadata.favorite(),
                    vaultId == effectiveDefaultId,
                    capacity,
                    counts.occupiedVisible(),
                    counts.totalStored(),
                    counts.overflow()
            ));
        }

        summaries.sort(Comparator
                .comparing((VaultSummary summary) -> !summary.defaultVault())
                .thenComparing(summary -> !summary.favorite())
                .thenComparingInt(VaultSummary::vaultId));
        return List.copyOf(summaries);
    }

    private Map<Integer, String> loadInventories(UUID uuid, List<Integer> vaultIds) {
        try {
            return database.getInventoriesBatch(uuid, vaultIds);
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to batch-load vault summaries for " + uuid + ": " + exception.getMessage());
            return Map.of();
        }
    }

    private Counts countSlots(String inventoryJson, int capacity) {
        if (inventoryJson == null || inventoryJson.isBlank()) return Counts.EMPTY;

        try {
            int occupiedVisible = 0;
            int totalStored = 0;
            int overflow = 0;
            for (Map.Entry<Integer, SavedItem> entry : VaultJson.parse(inventoryJson).entrySet()) {
                Integer slot = entry.getKey();
                SavedItem item = entry.getValue();
                if (slot == null || slot < 0 || item == null || !item.isValid()) continue;
                totalStored++;
                if (slot < capacity) occupiedVisible++;
                else overflow++;
            }
            return new Counts(occupiedVisible, totalStored, overflow);
        } catch (RuntimeException exception) {
            System.err.println("[VoidVault] Ignoring invalid inventory JSON while building a vault summary: " + exception.getMessage());
            return Counts.EMPTY;
        }
    }

    private record Counts(int occupiedVisible, int totalStored, int overflow) {
        private static final Counts EMPTY = new Counts(0, 0, 0);
    }
}

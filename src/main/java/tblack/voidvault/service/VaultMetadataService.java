package tblack.voidvault.service;

import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.ui.VaultColor;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class VaultMetadataService {
    private final DatabaseService database;
    private final VaultIconCatalog iconCatalog;

    public VaultMetadataService(DatabaseService database, VaultIconCatalog iconCatalog) {
        this.database = database;
        this.iconCatalog = iconCatalog;
    }

    public VaultMetadata get(UUID uuid, int vaultId) {
        if (uuid == null) throw new IllegalArgumentException("uuid cannot be null");
        if (vaultId < 1) throw new IllegalArgumentException("vaultId must be greater than zero");
        try {
            return database.getVaultMetadata(uuid, vaultId);
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to load metadata for vault " + vaultId + ": " + exception.getMessage());
            return VaultMetadata.empty(uuid, vaultId);
        }
    }

    public Map<Integer, VaultMetadata> getAll(UUID uuid) {
        if (uuid == null) return Collections.emptyMap();
        try {
            return database.getAllVaultMetadata(uuid);
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to load metadata for " + uuid + ": " + exception.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean setName(UUID uuid, int vaultId, String rawName) {
        if (uuid == null || vaultId < 1) return false;
        try {
            database.setVaultName(uuid, vaultId, rawName);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to set name for vault " + vaultId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean setIcon(UUID uuid, int vaultId, String iconId) {
        if (uuid == null || vaultId < 1) return false;
        String normalized = iconId == null || iconId.isBlank() ? null : iconId.trim();
        if (normalized != null && !iconCatalog.isValidItemId(normalized)) return false;
        try {
            database.setIcon(uuid, vaultId, normalized);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to set icon for vault " + vaultId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean setColor(UUID uuid, int vaultId, String colorId) {
        if (uuid == null || vaultId < 1) return false;
        String validated = VaultColor.normalizeSelection(colorId);
        if (colorId != null && !colorId.isBlank() && validated == null) return false;
        try {
            database.setColor(uuid, vaultId, validated);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to set color for vault " + vaultId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean setFavorite(UUID uuid, int vaultId, boolean favorite) {
        if (uuid == null || vaultId < 1) return false;
        try {
            database.setFavorite(uuid, vaultId, favorite);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to set favorite for vault " + vaultId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean setDefault(UUID uuid, int vaultId) {
        if (uuid == null || vaultId < 1) return false;
        try {
            database.setDefaultVault(uuid, vaultId);
            return true;
        } catch (SQLException exception) {
            System.err.println("[VoidVault] Failed to set default vault " + vaultId + ": " + exception.getMessage());
            return false;
        }
    }

    public int getDefaultVaultId(UUID uuid) {
        if (uuid == null) return DatabaseService.PRIMARY_VAULT_ID;
        Map<Integer, VaultMetadata> all = getAll(uuid);
        for (Map.Entry<Integer, VaultMetadata> entry : all.entrySet()) {
            if (entry.getValue().isDefault()) {
                return entry.getKey();
            }
        }
        return DatabaseService.PRIMARY_VAULT_ID;
    }
}

package tblack.voidvault.model;

import tblack.voidvault.ui.VaultColor;

import java.util.UUID;

public record VaultMetadata(
        UUID ownerUuid,
        int vaultId,
        String displayName,
        String iconId,
        String colorId,
        boolean favorite,
        boolean defaultVault
) {
    private static final int MAX_ICON_ID_LENGTH = 256;

    public VaultMetadata {
        if (ownerUuid == null) {
            throw new IllegalArgumentException("ownerUuid cannot be null");
        }
        if (vaultId < 1) {
            throw new IllegalArgumentException("vaultId must be greater than zero");
        }
        iconId = normalizeIconId(iconId);
        colorId = VaultColor.normalizeSelection(colorId);
    }

    public static VaultMetadata empty(UUID ownerUuid, int vaultId) {
        return new VaultMetadata(ownerUuid, vaultId, null, null, null, false, false);
    }

    public boolean hasCustomName() {
        return displayName != null && !displayName.isBlank();
    }

    public boolean hasCustomIcon() {
        return iconId != null;
    }

    public boolean isDefaultEffective() {
        return defaultVault;
    }

    public boolean isDefaultExplicit() {
        return defaultVault;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public boolean isDefault() {
        return defaultVault;
    }

    public boolean isEffectivelyEmpty() {
        return !hasCustomName() && iconId == null && colorId == null && !favorite && !defaultVault;
    }

    public VaultMetadata withDisplayName(String displayName) {
        return new VaultMetadata(ownerUuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    public VaultMetadata withIcon(String iconId) {
        return new VaultMetadata(ownerUuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    public VaultMetadata withColor(String colorId) {
        return new VaultMetadata(ownerUuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    public VaultMetadata withFavorite(boolean favorite) {
        return new VaultMetadata(ownerUuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    public VaultMetadata withDefault(boolean defaultVault) {
        return new VaultMetadata(ownerUuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    private static String normalizeIconId(String rawIconId) {
        if (rawIconId == null) return null;
        String normalized = rawIconId.replaceAll("\\p{Cntrl}", "").trim();
        if (normalized.isEmpty()) return null;
        return normalized.length() <= MAX_ICON_ID_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ICON_ID_LENGTH);
    }
}

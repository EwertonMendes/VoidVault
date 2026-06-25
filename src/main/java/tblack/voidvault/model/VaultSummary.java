package tblack.voidvault.model;

public record VaultSummary(
        int vaultId,
        String displayName,
        String iconId,
        String colorId,
        boolean favorite,
        boolean defaultVault,
        int capacity,
        int occupiedVisibleSlots,
        int totalStoredSlots,
        int overflowSlots
) {
    public VaultSummary {
        if (vaultId < 1) {
            throw new IllegalArgumentException("vaultId must be greater than zero");
        }
        if (capacity < 0) capacity = 0;
        if (occupiedVisibleSlots < 0) occupiedVisibleSlots = 0;
        if (totalStoredSlots < 0) totalStoredSlots = 0;
        if (overflowSlots < 0) overflowSlots = 0;
    }

    public boolean hasOverflow() {
        return overflowSlots > 0;
    }

    public boolean hasCustomName() {
        return displayName != null && !displayName.isBlank();
    }

    public String effectiveName(int vaultId) {
        if (hasCustomName()) return displayName;
        return null;
    }

    public double occupancyRatio() {
        if (capacity <= 0) return 0.0;
        return Math.min(1.0, (double) occupiedVisibleSlots / capacity);
    }
}

package tblack.voidvault.model;

public record DepositMatchingResult(
        int movedItemCount,
        int affectedStackCount,
        int skippedItemCount,
        boolean vaultWasFull
) {
    public static DepositMatchingResult empty() {
        return new DepositMatchingResult(0, 0, 0, false);
    }

    public static DepositMatchingResult noSpace() {
        return new DepositMatchingResult(0, 0, 0, true);
    }

    public static DepositMatchingResult noSimilar() {
        return new DepositMatchingResult(0, 0, 0, false);
    }

    public boolean hasMovedItems() {
        return movedItemCount > 0;
    }
}

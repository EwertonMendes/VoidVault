package tblack.voidvault.model;

public record SortResult(
        int sortedSlotCount,
        boolean changed
) {
    public static SortResult empty() {
        return new SortResult(0, false);
    }
}

package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultSummaryTest {

    @Test
    void basicSummary() {
        VaultSummary summary = new VaultSummary(1, "My Vault", "default", "blue", false, false, 27, 5, 5, 0);
        assertEquals(1, summary.vaultId());
        assertEquals("My Vault", summary.displayName());
        assertEquals(27, summary.capacity());
        assertEquals(5, summary.occupiedVisibleSlots());
        assertEquals(5, summary.totalStoredSlots());
        assertEquals(0, summary.overflowSlots());
        assertFalse(summary.hasOverflow());
    }

    @Test
    void overflowDetected() {
        VaultSummary summary = new VaultSummary(1, null, null, null, false, false, 9, 9, 10, 1);
        assertTrue(summary.hasOverflow());
        assertEquals(1, summary.overflowSlots());
    }

    @Test
    void occupancyRatio() {
        VaultSummary summary = new VaultSummary(1, null, null, null, false, false, 18, 9, 9, 0);
        assertEquals(0.5, summary.occupancyRatio(), 0.001);
    }

    @Test
    void occupancyRatioFull() {
        VaultSummary summary = new VaultSummary(1, null, null, null, false, false, 9, 9, 9, 0);
        assertEquals(1.0, summary.occupancyRatio(), 0.001);
    }

    @Test
    void occupancyRatioZeroCapacity() {
        VaultSummary summary = new VaultSummary(1, null, null, null, false, false, 0, 0, 0, 0);
        assertEquals(0.0, summary.occupancyRatio(), 0.001);
    }

    @Test
    void hasCustomName() {
        VaultSummary named = new VaultSummary(1, "My Vault", null, null, false, false, 9, 0, 0, 0);
        assertTrue(named.hasCustomName());

        VaultSummary unnamed = new VaultSummary(1, null, null, null, false, false, 9, 0, 0, 0);
        assertFalse(unnamed.hasCustomName());

        VaultSummary blank = new VaultSummary(1, "  ", null, null, false, false, 9, 0, 0, 0);
        assertFalse(blank.hasCustomName());
    }

    @Test
    void negativeValuesClamped() {
        VaultSummary summary = new VaultSummary(1, null, null, null, false, false, -1, -5, -3, -2);
        assertEquals(0, summary.capacity());
        assertEquals(0, summary.occupiedVisibleSlots());
        assertEquals(0, summary.totalStoredSlots());
        assertEquals(0, summary.overflowSlots());
    }

    @Test
    void zeroVaultIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new VaultSummary(0, null, null, null, false, false, 9, 0, 0, 0));
    }
}

package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DepositMatchingResultTest {

    @Test
    void empty() {
        DepositMatchingResult result = DepositMatchingResult.empty();
        assertFalse(result.hasMovedItems());
        assertEquals(0, result.movedItemCount());
        assertEquals(0, result.affectedStackCount());
        assertEquals(0, result.skippedItemCount());
        assertFalse(result.vaultWasFull());
    }

    @Test
    void noSpace() {
        DepositMatchingResult result = DepositMatchingResult.noSpace();
        assertFalse(result.hasMovedItems());
        assertTrue(result.vaultWasFull());
    }

    @Test
    void noSimilar() {
        DepositMatchingResult result = DepositMatchingResult.noSimilar();
        assertFalse(result.hasMovedItems());
        assertFalse(result.vaultWasFull());
    }

    @Test
    void withMovedItems() {
        DepositMatchingResult result = new DepositMatchingResult(10, 3, 0, false);
        assertTrue(result.hasMovedItems());
        assertEquals(10, result.movedItemCount());
        assertEquals(3, result.affectedStackCount());
    }
}

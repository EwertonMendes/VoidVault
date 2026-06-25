package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SortResultTest {

    @Test
    void empty() {
        SortResult result = SortResult.empty();
        assertEquals(0, result.sortedSlotCount());
        assertFalse(result.changed());
    }

    @Test
    void withChanges() {
        SortResult result = new SortResult(5, true);
        assertEquals(5, result.sortedSlotCount());
        assertTrue(result.changed());
    }
}

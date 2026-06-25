package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SavedItemTest {

    @Test
    void validItem() {
        SavedItem item = new SavedItem("Resource_Bar_Adamantite", 10, null, 100.0);
        assertTrue(item.isValid());
        assertEquals("Resource_Bar_Adamantite", item.id);
        assertEquals(10, item.amount);
    }

    @Test
    void nullIdInvalid() {
        SavedItem item = new SavedItem(null, 10, null, 0);
        assertFalse(item.isValid());
    }

    @Test
    void blankIdInvalid() {
        SavedItem item = new SavedItem("  ", 10, null, 0);
        assertFalse(item.isValid());
    }

    @Test
    void zeroAmountInvalid() {
        SavedItem item = new SavedItem("Resource_Bar_Adamantite", 0, null, 0);
        assertFalse(item.isValid());
    }

    @Test
    void negativeAmountInvalid() {
        SavedItem item = new SavedItem("Resource_Bar_Adamantite", -1, null, 0);
        assertFalse(item.isValid());
    }

    @Test
    void defaultConstructor() {
        SavedItem item = new SavedItem();
        assertFalse(item.isValid());
    }
}

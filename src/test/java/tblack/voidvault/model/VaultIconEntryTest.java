package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultIconEntryTest {

    @Test
    void trimsFieldsAndKeepsLocalizedName() {
        VaultIconEntry entry = new VaultIconEntry(
                " Food_Bread ",
                " items.food_bread.name ",
                " Pão ",
                "pao bread"
        );
        assertEquals("Food_Bread", entry.itemId());
        assertEquals("items.food_bread.name", entry.translationKey());
        assertEquals("Pão", entry.displayName());
        assertEquals("pao bread", entry.searchText());
        assertTrue(entry.hasDisplayName());
    }

    @Test
    void missingDisplayNameProducesEmptySearchText() {
        VaultIconEntry entry = new VaultIconEntry("Food_Bread", "items.food_bread.name", null, null);
        assertNull(entry.displayName());
        assertEquals("", entry.searchText());
        assertFalse(entry.hasDisplayName());
    }

    @Test
    void rejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class, () -> new VaultIconEntry(" ", null, null, null));
    }
}

package tblack.voidvault.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultIconTest {

    @Test
    void exposesStableControlledCatalogue() {
        assertEquals(
                List.of("default", "minerals", "building", "equipment", "food", "potions", "resources", "valuable", "farming", "misc"),
                List.copyOf(VaultIcon.getAllIds())
        );
    }

    @Test
    void validatesKnownAndUnknownIds() {
        assertTrue(VaultIcon.isValidId("default"));
        assertTrue(VaultIcon.isValidId("minerals"));
        assertFalse(VaultIcon.isValidId("nonexistent"));
        assertFalse(VaultIcon.isValidId(null));
    }

    @Test
    void normalizesInvalidIdsToDefault() {
        assertEquals(VaultIcon.DEFAULT_ID, VaultIcon.normalize(null));
        assertEquals(VaultIcon.DEFAULT_ID, VaultIcon.normalize("nonexistent"));
        assertEquals("food", VaultIcon.normalize("food"));
    }

    @Test
    void returnsSafeTextBadgesWithoutDependingOnGameItemAssets() {
        assertEquals("VV", VaultIcon.shortLabel(null));
        assertEquals("ORE", VaultIcon.shortLabel("minerals"));
        assertEquals("GEAR", VaultIcon.shortLabel("equipment"));
        assertEquals("VV", VaultIcon.shortLabel("unknown"));
    }
}

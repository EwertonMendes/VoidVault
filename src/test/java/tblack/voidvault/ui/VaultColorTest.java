package tblack.voidvault.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultColorTest {

    @Test
    void defaultColor() {
        assertEquals("void", VaultColor.DEFAULT_ID);
    }

    @Test
    void exposesExpandedPresetPalette() {
        assertEquals(25, VaultColor.getAllIds().size());
        assertTrue(VaultColor.isValidId("void"));
        assertTrue(VaultColor.isValidId("lavender"));
        assertTrue(VaultColor.isValidId("magenta"));
        assertTrue(VaultColor.isValidId("amber"));
        assertTrue(VaultColor.isValidId("emerald"));
        assertTrue(VaultColor.isValidId("sky"));
        assertTrue(VaultColor.isValidId("navy"));
        assertTrue(VaultColor.isValidId("silver"));
    }

    @Test
    void normalizesCustomHexColors() {
        assertEquals("#12ABEF", VaultColor.normalizeCustomHex("12abef"));
        assertEquals("#AABBCC", VaultColor.normalizeCustomHex("#abc"));
        assertEquals("#112233", VaultColor.normalizeCustomHex("#112233FF"));
        assertTrue(VaultColor.isCustomColor("#12ABEF"));
        assertTrue(VaultColor.isValidId("#12abef"));
    }

    @Test
    void rejectsInvalidColorValues() {
        assertFalse(VaultColor.isValidId("nonexistent"));
        assertFalse(VaultColor.isValidId("#12GG45"));
        assertFalse(VaultColor.isValidId(null));
        assertNull(VaultColor.normalizeCustomHex("#12GG45"));
    }

    @Test
    void getReturnsCorrectPresetEntry() {
        VaultColor.Entry entry = VaultColor.get("blue");
        assertEquals("#3498DB", entry.mainColor());
        assertEquals("#54B8FB", entry.hoverColor());
        assertEquals("#2478AB", entry.accentColor());
    }

    @Test
    void customColorDerivesStableHoverAndAccentColors() {
        VaultColor.Entry entry = VaultColor.get("#336699");
        assertEquals("#336699", entry.mainColor());
        assertEquals("#5882AB", entry.hoverColor());
        assertEquals("#285077", entry.accentColor());
    }

    @Test
    void nullAndInvalidReturnDefault() {
        assertEquals(VaultColor.get(VaultColor.DEFAULT_ID), VaultColor.get(null));
        assertEquals(VaultColor.get(VaultColor.DEFAULT_ID), VaultColor.get("nonexistent"));
    }
}

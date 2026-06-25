package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultMetadataTest {

    private static final UUID UUID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void emptyMetadata() {
        VaultMetadata meta = VaultMetadata.empty(UUID_1, 1);
        assertEquals(UUID_1, meta.ownerUuid());
        assertEquals(1, meta.vaultId());
        assertNull(meta.displayName());
        assertNull(meta.iconId());
        assertNull(meta.colorId());
        assertFalse(meta.favorite());
        assertFalse(meta.defaultVault());
    }

    @Test
    void customValuesMakeMetadataNonEmpty() {
        assertFalse(VaultMetadata.empty(UUID_1, 1).withDisplayName("My Vault").isEffectivelyEmpty());
        assertFalse(VaultMetadata.empty(UUID_1, 1).withFavorite(true).isEffectivelyEmpty());
        assertFalse(VaultMetadata.empty(UUID_1, 1).withDefault(true).isEffectivelyEmpty());
        assertFalse(VaultMetadata.empty(UUID_1, 1).withIcon("Food_Bread").isEffectivelyEmpty());
        assertFalse(VaultMetadata.empty(UUID_1, 1).withColor("blue").isEffectivelyEmpty());
    }

    @Test
    void arbitraryRegistryItemIdIsPreservedUntilRuntimeValidation() {
        VaultMetadata meta = new VaultMetadata(UUID_1, 1, null, "Weapon_Sword_Iron", null, false, false);
        assertEquals("Weapon_Sword_Iron", meta.iconId());
    }

    @Test
    void blankIconBecomesNull() {
        VaultMetadata meta = new VaultMetadata(UUID_1, 1, null, "  ", null, false, false);
        assertNull(meta.iconId());
    }

    @Test
    void iconControlCharactersAreRemoved() {
        VaultMetadata meta = new VaultMetadata(UUID_1, 1, null, "Food_\nBread", null, false, false);
        assertEquals("Food_Bread", meta.iconId());
    }

    @Test
    void invalidColorNormalizedToNull() {
        VaultMetadata meta = new VaultMetadata(UUID_1, 1, null, null, "nonexistent_color", false, false);
        assertNull(meta.colorId());
    }


    @Test
    void customHexColorIsNormalizedAndPreserved() {
        VaultMetadata meta = new VaultMetadata(UUID_1, 1, null, null, "12abef", false, false);
        assertEquals("#12ABEF", meta.colorId());
    }

    @Test
    void withMethods() {
        VaultMetadata meta = VaultMetadata.empty(UUID_1, 1)
                .withDisplayName("Test")
                .withIcon("Food_Bread")
                .withColor("blue")
                .withFavorite(true)
                .withDefault(true);

        assertEquals("Test", meta.displayName());
        assertEquals("Food_Bread", meta.iconId());
        assertEquals("blue", meta.colorId());
        assertTrue(meta.favorite());
        assertTrue(meta.defaultVault());
    }

    @Test
    void validatesOwnerAndVaultId() {
        assertThrows(IllegalArgumentException.class, () -> new VaultMetadata(null, 1, null, null, null, false, false));
        assertThrows(IllegalArgumentException.class, () -> new VaultMetadata(UUID_1, 0, null, null, null, false, false));
    }
}

package tblack.voidvault.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultKeyTest {

    @Test
    void validKey() {
        UUID uuid = UUID.randomUUID();
        VaultKey key = new VaultKey(uuid, 1);
        assertEquals(uuid, key.ownerUuid());
        assertEquals(1, key.vaultId());
    }

    @Test
    void nullUuidThrows() {
        assertThrows(IllegalArgumentException.class, () -> new VaultKey(null, 1));
    }

    @Test
    void zeroVaultIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new VaultKey(UUID.randomUUID(), 0));
    }

    @Test
    void negativeVaultIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new VaultKey(UUID.randomUUID(), -1));
    }

    @Test
    void equality() {
        UUID uuid = UUID.randomUUID();
        VaultKey a = new VaultKey(uuid, 1);
        VaultKey b = new VaultKey(uuid, 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityDifferentVaultId() {
        UUID uuid = UUID.randomUUID();
        VaultKey a = new VaultKey(uuid, 1);
        VaultKey b = new VaultKey(uuid, 2);
        assertNotEquals(a, b);
    }
}

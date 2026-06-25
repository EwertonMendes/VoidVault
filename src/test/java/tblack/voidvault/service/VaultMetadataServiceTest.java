package tblack.voidvault.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.storage.DatabaseService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultMetadataServiceTest {

    private DatabaseService database;
    private VaultMetadataService service;
    private Path tempDbPath;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        database = new DatabaseService();
        tempDbPath = Files.createTempFile("voidvault-meta-test-", ".db");
        Files.delete(tempDbPath);

        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "sqlite";
        config.database.file = tempDbPath.toString();
        database.connect(config);

        service = new VaultMetadataService(database, testIconCatalog());
    }

    @AfterEach
    void tearDown() {
        database.close();
        try {
            Files.deleteIfExists(tempDbPath);
            Files.deleteIfExists(Path.of(tempDbPath + "-wal"));
            Files.deleteIfExists(Path.of(tempDbPath + "-shm"));
        } catch (IOException ignored) {
        }
    }

    @Test
    void getReturnsEmptyForNewVault() {
        UUID uuid = UUID.randomUUID();
        VaultMetadata meta = service.get(uuid, 1);
        assertNotNull(meta);
        assertNull(meta.displayName());
    }

    @Test
    void getReturnsEmptyForNullUuid() {
        assertThrows(IllegalArgumentException.class, () -> service.get(null, 1));
    }

    @Test
    void getReturnsEmptyForInvalidVaultId() {
        UUID uuid = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.get(uuid, 0));
    }

    @Test
    void getAllReturnsEmptyForNullUuid() {
        Map<Integer, VaultMetadata> all = service.getAll(null);
        assertTrue(all.isEmpty());
    }

    @Test
    void setNameAndRetrieve() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setName(uuid, 1, "My Vault"));

        VaultMetadata meta = service.get(uuid, 1);
        assertEquals("My Vault", meta.displayName());
    }

    @Test
    void setNameNullUuidReturnsFalse() {
        assertFalse(service.setName(null, 1, "Test"));
    }

    @Test
    void setNameInvalidVaultIdReturnsFalse() {
        UUID uuid = UUID.randomUUID();
        assertFalse(service.setName(uuid, 0, "Test"));
    }

    @Test
    void setIconAndRetrieve() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setIcon(uuid, 1, "Food_Bread"));

        VaultMetadata meta = service.get(uuid, 1);
        assertEquals("Food_Bread", meta.iconId());
    }

    @Test
    void setIconRejectsUnknownRegistryId() {
        UUID uuid = UUID.randomUUID();
        assertFalse(service.setIcon(uuid, 1, "nonexistent"));

        VaultMetadata meta = service.get(uuid, 1);
        assertNull(meta.iconId());
    }

    @Test
    void setIconNullRestoresDefault() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setIcon(uuid, 1, "Food_Bread"));
        assertTrue(service.setIcon(uuid, 1, null));
        assertNull(service.get(uuid, 1).iconId());
    }

    @Test
    void setColorAndRetrieve() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setColor(uuid, 1, "blue"));

        VaultMetadata meta = service.get(uuid, 1);
        assertEquals("blue", meta.colorId());
    }

    @Test
    void setCustomHexColorAndRetrieve() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setColor(uuid, 1, "12abef"));
        assertEquals("#12ABEF", service.get(uuid, 1).colorId());
    }

    @Test
    void setColorRejectsInvalidValueWithoutClearingExistingColor() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setColor(uuid, 1, "blue"));
        assertFalse(service.setColor(uuid, 1, "not-a-color"));
        assertEquals("blue", service.get(uuid, 1).colorId());
    }

    @Test
    void setFavoriteAndRetrieve() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setFavorite(uuid, 1, true));

        VaultMetadata meta = service.get(uuid, 1);
        assertTrue(meta.favorite());
    }

    @Test
    void setDefaultVault() {
        UUID uuid = UUID.randomUUID();
        assertTrue(service.setDefault(uuid, 1));

        VaultMetadata meta = service.get(uuid, 1);
        assertTrue(meta.defaultVault());
    }

    @Test
    void getDefaultVaultIdReturnsDefault() {
        UUID uuid = UUID.randomUUID();
        int defaultId = service.getDefaultVaultId(uuid);
        assertEquals(DatabaseService.PRIMARY_VAULT_ID, defaultId);
    }

    @Test
    void getDefaultVaultIdAfterSet() {
        UUID uuid = UUID.randomUUID();
        service.setDefault(uuid, 3);

        int defaultId = service.getDefaultVaultId(uuid);
        assertEquals(3, defaultId);
    }

    @Test
    void getDefaultVaultIdNullUuidReturnsPrimary() {
        assertEquals(DatabaseService.PRIMARY_VAULT_ID, service.getDefaultVaultId(null));
    }

    private VaultIconCatalog testIconCatalog() {
        return new VaultIconCatalog() {
            private final java.util.Set<String> ids = java.util.Set.of("VoidVault", "Food_Bread", "Weapon_Sword_Iron");

            @Override
            public boolean isValidItemId(String itemId) {
                return itemId != null && ids.contains(itemId);
            }

            @Override
            public String resolveItemId(String storedItemId) {
                return isValidItemId(storedItemId) ? storedItemId : "VoidVault";
            }

            @Override
            public java.util.List<tblack.voidvault.model.VaultIconEntry> search(String query, String locale) {
                return ids.stream()
                        .sorted()
                        .map(id -> new tblack.voidvault.model.VaultIconEntry(id, "items." + id + ".name", id, id))
                        .toList();
            }

            @Override
            public tblack.voidvault.model.VaultIconEntry describe(String itemId, String locale) {
                return isValidItemId(itemId)
                        ? new tblack.voidvault.model.VaultIconEntry(itemId, "items." + itemId + ".name", itemId, itemId)
                        : null;
            }

            @Override
            public void invalidate() {
            }
        };
    }
}

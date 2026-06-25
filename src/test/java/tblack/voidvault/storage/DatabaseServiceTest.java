package tblack.voidvault.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.VaultKey;
import tblack.voidvault.model.VaultMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServiceTest {

    private DatabaseService database;
    private Path tempDbPath;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        database = new DatabaseService();
        tempDbPath = Files.createTempFile("voidvault-test-", ".db");
        Files.delete(tempDbPath);

        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "sqlite";
        config.database.file = tempDbPath.toString();
        database.connect(config);
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
    void connectAndIsConnected() {
        assertTrue(database.isConnected());
    }

    @Test
    void closeAndIsConnected() {
        database.close();
        assertFalse(database.isConnected());
    }

    @Test
    void createTablesIdempotent() throws SQLException, IOException {
        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "sqlite";
        config.database.file = tempDbPath.toString();
        database.connect(config);
        assertTrue(database.isConnected());
    }

    @Test
    void saveAndGetInventory() throws SQLException {
        UUID uuid = UUID.randomUUID();
        String data = "{\"0\":{\"id\":\"Item_A\",\"amount\":5,\"metadata\":null,\"durability\":0.0}}";
        database.saveInventory(uuid, 1, data);

        String retrieved = database.getInventory(uuid, 1);
        assertEquals(data, retrieved);
    }

    @Test
    void saveAndGetPrimaryVault() throws SQLException {
        UUID uuid = UUID.randomUUID();
        String data = "{\"0\":{\"id\":\"Item_A\",\"amount\":5}}";
        database.saveInventory(uuid, data);

        String retrieved = database.getInventory(uuid);
        assertEquals(data, retrieved);
    }

    @Test
    void existsAfterSave() throws SQLException {
        UUID uuid = UUID.randomUUID();
        assertFalse(database.exists(uuid));

        database.saveInventory(uuid, 1, "{}");
        assertTrue(database.exists(uuid));
    }

    @Test
    void saveMetadataAndGet() throws SQLException {
        UUID uuid = UUID.randomUUID();
        VaultMetadata meta = new VaultMetadata(uuid, 1, "My Vault", "minerals", "blue", true, false);
        database.saveVaultMetadata(meta);

        VaultMetadata retrieved = database.getVaultMetadata(uuid, 1);
        assertEquals("My Vault", retrieved.displayName());
        assertEquals("minerals", retrieved.iconId());
        assertEquals("blue", retrieved.colorId());
        assertTrue(retrieved.favorite());
        assertFalse(retrieved.defaultVault());
    }

    @Test
    void saveMetadataUpserts() throws SQLException {
        UUID uuid = UUID.randomUUID();
        VaultMetadata meta1 = new VaultMetadata(uuid, 1, "First", null, null, false, false);
        database.saveVaultMetadata(meta1);

        VaultMetadata meta2 = new VaultMetadata(uuid, 1, "Second", "minerals", null, true, false);
        database.saveVaultMetadata(meta2);

        VaultMetadata retrieved = database.getVaultMetadata(uuid, 1);
        assertEquals("Second", retrieved.displayName());
        assertEquals("minerals", retrieved.iconId());
        assertTrue(retrieved.favorite());
    }

    @Test
    void getAllVaultMetadata() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, "Vault 1", null, null, false, false));
        database.saveVaultMetadata(new VaultMetadata(uuid, 2, "Vault 2", "minerals", null, true, false));

        Map<Integer, VaultMetadata> all = database.getAllVaultMetadata(uuid);
        assertEquals(2, all.size());
        assertEquals("Vault 1", all.get(1).displayName());
        assertEquals("Vault 2", all.get(2).displayName());
    }

    @Test
    void setDefaultVault() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));
        database.saveVaultMetadata(new VaultMetadata(uuid, 2, null, null, null, false, false));

        database.setDefaultVault(uuid, 2);

        VaultMetadata meta1 = database.getVaultMetadata(uuid, 1);
        VaultMetadata meta2 = database.getVaultMetadata(uuid, 2);
        assertFalse(meta1.defaultVault());
        assertTrue(meta2.defaultVault());
    }

    @Test
    void setDefaultVaultClearsPrevious() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.setDefaultVault(uuid, 1);
        database.setDefaultVault(uuid, 2);

        VaultMetadata meta1 = database.getVaultMetadata(uuid, 1);
        VaultMetadata meta2 = database.getVaultMetadata(uuid, 2);
        assertFalse(meta1.defaultVault());
        assertTrue(meta2.defaultVault());
    }

    @Test
    void setFavorite() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));

        database.setFavorite(uuid, 1, true);
        VaultMetadata meta = database.getVaultMetadata(uuid, 1);
        assertTrue(meta.favorite());

        database.setFavorite(uuid, 1, false);
        meta = database.getVaultMetadata(uuid, 1);
        assertFalse(meta.favorite());
    }

    @Test
    void setIcon() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));

        database.setIcon(uuid, 1, "minerals");
        VaultMetadata meta = database.getVaultMetadata(uuid, 1);
        assertEquals("minerals", meta.iconId());
    }

    @Test
    void setColor() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));

        database.setColor(uuid, 1, "blue");
        VaultMetadata meta = database.getVaultMetadata(uuid, 1);
        assertEquals("blue", meta.colorId());
    }

    @Test
    void setVaultName() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));

        database.setVaultName(uuid, 1, "My Vault");
        String name = database.getVaultName(uuid, 1);
        assertEquals("My Vault", name);
    }

    @Test
    void setVaultNameNull() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, "Old Name", null, null, false, false));

        database.setVaultName(uuid, 1, null);
        VaultMetadata meta = database.getVaultMetadata(uuid, 1);
        assertNull(meta.displayName());
    }

    @Test
    void getStoredVaultIds() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveInventory(uuid, 1, "{}");
        database.saveInventory(uuid, 3, "{}");

        var ids = database.getStoredVaultIds(uuid);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1));
        assertTrue(ids.contains(3));
    }

    @Test
    void getInventoriesBatch() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveInventory(uuid, 1, "{\"0\":{\"id\":\"A\",\"amount\":1}}");
        database.saveInventory(uuid, 2, "{\"0\":{\"id\":\"B\",\"amount\":2}}");
        database.saveInventory(uuid, 3, "{\"0\":{\"id\":\"C\",\"amount\":3}}");

        Map<Integer, String> batch = database.getInventoriesBatch(uuid, java.util.List.of(1, 3));
        assertEquals(2, batch.size());
        assertTrue(batch.containsKey(1));
        assertTrue(batch.containsKey(3));
        assertFalse(batch.containsKey(2));
    }

    @Test
    void invalidVaultIdThrows() throws SQLException {
        UUID uuid = UUID.randomUUID();
        assertThrows(SQLException.class, () -> database.getInventory(uuid, 0));
        assertThrows(SQLException.class, () -> database.getInventory(uuid, -1));
    }

    @Test
    void invalidDatabaseTypeThrows() {
        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "mysql";
        config.database.file = tempDbPath.toString();
        assertThrows(SQLException.class, () -> database.connect(config));
    }

    @Test
    void schemaVersion3() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, null, null, null, false, false));

        var meta = database.getVaultMetadata(uuid, 1);
        assertNotNull(meta);
        assertNull(meta.iconId());
        assertNull(meta.colorId());
        assertFalse(meta.favorite());
        assertFalse(meta.defaultVault());
    }
}

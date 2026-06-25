package tblack.voidvault.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.VaultMetadata;
import tblack.voidvault.model.VaultSummary;
import tblack.voidvault.permissions.PermissionService;
import tblack.voidvault.storage.DatabaseService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SqlSourceToSinkFlow")
class VaultSummaryServiceTest {

    private DatabaseService database;
    private PermissionService permissionService;
    private VaultMetadataService metadataService;
    private VaultSummaryService service;
    private Path tempDbPath;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        database = new DatabaseService();
        tempDbPath = Files.createTempFile("voidvault-summary-test-", ".db");
        Files.delete(tempDbPath);

        VoidVaultConfig config = new VoidVaultConfig();
        config.database.type = "sqlite";
        config.database.file = tempDbPath.toString();
        config.multiVaults.enabled = true;
        config.multiVaults.defaultVaults = 3;
        database.connect(config);

        permissionService = new PermissionService(config);
        metadataService = new VaultMetadataService(database, testIconCatalog());
        service = new VaultSummaryService(database, permissionService, metadataService);
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
    void buildSummariesEmptyUuid() {
        List<VaultSummary> summaries = service.buildSummaries(null);
        assertTrue(summaries.isEmpty());
    }

    @Test
    void buildSummariesNoData() {
        UUID uuid = UUID.randomUUID();
        List<VaultSummary> summaries = service.buildSummaries(uuid);
        assertFalse(summaries.isEmpty());
        assertEquals(3, summaries.size());
    }

    @Test
    void buildSummariesSortsDefaultFirst() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, "V1", null, null, false, false));
        database.saveVaultMetadata(new VaultMetadata(uuid, 2, "V2", null, null, false, true));

        List<VaultSummary> summaries = service.buildSummaries(uuid);
        assertTrue(summaries.size() >= 2);
        assertTrue(summaries.get(0).defaultVault());
    }

    @Test
    void buildSummariesSortsFavoriteSecond() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, "V1", null, null, true, false));
        database.saveVaultMetadata(new VaultMetadata(uuid, 2, "V2", null, null, false, false));
        database.saveVaultMetadata(new VaultMetadata(uuid, 3, "V3", null, null, false, false));

        List<VaultSummary> summaries = service.buildSummaries(uuid);
        assertEquals(3, summaries.size());
        assertTrue(summaries.get(0).favorite());
    }

    @Test
    void buildSummariesWithInventoryData() throws SQLException {
        UUID uuid = UUID.randomUUID();
        String data = "{\"0\":{\"id\":\"Item_A\",\"amount\":5,\"metadata\":null,\"durability\":0.0}}";
        database.saveInventory(uuid, 1, data);

        List<VaultSummary> summaries = service.buildSummaries(uuid);
        assertFalse(summaries.isEmpty());

        VaultSummary vault1 = summaries.stream()
                .filter(s -> s.vaultId() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(vault1);
        assertEquals(1, vault1.totalStoredSlots());
        assertEquals(1, vault1.occupiedVisibleSlots());
        assertEquals(0, vault1.overflowSlots());
    }

    @Test
    void buildSummariesWithOverflow() throws SQLException {
        UUID uuid = UUID.randomUUID();
        String data = "{\"0\":{\"id\":\"A\",\"amount\":1},\"9\":{\"id\":\"B\",\"amount\":1}}";
        database.saveInventory(uuid, 1, data);

        List<VaultSummary> summaries = service.buildSummaries(uuid);
        VaultSummary vault1 = summaries.stream()
                .filter(s -> s.vaultId() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(vault1);
        assertEquals(2, vault1.totalStoredSlots());
        assertEquals(1, vault1.occupiedVisibleSlots());
        assertEquals(1, vault1.overflowSlots());
        assertTrue(vault1.hasOverflow());
    }

    @Test
    void buildSummariesWithMetadata() throws SQLException {
        UUID uuid = UUID.randomUUID();
        database.saveVaultMetadata(new VaultMetadata(uuid, 1, "Custom Vault", "Food_Bread", "blue", true, false));

        List<VaultSummary> summaries = service.buildSummaries(uuid);
        VaultSummary vault1 = summaries.stream()
                .filter(s -> s.vaultId() == 1)
                .findFirst()
                .orElse(null);
        assertNotNull(vault1);
        assertEquals("Custom Vault", vault1.displayName());
        assertEquals("Food_Bread", vault1.iconId());
        assertEquals("blue", vault1.colorId());
        assertTrue(vault1.favorite());
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

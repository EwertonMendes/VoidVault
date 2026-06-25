package tblack.voidvault.storage;

import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.VaultKey;
import tblack.voidvault.model.VaultMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DatabaseService {
    public static final int PRIMARY_VAULT_ID = 1;

    private Connection connection;
    private VoidVaultConfig config;
    private Path connectedPath;

    public synchronized void connect(VoidVaultConfig config) throws SQLException, IOException {
        this.config = config;
        validateDatabaseType(config);

        Path dbPath = Path.of(config.database.file).normalize();
        if (isConnectedTo(dbPath)) {
            createTables();
            migrateLegacyVaults();
            migrateSchemaV3();
            return;
        }

        close();
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC Driver not found. Make sure the shadow jar contains sqlite-jdbc.", exception);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connectedPath = dbPath;
        applyPragmas();
        createTables();
        migrateLegacyVaults();
        migrateSchemaV3();
    }

    private void validateDatabaseType(VoidVaultConfig config) throws SQLException {
        if ("sqlite".equalsIgnoreCase(config.database.type)) {
            return;
        }
        throw new SQLException("VoidVault only supports SQLite. Current type: " + config.database.type);
    }

    private boolean isConnectedTo(Path dbPath) {
        if (!isConnected()) {
            return false;
        }
        return Objects.equals(connectedPath, dbPath);
    }

    private void applyPragmas() throws SQLException {
        ensureConnected();
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void createTables() throws SQLException {
        ensureConnected();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS void_vaults (" +
                    "uuid TEXT PRIMARY KEY," +
                    "inventory_data TEXT NOT NULL," +
                    "source TEXT DEFAULT 'voidvault'," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");
            statement.execute("CREATE TABLE IF NOT EXISTS void_vault_inventories (" +
                    "uuid TEXT NOT NULL," +
                    "vault_id INTEGER NOT NULL," +
                    "inventory_data TEXT NOT NULL," +
                    "source TEXT DEFAULT 'voidvault'," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (uuid, vault_id)" +
                    ");");
            statement.execute("CREATE TABLE IF NOT EXISTS void_vault_imports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "source TEXT NOT NULL," +
                    "source_path TEXT NOT NULL," +
                    "players_found INTEGER NOT NULL," +
                    "players_imported INTEGER NOT NULL," +
                    "total_item_slots INTEGER NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");");
            statement.execute("CREATE TABLE IF NOT EXISTS void_vault_metadata (" +
                    "uuid TEXT NOT NULL," +
                    "vault_id INTEGER NOT NULL," +
                    "display_name TEXT," +
                    "icon_id TEXT," +
                    "color_id TEXT," +
                    "is_favorite INTEGER NOT NULL DEFAULT 0," +
                    "is_default INTEGER NOT NULL DEFAULT 0," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (uuid, vault_id)" +
                    ");");
            statement.execute("CREATE TABLE IF NOT EXISTS void_vault_schema (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT NOT NULL" +
                    ");");
        }
    }

    private void migrateLegacyVaults() throws SQLException {
        ensureConnected();
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT OR IGNORE INTO void_vault_inventories (uuid, vault_id, inventory_data, source, last_updated) " +
                    "SELECT uuid, 1, inventory_data, source, last_updated FROM void_vaults");
        }
        setSchemaValue("legacy_vaults_migrated", "true");
    }

    private void migrateSchemaV3() throws SQLException {
        ensureConnected();
        String currentVersion = getSchemaValue("db_version");
        boolean migrated = !"3".equals(currentVersion);

        try {
            connection.setAutoCommit(false);

            List<String> existingColumns = getColumnNames("void_vault_metadata");
            if (!existingColumns.contains("icon_id")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE void_vault_metadata ADD COLUMN icon_id TEXT");
                }
            }
            if (!existingColumns.contains("color_id")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE void_vault_metadata ADD COLUMN color_id TEXT");
                }
            }
            if (!existingColumns.contains("is_favorite")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE void_vault_metadata ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0");
                }
            }
            if (!existingColumns.contains("is_default")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE void_vault_metadata ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0");
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_void_vault_metadata_default " +
                        "ON void_vault_metadata(uuid) WHERE is_default = 1");
            }

            setSchemaValue("db_version", "3");
            connection.commit();
            if (migrated) {
                System.out.println("[VoidVault] Database migrated to schema version 3");
            }
        } catch (SQLException exception) {
            rollbackQuietly(exception);
            System.err.println("[VoidVault] Schema migration to v3 failed: " + exception.getMessage());
            throw exception;
        } finally {
            restoreAutoCommit();
        }
    }

    private void rollbackQuietly(SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void restoreAutoCommit() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.setAutoCommit(true);
        }
    }

    private List<String> getColumnNames(String tableName) throws SQLException {
        if (tableName == null || !tableName.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Invalid SQLite table name: " + tableName);
        }

        List<String> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (result.next()) {
                columns.add(result.getString("name").toLowerCase(java.util.Locale.ROOT));
            }
        }
        return columns;
    }

    private String getSchemaValue(String key) throws SQLException {
        ensureConnected();
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM void_vault_schema WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("value");
                }
            }
        }
        return null;
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    public synchronized String getInventory(UUID uuid) throws SQLException {
        return getInventory(uuid, PRIMARY_VAULT_ID);
    }

    public synchronized String getInventory(UUID uuid, int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        try (PreparedStatement statement = connection.prepareStatement("SELECT inventory_data FROM void_vault_inventories WHERE uuid = ? AND vault_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("inventory_data");
                }
            }
        }
        return null;
    }

    public synchronized boolean exists(UUID uuid) throws SQLException {
        return exists(uuid, PRIMARY_VAULT_ID);
    }

    public synchronized boolean exists(UUID uuid, int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM void_vault_inventories WHERE uuid = ? AND vault_id = ? LIMIT 1")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public synchronized void saveInventory(UUID uuid, String inventoryData) throws SQLException {
        saveInventory(uuid, PRIMARY_VAULT_ID, inventoryData, "voidvault");
    }

    public synchronized void saveInventory(UUID uuid, String inventoryData, String source) throws SQLException {
        saveInventory(uuid, PRIMARY_VAULT_ID, inventoryData, source);
    }

    public synchronized void saveInventory(UUID uuid, int vaultId, String inventoryData) throws SQLException {
        saveInventory(uuid, vaultId, inventoryData, "voidvault");
    }

    public synchronized void saveInventory(UUID uuid, int vaultId, String inventoryData, String source) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        String normalizedData = inventoryData == null ? "{}" : inventoryData;
        String normalizedSource = source == null ? "voidvault" : source;
        String sql = "INSERT INTO void_vault_inventories (uuid, vault_id, inventory_data, source, last_updated) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET inventory_data = excluded.inventory_data, source = excluded.source, last_updated = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            statement.setString(3, normalizedData);
            statement.setString(4, normalizedSource);
            statement.executeUpdate();
        }

        if (vaultId == PRIMARY_VAULT_ID) {
            saveLegacyInventory(uuid, normalizedData, normalizedSource);
        }
    }

    private void saveLegacyInventory(UUID uuid, String inventoryData, String source) throws SQLException {
        String sql = "INSERT INTO void_vaults (uuid, inventory_data, source, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid) DO UPDATE SET inventory_data = excluded.inventory_data, source = excluded.source, last_updated = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, inventoryData);
            statement.setString(3, source);
            statement.executeUpdate();
        }
    }

    public synchronized Map<UUID, String> getAllInventories() throws SQLException {
        return getAllInventories(PRIMARY_VAULT_ID);
    }

    public synchronized Map<UUID, String> getAllInventories(int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        Map<UUID, String> rows = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, inventory_data FROM void_vault_inventories WHERE vault_id = ? ORDER BY uuid")) {
            statement.setInt(1, vaultId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.put(UUID.fromString(result.getString("uuid")), result.getString("inventory_data"));
                }
            }
        }
        return rows;
    }

    public synchronized List<Integer> getStoredVaultIds(UUID uuid) throws SQLException {
        ensureConnected();
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT vault_id FROM void_vault_inventories WHERE uuid = ? ORDER BY vault_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getInt("vault_id"));
                }
            }
        }
        return ids;
    }

    public synchronized Map<Integer, String> getInventoriesBatch(UUID uuid, List<Integer> vaultIds) throws SQLException {
        ensureConnected();
        Map<Integer, String> results = new LinkedHashMap<>();
        if (vaultIds == null || vaultIds.isEmpty()) return results;

        StringBuilder sb = new StringBuilder("SELECT vault_id, inventory_data FROM void_vault_inventories WHERE uuid = ? AND vault_id IN (");
        for (int i = 0; i < vaultIds.size(); i++) {
            sb.append(i > 0 ? ",?" : "?");
        }
        sb.append(") ORDER BY vault_id");

        try (PreparedStatement statement = connection.prepareStatement(sb.toString())) {
            statement.setString(1, uuid.toString());
            for (int i = 0; i < vaultIds.size(); i++) {
                statement.setInt(i + 2, vaultIds.get(i));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    results.put(result.getInt("vault_id"), result.getString("inventory_data"));
                }
            }
        }
        return results;
    }

    public synchronized VaultMetadata getVaultMetadata(UUID uuid, int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT display_name, icon_id, color_id, is_favorite, is_default FROM void_vault_metadata WHERE uuid = ? AND vault_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return readMetadata(uuid, vaultId, result);
                }
            }
        }
        return VaultMetadata.empty(uuid, vaultId);
    }

    public synchronized Map<Integer, VaultMetadata> getAllVaultMetadata(UUID uuid) throws SQLException {
        ensureConnected();
        Map<Integer, VaultMetadata> map = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vault_id, display_name, icon_id, color_id, is_favorite, is_default FROM void_vault_metadata WHERE uuid = ? ORDER BY vault_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int vaultId = result.getInt("vault_id");
                    map.put(vaultId, readMetadata(uuid, vaultId, result));
                }
            }
        }
        return map;
    }

    private VaultMetadata readMetadata(UUID uuid, int vaultId, ResultSet result) throws SQLException {
        String displayName = result.getString("display_name");
        String iconId = result.getString("icon_id");
        String colorId = result.getString("color_id");
        boolean favorite = result.getInt("is_favorite") == 1;
        boolean defaultVault = result.getInt("is_default") == 1;
        return new VaultMetadata(uuid, vaultId, displayName, iconId, colorId, favorite, defaultVault);
    }

    public synchronized void saveVaultMetadata(VaultMetadata metadata) throws SQLException {
        ensureConnected();
        validateVaultId(metadata.vaultId());
        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, display_name, icon_id, color_id, is_favorite, is_default, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET " +
                "display_name = excluded.display_name, " +
                "icon_id = excluded.icon_id, " +
                "color_id = excluded.color_id, " +
                "is_favorite = excluded.is_favorite, " +
                "is_default = excluded.is_default, " +
                "updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, metadata.ownerUuid().toString());
            statement.setInt(2, metadata.vaultId());
            setNullableString(statement, 3, metadata.displayName());
            setNullableString(statement, 4, metadata.iconId());
            setNullableString(statement, 5, metadata.colorId());
            statement.setInt(6, metadata.favorite() ? 1 : 0);
            statement.setInt(7, metadata.defaultVault() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public synchronized void setDefaultVault(UUID uuid, int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement clear = connection.prepareStatement(
                    "UPDATE void_vault_metadata SET is_default = 0 WHERE uuid = ? AND is_default = 1")) {
                clear.setString(1, uuid.toString());
                clear.executeUpdate();
            }

            String upsert = "INSERT INTO void_vault_metadata (uuid, vault_id, is_default, updated_at) VALUES (?, ?, 1, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT(uuid, vault_id) DO UPDATE SET is_default = 1, updated_at = CURRENT_TIMESTAMP";
            try (PreparedStatement set = connection.prepareStatement(upsert)) {
                set.setString(1, uuid.toString());
                set.setInt(2, vaultId);
                set.executeUpdate();
            }

            connection.commit();
        } catch (SQLException exception) {
            rollbackQuietly(exception);
            throw exception;
        } finally {
            restoreAutoCommit();
        }
    }

    public synchronized void setFavorite(UUID uuid, int vaultId, boolean favorite) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, is_favorite, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET is_favorite = excluded.is_favorite, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            statement.setInt(3, favorite ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public synchronized void setIcon(UUID uuid, int vaultId, String iconId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, icon_id, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET icon_id = excluded.icon_id, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            setNullableString(statement, 3, iconId);
            statement.executeUpdate();
        }
    }

    public synchronized void setColor(UUID uuid, int vaultId, String colorId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, color_id, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET color_id = excluded.color_id, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            setNullableString(statement, 3, colorId);
            statement.executeUpdate();
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    public synchronized String getVaultName(UUID uuid, int vaultId) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        try (PreparedStatement statement = connection.prepareStatement("SELECT display_name FROM void_vault_metadata WHERE uuid = ? AND vault_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("display_name");
                }
            }
        }
        return null;
    }

    public synchronized Map<Integer, String> getVaultNames(UUID uuid) throws SQLException {
        ensureConnected();
        Map<Integer, String> names = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT vault_id, display_name FROM void_vault_metadata WHERE uuid = ? ORDER BY vault_id")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("display_name");
                    if (name == null || name.isBlank()) continue;
                    names.put(result.getInt("vault_id"), name);
                }
            }
        }
        return names;
    }

    public synchronized void setVaultName(UUID uuid, int vaultId, String displayName) throws SQLException {
        ensureConnected();
        validateVaultId(vaultId);
        String normalized = displayName == null || displayName.isBlank() ? null : displayName;
        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, display_name, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET display_name = excluded.display_name, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            setNullableString(statement, 3, normalized);
            statement.executeUpdate();
        }
    }

    public synchronized void recordImport(String source, String sourcePath, int playersFound, int playersImported, int totalItemSlots) throws SQLException {
        ensureConnected();
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO void_vault_imports (source, source_path, players_found, players_imported, total_item_slots) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, source);
            statement.setString(2, sourcePath);
            statement.setInt(3, playersFound);
            statement.setInt(4, playersImported);
            statement.setInt(5, totalItemSlots);
            statement.executeUpdate();
        }
    }

    public synchronized void close() {
        if (connection == null) {
            connectedPath = null;
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        } finally {
            connection = null;
            connectedPath = null;
        }
    }

    private void setSchemaValue(String key, String value) throws SQLException {
        String sql = "INSERT INTO void_vault_schema (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void validateVaultId(int vaultId) throws SQLException {
        if (vaultId > 0) {
            return;
        }
        throw new SQLException("Invalid vault id: " + vaultId);
    }

    private void ensureConnected() throws SQLException {
        if (isConnected()) {
            return;
        }
        throw new SQLException("VoidVault database is not connected");
    }
}

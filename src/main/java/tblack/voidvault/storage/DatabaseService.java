package tblack.voidvault.storage;

import tblack.voidvault.config.VoidVaultConfig;

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
        setSchemaValue("db_version", "2");
        setSchemaValue("legacy_vaults_migrated", "true");
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
        if (normalized == null) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM void_vault_metadata WHERE uuid = ? AND vault_id = ?")) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, vaultId);
                statement.executeUpdate();
            }
            return;
        }

        String sql = "INSERT INTO void_vault_metadata (uuid, vault_id, display_name, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid, vault_id) DO UPDATE SET display_name = excluded.display_name, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, vaultId);
            statement.setString(3, normalized);
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

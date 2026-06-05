package tblack.voidvault.storage;

import tblack.voidvault.config.VoidVaultConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseService {
    private Connection connection;
    private VoidVaultConfig config;

    public synchronized void connect(VoidVaultConfig config) throws SQLException, IOException {
        this.config = config;
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    return;
                }
            } catch (SQLException ignored) {
            }
        }

        if (!"sqlite".equalsIgnoreCase(config.database.type)) {
            throw new SQLException("VoidVault 1.0 only supports SQLite. Current type: " + config.database.type);
        }

        Path dbPath = Path.of(config.database.file);
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC Driver not found. Make sure the shadow jar contains sqlite-jdbc.", exception);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS void_vaults (" +
                    "uuid TEXT PRIMARY KEY," +
                    "inventory_data TEXT NOT NULL," +
                    "source TEXT DEFAULT 'voidvault'," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
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
        }
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    public synchronized String getInventory(UUID uuid) throws SQLException {
        ensureConnected();
        try (PreparedStatement statement = connection.prepareStatement("SELECT inventory_data FROM void_vaults WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getString("inventory_data");
                }
            }
        }
        return null;
    }

    public synchronized boolean exists(UUID uuid) throws SQLException {
        ensureConnected();
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM void_vaults WHERE uuid = ? LIMIT 1")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public synchronized void saveInventory(UUID uuid, String inventoryData) throws SQLException {
        saveInventory(uuid, inventoryData, "voidvault");
    }

    public synchronized void saveInventory(UUID uuid, String inventoryData, String source) throws SQLException {
        ensureConnected();
        String sql = "INSERT INTO void_vaults (uuid, inventory_data, source, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(uuid) DO UPDATE SET inventory_data = excluded.inventory_data, source = excluded.source, last_updated = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, inventoryData == null ? "{}" : inventoryData);
            statement.setString(3, source == null ? "voidvault" : source);
            statement.executeUpdate();
        }
    }

    public synchronized Map<UUID, String> getAllInventories() throws SQLException {
        ensureConnected();
        Map<UUID, String> rows = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid, inventory_data FROM void_vaults ORDER BY uuid")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.put(UUID.fromString(result.getString("uuid")), result.getString("inventory_data"));
                }
            }
        }
        return rows;
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
        if (connection == null) return;
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void ensureConnected() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("VoidVault database is not connected");
        }
    }
}

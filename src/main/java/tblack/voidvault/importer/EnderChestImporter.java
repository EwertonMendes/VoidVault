package tblack.voidvault.importer;

import com.google.gson.JsonSyntaxException;
import tblack.voidvault.config.ConfigManager;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.model.ImportReport;
import tblack.voidvault.model.SavedItem;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.util.VaultJson;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public class EnderChestImporter {
    private final DatabaseService targetDatabase;
    private VoidVaultConfig config;

    public EnderChestImporter(DatabaseService targetDatabase, VoidVaultConfig config) {
        this.targetDatabase = targetDatabase;
        this.config = config;
    }

    public void reload(VoidVaultConfig config) {
        this.config = config;
    }

    public ImportReport dryRun() {
        return run(true);
    }

    public ImportReport confirm() {
        return run(false);
    }

    private ImportReport run(boolean dryRun) {
        ImportReport report = new ImportReport();
        report.dryRun = dryRun;

        try {
            if (!dryRun && config.importer.createBackupBeforeConfirm) {
                backupTargetDatabase(report);
            }
            importSqlite(report, dryRun);
            importLegacyJsonDirectory(report, dryRun);

            if (!dryRun) {
                targetDatabase.recordImport("kvothe_EnderChest", config.importer.legacyDatabasePath, report.playersFound, report.playersImported, report.totalItemSlots);
            }
            writeReport(report);
        } catch (Exception exception) {
            report.warnings.add("Import failed: " + exception.getMessage());
            exception.printStackTrace();
        }

        return report;
    }

    private void importSqlite(ImportReport report, boolean dryRun) {
        Path dbPath = Path.of(config.importer.legacyDatabasePath);
        if (!Files.exists(dbPath)) {
            report.warnings.add("Legacy SQLite database not found: " + dbPath.toAbsolutePath());
            return;
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            report.warnings.add("SQLite JDBC Driver not found while importing legacy database.");
            return;
        }

        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement statement = source.prepareStatement("SELECT uuid, inventory_data, last_updated FROM ender_chests ORDER BY uuid");
             ResultSet rows = statement.executeQuery()) {

            while (rows.next()) {
                String uuidText = rows.getString("uuid");
                String inventoryData = rows.getString("inventory_data");
                importOne(report, dryRun, uuidText, inventoryData, "enderchest-sqlite");
            }
        } catch (SQLException exception) {
            report.warnings.add("Failed to read legacy SQLite database: " + exception.getMessage());
        }
    }

    private void importLegacyJsonDirectory(ImportReport report, boolean dryRun) {
        Path jsonDir = Path.of(config.importer.legacyJsonDirectory);
        if (!Files.isDirectory(jsonDir)) {
            return;
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(jsonDir, "*.json")) {
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String uuidText = fileName.substring(0, fileName.length() - ".json".length());
                String data = Files.readString(file);
                importOne(report, dryRun, uuidText, data, "enderchest-json");
            }
        } catch (Exception exception) {
            report.warnings.add("Failed to read legacy JSON directory: " + exception.getMessage());
        }
    }

    private void importOne(ImportReport report, boolean dryRun, String uuidText, String inventoryData, String source) {
        report.playersFound++;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (IllegalArgumentException exception) {
            report.invalidRows++;
            report.warnings.add("Invalid UUID skipped: " + uuidText);
            return;
        }

        Map<Integer, SavedItem> items;
        try {
            items = VaultJson.parse(inventoryData);
        } catch (JsonSyntaxException exception) {
            report.invalidRows++;
            report.warnings.add("Invalid JSON skipped for " + uuid + ": " + exception.getMessage());
            return;
        }

        int validItems = 0;
        int maxSlot = -1;
        for (Map.Entry<Integer, SavedItem> entry : items.entrySet()) {
            Integer slot = entry.getKey();
            SavedItem item = entry.getValue();
            if (slot == null || slot < 0 || item == null || !item.isValid()) {
                report.invalidItems++;
                continue;
            }
            validItems++;
            maxSlot = Math.max(maxSlot, slot);
        }

        if (config.importer.skipEmptyVaults && validItems == 0) {
            report.playersSkipped++;
            return;
        }

        if (validItems > 0) {
            report.playersWithItems++;
        }
        report.totalItemSlots += validItems;
        report.maxSlot = Math.max(report.maxSlot, maxSlot);
        if (maxSlot >= config.slots.defaultSlots) {
            report.overflowPlayers++;
        }

        try {
            boolean exists = targetDatabase.exists(uuid);
            String existingInventory = exists ? targetDatabase.getInventory(uuid) : null;
            boolean existingIsEmpty = isEmptyInventory(existingInventory);

            if (exists && !existingIsEmpty && !config.importer.overwriteExisting) {
                report.playersSkipped++;
                report.warnings.add("Skipped existing non-empty VoidVault for " + uuid + " because overwriteExisting=false");
                return;
            }

            if (exists) {
                report.overwrittenPlayers++;
            }

            if (!dryRun) {
                targetDatabase.saveInventory(uuid, VaultJson.stringify(items), source);
            }
            report.playersImported++;
        } catch (SQLException exception) {
            report.invalidRows++;
            report.warnings.add("Failed to import " + uuid + ": " + exception.getMessage());
        }
    }

    private boolean isEmptyInventory(String inventoryData) {
        if (inventoryData == null || inventoryData.isBlank()) {
            return true;
        }

        try {
            return VaultJson.parse(inventoryData).isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private void backupTargetDatabase(ImportReport report) {
        try {
            Path dbPath = Path.of(config.database.file);
            if (!Files.exists(dbPath)) return;

            String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now());
            Path backupDir = ConfigManager.MOD_DIR.resolve("backups").resolve("before-enderchest-import-" + stamp);
            Files.createDirectories(backupDir);
            Files.copy(dbPath, backupDir.resolve(dbPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            report.warnings.add("Could not create database backup: " + exception.getMessage());
        }
    }

    private void writeReport(ImportReport report) {
        try {
            Path reportsDir = ConfigManager.MOD_DIR.resolve("reports");
            Files.createDirectories(reportsDir);
            String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now());
            Path file = reportsDir.resolve((report.dryRun ? "dryrun" : "confirm") + "-enderchest-import-" + stamp + ".txt");
            Files.writeString(file, report.toFileReport());
        } catch (IOException exception) {
            report.warnings.add("Could not write import report: " + exception.getMessage());
        }
    }
}

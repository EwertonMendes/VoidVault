package tblack.voidvault.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Stream;

public class ConfigManager {
    public static final Path MOD_DIR = Paths.get("mods", "VoidVault");
    public static final Path CONFIG_FILE = MOD_DIR.resolve("config.json");
    private static final Path CONFIG_DIR = Paths.get("config", "VoidVault");
    private static final Path CONFIG_DIR_CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final String CONFIG_DEFAULT_DB_FILE = "config/VoidVault/voidvault.db";
    private static final String DEFAULT_DB_FILE = "mods/VoidVault/voidvault.db";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private VoidVaultConfig config;

    public VoidVaultConfig load() {
        try {
            migrateConfigDirectoryBackToMods();
            Files.createDirectories(MOD_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                config = new VoidVaultConfig();
                save();
                return config;
            }

            String json = Files.readString(CONFIG_FILE);
            config = gson.fromJson(json, VoidVaultConfig.class);
            if (config == null) {
                config = new VoidVaultConfig();
            }
            normalize();
            save();
            return config;
        } catch (Exception exception) {
            exception.printStackTrace();
            config = new VoidVaultConfig();
            normalize();
            return config;
        }
    }

    public VoidVaultConfig reload() {
        return load();
    }

    public VoidVaultConfig getConfig() {
        if (config != null) {
            return config;
        }
        return load();
    }

    public void save() throws IOException {
        Files.createDirectories(MOD_DIR);
        Files.writeString(CONFIG_FILE, gson.toJson(config));
    }

    private void normalize() {
        if (config.database == null) config.database = new VoidVaultConfig.Database();
        if (config.commands == null) config.commands = new VoidVaultConfig.Commands();
        if (config.slots == null) config.slots = new VoidVaultConfig.Slots();
        if (config.multiVaults == null) config.multiVaults = new VoidVaultConfig.MultiVaults();
        if (config.crafting == null) config.crafting = new VoidVaultConfig.Crafting();
        if (config.importer == null) config.importer = new VoidVaultConfig.Importer();
        if (config.safety == null) config.safety = new VoidVaultConfig.Safety();
        if (config.organization == null) config.organization = new VoidVaultConfig.Organization();
        if (config.commands.aliases == null) config.commands.aliases = new ArrayList<>();
        if (config.slots.tiers == null) config.slots.tiers = new ArrayList<>();
        if (config.multiVaults.tiers == null) config.multiVaults.tiers = new ArrayList<>();
        if (config.crafting.input == null) config.crafting.input = new ArrayList<>();
        if (config.crafting.benchRequirement == null) config.crafting.benchRequirement = new VoidVaultConfig.BenchRequirement();
        if (config.crafting.benchRequirement.categories == null) config.crafting.benchRequirement.categories = new ArrayList<>();

        normalizeSafety();
        normalizeOrganization();
        config.configVersion = "3";
        normalizeDatabase();
        normalizeCommands();
        normalizeSlots();
        normalizeMultiVaults();
    }

    private void normalizeSafety() {
        if (config.safety.saveDebounceMillis < 100) {
            config.safety.saveDebounceMillis = 100;
        }
        if (config.safety.saveMaxDelayMillis < config.safety.saveDebounceMillis) {
            config.safety.saveMaxDelayMillis = config.safety.saveDebounceMillis;
        }
        if (config.safety.saveMaxDelayMillis > 30000) {
            config.safety.saveMaxDelayMillis = 30000;
        }
    }

    private void normalizeOrganization() {
        if (config.organization == null) {
            config.organization = new VoidVaultConfig.Organization();
        }
    }

    private void normalizeDatabase() {
        if (config.database.file == null || config.database.file.isBlank()) {
            config.database.file = DEFAULT_DB_FILE;
            return;
        }
        if (isConfigDefaultPath(config.database.file)) {
            config.database.file = DEFAULT_DB_FILE;
        }
    }

    private void normalizeCommands() {
        if (config.commands.primary == null || config.commands.primary.isBlank() || config.commands.primary.equalsIgnoreCase("voidvaults")) {
            config.commands.primary = "voidvault";
        }

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add("vv");
        for (String alias : config.commands.aliases) {
            if (alias == null || alias.isBlank()) continue;
            if (alias.equalsIgnoreCase(config.commands.primary)) continue;
            aliases.add(alias);
        }
        aliases.remove("voidvaults");
        config.commands.aliases = new ArrayList<>(aliases);
    }

    private void normalizeSlots() {
        config.slots.defaultSlots = Math.max(1, config.slots.defaultSlots);
        config.slots.maxSlots = Math.max(config.slots.defaultSlots, config.slots.maxSlots);
    }

    private void normalizeMultiVaults() {
        config.multiVaults.defaultVaults = Math.max(1, Math.min(VoidVaultConfig.MAX_VAULTS_LIMIT, config.multiVaults.defaultVaults));
        config.multiVaults.maxVaults = Math.max(config.multiVaults.defaultVaults, Math.min(VoidVaultConfig.MAX_VAULTS_LIMIT, config.multiVaults.maxVaults));
    }

    private void migrateConfigDirectoryBackToMods() throws IOException {
        if (!Files.exists(CONFIG_DIR_CONFIG_FILE)) return;
        if (Files.exists(CONFIG_FILE)) return;

        copyDirectory(CONFIG_DIR, MOD_DIR);
        deleteDirectory(CONFIG_DIR);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean isConfigDefaultPath(String value) {
        return value.replace('\\', '/').equalsIgnoreCase(CONFIG_DEFAULT_DB_FILE);
    }
}

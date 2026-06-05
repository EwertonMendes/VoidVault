package dev.voidvault.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {
    public static final Path MOD_DIR = Paths.get("mods", "VoidVault");
    public static final Path CONFIG_FILE = MOD_DIR.resolve("config.json");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private VoidVaultConfig config;

    public VoidVaultConfig load() {
        try {
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
            return config;
        }
    }

    public VoidVaultConfig reload() {
        return load();
    }

    public VoidVaultConfig getConfig() {
        if (config == null) {
            return load();
        }
        return config;
    }

    public void save() throws IOException {
        Files.createDirectories(MOD_DIR);
        Files.writeString(CONFIG_FILE, gson.toJson(config));
    }

    private void normalize() {
        if (config.database == null) config.database = new VoidVaultConfig.Database();
        if (config.commands == null) config.commands = new VoidVaultConfig.Commands();
        if (config.slots == null) config.slots = new VoidVaultConfig.Slots();
        if (config.crafting == null) config.crafting = new VoidVaultConfig.Crafting();
        if (config.importer == null) config.importer = new VoidVaultConfig.Importer();
        if (config.safety == null) config.safety = new VoidVaultConfig.Safety();
        if (config.commands.aliases == null) config.commands.aliases = new java.util.ArrayList<>();
        if (config.slots.tiers == null) config.slots.tiers = new java.util.ArrayList<>();
        if (config.crafting.input == null) config.crafting.input = new java.util.ArrayList<>();

        if (config.commands.primary == null || config.commands.primary.isBlank() || config.commands.primary.equalsIgnoreCase("voidvaults")) {
            config.commands.primary = "voidvault";
        }

        java.util.LinkedHashSet<String> aliases = new java.util.LinkedHashSet<>();
        aliases.add("vv");
        for (String alias : config.commands.aliases) {
            if (alias == null || alias.isBlank()) continue;
            if (alias.equalsIgnoreCase(config.commands.primary)) continue;
            aliases.add(alias);
        }
        aliases.remove("voidvaults");
        config.commands.aliases = new java.util.ArrayList<>(aliases);
    }
}

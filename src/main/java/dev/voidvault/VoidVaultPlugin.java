package dev.voidvault;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.voidvault.commands.VoidVaultCommand;
import dev.voidvault.config.ConfigManager;
import dev.voidvault.config.VoidVaultConfig;
import dev.voidvault.importer.EnderChestImporter;
import dev.voidvault.permissions.PermissionService;
import dev.voidvault.storage.DatabaseService;
import dev.voidvault.storage.VaultManager;
import dev.voidvault.systems.VoidVaultUseBlockSystem;
import dev.voidvault.util.CraftingRecipeService;

import javax.annotation.Nonnull;

public class VoidVaultPlugin extends JavaPlugin {
    private static VoidVaultPlugin instance;

    private final ConfigManager configManager = new ConfigManager();
    private final DatabaseService databaseService = new DatabaseService();

    private VoidVaultConfig currentConfig;
    private PermissionService permissionService;
    private VaultManager vaultManager;
    private EnderChestImporter enderChestImporter;

    public VoidVaultPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();
        instance = this;

        currentConfig = configManager.load();
        permissionService = new PermissionService(currentConfig);

        try {
            databaseService.connect(currentConfig);
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to connect database: " + exception.getMessage());
            exception.printStackTrace();
        }

        vaultManager = new VaultManager(databaseService, permissionService, currentConfig);
        enderChestImporter = new EnderChestImporter(databaseService, currentConfig);

        getCommandRegistry().registerCommand(new VoidVaultCommand(this, currentConfig));
        getEntityStoreRegistry().registerSystem(new VoidVaultUseBlockSystem(vaultManager));
        CraftingRecipeService.apply(currentConfig);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (currentConfig == null || currentConfig.safety.saveOnShutdown) {
                    vaultManager.saveLoaded();
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            } finally {
                databaseService.close();
            }
        }));

        System.out.println("[VoidVault] Loaded VoidVault " + currentConfig.configVersion);
    }

    public void reloadVoidVault() {
        currentConfig = configManager.reload();
        permissionService.reload(currentConfig);
        enderChestImporter.reload(currentConfig);
        vaultManager.reload(permissionService, currentConfig);
        CraftingRecipeService.apply(currentConfig);
    }

    public static VoidVaultPlugin getInstance() {
        return instance;
    }

    public VoidVaultConfig getCurrentConfig() {
        return currentConfig;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public EnderChestImporter getEnderChestImporter() {
        return enderChestImporter;
    }
}

package tblack.voidvault;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import tblack.voidvault.commands.VoidVaultCommand;
import tblack.voidvault.config.ConfigManager;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.importer.EnderChestImporter;
import tblack.voidvault.permissions.PermissionService;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.storage.VaultManager;
import tblack.voidvault.systems.VoidVaultUseBlockSystem;
import tblack.voidvault.util.CraftingRecipeService;

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
        permissionService.registerPermissions();
        connectDatabase(currentConfig);

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

        System.out.println("[VoidVault] Loaded VoidVault " + getManifest().getVersion());
    }

    public void reloadVoidVault() {
        if (vaultManager != null) {
            vaultManager.saveLoaded();
            vaultManager.discardLoaded();
        }

        currentConfig = configManager.reload();
        connectDatabase(currentConfig);
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

    private void connectDatabase(VoidVaultConfig config) {
        try {
            databaseService.connect(config);
        } catch (Exception exception) {
            System.err.println("[VoidVault] Failed to connect database: " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}

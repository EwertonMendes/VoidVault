package tblack.voidvault.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import tblack.voidvault.VoidVaultPlugin;
import tblack.voidvault.config.VoidVaultConfig;
import tblack.voidvault.i18n.I18n;
import tblack.voidvault.importer.EnderChestImporter;
import tblack.voidvault.model.ImportReport;
import tblack.voidvault.model.VaultInfo;
import tblack.voidvault.storage.DatabaseService;
import tblack.voidvault.util.Chat;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class VoidVaultCommand extends AbstractPlayerCommand {
    private final VoidVaultPlugin plugin;

    public VoidVaultCommand(VoidVaultPlugin plugin, VoidVaultConfig config) {
        super(config.commands.primary, I18n.commandKey("commands.root.description"));
        this.plugin = plugin;

        if (config.commands.aliases != null && !config.commands.aliases.isEmpty()) {
            addAliases(config.commands.aliases.toArray(String[]::new));
        }

        addUsageVariant(new VaultIdVariant(plugin));
        addSubCommand(new HelpCommand());
        addSubCommand(new ReloadCommand(plugin));
        addSubCommand(new UiCommand(plugin));
        addSubCommand(new RenameCommand(plugin));
        addSubCommand(new OverflowCommand(plugin));
        addSubCommand(new ListCommand(plugin));
        addSubCommand(new OpenCommand(plugin));
        addSubCommand(new ImportCommand(plugin));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Chat.send(context, Chat.error(context, "messages.player_entity_not_found"));
            return;
        }

        if (!hasUiPermission(context)) {
            Chat.send(context, Chat.error(context, "messages.no_permission.ui"));
            return;
        }

        VoidVaultPlugin plugin = VoidVaultPlugin.getInstance();
        UUID ownerUuid = playerRef.getUuid();
        int defaultVaultId = plugin.getVaultManager().getMetadataService().getDefaultVaultId(ownerUuid);
        if (!plugin.getVaultManager().canAccessVault(ownerUuid, defaultVaultId)) {
            defaultVaultId = DatabaseService.PRIMARY_VAULT_ID;
        }
        openSelfVault(context, player, ownerUuid, defaultVaultId);
    }

    private boolean hasUiPermission(CommandContext context) {
        VoidVaultConfig config = plugin.getCurrentConfig();
        return hasCommandPermission(context, config.commands.uiPermission);
    }

    private static boolean hasCommandPermission(VoidVaultPlugin plugin, CommandContext context, String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }

        try {
            if (context.sender().hasPermission(permission)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            UUID uuid = context.sender().getUuid();
            return plugin.getPermissionService().hasPermission(uuid, permission);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasCommandPermission(CommandContext context, String permission) {
        return hasCommandPermission(plugin, context, permission);
    }

    private static void openSelfVault(CommandContext context, Player player, UUID ownerUuid, int vaultId) {
        VoidVaultPlugin plugin = VoidVaultPlugin.getInstance();
        if (!validateSelfVaultAccess(context, ownerUuid, vaultId)) {
            return;
        }

        try {
            plugin.getVaultManager().openVault(player, ownerUuid, vaultId);
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error(context, "messages.open_failed.self"));
        }
    }


    private static void openSelfVaultSelector(CommandContext context, Player player, UUID ownerUuid) {
        VoidVaultPlugin plugin = VoidVaultPlugin.getInstance();
        VoidVaultConfig config = plugin.getCurrentConfig();
        if (!config.isMultiVaultEnabled()) {
            Chat.send(context, Chat.error(context, "messages.vault.multi_disabled"));
            return;
        }

        try {
            plugin.getVaultManager().openVaultSelector(player);
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error(context, "messages.open_failed.self"));
        }
    }

    private static boolean validateSelfVaultAccess(CommandContext context, UUID ownerUuid, int vaultId) {
        VoidVaultPlugin plugin = VoidVaultPlugin.getInstance();
        VoidVaultConfig config = plugin.getCurrentConfig();
        if (vaultId < 1) {
            Chat.send(context, Chat.error(context, "messages.vault.invalid"));
            return false;
        }
        if (vaultId > 1 && !config.isMultiVaultEnabled()) {
            Chat.send(context, Chat.error(context, "messages.vault.multi_disabled"));
            return false;
        }
        if (plugin.getVaultManager().canAccessVault(ownerUuid, vaultId)) {
            return true;
        }
        Chat.send(context, Chat.error(context, "messages.vault.locked", vaultId));
        return false;
    }

    private static void sendHelp(CommandContext context) {
        Chat.send(context, Chat.title(context, "messages.help.title"));
        Chat.send(context, Chat.info(context, "messages.help.open_alias_vv"));
        Chat.send(context, Chat.info(context, "messages.help.open_number"));
        Chat.send(context, Chat.info(context, "messages.help.ui"));
        Chat.send(context, Chat.info(context, "messages.help.rename"));
        Chat.send(context, Chat.info(context, "messages.help.list"));
        Chat.send(context, Chat.info(context, "messages.help.overflow"));
        Chat.send(context, Chat.info(context, "messages.help.open_other"));
        Chat.send(context, Chat.info(context, "messages.help.reload"));
        Chat.send(context, Chat.info(context, "messages.help.import_enderchest"));
    }

    private static void sendImportReport(CommandContext context, ImportReport report) {
        Chat.send(context, Chat.title(context, report.dryRun ? "messages.import.preview_title" : "messages.import.complete_title"));
        Chat.send(context, Chat.info(context, "messages.import.players_found", report.playersFound));
        Chat.send(context, Chat.info(context, "messages.import.players_imported", report.playersImported));
        Chat.send(context, Chat.info(context, "messages.import.players_skipped", report.playersSkipped));
        Chat.send(context, Chat.info(context, "messages.import.players_with_items", report.playersWithItems));
        Chat.send(context, Chat.info(context, "messages.import.stored_item_slots", report.totalItemSlots));
        Chat.send(context, Chat.info(context, "messages.import.overflow_players", report.overflowPlayers));
        Chat.send(context, Chat.info(context, "messages.import.invalid_rows_items", report.invalidRows, report.invalidItems));
    }

    private static UUID resolveUuid(String target) {
        try {
            return UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            PlayerRef playerRef = Universe.get().getPlayerByUsername(target, NameMatching.EXACT_IGNORE_CASE);
            return playerRef == null ? null : playerRef.getUuid();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseVaultId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static class HelpCommand extends CommandBase {
        private HelpCommand() {
            super("help", I18n.commandKey("commands.help.description"));
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            sendHelp(context);
        }
    }

    private static class VaultIdVariant extends AbstractPlayerCommand {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> vaultArg;

        private VaultIdVariant(VoidVaultPlugin plugin) {
            super(I18n.commandKey("commands.vault_id.description"));
            this.plugin = plugin;
            this.vaultArg = withRequiredArg("vault", I18n.commandKey("arguments.vault.description"), ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            VoidVaultConfig config = plugin.getCurrentConfig();
            if (!hasCommandPermission(plugin, context, config.commands.usePermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.use"));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Chat.send(context, Chat.error(context, "messages.player_entity_not_found"));
                return;
            }

            String value = vaultArg.get(context);
            if (value != null && value.equalsIgnoreCase("ui")) {
                if (!hasCommandPermission(plugin, context, config.commands.uiPermission)) {
                    Chat.send(context, Chat.error(context, "messages.no_permission.ui"));
                    return;
                }
                openSelfVaultSelector(context, player, playerRef.getUuid());
                return;
            }

            if (!hasCommandPermission(plugin, context, config.commands.uiPermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.ui"));
                return;
            }

            if (value == null || value.isBlank()) {
                UUID ownerUuid = playerRef.getUuid();
                int defaultVaultId = plugin.getVaultManager().getMetadataService().getDefaultVaultId(ownerUuid);
                if (!plugin.getVaultManager().canAccessVault(ownerUuid, defaultVaultId)) {
                    defaultVaultId = DatabaseService.PRIMARY_VAULT_ID;
                }
                openSelfVault(context, player, ownerUuid, defaultVaultId);
                return;
            }

            int vaultId = parseVaultId(value);
            openSelfVault(context, player, playerRef.getUuid(), vaultId);
        }
    }

    private static class ReloadCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private ReloadCommand(VoidVaultPlugin plugin) {
            super("reload", I18n.commandKey("commands.reload.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            VoidVaultConfig config = plugin.getCurrentConfig();
            if (!hasCommandPermission(plugin, context, config.commands.reloadPermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.reload"));
                return;
            }

            try {
                plugin.reloadVoidVault();
                Chat.send(context, Chat.ok(context, "messages.reload.success"));
            } catch (Exception exception) {
                exception.printStackTrace();
                Chat.send(context, Chat.error(context, "messages.reload.failed"));
            }
        }
    }


    private static class UiCommand extends AbstractPlayerCommand {
        private final VoidVaultPlugin plugin;

        private UiCommand(VoidVaultPlugin plugin) {
            super("ui", I18n.commandKey("commands.ui.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            VoidVaultConfig config = plugin.getCurrentConfig();
            if (!hasCommandPermission(plugin, context, config.commands.uiPermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.ui"));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Chat.send(context, Chat.error(context, "messages.player_entity_not_found"));
                return;
            }

            openSelfVaultSelector(context, player, playerRef.getUuid());
        }
    }


    private static class RenameCommand extends CommandBase {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> vaultArg;
        private final RequiredArg<String> nameArg;

        private RenameCommand(VoidVaultPlugin plugin) {
            super("rename", I18n.commandKey("commands.rename.description"));
            this.plugin = plugin;
            this.vaultArg = withRequiredArg("vault", I18n.commandKey("arguments.vault.description"), ArgTypes.STRING);
            this.nameArg = withRequiredArg("name", I18n.commandKey("arguments.name.description"), ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!context.isPlayer()) {
                Chat.send(context, Chat.error(context, "messages.players_only"));
                return;
            }

            VoidVaultConfig config = plugin.getCurrentConfig();
            if (!hasCommandPermission(plugin, context, config.commands.usePermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.use"));
                return;
            }

            UUID senderUuid = context.sender().getUuid();
            int vaultId = parseVaultId(vaultArg.get(context));
            if (!validateSelfVaultAccess(context, senderUuid, vaultId)) {
                return;
            }

            String rawName = nameArg.get(context);
            String normalized = plugin.getVaultManager().normalizeVaultName(rawName);
            boolean saved = plugin.getVaultManager().setVaultName(senderUuid, vaultId, rawName);
            if (!saved) {
                Chat.send(context, Chat.error(context, "messages.rename.failed"));
                return;
            }

            if (normalized == null) {
                Chat.send(context, Chat.ok(context, "messages.rename.cleared", vaultId));
                return;
            }

            Chat.send(context, Chat.ok(context, "messages.rename.success", vaultId, normalized));
        }
    }

    private static class OverflowCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private OverflowCommand(VoidVaultPlugin plugin) {
            super("overflow", I18n.commandKey("commands.overflow.description"));
            this.plugin = plugin;
            addUsageVariant(new OverflowTargetVariant(plugin));
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!context.isPlayer()) {
                Chat.send(context, Chat.error(context, "messages.players_only"));
                return;
            }

            sendOverflow(context, plugin, DatabaseService.PRIMARY_VAULT_ID, false);
        }
    }

    private static class OverflowTargetVariant extends CommandBase {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> targetArg;

        private OverflowTargetVariant(VoidVaultPlugin plugin) {
            super(I18n.commandKey("commands.overflow.target.description"));
            this.plugin = plugin;
            this.targetArg = withRequiredArg("vault", I18n.commandKey("arguments.vault.description"), ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!context.isPlayer()) {
                Chat.send(context, Chat.error(context, "messages.players_only"));
                return;
            }

            String target = targetArg.get(context);
            if (target != null && target.equalsIgnoreCase("all")) {
                sendOverflow(context, plugin, DatabaseService.PRIMARY_VAULT_ID, true);
                return;
            }

            int vaultId = parseVaultId(target);
            sendOverflow(context, plugin, vaultId, false);
        }
    }

    private static void sendOverflow(CommandContext context, VoidVaultPlugin plugin, int vaultId, boolean all) {
        try {
            UUID senderUuid = context.sender().getUuid();
            if (all) {
                int overflow = plugin.getVaultManager().getOverflowCountAll(senderUuid);
                sendOverflowResult(context, overflow, "messages.overflow.has_items_all");
                return;
            }
            if (!validateSelfVaultAccess(context, senderUuid, vaultId)) {
                return;
            }
            int overflow = plugin.getVaultManager().getOverflowCount(senderUuid, vaultId);
            sendOverflowResult(context, overflow, "messages.overflow.has_items_vault", vaultId);
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error(context, "messages.overflow.failed"));
        }
    }

    private static void sendOverflowResult(CommandContext context, int overflow, String key, Object... args) {
        if (overflow == 0) {
            Chat.send(context, Chat.ok(context, "messages.overflow.none"));
            return;
        }

        Object[] messageArgs = new Object[args.length + 1];
        messageArgs[0] = overflow;
        System.arraycopy(args, 0, messageArgs, 1, args.length);
        Chat.send(context, Chat.info(context, key, messageArgs));
    }

    private static class ListCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private ListCommand(VoidVaultPlugin plugin) {
            super("list", I18n.commandKey("commands.list.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!context.isPlayer()) {
                Chat.send(context, Chat.error(context, "messages.players_only"));
                return;
            }

            UUID senderUuid = context.sender().getUuid();
            List<VaultInfo> vaults = plugin.getVaultManager().listVaults(senderUuid);
            Chat.send(context, Chat.title(context, "messages.list.title"));
            for (VaultInfo vault : vaults) {
                boolean named = vault.displayName() != null && !vault.displayName().isBlank();
                String key = vault.accessible() ? "messages.list.accessible" : "messages.list.locked";
                if (named) {
                    key = vault.accessible() ? "messages.list.accessible_named" : "messages.list.locked_named";
                    Chat.send(context, Chat.info(context, key, vault.vaultId(), vault.displayName(), vault.overflowItems()));
                    continue;
                }
                Chat.send(context, Chat.info(context, key, vault.vaultId(), vault.overflowItems()));
            }
        }
    }

    private static class OpenCommand extends AbstractPlayerCommand {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> targetArg;

        private OpenCommand(VoidVaultPlugin plugin) {
            super("open", I18n.commandKey("commands.open.description"));
            this.plugin = plugin;
            this.targetArg = withRequiredArg("player", I18n.commandKey("arguments.player.description"), ArgTypes.STRING);
            addUsageVariant(new OpenVaultIdVariant(plugin));
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            openTarget(context, store, ref, targetArg.get(context), DatabaseService.PRIMARY_VAULT_ID, plugin);
        }
    }

    private static class OpenVaultIdVariant extends AbstractPlayerCommand {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> targetArg;
        private final RequiredArg<String> vaultArg;

        private OpenVaultIdVariant(VoidVaultPlugin plugin) {
            super(I18n.commandKey("commands.open.target_vault.description"));
            this.plugin = plugin;
            this.targetArg = withRequiredArg("player", I18n.commandKey("arguments.player.description"), ArgTypes.STRING);
            this.vaultArg = withRequiredArg("vault", I18n.commandKey("arguments.vault.description"), ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            openTarget(context, store, ref, targetArg.get(context), parseVaultId(vaultArg.get(context)), plugin);
        }
    }

    private static void openTarget(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, String target, int vaultId, VoidVaultPlugin plugin) {
        VoidVaultConfig config = plugin.getCurrentConfig();
        if (!hasCommandPermission(plugin, context, config.commands.adminPermission)) {
            Chat.send(context, Chat.error(context, "messages.no_permission.inspect"));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Chat.send(context, Chat.error(context, "messages.player_entity_not_found"));
            return;
        }

        UUID targetUuid = resolveUuid(target);
        if (targetUuid == null) {
            Chat.send(context, Chat.error(context, "messages.player_not_found"));
            return;
        }

        if (vaultId < 1) {
            Chat.send(context, Chat.error(context, "messages.vault.invalid"));
            return;
        }

        try {
            plugin.getVaultManager().openVault(player, targetUuid, vaultId);
            Chat.send(context, Chat.ok(context, "messages.open.success_other_vault", target, vaultId));
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error(context, "messages.open_failed.other"));
        }
    }

    private static class ImportCommand extends AbstractCommandCollection {
        private ImportCommand(VoidVaultPlugin plugin) {
            super("import", I18n.commandKey("commands.import.description"));
            addSubCommand(new EnderChestImportCommand(plugin));
        }
    }

    private static class EnderChestImportCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private EnderChestImportCommand(VoidVaultPlugin plugin) {
            super("enderchest", I18n.commandKey("commands.import.enderchest.description"));
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            VoidVaultConfig config = plugin.getCurrentConfig();
            if (!hasCommandPermission(plugin, context, config.commands.importPermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.import"));
                return;
            }

            plugin.getVaultManager().discardLoaded();

            EnderChestImporter importer = plugin.getEnderChestImporter();
            ImportReport report = importer.confirm();

            plugin.getVaultManager().discardLoaded();
            sendImportReport(context, report);

            System.out.println("[VoidVault] EnderChest import finished: playersFound=" + report.playersFound
                    + ", playersImported=" + report.playersImported
                    + ", playersSkipped=" + report.playersSkipped
                    + ", playersWithItems=" + report.playersWithItems
                    + ", totalItemSlots=" + report.totalItemSlots
                    + ", overflowPlayers=" + report.overflowPlayers
                    + ", invalidRows=" + report.invalidRows
                    + ", invalidItems=" + report.invalidItems);

            if (!report.warnings.isEmpty()) {
                Chat.send(context, Chat.info(context, "messages.import.warnings", Math.min(report.warnings.size(), 5)));
            }
        }
    }
}

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
import tblack.voidvault.util.Chat;

import javax.annotation.Nonnull;
import java.util.UUID;

public class VoidVaultCommand extends AbstractPlayerCommand {
    private final VoidVaultPlugin plugin;

    public VoidVaultCommand(VoidVaultPlugin plugin, VoidVaultConfig config) {
        super(config.commands.primary, I18n.commandKey("commands.root.description"));
        this.plugin = plugin;

        if (config.commands.aliases != null && !config.commands.aliases.isEmpty()) {
            addAliases(config.commands.aliases.toArray(String[]::new));
        }

        addSubCommand(new HelpCommand(plugin));
        addSubCommand(new ReloadCommand(plugin));
        addSubCommand(new OverflowCommand(plugin));
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

        VoidVaultConfig config = plugin.getCurrentConfig();
        if (!hasCommandPermission(context, config.commands.usePermission)) {
            Chat.send(context, Chat.error(context, "messages.no_permission.use"));
            return;
        }

        try {
            plugin.getVaultManager().openVault(player, playerRef.getUuid());
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error(context, "messages.open_failed.self"));
        }
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

    private static void sendHelp(CommandContext context) {
        Chat.send(context, Chat.title(context, "messages.help.title"));
        Chat.send(context, Chat.info(context, "messages.help.open_alias_vv"));
        Chat.send(context, Chat.info(context, "messages.help.open_alias_voidvault"));
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

    private static class HelpCommand extends CommandBase {
        private HelpCommand(VoidVaultPlugin plugin) {
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

            plugin.reloadVoidVault();
            Chat.send(context, Chat.ok(context, "messages.reload.success"));
        }
    }

    private static class OverflowCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private OverflowCommand(VoidVaultPlugin plugin) {
            super("overflow", I18n.commandKey("commands.overflow.description"));
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

            try {
                UUID senderUuid = context.sender().getUuid();
                int overflow = plugin.getVaultManager().getOverflowCount(senderUuid);

                if (overflow == 0) {
                    Chat.send(context, Chat.ok(context, "messages.overflow.none"));
                } else {
                    Chat.send(context, Chat.info(context, "messages.overflow.has_items", overflow));
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                Chat.send(context, Chat.error(context, "messages.overflow.failed"));
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
            if (!hasCommandPermission(plugin, context, config.commands.adminPermission)) {
                Chat.send(context, Chat.error(context, "messages.no_permission.inspect"));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Chat.send(context, Chat.error(context, "messages.player_entity_not_found"));
                return;
            }

            String target = targetArg.get(context);
            UUID targetUuid = resolveUuid(target);
            if (targetUuid == null) {
                Chat.send(context, Chat.error(context, "messages.player_not_found"));
                return;
            }

            try {
                plugin.getVaultManager().openVault(player, targetUuid);
                Chat.send(context, Chat.ok(context, "messages.open.success_other", target));
            } catch (Exception exception) {
                exception.printStackTrace();
                Chat.send(context, Chat.error(context, "messages.open_failed.other"));
            }
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

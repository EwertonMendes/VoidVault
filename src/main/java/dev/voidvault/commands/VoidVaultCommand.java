package dev.voidvault.commands;

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
import dev.voidvault.VoidVaultPlugin;
import dev.voidvault.config.VoidVaultConfig;
import dev.voidvault.importer.EnderChestImporter;
import dev.voidvault.model.ImportReport;
import dev.voidvault.util.Chat;

import javax.annotation.Nonnull;
import java.util.UUID;

public class VoidVaultCommand extends AbstractPlayerCommand {
    private final VoidVaultPlugin plugin;

    public VoidVaultCommand(VoidVaultPlugin plugin, VoidVaultConfig config) {
        super(config.commands.primary, "Open your VoidVault or manage the plugin");
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
            Chat.send(context, Chat.error("Could not resolve your player entity."));
            return;
        }

        VoidVaultConfig config = plugin.getCurrentConfig();
        if (!hasCommandPermission(context, config.commands.usePermission)) {
            Chat.send(context, Chat.error("You don't have permission to use VoidVault."));
            return;
        }

        try {
            plugin.getVaultManager().openVault(player, playerRef.getUuid());
        } catch (Exception exception) {
            exception.printStackTrace();
            Chat.send(context, Chat.error("Failed to open your VoidVault."));
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
        Chat.send(context, Chat.title("VoidVault commands"));
        Chat.send(context, Chat.info("/vv - Open your vault"));
        Chat.send(context, Chat.info("/voidvault - Open your vault"));
        Chat.send(context, Chat.info("/voidvault overflow - Check hidden migrated slots"));
        Chat.send(context, Chat.info("/voidvault open <player|uuid> - Admin inspect"));
        Chat.send(context, Chat.info("/voidvault reload - Reload config"));
        Chat.send(context, Chat.info("/voidvault import enderchest - Import legacy EnderChest data"));
    }

    private static void sendImportReport(CommandContext context, ImportReport report) {
        Chat.send(context, Chat.title("VoidVault import " + (report.dryRun ? "preview" : "complete")));
        Chat.send(context, Chat.info("Players found: " + report.playersFound));
        Chat.send(context, Chat.info("Players imported: " + report.playersImported));
        Chat.send(context, Chat.info("Players skipped: " + report.playersSkipped));
        Chat.send(context, Chat.info("Players with items: " + report.playersWithItems));
        Chat.send(context, Chat.info("Stored item slots: " + report.totalItemSlots));
        Chat.send(context, Chat.info("Players with overflow slots: " + report.overflowPlayers));
        Chat.send(context, Chat.info("Invalid rows/items: " + report.invalidRows + "/" + report.invalidItems));
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
            super("help", "Shows VoidVault commands");
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
            super("reload", "Reloads VoidVault configuration");
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
                Chat.send(context, Chat.error("You don't have permission to reload VoidVault."));
                return;
            }

            plugin.reloadVoidVault();
            Chat.send(context, Chat.ok("VoidVault configuration reloaded."));
        }
    }

    private static class OverflowCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private OverflowCommand(VoidVaultPlugin plugin) {
            super("overflow", "Checks hidden migrated slots");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            if (!context.isPlayer()) {
                Chat.send(context, Chat.error("This command can only be used by players."));
                return;
            }

            try {
                UUID senderUuid = context.sender().getUuid();
                int overflow = plugin.getVaultManager().getOverflowCount(senderUuid);

                if (overflow == 0) {
                    Chat.send(context, Chat.ok("You have no hidden overflow items."));
                } else {
                    Chat.send(context, Chat.info("You have " + overflow + " item slots stored above your current visible slot limit. They will appear when you unlock more slots."));
                }
            } catch (Exception exception) {
                exception.printStackTrace();
                Chat.send(context, Chat.error("Failed to check overflow."));
            }
        }
    }

    private static class OpenCommand extends AbstractPlayerCommand {
        private final VoidVaultPlugin plugin;
        private final RequiredArg<String> targetArg;

        private OpenCommand(VoidVaultPlugin plugin) {
            super("open", "Opens another player's VoidVault");
            this.plugin = plugin;
            this.targetArg = withRequiredArg("player", "Player name or UUID", ArgTypes.STRING);
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
                Chat.send(context, Chat.error("You don't have permission to inspect other VoidVaults."));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Chat.send(context, Chat.error("Could not resolve your player entity."));
                return;
            }

            String target = targetArg.get(context);
            UUID targetUuid = resolveUuid(target);
            if (targetUuid == null) {
                Chat.send(context, Chat.error("Player not found. Use an online player name or UUID."));
                return;
            }

            try {
                plugin.getVaultManager().openVault(player, targetUuid);
                Chat.send(context, Chat.ok("Opened VoidVault for " + target + "."));
            } catch (Exception exception) {
                exception.printStackTrace();
                Chat.send(context, Chat.error("Failed to open target VoidVault."));
            }
        }
    }

    private static class ImportCommand extends AbstractCommandCollection {
        private ImportCommand(VoidVaultPlugin plugin) {
            super("import", "Imports legacy vault data");
            addSubCommand(new EnderChestImportCommand(plugin));
        }
    }

    private static class EnderChestImportCommand extends CommandBase {
        private final VoidVaultPlugin plugin;

        private EnderChestImportCommand(VoidVaultPlugin plugin) {
            super("enderchest", "Imports data from kvothe EnderChest");
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
                Chat.send(context, Chat.error("You don't have permission to import legacy EnderChest data."));
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
                Chat.send(context, Chat.info("Warnings: " + Math.min(report.warnings.size(), 5) + " shown in mods/VoidVault/reports."));
            }
        }
    }
}

package dev.indrajeeth.tpshield.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.manager.CommandManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Registers all TpShield commands via Paper's Brigadier Lifecycle API.
 * This ensures the Minecraft client knows about every command, preventing
 * the "unknown command" confirmation dialog when clicking chat run_command
 * click events (e.g. the "[View Request]" notification button).
 */
@SuppressWarnings("UnstableApiUsage")
public final class BrigadierRegistrar {

    private final TpShield plugin;
    private final CommandManager commandManager;

    public BrigadierRegistrar(TpShield plugin, CommandManager commandManager) {
        this.plugin = plugin;
        this.commandManager = commandManager;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands cmds = event.registrar();

            for (String name : List.of("tptoggle", "tplist",
                                       "tpnotify", "tpauto", "tprate")) {
                String n = name;
                cmds.register(
                    Commands.literal(n)
                        .executes(ctx -> { delegate(ctx.getSource().getSender(), n, new String[0]); return 1; })
                        .build()
                );
            }

            cmds.register(
                Commands.literal("tpa")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemaining().toLowerCase();
                            Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(prefix))
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            delegate(ctx.getSource().getSender(), "tpa",
                                new String[]{StringArgumentType.getString(ctx, "player")});
                            return 1;
                        }))
                    .executes(ctx -> { delegate(ctx.getSource().getSender(), "tpa", new String[0]); return 1; })
                    .build()
            );

            cmds.register(
                Commands.literal("tpcancel")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getSender() instanceof Player player) {
                                String prefix = builder.getRemaining().toLowerCase();
                                plugin.getTeleportManager()
                                    .getSentRequestsBy(player.getUniqueId()).stream()
                                    .map(Bukkit::getPlayer)
                                    .filter(java.util.Objects::nonNull)
                                    .map(Player::getName)
                                    .filter(nm -> nm.toLowerCase().startsWith(prefix))
                                    .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            delegate(ctx.getSource().getSender(), "tpcancel",
                                new String[]{StringArgumentType.getString(ctx, "player")});
                            return 1;
                        }))
                    .executes(ctx -> { delegate(ctx.getSource().getSender(), "tpcancel", new String[0]); return 1; })
                    .build()
            );

            for (String name : List.of("tpaccept", "tpdeny")) {
                String n = name;
                cmds.register(
                    Commands.literal(n)
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                if (ctx.getSource().getSender() instanceof Player player) {
                                    String prefix = builder.getRemaining().toLowerCase();
                                    plugin.getTeleportManager()
                                        .getPendingRequestsFor(player.getUniqueId()).stream()
                                        .map(Bukkit::getPlayer)
                                        .filter(java.util.Objects::nonNull)
                                        .map(Player::getName)
                                        .filter(nm -> nm.toLowerCase().startsWith(prefix))
                                        .forEach(builder::suggest);
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                delegate(ctx.getSource().getSender(), n,
                                    new String[]{StringArgumentType.getString(ctx, "player")});
                                return 1;
                            }))
                        .executes(ctx -> { delegate(ctx.getSource().getSender(), n, new String[0]); return 1; })
                        .build()
                );
            }

            cmds.register(
                Commands.literal("tpaview")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getSender() instanceof Player player) {
                                String prefix = builder.getRemaining().toLowerCase();
                                plugin.getTeleportManager()
                                    .getPendingRequestsFor(player.getUniqueId()).stream()
                                    .map(Bukkit::getPlayer)
                                    .filter(java.util.Objects::nonNull)
                                    .map(Player::getName)
                                    .filter(nm -> nm.toLowerCase().startsWith(prefix))
                                    .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            delegate(ctx.getSource().getSender(), "tpaview",
                                new String[]{StringArgumentType.getString(ctx, "player")});
                            return 1;
                        }))
                    .executes(ctx -> { delegate(ctx.getSource().getSender(), "tpaview", new String[0]); return 1; })
                    .build()
            );

            cmds.register(
                Commands.literal("tpstats")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            String prefix = builder.getRemaining().toLowerCase();
                            Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(prefix))
                                .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            delegate(ctx.getSource().getSender(), "tpstats",
                                new String[]{StringArgumentType.getString(ctx, "player")});
                            return 1;
                        }))
                    .executes(ctx -> { delegate(ctx.getSource().getSender(), "tpstats", new String[0]); return 1; })
                    .build()
            );

            cmds.register(
                Commands.literal("tpshield")
                    .then(Commands.argument("sub", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if ("reload".startsWith(builder.getRemaining().toLowerCase()))
                                builder.suggest("reload");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            delegate(ctx.getSource().getSender(), "tpshield",
                                new String[]{StringArgumentType.getString(ctx, "sub")});
                            return 1;
                        }))
                    .executes(ctx -> { delegate(ctx.getSource().getSender(), "tpshield", new String[0]); return 1; })
                    .build()
            );
        });
    }

    private void delegate(CommandSender sender, String name, String[] args) {
        SimpleCommandHandler handler = commandManager.getHandler(name);
        if (handler != null) {
            handler.onCommand(sender, null, name, args);
        } else {
            plugin.getLogger().warning("[BrigadierRegistrar] Command not found: /" + name);
        }
    }
}

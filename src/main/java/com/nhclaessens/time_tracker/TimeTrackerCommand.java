package com.nhclaessens.time_tracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.function.Supplier;

public class TimeTrackerCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("timetracker")
                .then(CommandManager.literal("time")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            UUID playerId = player.getUuid();
                            PlayerActivityManager.PlayerActivityData data = PlayerActivityManager.playerActivityMap.get(playerId);

                            if (data != null) {
                                player.sendMessage(Text.literal("Active Time: " + formatTime(data.getTotalActiveTime())));
                                player.sendMessage(Text.literal("AFK Time: " + formatTime(data.getTotalAfkTime())));
                                player.sendMessage(Text.literal("Total Time: " + formatTime(data.getTotalTime())));
                            }

                            return 1;
                        })
                        .then(CommandManager.argument("username", StringArgumentType.string())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    String username = StringArgumentType.getString(context, "username");
                                    ServerPlayerEntity targetPlayer = context.getSource().getServer().getPlayerManager().getPlayer(username);

                                    PlayerActivityManager.PlayerActivityData data;

                                    if (targetPlayer != null) {
                                        UUID playerId = targetPlayer.getUuid();
                                        data = PlayerActivityManager.playerActivityMap.get(playerId);
                                    } else {
                                        data = PlayerActivityManager.getByUsername(username);
                                    }

                                    if (data != null) {
                                        context.getSource().sendFeedback(() -> Text.literal(username + ": Active Time: " + formatTime(data.getTotalActiveTime()) + ", AFK Time: " + formatTime(data.getTotalAfkTime()) + ", Total Time: " + formatTime(data.getTotalTime())), false);
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.literal("No data available for player: " + username), false);
                                    }

                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("timeall")
                        .requires(source -> source.hasPermissionLevel(2)) // Only allow admins
                        .executes(context -> {
                            for (var entry : PlayerActivityManager.playerActivityMap.entrySet()) {
                                PlayerActivityManager.PlayerActivityData data = entry.getValue();
                                Supplier<Text> messageSupplier = () -> Text.literal(data.username + ": Active Time: " + formatTime(data.getTotalActiveTime()) + ", AFK Time: " + formatTime(data.getTotalAfkTime()) + ", Total Time: " + formatTime(data.getTotalTime()));
                                context.getSource().sendFeedback(messageSupplier, false);
                            }

                            return 1;
                        })
                )
                .then(CommandManager.literal("config")
                        .requires(source -> source.hasPermissionLevel(2)) // Only allow admins
                        .executes(context -> {
                            String configString = ConfigHandler.getConfigAsString();
                            context.getSource().sendFeedback(() -> Text.literal("Current Config: " + configString), false);
                            return 1;
                        })
                )
        );
    }

    private static String formatTime(long timeMillis) {
        long seconds = (timeMillis / 1000) % 60;
        long minutes = (timeMillis / (1000 * 60)) % 60;
        long hours = (timeMillis / (1000 * 60 * 60)) % 24;
        long days = (timeMillis / (1000 * 60 * 60 * 24));

        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
    }
}

package com.nhclaessens.time_tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.NumberFormatType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.nhclaessens.time_tracker.TimeTracker.LOGGER;

public class PlayerActivityManager {
    private static int CHECK_INTERVAL = ConfigHandler.getConfig().runEveryNTicks;
    private static final long SAVE_INTERVAL = 60; // 60 seconds
    private static int ticksElapsed = 0;
    private static int ticksElapsedTimeTracker = 0;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File PLAYER_DATA_FILE = new File("time_tracker-data.json");
    static final Map<UUID, PlayerActivityData> playerActivityMap = new HashMap<>();

    public static void init() {
        loadPlayerData();

        // Registering player join and leave events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerActivityData data = playerActivityMap.computeIfAbsent(player.getUuid(), PlayerActivityData::new);
            // Reset last active time and store initial position
            data.resetLastActiveTime();
            data.setLastKnownPosition(player.getPos());
            data.setLastKnownRotation(player.getRotationVecClient());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerActivityData data = playerActivityMap.get(player.getUuid());
            if (data != null) {
                // Update and save player data when they leave
                data.updateLastActivityBeforeLeaving();
                savePlayerData();
            }
        });

        // Periodic activity check
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            float afkTimeoutMillis = ConfigHandler.getConfig().afkTimeoutMinutes * 60 * 1000;
            boolean countLookingAround = ConfigHandler.getConfig().countLookingAroundAsActive;

            ServerTickEvents.START_SERVER_TICK.register(serverTick -> {
                if(ticksElapsedTimeTracker % CHECK_INTERVAL == 0) {
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        PlayerActivityData data = playerActivityMap.computeIfAbsent(player.getUuid(), PlayerActivityData::new);
                        data.updateActivity(player, afkTimeoutMillis, countLookingAround);
                    }
                }
                ticksElapsedTimeTracker++;

            });
        });

        // Save data on server shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(PlayerActivityManager::savePlayerData));
        startPeriodicSave();
    }

    public static void startPeriodicSave() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            ticksElapsed++;

            if (ticksElapsed >= SAVE_INTERVAL * 20) {
                savePlayerData();
                ticksElapsed = 0; // Reset the tick counter after saving
            }
        });
    }

    public static void setupScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("active_time");

        if (objective == null) {
            objective = scoreboard.addObjective(
                    "active_time",
                    ScoreboardCriterion.DUMMY,
                    Text.literal("Active Time"),
                    ScoreboardCriterion.RenderType.INTEGER,
                    false,
                    null
            );
        }

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.LIST, objective); // Display in the tab list (slot 0)
    }

    public static class PlayerActivityData {
        public String username;
        private long lastCheckTime;
        private long lastActiveTime;
        private long totalActiveTime;
        private long totalAfkTime;
        private boolean isAfk;
        private Vec3d lastKnownPosition;
        private Vec3d lastKnownRotation;

        public PlayerActivityData(UUID playerId) {
            this.lastActiveTime = System.currentTimeMillis();
            this.totalActiveTime = 0;
            this.totalAfkTime = 0;
            this.isAfk = false;
        }

        public void resetLastActiveTime() {
            this.lastActiveTime = System.currentTimeMillis();
        }

        public void setLastKnownPosition(Vec3d position) {
            this.lastKnownPosition = position;
        }

        public void setLastKnownRotation(Vec3d rotation) {
            this.lastKnownRotation = rotation;
        }

        public void updateActivity(ServerPlayerEntity player, float afkTimeoutMillis, boolean countLookingAround) {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastCheck = currentTime - lastCheckTime;
            long timeSinceLastActive = currentTime - lastActiveTime;

            if(username == null) {
                username = player.getNameForScoreboard();
            }

            // account for server being offline
            if(lastCheckTime == 0 || timeSinceLastCheck > afkTimeoutMillis) {
                lastCheckTime = currentTime;
                timeSinceLastCheck = 0;
            }

            Vec3d currentPosition = player.getPos();
            Vec3d currentRotation = player.getRotationVecClient();

            boolean hasMoved = !currentPosition.equals(lastKnownPosition);
            boolean hasLookedAround = countLookingAround && !currentRotation.equals(lastKnownRotation);

            // Make sure teams for username colors exist
            Scoreboard scoreboard = Objects.requireNonNull(player.getServer()).getScoreboard();
            Team activeTeam = scoreboard.getTeam("Active");
            Team afkTeam = scoreboard.getTeam("AFK");

            if (activeTeam == null) {
                activeTeam = scoreboard.addTeam("Active");
                activeTeam.setColor(Formatting.WHITE); // Default color for active players
            }

            if (afkTeam == null) {
                afkTeam = scoreboard.addTeam("AFK");
                afkTeam.setColor(Formatting.GRAY); // Gray color for AFK players
            }

            // Live update time
            if(isAfk) {
                totalAfkTime += timeSinceLastCheck;
            } else {
                totalActiveTime += timeSinceLastCheck;
            }

            scoreboard.getOrCreateScore(ScoreHolder.fromName(player.getNameForScoreboard()), scoreboard.getNullableObjective("active_time")).setScore((int) (totalActiveTime / 1000 / 60));

            lastCheckTime = currentTime;

            if (hasMoved || hasLookedAround) {
                lastKnownPosition = currentPosition;
                lastKnownRotation = currentRotation;
                lastActiveTime = currentTime;
                if (isAfk) {
                    isAfk = false;
                    scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), activeTeam);
                }
            } else if (timeSinceLastActive > afkTimeoutMillis) {
                if (!isAfk) {
                    isAfk = true;
                    scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), afkTeam);

                    if (ConfigHandler.getConfig().logAfkStatus) {
                        Text message = Text.literal(player.getName().getString() + " has gone AFK.");
                        // Send the message to all players
                        Objects.requireNonNull(player.getServer()).getPlayerManager().broadcast(message, false);
                    }
                }
            }
        }

        public void updateLastActivityBeforeLeaving() {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastActive = currentTime - lastActiveTime;
            totalActiveTime += timeSinceLastActive;
        }

        public long getTotalActiveTime() {
            return totalActiveTime;
        }

        public long getTotalAfkTime() {
            return totalAfkTime;
        }

        public long getTotalTime() {
            return totalActiveTime + totalAfkTime;
        }
    }

    // Method to load player data from a file
    public static void loadPlayerData() {
        if (PLAYER_DATA_FILE.exists()) {
            try (FileReader reader = new FileReader(PLAYER_DATA_FILE)) {
                Map<String, PlayerActivityData> data = GSON.fromJson(reader, new TypeToken<Map<String, PlayerActivityData>>() {}.getType());
                for (Map.Entry<String, PlayerActivityData> entry : data.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    playerActivityMap.put(playerId, entry.getValue());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to save player data to a file
    public static void savePlayerData() {
        try (FileWriter writer = new FileWriter(PLAYER_DATA_FILE)) {
            GSON.toJson(playerActivityMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static PlayerActivityData getByUsername(String username){
        for(var entry : playerActivityMap.entrySet()){
            if(Objects.equals(entry.getValue().username, username)){
                return entry.getValue();
            }
        }
        return null;
    }
}

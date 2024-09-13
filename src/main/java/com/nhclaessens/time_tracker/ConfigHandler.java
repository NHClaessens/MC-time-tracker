package com.nhclaessens.time_tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("time_tracker-settings.json");

    private static Config config = new Config();

    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Create default config if it doesn't exist
            saveConfig();
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getConfigAsString() {
        return GSON.toJson(config);
    }

    public static Config getConfig() {
        return config;
    }

    public static class Config {
        public float afkTimeoutMinutes = 5;
        public boolean countLookingAroundAsActive = true; // New option to disable looking around
        public boolean logAfkStatus = false;
        public int runEveryNTicks = 1;
    }
}
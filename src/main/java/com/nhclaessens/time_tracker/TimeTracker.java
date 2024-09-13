package com.nhclaessens.time_tracker;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Time;

public class TimeTracker implements ModInitializer {
	public static final String MOD_ID = "time-tracker";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigHandler.loadConfig();
		PlayerActivityManager.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			TimeTrackerCommand.register(dispatcher);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(PlayerActivityManager::setupScoreboard);

		// Save config on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(ConfigHandler::saveConfig));
	}
}
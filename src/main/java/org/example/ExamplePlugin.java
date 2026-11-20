package org.example;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

/**
 * Base hunting rusherhack plugin
 */
public class ExamplePlugin extends Plugin {

	@Override
	public void onLoad() {
		this.getLogger().info("Base hunting plugin loaded");
		final SpiralModule spiral = new SpiralModule();
		RusherHackAPI.getModuleManager().registerFeature(spiral);
		RusherHackAPI.getCommandManager().registerFeature(new SpiralCommand(spiral));
	}

	@Override
	public void onUnload() {
		this.getLogger().info("Base hunting plugin unloaded");
	}
}

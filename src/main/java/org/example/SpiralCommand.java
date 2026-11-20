package org.example;

import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

import java.util.List;

/**
 * Save / load named spiral progress. Saves are one file each under rusherhack/spiral/.
 * Usage: .spiral save <name> | .spiral load <name> | .spiral list | .spiral delete <name>
 * (Loading is also available from the ClickGUI picker on the Spiral module.)
 */
public class SpiralCommand extends Command {

	private final SpiralModule module;

	public SpiralCommand(SpiralModule module) {
		super("spiral", "Save/load named spiral progress");
		this.module = module;
	}

	@CommandExecutor(subCommand = "save")
	@CommandExecutor.Argument("name")
	private String save(String name) {
		if (!SpiralModule.validName(name)) {
			return "Invalid name (no /, \\, ;, '.' or '..')";
		}
		if (!SpiralModule.writeSave(name, module.getState())) {
			return "Failed to write save '" + name + "'";
		}
		module.addSaveOption(name); // keep the GUI picker in sync
		return "Saved spiral '" + name + "'";
	}

	@CommandExecutor(subCommand = "load")
	@CommandExecutor.Argument("name")
	private String load(String name) {
		return module.loadByName(name) == null
				? "No spiral named '" + name + "'"
				: "Loaded spiral '" + name + "', continuing.";
	}

	@CommandExecutor(subCommand = "list")
	private String list() {
		final List<String> names = SpiralModule.savedNames();
		return names.isEmpty() ? "No saved spirals" : "Saved spirals: " + String.join(", ", names);
	}

	@CommandExecutor(subCommand = {"delete", "del"})
	@CommandExecutor.Argument("name")
	private String delete(String name) {
		return SpiralModule.deleteSave(name)
				? "Deleted spiral '" + name + "' (picker clears on restart)"
				: "No spiral named '" + name + "'";
	}
}

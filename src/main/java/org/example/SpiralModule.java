package org.example;

import org.rusherhack.client.api.events.client.input.EventInputTick;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.ModeSetting;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.interfaces.INamed;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks the player in an expanding square spiral for base/stash hunting.
 * Ported from miles352's meteor-stashhunting-addon spiral. Flight-friendly.
 * Saved progress lives one-file-per-spiral in {@code <gameDir>/rusherhack/spiral/<name>}.
 */
public class SpiralModule extends ToggleableModule {

	private static final String NONE = "<none>";

	private final NumberSetting<Integer> chunkGap =
			new NumberSetting<>("ChunkGap", "Gap in chunks between spiral laps", 1, 0, 64).incremental(1);

	private final BooleanSetting holdY =
			new BooleanSetting("HoldY", "Hold a Y level with space/shift (flight)", true);

	private final NumberSetting<Double> targetY =
			new NumberSetting<>("YLevel", "Y level to stay at", 120.0, -64.0, 320.0)
					.incremental(1.0)
					.setVisibility(this.holdY::getValue);

	// ClickGUI save picker: choose a saved spiral, then hit Load to continue it
	private final ModeSetting<Save> loadPicker =
			new ModeSetting<>("Load", "Saved spiral to continue", new Save(NONE));
	private final BooleanSetting loadButton =
			new BooleanSetting("LoadSelected", "Continue the spiral picked above", false);

	// names already in loadPicker, tracked ourselves so we never call the version-fragile ModeSetting.getOptions()
	private final Set<String> pickerNames = new HashSet<>();

	// spiral state: last corner (block coords) and how far the current legs run
	private double cornerX, cornerZ;
	private float yaw;
	private boolean movingAlongX;
	private int width, height;

	public SpiralModule() {
		super("Spiral", "Walk an expanding square spiral to sweep chunks", ModuleCategory.MOVEMENT);

		for (String name : savedNames()) {
			this.loadPicker.addOption(new Save(name));
			this.pickerNames.add(name);
		}
		this.loadButton.onChange(pressed -> {
			if (pressed) {
				loadSelected();
				this.loadButton.setValue(false); // spring back like a button
			}
		});

		this.registerSettings(this.chunkGap, this.holdY, this.targetY, this.loadPicker, this.loadButton);
	}

	@Override
	public void onEnable() {
		if (mc.player == null) {
			this.toggle();
			return;
		}
		// start the spiral from wherever we are, heading east (+X)
		this.cornerX = mc.player.getX();
		this.cornerZ = mc.player.getZ();
		this.yaw = -90.0f;
		this.movingAlongX = true;
		this.width = 0;
		this.height = 0;
	}

	@Subscribe
	private void onInput(EventInputTick event) {
		// run once per tick
		if (event.getStage() != Stage.POST || mc.player == null) {
			return;
		}

		// drive forward along the current heading
		mc.player.setYRot(this.yaw);
		event.getInput().forward(true);

		// hold the Y level with jump (space) / sneak (shift) so flight does the moving
		if (this.holdY.getValue()) {
			// deadband: only correct when more than 2 blocks off, so it doesn't spam space/shift
			final double diff = mc.player.getY() - this.targetY.getValue();
			if (diff < -2.0) {
				event.getInput().jump(true);
			} else if (diff > 2.0) {
				event.getInput().sneak(true);
			}
		}

		final int leg = 16 * this.chunkGap.getValue();

		// turn 90 degrees once the current leg is fully walked; each turn grows the spiral
		if (this.movingAlongX && Math.abs(mc.player.getX() - this.cornerX) >= (leg + this.width)) {
			this.yaw += 90.0f;
			this.cornerX = mc.player.getX();
			this.width += leg;
			this.movingAlongX = false;
			mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
		} else if (!this.movingAlongX && Math.abs(mc.player.getZ() - this.cornerZ) >= (leg + this.height)) {
			this.yaw += 90.0f;
			this.cornerZ = mc.player.getZ();
			this.height += leg;
			this.movingAlongX = true;
			mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
		}
	}

	private void loadSelected() {
		final Save sel = this.loadPicker.getValue();
		if (sel == null || sel.getName().equals(NONE)) {
			ChatUtils.print("No spiral selected");
			return;
		}
		if (loadByName(sel.getName()) == null) {
			ChatUtils.print("Couldn't read spiral '" + sel.getName() + "'");
		} else {
			ChatUtils.print("Loaded spiral '" + sel.getName() + "', continuing.");
		}
	}

	/** Load a saved spiral by name and continue it. Returns the name, or null if it doesn't exist. */
	public String loadByName(String name) {
		final State s = readSave(name);
		if (s == null) {
			return null;
		}
		if (!this.isToggled()) {
			this.setToggled(true);
		}
		applyState(s); // after enable, so onEnable's reset doesn't clobber it
		return name;
	}

	/** Add a name to the ClickGUI picker if it isn't already listed (called after a command save). */
	public void addSaveOption(String name) {
		if (this.pickerNames.add(name)) {
			this.loadPicker.addOption(new Save(name));
		}
	}

	// ---- persistence: one file per spiral at <gameDir>/rusherhack/spiral/<name> ----

	/** Snapshot of spiral progress. All coords are absolute, so it resumes from anywhere. */
	public record State(double cornerX, double cornerZ, float yaw, boolean movingAlongX, int width, int height, int chunkGap) {}

	public record Save(String name) implements INamed {
		@Override
		public String getName() {
			return this.name;
		}
	}

	public State getState() {
		return new State(this.cornerX, this.cornerZ, this.yaw, this.movingAlongX, this.width, this.height, this.chunkGap.getValue());
	}

	public void applyState(State s) {
		this.cornerX = s.cornerX();
		this.cornerZ = s.cornerZ();
		this.yaw = s.yaw();
		this.movingAlongX = s.movingAlongX();
		this.width = s.width();
		this.height = s.height();
		this.chunkGap.setValue(s.chunkGap());
	}

	public static File saveDir() {
		return new File(mc.gameDirectory, "rusherhack/spiral");
	}

	/** valid = usable as a bare filename (no path tricks). */
	public static boolean validName(String name) {
		return name != null && !name.isBlank()
				&& !name.contains("/") && !name.contains("\\")
				&& !name.equals(".") && !name.equals("..") && !name.contains(";");
	}

	public static List<String> savedNames() {
		final String[] files = saveDir().list();
		if (files == null) {
			return List.of();
		}
		final List<String> names = new ArrayList<>(Arrays.asList(files));
		names.sort(String::compareToIgnoreCase);
		return names;
	}

	public static State readSave(String name) {
		try {
			final List<String> lines = Files.readAllLines(new File(saveDir(), name).toPath());
			if (lines.isEmpty()) {
				return null;
			}
			final String[] p = lines.get(0).split(";");
			if (p.length != 7) {
				return null;
			}
			return new State(Double.parseDouble(p[0]), Double.parseDouble(p[1]),
					Float.parseFloat(p[2]), Boolean.parseBoolean(p[3]),
					Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
		} catch (Exception e) {
			return null; // ponytail: missing/corrupt save -> treat as absent
		}
	}

	public static boolean writeSave(String name, State s) {
		try {
			final File dir = saveDir();
			dir.mkdirs();
			Files.write(new File(dir, name).toPath(), List.of(String.join(";",
					Double.toString(s.cornerX()), Double.toString(s.cornerZ()),
					Float.toString(s.yaw()), Boolean.toString(s.movingAlongX()),
					Integer.toString(s.width()), Integer.toString(s.height()),
					Integer.toString(s.chunkGap()))));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean deleteSave(String name) {
		return new File(saveDir(), name).delete();
	}
}

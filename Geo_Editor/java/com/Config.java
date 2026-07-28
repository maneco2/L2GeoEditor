/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com;

import com.jogl.GLCellRenderSelector;
import com.jogl.InputBindings;
import com.jogl.renderer.DLLoDRenderer;
import com.jogl.renderer.VBOGLSLRenderer;
import com.geo.RegionWindowPlan;
import com.swing.ThemeManager;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;


/**
 * L2J project source.
 * 
 * @author Forsaiken aka Patrick
 * @author Maneco2
 */
public final class Config {
	public static final String APP_NAME = "L2GeoEditor";
	public static final String APP_VERSION = "2.0.3.41";
	public static int MINI_MAP_SIZE = 144;
	public static final int MINI_MAP_MARGIN = 0;
	public static final float MINI_MAP_FADE_STEP = 0.12f;

	private static final int mix(final Color c1, final Color c2, final float ratio) {
		return new Color(mix(c1.getRed(), c2.getRed(), ratio), mix(c1.getGreen(), c2.getGreen(), ratio),
				mix(c1.getBlue(), c2.getBlue(), ratio), mix(c1.getAlpha(), c2.getAlpha(), ratio)).getRGB();
	}

	private static final int mix(final int v1, final int v2, final float ratio) {
		return Math.min(((int) (v1 * ratio) + v2) / 2, 255);
	}

	private static final File CONFIG_DIRECTORY = new File("./configs");
	private static final File CONFIG_FILE = new File(CONFIG_DIRECTORY, "GeoEditor.xml");
	private static final File LEGACY_CONFIG_FILE = new File("./GeoEditor.ini");
	private static final ConfigProperties PROPERTIES = new ConfigProperties();
	public static int DEFAULT_COLOR_GUI_SELECTED = Color.YELLOW.getRGB();
	public static int DEFAULT_COLOR_FLAT_NORMAL = Color.BLUE.getRGB();
	public static int DEFAULT_COLOR_FLAT_HIGHLIGHTED = Color.CYAN.getRGB();
	public static int DEFAULT_COLOR_FLAT_SELECTED = Color.MAGENTA.getRGB();
	public static int DEFAULT_COLOR_COMPLEX_NORMAL = Color.GREEN.getRGB();
	public static int DEFAULT_COLOR_COMPLEX_HIGHLIGHTED = Color.CYAN.getRGB();
	public static int DEFAULT_COLOR_COMPLEX_SELECTED = Color.MAGENTA.getRGB();
	public static int DEFAULT_COLOR_MULTILAYER_NORMAL = Color.RED.getRGB();
	public static int DEFAULT_COLOR_MULTILAYER_HIGHLIGHTED = Color.CYAN.getRGB();
	public static int DEFAULT_COLOR_MULTILAYER_SELECTED = Color.MAGENTA.getRGB();
	public static int DEFAULT_COLOR_MULTILAYER_NORMAL_SPECIAL = mix(Color.WHITE, Color.RED, 1.125f);
	public static int DEFAULT_COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL = mix(Color.WHITE, Color.CYAN, 1.125f);
	public static int DEFAULT_COLOR_MULTILAYER_SELECTED_SPECIAL = mix(Color.WHITE, Color.MAGENTA, 1.125f);

	public static String PATH_TO_GEO_FILES = "./data/geodata/";
	public static boolean TERRAIN_DEFAULT_ON = false;
	public static int VIS_GRID_RANGE = GLCellRenderSelector.MIN_VIS_GRID_RANGE;
	public static String UI_THEME = ThemeManager.LIGHT;
	public static String CELL_RENDERER = VBOGLSLRenderer.NAME;
	public static int DLLoD_RANGE = DLLoDRenderer.MAX_DISTANCE_SQ;
	public static final boolean V_SYNC = true;
	public static boolean USE_TRANSPARENCY = true;
	public static final boolean USE_MULTITHREADING = true;
	public static boolean DRAW_OUTLINE = false;
	public static boolean FULL_MAP = false;
	public static boolean AUXILIARY_TERRAINS = true;
	public static boolean SHOW_GEODATA_GRID = true;
	public static boolean CIRCULAR_GEODATA_GRID = false;
	public static boolean FULL_TEXTURED_TERRAIN = false;
	public static int MAX_NEIGHBOR_TEXTURES = 8;
	public static int SELECTION_MOVE_MAX_Z = 32;
	public static int SELECTION_HEIGHT_STEP = 8;
	public static int CAMERA_SPEED_PERCENT = 100;
	public static boolean WINDOW_MAXIMIZED = false;
	public static int WINDOW_X = -1;
	public static int WINDOW_Y = -1;
	public static int WINDOW_WIDTH = -1;
	public static int WINDOW_HEIGHT = -1;
	public static int MAP_SELECTOR_WIDTH = 900;
	public static int MAP_SELECTOR_HEIGHT = 760;

	public static int COLOR_GUI_SELECTED = DEFAULT_COLOR_GUI_SELECTED;
	public static int COLOR_FLAT_NORMAL = DEFAULT_COLOR_FLAT_NORMAL;
	public static int COLOR_FLAT_HIGHLIGHTED = DEFAULT_COLOR_FLAT_HIGHLIGHTED;
	public static int COLOR_FLAT_SELECTED = DEFAULT_COLOR_FLAT_SELECTED;
	public static int COLOR_COMPLEX_NORMAL = DEFAULT_COLOR_COMPLEX_NORMAL;
	public static int COLOR_COMPLEX_HIGHLIGHTED = DEFAULT_COLOR_COMPLEX_HIGHLIGHTED;
	public static int COLOR_COMPLEX_SELECTED = DEFAULT_COLOR_COMPLEX_SELECTED;
	public static int COLOR_MULTILAYER_NORMAL = DEFAULT_COLOR_MULTILAYER_NORMAL;
	public static int COLOR_MULTILAYER_HIGHLIGHTED = DEFAULT_COLOR_MULTILAYER_HIGHLIGHTED;
	public static int COLOR_MULTILAYER_SELECTED = DEFAULT_COLOR_MULTILAYER_SELECTED;
	public static int COLOR_MULTILAYER_NORMAL_SPECIAL = DEFAULT_COLOR_MULTILAYER_NORMAL_SPECIAL;
	public static int COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL = DEFAULT_COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL;
	public static int COLOR_MULTILAYER_SELECTED_SPECIAL = DEFAULT_COLOR_MULTILAYER_SELECTED_SPECIAL;

	public static int NSWE_TEXTURE_ID = 5;

	public static final void load() {
		boolean migrateLegacyConfig = false;
		try {
			if (CONFIG_FILE.isFile()) {
				PROPERTIES.loadXml(CONFIG_FILE);
			} else if (LEGACY_CONFIG_FILE.isFile()) {
				PROPERTIES.loadLegacy(LEGACY_CONFIG_FILE);
				migrateLegacyConfig = true;
			}
			if (!PROPERTIES.isEmpty()) {
				PATH_TO_GEO_FILES = PROPERTIES.getProperty("PATH_TO_GEO_FILES", PATH_TO_GEO_FILES);
				TERRAIN_DEFAULT_ON = Boolean
						.parseBoolean(PROPERTIES.getProperty("TERRAIN_DEFAULT_ON", String.valueOf(TERRAIN_DEFAULT_ON)));
				VIS_GRID_RANGE = Integer
						.parseInt(PROPERTIES.getProperty("VIS_GRID_RANGE", String.valueOf(VIS_GRID_RANGE)));
				UI_THEME = PROPERTIES.getProperty("UI_THEME", UI_THEME);
				CELL_RENDERER = PROPERTIES.getProperty("CELL_RENDERER", VBOGLSLRenderer.NAME);
				CELL_RENDERER = VBOGLSLRenderer.NAME;
				MAP_SELECTOR_WIDTH = Integer.parseInt(PROPERTIES.getProperty("MAP_SELECTOR_WIDTH", "900"));
				MAP_SELECTOR_HEIGHT = Integer.parseInt(PROPERTIES.getProperty("MAP_SELECTOR_HEIGHT", "760"));
				DLLoD_RANGE = Integer.parseInt(PROPERTIES.getProperty("DLLoD_RANGE", String.valueOf(DLLoD_RANGE)));
				USE_TRANSPARENCY = Boolean
						.parseBoolean(PROPERTIES.getProperty("USE_TRANSPARENCY", String.valueOf(USE_TRANSPARENCY)));
				DRAW_OUTLINE = Boolean
						.parseBoolean(PROPERTIES.getProperty("DRAW_OUTLINE", String.valueOf(DRAW_OUTLINE)));
				FULL_MAP = Boolean.parseBoolean(PROPERTIES.getProperty("FULL_MAP", String.valueOf(FULL_MAP)));
				AUXILIARY_TERRAINS = Boolean.parseBoolean(
						PROPERTIES.getProperty("AUXILIARY_TERRAINS", String.valueOf(AUXILIARY_TERRAINS)));
				SHOW_GEODATA_GRID = Boolean.parseBoolean(
						PROPERTIES.getProperty("SHOW_GEODATA_GRID", String.valueOf(SHOW_GEODATA_GRID)));
				CIRCULAR_GEODATA_GRID = Boolean.parseBoolean(
						PROPERTIES.getProperty("CIRCULAR_GEODATA_GRID", String.valueOf(CIRCULAR_GEODATA_GRID)));
				FULL_TEXTURED_TERRAIN = Boolean.parseBoolean(
						PROPERTIES.getProperty("FULL_TEXTURED_TERRAIN", String.valueOf(FULL_TEXTURED_TERRAIN)));
				MAX_NEIGHBOR_TEXTURES = RegionWindowPlan.normalizeNeighborCount(
						parseIntProperty("MAX_NEIGHBOR_TEXTURES", MAX_NEIGHBOR_TEXTURES));
				SELECTION_MOVE_MAX_Z = normalizeSelectionMoveMaxZ(
						parseIntProperty("SELECTION_MOVE_MAX_Z", SELECTION_MOVE_MAX_Z));
				SELECTION_HEIGHT_STEP = normalizeHeightStep(
						parseIntProperty("SELECTION_HEIGHT_STEP", SELECTION_HEIGHT_STEP));
				CAMERA_SPEED_PERCENT = normalizeCameraSpeed(
						parseIntProperty("CAMERA_SPEED_PERCENT", CAMERA_SPEED_PERCENT));
				WINDOW_MAXIMIZED = Boolean
						.parseBoolean(PROPERTIES.getProperty("WINDOW_MAXIMIZED", String.valueOf(WINDOW_MAXIMIZED)));
				WINDOW_X = parseIntProperty("WINDOW_X", WINDOW_X);
				WINDOW_Y = parseIntProperty("WINDOW_Y", WINDOW_Y);
				WINDOW_WIDTH = parseIntProperty("WINDOW_WIDTH", WINDOW_WIDTH);
				WINDOW_HEIGHT = parseIntProperty("WINDOW_HEIGHT", WINDOW_HEIGHT);
				MINI_MAP_SIZE = Integer.parseInt(PROPERTIES.getProperty("MINI_MAP_SIZE", String.valueOf(MINI_MAP_SIZE)));

				COLOR_FLAT_NORMAL = Integer
						.parseInt(PROPERTIES.getProperty("COLOR_FLAT_NORMAL", String.valueOf(COLOR_FLAT_NORMAL)));
				COLOR_FLAT_HIGHLIGHTED = Integer.parseInt(
						PROPERTIES.getProperty("COLOR_FLAT_HIGHLIGHTED", String.valueOf(COLOR_FLAT_HIGHLIGHTED)));
				COLOR_FLAT_SELECTED = Integer
						.parseInt(PROPERTIES.getProperty("COLOR_FLAT_SELECTED", String.valueOf(COLOR_FLAT_SELECTED)));
				COLOR_COMPLEX_NORMAL = Integer
						.parseInt(PROPERTIES.getProperty("COLOR_COMPLEX_NORMAL", String.valueOf(COLOR_COMPLEX_NORMAL)));
				COLOR_COMPLEX_HIGHLIGHTED = Integer.parseInt(
						PROPERTIES.getProperty("COLOR_COMPLEX_HIGHLIGHTED", String.valueOf(COLOR_COMPLEX_HIGHLIGHTED)));
				COLOR_COMPLEX_SELECTED = Integer.parseInt(
						PROPERTIES.getProperty("COLOR_COMPLEX_SELECTED", String.valueOf(COLOR_COMPLEX_SELECTED)));
				COLOR_MULTILAYER_NORMAL = Integer.parseInt(
						PROPERTIES.getProperty("COLOR_MULTILAYER_NORMAL", String.valueOf(COLOR_MULTILAYER_NORMAL)));
				COLOR_MULTILAYER_HIGHLIGHTED = Integer.parseInt(PROPERTIES.getProperty("COLOR_MULTILAYER_HIGHLIGHTED",
						String.valueOf(COLOR_MULTILAYER_HIGHLIGHTED)));
				COLOR_MULTILAYER_SELECTED = Integer.parseInt(
						PROPERTIES.getProperty("COLOR_MULTILAYER_SELECTED", String.valueOf(COLOR_MULTILAYER_SELECTED)));

				COLOR_MULTILAYER_NORMAL_SPECIAL = Integer.parseInt(PROPERTIES.getProperty(
						"COLOR_MULTILAYER_NORMAL_SPECIAL", String.valueOf(COLOR_MULTILAYER_NORMAL_SPECIAL)));
				COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL = Integer.parseInt(PROPERTIES.getProperty(
						"COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL", String.valueOf(COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL)));
				COLOR_MULTILAYER_SELECTED_SPECIAL = Integer.parseInt(PROPERTIES.getProperty(
						"COLOR_MULTILAYER_SELECTED_SPECIAL", String.valueOf(COLOR_MULTILAYER_SELECTED_SPECIAL)));

				NSWE_TEXTURE_ID = Integer
						.parseInt(PROPERTIES.getProperty("NSWE_TEXTURE_ID", String.valueOf(NSWE_TEXTURE_ID)));
				InputBindings.beginConfigurationLoad();
				for (final InputBindings.Action action : InputBindings.Action.values())
					InputBindings.deserialize(action, PROPERTIES.getProperty(InputBindings.propertyName(action), null));
				InputBindings.finishConfigurationLoad();
			}
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			checkConfigs();
		}

		ThemeManager.apply(UI_THEME);
		if (migrateLegacyConfig) {
			save();
			if (CONFIG_FILE.isFile() && !LEGACY_CONFIG_FILE.delete())
				System.err.println("Unable to remove migrated legacy config: " + LEGACY_CONFIG_FILE);
		}
	}

	private static int parseIntProperty(final String key, final int defaultValue) {
		try {
			return Integer.parseInt(PROPERTIES.getProperty(key, String.valueOf(defaultValue)).trim());
		} catch (final NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static final void checkConfigs() {
		if (!new File(PATH_TO_GEO_FILES).isDirectory()) {
			PATH_TO_GEO_FILES = "./data/geodata/";
		}

		if (VIS_GRID_RANGE < GLCellRenderSelector.MIN_VIS_GRID_RANGE) {
			VIS_GRID_RANGE = GLCellRenderSelector.MIN_VIS_GRID_RANGE;
		} else if (VIS_GRID_RANGE > GLCellRenderSelector.MAX_VIS_GRID_RANGE) {
			VIS_GRID_RANGE = GLCellRenderSelector.MAX_VIS_GRID_RANGE;
		}

		if (DLLoD_RANGE < DLLoDRenderer.MIN_DISTANCE_SQ) {
			DLLoD_RANGE = DLLoDRenderer.MIN_DISTANCE_SQ;
		} else if (DLLoD_RANGE > DLLoDRenderer.MAX_DISTANCE_SQ) {
			DLLoD_RANGE = DLLoDRenderer.MAX_DISTANCE_SQ;
		}
	}

	public static final void save() {
		PROPERTIES.clear();
		PROPERTIES.put("PATH_TO_GEO_FILES", String.valueOf(PATH_TO_GEO_FILES));
		PROPERTIES.put("TERRAIN_DEFAULT_ON", String.valueOf(TERRAIN_DEFAULT_ON));
		PROPERTIES.put("VIS_GRID_RANGE", String.valueOf(VIS_GRID_RANGE));
		PROPERTIES.put("UI_THEME", UI_THEME);
		PROPERTIES.put("CELL_RENDERER", String.valueOf(CELL_RENDERER));
		PROPERTIES.put("DLLoD_RANGE", String.valueOf(DLLoD_RANGE));
		PROPERTIES.put("V_SYNC", String.valueOf(V_SYNC));
		PROPERTIES.put("USE_TRANSPARENCY", String.valueOf(USE_TRANSPARENCY));
		PROPERTIES.put("USE_MULTITHREADING", String.valueOf(USE_MULTITHREADING));
		PROPERTIES.put("DRAW_OUTLINE", String.valueOf(DRAW_OUTLINE));
		PROPERTIES.put("FULL_MAP", String.valueOf(FULL_MAP));
		PROPERTIES.put("AUXILIARY_TERRAINS", String.valueOf(AUXILIARY_TERRAINS));
		PROPERTIES.put("SHOW_GEODATA_GRID", String.valueOf(SHOW_GEODATA_GRID));
		PROPERTIES.put("CIRCULAR_GEODATA_GRID", String.valueOf(CIRCULAR_GEODATA_GRID));
		PROPERTIES.put("FULL_TEXTURED_TERRAIN", String.valueOf(FULL_TEXTURED_TERRAIN));
		PROPERTIES.put("MAX_NEIGHBOR_TEXTURES", String.valueOf(MAX_NEIGHBOR_TEXTURES));
		PROPERTIES.put("SELECTION_MOVE_MAX_Z", String.valueOf(SELECTION_MOVE_MAX_Z));
		PROPERTIES.put("SELECTION_HEIGHT_STEP", String.valueOf(SELECTION_HEIGHT_STEP));
		PROPERTIES.put("CAMERA_SPEED_PERCENT", String.valueOf(CAMERA_SPEED_PERCENT));
		PROPERTIES.put("WINDOW_MAXIMIZED", String.valueOf(WINDOW_MAXIMIZED));
		PROPERTIES.put("WINDOW_X", String.valueOf(WINDOW_X));
		PROPERTIES.put("WINDOW_Y", String.valueOf(WINDOW_Y));
		PROPERTIES.put("WINDOW_WIDTH", String.valueOf(WINDOW_WIDTH));
		PROPERTIES.put("WINDOW_HEIGHT", String.valueOf(WINDOW_HEIGHT));
		PROPERTIES.put("MINI_MAP_SIZE", String.valueOf(MINI_MAP_SIZE));
		PROPERTIES.put("MAP_SELECTOR_WIDTH", String.valueOf(MAP_SELECTOR_WIDTH));
		PROPERTIES.put("MAP_SELECTOR_HEIGHT", String.valueOf(MAP_SELECTOR_HEIGHT));

		PROPERTIES.put("COLOR_FLAT_NORMAL", String.valueOf(COLOR_FLAT_NORMAL));
		PROPERTIES.put("COLOR_FLAT_HIGHLIGHTED", String.valueOf(COLOR_FLAT_HIGHLIGHTED));
		PROPERTIES.put("COLOR_FLAT_SELECTED", String.valueOf(COLOR_FLAT_SELECTED));
		PROPERTIES.put("COLOR_COMPLEX_NORMAL", String.valueOf(COLOR_COMPLEX_NORMAL));
		PROPERTIES.put("COLOR_COMPLEX_HIGHLIGHTED", String.valueOf(COLOR_COMPLEX_HIGHLIGHTED));
		PROPERTIES.put("COLOR_COMPLEX_SELECTED", String.valueOf(COLOR_COMPLEX_SELECTED));
		PROPERTIES.put("COLOR_MULTILAYER_NORMAL", String.valueOf(COLOR_MULTILAYER_NORMAL));
		PROPERTIES.put("COLOR_MULTILAYER_HIGHLIGHTED", String.valueOf(COLOR_MULTILAYER_HIGHLIGHTED));
		PROPERTIES.put("COLOR_MULTILAYER_SELECTED", String.valueOf(COLOR_MULTILAYER_SELECTED));

		PROPERTIES.put("COLOR_MULTILAYER_NORMAL_SPECIAL", String.valueOf(COLOR_MULTILAYER_NORMAL_SPECIAL));
		PROPERTIES.put("COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL", String.valueOf(COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL));
		PROPERTIES.put("COLOR_MULTILAYER_SELECTED_SPECIAL", String.valueOf(COLOR_MULTILAYER_SELECTED_SPECIAL));

		PROPERTIES.put("NSWE_TEXTURE_ID", String.valueOf(NSWE_TEXTURE_ID));
		for (final InputBindings.Action action : InputBindings.Action.values())
			PROPERTIES.put(InputBindings.propertyName(action), InputBindings.serialize(action));

		PROPERTIES.saveXml(CONFIG_FILE);
	}

	private static int normalizeHeightStep(final int value) {
		return Math.max(8, Math.min(4096, (value / 8) * 8));
	}

	public static int normalizeSelectionMoveMaxZ(final int value) {
		if (value <= 0)
			return 0;
		final long rounded = (((long) value + 4L) / 8L) * 8L;
		return (int) Math.max(8L, Math.min(32760L, rounded));
	}

	public static int normalizeCameraSpeed(final int value) {
		final long rounded = (((long) value + 12L) / 25L) * 25L;
		return (int) Math.max(25L, Math.min(400L, rounded));
	}

	@SuppressWarnings("serial")
	private static final class ConfigProperties extends Properties {
		ConfigProperties() {
		}

		final void loadLegacy(final File file) throws Exception {
			try (FileInputStream fis = new FileInputStream(file)) {
				super.clear();
				super.load(fis);
			}
		}

		final void loadXml(final File file) throws Exception {
			try (FileInputStream fis = new FileInputStream(file)) {
				super.clear();
				super.loadFromXML(fis);
			}
		}

		final void saveXml(final File file) {
			final File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
				System.err.println("Unable to create config directory: " + parent);
				return;
			}
			try (FileOutputStream fos = new FileOutputStream(file)) {
				super.storeToXML(fos, "L2GeoEditor Config", "UTF-8");
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public final String getProperty(final String key, final String defaultValue) {
			final String property = super.getProperty(key);
			return ((property == null) ? defaultValue : property);
		}
	}
}

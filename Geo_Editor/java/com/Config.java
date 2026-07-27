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
import com.jogl.renderer.DLLoDRenderer;
import com.jogl.renderer.VBOGLSLRenderer;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

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
	public static final int MINI_MAP_MARGIN = 1;
	public static final float MINI_MAP_FADE_STEP = 0.12f;
	private static final LookAndFeelInfo[] LOOK_AND_FEEL_INFOS;

	private static final int mix(final Color c1, final Color c2, final float ratio) {
		return new Color(mix(c1.getRed(), c2.getRed(), ratio), mix(c1.getGreen(), c2.getGreen(), ratio),
				mix(c1.getBlue(), c2.getBlue(), ratio), mix(c1.getAlpha(), c2.getAlpha(), ratio)).getRGB();
	}

	private static final int mix(final int v1, final int v2, final float ratio) {
		return Math.min(((int) (v1 * ratio) + v2) / 2, 255);
	}

	private static final File CONFIG_FILE = new File("./GeoEditor.ini");
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
	public static String LOOK_AND_FEEL = "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel";
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
	public static int MAX_NEIGHBOR_TEXTURES = 2;
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

	public static final LookAndFeelInfo[] getInstalledLookAndFeels() {
		return LOOK_AND_FEEL_INFOS;
	}

	public static final LookAndFeelInfo getLookAndFeel(final String className, final LookAndFeelInfo dftl) {
		for (int i = LOOK_AND_FEEL_INFOS.length; i-- > 0;) {
			if (LOOK_AND_FEEL_INFOS[i].getClassName().equals(className))
				return LOOK_AND_FEEL_INFOS[i];
		}
		return dftl;
	}

	public static final LookAndFeelInfo getActiveLookAndFeel() {
		return getLookAndFeel(UIManager.getLookAndFeel().getClass().getName(), null);
	}

	static {
		final LookAndFeelInfo[] temp = UIManager.getInstalledLookAndFeels();
		LOOK_AND_FEEL_INFOS = new LookAndFeelInfo[temp.length];
		for (int i = temp.length; i-- > 0;) {
			LOOK_AND_FEEL_INFOS[i] = new LookAndFeelInfo(temp[i].getName(), temp[i].getClassName()) {
				@Override
				public final String toString() {
					return getName();
				}
			};
		}
	}

	public static final void load() {
		try {
			if (CONFIG_FILE.isFile()) {
				PROPERTIES.load(CONFIG_FILE);

				PATH_TO_GEO_FILES = PROPERTIES.getProperty("PATH_TO_GEO_FILES", PATH_TO_GEO_FILES);
				TERRAIN_DEFAULT_ON = Boolean
						.parseBoolean(PROPERTIES.getProperty("TERRAIN_DEFAULT_ON", String.valueOf(TERRAIN_DEFAULT_ON)));
				VIS_GRID_RANGE = Integer
						.parseInt(PROPERTIES.getProperty("VIS_GRID_RANGE", String.valueOf(VIS_GRID_RANGE)));
				LOOK_AND_FEEL = PROPERTIES.getProperty("LOOK_AND_FEEL", LOOK_AND_FEEL);
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
				MAX_NEIGHBOR_TEXTURES = Math.max(1, Math.min(8, parseIntProperty("MAX_NEIGHBOR_TEXTURES", MAX_NEIGHBOR_TEXTURES)));
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
			}
		} catch (final Exception e) {
			e.printStackTrace();
		} finally {
			checkConfigs();
		}

		try {
			final LookAndFeelInfo lookAndFeelInfo = getLookAndFeel(LOOK_AND_FEEL, null);
			if (lookAndFeelInfo != null)
				UIManager.setLookAndFeel(lookAndFeelInfo.getClassName());
		} catch (final Exception e) {
			e.printStackTrace();
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
		PROPERTIES.put("LOOK_AND_FEEL", String.valueOf(LOOK_AND_FEEL));
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

		PROPERTIES.save(CONFIG_FILE);
	}

	@SuppressWarnings("serial")
	private static final class ConfigProperties extends Properties {
		ConfigProperties() {
		}

		final void load(final File file) throws Exception {
			try (FileInputStream fis = new FileInputStream(file)) {
				super.clear();
				super.load(fis);
			}
		}

		final void save(final File file) {
			try (FileWriter fw = new FileWriter(file)) {
				super.store(fw, "G3DEditor Config");
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

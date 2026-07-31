/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.editor;

import java.awt.Color;

/** Mutable runtime settings owned exclusively by the JavaFX/LWJGL editor. */
final class EditorSettings {
	static int MINI_MAP_SIZE = 144;
	static String PATH_TO_GEO_FILES = "./data/geodata/";
	static boolean USE_TRANSPARENCY = true;
	static boolean DRAW_OUTLINE;
	static boolean CIRCULAR_GEODATA_GRID;
	static int SELECTION_MOVE_MAX_Z = 32;
	static int SELECTION_HEIGHT_STEP = 8;
	static int NSWE_TEXTURE_ID = 5;

	static int COLOR_GUI_SELECTED = Color.YELLOW.getRGB();
	static int COLOR_FLAT_NORMAL = Color.BLUE.getRGB();
	static int COLOR_FLAT_HIGHLIGHTED = Color.CYAN.getRGB();
	static int COLOR_FLAT_SELECTED = Color.MAGENTA.getRGB();
	static int COLOR_COMPLEX_NORMAL = Color.GREEN.getRGB();
	static int COLOR_COMPLEX_HIGHLIGHTED = Color.CYAN.getRGB();
	static int COLOR_COMPLEX_SELECTED = Color.MAGENTA.getRGB();
	static int COLOR_MULTILAYER_NORMAL = Color.RED.getRGB();
	static int COLOR_MULTILAYER_HIGHLIGHTED = Color.CYAN.getRGB();
	static int COLOR_MULTILAYER_SELECTED = Color.MAGENTA.getRGB();
	static int COLOR_MULTILAYER_NORMAL_SPECIAL = mix(Color.WHITE, Color.RED, 1.125f);
	static int COLOR_MULTILAYER_HIGHLIGHTED_SPECIAL = mix(Color.WHITE, Color.CYAN, 1.125f);
	static int COLOR_MULTILAYER_SELECTED_SPECIAL = mix(Color.WHITE, Color.MAGENTA, 1.125f);

	private EditorSettings() {
	}

	static int normalizeSelectionMoveMaxZ(final int value) {
		if (value <= 0)
			return 0;
		final long rounded = (((long) value + 4L) / 8L) * 8L;
		return (int) Math.max(8L, Math.min(32760L, rounded));
	}

	private static int mix(final Color first, final Color second, final float ratio) {
		return new Color(mix(first.getRed(), second.getRed(), ratio),
				mix(first.getGreen(), second.getGreen(), ratio),
				mix(first.getBlue(), second.getBlue(), ratio),
				mix(first.getAlpha(), second.getAlpha(), ratio)).getRGB();
	}

	private static int mix(final int first, final int second, final float ratio) {
		return Math.min(((int) (first * ratio) + second) / 2, 255);
	}
}

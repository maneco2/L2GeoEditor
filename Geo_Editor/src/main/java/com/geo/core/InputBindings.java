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
package com.geo.core;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * Central registry for editable keyboard controls.
 *
 * @author Maneco2
 * @version 1.0
 */
public final class InputBindings {
	public enum Action {
		MOVE_FORWARD("Move forward", KeyEvent.VK_W, 0),
		MOVE_BACKWARD("Move backward", KeyEvent.VK_S, 0),
		MOVE_LEFT("Move left", KeyEvent.VK_A, 0),
		MOVE_RIGHT("Move right", KeyEvent.VK_D, 0),
		MOVE_UP("Move up", KeyEvent.VK_Q, 0),
		MOVE_DOWN("Move down", KeyEvent.VK_E, 0),
		MOVE_FASTER("Move faster", KeyEvent.VK_SPACE, 0),
		TOGGLE_TEXTURED_TERRAIN("Toggle textured terrain", KeyEvent.VK_T, 0),
		TOGGLE_TERRAIN_WIREFRAME("Toggle terrain wireframe", KeyEvent.VK_R, 0),
		TOGGLE_GEODATA_GRID("Toggle geodata grid", KeyEvent.VK_G, 0),
		FREEZE_GEODATA_GRID("Freeze or unfreeze grid", KeyEvent.VK_F, 0),
		SELECT_NORTH("Move selected cell north", KeyEvent.VK_UP, 0),
		SELECT_SOUTH("Move selected cell south", KeyEvent.VK_DOWN, 0),
		SELECT_WEST("Move selected cell west", KeyEvent.VK_LEFT, 0),
		SELECT_EAST("Move selected cell east", KeyEvent.VK_RIGHT, 0),
		RAISE_SELECTED_HEIGHT("Raise selected height", KeyEvent.VK_PAGE_UP, 0),
		LOWER_SELECTED_HEIGHT("Lower selected height", KeyEvent.VK_PAGE_DOWN, 0),
		UNDO_EDIT("Undo edit", KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK),
		REDO_EDIT("Redo edit", KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
		OPEN_BLOCK_ACTIONS("Open selected-block actions", KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK);

		private final String label;
		private final int defaultKeyCode;
		private final int defaultModifiers;
		private int keyCode;
		private int modifiers;

		Action(final String label, final int defaultKeyCode, final int defaultModifiers) {
			this.label = label;
			this.defaultKeyCode = defaultKeyCode;
			this.defaultModifiers = defaultModifiers;
			reset();
		}

		public String getLabel() {
			return label;
		}

		public int getKeyCode() {
			return keyCode;
		}

		public int getModifiers() {
			return modifiers;
		}

		public String getDisplayText() {
			final StringBuilder display = new StringBuilder();
			appendModifier(display, modifiers, InputEvent.CTRL_DOWN_MASK, "Ctrl");
			appendModifier(display, modifiers, InputEvent.ALT_DOWN_MASK, "Alt");
			appendModifier(display, modifiers, InputEvent.SHIFT_DOWN_MASK, "Shift");
			appendModifier(display, modifiers, InputEvent.META_DOWN_MASK, "Meta");
			return display.append(KeyEvent.getKeyText(keyCode)).toString();
		}

		private void set(final int newKeyCode, final int newModifiers) {
			keyCode = newKeyCode;
			modifiers = normalizeModifiers(newModifiers);
		}

		private void reset() {
			set(defaultKeyCode, defaultModifiers);
		}
	}

	private static final int SUPPORTED_MODIFIERS = InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
			| InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK;

	private InputBindings() {
	}

	public static boolean matches(final Action action, final int keyCode, final int modifiers) {
		return action.keyCode == keyCode && action.modifiers == normalizeModifiers(modifiers);
	}

	public static Action findConflict(final Action changedAction, final int keyCode, final int modifiers) {
		for (final Action action : Action.values())
			if (action != changedAction && matches(action, keyCode, modifiers))
				return action;
		return null;
	}

	public static boolean set(final Action action, final int keyCode, final int modifiers) {
		if (keyCode == KeyEvent.VK_UNDEFINED || isModifierKey(keyCode)
				|| findConflict(action, keyCode, modifiers) != null)
			return false;
		action.set(keyCode, modifiers);
		return true;
	}

	public static void resetAll() {
		for (final Action action : Action.values())
			action.reset();
	}

	/** Clears defaults temporarily so swapped bindings can be restored without false conflicts. */
	public static void beginConfigurationLoad() {
		for (final Action action : Action.values())
			action.set(KeyEvent.VK_UNDEFINED, 0);
	}

	/** Restores defaults only for missing or invalid configuration entries. */
	public static void finishConfigurationLoad() {
		for (final Action action : Action.values())
			if (action.keyCode == KeyEvent.VK_UNDEFINED)
				action.reset();
	}

	public static String serialize(final Action action) {
		return action.keyCode + ":" + action.modifiers;
	}

	public static void deserialize(final Action action, final String value) {
		if (value == null || value.isBlank())
			return;
		final String[] parts = value.trim().split(":", 2);
		try {
			final int keyCode = Integer.parseInt(parts[0]);
			final int modifiers = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
			set(action, keyCode, modifiers);
		} catch (final NumberFormatException ignored) {
			// Keep the default binding when a configuration entry is invalid.
		}
	}

	public static String propertyName(final Action action) {
		return "KEY_BINDING_" + action.name().toUpperCase(Locale.ROOT);
	}

	private static int normalizeModifiers(final int modifiers) {
		return modifiers & SUPPORTED_MODIFIERS;
	}

	private static void appendModifier(final StringBuilder display, final int modifiers,
			final int mask, final String name) {
		if ((modifiers & mask) != 0)
			display.append(name).append(" + ");
	}

	private static boolean isModifierKey(final int keyCode) {
		return keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_SHIFT
				|| keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_ALT_GRAPH;
	}
}

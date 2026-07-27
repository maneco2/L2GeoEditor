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
package com.jogl;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Checks the block context-menu input gesture.
 *
 * @author Maneco2
 * @version 1.0
 */
public final class AWTInputTest {
	public static void main(final String[] args) {
		assertState(true, KeyEvent.VK_1, true, true, "Alt + 1 with selection");
		assertState(false, KeyEvent.VK_1, false, true, "1 without Alt");
		assertState(false, KeyEvent.VK_1, true, false, "Alt + 1 without selection");
		assertState(false, KeyEvent.VK_NUMPAD1, true, true, "Alt + numpad 1");
		editableBindings();
		cameraSpeed();
		System.out.println("AWTInputTest OK");
	}

	private static void cameraSpeed() {
		check(Math.abs(AWTInput.calculateMoveDistance(1000d, false, 100) - 20d) < 0.0001d,
				"default camera speed");
		check(Math.abs(AWTInput.calculateMoveDistance(1000d, false, 50) - 10d) < 0.0001d,
				"half camera speed");
		check(Math.abs(AWTInput.calculateMoveDistance(1000d, true, 200) - 200d) < 0.0001d,
				"fast doubled camera speed");
	}

	private static void editableBindings() {
		InputBindings.resetAll();
		check(InputBindings.matches(InputBindings.Action.MOVE_FORWARD, KeyEvent.VK_W, 0),
				"default forward binding");
		check(InputBindings.matches(InputBindings.Action.SELECT_NORTH, KeyEvent.VK_UP, 0),
				"default selected-cell north binding");
		check(InputBindings.matches(InputBindings.Action.SELECT_EAST, KeyEvent.VK_RIGHT, 0),
				"default selected-cell east binding");
		check(InputBindings.matches(InputBindings.Action.RAISE_SELECTED_HEIGHT, KeyEvent.VK_PAGE_UP, 0),
				"default raise-height binding");
		check(InputBindings.matches(InputBindings.Action.LOWER_SELECTED_HEIGHT, KeyEvent.VK_PAGE_DOWN, 0),
				"default lower-height binding");
		check(InputBindings.set(InputBindings.Action.MOVE_FORWARD, KeyEvent.VK_F2, 0),
				"set editable binding");
		check(InputBindings.matches(InputBindings.Action.MOVE_FORWARD, KeyEvent.VK_F2, 0),
				"updated forward binding");
		check(!InputBindings.set(InputBindings.Action.MOVE_BACKWARD, KeyEvent.VK_F2, 0),
				"reject duplicate binding");
		check(!InputBindings.set(InputBindings.Action.MOVE_BACKWARD, KeyEvent.VK_SHIFT, 0),
				"reject modifier-only binding");
		InputBindings.deserialize(InputBindings.Action.OPEN_BLOCK_ACTIONS,
				KeyEvent.VK_2 + ":" + InputEvent.ALT_DOWN_MASK);
		check(InputBindings.matches(InputBindings.Action.OPEN_BLOCK_ACTIONS, KeyEvent.VK_2,
				InputEvent.ALT_DOWN_MASK), "deserialize binding");
		check(AWTInput.shouldOpenBlockContextMenu(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK, true),
				"custom block action binding");
		InputBindings.beginConfigurationLoad();
		InputBindings.deserialize(InputBindings.Action.MOVE_FORWARD, KeyEvent.VK_S + ":0");
		InputBindings.deserialize(InputBindings.Action.MOVE_BACKWARD, KeyEvent.VK_W + ":0");
		InputBindings.finishConfigurationLoad();
		check(InputBindings.matches(InputBindings.Action.MOVE_FORWARD, KeyEvent.VK_S, 0),
				"load swapped forward binding");
		check(InputBindings.matches(InputBindings.Action.MOVE_BACKWARD, KeyEvent.VK_W, 0),
				"load swapped backward binding");
		InputBindings.resetAll();
	}

	private static void assertState(final boolean expected, final int keyCode, final boolean altDown,
			final boolean selected, final String message) {
		final boolean actual = AWTInput.shouldOpenBlockContextMenu(keyCode, altDown, selected);
		if (actual != expected)
			throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}

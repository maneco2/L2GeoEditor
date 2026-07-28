/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.jogl;

import java.awt.Rectangle;

/** Verifies minimap alignment and bounds at common canvas resolutions. */
public final class MiniMapLayoutTest {
	public static void main(final String[] args) {
		check(MiniMapLayout.calculateBounds(1920, 969, 320, 0)
				.equals(new Rectangle(0, 649, 320, 320)), "full HD alignment");
		check(MiniMapLayout.calculateBounds(1024, 697, 320, 0)
				.equals(new Rectangle(0, 377, 320, 320)), "minimum window alignment");
		check(MiniMapLayout.calculateBounds(240, 180, 320, 0)
				.equals(new Rectangle(0, 0, 180, 180)), "small canvas clamp");
		check(MiniMapLayout.calculateBounds(800, 600, 144, 1)
				.equals(new Rectangle(1, 455, 144, 144)), "shared margin");
		System.out.println("MiniMapLayoutTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}

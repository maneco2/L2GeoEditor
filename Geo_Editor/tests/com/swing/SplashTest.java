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
package com.swing;

import java.awt.Rectangle;

/** Tests aspect-ratio-safe fullscreen splash scaling. @author Maneco2 @version 1.0 */
public final class SplashTest {
	public static void main(final String[] args) {
		final Rectangle wide = Splash.calculateCoverBounds(1600, 900, 1920, 1080);
		check(wide.equals(new Rectangle(0, 0, 1920, 1080)), "matching aspect ratio");

		final Rectangle tallTarget = Splash.calculateCoverBounds(1600, 900, 1280, 1024);
		check(tallTarget.height == 1024, "fills target height");
		check(tallTarget.width >= 1280 && tallTarget.x <= 0, "crops horizontally");

		final Rectangle wideTarget = Splash.calculateCoverBounds(1024, 1024, 1920, 1080);
		check(wideTarget.width == 1920, "fills target width");
		check(wideTarget.height >= 1080 && wideTarget.y <= 0, "crops vertically");
		check(Splash.calculateCoverBounds(0, 0, 1920, 1080).isEmpty(), "invalid dimensions");
		check(Splash.calculateFadeDurationMillis(0.015F) >= 1_650L, "smooth fade-out duration");
		check(Splash.calculateFadeDurationMillis(0F) == 0L, "invalid fade step");
		System.out.println("SplashTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}

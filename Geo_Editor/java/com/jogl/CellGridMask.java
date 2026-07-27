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

/**
 * Geometry-independent mask used by the future cell-level geodata renderer.
 * @author Maneco2
 * @version 1.0
 */
public final class CellGridMask {
	private CellGridMask() {
	}

	public static boolean contains(final int cellX, final int cellY, final int centerX, final int centerY,
			final int radius) {
		if (radius < 0)
			return false;
		final long dx = (long) cellX - centerX;
		final long dy = (long) cellY - centerY;
		return dx * dx + dy * dy <= (long) radius * radius;
	}

	/** Uses cell centers so the circular edge does not favor block boundaries. */
	public static boolean containsCellCenter(final int cellX, final int cellY, final int centerX,
			final int centerY, final int radius) {
		if (radius < 0)
			return false;
		final double dx = cellX + 0.5d - centerX;
		final double dy = cellY + 0.5d - centerY;
		return dx * dx + dy * dy <= (double) radius * radius;
	}
}

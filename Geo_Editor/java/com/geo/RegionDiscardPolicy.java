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
package com.geo;

/** Limits region disposal work performed during a single render frame. @author Maneco2 @version 1.0 */
public final class RegionDiscardPolicy {
	private RegionDiscardPolicy() {
	}

	public static int frameBudget(final int pendingRegions) {
		if (pendingRegions > 48)
			return 4;
		if (pendingRegions > 16)
			return 8;
		return 16;
	}
}

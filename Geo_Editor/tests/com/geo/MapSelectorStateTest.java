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

import com.swing.mapselector.MapSelectorState;

public final class MapSelectorStateTest {
    public static void main(final String[] args) {
        final MapSelectorState state = new MapSelectorState();
        check(state.getView() == MapSelectorState.View.WORLD, "starts in world map");
        state.selectRegion(12, 24);
        check(state.getView() == MapSelectorState.View.REGION, "click opens region");
        check(state.getRegionX() == 12 && state.getRegionY() == 24, "region coordinates");
        state.escape();
        check(state.getView() == MapSelectorState.View.WORLD, "first escape returns world");
        state.escape();
        check(state.getView() == MapSelectorState.View.CLOSED, "second escape closes selector");
        System.out.println("MapSelectorStateTest OK");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }
}

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
package com.swing.mapselector;

/**
 * State machine for the map selector UI. It contains no Swing or OpenGL code.
 * @author Maneco2
 * @version 1.0
 */
public final class MapSelectorState {
    public enum View { WORLD, REGION, CLOSED }

    private View view = View.WORLD;
    private int regionX = -1;
    private int regionY = -1;

    public View getView() { return view; }
    public int getRegionX() { return regionX; }
    public int getRegionY() { return regionY; }

    public void selectRegion(final int x, final int y) {
        if (view == View.CLOSED) throw new IllegalStateException("Selector is closed");
        regionX = x;
        regionY = y;
        view = View.REGION;
    }

    public void escape() {
        if (view == View.REGION) view = View.WORLD;
        else if (view == View.WORLD) view = View.CLOSED;
    }
}

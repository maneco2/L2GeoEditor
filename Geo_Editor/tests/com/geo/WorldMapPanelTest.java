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

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import com.swing.mapselector.WorldMapPanel;

public final class WorldMapPanelTest {
    public static void main(final String[] args) {
        final AtomicInteger x = new AtomicInteger(-1);
        final AtomicInteger y = new AtomicInteger(-1);
        final WorldMapPanel panel = new WorldMapPanel(new BufferedImage(170, 160, BufferedImage.TYPE_INT_RGB), 17, 16,
                (regionX, regionY) -> { x.set(regionX); y.set(regionY); });
        panel.setSize(170, 160);
        panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(), 0, 55, 50, 1, false, java.awt.event.MouseEvent.BUTTON1));
        if (x.get() != 5 || y.get() != 5) throw new AssertionError("world map click mapping");
        System.out.println("WorldMapPanelTest OK");
    }
}

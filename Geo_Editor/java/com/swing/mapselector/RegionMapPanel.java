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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;
import javax.swing.JPanel;

/** Region preview and coordinate marker panel. @author Maneco2 @version 1.0 */
@SuppressWarnings("serial")
public final class RegionMapPanel extends JPanel {
    private final BufferedImage image;
    private final BiConsumer<Integer, Integer> clickListener;
    // Keep the marker in image-relative coordinates because Swing scales the
    // preview to the available dialog size after the panel is created.
    private double markerU = -1d, markerV = -1d;
    public RegionMapPanel(final BufferedImage image, final BiConsumer<Integer, Integer> clickListener) {
        this.image = image; this.clickListener = clickListener;
        setPreferredSize(new java.awt.Dimension(image.getWidth(), image.getHeight()));
        final java.awt.event.MouseAdapter selector = new java.awt.event.MouseAdapter() {
            private void select(final java.awt.event.MouseEvent event) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return;
                final Rectangle bounds = squareBounds(getWidth(), getHeight());
                if (!bounds.contains(event.getPoint())) return;
                markerU = Math.max(0d, Math.min(1d, (event.getX() - bounds.x) / (double) Math.max(1, bounds.width)));
                markerV = Math.max(0d, Math.min(1d, (event.getY() - bounds.y) / (double) Math.max(1, bounds.height)));
                repaint();
                clickListener.accept((int) (markerU * 256d), (int) (markerV * 256d));
            }
            @Override public void mousePressed(final java.awt.event.MouseEvent event) { select(event); }
            @Override public void mouseDragged(final java.awt.event.MouseEvent event) { select(event); }
        };
        addMouseListener(selector); addMouseMotionListener(selector);
    }

    public void setMarkerToCenter() {
        markerU = 0.5d;
        markerV = 0.5d;
        repaint();
    }

    /** Positions the preview marker using the region's 256-block grid. */
    public void setMarkerAtCell(final int cellX, final int cellY) {
        markerU = Math.max(0d, Math.min(1d, cellX / 256d));
        markerV = Math.max(0d, Math.min(1d, cellY / 256d));
        repaint();
    }

    @Override protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Rectangle bounds = squareBounds(getWidth(), getHeight());
        graphics.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
        if (markerU >= 0d) {
            final int markerX = bounds.x + (int) Math.round(markerU * Math.max(0, bounds.width - 1));
            final int markerY = bounds.y + (int) Math.round(markerV * Math.max(0, bounds.height - 1));
              graphics.setColor(new Color(255, 40, 40, 90));
              graphics.fillOval(markerX - 12, markerY - 12, 24, 24);
              graphics.setColor(Color.WHITE);
              graphics.drawOval(markerX - 12, markerY - 12, 24, 24);
              graphics.setColor(Color.RED);
              graphics.drawOval(markerX - 8, markerY - 8, 16, 16);
              graphics.fillOval(markerX - 3, markerY - 3, 6, 6);
        }
    }

    private static Rectangle squareBounds(final int width, final int height) {
        final int size = Math.max(0, Math.min(width, height));
        return new Rectangle((width - size) / 2, (height - size) / 2, size, size);
    }
}

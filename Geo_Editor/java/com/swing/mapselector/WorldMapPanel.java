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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;

import javax.swing.JPanel;

/**
 * Minimal world-map view: one click selects a region; double-click has no special meaning.
 * @author Maneco2
 * @version 1.0
 */
@SuppressWarnings("serial")
public final class WorldMapPanel extends JPanel {
    @FunctionalInterface
    public interface RegionSelectionListener { void regionSelected(int regionX, int regionY); }

    private final BufferedImage image;
    private final RegionSelectionListener listener;
    private final int columns;
    private final int rows;
    private int selectedX = -1;
    private int selectedY = -1;

    public WorldMapPanel(final BufferedImage image, final int columns, final int rows,
            final RegionSelectionListener listener) {
        this.image = Objects.requireNonNull(image, "image");
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("Invalid map grid");
        this.columns = columns;
        this.rows = rows;
        this.listener = Objects.requireNonNull(listener, "listener");
        setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(final MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) return;
                final Point cell = cellAt(event.getPoint());
                if (cell == null) return;
                selectedX = cell.x;
                selectedY = cell.y;
                repaint();
                listener.regionSelected(selectedX, selectedY);
            }
        });
    }

    private Point cellAt(final Point point) {
        final Rectangle bounds = calculateMapBounds(getWidth(), getHeight(), columns, rows);
        if (!bounds.contains(point)) return null;
        return new Point((point.x - bounds.x) * columns / Math.max(1, bounds.width),
                (point.y - bounds.y) * rows / Math.max(1, bounds.height));
    }

    @Override protected void paintComponent(final Graphics graphics) {
        super.paintComponent(graphics);
        final Rectangle bounds = calculateMapBounds(getWidth(), getHeight(), columns, rows);
        graphics.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, null);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, bounds.width / 95)));
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) {
            final int left = bounds.x + x * bounds.width / columns;
            final int top = bounds.y + y * bounds.height / rows;
            graphics.setColor(new Color(255, 255, 255, 210));
            graphics.drawString((x + 10) + "_" + (y + 10), left + 3, top + 14);
        }
        if (selectedX >= 0) {
            final int x = bounds.x + selectedX * bounds.width / columns;
            final int y = bounds.y + selectedY * bounds.height / rows;
            final int w = Math.max(1, bounds.width / columns);
            final int h = Math.max(1, bounds.height / rows);
            graphics.setColor(Color.RED);
            graphics.drawRect(x, y, w - 1, h - 1);
        }
    }

    public static Rectangle calculateMapBounds(final int width, final int height, final int columns, final int rows) {
        if (width <= 0 || height <= 0 || columns <= 0 || rows <= 0) return new Rectangle();
        final double scale = Math.min((double) width / columns, (double) height / rows);
        final int mapWidth = Math.max(1, (int) Math.floor(columns * scale));
        final int mapHeight = Math.max(1, (int) Math.floor(rows * scale));
        return new Rectangle((width - mapWidth) / 2, (height - mapHeight) / 2, mapWidth, mapHeight);
    }
}

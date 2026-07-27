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

import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

/** @author Maneco2 @version 1.0 */
public final class NeighborLoadingOverlayTest {
	public static void main(final String[] args) throws Exception {
		check(NeighborLoadingOverlay.percentage(0, 24) == 0, "zero progress");
		check(NeighborLoadingOverlay.percentage(17, 24) == 71, "rounded progress");
		check(NeighborLoadingOverlay.percentage(24, 24) == 100, "complete progress");
		check(NeighborLoadingOverlay.percentage(150, 120) == 100, "upper clamp");
		check(NeighborLoadingOverlay.percentage(0, 0) == 100, "empty window");
		SwingUtilities.invokeAndWait(() -> {
			final NeighborLoadingOverlay overlay = new NeighborLoadingOverlay();
			overlay.setSize(1280, 720);
			overlay.start(24, "Preparing geodata");
			overlay.updateProgress(17, 24, "Preparing geodata");
			overlay.doLayout();
			final BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
			overlay.paint(image.getGraphics());
			final int outsideAlpha = image.getRGB(20, 20) >>> 24;
			final int cardAlpha = image.getRGB(440, 250) >>> 24;
			check(cardAlpha > outsideAlpha, "center card was not painted");
		});
		System.out.println("NeighborLoadingOverlayTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}

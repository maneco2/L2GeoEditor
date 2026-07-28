/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.jogl;

import java.awt.Rectangle;

/** Shared minimap bounds for OpenGL rendering and AWT hit testing. */
public final class MiniMapLayout {
	private MiniMapLayout() {
	}

	public static Rectangle calculateBounds(final int canvasWidth, final int canvasHeight,
			final int requestedSize, final int margin) {
		final int safeMargin = Math.max(0, margin);
		final int availableWidth = Math.max(0, canvasWidth - safeMargin * 2);
		final int availableHeight = Math.max(0, canvasHeight - safeMargin * 2);
		final int size = Math.max(0, Math.min(requestedSize, Math.min(availableWidth, availableHeight)));
		return new Rectangle(safeMargin, canvasHeight - safeMargin - size, size, size);
	}
}

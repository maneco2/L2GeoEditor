/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.jogl.renderer;

import com.geo.GeoEngine;

/** Deterministic VBO GLSL batching rules used by rendering metrics. */
public final class RendererBatchPolicy {
	private static final int MULTILAYER_BATCH_SIZE = 64;

	private RendererBatchPolicy() {
	}

	public static int drawCalls(final int blockType, final int elements) {
		if (elements <= 0)
			return 0;
		if (blockType == GeoEngine.GEO_BLOCK_TYPE_MULTILAYER)
			return (elements + MULTILAYER_BATCH_SIZE - 1) / MULTILAYER_BATCH_SIZE;
		return 1;
	}
}

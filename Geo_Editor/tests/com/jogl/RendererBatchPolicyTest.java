/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.jogl;

import com.geo.GeoEngine;
import com.jogl.renderer.RendererBatchPolicy;

/** Verifies the reported VBO GLSL draw-call count. */
public final class RendererBatchPolicyTest {
	public static void main(final String[] args) {
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_FLAT, 1) == 1, "flat batch");
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_COMPLEX, 64) == 1, "complex batch");
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, 1) == 1, "single multilayer");
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, 64) == 1, "full multilayer batch");
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, 65) == 2, "split multilayer batch");
		check(RendererBatchPolicy.drawCalls(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, 0) == 0, "empty batch");
		System.out.println("RendererBatchPolicyTest OK");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}

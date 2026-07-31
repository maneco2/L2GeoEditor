/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

/** UI- and renderer-neutral Lineage II coordinate conversions. */
public final class GeoCoordinates {
	public static final int CELL_SHIFT = 4;
	public static final int BLOCK_CELL_SHIFT = 3;
	public static final int REGION_BLOCK_SHIFT = 8;
	public static final int REGION_GEO_SHIFT = 11;
	public static final int REGION_SIZE = 1 << REGION_BLOCK_SHIFT;
	public static final int REGION_GEO_SIZE = 1 << REGION_GEO_SHIFT;
	public static final int MAP_MIN_X = -327680;
	public static final int MAP_MIN_Y = -262144;

	private GeoCoordinates() {
	}

	public static int geoXFromWorld(final int worldX) {
		return (worldX - MAP_MIN_X) >> CELL_SHIFT;
	}

	public static int geoYFromWorld(final int worldY) {
		return (worldY - MAP_MIN_Y) >> CELL_SHIFT;
	}

	public static int worldXFromGeo(final int geoX) {
		return (geoX << CELL_SHIFT) + MAP_MIN_X;
	}

	public static int worldYFromGeo(final int geoY) {
		return (geoY << CELL_SHIFT) + MAP_MIN_Y;
	}

	public static int regionFromGeo(final int geo) {
		return geo >> REGION_GEO_SHIFT;
	}

	public static int blockFromGeo(final int geo) {
		return (geo >> BLOCK_CELL_SHIFT) % REGION_SIZE;
	}

	public static int cellFromGeo(final int geo) {
		return geo & 7;
	}

	public static int geoFromRegionBlock(final int region, final int block) {
		return (region << REGION_GEO_SHIFT) + (block << BLOCK_CELL_SHIFT);
	}
}

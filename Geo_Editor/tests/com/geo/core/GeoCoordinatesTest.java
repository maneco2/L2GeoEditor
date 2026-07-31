/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

public final class GeoCoordinatesTest {
	public static void main(final String[] args) {
		check(GeoCoordinates.geoXFromWorld(83584) == 25704, "world to geo X");
		check(GeoCoordinates.geoYFromWorld(148464) == 25663, "world to geo Y");
		check(GeoCoordinates.worldXFromGeo(25704) == 83584, "geo to world X");
		check(GeoCoordinates.worldYFromGeo(25663) == 148464, "geo to world Y");
		check(GeoCoordinates.regionFromGeo(25704) == 12, "region X");
		check(GeoCoordinates.blockFromGeo(25704) == 141, "block X");
		check(GeoCoordinates.cellFromGeo(25663) == 7, "cell Y");
		check(GeoCoordinates.geoFromRegionBlock(12, 141) == 25704, "region block");
		System.out.println("GeoCoordinatesTest OK");
	}

	private static void check(final boolean condition, final String description) {
		if (!condition)
			throw new AssertionError(description);
	}
}

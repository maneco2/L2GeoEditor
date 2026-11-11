/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

import java.io.File;

public final class RegionSnapshotLoaderTest {
	public static void main(final String[] args) throws Exception {
		final File file = new File(System.getProperty("geoeditor.realGeodata",
				"src/main/resources/geodata/23_18.l2j"));
		final RegionSnapshot snapshot = RegionSnapshotLoader.load(file);
		check(snapshot.name().equals("23_18"), "name");
		check(snapshot.regionX() == 13 && snapshot.regionY() == 8, "region");
		check(snapshot.size() == 256, "size");
		check(snapshot.blockType(0, 0) == 1, "real complex block");
		check(snapshot.blockType(128, 128) == 2, "real multilayer block");
		check(snapshot.blockType(255, 255) == 0, "real flat block");
		System.out.println("RegionSnapshotLoaderTest OK - real region="
				+ snapshot.name());
	}

	private static void check(final boolean condition, final String description) {
		if (!condition)
			throw new AssertionError(description);
	}
}

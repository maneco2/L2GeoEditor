/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo;

import com.util.GeoStreamReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Read-only coordinate, height and NSWE fixtures from a real L2J region. */
public final class RealGeodataFixtureTest {
	private static final File REGION_FILE = new File("Build/data/geodata/22_22.l2j");
	private static final long EXPECTED_FILE_SIZE = 8_210_356L;
	private static final String EXPECTED_SHA256 =
			"8CC9CFF435EB6899C8D0C897473A6A904663E70C860D2C19F698A3220B51B733";
	private static final Fixture[] FIXTURES = {
			new Fixture(25704, 25663, -3210, 83584, 148464, 141, 135, 0, 7, 2, 1, -3400, 15),
			new Fixture(25600, 25600, 0, 81920, 147456, 128, 128, 0, 0, 1, 1, -3176, 7),
			new Fixture(24576, 24576, 0, 65536, 131072, 0, 0, 0, 0, 1, 1, -3720, 15),
			new Fixture(26623, 26623, 0, 98288, 163824, 255, 255, 7, 7, 0, 1, -4120, 15)
	};

	public static void main(final String[] args) throws Exception {
		if (!REGION_FILE.isFile()) {
			System.out.println("RealGeodataFixtureTest skipped - local 22_22.l2j not found");
			return;
		}
		if (REGION_FILE.length() != EXPECTED_FILE_SIZE
				|| !sha256(REGION_FILE).equals(EXPECTED_SHA256)) {
			System.out.println("RealGeodataFixtureTest skipped - local 22_22.l2j is not the known fixture");
			return;
		}
		final GeoRegion region;
		try (FileInputStream input = new FileInputStream(REGION_FILE);
				BufferedInputStream buffered = new BufferedInputStream(input)) {
			region = new GeoRegion(12, 12, GeoStreamReader.wrap(buffered), true, REGION_FILE);
		}
		try {
			for (final Fixture fixture : FIXTURES)
				validate(region, fixture);
		} finally {
			region.releaseForDiscard();
		}
		System.out.println("RealGeodataFixtureTest OK - fixtures=" + FIXTURES.length);
	}

	private static void validate(final GeoRegion region, final Fixture fixture) {
		final GeoCell cell = region.nGetCell(fixture.geoX(), fixture.geoY(), fixture.worldZ());
		check(GeoEngine.getWorldX(fixture.geoX()) == fixture.worldX(), "world X");
		check(GeoEngine.getWorldY(fixture.geoY()) == fixture.worldY(), "world Y");
		check(GeoEngine.getGeoX(fixture.worldX()) == fixture.geoX(), "geo X round trip");
		check(GeoEngine.getGeoY(fixture.worldY()) == fixture.geoY(), "geo Y round trip");
		check(GeoEngine.getBlockXY(fixture.geoX()) == fixture.blockX(), "block X");
		check(GeoEngine.getBlockXY(fixture.geoY()) == fixture.blockY(), "block Y");
		check(GeoEngine.getCellXY(fixture.geoX()) == fixture.cellX(), "cell X");
		check(GeoEngine.getCellXY(fixture.geoY()) == fixture.cellY(), "cell Y");
		check(region.nGetType(fixture.geoX(), fixture.geoY()) == fixture.type(), "block type");
		check(region.nGetLayerCount(fixture.geoX(), fixture.geoY()) == fixture.layers(), "layer count");
		check(cell.getHeight() == fixture.height(), "height");
		check(cell.getNSWE() == fixture.nswe(), "NSWE");
	}

	private static String sha256(final File file) throws Exception {
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = new BufferedInputStream(new FileInputStream(file));
				DigestInputStream hashing = new DigestInputStream(input, digest)) {
			hashing.transferTo(OutputStream.nullOutputStream());
		}
		return HexFormat.of().withUpperCase().formatHex(digest.digest());
	}

	private static void check(final boolean condition, final String description) {
		if (!condition)
			throw new AssertionError("Real geodata fixture mismatch: " + description);
	}

	private record Fixture(int geoX, int geoY, int worldZ, int worldX, int worldY,
			int blockX, int blockY, int cellX, int cellY, int type, int layers, int height, int nswe) {
	}
}

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
	private static final String REGION_NAME = "23_18";
	private static final File REGION_FILE = new File(System.getProperty("geoeditor.realGeodata",
			"src/main/resources/geodata/23_18.l2j"));
	private static final long EXPECTED_FILE_SIZE = 11_065_818L;
	private static final String EXPECTED_SHA256 =
			"F9FDD9A44BF0DA1FB4C54D7B8FCE1D18FD717E57717D3DDAF82CE226A217C656";
	private static final Fixture[] FIXTURES = {
			new Fixture(26624, 16384, 98304, 0, 0, 0, 0, 0, 1, 1, 0, -3696, 15),
			new Fixture(26625, 16385, 98320, 16, 0, 0, 1, 1, 1, 1, 0, -3696, 15),
			new Fixture(27648, 17408, 114688, 16384, 128, 128, 0, 0, 2, 11, 0, -5096, 15),
			new Fixture(28671, 18431, 131056, 32752, 255, 255, 7, 7, 0, 1, 0, -3720, 15)
	};

	public static void main(final String[] args) throws Exception {
		if (!REGION_FILE.isFile()) {
			System.out.println("RealGeodataFixtureTest skipped - local " + REGION_NAME + ".l2j not found");
			return;
		}
		if (REGION_FILE.length() != EXPECTED_FILE_SIZE
				|| !sha256(REGION_FILE).equals(EXPECTED_SHA256)) {
			System.out.println("RealGeodataFixtureTest skipped - local " + REGION_NAME
					+ ".l2j is not the known fixture");
			return;
		}
		final GeoRegion region;
		try (FileInputStream input = new FileInputStream(REGION_FILE);
				BufferedInputStream buffered = new BufferedInputStream(input)) {
			region = new GeoRegion(13, 8, GeoStreamReader.wrap(buffered), true, REGION_FILE);
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
		final GeoCell cell = region.nGetCellByLayer(fixture.geoX(), fixture.geoY(), fixture.layer());
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

	private record Fixture(int geoX, int geoY, int worldX, int worldY, int blockX, int blockY,
			int cellX, int cellY, int type, int layers, int layer, int height, int nswe) {
	}
}

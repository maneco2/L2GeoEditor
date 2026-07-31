/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.geo.GeoEngine;
import com.geo.GeoRegion;
import com.util.GeoReader;
import com.util.GeoStreamReader;

/**
 * Lightweight read-only loader for neighboring regions.
 *
 * It intentionally parses the L2J stream directly instead of constructing an
 * editable {@link GeoRegion}. Neighbor summaries need only one type and maximum
 * height per block, so materializing every cell and the restore copy caused
 * large temporary heaps during streaming.
 */
public final class RegionSnapshotLoader {
	private static final Pattern FILE_NAME =
			Pattern.compile("(\\d{2})_(\\d{2})\\.l2j", Pattern.CASE_INSENSITIVE);

	private RegionSnapshotLoader() {
	}

	public static RegionSnapshot load(final File file) throws IOException {
		if (!file.isFile())
			throw new IOException("Geodata region not found: " + file);
		final Matcher matcher = FILE_NAME.matcher(file.getName());
		if (!matcher.matches())
			throw new IOException("Unsupported geodata file name: " + file.getName());
		final int regionX = Integer.parseInt(matcher.group(1)) - 10;
		final int regionY = Integer.parseInt(matcher.group(2)) - 10;
		final int size = GeoCoordinates.REGION_SIZE;
		final short[] heights = new short[size * size];
		final short[] minimumHeights = new short[size * size];
		final byte[] types = new byte[size * size];
		try (FileInputStream input = new FileInputStream(file);
				BufferedInputStream buffered = new BufferedInputStream(input)) {
			final GeoReader reader = GeoStreamReader.wrap(buffered);
			try {
				for (int blockX = 0; blockX < size; blockX++) {
					for (int blockY = 0; blockY < size; blockY++) {
						final byte type = GeoRegion.getType(reader, true);
						final int index = blockY * size + blockX;
						types[index] = type;
						final HeightRange range = readHeightRange(reader, type);
						heights[index] = range.maximum();
						minimumHeights[index] = range.minimum();
					}
				}
			} catch (final RuntimeException e) {
				throw new IOException("Invalid geodata stream: " + file, e);
			}
		}
		return new RegionSnapshot((regionX + 10) + "_" + (regionY + 10),
				regionX, regionY, size, heights, minimumHeights, types);
	}

	private static HeightRange readHeightRange(final GeoReader reader, final byte type) {
		switch (type) {
		case GeoEngine.GEO_BLOCK_TYPE_FLAT: {
			final short height = reader.getShort();
			return new HeightRange(height, height);
		}
		case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
			return readComplexRange(reader);
		case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
			return readMultilayerRange(reader);
		default:
			throw new IllegalArgumentException("Unknown geodata block type: " + type);
		}
	}

	private static HeightRange readComplexRange(final GeoReader reader) {
		short maximum = Short.MIN_VALUE;
		short minimum = Short.MAX_VALUE;
		for (int cell = 0; cell < 64; cell++) {
			final short height = GeoEngine.getHeight(reader.getShort());
			maximum = (short) Math.max(maximum, height);
			minimum = (short) Math.min(minimum, height);
		}
		return new HeightRange(minimum, maximum);
	}

	private static HeightRange readMultilayerRange(final GeoReader reader) {
		short maximum = Short.MIN_VALUE;
		short minimum = Short.MAX_VALUE;
		for (int cell = 0; cell < 64; cell++) {
			final int layers = Byte.toUnsignedInt(reader.get());
			if (!GeoEngine.layersValid(layers))
				throw new IllegalArgumentException("Invalid geodata layer count: " + layers);
			for (int layer = 0; layer < layers; layer++) {
				final short height = GeoEngine.getHeight(reader.getShort());
				maximum = (short) Math.max(maximum, height);
				minimum = (short) Math.min(minimum, height);
			}
		}
		return new HeightRange(minimum, maximum);
	}

	private record HeightRange(short minimum, short maximum) {}
}

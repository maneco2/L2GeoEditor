package com.geo.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.geo.GeoEngine;

/** Small, generated L2J region used by source-only tests. */
final class DeterministicGeodataFixture implements AutoCloseable {
	static final int REGION_X = 12;
	static final int REGION_Y = 12;
	static final int REGION_GEO_START = REGION_X * 2048;
	static final int COMPLEX_LOCAL_X = 8;
	static final int MULTILAYER_LOCAL_X = 16;

	private final Path directory;
	private final Path file;

	private DeterministicGeodataFixture(final Path directory, final Path file) {
		this.directory = directory;
		this.file = file;
	}

	static DeterministicGeodataFixture create() throws IOException {
		final Path directory = Files.createTempDirectory("geoeditor-fixture-");
		final Path file = directory.resolve("22_22.l2j");
		try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(file))) {
			for (int blockX = 0; blockX < 256; blockX++) {
				for (int blockY = 0; blockY < 256; blockY++) {
					if (blockX == 1 && blockY == 0)
						writeComplex(output);
					else if (blockX == 2 && blockY == 0)
						writeMultilayer(output);
					else
						writeFlat(output);
				}
			}
		}
		return new DeterministicGeodataFixture(directory, file);
	}

	Path path() {
		return file;
	}

	private static void writeFlat(final OutputStream output) throws IOException {
		output.write(GeoEngine.GEO_BLOCK_TYPE_FLAT);
		writeShort(output, (short) 128);
	}

	private static void writeComplex(final OutputStream output) throws IOException {
		output.write(GeoEngine.GEO_BLOCK_TYPE_COMPLEX);
		final short value = GeoEngine.convertHeightToHeightAndNSWEALL((short) 256);
		for (int cell = 0; cell < 64; cell++)
			writeShort(output, value);
	}

	private static void writeMultilayer(final OutputStream output) throws IOException {
		output.write(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER);
		final short lower = GeoEngine.convertHeightToHeightAndNSWEALL((short) 64);
		final short upper = GeoEngine.convertHeightToHeightAndNSWEALL((short) 192);
		for (int cell = 0; cell < 64; cell++) {
			output.write(2);
			writeShort(output, upper);
			writeShort(output, lower);
		}
	}

	private static void writeShort(final OutputStream output, final short value)
			throws IOException {
		output.write(value & 0xff);
		output.write(value >>> 8 & 0xff);
	}

	@Override
	public void close() throws IOException {
		Files.deleteIfExists(file);
		Files.deleteIfExists(directory);
	}
}

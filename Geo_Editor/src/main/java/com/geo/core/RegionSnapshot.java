/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

/** Immutable renderer-neutral height/type snapshot of one geodata region. */
public record RegionSnapshot(String name, int regionX, int regionY, int size,
		short[] maximumHeights, short[] minimumHeights, byte[] blockTypes) {
	public RegionSnapshot(final String name, final int regionX, final int regionY,
			final int size, final short[] maximumHeights, final byte[] blockTypes) {
		this(name, regionX, regionY, size, maximumHeights, maximumHeights, blockTypes);
	}

	public RegionSnapshot {
		final int expected = Math.multiplyExact(size, size);
		if (size <= 0 || maximumHeights.length != expected
				|| minimumHeights.length != expected || blockTypes.length != expected)
			throw new IllegalArgumentException("Invalid region snapshot dimensions.");
		maximumHeights = maximumHeights.clone();
		minimumHeights = minimumHeights.clone();
		blockTypes = blockTypes.clone();
	}

	public short maximumHeight(final int x, final int y) {
		return maximumHeights[index(x, y)];
	}

	public byte blockType(final int x, final int y) {
		return blockTypes[index(x, y)];
	}

	public short minimumHeight(final int x, final int y) {
		return minimumHeights[index(x, y)];
	}

	void updateBlockHeights(final int x, final int y,
			final short maximum, final short minimum) {
		final int index = index(x, y);
		maximumHeights[index] = maximum;
		minimumHeights[index] = minimum;
	}

	@Override
	public short[] maximumHeights() {
		return maximumHeights.clone();
	}

	@Override
	public byte[] blockTypes() {
		return blockTypes.clone();
	}

	@Override
	public short[] minimumHeights() {
		return minimumHeights.clone();
	}

	private int index(final int x, final int y) {
		if (x < 0 || y < 0 || x >= size || y >= size)
			throw new IndexOutOfBoundsException("Region coordinate: " + x + "," + y);
		return y * size + x;
	}
}

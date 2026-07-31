/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

import com.geo.GeoEngine;

/**
 * Compact read-only cell/layer data for the active region.
 *
 * Neighboring regions intentionally keep using the lighter {@link RegionSnapshot}.
 */
public final class DetailedRegionSnapshot {
	private final RegionSnapshot summary;
	private final int[] layerOffsets;
	private final short[] packedLayers;

	DetailedRegionSnapshot(final RegionSnapshot summary, final int[] layerOffsets,
			final short[] packedLayers) {
		final int expectedCells = GeoCoordinates.REGION_GEO_SIZE
				* GeoCoordinates.REGION_GEO_SIZE;
		if (layerOffsets.length != expectedCells + 1)
			throw new IllegalArgumentException("Invalid detailed snapshot dimensions.");
		if (layerOffsets[expectedCells] != packedLayers.length)
			throw new IllegalArgumentException("Invalid detailed snapshot layer count.");
		this.summary = summary;
		this.layerOffsets = layerOffsets;
		this.packedLayers = packedLayers;
	}

	public RegionSnapshot summary() {
		return summary;
	}

	public int layerCount(final int localGeoX, final int localGeoY) {
		final int index = index(localGeoX, localGeoY);
		return layerOffsets[index + 1] - layerOffsets[index];
	}

	public short height(final int localGeoX, final int localGeoY, final int layer) {
		return GeoEngine.getHeight(packed(localGeoX, localGeoY, layer));
	}

	public short nswe(final int localGeoX, final int localGeoY, final int layer) {
		return GeoEngine.getNSWE(packed(localGeoX, localGeoY, layer));
	}

	public short packed(final int localGeoX, final int localGeoY, final int layer) {
		final int index = index(localGeoX, localGeoY);
		final int offset = layerOffsets[index];
		final int count = layerOffsets[index + 1] - offset;
		if (layer < 0 || layer >= count)
			throw new IndexOutOfBoundsException("Cell layer: " + layer);
		return packedLayers[offset + layer];
	}

	public int totalLogicalLayers() {
		return packedLayers.length;
	}

	void updatePacked(final int localGeoX, final int localGeoY,
			final int layer, final short packed) {
		final int index = index(localGeoX, localGeoY);
		final int offset = layerOffsets[index];
		final int count = layerOffsets[index + 1] - offset;
		if (layer < 0 || layer >= count)
			throw new IndexOutOfBoundsException("Cell layer: " + layer);
		packedLayers[offset + layer] = packed;
	}

	private static int index(final int localGeoX, final int localGeoY) {
		final int size = GeoCoordinates.REGION_GEO_SIZE;
		if (localGeoX < 0 || localGeoY < 0
				|| localGeoX >= size || localGeoY >= size)
			throw new IndexOutOfBoundsException(
					"Local geodata coordinate: " + localGeoX + "," + localGeoY);
		return localGeoX * size + localGeoY;
	}
}

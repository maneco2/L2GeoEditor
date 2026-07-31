/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.geo.core;

public final class RegionSnapshotLoaderTest {
	public static void main(final String[] args) throws Exception {
		try (DeterministicGeodataFixture fixture =
				DeterministicGeodataFixture.create()) {
			final RegionSnapshot snapshot = RegionSnapshotLoader.load(fixture.path().toFile());
			check(snapshot.name().equals("22_22"), "name");
			check(snapshot.regionX() == 12 && snapshot.regionY() == 12, "region");
			check(snapshot.size() == 256, "size");
			check(snapshot.blockType(0, 0) == 0, "flat block");
			check(snapshot.blockType(1, 0) == 1, "complex block");
			check(snapshot.blockType(2, 0) == 2, "multilayer block");
			try (EditableRegionSession session =
					EditableRegionSession.load(fixture.path().toFile())) {
				final RegionSnapshot editable = session.snapshot().summary();
				for (int y = 0; y < snapshot.size(); y += 17) {
					for (int x = 0; x < snapshot.size(); x += 17) {
						check(snapshot.maximumHeight(x, y) == editable.maximumHeight(x, y),
								"maximum height parity at " + x + "," + y);
						check(snapshot.blockType(x, y) == editable.blockType(x, y),
								"block type parity at " + x + "," + y);
					}
				}
			}
			System.out.println("RegionSnapshotLoaderTest OK - deterministic region="
					+ snapshot.name());
		}
	}

	private static void check(final boolean condition, final String description) {
		if (!condition)
			throw new AssertionError(description);
	}
}

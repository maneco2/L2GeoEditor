package com.geo.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.geo.GeoEngine;

public final class EditableRegionSessionTest {
	public static void main(final String[] args) throws Exception {
		try (DeterministicGeodataFixture fixture =
				DeterministicGeodataFixture.create()) {
			final Path directory = Files.createTempDirectory("geoeditor-session-");
			final Path copy = directory.resolve("22_22.l2j");
			Files.copy(fixture.path(), copy, StandardCopyOption.REPLACE_EXISTING);
			try {
			final int localX = DeterministicGeodataFixture.COMPLEX_LOCAL_X;
			final int localY = 0;
			int savedMultiX = -1;
			int savedMultiY = -1;
			try (EditableRegionSession session =
					EditableRegionSession.load(copy.toFile())) {
				final DetailedRegionSnapshot originalSnapshot = session.snapshot();
				final int editedBlockX = localX >> 3;
				final int editedBlockY = localY >> 3;
				final short originalHeight = session.snapshot().height(localX, localY, 0);
				final short originalNswe = session.snapshot().nswe(localX, localY, 0);
				final EditableRegionSession.BlockCheckpoint beforeCellEdit =
						session.captureBlocks(new int[] { editedBlockX },
								new int[] { editedBlockY });
				session.setHeight(localX, localY, 0, (short) -3392);
				session.setNswe(localX, localY, 0, (short) 7);
				if (session.snapshot() != originalSnapshot)
					throw new AssertionError(
							"Single-cell edits must update the snapshot incrementally.");
				if (session.snapshot().height(localX, localY, 0) != -3392
						|| session.snapshot().nswe(localX, localY, 0) != 7)
					throw new AssertionError("Incremental snapshot edit failed.");
				final EditableRegionSession.BlockCheckpoint afterCellEdit =
						session.captureBlocks(new int[] { editedBlockX },
								new int[] { editedBlockY });
				session.restoreCheckpoint(beforeCellEdit);
				if (session.snapshot().height(localX, localY, 0) != originalHeight
						|| session.snapshot().nswe(localX, localY, 0) != originalNswe)
					throw new AssertionError("Checkpoint undo failed.");
				session.restoreCheckpoint(afterCellEdit);
				if (session.snapshot().height(localX, localY, 0) != -3392
						|| session.snapshot().nswe(localX, localY, 0) != 7)
					throw new AssertionError("Checkpoint redo failed.");
				int multiX = -1, multiY = -1;
				for (int blockX = 0; blockX < 256 && multiX < 0; blockX++) {
					for (int blockY = 0; blockY < 256; blockY++) {
						if (session.snapshot().summary().blockType(blockX, blockY)
								== GeoEngine.GEO_BLOCK_TYPE_MULTILAYER) {
							multiX = blockX * 8;
							multiY = blockY * 8;
							break;
						}
					}
				}
				if (multiX < 0)
					throw new AssertionError("Fixture has no multilayer block.");
				savedMultiX = multiX;
				savedMultiY = multiY;
				final int before = session.snapshot().layerCount(multiX, multiY);
				final int beforeAdjacent = session.snapshot().layerCount(multiX + 1, multiY);
				final short addedHeight = 16000;
				if (session.addLayers(new int[] { multiX, multiX + 1 },
						new int[] { multiY, multiY }, addedHeight, false, false) != 2
						|| session.snapshot().layerCount(multiX, multiY) != before + 1
						|| session.snapshot().layerCount(multiX + 1, multiY) != beforeAdjacent + 1)
					throw new AssertionError("Batch layer addition failed.");
				if (session.removeLayers(new int[] { multiX, multiX + 1 },
						new int[] { multiY, multiY }, 2, addedHeight,
						addedHeight, false) != 2
						|| session.snapshot().layerCount(multiX, multiY) != before
						|| session.snapshot().layerCount(multiX + 1, multiY) != beforeAdjacent)
					throw new AssertionError("Batch layer removal failed.");
				session.setHeight(multiX, multiY, 0, (short) -3200);
				session.setHeight(multiX + 1, multiY, 0, (short) -3192);
				if (!session.isModified())
					throw new AssertionError("Editing must mark the region modified.");
				session.save();
			}
			try (EditableRegionSession reloaded =
					EditableRegionSession.load(copy.toFile())) {
				if (reloaded.snapshot().height(localX, localY, 0) != -3392
						|| reloaded.snapshot().nswe(localX, localY, 0) != 7)
					throw new AssertionError("Saved cell did not round-trip.");
				if (reloaded.snapshot().height(savedMultiX, savedMultiY, 0) != -3200
						|| reloaded.snapshot().height(savedMultiX + 1, savedMultiY, 0) != -3192)
					throw new AssertionError("Batch cell edits did not round-trip.");
				if (reloaded.isModified())
					throw new AssertionError("Reloaded region must be clean.");
			}
			System.out.println("EditableRegionSessionTest OK - undo/redo/batch/save/reload");
			} finally {
				Files.deleteIfExists(copy);
				Files.deleteIfExists(directory);
			}
		}
	}
}

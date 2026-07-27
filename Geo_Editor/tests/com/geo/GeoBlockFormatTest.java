/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.geo;

import com.geo.blocks.GeoBlockComplex;
import com.geo.blocks.GeoBlockFlat;
import com.geo.blocks.GeoBlockMultiLayer;
import com.util.GeoByteBuffer;

/** Synthetic format tests for the three L2J block types. */
public final class GeoBlockFormatTest {
    private static final short HEIGHT_AND_NSWE = GeoEngine.convertHeightToHeightAndNSWEALL((short) 128);

    public static void main(final String[] args) {
        flatBlock();
        complexBlock();
        multilayerBlock();
        multilayerSelection();
        serializationRoundTrips();
        fullFlatRegion();
        mixedRegion();
        blockParseBaseline();
        System.out.println("GeoBlockFormatTest OK");
    }

    private static void flatBlock() {
        final GeoByteBuffer data = GeoByteBuffer.allocate(2);
        data.putShort((short) 128);
        data.clear();

        final GeoBlockFlat block = new GeoBlockFlat(data, 0, 0, true);
        check(block.getType() == GeoEngine.GEO_BLOCK_TYPE_FLAT, "flat type");
        check(block.getMinHeight() == 128, "flat height");
        check(block.nGetLayerCount(0, 0) == 1, "flat layer count");
    }

    private static void complexBlock() {
        final GeoByteBuffer data = GeoByteBuffer.allocate(64 * 2);
        for (int i = 0; i < 64; i++) {
            data.putShort(HEIGHT_AND_NSWE);
        }
        data.clear();

        final GeoBlockComplex block = new GeoBlockComplex(data, 0, 0, true);
        check(block.getType() == GeoEngine.GEO_BLOCK_TYPE_COMPLEX, "complex type");
        check(block.nGetCell(0, 0, 128).getHeight() == 128, "complex height");
        check(block.nGetCell(0, 0, 128).getNSWE() == GeoEngine.NSWE_ALL, "complex NSWE");
    }

    private static void multilayerBlock() {
        final GeoByteBuffer data = GeoByteBuffer.allocate(64 * 3);
        for (int i = 0; i < 64; i++) {
            data.put((byte) 1);
            data.putShort(HEIGHT_AND_NSWE);
        }
        data.clear();

        final GeoBlockMultiLayer block = new GeoBlockMultiLayer(data, 0, 0, true);
        check(block.getType() == GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, "multilayer type");
        check(block.nGetLayerCount(0, 0) == 1, "multilayer layer count");
        check(block.nGetCell(0, 0, 128).getHeight() == 128, "multilayer height");
        check(block.nGetCell(0, 0, 128).getNSWE() == GeoEngine.NSWE_ALL, "multilayer NSWE");
    }

    private static void multilayerSelection() {
        final short lower = GeoEngine.convertHeightToHeightAndNSWEALL((short) 64);
        final short upper = GeoEngine.convertHeightToHeightAndNSWEALL((short) 192);
        final GeoByteBuffer data = GeoByteBuffer.allocate(64 * 5);
        for (int i = 0; i < 64; i++) {
            data.put((byte) 2);
            data.putShort(upper);
            data.putShort(lower);
        }
        data.clear();

        final GeoBlockMultiLayer block = new GeoBlockMultiLayer(data, 0, 0, true);
        check(block.nGetLayerCount(0, 0) == 2, "multilayer two levels");
        check(block.nGetCell(0, 0, 70).getHeight() == 64, "selected lower height");
        check(block.nGetCell(0, 0, 180).getHeight() == 192, "selected upper height");
    }

    private static void serializationRoundTrips() {
        final GeoBlockFlat flat = new GeoBlockFlat(createFlatData(), 0, 0, true);
        final GeoByteBuffer flatBytes = GeoByteBuffer.allocate(flat.getRequiredCapacity(true));
        flat.writeTo(flatBytes, true);
        check(flatBytes.capacity() == 3, "flat serialized capacity");
        flatBytes.clear();
        check(flatBytes.get() == GeoEngine.GEO_BLOCK_TYPE_FLAT, "flat serialized type");
        flatBytes.clear();
        final GeoBlockFlat flatRead = new GeoBlockFlat(skipType(flatBytes), 0, 0, true);
        flatBytes.clear();
        check(flatRead.dataEquals(flatBytes), "flat round trip");

        final GeoBlockComplex complex = new GeoBlockComplex(createComplexData(), 0, 0, true);
        final GeoByteBuffer complexBytes = GeoByteBuffer.allocate(complex.getRequiredCapacity(true));
        complex.writeTo(complexBytes, true);
        check(complexBytes.capacity() == 129, "complex serialized capacity");
        complexBytes.clear();
        check(complexBytes.get() == GeoEngine.GEO_BLOCK_TYPE_COMPLEX, "complex serialized type");
        complexBytes.clear();
        final GeoBlockComplex complexRead = new GeoBlockComplex(skipType(complexBytes), 0, 0, true);
        complexBytes.clear();
        check(complexRead.dataEquals(complexBytes), "complex round trip");

        final GeoBlockMultiLayer multi = new GeoBlockMultiLayer(createTwoLayerData(), 0, 0, true);
        final GeoByteBuffer multiBytes = GeoByteBuffer.allocate(multi.getRequiredCapacity(true));
        multi.writeTo(multiBytes, true);
        check(multiBytes.capacity() == 321, "multilayer serialized capacity");
        multiBytes.clear();
        check(multiBytes.get() == GeoEngine.GEO_BLOCK_TYPE_MULTILAYER, "multilayer serialized type");
        multiBytes.clear();
        final GeoBlockMultiLayer multiRead = new GeoBlockMultiLayer(skipType(multiBytes), 0, 0, true);
        multiBytes.clear();
        check(multiRead.dataEquals(multiBytes), "multilayer round trip");
    }

    private static GeoByteBuffer skipType(final GeoByteBuffer data) {
        data.get();
        return data;
    }

    private static GeoByteBuffer createFlatData() {
        final GeoByteBuffer data = GeoByteBuffer.allocate(2);
        data.putShort((short) 128);
        data.clear();
        return data;
    }

    private static GeoByteBuffer createComplexData() {
        final GeoByteBuffer data = GeoByteBuffer.allocate(64 * 2);
        for (int i = 0; i < 64; i++)
            data.putShort(HEIGHT_AND_NSWE);
        data.clear();
        return data;
    }

    private static GeoByteBuffer createTwoLayerData() {
        final short lower = GeoEngine.convertHeightToHeightAndNSWEALL((short) 64);
        final short upper = GeoEngine.convertHeightToHeightAndNSWEALL((short) 192);
        final GeoByteBuffer data = GeoByteBuffer.allocate(64 * 5);
        for (int i = 0; i < 64; i++) {
            data.put((byte) 2);
            data.putShort(upper);
            data.putShort(lower);
        }
        data.clear();
        return data;
    }

    private static void fullFlatRegion() {
        final int blocks = GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE;
        final GeoByteBuffer data = GeoByteBuffer.allocate(blocks * 3);
        for (int i = 0; i < blocks; i++) {
            data.put(GeoEngine.GEO_BLOCK_TYPE_FLAT);
            data.putShort((short) 256);
        }
        data.clear();

        final GeoRegion region = new GeoRegion(0, 0, data, true, null);
        check(region.getGeoBlocks().length == GeoEngine.GEO_REGION_SIZE, "region block width");
        check(region.getGeoBlocks()[0].length == GeoEngine.GEO_REGION_SIZE, "region block height");
        check(region.nGetType(region.getGeoX(255), region.getGeoY(255)) == GeoEngine.GEO_BLOCK_TYPE_FLAT,
                "last region block type");
        check(region.nGetCell(region.getGeoX(255) + 7, region.getGeoY(255) + 7, 256).getHeight() == 256,
                "last region cell height");
    }

    private static void mixedRegion() {
        final int blocks = GeoEngine.GEO_REGION_SIZE * GeoEngine.GEO_REGION_SIZE;
        final int flatSize = 3;
        final int complexSize = 1 + (64 * 2);
        final int multiSize = 1 + (64 * 5);
        final int flatCount = (blocks + 2) / 3;
        final int complexCount = (blocks + 1) / 3;
        final int multiCount = blocks / 3;
        final GeoByteBuffer data = GeoByteBuffer.allocate(flatCount * flatSize + complexCount * complexSize
                + multiCount * multiSize);
        for (int i = 0; i < blocks; i++) {
            final int type = i % 3;
            if (type == 0) {
                data.put(GeoEngine.GEO_BLOCK_TYPE_FLAT);
                data.putShort((short) 128);
            } else if (type == 1) {
                data.put(GeoEngine.GEO_BLOCK_TYPE_COMPLEX);
                for (int cell = 0; cell < 64; cell++)
                    data.putShort(HEIGHT_AND_NSWE);
            } else {
                data.put(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER);
                for (int cell = 0; cell < 64; cell++) {
                    data.put((byte) 2);
                    data.putShort(HEIGHT_AND_NSWE);
                    data.putShort(GeoEngine.convertHeightToHeightAndNSWEALL((short) 256));
                }
            }
        }
        data.clear();

        final long parseStart = System.nanoTime();
        final GeoRegion region = new GeoRegion(1, 1, data, true, null);
        final long parseMicros = (System.nanoTime() - parseStart) / 1_000L;
        System.out.println("MixedRegion baseline: blocks=" + blocks + ", parseMicros=" + parseMicros);
        int flatBlocks = 0;
        int complexBlocks = 0;
        int multiBlocks = 0;
        long flatCells = 0L;
        long complexCells = 0L;
        long multiCells = 0L;
        for (int x = 0; x < GeoEngine.GEO_REGION_SIZE; x++) {
            for (int y = 0; y < GeoEngine.GEO_REGION_SIZE; y++) {
                switch (region.getGeoBlocks()[x][y].getType()) {
                case GeoEngine.GEO_BLOCK_TYPE_FLAT:
                    flatBlocks++;
                    flatCells += region.getGeoBlocks()[x][y].getCells().length;
                    break;
                case GeoEngine.GEO_BLOCK_TYPE_COMPLEX:
                    complexBlocks++;
                    complexCells += region.getGeoBlocks()[x][y].getCells().length;
                    break;
                case GeoEngine.GEO_BLOCK_TYPE_MULTILAYER:
                    multiBlocks++;
                    multiCells += region.getGeoBlocks()[x][y].getCells().length;
                    break;
                default:
                    throw new AssertionError("unknown mixed block type");
                }
            }
        }
        check(flatBlocks == flatCount, "mixed flat count");
        check(complexBlocks == complexCount, "mixed complex count");
        check(multiBlocks == multiCount, "mixed multilayer count");
        check(region.getTotalLayerCount() == flatCells + complexCells + multiCells,
                "mixed region total layer count");
        check(region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_FLAT) == flatCells,
                "mixed flat layer total");
        check(region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_COMPLEX) == complexCells,
                "mixed complex layer total");
        check(region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER) == multiCells,
                "mixed multilayer layer total");
        check(region.getTotalLayerCount() == flatCells + complexCells + multiCells,
                "cached mixed region total layer count");
        check(region.getTotalLayerCount(GeoEngine.GEO_BLOCK_TYPE_MULTILAYER) == multiCells,
                "cached mixed multilayer layer total");
        System.out.println("MixedRegion baseline by type: flat=" + flatBlocks + " blocks/" + (flatBlocks * flatSize)
                + " bytes, complex=" + complexBlocks + " blocks/" + (complexBlocks * complexSize)
                + " bytes, multilayer=" + multiBlocks + " blocks/" + (multiBlocks * multiSize) + " bytes");
        check(flatCells == flatBlocks, "flat cell count");
        check(complexCells == complexBlocks * 64L, "complex cell count");
        check(multiCells == multiBlocks * 128L, "multilayer cell count");
        System.out.println("MixedRegion cells: flat=" + flatCells + ", complex=" + complexCells + ", multilayer="
                + multiCells);
        check(region.nGetType(region.getGeoX(0), region.getGeoY(0)) == GeoEngine.GEO_BLOCK_TYPE_FLAT,
                "mixed flat block");
        check(region.nGetType(region.getGeoX(1), region.getGeoY(0)) == GeoEngine.GEO_BLOCK_TYPE_COMPLEX,
                "mixed complex block");
        check(region.nGetType(region.getGeoX(2), region.getGeoY(0)) == GeoEngine.GEO_BLOCK_TYPE_MULTILAYER,
                "mixed multilayer block");
        check(((GeoBlockMultiLayer) region.getGeoBlocks()[2][0]).getTotalLayerCount() == 128,
                "mixed multilayer layer total");
    }

    private static void blockParseBaseline() {
        final int samples = 256;
        final long flatStart = System.nanoTime();
        for (int i = 0; i < samples; i++)
            new GeoBlockFlat(createFlatData(), 0, 0, true);
        final long flatMicros = (System.nanoTime() - flatStart) / 1_000L;
        final long complexStart = System.nanoTime();
        for (int i = 0; i < samples; i++)
            new GeoBlockComplex(createComplexData(), 0, 0, true);
        final long complexMicros = (System.nanoTime() - complexStart) / 1_000L;
        final long multiStart = System.nanoTime();
        for (int i = 0; i < samples; i++)
            new GeoBlockMultiLayer(createTwoLayerData(), 0, 0, true);
        final long multiMicros = (System.nanoTime() - multiStart) / 1_000L;
        System.out.println("Block parse baseline/" + samples + ": flat=" + flatMicros + "us, complex="
                + complexMicros + "us, multilayer=" + multiMicros + "us");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

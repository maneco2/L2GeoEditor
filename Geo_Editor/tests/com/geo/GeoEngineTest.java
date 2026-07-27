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

/**
 * Testes sem dependência externa para os contratos matemáticos do GeoEngine.
 */
public final class GeoEngineTest {
    private static int assertions;

    public static void main(final String[] args) {
        coordinatesRoundTrip();
        coordinateBoundaries();
        coordinateConventions();
        indexesAndRegions();
        heightsAndNswe();
        blockedDirectionMasks();
        movementPermissions();
        antiCornerCutRules();
        System.out.println("GeoEngineTest OK - assertions=" + assertions);
    }

    private static void coordinatesRoundTrip() {
        final int worldX = 123456;
        final int worldY = -98765;
        final int geoX = GeoEngine.getGeoX(worldX);
        final int geoY = GeoEngine.getGeoY(worldY);
        check(GeoEngine.getWorldX(geoX) <= worldX, "world X floor");
        check(GeoEngine.getWorldY(geoY) <= worldY, "world Y floor");
        check(GeoEngine.getGeoX(GeoEngine.getWorldX(geoX) + 15) == geoX, "world X remains in cell");
        check(GeoEngine.getGeoY(GeoEngine.getWorldY(geoY) + 15) == geoY, "world Y remains in cell");
        check(GeoEngine.getGeoX(GeoEngine.getWorldX(geoX)) == geoX, "world/geo X round trip");
        check(GeoEngine.getGeoY(GeoEngine.getWorldY(geoY)) == geoY, "world/geo Y round trip");
    }

    private static void indexesAndRegions() {
        check(GeoEngine.getBlockXY(17) == 2, "block coordinate");
        check(GeoEngine.getCellXY(15) == 7, "cell coordinate");
        check(GeoEngine.getRegionXY(2048) == 1, "region coordinate");
        check(GeoEngine.getGeoXY(3, 5) == 6184, "geo coordinate");
        check(GeoEngine.getBlockIndex(2, 7) == 519, "block index");
        check(GeoEngine.getCellIndex(2, 7) == 23, "cell index");
    }

    private static void coordinateBoundaries() {
        check(GeoEngine.getGeoX(GeoEngine.MAP_MIN_X) == GeoEngine.GEOX_MIN, "minimum geo X");
        check(GeoEngine.getGeoY(GeoEngine.MAP_MIN_Y) == GeoEngine.GEOY_MIN, "minimum geo Y");
        check(GeoEngine.getGeoX(GeoEngine.MAP_MAX_X) == GeoEngine.GEOX_MAX, "maximum geo X");
        check(GeoEngine.getGeoY(GeoEngine.MAP_MAX_Y) == GeoEngine.GEOY_MAX, "maximum geo Y");
        check(GeoEngine.getGeoX(GeoEngine.MAP_MIN_X - 1) < GeoEngine.GEOX_MIN, "outside minimum X");
        check(GeoEngine.getGeoY(GeoEngine.MAP_MIN_Y - 1) < GeoEngine.GEOY_MIN, "outside minimum Y");
    }

    private static void coordinateConventions() {
        check(GeoEngine.getWorldX(0) == GeoEngine.MAP_MIN_X, "editor uses cell origin on X");
        check(GeoEngine.getWorldY(0) == GeoEngine.MAP_MIN_Y, "editor uses cell origin on Y");
        check(GeoEngine.getWorldX(0) + (1 << GeoEngine.CELL_SHIFT) / 2 == GeoEngine.MAP_MIN_X + 8,
                "cell center on X");
        check(GeoEngine.getWorldY(0) + (1 << GeoEngine.CELL_SHIFT) / 2 == GeoEngine.MAP_MIN_Y + 8,
                "cell center on Y");
        check(GeoEngine.getRegionXY(GeoEngine.getGeoXY(10, 0)) == 10, "internal region X mapping");
        check(GeoEngine.getRegionXY(GeoEngine.getGeoXY(10, 0) + 2047) == 10, "region X extent");
        check(GeoEngine.getRegionXY(GeoEngine.getGeoXY(11, 0)) == 11, "next internal region X");
    }

    private static void heightsAndNswe() {
        final short encoded = GeoEngine.convertHeightToHeightAndNSWEALL((short) 100);
        check(GeoEngine.getHeight(encoded) == 96, "height encode/decode quantization");
        check(GeoEngine.getNSWE(encoded) == GeoEngine.NSWE_ALL, "default NSWE");
        check(GeoEngine.getHeight(GeoEngine.updateHeightOfHeightAndNSWE(encoded, (short) -50)) == -56,
                "height update quantization");
        check(GeoEngine.getNSWE(GeoEngine.updateNSWEOfHeightAndNSWE(encoded, GeoEngine.NORTH)) == GeoEngine.NORTH,
                "NSWE update");
        check(GeoEngine.getGeoHeightOfHeight((short) 20000) == GeoEngine.HEIGHT_MAX_VALUE, "height upper clamp");
        check(GeoEngine.getGeoHeightOfHeight((short) -20000) == GeoEngine.HEIGHT_MIN_VALUE, "height lower clamp");
    }

    private static void movementPermissions() {
        check(GeoEngine.checkNSWE(GeoEngine.EAST, 0, 0, 1, 0), "east permitted");
        check(!GeoEngine.checkNSWE(GeoEngine.WEST, 0, 0, 1, 0), "east blocked by west-only");
        check(GeoEngine.checkNSWE(GeoEngine.NORTH, 0, 1, 0, 0), "north permitted");
        check(GeoEngine.checkNSWE(GeoEngine.NORTHEAST, 0, 0, 1, -1), "north-east permitted");
        check(!GeoEngine.checkNSWE(GeoEngine.NORTHEAST, 0, 0, -1, -1), "north-east rejects west move");
        check(GeoEngine.checkNSWE(GeoEngine.NORTHWEST, 0, 0, -1, -1), "north-west permitted");
        check(GeoEngine.checkNSWE(GeoEngine.SOUTHEAST, 0, 0, 1, 1), "south-east permitted");
        check(GeoEngine.checkNSWE(GeoEngine.SOUTHWEST, 0, 0, -1, 1), "south-west permitted");
        check(GeoEngine.checkNSWE(GeoEngine.NSWE_ALL, 0, 0, 1, 1), "all directions permitted");
    }

    private static void blockedDirectionMasks() {
        check(GeoEngine.invertNSWE(GeoEngine.NSWE_NONE) == GeoEngine.NSWE_ALL,
                "no blocked directions stores all directions");
        check(GeoEngine.invertNSWE(GeoEngine.NORTH) == GeoEngine.NNORTH,
                "blocked north stores every direction except north");
        check(GeoEngine.invertNSWE(GeoEngine.SOUTH) == GeoEngine.NSOUTH,
                "blocked south stores every direction except south");
        check(GeoEngine.invertNSWE(GeoEngine.invertNSWE(GeoEngine.NORTHWEST)) == GeoEngine.NORTHWEST,
                "blocked mask conversion is reversible");
    }

    private static void antiCornerCutRules() {
        check(antiCornerCut(GeoEngine.NORTH, GeoEngine.EAST, GeoEngine.NORTHEAST),
                "north-east corner allowed");
        check(!antiCornerCut(GeoEngine.NORTH, GeoEngine.WEST, GeoEngine.NORTHEAST),
                "north-east corner blocks wrong side");
        check(antiCornerCut(GeoEngine.SOUTH, GeoEngine.WEST, GeoEngine.SOUTHWEST),
                "south-west corner allowed");
        check(!antiCornerCut(GeoEngine.SOUTH, GeoEngine.EAST, GeoEngine.SOUTHWEST),
                "south-west corner blocks wrong side");
    }

    private static boolean antiCornerCut(final short firstEdge, final short secondEdge, final short diagonal) {
        return (firstEdge & diagonal) == firstEdge && (secondEdge & diagonal) == secondEdge;
    }

    private static void check(final boolean condition, final String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

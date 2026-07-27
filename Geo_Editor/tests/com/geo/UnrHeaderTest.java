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
import com.geo.unr.UnrHeader;
import java.nio.charset.StandardCharsets;
public final class UnrHeaderTest {
    public static void main(final String[] args) throws Exception {
        final byte[] header = "Lineage2Ver111m".getBytes(StandardCharsets.UTF_16LE);
        final UnrHeader parsed = UnrHeader.read(header);
        check(parsed.signature().equals("Lineage2Ver111m"), "signature");
        check(parsed.signatureBytes() == header.length, "signature bytes");
        boolean rejected = false;
        try { UnrHeader.read("invalid".getBytes(StandardCharsets.UTF_16LE)); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "invalid signature rejected");
        System.out.println("UnrHeaderTest OK");
    }
    private static void check(final boolean condition, final String message) { if (!condition) throw new AssertionError(message); }
}

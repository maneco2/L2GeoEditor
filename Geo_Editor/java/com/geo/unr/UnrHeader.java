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
package com.geo.unr;
import java.io.IOException;
import java.io.InputStream;

/**
 * Read-only identification of the legacy Lineage 2 Unreal map header.
 * @author Maneco2
 * @version 1.0
 */
public record UnrHeader(String signature, int signatureBytes, long fileSize) {
    public static final String EXPECTED_PREFIX = "Lineage2Ver";
    public static UnrHeader read(final InputStream input, final long fileSize) throws IOException {
        final byte[] bytes = input.readNBytes(64);
        final StringBuilder value = new StringBuilder();
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            if (bytes[i + 1] != 0) break;
            final char c = (char) (bytes[i] & 0xff);
            if (c < 32 || c > 126) break;
            value.append(c);
        }
        if (!value.toString().startsWith(EXPECTED_PREFIX)) throw new IOException("Unsupported UNR signature: " + value);
        return new UnrHeader(value.toString(), value.length() * 2, fileSize);
    }
    public static UnrHeader read(final byte[] bytes) throws IOException { return read(new java.io.ByteArrayInputStream(bytes), bytes.length); }
}

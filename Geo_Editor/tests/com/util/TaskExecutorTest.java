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
package com.util;

/** Deterministic checks for executor configuration. @author Maneco2 @version 1.0 */
public final class TaskExecutorTest {
    public static void main(final String[] args) {
        final TaskExecutor executor = new TaskExecutor(4);
        check(executor.getParallelism() == 4, "configured parallelism");
        executor.shutdown();
        final TaskExecutor minimum = new TaskExecutor(0);
        check(minimum.getParallelism() == 1, "minimum parallelism");
        minimum.shutdown();
        System.out.println("TaskExecutorTest OK");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}

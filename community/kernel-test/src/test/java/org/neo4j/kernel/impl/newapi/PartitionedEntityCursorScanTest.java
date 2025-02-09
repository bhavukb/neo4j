/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.newapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.neo4j.internal.kernel.api.Cursor;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.io.pagecache.context.CursorContext;

class PartitionedEntityCursorScanTest {

    @Test
    void shouldFailOnZeroDesiredPartitions() {
        assertThatIllegalArgumentException().isThrownBy(() -> scan(0, 100));
    }

    @Test
    void shouldHaveAtLeastOnePartition() {
        assertThat(scan(1, 0).getNumberOfPartitions()).isGreaterThan(0);
    }

    @Test
    void numberOfPartitionsShouldNotExceedTotalCount() {
        assertThat(scan(1000, 100).getNumberOfPartitions()).isEqualTo(100);
    }

    @Test
    void lastBatchSizeShouldBeBig() {
        PartitionedEntityCursorScan<?, ?> scan = scan(4, 100);
        assertThat(scan.computeBatchSize()).isEqualTo(25);
        assertThat(scan.computeBatchSize()).isEqualTo(25);
        assertThat(scan.computeBatchSize()).isEqualTo(25);
        assertThat(scan.computeBatchSize()).isEqualTo(Long.MAX_VALUE);
    }

    private PartitionedEntityCursorScan<?, ?> scan(int desiredNumberOfPartitions, int totalCount) {
        return new TestEntityCursorScan(desiredNumberOfPartitions, totalCount);
    }

    private static class TestEntityCursorScan extends PartitionedEntityCursorScan<Cursor, Object> {
        TestEntityCursorScan(int desiredNumberOfPartitions, long totalCount) {
            super(null, null, desiredNumberOfPartitions, totalCount);
        }

        @Override
        public boolean reservePartition(Cursor cursor, CursorContext cursorContext, AccessMode accessMode) {
            return true;
        }
    }
}

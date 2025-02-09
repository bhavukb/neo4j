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
package org.neo4j.kernel.impl.util.diffsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.neo4j.collection.PrimitiveLongCollections.toSet;
import static org.neo4j.internal.helpers.collection.Iterators.asSet;

import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.junit.jupiter.api.Test;
import org.neo4j.kernel.impl.util.collection.CollectionsFactory;
import org.neo4j.kernel.impl.util.collection.OnHeapCollectionsFactory;
import org.neo4j.memory.EmptyMemoryTracker;

class MutableLongDiffSetsImplTest {

    @Test
    void newDiffSetIsEmpty() {
        assertTrue(createDiffSet().isEmpty());
    }

    @Test
    void addElementsToDiffSets() {
        MutableLongDiffSetsImpl diffSets = createDiffSet();

        diffSets.add(1L);
        diffSets.add(2L);

        assertEquals(asSet(1L, 2L), toSet(diffSets.getAdded()));
        assertTrue(diffSets.getRemoved().isEmpty());
        assertFalse(diffSets.isEmpty());
    }

    @Test
    void removeElementsInDiffSets() {
        MutableLongDiffSetsImpl diffSets = createDiffSet();

        diffSets.remove(1L);
        diffSets.remove(2L);

        assertFalse(diffSets.isEmpty());
        assertEquals(asSet(1L, 2L), toSet(diffSets.getRemoved()));
    }

    @Test
    void removeAndAddElementsToDiffSets() {
        MutableLongDiffSetsImpl diffSets = createDiffSet();

        diffSets.remove(1L);
        diffSets.remove(2L);
        diffSets.add(1L);
        diffSets.add(2L);
        diffSets.add(3L);
        diffSets.remove(4L);

        assertFalse(diffSets.isEmpty());
        assertEquals(asSet(4L), toSet(diffSets.getRemoved()));
        assertEquals(asSet(3L), toSet(diffSets.getAdded()));
    }

    @Test
    void checkIsElementsAddedOrRemoved() {
        MutableLongDiffSetsImpl diffSet = createDiffSet();

        diffSet.add(1L);

        assertTrue(diffSet.isAdded(1L));
        assertFalse(diffSet.isRemoved(1L));

        diffSet.remove(2L);

        assertFalse(diffSet.isAdded(2L));
        assertTrue(diffSet.isRemoved(2L));

        assertFalse(diffSet.isAdded(3L));
        assertFalse(diffSet.isRemoved(3L));
    }

    @Test
    void addedAndRemovedElementsDelta() {
        MutableLongDiffSetsImpl diffSet = createDiffSet();
        assertEquals(0, diffSet.delta());

        diffSet.add(7L);
        diffSet.add(8L);
        diffSet.add(9L);
        diffSet.add(10L);
        assertEquals(4, diffSet.delta());

        diffSet.remove(8L);
        diffSet.remove(9L);
        assertEquals(2, diffSet.delta());
    }

    @Test
    void useCollectionsFactory() {
        final MutableLongSet set1 = new LongHashSet();
        final MutableLongSet set2 = new LongHashSet();
        final CollectionsFactory collectionsFactory = mock(CollectionsFactory.class);
        doReturn(set1, set2).when(collectionsFactory).newLongSet(EmptyMemoryTracker.INSTANCE);

        final MutableLongDiffSetsImpl diffSets =
                new MutableLongDiffSetsImpl(collectionsFactory, EmptyMemoryTracker.INSTANCE);
        diffSets.add(1L);
        diffSets.remove(2L);

        assertSame(set1, diffSets.getAdded());
        assertSame(set2, diffSets.getRemoved());
        verify(collectionsFactory, times(2)).newLongSet(EmptyMemoryTracker.INSTANCE);
        verifyNoMoreInteractions(collectionsFactory);
    }

    private static MutableLongDiffSetsImpl createDiffSet() {
        return new MutableLongDiffSetsImpl(OnHeapCollectionsFactory.INSTANCE, EmptyMemoryTracker.INSTANCE);
    }
}

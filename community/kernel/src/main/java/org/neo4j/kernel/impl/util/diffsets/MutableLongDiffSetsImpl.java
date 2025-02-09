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

import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.neo4j.kernel.impl.util.collection.CollectionsFactory;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.VisibleForTesting;

/**
 * Primitive long version of collection that with given a sequence of add and removal operations, tracks
 * which elements need to actually be added and removed at minimum from some
 * target collection such that the result is equivalent to just
 * executing the sequence of additions and removals in order.
 */
public class MutableLongDiffSetsImpl implements MutableLongDiffSets {
    private static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(MutableLongDiffSetsImpl.class);
    private static final MutableLongSet NOT_INITIALIZED =
            LongSets.mutable.empty().asUnmodifiable();

    private final CollectionsFactory collectionsFactory;
    private final MemoryTracker memoryTracker;
    private MutableLongSet added;
    private MutableLongSet removed;

    static MutableLongDiffSetsImpl createMutableLongDiffSetsImpl(
            CollectionsFactory collectionsFactory, MemoryTracker memoryTracker) {
        memoryTracker.allocateHeap(SHALLOW_SIZE);
        return new MutableLongDiffSetsImpl(collectionsFactory, memoryTracker);
    }

    @VisibleForTesting
    public MutableLongDiffSetsImpl(
            MutableLongSet added,
            MutableLongSet removed,
            CollectionsFactory collectionsFactory,
            MemoryTracker memoryTracker) {
        this.added = added;
        this.removed = removed;
        this.collectionsFactory = collectionsFactory;
        this.memoryTracker = memoryTracker;
    }

    protected MutableLongDiffSetsImpl(CollectionsFactory collectionsFactory, MemoryTracker memoryTracker) {
        this(NOT_INITIALIZED, NOT_INITIALIZED, collectionsFactory, memoryTracker);
    }

    @Override
    public boolean isAdded(long element) {
        return added.contains(element);
    }

    @Override
    public boolean isRemoved(long element) {
        return removed.contains(element);
    }

    @Override
    public void add(long element) {
        checkAddedElements();
        addElement(element);
    }

    @Override
    public boolean remove(long element) {
        checkRemovedElements();
        return removeElement(element);
    }

    @Override
    public int delta() {
        return added.size() - removed.size();
    }

    @Override
    public LongSet getAdded() {
        return added;
    }

    @Override
    public LongSet getRemoved() {
        return removed;
    }

    @Override
    public LongSet getRemovedFromAdded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    private void addElement(long element) {
        if (removed.isEmpty() || !removed.remove(element)) {
            added.add(element);
        }
    }

    private boolean removeElement(long element) {
        if (!added.isEmpty() && added.remove(element)) {
            return true;
        }
        return removed.add(element);
    }

    private void checkAddedElements() {
        if (added == NOT_INITIALIZED) {
            added = collectionsFactory.newLongSet(memoryTracker);
        }
    }

    private void checkRemovedElements() {
        if (removed == NOT_INITIALIZED) {
            removed = collectionsFactory.newLongSet(memoryTracker);
        }
    }
}

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
package org.neo4j.bolt.protocol.v40.fsm;

import org.neo4j.bolt.protocol.common.fsm.State;
import org.neo4j.bolt.protocol.common.fsm.StateMachineContext;
import org.neo4j.bolt.tx.Transaction;
import org.neo4j.bolt.tx.statement.Statement;
import org.neo4j.memory.HeapEstimator;

/**
 * When AUTOCOMMIT, additionally attach bookmark to PULL, DISCARD result
 */
public class AutoCommitState extends AbstractStreamingState {
    public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(AutoCommitState.class);

    @Override
    public String name() {
        return "AUTOCOMMIT";
    }

    @Override
    protected State processStreamPullResultMessage(
            Transaction tx, Statement statement, StateMachineContext context, long noToPull) throws Throwable {
        super.processStreamPullResultMessage(tx, statement, context, noToPull);

        if (!statement.hasRemaining()) {
            this.commit(context, tx);
            return this.readyState;
        }

        return this;
    }

    @Override
    protected State processStreamDiscardResultMessage(
            Transaction tx, Statement statement, StateMachineContext context, long noToDiscard) throws Throwable {
        super.processStreamDiscardResultMessage(tx, statement, context, noToDiscard);

        if (!statement.hasRemaining()) {
            this.commit(context, tx);
            return this.readyState;
        }

        return this;
    }
}

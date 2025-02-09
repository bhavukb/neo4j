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
package org.neo4j.bolt.fsm;

import static org.neo4j.bolt.testing.assertions.MapValueAssertions.assertThat;
import static org.neo4j.bolt.testing.assertions.ResponseRecorderAssertions.assertThat;
import static org.neo4j.bolt.testing.assertions.StateMachineAssertions.assertThat;
import static org.neo4j.values.storable.BooleanValue.TRUE;
import static org.neo4j.values.storable.Values.longValue;

import org.neo4j.bolt.protocol.common.connector.connection.Connection;
import org.neo4j.bolt.protocol.common.fsm.StateMachine;
import org.neo4j.bolt.protocol.common.fsm.response.NoopResponseHandler;
import org.neo4j.bolt.protocol.common.message.request.RequestMessage;
import org.neo4j.bolt.protocol.v40.fsm.FailedState;
import org.neo4j.bolt.protocol.v40.fsm.InTransactionState;
import org.neo4j.bolt.protocol.v40.fsm.InterruptedState;
import org.neo4j.bolt.protocol.v40.fsm.ReadyState;
import org.neo4j.bolt.protocol.v40.messaging.request.RunMessage;
import org.neo4j.bolt.runtime.BoltConnectionFatality;
import org.neo4j.bolt.test.annotation.CommunityStateMachineTestExtension;
import org.neo4j.bolt.testing.annotation.fsm.StateMachineTest;
import org.neo4j.bolt.testing.annotation.fsm.initializer.Authenticated;
import org.neo4j.bolt.testing.annotation.fsm.initializer.InTransaction;
import org.neo4j.bolt.testing.annotation.fsm.initializer.Streaming;
import org.neo4j.bolt.testing.assertions.AnyValueAssertions;
import org.neo4j.bolt.testing.assertions.ConnectionAssertions;
import org.neo4j.bolt.testing.assertions.ResponseRecorderAssertions;
import org.neo4j.bolt.testing.messages.BoltMessages;
import org.neo4j.bolt.testing.response.ResponseRecorder;
import org.neo4j.kernel.api.exceptions.Status;

@CommunityStateMachineTestExtension
public class InTransactionStateIT {

    @StateMachineTest
    void shouldTransitionToInTransaction(
            @Authenticated StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws BoltConnectionFatality {
        fsm.process(messages.begin(), recorder);

        assertThat(recorder).hasSuccessResponse();

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldReturnToReadyOnCommit(@Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws Throwable {
        fsm.process(messages.commit(), recorder);

        assertThat(recorder).hasSuccessResponse(meta -> assertThat(meta).containsKey("bookmark"));

        assertThat(fsm).isInState(ReadyState.class);
    }

    @StateMachineTest
    void shouldReturnToReadyOnRollback(@Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws Throwable {
        fsm.process(messages.rollback(), recorder);

        assertThat(recorder)
                .hasSuccessResponse(
                        meta -> assertThat(meta).doesNotContainKey("bookmark").doesNotContainKey("db"));

        assertThat(fsm).isInState(ReadyState.class);
    }

    @StateMachineTest
    void shouldRemainInStateWhenStatementClosesViaDiscard(
            @Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Throwable {
        fsm.process(messages.discard(100L), recorder);

        assertThat(recorder)
                .hasSuccessResponse(
                        meta -> assertThat(meta).doesNotContainKey("bookmark").containsKey("db"));

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldIndicateRemainingElementsWhenDiscarding(
            @Streaming("UNWIND [1, 2, 3] AS n RETURN n") StateMachine fsm,
            BoltMessages messages,
            ResponseRecorder recorder)
            throws Throwable {
        fsm.process(messages.discard(2), recorder);

        assertThat(recorder)
                .hasSuccessResponse(
                        meta -> assertThat(meta).containsEntry("has_more", TRUE).doesNotContainKey("db"));

        fsm.process(messages.discard(2), recorder);

        assertThat(recorder).hasSuccessResponse(meta -> assertThat(meta)
                .containsKey("type")
                .containsKey("t_last")
                .doesNotContainKey("bookmark")
                .containsKey("db")
                .doesNotContainKey("has_more"));

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldRemainInStateWhenStatementClosesViaPull(
            @Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Throwable {
        fsm.process(messages.pull(100), recorder);

        assertThat(recorder).hasRecord().hasSuccessResponse(meta -> assertThat(meta)
                .containsKey("type")
                .containsKey("t_last")
                .doesNotContainKey("bookmark")
                .containsKey("db"));

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldIndicateRemainingElementsWhenPulling(
            @Streaming("UNWIND [1, 2, 3] AS n RETURN n") StateMachine fsm,
            BoltMessages messages,
            ResponseRecorder recorder)
            throws Throwable {
        fsm.process(messages.pull(2), recorder);

        assertThat(recorder).hasRecord(longValue(1)).hasRecord(longValue(2)).hasSuccessResponse(meta -> assertThat(meta)
                .containsEntry("has_more", TRUE)
                .doesNotContainKey("db"));

        fsm.process(messages.pull(2), recorder);

        assertThat(recorder).hasRecord(longValue(3)).hasSuccessResponse(meta -> assertThat(meta)
                .containsKey("type")
                .containsKey("t_last")
                .doesNotContainKey("bookmark")
                .containsKey("db"));

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldSupportMultipleStatements(@Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws Throwable {
        fsm.process(messages.run("MATCH (n) RETURN n LIMIT 1"), recorder);

        assertThat(recorder).hasSuccessResponse(meta -> assertThat(meta).doesNotContainKey("bookmark"));

        assertThat(fsm).isInState(InTransactionState.class);
    }

    @StateMachineTest
    void shouldReceiveBookmarkOnCommit(@Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws BoltConnectionFatality {
        fsm.process(messages.commit(), recorder);

        assertThat(recorder).hasSuccessResponse(meta -> assertThat(meta)
                .containsEntry("bookmark", value -> AnyValueAssertions.assertThat(value)
                        .asString()
                        .isNotEmpty()
                        .isNotBlank()));
    }

    @StateMachineTest
    void shouldNotReceiveBookmarkOnRollback(
            @Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Throwable {
        fsm.process(messages.rollback(), recorder);

        // Then
        assertThat(recorder).hasSuccessResponse(meta -> assertThat(meta).doesNotContainKey("bookmark"));
    }

    @StateMachineTest
    void shouldCloseTransactionEvenIfCommitFails(
            @Authenticated StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Exception {
        fsm.process(messages.begin(), NoopResponseHandler.getInstance());
        fsm.process(messages.run("X"), recorder);
        fsm.process(messages.pull(), recorder);

        assertThat(recorder).hasFailureResponse(Status.Statement.SyntaxError).hasIgnoredResponse();

        // The tx shall still be open.
        ConnectionAssertions.assertThat(fsm.connection()).hasTransaction();

        recorder.reset();
        fsm.process(messages.commit(), recorder);

        assertThat(recorder).hasIgnoredResponse();

        reset(fsm, messages);

        ConnectionAssertions.assertThat(fsm.connection()).hasNoTransaction();
    }

    @StateMachineTest
    void shouldCloseTransactionOnRollbackAfterFailure(
            @Authenticated StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Exception {
        fsm.process(messages.begin(), NoopResponseHandler.getInstance());
        fsm.process(messages.run("X"), recorder);
        fsm.process(messages.pull(), recorder);

        assertThat(recorder).hasFailureResponse(Status.Statement.SyntaxError).hasIgnoredResponse();

        // The tx shall still be open.
        ConnectionAssertions.assertThat(fsm.connection()).hasTransaction();

        recorder.reset();
        fsm.process(messages.rollback(), recorder);

        assertThat(recorder).hasIgnoredResponse();

        reset(fsm, messages);

        ConnectionAssertions.assertThat(fsm.connection()).hasNoTransaction();
    }

    @StateMachineTest
    void shouldReportTerminationError(@InTransaction StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws Throwable {
        var tx = fsm.connection()
                .transaction()
                .orElseThrow(() -> new AssertionError("No transaction active in connection"));

        tx.interrupt();
        fsm.validateTransaction();

        fsm.process(messages.run("RETURN 1"), recorder);

        assertThat(recorder).hasFailureResponse(Status.Transaction.Terminated);
        assertThat(fsm).isInState(FailedState.class);

        // ensure that the transaction remains associated until the client explicitly resets
        ConnectionAssertions.assertThat(fsm.connection()).hasTransaction();
    }

    @StateMachineTest
    void shouldReportTerminationErrorWithoutExplicitValidation(
            @InTransaction StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Throwable {
        var tx = fsm.connection()
                .transaction()
                .orElseThrow(() -> new AssertionError("No transaction active in connection"));

        tx.interrupt();

        fsm.process(messages.run("RETURN 1"), recorder);

        ResponseRecorderAssertions.assertThat(recorder).hasFailureResponse(Status.Transaction.Terminated);
        assertThat(fsm).isInState(FailedState.class);

        // ensure that the transaction remains associated until the client explicitly resets
        ConnectionAssertions.assertThat(fsm.connection()).hasTransaction();
    }

    @StateMachineTest
    void shouldTerminateOnInvalidStatement(
            @Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder) throws Throwable {
        fsm.process(new RunMessage("✨✨✨ INVALID QUERY STRING ✨✨✨"), recorder);

        // Then
        assertThat(fsm).isInState(FailedState.class);
    }

    @StateMachineTest
    void shouldTerminateOnInterrupt(@Streaming StateMachine fsm, BoltMessages messages, ResponseRecorder recorder)
            throws Throwable {
        fsm.connection().interrupt();

        fsm.process(messages.pull(), recorder);
        assertThat(recorder).hasIgnoredResponse();

        assertThat(fsm).isInState(InterruptedState.class);
    }

    private void shouldTerminateConnectionOnMessage(StateMachine fsm, RequestMessage message) {
        var recorder = new ResponseRecorder();

        assertThat(fsm).shouldKillConnection(it -> it.process(message, recorder));

        assertThat(recorder).hasFailureResponse(Status.Request.Invalid);
    }

    @StateMachineTest
    void shouldTerminateConnectionOnHello(@Streaming StateMachine fsm, BoltMessages messages) {
        shouldTerminateConnectionOnMessage(fsm, messages.hello());
    }

    @StateMachineTest
    void shouldTerminateConnectionOnBegin(@Streaming StateMachine fsm, BoltMessages messages) {
        shouldTerminateConnectionOnMessage(fsm, messages.begin());
    }

    @StateMachineTest
    void shouldTerminateConnectionOnReset(@Streaming StateMachine fsm, BoltMessages messages) {
        shouldTerminateConnectionOnMessage(fsm, messages.reset());
    }

    @StateMachineTest
    void shouldTerminateConnectionOnGoodbye(@Streaming StateMachine fsm, BoltMessages messages) {
        shouldTerminateConnectionOnMessage(fsm, messages.goodbye());
    }

    @StateMachineTest
    void shouldAllowUserControlledRollbackOnExplicitTxFailure(
            @Authenticated StateMachine fsm, ResponseRecorder recorder, BoltMessages messages, Connection connection)
            throws Throwable {
        // Given whenever en explicit transaction has a failure,
        // it is more natural for drivers to see the failure, acknowledge it
        // and send a `RESET`, because that means that all failures in the
        // transaction, be they client-local or inside neo, can be handled the
        // same way by a driver.

        fsm.process(messages.begin(), NoopResponseHandler.getInstance());
        fsm.process(messages.run("CREATE (n:Victim)-[:REL]->()"), NoopResponseHandler.getInstance());
        fsm.process(messages.discard(), NoopResponseHandler.getInstance());

        // When I perform an action that will fail
        fsm.process(messages.run("this is not valid syntax"), recorder);

        // Then I should see a failure
        assertThat(recorder).hasFailureResponse(Status.Statement.SyntaxError);

        // This result in an illegal state change, and closes all open statement by default.
        ConnectionAssertions.assertThat(connection).hasTransaction();
        assertThat(fsm).isInState(FailedState.class);

        // And when I reset that failure, and the tx should be rolled back
        reset(fsm, messages);

        // and there should be no remaining open statements
        ConnectionAssertions.assertThat(connection).hasNoTransaction();
    }

    private static void reset(StateMachine machine, BoltMessages messages) throws BoltConnectionFatality {
        var recorder = new ResponseRecorder();

        machine.connection().interrupt();
        machine.process(messages.reset(), recorder);

        assertThat(recorder).hasSuccessResponse();
    }
}

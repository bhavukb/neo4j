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
package org.neo4j.bolt.v44.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.neo4j.bolt.testing.assertions.BoltConnectionAssertions.assertThat;
import static org.neo4j.packstream.testing.PackstreamBufAssertions.assertThat;
import static org.neo4j.values.storable.Values.longValue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.bolt.AbstractBoltITBase;
import org.neo4j.bolt.protocol.common.connector.connection.Feature;
import org.neo4j.bolt.protocol.v40.bookmark.BookmarkWithDatabaseId;
import org.neo4j.bolt.testing.client.TransportConnection;
import org.neo4j.bolt.testing.messages.BoltV44Wire;
import org.neo4j.bolt.transport.Neo4jWithSocketExtension;
import org.neo4j.fabric.bolt.FabricBookmark;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.packstream.io.PackstreamBuf;
import org.neo4j.test.extension.testdirectory.EphemeralTestDirectoryExtension;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.virtual.MapValueBuilder;

@EphemeralTestDirectoryExtension
@Neo4jWithSocketExtension
public class BoltV44TransportIT extends AbstractBoltITBase {

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnTheRoutingTableInTheSuccessMessageWhenItReceivesTheRouteMessage(
            TransportConnection.Factory connectionFactory) throws Exception {
        connectAndHandshake(connectionFactory);

        connection.send(wire.route());

        assertThat(connection)
                .receivesSuccess(metadata -> assertThat(metadata).hasEntrySatisfying("rt", rt -> assertThat(rt)
                        .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
                        .satisfies(BoltV44TransportIT::assertRoutingTableHasCorrectShape)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnTheRoutingTableInTheSuccessMessageWhenItReceivesTheRouteMessageWithBookmark(
            TransportConnection.Factory connectionFactory) throws Exception {
        connectAndHandshake(connectionFactory);

        var lastClosedTransactionId = getLastClosedTransactionId();
        var routeBookmark = new BookmarkWithDatabaseId(lastClosedTransactionId, getDatabaseId());

        connection.send(wire.route(null, List.of(routeBookmark.toString()), null));

        assertThat(connection).receivesSuccess(metadata -> {
            assertThat(metadata.containsKey("rt")).isTrue();
            assertThat(metadata.get("rt"))
                    .isInstanceOf(Map.class)
                    .satisfies(rt -> assertRoutingTableHasCorrectShape((Map<?, ?>) rt));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnFailureIfRoutingTableFailedToReturn(TransportConnection.Factory connectionFactory)
            throws Exception {
        connectAndHandshake(connectionFactory);

        connection.send(wire.route(null, null, "DOESNT_EXIST!"));
        assertThat(connection).receivesFailure();

        connection.send(wire.reset());
        assertThat(connection).receivesSuccess();

        connection.send(wire.route());
        assertThat(connection).receivesSuccess(metadata -> {
            assertThat(metadata.containsKey("rt")).isTrue();
            assertThat(metadata.get("rt"))
                    .isInstanceOf(Map.class)
                    .satisfies(rt -> assertRoutingTableHasCorrectShape((Map<?, ?>) rt));
        });
    }

    private static void assertRoutingTableHasCorrectShape(Map<?, ?> routingTable) {
        assertAll(
                () -> {
                    assertThat(routingTable.containsKey("ttl")).isTrue();
                    assertThat(routingTable.get("ttl")).isInstanceOf(Long.class);
                },
                () -> {
                    assertThat(routingTable.containsKey("servers")).isTrue();
                    assertThat(routingTable.get("servers"))
                            .isInstanceOf(List.class)
                            .satisfies(s -> {
                                var servers = (List<?>) s;
                                for (var srv : servers) {
                                    assertThat(srv).isInstanceOf(Map.class);
                                    var server = (Map<?, ?>) srv;
                                    assertAll(
                                            () -> {
                                                assertThat(server.containsKey("role"))
                                                        .isTrue();
                                                assertThat(server.get("role")).isIn("READ", "WRITE", "ROUTE");
                                            },
                                            () -> {
                                                assertThat(server.containsKey("addresses"))
                                                        .isTrue();
                                                assertThat(server.get("addresses"))
                                                        .isInstanceOf(List.class)
                                                        .satisfies(ad -> {
                                                            var addresses = (List<?>) ad;
                                                            for (var address : addresses) {
                                                                assertThat(address)
                                                                        .isInstanceOf(String.class);
                                                            }
                                                        });
                                            });
                                }
                            });
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnUpdatedBookmarkAfterAutoCommitTransaction(TransportConnection.Factory connectionFactory)
            throws Exception {
        connectAndHandshake(connectionFactory);

        // bookmark is expected to advance once the auto-commit transaction is committed
        var lastClosedTransactionId = getLastClosedTransactionId();
        var expectedBookmark = new FabricBookmark(
                        List.of(new FabricBookmark.InternalGraphState(
                                getDatabaseId().databaseId().uuid(), lastClosedTransactionId + 1)),
                        List.of())
                .serialize();

        connection.send(wire.run("CREATE ()"));
        connection.send(wire.pull());

        assertThat(connection).receivesSuccess().receivesSuccess(map -> assertThat(map)
                .containsEntry("bookmark", expectedBookmark));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldReturnUpdatedBookmarkAfterExplicitTransaction(TransportConnection.Factory connectionFactory)
            throws Exception {
        connectAndHandshake(connectionFactory);

        // bookmark is expected to advance once the auto-commit transaction is committed
        var lastClosedTransactionId = getLastClosedTransactionId();
        var expectedBookmark = new FabricBookmark(
                        List.of(new FabricBookmark.InternalGraphState(
                                getDatabaseId().databaseId().uuid(), lastClosedTransactionId + 1)),
                        List.of())
                .serialize();

        connection.send(wire.begin());
        assertThat(connection).receivesSuccess();

        connection.send(wire.run("CREATE ()")).send(wire.pull());

        assertThat(connection).receivesSuccess().receivesSuccess(meta -> assertThat(meta)
                .doesNotContainKey("bookmark"));

        connection.send(wire.commit());
        assertThat(connection).receivesSuccess(meta -> assertThat(meta).containsEntry("bookmark", expectedBookmark));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldStreamWhenStatementIdNotProvided(TransportConnection.Factory connectionFactory) throws Exception {
        connectAndHandshake(connectionFactory);

        // begin a transaction
        connection.send(wire.begin());
        assertThat(connection).receivesSuccess();

        // execute a query
        connection.send(wire.run("UNWIND range(30, 40) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 0L).containsKeys("fields", "t_first"));

        // request 5 records but do not provide qid
        connection.send(wire.pull(5));
        assertThat(connection)
                .receivesRecord(longValue(30L))
                .receivesRecord(longValue(31L))
                .receivesRecord(longValue(32L))
                .receivesRecord(longValue(33L))
                .receivesRecord(longValue(34L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 2 more records but do not provide qid
        connection.send(wire.pull(2));
        assertThat(connection)
                .receivesRecord(longValue(35L))
                .receivesRecord(longValue(36L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 3 more records and provide qid
        connection.send(wire.pull(3L, 0));

        assertThat(connection)
                .receivesRecord(longValue(37L))
                .receivesRecord(longValue(38L))
                .receivesRecord(longValue(39L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 10 more records but do not provide qid, only 1 more record is available
        connection.send(wire.pull(10L));
        assertThat(connection).receivesRecord(longValue(40L)).receivesSuccess(meta -> assertThat(meta)
                .containsKey("t_last"));

        // rollback the transaction
        connection.send(wire.rollback());
        assertThat(connection).receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    public void shouldSendAndReceiveStatementIds(TransportConnection.Factory connectionFactory) throws Exception {
        connectAndHandshake(connectionFactory);

        // begin a transaction
        connection.send(wire.begin());
        assertThat(connection).receivesSuccess();

        // execute query #0
        connection.send(wire.run("UNWIND range(1, 10) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 0L).containsKeys("fields", "t_first"));

        // request 3 records for query #0
        connection.send(wire.pull(3L, 0));
        assertThat(connection)
                .receivesRecord(longValue(1L))
                .receivesRecord(longValue(2L))
                .receivesRecord(longValue(3L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #1
        connection.send(wire.run("UNWIND range(11, 20) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 1L).containsKeys("fields", "t_first"));

        // request 2 records for query #1
        connection.send(wire.pull(2, 1));
        assertThat(connection)
                .receivesRecord(longValue(11L))
                .receivesRecord(longValue(12L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #2
        connection.send(wire.run("UNWIND range(21, 30) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 2L).containsKeys("fields", "t_first"));

        // request 4 records for query #2
        // no qid - should use the statement from the latest RUN
        connection.send(wire.pull(4));

        assertThat(connection)
                .receivesRecord(longValue(21L))
                .receivesRecord(longValue(22L))
                .receivesRecord(longValue(23L))
                .receivesRecord(longValue(24L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // execute query #3
        connection.send(wire.run("UNWIND range(31, 40) AS x RETURN x"));
        assertThat(connection)
                .receivesSuccess(
                        meta -> assertThat(meta).containsEntry("qid", 3L).containsKeys("fields", "t_first"));

        // request 1 record for query #3
        connection.send(wire.pull(1, 3));
        assertThat(connection).receivesRecord(longValue(31L)).receivesSuccess(meta -> assertThat(meta)
                .containsEntry("has_more", true));

        // request 2 records for query #0
        connection.send(wire.pull(2, 0));
        assertThat(connection)
                .receivesRecord(longValue(4L))
                .receivesRecord(longValue(5L))
                .receivesSuccess(meta -> assertThat(meta).containsEntry("has_more", true));

        // request 9 records for query #3
        connection.send(wire.pull(9, 3));
        assertThat(connection)
                .receivesRecord(longValue(32L))
                .receivesRecord(longValue(33L))
                .receivesRecord(longValue(34L))
                .receivesRecord(longValue(35L))
                .receivesRecord(longValue(36L))
                .receivesRecord(longValue(37L))
                .receivesRecord(longValue(38L))
                .receivesRecord(longValue(39L))
                .receivesRecord(longValue(40L))
                .receivesSuccess(meta -> assertThat(meta).containsKey("t_last").doesNotContainKey("has_more"));

        // commit the transaction
        connection.send(wire.commit());
        assertThat(connection).receivesSuccess();
    }

    private static void assertLegacyNode(
            PackstreamBuf buf, long nodeId, String label, Consumer<Map<String, Object>> propertyAssertions) {
        assertThat(buf)
                .containsStruct(0x4E, 3)
                .containsInt(nodeId)
                .containsList(labels -> Assertions.assertThat(labels).containsExactly(label))
                .containsMap(propertyAssertions);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldReturnLegacyIdForNodes(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        connection
                .send(wire.run("CREATE (m:Movie{title:\"The Matrix\"}) RETURN m"))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .satisfies(
                                buf -> assertLegacyNode(buf, 0, "Movie", properties -> Assertions.assertThat(properties)
                                        .hasSize(1)
                                        .containsEntry("title", "The Matrix")))
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    private static void assertLegacyIdRelationship(PackstreamBuf buf, Consumer<PackstreamBuf> nodeIdAssertions) {
        assertThat(buf)
                .containsInt(0)
                .satisfies(nodeIdAssertions)
                .containsString("PLAYED_IN")
                .containsMap(properties ->
                        Assertions.assertThat(properties).hasSize(1).containsEntry("year", 2021L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldReturnElementIdForRelationships(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        connection
                .send(
                        wire.run(
                                "CREATE (:Actor{name: \"Greg\"})-[r:PLAYED_IN{year: 2021}]->(:Movie{title:\"The Matrix\"}) RETURN r"))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x52, 5)
                        .satisfies(buf -> assertLegacyIdRelationship(
                                buf, b -> assertThat(b).containsInt(0).containsInt(1)))
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldReturnElementIdForPaths(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        connection
                .send(
                        wire.run(
                                "CREATE p=(:Actor{name: \"Greg\"})-[:PLAYED_IN{year: 2021}]->(:Movie{title:\"The Matrix\"}) RETURN p"))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x50, 3)
                        .containsListHeader(2)
                        .satisfies(
                                buf -> assertLegacyNode(buf, 0, "Actor", properties -> Assertions.assertThat(properties)
                                        .hasSize(1)
                                        .containsEntry("name", "Greg")))
                        .satisfies(
                                buf -> assertLegacyNode(buf, 1, "Movie", properties -> Assertions.assertThat(properties)
                                        .hasSize(1)
                                        .containsEntry("title", "The Matrix")))
                        .containsListHeader(1)
                        .containsStruct(0x72, 3)
                        .satisfies(buf -> assertLegacyIdRelationship(buf, b -> {}))
                        .containsList(indices -> Assertions.assertThat(indices).containsExactly(1L, 1L))
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void shouldNegotiateUTCPatch(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndNegotiate(connectionFactory);

        wire.enable(Feature.UTC_DATETIME);
        connection.send(wire.hello());

        assertThat(connection).receivesSuccess(meta -> assertThat(meta)
                .containsEntry("patch_bolt", List.of(Feature.UTC_DATETIME.getId())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldAcceptLegacyOffsetDateTimes(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        var input =
                DateTimeValue.datetime(OffsetDateTime.of(1995, 6, 14, 12, 50, 35, 556000000, ZoneOffset.ofHours(1)));

        var params = new MapValueBuilder();
        params.add("input", input);

        connection.send(wire.run("RETURN $input", params.build())).send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x46, 3)
                        .containsInt(803134235)
                        .containsInt(556000000)
                        .containsInt(3600)
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldNotAcceptUTCDatesWithoutPatch(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        var input =
                DateTimeValue.datetime(OffsetDateTime.of(1995, 6, 14, 12, 50, 35, 556000000, ZoneOffset.ofHours(1)));

        var params = new MapValueBuilder();
        params.add("input", input);

        var utcEnabledWire = new BoltV44Wire();
        utcEnabledWire.enable(Feature.UTC_DATETIME);

        connection.send(utcEnabledWire.run("RETURN $input", params.build()));

        assertThat(connection).receivesFailure();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldAcceptUTCOffsetDateTimes(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory, Feature.UTC_DATETIME);

        var input =
                DateTimeValue.datetime(OffsetDateTime.of(1995, 6, 14, 12, 50, 35, 556000000, ZoneOffset.ofHours(1)));

        var params = new MapValueBuilder();
        params.add("input", input);

        connection.send(wire.run("RETURN $input", params.build())).send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x49, 3)
                        .containsInt(803130635)
                        .containsInt(556000000)
                        .containsInt(3600)
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldRejectLegacyOffsetDatesWhenUTCNegotiated(TransportConnection.Factory connectionFactory)
            throws Exception {
        this.connectAndHandshake(connectionFactory, Feature.UTC_DATETIME);

        var input =
                DateTimeValue.datetime(OffsetDateTime.of(1995, 6, 14, 12, 50, 35, 556000000, ZoneOffset.ofHours(1)));

        var params = new MapValueBuilder();
        params.add("input", input);

        var legacyOffsetWire = new BoltV44Wire();

        connection.send(legacyOffsetWire.run("RETURN $input", params.build()));

        assertThat(connection).receivesFailure(Status.Request.Invalid);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldAcceptLegacyZoneDateTimes(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory);

        connection
                .send(wire.run("RETURN datetime('1995-06-14T12:50:35.556+02:00[Europe/Berlin]')"))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x66, 3)
                        .containsInt(803134235)
                        .containsInt(556000000)
                        .containsString("Europe/Berlin")
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsProvider")
    void shouldAcceptUTCZoneDateTimes(TransportConnection.Factory connectionFactory) throws Exception {
        this.connectAndHandshake(connectionFactory, Feature.UTC_DATETIME);

        connection
                .send(wire.run("RETURN datetime('1995-06-14T12:50:35.556+02:00[Europe/Berlin]')"))
                .send(wire.pull());

        assertThat(connection)
                .receivesSuccess()
                .packstreamSatisfies(stream -> stream.receivesMessage()
                        .containsStruct(0x71, 1)
                        .containsListHeader(1)
                        .containsStruct(0x69, 3)
                        .containsInt(803127035)
                        .containsInt(556000000)
                        .containsString("Europe/Berlin")
                        .asBuffer()
                        .hasNoRemainingReadableBytes())
                .receivesSuccess();
    }
}

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
package org.neo4j.graphdb.impl.notification;

import java.util.Objects;
import org.neo4j.graphdb.InputPosition;
import org.neo4j.graphdb.NotificationCategory;
import org.neo4j.graphdb.SeverityLevel;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.impl.schema.TextIndexProvider;
import org.neo4j.kernel.api.impl.schema.trigram.TrigramIndexProvider;

/**
 * This bundles a specific description with a (potentially) more generic NotificationCode
 */
public enum NotificationCodeWithDescription {
    CARTESIAN_PRODUCT(
            Status.Statement.CartesianProduct,
            "If a part of a query contains multiple disconnected patterns, this will build a "
                    + "cartesian product between all those parts. This may produce a large amount of data and slow down"
                    + " query processing. "
                    + "While occasionally intended, it may often be possible to reformulate the query that avoids the "
                    + "use of this cross "
                    + "product, perhaps by adding a relationship between the different parts or by using OPTIONAL MATCH"),
    RUNTIME_UNSUPPORTED(
            Status.Statement.RuntimeUnsupportedWarning,
            "Selected runtime is unsupported for this query, please use a different runtime instead or fallback to default."),
    INDEX_HINT_UNFULFILLABLE(
            Status.Schema.HintedIndexNotFound, "The hinted index does not exist, please check the schema"),
    JOIN_HINT_UNFULFILLABLE(
            Status.Statement.JoinHintUnfulfillableWarning,
            "The hinted join was not planned. This could happen because no generated plan contained the join key, "
                    + "please try using a different join key or restructure your query."),
    INDEX_LOOKUP_FOR_DYNAMIC_PROPERTY(
            Status.Statement.DynamicProperty,
            "Using a dynamic property makes it impossible to use an index lookup for this query"),
    DEPRECATED_FUNCTION(Status.Statement.FeatureDeprecationWarning, "The query used a deprecated function."),
    DEPRECATED_PROCEDURE(Status.Statement.FeatureDeprecationWarning, "The query used a deprecated procedure."),

    DEPRECATED_RUNTIME_OPTION(
            Status.Statement.FeatureDeprecationWarning, "The query used a deprecated runtime option."),
    PROCEDURE_WARNING(Status.Procedure.ProcedureWarning, "The query used a procedure that generated a warning."),
    DEPRECATED_PROCEDURE_RETURN_FIELD(
            Status.Statement.FeatureDeprecationWarning, "The query used a deprecated field from a procedure."),
    DEPRECATED_RELATIONSHIP_TYPE_SEPARATOR(
            Status.Statement.FeatureDeprecationWarning,
            "The semantics of using colon in the separation of alternative relationship types will change in a future version."),
    DEPRECATED_NODE_OR_RELATIONSHIP_ON_RHS_SET_CLAUSE(
            Status.Statement.FeatureDeprecationWarning,
            "The use of nodes or relationships for setting properties is deprecated and will be removed in a future version. "
                    + "Please use properties() instead."),
    DEPRECATED_SHORTEST_PATH_WITH_FIXED_LENGTH_RELATIONSHIP(
            Status.Statement.FeatureDeprecationWarning,
            "The use of shortestPath and allShortestPaths with fixed length relationships is deprecated and will be removed in a future version. "
                    + "Please use a path with a length of 1 [r*1..1] instead or a Match with a limit."),
    DEPRECATED_TEXT_INDEX_PROVIDER(
            Status.Statement.FeatureDeprecationWarning,
            "The `" + TextIndexProvider.DESCRIPTOR.name()
                    + "` provider for text indexes is deprecated and will be removed in a future version. "
                    + "Please use `" + TrigramIndexProvider.DESCRIPTOR.name() + "` instead."),
    EAGER_LOAD_CSV(
            Status.Statement.EagerOperator,
            "Using LOAD CSV with a large data set in a query where the execution plan contains the "
                    + "Eager operator could potentially consume a lot of memory and is likely to not perform well. "
                    + "See the Neo4j Manual entry on the Eager operator for more information and hints on "
                    + "how problems could be avoided."),
    DEPRECATED_FORMAT(Status.Request.DeprecatedFormat, "The requested format has been deprecated."),
    LARGE_LABEL_LOAD_CSV(
            Status.Statement.NoApplicableIndex,
            "Using LOAD CSV followed by a MATCH or MERGE that matches a non-indexed label will most likely "
                    + "not perform well on large data sets. Please consider using a schema index."),
    MISSING_LABEL(
            Status.Statement.UnknownLabelWarning,
            "One of the labels in your query is not available in the database, make sure you didn't "
                    + "misspell it or that the label is available when you run this statement in your application"),
    MISSING_REL_TYPE(
            Status.Statement.UnknownRelationshipTypeWarning,
            "One of the relationship types in your query is not available in the database, make sure you didn't "
                    + "misspell it or that the label is available when you run this statement in your application"),
    MISSING_PROPERTY_NAME(
            Status.Statement.UnknownPropertyKeyWarning,
            "One of the property names in your query is not available in the database, make sure you didn't "
                    + "misspell it or that the label is available when you run this statement in your application"),
    UNBOUNDED_SHORTEST_PATH(
            Status.Statement.UnboundedVariableLengthPattern,
            "Using shortest path with an unbounded pattern will likely result in long execution times. "
                    + "It is recommended to use an upper limit to the number of node hops in your pattern."),
    EXHAUSTIVE_SHORTEST_PATH(
            Status.Statement.ExhaustiveShortestPath,
            "Using shortest path with an exhaustive search fallback might cause query slow down since shortest path "
                    + "graph algorithms might not work for this use case. It is recommended to introduce a WITH to separate the "
                    + "MATCH containing the shortest path from the existential predicates on that path."),
    RUNTIME_EXPERIMENTAL(Status.Statement.RuntimeExperimental, "You are using an experimental feature"),
    MISSING_PARAMETERS_FOR_EXPLAIN(
            Status.Statement.ParameterNotProvided,
            "Did not supply query with enough parameters. The produced query plan will not be cached and is not executable without EXPLAIN."),
    SUBOPTIMAL_INDEX_FOR_CONTAINS_QUERY(
            Status.Statement.SuboptimalIndexForWildcardQuery,
            "If the performance of this statement using `CONTAINS` doesn't meet your expectations check out the alternative index-providers, see "
                    + "documentation on index configuration."),
    SUBOPTIMAL_INDEX_FOR_ENDS_WITH_QUERY(
            Status.Statement.SuboptimalIndexForWildcardQuery,
            "If the performance of this statement using `ENDS WITH` doesn't meet your expectations check out the alternative index-providers, see "
                    + "documentation on index configuration."),
    CODE_GENERATION_FAILED(
            Status.Statement.CodeGenerationFailed,
            "The database was unable to generate code for the query. A stacktrace can be found in the debug.log."),
    SUBQUERY_VARIABLE_SHADOWING(
            Status.Statement.SubqueryVariableShadowing,
            "Variable in subquery is shadowing a variable with the same name from the outer scope. "
                    + "If you want to use that variable instead, it must be imported into the subquery using importing WITH clause."),
    UNION_RETURN_ORDER(
            Status.Statement.FeatureDeprecationWarning,
            "All subqueries in a UNION [ALL] should have the same ordering for the return columns. "
                    + "Using differently ordered return items in a UNION [ALL] clause is deprecated and will be removed in a future version."),
    HOME_DATABASE_NOT_PRESENT(
            Status.Database.HomeDatabaseNotFound,
            "The home database provided does not currently exist in the DBMS. This command will not take effect until this database is created."),
    DEPRECATED_DATABASE_NAME(
            Status.Statement.FeatureDeprecationWarning,
            "Databases and aliases with unescaped `.` are deprecated unless to indicate that they belong to a composite database. Names containing `.` should be escaped."),
    UNSATISFIABLE_RELATIONSHIP_TYPE_EXPRESSION(
            Status.Statement.UnsatisfiableRelationshipTypeExpression,
            "Relationship type expression cannot possibly be satisfied."),
    REPEATED_RELATIONSHIP_REFERENCE(
            Status.Statement.RepeatedRelationshipReference,
            "A relationship is referenced more than once in the query, which leads to no results because relationships must not occur more than once in each result."),
    REPEATED_VAR_LENGTH_RELATIONSHIP_REFERENCE(
            Status.Statement.RepeatedRelationshipReference,
            "A variable-length relationship variable is bound more than once, which leads to no results because relationships must not occur more than once in each result.");

    private final Status status;
    private final String description;
    private final SeverityLevel severity;

    private final NotificationCategory category;

    NotificationCodeWithDescription(Status status, String description) {
        this.status = status;
        this.description = description;

        var code = status.code();

        if (code instanceof Status.NotificationCode) {
            this.severity = mapSeverity(((Status.NotificationCode) code).getSeverity());
            this.category = mapCategory(((Status.NotificationCode) code).getNotificationCategory());
        } else {
            throw new IllegalStateException("'" + status + "' is not a notification code.");
        }
    }

    // TODO: Move construction of Notifications to a factory with explicit methods per type of notification
    public Notification notification(InputPosition position, NotificationDetail... details) {
        return new Notification(position, details);
    }

    public final class Notification implements org.neo4j.graphdb.Notification {
        private final InputPosition position;
        private final String detailedDescription;

        Notification(InputPosition position, NotificationDetail... details) {
            this.position = position;

            if (details.length == 0) {
                this.detailedDescription = description;
            } else {
                StringBuilder builder = new StringBuilder(description.length());
                builder.append(description);
                builder.append(' ');
                builder.append('(');
                String comma = "";
                for (NotificationDetail detail : details) {
                    builder.append(comma);
                    builder.append(detail);
                    comma = ", ";
                }
                builder.append(')');

                this.detailedDescription = builder.toString();
            }
        }

        @Override
        public String getCode() {
            return status.code().serialize();
        }

        @Override
        public String getTitle() {
            return status.code().description();
        }

        @Override
        public String getDescription() {
            return detailedDescription;
        }

        @Override
        public InputPosition getPosition() {
            return position;
        }

        @Override
        public NotificationCategory getCategory() {
            return category;
        }

        @Override
        public SeverityLevel getSeverity() {
            return severity;
        }

        @Override
        public String toString() {
            return "Notification{" + "position="
                    + position + ", detailedDescription='"
                    + detailedDescription + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Notification that = (Notification) o;
            return Objects.equals(position, that.position)
                    && Objects.equals(detailedDescription, that.detailedDescription);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, detailedDescription);
        }
    }

    private SeverityLevel mapSeverity(String severityLevel) {
        return SeverityLevel.valueOf(severityLevel);
    }

    private NotificationCategory mapCategory(String category) {
        return NotificationCategory.valueOf(category);
    }
}

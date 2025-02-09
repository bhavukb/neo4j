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
package org.neo4j.kernel.impl.coreapi.schema;

import static org.neo4j.graphdb.schema.IndexSettingUtil.toIndexConfigFromIndexSettingObjectMap;

import java.util.List;
import java.util.Map;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.schema.ConstraintCreator;
import org.neo4j.graphdb.schema.IndexSetting;
import org.neo4j.graphdb.schema.IndexType;
import org.neo4j.internal.schema.IndexConfig;

public class BaseRelationshipConstraintCreator extends AbstractConstraintCreator implements ConstraintCreator {
    protected final RelationshipType type;

    BaseRelationshipConstraintCreator(
            InternalSchemaActions actions,
            String name,
            RelationshipType type,
            IndexType indexType,
            IndexConfig indexConfig) {
        super(actions, name, indexType, indexConfig);
        this.type = type;
    }

    @Override
    public ConstraintCreator assertPropertyIsUnique(String propertyKey) {
        return new RelationshipPropertyUniqueConstraintCreator(
                actions, name, type, List.of(propertyKey), indexType, indexConfig);
    }

    @Override
    public ConstraintCreator assertPropertyExists(String propertyKey) {
        return new RelationshipPropertyExistenceCreator(actions, name, type, propertyKey, indexType, indexConfig);
    }

    @Override
    public ConstraintCreator assertPropertyIsNodeKey(String propertyKey) {
        throw new UnsupportedOperationException("Node key constraints are not supported on relationships.");
    }

    /**
     * FIXME RELKEY This is just here until we are ready to expose it on Core API
     *
     * Imposes both a uniqueness constraint, and a property existence constraint, for the given property.
     * This means that all relationships with the given relationship type must have this property, and they must all have different values for the property.
     * Multiple calls to this method will result in compound relationship key constraint.
     *
     * @param propertyKey property to use as the relationship key.
     * @return a {@link ConstraintCreator} instance to be used for further interaction.
     */
    public ConstraintCreator assertPropertyIsRelationshipKey(String propertyKey) {
        return new RelationshipKeyConstraintCreator(actions, name, type, List.of(propertyKey), indexType, indexConfig);
    }

    @Override
    public ConstraintCreator withName(String name) {
        return new BaseRelationshipConstraintCreator(actions, name, type, indexType, indexConfig);
    }

    @Override
    public ConstraintCreator withIndexType(IndexType indexType) {
        return new BaseRelationshipConstraintCreator(actions, name, type, indexType, indexConfig);
    }

    @Override
    public ConstraintCreator withIndexConfiguration(Map<IndexSetting, Object> indexConfiguration) {
        return new BaseRelationshipConstraintCreator(
                actions, name, type, indexType, toIndexConfigFromIndexSettingObjectMap(indexConfiguration));
    }
}

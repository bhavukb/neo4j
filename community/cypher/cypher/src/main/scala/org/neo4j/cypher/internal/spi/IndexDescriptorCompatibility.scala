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
package org.neo4j.cypher.internal.spi

import org.neo4j.cypher.internal.planner.spi
import org.neo4j.cypher.internal.planner.spi.EventuallyConsistent
import org.neo4j.cypher.internal.planner.spi.IndexBehaviour
import org.neo4j.cypher.internal.planner.spi.IndexDescriptor
import org.neo4j.cypher.internal.planner.spi.SkipAndLimit
import org.neo4j.cypher.internal.planner.spi.SlowContains
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundTokenContext
import org.neo4j.internal.kernel.api.exceptions.LabelNotFoundKernelException
import org.neo4j.internal.kernel.api.exceptions.PropertyKeyIdNotFoundKernelException
import org.neo4j.internal.schema
import org.neo4j.internal.schema.LabelSchemaDescriptor
import org.neo4j.internal.schema.RelationTypeSchemaDescriptor
import org.neo4j.internal.schema.SchemaDescriptor
import org.neo4j.internal.schema.SchemaDescriptors
import org.neo4j.kernel.api.exceptions.RelationshipTypeNotFoundException

import scala.util.control.Exception.catching

trait IndexDescriptorCompatibility {
  def kernelToCypher(behaviour: schema.IndexBehaviour): IndexBehaviour = {
    behaviour match {
      case schema.IndexBehaviour.SLOW_CONTAINS => SlowContains
      case schema.IndexBehaviour.SKIP_AND_LIMIT => SkipAndLimit
      case schema.IndexBehaviour.EVENTUALLY_CONSISTENT => EventuallyConsistent
      case _ => throw new IllegalStateException("Missing kernel to cypher mapping for index behaviour: " + behaviour)
    }
  }

  def cypherToKernelSchema(index: spi.IndexDescriptor): SchemaDescriptor = index.entityType match {
    case IndexDescriptor.EntityType.Node(label) =>
      SchemaDescriptors.forLabel(label.id, index.properties.map(_.id):_*)
    case IndexDescriptor.EntityType.Relationship(relType) =>
      SchemaDescriptors.forRelType(relType.id, index.properties.map(_.id):_*)
  }

  def toLabelSchemaDescriptor(tc: TransactionBoundTokenContext,
                              labelName: String,
                              propertyKeys: Seq[String]): Option[LabelSchemaDescriptor] = {
    catching(classOf[LabelNotFoundKernelException], classOf[PropertyKeyIdNotFoundKernelException]) opt {
      val labelId: Int = tc.getLabelId(labelName)
      val propertyKeyIds: Seq[Int] = propertyKeys.map(tc.getPropertyKeyId)
      SchemaDescriptors.forLabel(labelId, propertyKeyIds: _*)
    }
  }

  def toRelTypeSchemaDescriptor(tc: TransactionBoundTokenContext,
                                relTypeName: String,
                                propertyKeys: Seq[String]): Option[RelationTypeSchemaDescriptor] = {
    catching(classOf[RelationshipTypeNotFoundException], classOf[PropertyKeyIdNotFoundKernelException]) opt {
      val relTypeId: Int = tc.getRelTypeId(relTypeName)
      val propertyKeyIds: Seq[Int] = propertyKeys.map(tc.getPropertyKeyId)
      SchemaDescriptors.forRelType(relTypeId, propertyKeyIds: _*)
    }
  }
}

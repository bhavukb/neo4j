/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.frontend

import org.neo4j.cypher.internal.ast.semantics.SemanticError
import org.neo4j.cypher.internal.ast.semantics.SemanticFeature
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class CollectExpressionSemanticAnalysisTest
    extends CypherFunSuite
    with NameBasedSemanticAnalysisTestSuite {

  test(
    "RETURN COLLECT { MATCH (a) }"
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(17, 1, 18)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(7, 1, 8)
      )
    )
  }

  test(
    "RETURN COLLECT { MATCH (n) RETURN n }"
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (m)
      |WHERE COLLECT { OPTIONAL MATCH (a)-[r]->(b) RETURN a.prop } = [5]
      |RETURN m
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (a)
      |WHERE COLLECT {
      |  MATCH (a)
      |  RETURN a.prop
      |}[0] = a
      |RETURN a
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (m)
      |WHERE COLLECT { MATCH (a:A)-[r]->(b) USING SCAN a:A RETURN a } = [m]
      |RETURN m
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { SET a.name = 1 }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression cannot contain any updates",
        InputPosition(17, 2, 8)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(17, 2, 8)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (b) WHERE b.a = a.a DETACH DELETE b }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression cannot contain any updates",
        InputPosition(17, 2, 8)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(17, 2, 8)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (b) MERGE (b)-[:FOLLOWS]->(:Person) }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression cannot contain any updates",
        InputPosition(17, 2, 8)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(17, 2, 8)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { CALL db.labels() YIELD label RETURN label  }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |   MATCH (a)-[:KNOWS]->(b)
      |   RETURN b.name as name
      |   UNION ALL
      |   MATCH (a)-[:LOVES]->(b)
      |   RETURN b.name as name
      |}""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (m)-[r]->(p), (a)-[r2]-(c) RETURN m.prop }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (a)-->(b) WHERE b.prop = 5 RETURN b }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe empty
  }

  test(
    """WITH 5 as aNum
      |MATCH (a)
      |RETURN COLLECT {
      |  WITH 6 as aNum
      |  MATCH (a)-->(b) WHERE b.prop = aNum
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
        InputPosition(54, 4, 13)
      )
    )
  }

  test(
    """WITH 5 as aNum
      |MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b) WHERE b.prop = aNum
      |  WITH 6 as aNum
      |  MATCH (b)-->(c) WHERE c.prop = aNum
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "The variable `aNum` is shadowing a variable with the same name from the outer scope and needs to be renamed",
        InputPosition(92, 5, 13)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b)
      |  WITH b as a
      |  MATCH (b)-->(c)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "The variable `a` is shadowing a variable with the same name from the outer scope and needs to be renamed",
        InputPosition(57, 4, 13)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT {
      |  MATCH (a)-->(b)
      |  WITH b as c
      |  MATCH (c)-->(d)
      |  RETURN a
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe Set.empty
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    RETURN CASE
      |       WHEN true THEN 1
      |       ELSE 2
      |    END
      |}[0] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe Set.empty
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n as a
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m as a
      |}[0] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe Set.empty
  }

  test(
    """RETURN COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION
      |    MATCH (n)
      |    RETURN n
      |}
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldBe Set.empty
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION
      |    MATCH (m)
      |}[1] > 1
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(42, 3, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(66, 5, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n.prop
      |    UNION ALL
      |    MATCH (m)
      |} = [1, 2]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(74, 5, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(88, 6, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m
      |} = [1, 2]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(56, 4, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(42, 3, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION ALL
      |    MATCH (m)
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(69, 5, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(83, 6, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m.prop
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(56, 4, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(42, 3, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    UNION ALL
      |    MATCH (m)
      |    RETURN m
      |    UNION ALL
      |    MATCH (l)
      |} = [1]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(56, 4, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(42, 3, 5)
      ),
      SemanticError(
        "Query cannot conclude with MATCH (must be a RETURN clause, an update clause, a unit subquery call, or a procedure call with no YIELD)",
        InputPosition(111, 8, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """MATCH (person:Person)
      |WHERE COLLECT {
      |    MATCH (n)
      |    RETURN n
      |    UNION
      |    MATCH (m)
      |    RETURN m
      |    UNION
      |    MATCH (l)
      |    RETURN l
      |} = [1, 2, 3]
      |RETURN person.name
     """.stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(69, 5, 5)
      ),
      SemanticError(
        "All sub queries in an UNION must have the same return column names",
        InputPosition(106, 8, 5)
      ),
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(28, 2, 7)
      )
    )
  }

  test(
    """RETURN COLLECT {
      |  MATCH (a)
      |  RETURN *
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(7, 1, 8)
      )
    )
  }

  test(
    """RETURN COLLECT {
      |  MATCH (a)
      |  RETURN a.prop1, a.prop2
      |}
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(7, 1, 8)
      )
    )
  }

  test(
    """MATCH (a)
      |WHERE COLLECT {
      |  MATCH (a)
      |  RETURN *
      |}[0] = a
      |RETURN a
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(16, 2, 7)
      )
    )
  }

  test(
    """MATCH (a)
      |RETURN COLLECT { MATCH (m)-[r]->(p), (a)-[r2]-(c) RETURN * }
      |""".stripMargin
  ) {
    runSemanticAnalysis().errors.toSet shouldEqual Set(
      SemanticError(
        "A Collect Expression must end with a single return column.",
        InputPosition(17, 2, 8)
      )
    )
  }
}

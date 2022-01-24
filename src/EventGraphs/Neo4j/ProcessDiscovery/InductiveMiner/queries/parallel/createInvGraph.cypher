CALL gds.graph.drop("iDFG", false) YIELD graphName AS droppedgraph
WITH count(droppedgraph) AS ignore
CALL gds.graph.create.cypher(
"iDFG",
"MATCH (c:Step) WHERE id(c) = $stepId
MATCH (c) -[:STEP_NODE]-> (cli:InvClass) RETURN DISTINCT id(cli) AS id",
"MATCH (c:Step) WHERE id(c) = $stepId MATCH (s:InvClass) -[:DF_C_I]-> (t:InvClass) WHERE (c) -[:STEP_NODE]-> (s) AND (c) -[:STEP_NODE]-> (t) RETURN id(s) AS source, id(t) AS target",
{parameters: {stepId:$stepId, entityType:$entityType}}) YIELD graphName, nodeCount, relationshipCount, createMillis
RETURN graphName
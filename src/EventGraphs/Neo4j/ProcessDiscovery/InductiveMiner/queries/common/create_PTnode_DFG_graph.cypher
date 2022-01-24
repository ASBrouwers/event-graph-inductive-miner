CALL gds.graph.drop($graphName, false) YIELD graphName AS droppedGraph
WITH count(droppedGraph) AS ignore
CALL gds.graph.create.cypher(
$graphName,
"MATCH (pt:PT_node) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class {Type: $classType}) RETURN id(cl) AS id",
"MATCH (pt:PT_node) WHERE id(pt) = $ptId MATCH (s:Class {Type: $classType}) -[:DF_C {EntityType: $entityType}]-> (t:Class {Type: $classType}) WHERE (pt) -[:REPRESENTS]-> (s) AND (pt) -[:REPRESENTS]-> (t) RETURN id(s) AS source, id(t) AS target",
{parameters: {ptId:$ptId, graphName:$graphName, entityType:$entityType, classType: $classType}}
) YIELD graphName, nodeCount, relationshipCount, createMillis RETURN nodeCount, relationshipCount;
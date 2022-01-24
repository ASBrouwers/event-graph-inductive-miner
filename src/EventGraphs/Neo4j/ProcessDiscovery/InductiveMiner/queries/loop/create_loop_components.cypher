CALL gds.graph.drop($graphName, false) YIELD graphName AS droppedgraph
WITH count(droppedgraph) AS ignore
MATCH (pt)
WHERE id(pt) = $ptId
CALL gds.graph.create.cypher(
$graphName,
'MATCH (c) WHERE id(c) = $ptId
MATCH (cl:Class {Type: $classType})
WHERE (c) -[:REPRESENTS]-> (cl)
RETURN id(cl) AS id',
'MATCH (c) WHERE id(c) = $ptId
MATCH (c) -[rep1:REPRESENTS]-> (cl:Class {Type: $classType}) -[:DF_C {EntityType: $entityType}]-> (cl2:Class {Type: $classType}) <-[rep2:REPRESENTS]- (c)
WHERE NOT (rep1.isEnd OR rep2.isStart)
RETURN id(cl) AS source, id(cl2) AS target',
{parameters: {ptId:$ptId, graphName:$graphName, entityType:$entityType, classType: $classType}}
) YIELD nodeCount
RETURN 1
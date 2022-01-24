MATCH (pt) WHERE id(pt) = $ptId
MATCH (alg:Algorithm) -[:PRODUCES]-> () -[:CONTAINS]-> (pt)
MATCH (pt) -[:MODEL_EDGE]-> (pt_c) -[:PT_L]-> (l_c)
MATCH (l_c) -[:HAS]-> (e)
WITH l_c, count(e) AS eventCount, alg
  WHERE count(e) = 0
CREATE (ent:Entity)
CREATE (l_c) -[:HAS]-> (start:Event {Activity: "start", isStart: true}) -[:CORR]-> (ent) //TODO change to $classType
CREATE (l_c) -[:HAS]-> (end:Event {Activity: "end", isEnd: true}) -[:CORR]-> (ent) //TODO change to $classType
CREATE (start) -[:DF {EntityType: $entityType, logs: [id(l_c)]}]-> (end)
MERGE (alg) -[:ALGORITHM_NODE]-> (start)
MERGE (alg) -[:ALGORITHM_NODE]-> (end)

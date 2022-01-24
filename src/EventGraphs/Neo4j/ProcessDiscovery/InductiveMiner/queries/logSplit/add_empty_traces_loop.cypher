MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:PT_L]-> (l:LogTree)
MATCH (alg:Algorithm) -[:PRODUCES]-> () -[:CONTAINS]-> (pt)
MATCH (pt) -[:MODEL_EDGE]-> (pt_main {main: true})
MATCH (pt_main) -[:PT_L]-> (l_main:LogTree)
MATCH (l_main) -[:LOG_COMP]-> (comp_main)
MATCH (l) -[:HAS]-> (st:Event)
  WHERE size([() -[r:DF]-> (st) WHERE id(l) IN r.logs | r]) = 0
CALL apoc.do.when(NOT (comp_main) -[:COMP_CL]-> () <-[:OBSERVES]- (st),
"CREATE (ent:Entity)
CREATE (l_main) -[:HAS]-> (start:Event {Activity: 'start', isStart: true}) -[:CORR]-> (ent)
CREATE (l_main) -[:HAS]-> (end:Event {Activity: 'end', isEnd: true}) -[:CORR]-> (ent)
MERGE (alg) -[:ALGORITHM_NODE]-> (start)
MERGE (alg) -[:ALGORITHM_NODE]-> (end)
CREATE (start) -[:DF {EntityType: $entityType, logs: [id(l_main)]}]-> (end)
RETURN 0",
"RETURN 1",
{comp_main:comp_main, st:st, l_main:l_main, alg:alg, entityType:$entityType}) YIELD value
WITH DISTINCT l, l_main, comp_main, alg
MATCH (l) -[:HAS]-> (ed:Event)
  WHERE size([(ed) -[r:DF]-> () WHERE id(l) IN r.logs | r]) = 0
CALL apoc.do.when(NOT (comp_main) -[:COMP_CL]-> () <-[:OBSERVES]- (ed),
"CREATE (ent:Entity)
CREATE (l_main) -[:HAS]-> (start:Event {Activity: 'start', isStart: true}) -[:CORR]-> (ent)
CREATE (l_main) -[:HAS]-> (end:Event {Activity: 'end', isEnd: true}) -[:CORR]-> (ent)
MERGE (alg) -[:ALGORITHM_NODE]-> (start)
MERGE (alg) -[:ALGORITHM_NODE]-> (end)
CREATE (start) -[:DF {EntityType: $entityType, logs: [id(l_main)]}]-> (end)
RETURN 0",
"RETURN 1",
{comp_main:comp_main, ed:ed, l_main:l_main, alg:alg, entityType:$entityType}) YIELD value
RETURN *
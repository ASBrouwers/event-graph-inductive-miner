MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Parallel"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree:Algorithm_node)
SET l.splitType = pt.type
WITH pt, l
MATCH (l) -[:LOG_COPY]-> (f)
  WHERE NOT () -[:DF_W]-> (f)
MATCH p=(f) -[:DF_W*0..]-> (e)
SET e.rank = length(p), e.path = id(nodes(p)[0])
WITH DISTINCT pt, l
MATCH (st)
  WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:Component)
MATCH (alg) -[:ALGORITHM_NODE]-> (l)
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol}) <-[:ALGORITHM_NODE]- (alg)
MERGE (l_c) -[:LOG_COMP]-> (comp)
WITH *
//MATCH (l) -[:HAS_ENT]-> (ent:Entity)
//CREATE (l_c) -[:HAS_ENT]-> (ent)
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH l_c, l, comp, st
MATCH (comp) -[:COMP_CL]-> (cl:Class) <-[:OBSERVES]- () -[:EVENT_COPY]-> (e) <-[:LOG_COPY]- (l)
OPTIONAL MATCH (e) -[dfw:DF_W]-> (e2) <-[:EVENT_COPY]- () -[:OBSERVES]-> () <-[:COMP_CL]- (comp2) <-[:STEP_NODE]- (st)
  WHERE comp <> comp2
DELETE dfw
WITH l_c, comp, e, count(e2) AS ignore, e.path AS path  ORDER BY e.rank
MATCH (e_o) -[:EVENT_COPY]-> (e)
MERGE (l_c) -[:HAS]-> (e_o)
WITH l_c, comp, collect(e) AS events, path
CALL apoc.do.when(size(events) > 1,
"UNWIND range(0, size(events)-2) AS i
WITH events[i] AS e1, events[i+1] AS e2
MERGE (e1) -[:DF_W]-> (e2)
",
"",
{events:events}) YIELD value
RETURN count(*)
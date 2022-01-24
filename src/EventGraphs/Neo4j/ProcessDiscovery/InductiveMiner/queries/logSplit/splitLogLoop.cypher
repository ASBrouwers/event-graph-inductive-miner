MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Loop"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree)
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
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol})
CREATE (l_c) <-[:ALGORITHM_NODE]- (alg)
CREATE (l_c) -[:LOG_COMP]-> (comp)
WITH DISTINCT pt, l, l_c, comp, st
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true, main: comp.main}) -[:PT_L]-> (l_c)
WITH DISTINCT pt, l_c, l, comp, st
MATCH (comp) -[:COMP_CL]-> () <-[:OBSERVES]- (e_o) -[:EVENT_COPY]-> (e) <-[:LOG_COPY]- (l) // Events whose classes correspond to comp
WITH DISTINCT l_c, comp, e, st, e_o
CREATE (l_c) -[:HAS]-> (e_o)
WITH DISTINCT l_c, comp, e, st
MATCH (e) -[dfw:DF_W]-> (e2) <-[:EVENT_COPY]- () -[:OBSERVES]-> () <-[:COMP_CL]- (comp2) <-[:STEP_NODE]- (st)
  WHERE comp <> comp2 AND comp2 IS NOT NULL //OR comp = comp2 AND (st) <-[:TRIED_STEP]- () -[:REPRESENTS {isStart: true}]-> () <-[:OBSERVES]- () -[:EVENT_COPY]-> (e2)
  AND (st) <-[:TRIED_STEP]- () -[:REPRESENTS {isEnd: true}]-> () <-[:OBSERVES]- () -[:EVENT_COPY]-> (e)
WITH l_c, comp, comp2, e, e2, dfw
DELETE dfw
RETURN count(*)
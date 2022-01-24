MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Sequence"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree:Algorithm_node)
SET l.splitType = pt.type
WITH DISTINCT pt, l
MATCH (st)
  WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:SEQ_COMP)
MATCH (alg) -[:ALGORITHM_NODE]-> (l)
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol}) <-[:ALGORITHM_NODE]- (alg)
WITH DISTINCT *
MERGE (l_c) -[:LOG_COMP]-> (comp)
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH *
MATCH (m:Model) -[:CONTAINS]-> (pt)
MERGE (m) -[:CONTAINS]-> (pt_c)
WITH DISTINCT l_c, l, comp, st
MATCH (comp) -[:COMP_CL]-> () <-[:OBSERVES]- () -[:EVENT_COPY]-> (e) <-[:LOG_COPY]- (l)
MATCH (e_o) -[:EVENT_COPY]-> (e)
WITH DISTINCT l_c, l, comp, st, e, e_o
CREATE (l_c) -[:HAS]-> (e_o)
WITH DISTINCT l_c, l, comp, e, st
MATCH (e) -[dfw:DF_W]-> (e2) <-[:EVENT_COPY]- () -[:OBSERVES]-> () <-[:COMP_CL]- (comp2:SEQ_COMP) <-[:STEP_NODE]- (st)
  WHERE comp <> comp2
DELETE dfw
// PART 2: Tau
//WITH count(*) AS ignore
//MATCH (pt) WHERE id(pt) = $ptId
//MATCH (st)
//  WHERE id(st) = $stepId
//MATCH (st) -[:STEP_NODE]-> (comp:SEQ_COMP)
//MATCH (pt) -[:PT_L]-> (l) -[:LOG_COPY]-> (e_c) <-[:EVENT_COPY]- (e) -[:CORR]-> (ent:Entity)
//WITH pt, l, comp, ent, e
//OPTIONAL MATCH (cl) <-[:OBSERVES]- (e)
//  WHERE  (comp) -[:COMP_CL]-> (cl)
//WITH comp, e
//  WHERE e IS NULL
//MATCH (l) -[:LOG_SPLIT]-> (l_c) -[:HAS]-> () -[:OBSERVES]-> () <-[:COMP_CL]- (comp)
//CREATE (l_c) -[:HAS]-> (tau:Event {Activity: "tau"})
//RETURN count(*)
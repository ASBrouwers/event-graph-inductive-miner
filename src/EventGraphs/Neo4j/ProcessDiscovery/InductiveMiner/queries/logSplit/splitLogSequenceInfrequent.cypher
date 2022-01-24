MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Sequence"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree:Algorithm_node)
SET l.splitType = pt.type
//WITH pt, l
//MATCH (l) -[:LOG_COPY]-> (f:Event)
//  WHERE NOT () -[:DF_W]-> (f)
//MATCH p=(f) -[:DF_W*0..]-> (e:Event)
//SET e.rank = length(p), e.path = id(nodes(p)[0])
WITH DISTINCT pt, l
MATCH (st:Step)
  WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:SEQ_COMP)
MATCH (alg:Algorithm) -[:ALGORITHM_NODE]-> (l)
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol}) <-[:ALGORITHM_NODE]- (alg)
MERGE (l_c) -[:LOG_COMP]-> (comp)
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH l_c, l, comp, st
MATCH (comp) -[:COMP_CL]-> () <-[:OBSERVES]- () -[:EVENT_COPY]-> (e:Event) <-[:LOG_COPY]- (l)
MATCH (e_o:Event) -[:EVENT_COPY]-> (e)
MERGE (l_c) -[:HAS]-> (e_o)
WITH l_c, l, comp, e, st
MATCH (e) -[dfw:DF_W]-> (e2:Event) <-[:EVENT_COPY]- (e2_o:Event)
WITH DISTINCT *
MATCH (e2_o) -[:OBSERVES]-> (cl2:Class) <-[:COMP_CL]- (comp2:SEQ_COMP) <-[:STEP_NODE]- (st)
  WHERE comp <> comp2
DELETE dfw
//WITH l_c, comp, e, count(e2) AS count, e.path AS path  ORDER BY e.rank
// PART 2: Tau
WITH count(*) AS ignore
MATCH (pt) WHERE id(pt) = $ptId
MATCH (st)
  WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:SEQ_COMP)
MATCH (pt) -[:PT_L]-> (l) -[:LOG_COPY]-> (e_c) <-[:EVENT_COPY]- (e) -[:CORR]-> (ent:Entity)
WITH pt, l, comp, ent, e
OPTIONAL MATCH (comp) -[:COMP_CL]-> (cl) <-[:OBSERVES]- (e) -[:CORR]-> (ent)
WITH comp, e
  WHERE e IS NULL
MATCH (l) -[:LOG_SPLIT]-> (l_c) -[:HAS]-> () -[:OBSERVES]-> () <-[:COMP_CL]- (comp)
CREATE (l_c) -[:HAS]-> (tau:Event {Activity: "tau"})
RETURN count(*)
MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Exclusive"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree)
SET l.splitType = pt.type
WITH pt, l
MATCH (st) -[:STEP_NODE]-> (comp:Component)
  WHERE id(st) = $stepId
MATCH (alg) -[:ALGORITHM_NODE]-> (l)
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol}) <-[:ALGORITHM_NODE]- (alg)
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH *
MATCH (m:Model) -[:CONTAINS]-> (pt)
MERGE (m) -[:CONTAINS]-> (pt_c)
MERGE (l_c) -[:LOG_COMP]-> (comp)
WITH pt, l, l_c, comp
MATCH (l) -[:LOG_COPY]-> (f)
  WHERE NOT () -[:DF_W]-> (f)
MATCH p=(f) -[:DF_W*0..]-> (e)
SET e.rank = length(p), e.path = id(nodes(p)[0])
WITH DISTINCT pt, l, l_c, comp, e, e.path AS pathId, CASE WHEN exists((e) <-[:EVENT_COPY]- () -[:OBSERVES]-> (:Class) <-[:COMP_CL]- (comp)) THEN 1 ELSE 0 END AS eventMatch
WITH pt, l, l_c, comp, collect(e) AS events, pathId, sum(eventMatch) AS matchingEvents
WITH pt, l, events, pathId, apoc.agg.maxItems(l_c, matchingEvents).items[0] AS bestSublog
UNWIND events AS eventCopy
MATCH (event) -[:EVENT_COPY]-> (eventCopy)
MERGE (bestSublog) -[:HAS]-> (event)
RETURN count(*)
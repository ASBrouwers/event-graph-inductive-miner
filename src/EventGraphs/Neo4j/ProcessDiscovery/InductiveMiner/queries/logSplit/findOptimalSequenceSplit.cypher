// Find sequence step to base split on
MATCH (st) WHERE id(st) = $stepId
// Find full component sequence
MATCH (pt) -[:TRIED_STEP]-> (st) -[:STEP_NODE]-> (comp1:SEQ_COMP)
  WHERE NOT () -[:DF_SEQ]-> (comp1)
SET pt.search = false, pt.type = "Sequence"
WITH *
MATCH p=(comp1) -[:DF_SEQ*0..]- (comp_end)
  WHERE NOT (comp_end) -[:DF_SEQ]-> ()
// Set log property
WITH DISTINCT pt, st, nodes(p) AS comps
MATCH (pt) -[:PT_L]-> (log)
SET log.splitType = pt.type
WITH *
UNWIND comps AS comp
MERGE (log) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node) <-[:COMP_LOG]- (comp)
WITH *
MATCH (alg) -[:ALGORITHM_NODE]-> (log)
MERGE (alg) -[:ALGORITHM_NODE]-> (l_c)
MERGE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH DISTINCT pt, st, log, comps
// // Get full traces from working copy
MATCH (log) -[:LOG_COPY]-> (ev_s)
  WHERE NOT () -[:DF_W]-> (ev_s)
MATCH p=(ev_s) -[:DF_W*]-> (ev_e)
  WHERE NOT (ev_e) -[:DF_W]-> ()
// // Generate all possible split index combinations
WITH pt, st,  log, nodes(p) AS events, comps
WITH pt, st, log, events, comps, REDUCE(output = [], r IN range(0, size(events)) | output + REDUCE(o = [], i IN range(0, size(comps)-2)| o + r)) AS idxs
WITH pt, st, log, events, comps, apoc.coll.combinations(idxs, size(comps)-1) AS idx, size(comps) AS nrComps
UNWIND idx AS idxs
WITH DISTINCT pt, log, st, events, comps, [0] + idxs + [size(events)] AS fullIdxs
WITH pt, st, log, [i IN range(0, size(fullIdxs) - 2) | [fullIdxs[i], fullIdxs[i+1]]] AS startEnds, events, comps
UNWIND range(0, size(startEnds)) AS startEndIdx
WITH DISTINCT *, startEnds[startEndIdx] AS startEnd, comps[startEndIdx] AS comp
UNWIND events[startEnd[0]..startEnd[1]] AS compEvent
WITH DISTINCT *
MATCH (compEvent) <-[:EVENT_COPY]- (oCompEvent)
MATCH (oCompEvent)-[:OBSERVES]-> (compEventClass)
  WHERE (pt) -[:REPRESENTS]-> (compEventClass)
WITH DISTINCT *, CASE WHEN (compEventClass) <-[:COMP_CL]- (comp) THEN 0 ELSE 1 END AS wrongEvent
WITH pt, log, events, comps, startEnds, sum(wrongEvent) AS wrongEvents
WITH pt, log, events, apoc.agg.minItems(startEnds, wrongEvents, 1) AS minItems, comps
WITH pt, log, events, minItems.items[0] AS bestSplit, minItems.value AS val, comps
UNWIND range(0, size(comps)-1) AS compId
WITH DISTINCT *, comps[compId] AS comp, bestSplit[compId] AS compSplit
MATCH (log) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node) <-[:COMP_LOG]- (comp)
MATCH (pt_c) -[:PT_L]-> (l_c)
WITH *
MATCH (m:Model) -[:CONTAINS]-> (pt)
MERGE (m) -[:CONTAINS]-> (pt_c)
WITH log, l_c, comp, events[compSplit[0]..compSplit[1]] AS compEvents
UNWIND compEvents AS compEvent
MATCH (compEvent) <-[:EVENT_COPY]- (e)
  WHERE (e) -[:OBSERVES]-> () <-[:COMP_CL]- (comp)
CREATE (l_c) -[:HAS]-> (e)
WITH DISTINCT log, l_c, e
MATCH (e) -[:EVENT_COPY]- (e_c) -[r:DF_W]-> (e_c2) -[:EVENT_COPY]- (e2) <-[:HAS]- (l_c2) <-[:LOG_SPLIT]- (log)
  WHERE l_c <> l_c2
WITH DISTINCT r, l_c, l_c2
// MATCH (e2) <-[:HAS]- (l_c2) <-[:LOG_SPLIT]- (log)
DELETE r
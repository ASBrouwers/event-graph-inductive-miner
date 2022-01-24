MATCH (st) WHERE id(st) = $stepId
MATCH (pt) -[:TRIED_STEP]-> (st)
MATCH (pt) -[:PT_L]-> (l)
MATCH (st) -[:STEP_NODE]-> (comp:Component)
MATCH (l_c) -[:LOG_COMP]-> (comp)
MATCH (l) -[:LOG_COPY]-> (e_c)
  WHERE (e_c) <-[:EVENT_COPY]- () <-[:HAS]- (l_c)
  AND NOT (comp) -[:COMP_CL]-> () <-[:OBSERVES]- () -[:EVENT_COPY]-> (e_c)
OPTIONAL MATCH (prev) -[:DF_W]-> (e_c)
OPTIONAL MATCH (e_c) -[:DF_W]-> (next)
CALL apoc.do.when(prev IS NOT NULL AND next IS NOT NULL,
"MERGE (prev) -[:DF_W]-> (next) RETURN 1",
"RETURN 0",
{prev:prev, next:next}) YIELD value
MATCH (e_c) <-[:EVENT_COPY]- () <-[r:HAS]- (l_c)
DELETE r
DETACH DELETE e_c
RETURN value
MATCH (pt) WHERE id(pt) = $ptId
MATCH (alg:Algorithm) -[:PRODUCES]-> () -[:CONTAINS]-> (pt)
MATCH (pt) -[:PT_L]-> (l)
MATCH (l) -[:HAS]-> (e1:Event)
MATCH (e1) -[:EVENT_COPY]-> (ec1) -[:DF_W]-> (ec2) <-[:EVENT_COPY]- (e2)
//  WHERE (l) -[:LOG_COPY]-> (ec1) AND (l) -[:LOG_COPY]-> (ec2)
MATCH (l) -[:LOG_SPLIT]-> (l_c) -[:HAS]-> (e1)
WHERE (l_c) -[:HAS]-> (e2)
WITH l, e1, e2, collect(id(l_c)) AS logIds, alg
MERGE (e1) -[df:DF {EntityType: $entityType}]-> (e2)
SET df.logs = apoc.coll.toSet(CASE WHEN df.logs IS NULL THEN logIds ELSE df.logs + logIds END)
WITH count(*) AS ignore, l, alg
MATCH (l) -[:LOG_SPLIT]-> (l_c)
MATCH (l_c) -[:HAS]-> (e:Event) -[:EVENT_COPY]-> (ec)
WITH *
CALL apoc.do.when(
NOT () -[:DF_W]-> (ec),
"MERGE (st:Event {isStart: true, Activity: 'start'}) -[r:DF]-> (e)
ON CREATE SET r.logs = [id(l_c)]
ON MATCH SET r.logs = r.logs + [id(l_c)]
MERGE (l_c) -[:L_START]-> (st)
MERGE (alg) -[:ALGORITHM_NODE]-> (st)
 RETURN st",
"RETURN null",
{l_c:l_c, e:e, alg:alg}
) YIELD value AS val1
CALL apoc.do.when(
  NOT (ec) -[:DF_W]-> (),
"MERGE (ed:Event {isEnd: true, Activity: 'end'}) <-[r:DF]- (e)
ON CREATE SET r.logs = [id(l_c)]
ON MATCH SET r.logs = r.logs + [id(l_c)]
 MERGE (l_c) -[:L_END]-> (ed)
MERGE (alg) -[:ALGORITHM_NODE]-> (ed)
 RETURN ed",
  "RETURN null",
  {l_c:l_c, e:e, alg:alg}
) YIELD value AS val2
WITH DISTINCT l
MATCH (l) -[:LOG_COPY]-> (c)
DETACH DELETE c
RETURN *
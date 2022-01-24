MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:PT_L]-> (l) -[:HAS]-> (e) -[:OBSERVES]-> (cl:Class)
WHERE NOT () -[:REPRESENTS]-> (cl)
MERGE (pt) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
WITH pt, l, cl, e
CALL apoc.do.when(
  size([() -[r:DF {EntityType: $entityType}]-> (e) WHERE id(l) IN r.logs | e]) = 0,
  "SET rep.isStart = true RETURN 0",
  "RETURN 1",
  {pt:pt, cl:cl, e:e, l:l, rep:rep}
) YIELD value AS ignore
CALL apoc.do.when(
size([(e) -[r:DF {EntityType: $entityType}]-> () WHERE id(l) IN r.logs | e]) = 0,
"SET rep.isEnd = true RETURN 0",
"RETURN 1",
{pt:pt, cl:cl, e:e, l:l, rep:rep}
) YIELD value
RETURN id(pt) AS rootId, ignore, value
MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:MODEL_EDGE]-> (ch)
OPTIONAL MATCH (pt) -[rep:REPRESENTS {isStart: true}]-> (s:Class {Type: $classType}) <-[:REPRESENTS]- (ch)
CALL apoc.do.when(s IS NOT NULL,
"MATCH (ch) -[rep:REPRESENTS]-> (s) SET rep.isStart = true RETURN 1",
"RETURN 1", {s:s, ch:ch}) YIELD value
WITH pt, ch
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (e:Class {Type: $classType}) <-[rep:REPRESENTS]- (ch)
SET rep.isEnd = true
RETURN count(pt) AS result
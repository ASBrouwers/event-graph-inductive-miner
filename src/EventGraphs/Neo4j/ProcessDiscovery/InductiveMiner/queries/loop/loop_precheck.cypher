MATCH (c) WHERE id(c) = $ptId
MATCH (c) -[rep:REPRESENTS]-> (cl:Class {Type: $classType})
WHERE NOT rep.isStart AND NOT rep.isEnd
RETURN count(cl) AS count
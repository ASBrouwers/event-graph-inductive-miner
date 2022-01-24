MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
RETURN count(DISTINCT cl) AS classCount
MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
DETACH DELETE cl
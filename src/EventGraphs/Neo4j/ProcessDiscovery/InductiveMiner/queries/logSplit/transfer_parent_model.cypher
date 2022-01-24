MATCH (pt) WHERE id(pt) = $ptId
MATCH (parent) -[:MODEL_EDGE]-> (pt)
MATCH (m:Model) -[:CONTAINS]-> (parent)
MERGE (m) -[:CONTAINS]-> (pt)
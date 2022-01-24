MATCH (pt)
WHERE id(pt) = $ptNode
SET pt.petri = true
WITH pt
MATCH (pt1) -[:PT_SEQ]-> (pt2)
WHERE (pt) -[:MODEL_EDGE]-> (pt1) AND (pt) -[:MODEL_EDGE]-> (pt2)
WITH pt, pt1, pt2
MATCH (pt1) -[:PETRI_E]-> (e)
MATCH (pt2) -[:PETRI_S]-> (s)
CALL apoc.refactor.mergeNodes([s, e], {properties: 'discard', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node AS s_node
WITH s_node, pt, pt1, pt2
MATCH () -[r:PETRI_S|PETRI_E]-> (s_node)
DELETE r
WITH pt, pt1, pt2
OPTIONAL MATCH (pt1) -[r1:PETRI_S]-> (startPlace)
MERGE (pt) -[:PETRI_S]-> (startPlace)
DELETE r1
WITH pt, pt1, pt2
OPTIONAL MATCH (pt2) -[r2:PETRI_E]-> (endPlace)
MERGE (pt) -[:PETRI_E]-> (endPlace)
DELETE r2
MATCH (pt)
WHERE id(pt) = $ptNode
SET pt.petri = true
WITH pt
MATCH (pt) -[:MODEL_EDGE]-> (pt_child)
MATCH (pt_child) -[r1:PETRI_S]-> (start)
MATCH (pt_child) -[r2:PETRI_E]-> (end)
DELETE r1, r2
WITH pt, collect(start) AS start, collect(end) AS end
CALL apoc.refactor.mergeNodes(start, {properties: 'discard', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node AS s_node
CALL apoc.refactor.mergeNodes(end, {properties: 'discard', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node AS e_node
CREATE (pt) -[:PETRI_S]-> (s_node)
CREATE (pt) -[:PETRI_E]-> (e_node)
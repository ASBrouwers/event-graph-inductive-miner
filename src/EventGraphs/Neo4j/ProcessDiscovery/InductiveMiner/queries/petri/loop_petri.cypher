MATCH (pt)
WHERE id(pt) = $ptNode
SET pt.petri = true
WITH pt
MATCH (model) -[:CONTAINS]-> (pt)
CREATE (pt) -[:PETRI_S]-> (p_s:PetriNet {type: 'p', model: model.ID}) -[:MODEL_EDGE]-> (t_s:PetriNet {type: 'tau', model: model.ID})
CREATE (t_e:PetriNet {type: 'tau', model: model.ID}) -[:MODEL_EDGE]-> (p_e:PetriNet {type: 'p', model: model.ID}) <-[:PETRI_E]- (pt)
CREATE (model) -[:CONTAINS_PN]-> (p_s), (model) -[:CONTAINS_PN]-> (t_s), (model) -[:CONTAINS_PN]-> (p_e), (model) -[:CONTAINS_PN]-> (t_e)
WITH DISTINCT pt, t_s, t_e
MATCH (pt) -[:MODEL_EDGE]-> (main_pt {main: true})
MATCH (pt) -[:MODEL_EDGE]-> (loop_pt {main: false})
MATCH (main_pt) -[r1:PETRI_S]-> (main_s)
MATCH (loop_pt) -[r2:PETRI_E]-> (loop_e)
DELETE r1, r2
WITH main_s, collect(loop_e) AS loop_es, pt, main_pt, loop_pt, t_s, t_e
CALL apoc.refactor.mergeNodes([main_s] + loop_es, {properties: 'discard', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node AS s_node
MERGE (t_s) -[:MODEL_EDGE]-> (s_node)
WITH pt, s_node, main_pt, loop_pt, t_e
MATCH (loop_pt) -[r3:PETRI_S]-> (loop_s)
MATCH (main_pt) -[r4:PETRI_E]-> (main_e)
DELETE r3, r4
WITH s_node, main_e, collect(loop_s) AS loop_ss, pt, t_e
CALL apoc.refactor.mergeNodes([main_e] + loop_ss, {properties: 'discard', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node AS e_node
MERGE (e_node) -[:MODEL_EDGE]-> (t_e)

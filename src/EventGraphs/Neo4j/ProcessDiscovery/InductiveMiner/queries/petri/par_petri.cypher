MATCH (pt)
WHERE id(pt) = $ptNode
SET pt.petri = true
WITH pt
MATCH (model) -[:CONTAINS]-> (pt)
CREATE (pt) -[:PETRI_S]-> (p_s:PetriNet {type: 'p', model: model.ID}) -[:MODEL_EDGE]-> (t_s:PetriNet {type: 'tau', model: model.ID})
CREATE (t_e:PetriNet {type: 'tau', model: model.ID}) -[:MODEL_EDGE]-> (p_e:PetriNet {type: 'p', model: model.ID}) <-[:PETRI_E]- (pt)
CREATE (model) -[:CONTAINS_PN]-> (p_s), (model) -[:CONTAINS_PN]-> (t_s), (model) -[:CONTAINS_PN]-> (p_e), (model) -[:CONTAINS_PN]-> (t_e)
WITH pt, t_s, t_e
MATCH (pt) -[:MODEL_EDGE]-> (pt_child)
MATCH (pt_child) -[r1:PETRI_S]-> (ps)
CREATE (t_s) -[:MODEL_EDGE]-> (ps)
DELETE r1
WITH pt_child, t_e
MATCH (pt_child) -[r2:PETRI_E]-> (pe)
CREATE (pe) -[:MODEL_EDGE]-> (t_e)
DELETE r2

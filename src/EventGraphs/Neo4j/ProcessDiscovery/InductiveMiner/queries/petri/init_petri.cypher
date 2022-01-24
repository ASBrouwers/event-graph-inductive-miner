MATCH (pt)
WHERE id(pt) = $ptNode
MATCH (model) -[:CONTAINS]-> (pt)
MATCH (pt) -[:MODEL_EDGE*0..]-> (p)
SET p.petri = false
WITH DISTINCT model, p
WHERE NOT (p) -[:MODEL_EDGE]-> ()
SET p.petri = true
WITH p, model
CALL apoc.do.when(size([(p) -[:REPRESENTS]-> (cl) | cl]) = 0,
  "CREATE (p) -[:PETRI_S]-> (ps:PetriNet {type: 'p', model: model.ID}) -[:MODEL_EDGE]-> (t:PetriNet {type: 'tau', model: model.ID, t: 'tau'}) -[:MODEL_EDGE]-> (pe:PetriNet {type: 'p', model: model.ID}) <-[:PETRI_E]- (p)
  RETURN ps, t, pe",
  "MERGE (p) -[:REPRESENTS]-> (cl)
   CREATE (p) -[:PETRI_S]-> (ps:PetriNet {type: 'p', model: model.ID}) -[:MODEL_EDGE]-> (t:PetriNet {type: 't', model: model.ID, t: cl.ID}) -[:MODEL_EDGE]-> (pe:PetriNet {type: 'p', model: model.ID}) <-[:PETRI_E]- (p)
   RETURN ps, t, pe",
  {p:p, model:model}
) YIELD value
WITH model, value.ps AS ps, value.pe AS pe, value.t AS t
CREATE (model) -[:CONTAINS_PN]-> (ps), (model) -[:CONTAINS_PN]-> (t), (model) -[:CONTAINS_PN]-> (pe)
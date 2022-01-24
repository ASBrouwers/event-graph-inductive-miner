MATCH (pt)
WHERE id(pt) = $ptNode
MATCH (pt) <-[:CONTAINS]- (model) -[:CONTAINS_PN]-> (t:PetriNet {t: 'tau'})
SET t.type = 'tau'
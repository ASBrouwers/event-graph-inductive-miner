MATCH (pt)
WHERE id(pt) = $ptNode
MATCH (pt) -[:MODEL_EDGE*0..]-> (par) -[:MODEL_EDGE]-> (chd)
WITH par, collect(chd) AS children
WHERE all(c IN children WHERE c.petri = true) AND par.petri = false
RETURN DISTINCT par, id(par) AS id
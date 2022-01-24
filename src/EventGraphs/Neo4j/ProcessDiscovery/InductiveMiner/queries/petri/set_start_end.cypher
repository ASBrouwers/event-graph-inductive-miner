MATCH (pt)
WHERE id(pt) = $ptNode
MATCH (pt) -[:PETRI_S]-> (p_s)
SET p_s.isStart = true
SET p_s.type = 's_e'
WITH pt
MATCH (pt) -[:PETRI_E]-> (p_e)
SET p_e.isEnd = true
SET p_e.type = 's_e'
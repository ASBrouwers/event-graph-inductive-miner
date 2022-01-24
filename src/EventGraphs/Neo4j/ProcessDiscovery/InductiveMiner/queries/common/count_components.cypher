MATCH (st)
WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (c:Component)
RETURN count(c) AS count
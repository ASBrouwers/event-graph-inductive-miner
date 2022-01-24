MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:Component)
SET comp.main = false
MATCH (st)
WHERE id(st) = $stepId
MATCH (pt:Model_node) -[:TRIED_STEP]-> (st)
MATCH (st) -[:STEP_NODE]-> (comp:Component)
WHERE NOT (pt) -[:REPRESENTS {isStart: true}]-> (:Class {Type: $classType}) <-[:COMP_CL]- (comp)
RETURN count(comp) AS violations
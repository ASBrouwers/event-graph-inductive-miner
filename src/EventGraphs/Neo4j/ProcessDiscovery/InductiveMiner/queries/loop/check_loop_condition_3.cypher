MATCH (st)
WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (main:Component {main: true})
MATCH (pt:Model_node) -[:TRIED_STEP]-> (st)
WITH pt, st, main
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (e:Class {Type: $classType}) <-[r:DF_C {EntityType: $entityType}]- (cl:Class {Type: $classType}) <-[:COMP_CL]- (comp:Component {main: false})
RETURN CASE WHEN count(r) > 0 THEN 1 ELSE 0 END AS violated
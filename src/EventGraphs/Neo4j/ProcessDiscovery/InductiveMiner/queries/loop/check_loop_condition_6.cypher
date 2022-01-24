MATCH (st)
WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (main:Component {main: true})
MATCH (pt:Model_node) -[:TRIED_STEP]-> (st)
WITH pt, st, main
MATCH (st) -[:STEP_NODE]-> (comp:Component {main: false}) -[:COMP_CL]-> (cl1:Class {Type: $classType}) <-[:DF_C {EntityType: $entityType}]- (cl2:Class {Type: $classType}) <-[:COMP_CL]- (main)
WHERE (pt) -[:REPRESENTS {isEnd: true}]-> (cl2)
OPTIONAL MATCH (main) -[:COMP_CL]-> (cl3:Class {Type: $classType}) <-[:REPRESENTS {isEnd: true}]- (pt)
WHERE NOT (cl1) <-[:DF_C {EntityType: $entityType}]- (cl3)
RETURN CASE WHEN count(cl3) > 0 THEN 1 ELSE 0 END AS violated


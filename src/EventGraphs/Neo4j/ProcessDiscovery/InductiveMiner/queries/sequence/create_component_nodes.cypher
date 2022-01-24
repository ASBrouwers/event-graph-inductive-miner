MATCH (c_p:PT_node) WHERE id(c_p) = $ptId
MATCH (a:Algorithm) -[:PRODUCES]-> (m:Model) -[:CONTAINS]-> (c_p)
CREATE (c_p) -[:TRIED_STEP]-> (st:Step {type: 'Sequence', filtered: $filtered}) <-[:ALGORITHM_NODE]- (a)
WITH c_p, st, a
MATCH (c_p) -[:REPRESENTS]-> (cl:Class {Type: $classType})
CREATE (st) -[:STEP_NODE]-> (sq:SEQ_COMP:Component {classes: cl.ID, nodeType: 'Component'}) <-[:ALGORITHM_NODE]- (a)
MERGE (sq) -[:COMP_CL]-> (cl)
WITH sq, cl, c_p, st
OPTIONAL MATCH (cl) -[:DF_C {EntityType: $entityType}]-> (cl2:Class {Type: $classType}) <-[:COMP_CL]- (sq_n:SEQ_COMP) <-[STEP_NODE]- (st)
WHERE NOT sq = sq_n
MERGE (sq) -[:DF_SEQ]-> (sq_n)
WITH count(*) AS ignore, st
RETURN 1, id(st) AS stepId
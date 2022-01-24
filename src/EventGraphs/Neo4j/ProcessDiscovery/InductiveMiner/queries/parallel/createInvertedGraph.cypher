MATCH (pt)
WHERE id(pt) = $ptId
MATCH (a:Algorithm) -[:PRODUCES]-> (:Model) -[:CONTAINS]-> (pt)
CREATE (pt) -[:TRIED_STEP]-> (st:Step {type: 'Parallel', filtered: $filtered}) <-[:ALGORITHM_NODE]- (a)
WITH pt, st, a
MATCH (pt) -[:REPRESENTS]-> (cl:Class {Type: $classType})
MERGE (a) -[:ALGORITHM_NODE]-> (cl_i:InvClass {ID: cl.ID, activity: cl.Type, nodeType: 'InvClass'}) -[:CLI_CL]-> (cl)
MERGE (pt) -[:PT_CLI]-> (cl_i)
MERGE (st) -[:STEP_NODE]-> (cl_i)
WITH cl, cl_i, pt, st
OPTIONAL MATCH (pt) -[:REPRESENTS]-> (cl2:Class {Type: $classType})
WHERE NOT (cl) -[:DF_C {EntityType: $entityType}]-> (cl2)
WITH cl, cl2, cl_i, pt, st, count(pt) AS count
OPTIONAL MATCH (cl2_i:InvClass) -[:CLI_CL]-> (cl2)
MERGE (cl_i) -[:DF_C_I]-> (cl2_i)
MERGE (cl2_i) <-[:STEP_NODE]- (st)
RETURN count(*) AS ignore, id(st) AS stepId

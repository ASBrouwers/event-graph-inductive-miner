MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
WHERE (cl) -[:DF_C {EntityType: $entityType}]-> (cl) // self loop
SET pt.type = "Loop", pt.search = false, pt.ClassName = null
WITH pt, cl
MATCH (model:Model) -[:CONTAINS]-> (pt)
MATCH (alg) -[:PRODUCES]-> (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_class:PT_node:Model_node {ClassName: cl.ID, search: false, main: true}) <-[:CONTAINS]- (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_tau:PT_node:Model_node {ClassName: "tau", search: false, main: false}) <-[:CONTAINS]- (model)
MERGE (pt_class) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
MERGE (pt_tau) -[repr2:REPRESENTS]-> (tau:Class:Tau {ID: "tau"}) <-[:ALGORITHM_NODE]- (alg)
ON CREATE SET repr2.isStart = false, repr2.isEnd = false
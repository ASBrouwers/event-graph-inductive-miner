MATCH (pt) WHERE id(pt) = $ptId
WITH pt
OPTIONAL MATCH (pt) -[:REPRESENTS]-> (cl:Class)
CALL apoc.do.case([cl IS NULL,
"SET pt.search = false, pt.ClassName = 'tau'
WITH pt, cl
MATCH (alg:Algorithm_node) -[:PRODUCES]-> (model:Model) -[:CONTAINS]-> (pt)
MERGE (pt) -[repr:REPRESENTS]-> (tau:Class:Tau {ID: 'tau'}) <-[:ALGORITHM_NODE]- (alg)
ON CREATE SET repr.isStart = false, repr.isEnd = false",
  exists((cl) -[:DF_C]-> (cl)),
"SET pt.type = 'Loop', pt.search = false, pt.ClassName = null
WITH pt, cl
MATCH (model:Model) -[:CONTAINS]-> (pt)
MATCH (alg) -[:PRODUCES]-> (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_class:PT_node:Model_node {ClassName: cl.ID, search: false, main: true}) <-[:CONTAINS]- (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_tau:PT_node:Model_node {ClassName: 'tau', search: false, main: false}) <-[:CONTAINS]- (model)
MERGE (pt_class) -[:REPRESENTS {isStart: true, isEnd: true}]-> (cl)
MERGE (pt_tau) -[:REPRESENTS {isStart: true, isEnd: true}]-> (tau:Class:Tau {ID: 'tau'}) <-[:ALGORITHM_NODE]- (alg)
RETURN 1"],
"SET pt.search = false, pt.ClassName = cl.ID RETURN 1",
{pt:pt, cl:cl}) YIELD value
WITH count(*) AS ignore
RETURN true AS quit LIMIT 1
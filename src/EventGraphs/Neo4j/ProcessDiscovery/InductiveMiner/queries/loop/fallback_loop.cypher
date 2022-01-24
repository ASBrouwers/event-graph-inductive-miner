MATCH (pt)
WHERE id(pt) = $ptId
SET pt.type = 'Loop', pt.search = false
CREATE (pt) -[:MODEL_EDGE]-> (pt_tau:Model_node:PT_node {search: false, main:true, nodeType: 'PT_node', ClassName: 'tau'}) -[:REPRESENTS {isStart: false, isEnd: false}]-> (tau:Class:Tau {ID: 'tau'})
WITH pt, tau, pt_tau
MATCH (model) -[:CONTAINS]-> (pt)
CREATE (model) -[:CONTAINS]-> (tau)
MERGE (model) -[:CONTAINS]-> (pt_tau)
WITH pt, model
MATCH (pt) -[:REPRESENTS]-> (cl:Class {Type: $classType})
CREATE (pt) -[:MODEL_EDGE]-> (pt_c:Model_node:PT_node {search: false, main:false, nodeType: 'PT_node', ClassName: cl.ID}) -[:REPRESENTS {isStart: false, isEnd: false}]-> (cl)
MERGE (model) -[:CONTAINS]-> (pt_c)
RETURN 1

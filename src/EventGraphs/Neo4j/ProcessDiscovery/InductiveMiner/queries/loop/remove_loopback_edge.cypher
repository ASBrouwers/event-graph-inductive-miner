MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (ed_cl) -[loopback:DF_C]-> (st_cl) <-[:REPRESENTS {isStart: true}]- (pt)
DELETE loopback
WITH pt
MERGE (pt) -[:MODEL_EDGE]-> (pt_tau:PT_node:Model_node {search: false, main: false, ClassName: 'tau'}) -[repr:REPRESENTS]-> (cl:Class:Tau {ID:  'tau'})
ON CREATE SET repr.isStart = false, repr.isEnd = false
WITH *
MATCH (m) -[:CONTAINS]-> (pt)
MERGE (m) -[:CONTAINS]-> (pt_tau)

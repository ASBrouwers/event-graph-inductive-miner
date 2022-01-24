MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:MODEL_EDGE]-> (ch1:PT_node) -[:PT_SEQ]-> (ch2:PT_node) <-[:MODEL_EDGE]- (pt)
MATCH (ch1) -[repEnd:REPRESENTS]-> (cl1) -[:DF_C {EntityType: $entityType}]-> (cl2) <-[repSt:REPRESENTS]- (ch2)
SET repEnd.isEnd = true, repSt.isStart = true
WITH pt
MATCH (pt) -[:MODEL_EDGE]-> (ch:PT_node) -[rep:REPRESENTS]-> (tau:Tau)
SET rep.isStart = true, rep.isEnd = true
RETURN count(*) AS count
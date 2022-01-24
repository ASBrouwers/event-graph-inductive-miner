MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:MODEL_EDGE]-> (ch1:PT_node), (pt) -[:MODEL_EDGE]-> (ch2:PT_node)
WHERE NOT () -[:PT_SEQ]-> (ch1) AND NOT (ch2) -[:PT_SEQ]-> ()
MATCH (pt) -[:REPRESENTS {isStart: true}]-> (s:Class {Type: $classType}) <-[rep:REPRESENTS]- (ch1)
SET rep.isStart = true
WITH pt, ch2
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (e:Class {Type: $classType}) <-[rep2:REPRESENTS]- (ch2)
SET rep2.isEnd = true

MATCH (st) WHERE id(st) = $stepId
MATCH (pt) -[:TRIED_STEP]-> (st)
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (cl) -[df:DF_C]-> (cl2) <-[:REPRESENTS {isStart: true}]- (pt)
RETURN count(df) AS loopback_count, collect(df) AS loopback_edges
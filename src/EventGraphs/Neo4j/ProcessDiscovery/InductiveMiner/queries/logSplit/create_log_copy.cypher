MATCH (pt) WHERE id(pt) = $ptId
MATCH (alg) -[:ALGORITHM_NODE]-> (l) -[:PT_L]- (pt)
MATCH (pt) -[:PT_L]-> (l)
MATCH (l) -[:HAS]-> (e:Event)
MERGE (e) -[:EVENT_COPY]-> (ec:Event {Activity: e.Activity})
MERGE (ec) <-[:ALGORITHM_NODE]- (alg)
MERGE (l) -[:LOG_COPY]-> (ec)
WITH l, e, ec
MATCH (e) -[r:DF]-> (e2:Event)
  WHERE id(l) IN r.logs
MATCH (e2) -[:EVENT_COPY]-> (e2c)
WITH DISTINCT ec, e2c
CREATE (ec) -[:DF_W]-> (e2c)
RETURN count(*)
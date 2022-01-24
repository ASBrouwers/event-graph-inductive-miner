MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl)
MATCH (cl) -[dfc:DF_C]-> (cl_target:Class) <-[:REPRESENTS]- (pt)
WITH cl, max(dfc.count) AS maxFreq, collect(dfc) AS rels
UNWIND rels AS dfc
WITH cl, dfc, maxFreq
  WHERE dfc.count < $ratio * maxFreq
DELETE dfc
RETURN count(*) AS count
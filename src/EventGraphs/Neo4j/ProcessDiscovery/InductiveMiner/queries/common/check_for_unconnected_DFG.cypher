MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:PT_L]-> (lt) <-[:L_LT]- (log) -[:HAS]-> (e:Event) -[:OBSERVES]-> (cl:Class)
  WHERE NOT () -[:REPRESENTS]-> (cl)
RETURN count(DISTINCT cl) AS count
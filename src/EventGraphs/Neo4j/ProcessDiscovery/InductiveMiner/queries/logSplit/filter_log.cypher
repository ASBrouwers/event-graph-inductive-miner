MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
MATCH (cl) -[df:DF_C]-> (cl2:Class)
WITH pt, min(df.count) AS minFreq
MATCH (pt) -[:PT_L]-> (log) -[:HAS]-> (e) -[:CORR]-> (entity) <-[:CORR]- (g)
WHERE (e) -[:DF]- (g)
MATCH (e) -[:OBSERVES]-> (cl_e) -[df:DF_C]-> (cl_f) <-[:OBSERVES]- (g)
WITH pt, log, minFreq, entity, min(df.count) AS df_min
  WHERE df_min = minFreq
WITH DISTINCT pt, log, entity, minFreq
MATCH (entity) <-[f:CORR]- (event:Event) <-[h:HAS]- (log)
DETACH DELETE entity, f, h
WITH pt, minFreq
//MATCH (pt) -[:REPRESENTS]-> (cl:Class) -[df:DF_C {count: minFreq}]-> (cl2)
//DELETE df
RETURN minFreq
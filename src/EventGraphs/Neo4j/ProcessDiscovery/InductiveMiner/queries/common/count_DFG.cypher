MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class {Type: $classType}) -[dfc:DF_C {EntityType: $entityType}]-> (cl2:Class {Type: $classType})
MATCH (cl) <-[:OBSERVES]- (e1) -[df:DF {EntityType: $entityType}]-> (e2) -[:OBSERVES]-> (cl2)
WITH dfc, count(df) AS c
SET dfc.count = c
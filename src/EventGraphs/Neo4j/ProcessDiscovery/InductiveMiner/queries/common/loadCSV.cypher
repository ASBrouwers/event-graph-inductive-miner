MATCH (ex_l:Log {ID: $csvname}) -[:HAS]-> (e)
DETACH DELETE ex_l, e
WITH count(*) AS ignore
CREATE (l:Log {ID: $csvname})
WITH l
LOAD CSV WITH HEADERS FROM "file:///C:/" + $csvname + ".csv" AS row FIELDTERMINATOR ','
WITH toInteger(row.EventID) as EventID, row.Activity AS Activity, toInteger(row.TraceID) AS TraceID, toInteger(row.Time) AS Time, l
CREATE (l) -[:HAS]-> (e:Event {ID: EventID, time: Time, Activity: Activity})
//MERGE (a:Class {ID: Activity, Type: $classType})
//CREATE (e) -[:OBSERVES]-> (a)
WITH e, TraceID
MERGE (t:Entity {uID: TraceID, EntityType: 'TraceID'})
CREATE (e) -[:CORR]-> (t)
WITH t, e AS nodes ORDER BY e.time, id(e)
WITH t, collect(nodes) AS nodeList
UNWIND range(0, size(nodeList)-2) AS i
WITH t, nodeList[i] AS source, nodeList[i+1] as target, nodeList
CREATE (source) -[:DF {EntityType: 'TraceID'}]-> (target)
//WITH source, target
//MATCH (s_c:Class {Type: $classType}) <-[:OBSERVES]- (source), (target) -[:OBSERVES]-> (t_c:Class {Type: $classType})
//MERGE (s_c) -[r:DF_C {EntityType: 'TraceId'}]-> (t_c)
RETURN $csvname AS logName
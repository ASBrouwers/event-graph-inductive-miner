MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:PT_L]-> (l:LogTree)
MATCH (pt) <-[:CONTAINS]- (m:Model)
MATCH (l) -[:HAS]-> (e:Event {isStart: false, isEnd: false})
MERGE (pt) -[repr:REPRESENTS]-> (cl:Class {ID: e[$classType], Type: $classType})
ON CREATE SET repr.isStart = false, repr.isEnd = false
MERGE (e) -[:OBSERVES]-> (cl)
MERGE (m) -[:CONTAINS]-> (cl)
WITH DISTINCT pt, l, cl, e
MATCH (e) -[df:DF]-> (e2:Event) <-[:HAS]- (l)
  WHERE id(l) IN df.logs
MATCH (e2) -[:OBSERVES]-> (c2:Class)
WHERE (pt) -[:REPRESENTS]-> (c2)
WITH df.EntityType AS EType, cl, COUNT(df) AS df_freq, c2, pt, l
MERGE (cl)-[rel2:DF_C{EntityType:EType}]->(c2) ON CREATE SET rel2.count=df_freq
WITH DISTINCT pt, l
MATCH (pt) -[rep:REPRESENTS]-> (cl)
OPTIONAL MATCH (start:Event {isStart: true}) -[r:DF]-> (s:Event) -[:OBSERVES]-> (cl)
  WHERE id(l) IN r.logs AND (l) -[:L_START]-> (start)
CALL apoc.do.when(start IS NOT NULL,
"SET rep.isStart = true RETURN cl",
"RETURN 1",
{pt:pt, cl:cl, s:s, rep:rep}) YIELD value AS val1
WITH pt, l, cl, rep
OPTIONAL MATCH (end:Event {isEnd: true}) <-[r:DF]- (e:Event) -[:OBSERVES]-> (cl)
  WHERE id(l) IN r.logs AND (l) -[:L_END]-> (end)
CALL apoc.do.when(end IS NOT NULL,
"SET rep.isEnd = true RETURN cl",
"RETURN 1",
{pt:pt, cl:cl, e:e, rep:rep}) YIELD value AS val2
WITH DISTINCT pt, l
MATCH (l) -[:HAS]-> (s:Event {isStart: true}) -[r:DF]-> (e:Event {isEnd: true})
WHERE id(l) IN r.logs
CREATE (pt) -[:REPRESENTS]-> (tau:Class:Tau {ID: "tau", Type: $classType})
RETURN 1

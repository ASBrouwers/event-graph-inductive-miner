MATCH (:Algorithm {ID: 'Inductive Miner', algorithm: 'IM'}) -[:PRODUCES]-> (ex_m:Model)
WITH max(ex_m.i) AS u
UNWIND [u, 0] AS mx
WITH max(mx) AS n
MATCH (l:Log) WHERE l.ID = $logName
WITH n, l LIMIT 1
CREATE (l) <-[:MAPS]- (alg:Algorithm {ID: 'Inductive Miner', algorithm: 'IM', filtering: $filtering, split: $split })
SET alg.freq = CASE WHEN $filtering = true THEN $freq ELSE null END
CREATE (l) -[:L_LT]-> (l2:LogTree:Algorithm_node {ID: l.ID, activityCol: l.activityCol}) <-[:ALGORITHM_NODE]- (alg)
WITH alg, 'IM_' + (n+1) + "_" + $logName AS modelName,  l, n, l2
MATCH (l) -[:HAS]-> (e:Event)
SET e.isStart = false, e.isEnd = false
CREATE (l2) -[:HAS]-> (e)
WITH *
CALL apoc.do.when(size([(l) -[:HAS]-> (s:Event) -[r:DF]-> (e) WHERE NOT e.isStart | s]) = 0,
"MERGE (l2) -[:L_START]-> (st:Event {Activity: 'start', isStart: true}) -[:DF {logs: [id(l2)]}]-> (e) MERGE (alg) -[:ALGORITHM_NODE]-> (st) RETURN 1", "RETURN 0", {l:l, e:e, l2:l2, alg:alg}) YIELD value AS v1
CALL apoc.do.when(size([(l) -[:HAS]-> (s:Event) <-[r:DF]- (e) WHERE NOT e.isEnd | s]) = 0,
"MERGE (l2) -[:L_END]->(ed:Event {Activity: 'end', isEnd: true}) <-[:DF {logs: [id(l2)]}]- (e) MERGE (alg) -[:ALGORITHM_NODE]-> (ed) RETURN 1", "RETURN 0", {l:l, e:e, l2:l2, alg:alg}) YIELD value AS v2
WITH *
MATCH (e) -[:CORR]-> (ent:Entity)
WITH alg, modelName, l, n, l2, e
OPTIONAL MATCH (e) -[df:DF]- ()
SET df.logs = apoc.coll.toSet(CASE WHEN df.logs IS NULL THEN [id(l2)] ELSE df.logs + [id(l2)] END)
WITH DISTINCT alg, modelName,  l, n, l2
CREATE (alg) -[:PRODUCES]-> (m:Model {ID: modelName, Algorithm: "Inductive Miner", i: (n+1)})
CREATE (pt:Model_node:PT_node{search: true, nodeType: 'PT_node'})
CREATE (m) -[:CONTAINS {root: true}]-> (pt)
CREATE (pt) -[:PT_L]-> (l2)
WITH pt, m, modelName, id(m) AS modelId, l2
RETURN DISTINCT id(pt) AS root, modelName, modelId
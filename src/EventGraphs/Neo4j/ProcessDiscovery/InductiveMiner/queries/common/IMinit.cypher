MATCH (:Algorithm {ID: 'Inductive Miner', algorithm: 'IM'}) -[:PRODUCES]-> (ex_m:Model)
WITH max(ex_m.i) AS u
UNWIND [u, 0] AS mx
WITH max(mx) AS n
MATCH (l:Log) WHERE l.ID = $logName
WITH n, l LIMIT 1
CREATE (l) <-[:MAPS]- (alg:Algorithm {ID: 'Inductive Miner', algorithm: 'IM'})
WITH alg, 'IM_' + (n+1) + "_" + $logName AS modelName,  l, n
CREATE (alg) -[:PRODUCES]-> (m:Model {ID: modelName, Algorithm: "Inductive Miner", i: (n+1)})
CREATE (c:Model_node:PT_node{search: true, nodeType: 'PT_node'})
MERGE (m) -[:CONTAINS {root: true}]-> (c)
WITH c, m, modelName, id(m) AS modelId, l
MATCH (l) -[:HAS]-> () -[:OBSERVES]-> (a:Class {Type: $classType})
//WHERE (a) -[:DF_C {EntityType: $entityType}]- ()
SET a.nodeType = 'Class'
MERGE (c) -[repr:REPRESENTS]-> (a)
ON CREATE SET repr.isStart = false, repr.isEnd = false
WITH DISTINCT c, modelName, modelId
MATCH (e1:Event)
  WHERE NOT () -[:DF {EntityType: $entityType}]-> (e1)
MATCH (e1) -[:OBSERVES]-> (cl1:Class {Type: $classType})
  WHERE (cl1) -[:DF_C {EntityType: $entityType}]- ()
MATCH (c) -[r:REPRESENTS]-> (cl1)
SET r.isStart = true
WITH DISTINCT c, modelName, modelId
MATCH (e2:Event)
  WHERE NOT (e2) -[:DF {EntityType: $entityType}]-> ()
MATCH (e2) -[:OBSERVES]-> (cl2:Class {Type: $classType})
  WHERE (cl2) -[:DF_C {EntityType: $entityType}]- ()
MATCH (c) -[r2:REPRESENTS]-> (cl2)
SET r2.isEnd = true
RETURN DISTINCT id(c) AS root, modelName, modelId
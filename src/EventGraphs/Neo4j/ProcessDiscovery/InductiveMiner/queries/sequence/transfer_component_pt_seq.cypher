MATCH (pt) WHERE id(pt) = $ptId
SET pt.type = "Sequence"
SET pt.search = false
WITH pt
MATCH (pt) -[:TRIED_STEP]-> () -[:STEP_NODE]-> (seq:SEQ_COMP)
MATCH (m:Model) -[:CONTAINS]-> (pt)
WITH DISTINCT pt, m, seq AS component
CREATE (pt_c:Model_node:PT_node {search: true, nodeType: 'PT_node'})
MERGE (pt) -[:MODEL_EDGE]-> (pt_c)
MERGE (component) -[:COMP_PTNODE]-> (pt_c)
MERGE (m) -[:CONTAINS]-> (pt_c)
WITH component, pt
MATCH (component) -[r:DF_SEQ]-> (com2:SEQ_COMP)
MATCH (component) -[:COMP_PTNODE]-> (c1:PT_node)
MATCH (com2) -[:COMP_PTNODE]-> (c2:PT_node)
MERGE (c1) -[:PT_SEQ]-> (c2)
WITH pt
MATCH (pt) -[:TRIED_STEP]-> (step:Step {type: "Sequence", filtered: $filtered})
MATCH (step) -[:STEP_NODE]-> (comp:Component)
MATCH (comp) -[:COMP_PTNODE]-> (ptn:PT_node)
MATCH (comp) -[:COMP_CL]-> (cl:Class)
WITH DISTINCT ptn, cl
MERGE (ptn) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
WITH ptn, count(cl) AS count, collect(cl.ID) AS classNames
SET ptn.search = count > 1
SET ptn.ClassName = CASE WHEN count = 1 THEN classNames[0] ELSE null END
WITH ptn AS pt, count
WHERE count = 1
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
  WHERE (cl) -[:DF_C {EntityType: $entityType}]-> (cl) // self loop
SET pt.type = "Loop", pt.search = false, pt.ClassName = null
WITH pt, cl
MATCH (model:Model) -[:CONTAINS]-> (pt)
MATCH (alg) -[:PRODUCES]-> (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_class:PT_node:Model_node {ClassName: cl.ID, search: false, main: true}) <-[:CONTAINS]- (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_tau:PT_node:Model_node {ClassName: "tau", search: false, main: false}) <-[:CONTAINS]- (model)
MERGE (pt_class) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
MERGE (pt_tau) -[repr2:REPRESENTS]-> (tau:Class:Tau {ID: "tau"}) <-[:ALGORITHM_NODE]- (alg)
ON CREATE SET repr2.isStart = false, repr2.isEnd = false
WITH count(*) AS ignore
RETURN 1 AS result
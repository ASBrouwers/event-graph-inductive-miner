MATCH (step) WHERE id(step) = $stepId
MATCH (step) -[:STEP_NODE]-> (comp:Component)
WITH collect(comp) AS components, step
CALL apoc.do.when(
size(components) > 0,
'MATCH (pt:PT_node) -[:TRIED_STEP]-> (step)
SET pt.type = step.type, pt.search = false
WITH pt, step, components, classType
UNWIND components AS component
CREATE (pt) -[:MODEL_EDGE]-> (pt_c:Model_node:PT_node {search: true, nodeType: "PT_node", main: component.main}) <-[r:COMP_PTNODE]- (component)
WITH pt, pt_c, component, classType
MATCH (m:Model) -[:CONTAINS]-> (pt)
MATCH (a:Algorithm) -[:PRODUCES]-> (m)
MERGE (m) -[:CONTAINS]-> (pt_c)
WITH pt_c, component, classType, a
MATCH (component) -[:COMP_CL]-> (cl:Class {Type: classType})
MERGE (pt_c) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
WITH pt_c, count(cl) AS count, collect(cl.ID) AS classNames, a
SET pt_c.search = count > 1
SET pt_c.ClassName = CASE WHEN count = 1 THEN classNames[0] ELSE null END
WITH pt_c AS pt, count, a
WHERE count = 1
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
WHERE (cl) -[:DF_C {EntityType: $entityType}]-> (cl) // self loop
SET pt.type = "Loop", pt.search = false, pt.ClassName = null
WITH pt, cl, a
MATCH (model:Model) -[:CONTAINS]-> (pt)
MERGE (pt) -[:MODEL_EDGE]-> (pt_class:PT_node:Model_node {ClassName: cl.ID, search: false, main: true}) <-[:CONTAINS]- (model)
MERGE (pt) -[:MODEL_EDGE]-> (pt_tau:PT_node:Model_node {ClassName: "tau", search: false, main: false}) <-[:CONTAINS]- (model)
MERGE (pt_class) -[repr:REPRESENTS]-> (cl)
ON CREATE SET repr.isStart = false, repr.isEnd = false
MERGE (pt_tau) -[repr2:REPRESENTS]-> (tau:Class:Tau {ID: "tau"}) <-[:ALGORITHM_NODE]- (a)
ON CREATE SET repr2.isStart = false, repr.isEnd = false
RETURN 1 AS result
',
'RETURN 0 AS result',
{components:components, step:step, classType:$classType, entityType:$entityType}
) YIELD value
RETURN value['result'] AS result
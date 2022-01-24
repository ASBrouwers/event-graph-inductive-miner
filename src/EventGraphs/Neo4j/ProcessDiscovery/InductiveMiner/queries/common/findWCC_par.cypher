MATCH (c_p) WHERE id(c_p) = $ptId
MATCH (a) -[:PRODUCES]-> (m) -[:CONTAINS]-> (c_p)
MATCH (st) WHERE id(st) = $stepId
WITH c_p, st, a
CALL gds.wcc.stats($graphName)
YIELD componentCount AS nrComponents
WITH c_p, nrComponents, st, a
CALL gds.wcc.stream($graphName)
YIELD nodeId, componentId
WITH DISTINCT collect(nodeId) AS nodeIds, componentId, st, nrComponents, a
MERGE (st) -[:STEP_NODE]-> (wcc:Component {nodeType: 'Component', id: componentId}) <-[:ALGORITHM_NODE]- (a)
WITH nodeIds, componentId, wcc, nrComponents, st
UNWIND nodeIds AS nodeId
MATCH (n) WHERE id(n) = nodeId
MERGE (wcc) -[:COMP_CL]-> (n)
RETURN CASE WHEN nrComponents > 1 THEN 1 ELSE 0 END AS result, id(st) AS stepId

MATCH (st) WHERE id(st) = $stepId
MATCH (pt) -[:TRIED_STEP]-> (st)
MATCH (pt) -[:REPRESENTS {isStart: true}]-> (s_cl) <-[:COMP_CL]- (s_comp) <-[:STEP_NODE]- (st) // Since we removed start nodes from comp, check their DF_C nodes
WITH collect(DISTINCT s_comp) AS merge
CALL apoc.refactor.mergeNodes(merge, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
SET node.main = true
RETURN $stepId AS result, node
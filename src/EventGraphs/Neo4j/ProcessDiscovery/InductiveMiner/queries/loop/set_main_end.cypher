MATCH (st) WHERE id(st) = $stepId
MATCH (pt) -[:TRIED_STEP]-> (st)
MATCH (pt) -[:REPRESENTS {isEnd: true}]-> (s_cl) <-[:COMP_CL]- (s_comp) <-[:STEP_NODE]- (st)
WITH collect(DISTINCT s_comp) AS s_comps
WITH s_comps AS merge
CALL apoc.refactor.mergeNodes(merge, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
SET node.main = true
RETURN $stepId AS result, node
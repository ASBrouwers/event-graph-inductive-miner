MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq1:SEQ_COMP)
MATCH p=(seq1) -[:DF_SEQ*]- (seq1)
WITH nodes(p) AS nodes
UNWIND nodes AS unw
WITH DISTINCT nodes, unw ORDER BY nodes ,unw
WITH nodes, collect(unw) AS sorted
WITH DISTINCT sorted
CALL apoc.refactor.mergeNodes(sorted, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
RETURN *
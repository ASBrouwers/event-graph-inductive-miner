CALL gds.alpha.scc.write("SeqGraph") YIELD createMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize
WITH count(*) AS ignore
MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq1:SEQ_COMP)
WITH seq1.componentId AS component, collect(seq1) AS ids
CALL apoc.refactor.mergeNodes(ids, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
RETURN node
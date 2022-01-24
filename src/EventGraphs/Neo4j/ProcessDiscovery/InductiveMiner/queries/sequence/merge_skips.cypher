MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq1:SEQ_COMP)
MATCH p=(seq1) -[:DF_SEQ*2..]-> (seq2)
MATCH (seq1) -[df_skip:DF_SEQ]-> (seq2)
WITH st, seq1, seq2, nodes(p) AS components, df_skip
UNWIND components AS component
WITH st, component, seq1, seq2, df_skip
WHERE component <> seq1 AND component <> seq2
DELETE df_skip
WITH st, collect(component) AS components, df_skip
MATCH (alg) -[:ALGORITHM_NODE]-> (st)
CALL apoc.refactor.mergeNodes(components, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
CREATE (node) -[:COMP_CL]-> (tau:Class:Tau {ID: "tau", Type: $classType}) <-[:ALGORITHM_NODE]- (alg)
MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq1:SEQ_COMP)
MATCH (st) -[:STEP_NODE]-> (seq2:SEQ_COMP)
  WHERE NOT (seq1) -[:DF_SEQ*]-> (seq2) AND NOT (seq2) -[:DF_SEQ*]-> (seq1) AND seq1 <> seq2
WITH DISTINCT seq1, seq2 ORDER BY id(seq1), id(seq2)
WITH seq1, collect(seq2) AS seq2s
WITH seq2s, [seq1] + seq2s AS seqs
UNWIND seqs AS seq
WITH DISTINCT seq2s, seq ORDER BY seq2s, id(seq)
WITH seq2s, collect(seq) AS toMerge
WITH DISTINCT toMerge
CALL apoc.refactor.mergeNodes(toMerge, {properties: 'combine', mergeRels: true, produceSelfRel: false, preserveExistingSelfRels: false}) YIELD node
WITH count(DISTINCT node) AS nodes
RETURN nodes AS result
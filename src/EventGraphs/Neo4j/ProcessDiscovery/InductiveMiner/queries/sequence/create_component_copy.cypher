MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq1:SEQ_COMP) -[:COMP_CL]-> (cl1:Class {Type: $classType})
MATCH (seq1) <-[:ALGORITHM_NODE]- (alg)
MERGE (seq1) -[:COPY]-> (seq_cp:SEQ_COMP_COPY) <-[:STEP_NODE]- (st)
MERGE (alg) -[:ALGORITHM_NODE]-> (seq_cp)
MERGE (seq_cp) -[:COMP_CL]-> (cl1)
WITH seq1, seq_cp, st
MATCH (cp1) <-[:COPY]- (seq1) -[:DF_SEQ]-> (seq2) -[:COPY]-> (cp2)
MERGE (cp1) -[:DF_SEQ]-> (cp2)
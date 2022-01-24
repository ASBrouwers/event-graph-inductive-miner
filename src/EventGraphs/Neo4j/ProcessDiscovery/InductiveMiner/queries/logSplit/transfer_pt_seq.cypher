MATCH (st) WHERE id(st) = $stepId
MATCH (pt) -[:TRIED_STEP]-> (st)
MATCH (st) -[:STEP_NODE]-> (comp:SEQ_COMP)
MATCH (comp) -[:COMP_CL]-> (cl:Class) <-[:OBSERVES]- () <-[:HAS]- () <-[:PT_L]- (pt1:PT_node) <-[:MODEL_EDGE]- (pt)
MATCH (comp) -[:DF_SEQ]-> (comp2) <-[:STEP_NODE]- (st)
MATCH (comp2) -[:COMP_CL]-> (cl2:Class) <-[:OBSERVES]- () <-[:HAS]- () <-[:PT_L]- (pt2:PT_node) <-[:MODEL_EDGE]- (pt)
  WHERE pt1 <> pt2
MERGE (pt1) -[:PT_SEQ]-> (pt2)
WITH pt1, pt2
MATCH (pt1) -[:PT_L]-> (l1)
MATCH (pt2) -[:PT_L]-> (l2)
MERGE (l1) -[:LT_SEQ]-> (l2)
RETURN 1;


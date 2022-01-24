MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (source_comp) -[:COMP_PTNODE]-> (source_pt)
MATCH (source_comp) -[:COMP_CL]-> (source_class) -[:DF_C {EntityType: $entityType}]-> (target_class) <-[:COMP_CL]- (target_comp) <-[:STEP_NODE]- (st)
MATCH (target_comp) -[:COMP_PTNODE]-> (target_pt)
WHERE source_comp <> target_comp
MATCH (source_pt) -[rep_s:REPRESENTS]-> (source_class)
SET rep_s.isEnd = true
WITH target_pt, target_class
MATCH (target_pt) -[rep_t:REPRESENTS]-> (target_class)
SET rep_t.isStart = true
RETURN *
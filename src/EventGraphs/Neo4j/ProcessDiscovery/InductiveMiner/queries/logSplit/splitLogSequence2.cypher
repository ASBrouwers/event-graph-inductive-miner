MATCH (pt) WHERE id(pt) = $ptId
SET pt.search = false, pt.type = "Sequence"
WITH pt
MATCH (pt) -[:PT_L]-> (l:LogTree:Algorithm_node)
SET l.splitType = pt.type
WITH pt, l
MATCH (st)
  WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (comp:Component)
CREATE (l) -[:LOG_SPLIT]-> (l_c:LogTree:Algorithm_node {activityCol: l.activityCol})
CREATE (pt) -[:MODEL_EDGE]-> (pt_c:PT_node:Model_node {search: true}) -[:PT_L]-> (l_c)
WITH l_c, l, comp
MATCH (comp) -[:COMP_CL]-> () <-[:OBSERVES]- (first:Event)
OPTIONAL MATCH (prevCl) <-[:OBSERVES]- (prev:Event) -[:DF {EntityType: $entityType}]-> (first)
  WHERE prev IS NULL OR NOT (comp) -[:COMP_CL]-> (prevCl)
WITH l, first, comp, l_c
MATCH p=(first) -[r:DF*0.. {EntityType: $entityType}]-> (last)
WITH l, first, comp, l_c, p, last, relationships(p) AS pathRels
WHERE all(pathRel IN pathRels WHERE id(l) IN pathRel.logs)
UNWIND nodes(p) AS pathNode
MATCH (pathNode) -[:OBSERVES]-> () <-[:COMP_CL]- (nodeComp:SEQ_COMP)
WITH collect(nodeComp) AS nodeComps, p, first, comp, last, l_c
  WHERE all(nc IN nodeComps WHERE nc = comp)
WITH DISTINCT p, first, last, comp, l_c
OPTIONAL MATCH (last) -[:DF {EntityType: $entityType}]-> (next:Event) -[:OBSERVES]-> (nextCl)
  WHERE next IS NULL OR NOT (comp) -[:COMP_CL]-> (nextCl)
UNWIND nodes(p) AS pathNode
MERGE (l_c) -[:HAS]-> (pathNode)
WITH DISTINCT l_c, p
UNWIND relationships(p) AS df
SET df.logs = df.logs + [id(l_c)]
RETURN count(*)
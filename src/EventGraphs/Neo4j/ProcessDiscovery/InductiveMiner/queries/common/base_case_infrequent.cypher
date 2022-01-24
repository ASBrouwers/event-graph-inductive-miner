MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:REPRESENTS]-> (cl:Class)
MATCH (pt) -[:PT_L]-> (l) -[:HAS]-> (e:Event) -[:CORR]-> (ent:Entity)
  WHERE (e) -[:OBSERVES]-> (cl)
WITH pt, cl, count(DISTINCT e) AS nrEvents, count(DISTINCT ent) AS nrEntities
WITH pt, cl, toFloat(nrEntities) / (nrEntities + nrEvents) AS p
WITH pt, cl, abs(p - 0.5) AS f
CALL apoc.do.when(f <= $ratio,
"SET pt.ClassName = cl.ID, pt.search = false RETURN true AS quit",
"SET pt.search = true RETURN false AS quit",
{pt:pt, cl:cl}) YIELD value
RETURN value.quit AS quit
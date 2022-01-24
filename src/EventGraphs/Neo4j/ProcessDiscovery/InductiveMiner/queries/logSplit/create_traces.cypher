MATCH (pt) WHERE id(pt) = $ptId
MATCH (pt) -[:PT_L]-> (l)
MATCH p=(e) -[:DF*0..]-> (f)
  WHERE all(n IN nodes(p) WHERE (l) -[:HAS]-> (n)) AND NOT () -[:DF]-> (e) AND NOT (f) -[:DF]-> ()
WITH DISTINCT l, p
CREATE (l) -[:HAS_TRACE]-> (t:Trace)
WITH p, t
UNWIND nodes(p) AS event
MERGE (event) -[:FORMS]-> (t)
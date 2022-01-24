MATCH (m:Model)
WHERE id(m) = $modelId
MATCH (m) -[:CONTAINS]-> () -[:MODEL_EDGE*0..]-> (n:PT_node {search: true}) RETURN id(n) AS id LIMIT 1
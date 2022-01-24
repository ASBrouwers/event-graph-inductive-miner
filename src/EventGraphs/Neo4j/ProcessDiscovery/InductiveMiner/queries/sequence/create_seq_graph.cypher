CALL gds.graph.drop("SeqGraph", false) YIELD graphName AS droppedGraph
WITH count(droppedGraph) AS ignore
MATCH (st) WHERE id(st) = $stepId
CALL gds.graph.create.cypher("SeqGraph",
"MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq:SEQ_COMP) RETURN id(seq) AS id",
"MATCH (st) WHERE id(st) = $stepId
MATCH (st) -[:STEP_NODE]-> (seq:SEQ_COMP) -[:DF_SEQ]-> (seq2:SEQ_COMP) <-[:STEP_NODE]- (st)
RETURN id(seq) AS source, id(seq2) AS target", {parameters: {stepId:$stepId}})
YIELD graphName, nodeCount, relationshipCount, createMillis RETURN *
MATCH (a:Algorithm {ID:'Inductive Miner'})
MATCH (a) -[:ALGORITHM_NODE]-> (n)
DETACH DELETE a, n
MATCH (m:Model{Algorithm: "Inductive Miner"})
OPTIONAL MATCH (m)-[:PRODUCES]-(a)
OPTIONAL MATCH (m)-[:CONTAINS]-(n)
OPTIONAL MATCH (m)-[:CONTAINS_PN]-(o)-[:MODEL_EDGE*]->(p)
DETACH DELETE a,m,n,o,p
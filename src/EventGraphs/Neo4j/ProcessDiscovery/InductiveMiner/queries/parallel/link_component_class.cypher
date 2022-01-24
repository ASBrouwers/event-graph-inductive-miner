MATCH (st:Step)
WHERE id(st) = $stepId
MATCH (comp:Component) -[:COMP_CL]-> (ci:InvClass)
MATCH (ci) -[:CLI_CL]-> (cl:Class {Type: $classType})
MERGE (comp) -[:COMP_CL]-> (cl)
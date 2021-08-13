package EventGraphs.Neo4j.ProcessDiscovery;

import EventGraphs.Utils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.List;
import java.util.Map;

public class HeuristicMiner extends DiscoveryAlgorithm {
    String algorithm = Utils.ALGORITHM_LABEL.get("HM");

    public HeuristicMiner(Driver driver){
        this.driver = driver;
    }

    public void generateProcessModel(Map<String,String> modelParams){
        // Call n4jQueries to generate process model based on parameters included in list
        //0:logName, 1:className, 2:dfType, 3:frequency, 4:dependency, 5:L1L, 6:L2L, 7:Relative-to-best, 8:bindings

        String modelID = createModelNode(modelParams);
        createModelNodes(modelID, modelParams.get("className"));
        createModelEdges(modelParams.get("dfType"), modelID);
        calculateDependency(modelParams.get("dfType"), modelID);
        runFlexibleHeuristicMiner(modelID, modelParams);
        addOutputBindings(modelID,modelParams.get("dfType"),modelParams.get("bind"));
        addInputBindings(modelID,modelParams.get("dfType"),modelParams.get("bind"));

        modelName = modelID;
    }

    public void generatePetriNet(){
        generatePetriNetOutputs();
        generatePetriNetInputs();
        setStartEndNodes();
        optimizePetriNet();
    }

    private String createModelNode(Map<String,String> modelParams){
        List<Record> queryAnswer;
        String getMaxID = "MATCH (m:Model{Algorithm:\""+algorithm+"\"})\n" +
                "WHERE m.ID CONTAINS 'HM_'\n" +
                "RETURN toInteger(split(m.ID,\"_\")[1]) AS idNum\n" +
                "ORDER BY idNum DESC LIMIT 1";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getMaxID);
                return result.list();
            });
        }

        // We define a default name for the model using "HM_" plus the next available number after the max number found in the database
        int idNum = 1;
        if(queryAnswer.size() != 0)
            idNum = queryAnswer.get(0).get("idNum").asInt() + 1;
        String modelID = "HM_"+idNum;

        String createModelNode = "MATCH (l:Log{ID:\""+modelParams.get("logName")+"\"})\n"+
                "MERGE (l)<-[:MAPS]-(:Algorithm{ID:\""+algorithm+"\",Class:\""+modelParams.get("className")+"\",DF:\""+modelParams.get("dfType")+"\",Freq:"+modelParams.get("freq")+
                ",Dep:"+modelParams.get("dep")+",L1L:"+modelParams.get("l1l")+",L2L:"+modelParams.get("l2l")+",Rel:"+modelParams.get("rel")+",Bind:"+modelParams.get("bind")+
                "})-[:PRODUCES]->(:Model{Algorithm:\""+algorithm+"\",Log:\""+modelParams.get("logName")+"\",ID:\""+modelID+"\"})";

        try ( Session session = driver.session() ) {
            session.run(createModelNode);
        }

        return modelID;
    }

    private void createModelNodes(String modelID, String className){
        String createModelNodes = "MATCH (m:Model{ID:\""+modelID+"\"})--(:Algorithm)--(:Log)--(:Event)--(c:Class{Type:\""+className+"\"})\n" +
                                        "MERGE (m)-[:CONTAINS{DG:\"Before\"}]->(dg:DG_node:Model_node{ID:c.ID})\n" +
                                        "MERGE (dg)-[:REPRESENTS]->(c)";

        String createArtificialNodes = "MATCH (m:Model{ID:\""+modelID+"\"})\n" +
                                        "MERGE (m)-[:CONTAINS{DG:\"Before\"}]->(:DG_node:Model_node{ID:\"ARTIFICIAL_START\",isStart:True})\n" +
                                        "MERGE (m)-[:CONTAINS{DG:\"Before\"}]->(:DG_node:Model_node{ID:\"ARTIFICIAL_END\",isEnd:True})";

        try ( Session session = driver.session() ) {
            session.run(createModelNodes);
            session.run(createArtificialNodes);
        }
    }

    private void createModelEdges(String dfType, String modelID){
        String createModelEdges = "MATCH (m:Model{ID:\""+modelID+"\"})-->(dg:DG_node)-->(c:Class)\n" +
                "MATCH (m)-->(dg2:DG_node)-->(c2:Class)\n" +
                "MATCH (c)-[:DF_C{EntityType:\""+dfType+"\"}]->(c2)\n" +
                "MATCH (c)<--(:Event)-[df:DF{EntityType:\""+dfType+"\"}]->(:Event)-->(c2)\n" +
                "WITH DISTINCT dg AS From, dg2 AS To, COUNT(df) AS f\n" +
                "MERGE (From)-[:MODEL_EDGE{Freq:f}]->(To)";

        String createStartEdge = "MATCH (m:Model{ID:\""+modelID+"\"})--(dg:DG_node)--(:Class)--(e:Event)\n" +
                "WHERE NOT EXISTS (()-[:DF{EntityType:\""+dfType+"\"}]->(e))\n" +
                "WITH m, dg AS startActivity, COUNT(e) AS f\n" +
                "MATCH (m)--(st:DG_node{isStart:True})\n" +
                "MERGE (st)-[:MODEL_EDGE{Freq:f}]->(startActivity)";

        String createEndEdge = "MATCH (m:Model{ID:\""+modelID+"\"})--(dg:DG_node)--(:Class)--(e:Event)\n" +
                "WHERE NOT EXISTS ((e)-[:DF{EntityType:\""+dfType+"\"}]->())\n" +
                "WITH m, dg AS endActivity, COUNT(e) AS f\n" +
                "MATCH (m)--(end:DG_node{isEnd:True})\n" +
                "MERGE (endActivity)-[:MODEL_EDGE{Freq:f}]->(end)";

        try ( Session session = driver.session() ) {
            session.run(createModelEdges);
            session.run(createStartEdge);
            session.run(createEndEdge);
        }
    }

    private void calculateDependency(String dfType, String modelID){
        String calculateDependency = "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                                        "OPTIONAL MATCH (b)-[me2:MODEL_EDGE]->(a)\n" +
                                        "WITH DISTINCT a, b, me, me.Freq AS fA, COALESCE(me2.Freq,0) AS fB\n" +
                                        "WITH a,b,ABS(ROUND(((fA-fB)*1.0/(fA+fB+1)),3)) AS dep\n" +
                                        "MATCH (a)-[me:MODEL_EDGE]->(b)\n" +
                                        "SET me.Dep = dep";

        String lengthOneLoopDep = "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]-(a)\n" +
                                    "SET me.L1L = ROUND((me.Freq*1.0)/(me.Freq+1),3)";

        String lengthTwoLoopDep = "MATCH (m:Model{ID:\""+modelID+"\"})--(dg:DG_node)-[:MODEL_EDGE]->(dg2:DG_node)-[:MODEL_EDGE]->(dg)\n" +
                "WITH DISTINCT m, dg, dg2\n" +
                "MATCH (m)--(dg)--(c:Class)\n" +
                "MATCH (m)--(dg2)--(c2:Class)\n" +
                "MATCH (e1:Event)--(c)--(e3:Event)\n" +
                "MATCH (c2)--(e2:Event)\n" +
                "OPTIONAL MATCH (e1)-[df:DF{EntityType:\""+dfType+"\"}]->(e2)-[:DF{EntityType:\""+dfType+"\"}]->(e3)\n" +
                "WITH m,dg,dg2,c,c2,COUNT(df) AS l2lFreqA\n" +
                "MATCH (e4:Event)--(c2)--(e6:Event)\n" +
                "MATCH (c)--(e5:Event)\n" +
                "OPTIONAL MATCH (e4)-[df2:DF{EntityType:\""+dfType+"\"}]->(e5)-[:DF{EntityType:\""+dfType+"\"}]->(e6)\n" +
                "WITH m,dg,dg2,l2lFreqA,COUNT(df2) AS l2lFreqB\n" +
                "WITH m,dg,dg2,ROUND(((l2lFreqA+l2lFreqB)*1.0)/(l2lFreqA+l2lFreqB+1),3) AS l2lDep\n" +
                "MATCH (m)--(dg)-[me:MODEL_EDGE]->(dg2)\n" +
                "SET me.L2L = l2lDep";

        try ( Session session = driver.session() ) {
            session.run(calculateDependency);
            session.run(lengthOneLoopDep);
            session.run(lengthTwoLoopDep);
        }
    }

    private void runFlexibleHeuristicMiner(String modelID, Map<String,String> params){
        String className = params.get("className");
        String dfType = params.get("dfType");
        String frequency = params.get("freq");
        String dependency = params.get("dep");
        String l1ldep = params.get("l1l");
        String l2ldep = params.get("l2l");
        String relBest = params.get("rel");

        String fhmQuery =
                        //Flexible Heuristic Miner Step 2
                "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(a)\n" +
                "WHERE me.L1L >= "+l1ldep+"\n" +
                "WITH COLLECT(DISTINCT [a.ID,a.ID]) AS C1\n" +
                "WITH CASE WHEN C1[0][0] IS NULL THEN [] ELSE C1 END AS C1\n" +
                        //Flexible Heuristic Miner Step 3
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE me.L2L >= "+l2ldep+" AND NOT [a.ID,a.ID] IN C1 AND NOT [b.ID,b.ID] IN C1\n" +
                "WITH C1,COLLECT(DISTINCT [a.ID,b.ID]) AS C2\n" +
                "WITH C1, CASE WHEN C2[0][0] IS NULL THEN [] ELSE C2 END AS C2\n" +
                        //Flexible Heuristic Miner Step 4
                "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE a <> b AND b.isEnd IS NULL\n" +
                "WITH C1,C2, a.ID AS act, [a.ID,b.ID] AS pair, me.Dep AS depVal\n" +
                "WITH C1,C2, act,apoc.agg.maxItems(pair,depVal) AS strFollowers\n" +
                "UNWIND strFollowers.items AS strPairs\n" +
                "WITH C1,C2, COLLECT(strPairs) AS Cout\n" +
                        //Flexible Heuristic Miner Step 5
                "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE a <> b AND a.isStart IS NULL\n" +
                "WITH C1,C2,Cout, b.ID AS act, [a.ID,b.ID] AS pair, me.Dep AS depVal\n" +
                "WITH C1,C2,Cout, act,apoc.agg.maxItems(pair,depVal) AS strCauses\n" +
                "UNWIND strCauses.items AS strPairs\n" +
                "WITH C1,C2,Cout, COLLECT(strPairs) AS Cin\n" +
                        //Flexible Heuristic Miner Step 6/7
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(x:DG_node),\n" +
                "(m)--(b:DG_node)-[me2:MODEL_EDGE]->(y:DG_node)\n" +
                "WHERE [a.ID,x.ID] IN Cout AND me.Dep < "+dependency+" AND [b.ID,y.ID] IN Cout AND [a.ID,b.ID] IN C2 AND (me2.Dep - me.Dep)>"+relBest+"\n" +
                "WITH C1,C2,Cout,Cin,COLLECT(DISTINCT [a.ID,x.ID]) AS Cout1\n" +
                "WITH C1,C2,Cout,Cin,CASE WHEN Cout1[0][0] IS NULL THEN [] ELSE Cout1 END AS Cout1\n" +
                "WITH C1,C2,Cin,apoc.coll.subtract(Cout, Cout1) as Cout\n" +
                        //Flexible Heuristic Miner Step 8/9
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(x:DG_node)-[me:MODEL_EDGE]->(a:DG_node),\n" +
                "(m)--(y:DG_node)-[me2:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE [x.ID,a.ID] IN Cin AND me.Dep < "+dependency+" AND [y.ID,b.ID] IN Cin AND [a.ID,b.ID] IN C2 AND (me2.Dep - me.Dep)>"+relBest+"\n" +
                "WITH C1,C2,Cout,Cin,COLLECT(DISTINCT [x.ID,a.ID]) AS Cin1\n" +
                "WITH C1,C2,Cout,Cin,CASE WHEN Cin1[0][0] IS NULL THEN [] ELSE Cin1 END AS Cin1\n" +
                "WITH C1,C2,Cout,apoc.coll.subtract(Cin, Cin1) as Cin\n" +
                        //Flexible Heuristic Miner Step 10
                "MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE me.Dep >= "+dependency+"\n" +
                "WITH C1,C2,Cin,Cout,COLLECT(DISTINCT [a.ID,b.ID]) AS Cout2_1\n" +
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node),(a)-[me2:MODEL_EDGE]->(c:DG_node)\n" +
                "WHERE [a.ID,c.ID] IN Cout AND ABS(me2.Dep - me.Dep)<"+relBest+"\n" +
                "WITH C1,C2,Cin,Cout2_1,COLLECT(DISTINCT [a.ID,b.ID]) AS Cout2_2\n" +
                "WITH C1,C2,Cin,Cout2_1,CASE WHEN Cout2_2[0][0] IS NULL THEN [] ELSE Cout2_2 END AS Cout2_2\n" +
                "WITH C1,C2,Cin,Cout2_1+Cout2_2 AS Cout2\n" +
                        //Flexible Heuristic Miner Step 11
                "MATCH (m:Model{ID:\""+modelID+"\"})--(b:DG_node)-[me:MODEL_EDGE]->(a:DG_node)\n" +
                "WHERE me.Dep >= "+dependency+" \n" +
                "WITH C1,C2,Cout2,Cin,COLLECT(DISTINCT [b.ID,a.ID]) AS Cin2_1\n" +
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(b:DG_node)-[me:MODEL_EDGE]->(a:DG_node),(c:DG_node)-[me2:MODEL_EDGE]->(a)\n" +
                "WHERE [c.ID,a.ID] IN Cin AND ABS(me2.Dep - me.Dep)<"+relBest+"\n" +
                "WITH C1,C2,Cout2,Cin2_1,COLLECT(DISTINCT [b.ID,a.ID]) AS Cin2_2\n" +
                "WITH C1,C2,Cout2,Cin2_1,CASE WHEN Cin2_2[0][0] IS NULL THEN [] ELSE Cin2_2 END AS Cin2_2\n" +
                "WITH C1,C2,Cout2,Cin2_1+Cin2_2 AS Cin2\n" +
                "WITH C1+C2+Cout2+Cin2 AS dgEdges\n" +
                "UNWIND dgEdges AS dgEdge\n" +
                "WITH COLLECT(DISTINCT dgEdge) AS dgEdges\n" +
                        //Frequency Threshold Edge Removal
                "MATCH (m:Model{ID:\""+modelID+"\"})--(:DG_node)--(:Class)--(:Event)--(n:Entity)\n" +
                "WHERE n.EntityType = \""+dfType+"\"\n" +
                "WITH dgEdges,COUNT(DISTINCT n) AS numEntities\n" +
                "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})--(a:DG_node)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE [a.ID,b.ID] IN dgEdges AND (me.Freq*1.0/numEntities) < "+frequency+"\n" +
                "WITH dgEdges,COLLECT(DISTINCT [a.ID,b.ID]) AS Cfreq\n" +
                "WITH apoc.coll.subtract(dgEdges, Cfreq) as dgEdges\n" +
                "UNWIND dgEdges AS dgEdge\n" +
                "WITH dgEdge\n" +
                        //Filtered Dependency Graph creation
                "MATCH (m:Model{ID:\""+modelID+"\"})\n" +
                "MERGE (m)-[:CONTAINS{DG:\"After\"}]->(a:DG_node:Model_node{ID:dgEdge[0]})\n" +
                "MERGE (m)-[:CONTAINS{DG:\"After\"}]->(b:DG_node:Model_node{ID:dgEdge[1]})\n" +
                "MERGE (a)-[:MODEL_EDGE]->(b)";

        String createStartProperties = "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:\"After\"}]-(a)\n" +
                "WHERE a.ID = \"ARTIFICIAL_START\"\n" +
                "CALL apoc.do.when(\n" +
                "\ta IS NULL,\n" +
                "    \"MATCH (m:Model{ID:'"+modelID+"'}) MERGE (m)-[:CONTAINS{DG:'After'}]->(:DG_node:Model_node{ID:'ARTIFICIAL_START',isStart:true})\",\n" +
                "    \"SET a.isStart = true\",\n" +
                "    {a:a}\n" +
                ")YIELD value RETURN 1";

        String createEndProperties = "OPTIONAL MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:\"After\"}]-(a)\n" +
                "WHERE a.ID = \"ARTIFICIAL_END\"\n" +
                "CALL apoc.do.when(\n" +
                "\ta IS NULL,\n" +
                "    \"MATCH (m:Model{ID:'"+modelID+"'}) MERGE (m)-[:CONTAINS{DG:'After'}]->(:DG_node:Model_node{ID:'ARTIFICIAL_END',isEnd:true})\",\n" +
                "    \"SET a.isEnd = true\",\n" +
                "    {a:a}\n" +
                ")YIELD value RETURN 1";

        try ( Session session = driver.session() ) {
            session.run(fhmQuery);
            session.run(createStartProperties);
            session.run(createEndProperties);
        }

        // Create outgoing edges for those nodes that remain in the dependency graph and have no outgoing edge.
        // The edge with the highest dependency measure is selected. If the connecting node was also removed from the
        // dependency graph, it will be added back and a subsequent check follows to make sure those new nodes have
        // outgoing edges, otherwise, the query is repeated until no more cases remain. This is similar to what is done
        // in ProM with the "Accepted Connected" option in the Heuristic Miner.
        int numNodes = getNumNodesMissingOutput(modelID);
        while(numNodes != 0){
            String createMissingOutgoingEdges = "CALL{\n" +
                    "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)-[me:MODEL_EDGE]->()\n" +
                    "    WITH a,COUNT(me) AS numEdges\n" +
                    "    MATCH (a)-[me:MODEL_EDGE]->(b:DG_node)\n" +
                    "    WHERE a = b AND numEdges = 1 RETURN a\n" +
                    "    UNION\n" +
                    "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)\n" +
                    "\tWHERE NOT (a)-[:MODEL_EDGE]->() AND a.isEnd IS NULL RETURN a\n" +
                    "} WITH a\n" +
                    "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'Before'}]-(b:DG_node)-[me:MODEL_EDGE]->(c:DG_node)\n" +
                    "WHERE b.ID = a.ID AND b <> c\n" +
                    "WITH m,a,c.ID AS cID,me.Dep AS depVal\n" +
                    "WITH m,a,apoc.agg.maxItems(cID,depVal) AS strFollowers\n" +
                    "WITH m,a,strFollowers.items AS items\n" +
                    "UNWIND items AS out\n" +
                    "WITH m,a,out\n" +
                    "OPTIONAL MATCH (m)-[:CONTAINS{DG:'After'}]-(b:DG_node{ID:out})\n" +
                    "CALL apoc.do.when(\n" +
                    "\tb IS NULL,\n" +
                    "    \"MERGE (a)-[:MODEL_EDGE]->(c:DG_node:Model_node{ID:out})\n" +
                    "    MERGE (m)-[:CONTAINS{DG:'After'}]->(c)\",\n" +
                    "    \"MERGE (a)-[:MODEL_EDGE]->(b)\",\n" +
                    "    {m:m,a:a,b:b,out:out}\n" +
                    ") YIELD value RETURN 1";
            try ( Session session = driver.session() ) {
                session.run(createMissingOutgoingEdges);
            }
            numNodes = getNumNodesMissingOutput(modelID);
        }


        // Create ingoing edges for those nodes that remain in the dependency graph and have no ingoing edge.
        // The edge with the highest dependency measure is selected. If the connecting node was also removed from the
        // dependency graph, it will be added back and a subsequent check follows to make sure those new nodes have
        // ingoing edges, otherwise, the query is repeated until no more cases remain. This is similar to what is done
        // in ProM with the "Accepted Connected" option in the Heuristic Miner.
        numNodes = getNumNodesMissingInput(modelID);
        while(numNodes != 0){
            String createMissingIngoingEdges = "CALL{\n" +
                    "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(:DG_node)-[me:MODEL_EDGE]->(a)\n" +
                    "    WITH a,COUNT(me) AS numEdges\n" +
                    "    MATCH (b:DG_node)-[me:MODEL_EDGE]->(a)\n" +
                    "    WHERE a = b AND numEdges = 1 RETURN a\n" +
                    "    UNION\n" +
                    "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)\n" +
                    "    WHERE NOT ()-[:MODEL_EDGE]->(a) AND a.isStart IS NULL RETURN a\n" +
                    "} WITH a\n" +
                    "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:\"Before\"}]-(b:DG_node)-[me:MODEL_EDGE]->(c:DG_node)\n" +
                    "WHERE c.ID = a.ID\n" +
                    "WITH m,a,b.ID AS bID,me.Dep AS depVal\n" +
                    "WITH m,a,apoc.agg.maxItems(bID,depVal) AS strCauses\n" +
                    "WITH m,a,strCauses.items AS items\n" +
                    "UNWIND items AS in\n" +
                    "WITH m,a,in\n" +
                    "OPTIONAL MATCH (m)-[:CONTAINS{DG:\"After\"}]-(b:DG_node{ID:in})\n" +
                    "CALL apoc.do.when(\n" +
                    "\tb IS NULL,\n" +
                    "    \"MERGE (c:DG_node:Model_node{ID:in})-[:MODEL_EDGE]->(a)\n" +
                    "    MERGE (m)-[:CONTAINS{DG:'After'}]->(c)\",\n" +
                    "    \"MERGE (b)-[:MODEL_EDGE]->(a)\",\n" +
                    "    {m:m,a:a,b:b,in:in}\n" +
                    ") YIELD value RETURN 1";
            try ( Session session = driver.session() ) {
                session.run(createMissingIngoingEdges);
            }
            numNodes = getNumNodesMissingInput(modelID);
        }

        String createM_CEdges = "MATCH (m:Model{ID:\""+modelID+"\"})--(:Algorithm)--(:Log)--(:Event)--(c:Class{Type:\""+className+"\"})\n" +
                "MATCH (m)-[:CONTAINS{DG:\"After\"}]->(dg:DG_node{ID:c.ID})\n" +
                "MERGE (dg)-[:REPRESENTS]->(c)";

        // Before deleting the initial DG, we copy the edge properties to the edges of the new DG
        // This helps us keep the information related to the properties such as the Freq or the Dep measures
        String copyEdgeProperties = "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:\"Before\"}]->(b1)-[meB:MODEL_EDGE]->(b2)\n" +
                                    "MATCH (m)-[:CONTAINS{DG:\"After\"}]->(a1)-[meA:MODEL_EDGE]->(a2)\n" +
                                    "WHERE b1.ID = a1.ID and b2.ID = a2.ID\n" +
                                    "SET meA = meB";

        String deleteInitialDG = "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:\"Before\"}]->(n)\n" +
                                    "DETACH DELETE n";

        try ( Session session = driver.session() ) {
            session.run(createM_CEdges);
            session.run(copyEdgeProperties);
            session.run(deleteInitialDG);
        }
    }

    private int getNumNodesMissingOutput(String modelID){
        // Check for nodes without any outgoing edge. Nodes whose only output is themselves are also included
        String numNodesWithMissingOutputs = "CALL{\n" +
                "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)-[me:MODEL_EDGE]->()\n" +
                "    WITH a,COUNT(me) AS numEdges\n" +
                "    MATCH (a:DG_node)-[me:MODEL_EDGE]->(b)\n" +
                "    WHERE a = b AND numEdges = 1 RETURN a\n" +
                "    UNION\n" +
                "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)\n" +
                "\tWHERE NOT (a)-[:MODEL_EDGE]->() AND a.isEnd IS NULL RETURN a\n" +
                "} WITH a\n" +
                "RETURN COUNT(a) AS numNodes";
        List<Record> queryAnswer;

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(numNodesWithMissingOutputs);
                return result.list();
            });
        }

        return queryAnswer.get(0).get("numNodes").asInt();
    }

    private int getNumNodesMissingInput(String modelID){
        // Check for nodes without any ingoing edge. Nodes whose only input is themselves are also included
        String numNodesWithMissingInputs = "CALL{\n" +
                "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(:DG_node)-[me:MODEL_EDGE]->(a)\n" +
                "    WITH a,COUNT(me) AS numEdges\n" +
                "    MATCH (b:DG_node)-[me:MODEL_EDGE]->(a)\n" +
                "    WHERE a = b AND numEdges = 1 RETURN a\n" +
                "    UNION\n" +
                "    MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS{DG:'After'}]-(a:DG_node)\n" +
                "    WHERE NOT ()-[:MODEL_EDGE]->(a) AND a.isStart IS NULL RETURN a\n" +
                "} WITH a\n" +
                "RETURN COUNT(a) AS numNodes";
        List<Record> queryAnswer;

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(numNodesWithMissingInputs);
                return result.list();
            });
        }

        return queryAnswer.get(0).get("numNodes").asInt();
    }

    private void addOutputBindings(String modelID, String dfType, String bThreshold){
        String addOutputBindings = "CALL{\n" +
                //Get all output bindings
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (a)--(cl:Class)--(i:Event)\n" +
                "\tMATCH (b)--(:Class)--(j:Event)\n" +
                "\tMATCH (i)-[:DF*{EntityType:\""+dfType+"\"}]->(j)\n" +
                "\tWITH DISTINCT m,cl.Type AS classType,i,COLLECT(j) AS eventsCaused\n" +
                //Get output bindings that have an intermediate connecting node
                "\tMATCH (m)-[:CONTAINS]-(c:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tOPTIONAL MATCH (i)-[:DF*{EntityType:\""+dfType+"\"}]->(k:Event)-[:DF*{EntityType:\""+dfType+"\"}]->(j:Event)\n" +
                "\tWHERE EXISTS((c)--(:Class)--(k)) AND EXISTS((b)--(:Class)--(j)) AND j IN eventsCaused\n" +
                "\tWITH i,classType,eventsCaused,COLLECT(DISTINCT j) AS eventsOtherCause\n" +
                //Remove output bindings that have an intermediate connection from the list
                "\tWITH i,classType,apoc.coll.subtract(eventsCaused, eventsOtherCause) AS eventList\n" +
                //Obtain class name and return bindings with their frequency
                "\tUNWIND eventList AS event\n" +
                "\tWITH DISTINCT i,classType,apoc.coll.sort(COLLECT(DISTINCT event[classType])) AS oB,1 AS n\n" +
                "\tRETURN DISTINCT i[classType] AS mActivity, oB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\n" +
                "\tUNION\n" +
                //Get all nodes with "ARTIFICIAL_END" as their Output Binding
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (a)--(cl:Class)--(i:Event)\n" +
                "\tWHERE b.isEnd AND NOT EXISTS((i)-[:DF{EntityType:\""+dfType+"\"}]->())\n" +
                "\tWITH DISTINCT i,cl.Type AS classType,[b.ID] AS oB, 1 AS n\n" +
                "\tRETURN DISTINCT i[classType] AS mActivity, oB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\n" +
                "\tUNION\n" +
                //Get all Output Bindings of the "ARTIFICIAL_START" node
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (b)--(cl:Class)--(i:Event)--(en:Entity{EntityType:\""+dfType+"\"})\n" +
                "\tWHERE a.isStart\n" +
                "\tWITH DISTINCT m,cl.Type AS classType,a AS start,en.uID AS enUID,COLLECT(i) AS eventsCaused\n" +
                "\tMATCH (m)-[:CONTAINS]-(c:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tOPTIONAL MATCH (k:Event)-[:DF*{EntityType:\""+dfType+"\"}]->(j:Event)\n" +
                "\tWHERE EXISTS((c)--(:Class)--(k)) AND EXISTS((b)--(:Class)--(j)) AND j IN eventsCaused\n" +
                "\tWITH start,enUID,classType,eventsCaused,COLLECT(DISTINCT j) AS eventsOtherCause\n" +
                "\tWITH start,enUID,classType,apoc.coll.subtract(eventsCaused, eventsOtherCause) AS eventList\n" +
                "\tUNWIND eventList AS event\n" +
                "\tWITH DISTINCT start,enUID,apoc.coll.sort(COLLECT(DISTINCT event[classType])) AS oB,1 AS n\n" +
                "\tRETURN DISTINCT start.ID as mActivity, oB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\n" +
                "}\n" +
                "WITH mActivity,apoc.agg.maxItems(mActivity, bindFreq).value AS freqMax, COLLECT([oB,bindFreq]) AS bindingsDetails\n" +
                //Get all single activities related (as output) to the Model Activity
                "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE a.ID = mActivity\n" +
                "WITH DISTINCT mActivity, bindingsDetails, freqMax, COLLECT(b.ID) AS allOutputs\n" +
                "CALL apoc.when(\n" +
                //If there is only one binding for that activity, there is no need to check against the threshold
                "\tSIZE(bindingsDetails) = 1,\n" +
                "    \"UNWIND bindingsDetails AS bindDetails\n" +
                "    RETURN mActivity AS mAct,COLLECT(apoc.text.join(bindDetails[0],'|')) AS outputBindings\",\n" +
                //Otherwise, a UNION is made to identify the highest-frequency binding for every output and the bindings above the threshold
                "    \"CALL{\n" +
                //The highest-frequency bindings help in ensuring that every output is represented with at least one relationship in the resulting model
                "    \tWITH mActivity, bindingsDetails, allOutputs\n" +
                "        UNWIND allOutputs AS o\n" +
                "        WITH mActivity, bindingsDetails, o\n" +
                "        UNWIND bindingsDetails AS bindDetails\n" +
                "        WITH mActivity, o, bindDetails\n" +
                "        WHERE o IN bindDetails[0]\n" +
                "        WITH mActivity,o,apoc.agg.maxItems(bindDetails[0],bindDetails[1]).items AS bindActsMaxFreq\n" +
                //Bindings composed of more than one activity are joined by a "|" character: ["A","B"] -> ["A|B"]
                "        RETURN mActivity AS mAct,COLLECT(DISTINCT apoc.text.join(bindActsMaxFreq[0],'|')) AS oBindings\n" +
                "\t\tUNION\n" +
                "    \tWITH mActivity, bindingsDetails, freqMax\n" +
                "        UNWIND bindingsDetails AS bindDetails\n" +
                "        WITH mActivity,freqMax,bindDetails\n" +
                "        WHERE (bindDetails[1]*1.0)/freqMax >= "+bThreshold+"\n" +
                "        RETURN mActivity AS mAct,COLLECT(DISTINCT apoc.text.join(bindDetails[0],'|')) AS oBindings\n" +
                "    }\n" +
                "    WITH mAct,oBindings\n" +
                "    UNWIND oBindings AS oBind\n" +
                "    RETURN mAct, COLLECT(DISTINCT oBind) AS outputBindings\",\n" +
                "    {mActivity:mActivity,bindingsDetails:bindingsDetails,freqMax:freqMax,allOutputs:allOutputs}\n" +
                ")YIELD value\n" +
                "WITH value.mAct AS mActivity, value.outputBindings AS outputBindings\n" +
                //Once all bindings have been identified for every Model Activity, they are stored as properties
                "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(dg:DG_node{ID:mActivity})\n" +
                "SET dg.OutputBindings = outputBindings";

        try ( Session session = driver.session() ) {
            session.run(addOutputBindings);
        }
    }

    private void addInputBindings(String modelID, String dfType, String bThreshold) {
        String addInputBindings = "CALL{\n" +
                //Get all input bindings
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (a)--(cl:Class)--(i:Event)\n" +
                "\tMATCH (b)--(:Class)--(j:Event)\n" +
                "\tMATCH (i)-[:DF*{EntityType:\""+dfType+"\"}]->(j)\n" +
                "\tWITH DISTINCT m,cl.Type AS classType,j,COLLECT(i) AS causeEvents\n" +
                //Get input bindings that have an intermediate connecting node
                "\tMATCH (m)-[:CONTAINS]-(c:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tOPTIONAL MATCH (i:Event)-[:DF*{EntityType:\""+dfType+"\"}]->(k:Event)-[:DF*{EntityType:\""+dfType+"\"}]->(j)\n" +
                "\tWHERE EXISTS((b)--(:Class)--(k)) AND EXISTS((c)--(:Class)--(i)) AND i IN causeEvents\n" +
                "\tWITH j,classType,causeEvents,COLLECT(DISTINCT i) AS eventsOtherCause\n" +
                //Remove input bindings that have an intermediate connection from the list
                "\tWITH j,classType,apoc.coll.subtract(causeEvents, eventsOtherCause) AS eventList\n" +
                //Obtain class name and return bindings with their frequency
                "\tUNWIND eventList AS event\n" +
                "\tWITH DISTINCT j,classType,apoc.coll.sort(COLLECT(DISTINCT event[classType])) AS iB,1 AS n\n" +
                "\tRETURN DISTINCT j[classType] AS mActivity, iB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\t\n" +
                "\tUNION\n" +
                //Get all nodes with "ARTIFICIAL_START" as their Input Binding
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (b)--(cl:Class)--(i:Event)\n" +
                "\tWHERE a.isStart AND NOT EXISTS(()-[:DF{EntityType:\""+dfType+"\"}]->(i))\n" +
                "\tWITH DISTINCT i,cl.Type AS classType,[a.ID] AS iB, 1 AS n\n" +
                "\tRETURN DISTINCT i[classType] AS mActivity, iB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\n" +
                "\tUNION\n" +
                //Get all Input Bindings of the "ARTIFICIAL_END" node
                "\tMATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tMATCH (a)--(cl:Class)--(i:Event)--(en:Entity{EntityType:\""+dfType+"\"})\n" +
                "\tWHERE b.isEnd\n" +
                "\tWITH DISTINCT m,cl.Type AS classType,b AS end,en.uID AS enUID,COLLECT(i) AS causeEvents\n" +
                "\tMATCH (m)-[:CONTAINS]-(c:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "\tOPTIONAL MATCH (i:Event)-[:DF*{EntityType:\""+dfType+"\"}]->(k:Event)\n" +
                "\tWHERE EXISTS((c)--(:Class)--(i)) AND EXISTS((b)--(:Class)--(k)) AND i IN causeEvents\n" +
                "\tWITH end,enUID,classType,causeEvents,COLLECT(DISTINCT i) AS eventsOtherCause\n" +
                "\tWITH end,enUID,classType,apoc.coll.subtract(causeEvents, eventsOtherCause) AS eventList\n" +
                "\tUNWIND eventList AS event\n" +
                "\tWITH DISTINCT end,enUID,apoc.coll.sort(COLLECT(DISTINCT event[classType])) AS iB,1 AS n\n" +
                "\tRETURN DISTINCT end.ID as mActivity, iB, SUM(n) AS bindFreq ORDER BY bindFreq DESC\n" +
                "}\n" +
                "WITH mActivity,apoc.agg.maxItems(mActivity, bindFreq).value AS freqMax, COLLECT([iB,bindFreq]) AS bindingsDetails\n" +
                //Get all single activities related (as input) to the Model Activity
                "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(a:DG_node)-[:MODEL_EDGE]->(b:DG_node)\n" +
                "WHERE b.ID = mActivity\n" +
                "WITH DISTINCT mActivity, bindingsDetails, freqMax, COLLECT(a.ID) AS allInputs\n" +
                "CALL apoc.when(\n" +
                //If there is only one binding for that activity, there is no need to check against the threshold
                "\tSIZE(bindingsDetails) = 1,\n" +
                "    \"UNWIND bindingsDetails AS bindDetails\n" +
                "    RETURN mActivity AS mAct,COLLECT(apoc.text.join(bindDetails[0],'|')) AS inputBindings\",\n" +
                //Otherwise, a UNION is made to identify the highest-frequency binding for every output and the bindings above the threshold
                "    \"CALL{\n" +
                //The highest-frequency bindings help in ensuring that every input is represented with at least one relationship in the resulting model
                "    \tWITH mActivity, bindingsDetails, allInputs\n" +
                "        UNWIND allInputs AS in\n" +
                "        WITH mActivity, bindingsDetails, in\n" +
                "        UNWIND bindingsDetails AS bindDetails\n" +
                "        WITH mActivity, in, bindDetails\n" +
                "        WHERE in IN bindDetails[0]\n" +
                "        WITH mActivity,in,apoc.agg.maxItems(bindDetails[0],bindDetails[1]).items AS bindActsMaxFreq\n" +
                //Bindings composed of more than one activity are joined by a "|" character: ["A","B"] -> ["A|B"]
                "        RETURN mActivity AS mAct,COLLECT(DISTINCT apoc.text.join(bindActsMaxFreq[0],'|')) AS iBindings\n" +
                "\t\tUNION\n" +
                "    \tWITH mActivity, bindingsDetails, freqMax\n" +
                "        UNWIND bindingsDetails AS bindDetails\n" +
                "        WITH mActivity,freqMax,bindDetails\n" +
                "        WHERE (bindDetails[1]*1.0)/freqMax >= "+bThreshold+"\n" +
                "        RETURN mActivity AS mAct,COLLECT(DISTINCT apoc.text.join(bindDetails[0],'|')) AS iBindings\n" +
                "    }\n" +
                "    WITH mAct,iBindings\n" +
                "    UNWIND iBindings AS iBind\n" +
                "    RETURN mAct, COLLECT(DISTINCT iBind) AS inputBindings\",\n" +
                "    {mActivity:mActivity,bindingsDetails:bindingsDetails,freqMax:freqMax,allInputs:allInputs}\n" +
                ")YIELD value\n" +
                "WITH value.mAct AS mActivity, value.inputBindings AS inputBindings\n" +
                //Once all bindings have been identified for every Model Activity, they are stored as properties
                "MATCH (m:Model{ID:\""+modelID+"\"})-[:CONTAINS]-(dg:DG_node{ID:mActivity})\n" +
                "SET dg.InputBindings = inputBindings";

        try ( Session session = driver.session() ) {
            session.run(addInputBindings);
        }
    }

    private void generatePetriNetOutputs(){
        String petriNetOutputs =
                //Obtain the outputs for all Model Activities
                "MATCH (m:Model{ID:\""+modelName+"\"})--(dg:DG_node)\n" +
                "WITH dg.ID AS mAct, dg.OutputBindings AS maOB\n" +
                "UNWIND maOB AS mActs\n" +
                "WITH mAct,mActs AS Output\n" +
                "WITH mAct, COLLECT(SPLIT(Output,\"|\")) AS Output\n" +
                "CALL apoc.do.when(\n" +
                //If the Model Activity has more than 1 output (OR split), a place is connected to the Model Activity
                //For each output, a tau transition is connected to the place
                //For each activity on the output (AND split), a place is connected to the tau transition
                "\tSIZE(Output) > 1,\n" +
                "    \"MERGE (p:PetriNet:Model_node{type:'t',t:mAct,model:'"+modelName+"'})-[:MODEL_EDGE]->(o:PetriNet:Model_node{type:'p',model:'"+modelName+"'})\n" +
                "\tWITH Output,mAct,o\n" +
                "\tUNWIND Output as a\n" +
                "\tCREATE (o)-[:MODEL_EDGE]->(t:PetriNet:Model_node{type:'tau',model:'"+modelName+"'})\n" +
                "\tWITH t,a,mAct\n" +
                "\tUNWIND a AS ma\n" +
                "\tOPTIONAL MATCH (n:PetriNet{model:'"+modelName+"'}) WHERE n.in = mAct AND n.out = ma\n" +
                "\tCALL apoc.do.when(\n" +
                "\t\tn IS NULL,\n" +
                "\t\t\\\"CREATE (t)-[:MODEL_EDGE]->(:PetriNet:Model_node{type:\\\\'p\\\\',in:mAct,out:ma,model:\\\\'"+modelName+"\\\\'})\\\",\n" +
                "\t\t\\\"CREATE (t)-[:MODEL_EDGE]->(n)\\\",\n" +
                "\t\t{n:n,t:t,mAct:mAct,ma:ma})\n" +
                "\tYIELD value RETURN 1\",\n" +
                //If the Model Activity has one output
                //For each activity on the output (AND split), a place is connected to the Model Activity
                "\t\"MERGE (p:PetriNet:Model_node{type:'t',t:mAct,model:'"+modelName+"'})\n" +
                "    WITH p,Output,mAct\n" +
                "    UNWIND Output as a\n" +
                "    WITH p,a,mAct\n" +
                "    UNWIND a AS ma\n" +
                "    OPTIONAL MATCH (n:PetriNet{model:'"+modelName+"'}) WHERE n.in = mAct AND n.out = ma\n" +
                "    CALL apoc.do.when(\n" +
                "        n IS NULL,\n" +
                "        \\\"CREATE (p)-[:MODEL_EDGE]->(:PetriNet:Model_node{type:\\\\'p\\\\',in:mAct,out:ma,model:\\\\'"+modelName+"\\\\'})\\\",\n" +
                "        \\\"CREATE (p)-[:MODEL_EDGE]->(n)\\\",\n" +
                "        {n:n,p:p,mAct:mAct,ma:ma})\n" +
                "    YIELD value RETURN 1\",     \n" +
                "    {mAct:mAct,Output:Output})\n" +
                "YIELD value RETURN 1";

        try ( Session session = driver.session() ) {
            session.run(petriNetOutputs);
        }
    }

    private void generatePetriNetInputs(){
        String petriNetInputs =
                //Create the node for the ARTIFICIAL END
                "CREATE (:PetriNet:Model_node{model:\""+modelName+"\",t:\"ARTIFICIAL_END\",type:\"t\"})\n" +
                //Obtain the inputs for all Model Activities
                "WITH 1 AS ignore\n" +
                "MATCH (m:Model{ID:\""+modelName+"\"})--(dg:DG_node)\n" +
                "WITH dg.ID AS mAct, dg.InputBindings AS maIB\n" +
                "UNWIND maIB AS mActs\n" +
                "WITH mAct,mActs AS Input\n" +
                "WITH mAct, COLLECT(SPLIT(Input,\"|\")) AS Input\n" +
                "CALL apoc.do.when(\n" +
                //If the Model Activity has more than 1 input (OR join), a place is connected to the Model Activity
                //For each input, a tau transition is connected to the place
                //For each activity on the input (AND join), a place is connected to the tau transition
                "\tSIZE(Input) > 1,\n" +
                "    \"MATCH (n:PetriNet{type:'t',t:mAct,model:'"+modelName+"'})\n" +
                "    MERGE (o:PetriNet:Model_node{type:'p',model:'"+modelName+"'})-[:MODEL_EDGE]->(n)\n" +
                "\tWITH Input,mAct,o\n" +
                "\tUNWIND Input as a\n" +
                "\tCREATE (t:PetriNet:Model_node{type:'tau',model:'"+modelName+"'})-[:MODEL_EDGE]->(o)\n" +
                "    WITH t,a,mAct\n" +
                "    UNWIND a AS ma\n" +
                "\tOPTIONAL MATCH (n:PetriNet{model:'"+modelName+"'}) WHERE n.in = ma AND n.out = mAct\n" +
                "    CALL apoc.do.when(\n" +
                "        n IS NULL,\n" +
                "        \\\"CREATE (:PetriNet:Model_node{type:\\\\'p\\\\',in:ma,out:mAct,model:\\\\'"+modelName+"\\\\'})-[:MODEL_EDGE]->(t)\\\",\n" +
                "        \\\"CREATE (n)-[:MODEL_EDGE]->(t)\\\",\n" +
                "        {n:n,t:t,mAct:mAct,ma:ma})\n" +
                "    YIELD value RETURN 1\",\n" +
                //If the Model Activity has one input
                //For each activity on the input (AND split), a place is connected to the Model Activity
                "    \"MATCH (p:PetriNet{type:'t',t:mAct,model:'"+modelName+"'})\n" +
                "    WITH p,Input,mAct\n" +
                "    UNWIND Input as a\n" +
                "    WITH p,a,mAct\n" +
                "    UNWIND a AS ma\n" +
                "    OPTIONAL MATCH (n:PetriNet{model:'"+modelName+"'}) WHERE n.in = ma AND n.out = mAct\n" +
                "    CALL apoc.do.when(\n" +
                "        n IS NULL,\n" +
                "        \\\"CREATE (:PetriNet:Model_node{type:\\\\'p\\\\',in:ma,out:mAct,model:\\\\'"+modelName+"\\\\'})-[:MODEL_EDGE]->(p)\\\",\n" +
                "        \\\"CREATE (n)-[:MODEL_EDGE]->(p)\\\",\n" +
                "        {n:n,p:p,mAct:mAct,ma:ma})\n" +
                "    YIELD value RETURN 1\",     \n" +
                "    {mAct:mAct,Input:Input})\n" +
                "YIELD value RETURN 1";

        try ( Session session = driver.session() ) {
            session.run(petriNetInputs);
        }
    }

    private void setStartEndNodes(){
        // Since our implementation of the DG to Petri net transformation treats the artificial start and end places as transitions,
        // we must correct this with these three queries. First, we change the type and properties of the nodes. Then, we remove
        // the places connected originally to the Start and End nodes since they were considered transitions.
        String setStartEndProperties = "MATCH (m:Model{ID:\""+modelName+"\"}),(s:PetriNet{t:\"ARTIFICIAL_START\"}),(e:PetriNet{t:\"ARTIFICIAL_END\"})\n" +
                "CREATE (m)-[:CONTAINS_PN]->(s)\n" +
                "SET s.type = \"s_e\",s.isStart=true,s.t=null\n" +
                "SET e.type = \"s_e\",e.isEnd=true,e.t=null";

        String removeStartPlace = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS_PN]->(s:PetriNet)-->(p:PetriNet)-->(n)\n" +
                "MERGE (s)-[:MODEL_EDGE]->(n)\n" +
                "DETACH DELETE p";

        String removeEndPlace = "MATCH (n)-->(p:PetriNet)-->(e:PetriNet{model:\""+modelName+"\",isEnd:true})\n" +
                "MERGE (n)-[:MODEL_EDGE]->(e)\n" +
                "DETACH DELETE p";

        try ( Session session = driver.session() ) {
            session.run(setStartEndProperties);
            session.run(removeStartPlace);
            session.run(removeEndPlace);
        }
    }

    private void optimizePetriNet(){
        //Find cases where the Petri Net has the following connections: Place1 -> Tau -> Place2
        //If the Tau transition only has one input and one output and the Place2 only has 1 input,
        //the Petri Net can be simplified by connecting the outputs of Place2 directly to Place1
        //and removing Tau and Place2
        String tauRemoval1 = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS_PN]->(s:PetriNet)-[*]->(t{type:'tau'})\n" +
                "WITH DISTINCT t\n" +
                "MATCH ()-[a:MODEL_EDGE]->(t)\n" +
                "WITH t,COUNT(DISTINCT a) AS inTau\n" +
                "MATCH (t)-[b:MODEL_EDGE]->()\n" +
                "WITH t,inTau,COUNT(DISTINCT b) AS outTau\n" +
                "WHERE inTau = 1 AND outTau = 1\n" +
                "MATCH (t)-->(p2)\n" +
                "MATCH ()-[c:MODEL_EDGE]->(p2)\n" +
                "WITH p2,t,COUNT(c) AS inP2\n" +
                "WHERE inP2 = 1\n" +
                "MATCH (p1)-->(t)-->(p2)\n" +
                "MATCH (p2)-->(outP2)\n" +
                "MERGE (p1)-[:MODEL_EDGE]->(outP2)\n" +
                "DETACH DELETE t,p2";

        //Find cases where the Petri Net has the following connections: Place1 -> Tau -> Place2
        //If the Tau transition only has one input and one output and the Place1 only has 1 output,
        //the Petri Net can be simplified by connecting the inputs of Place1 directly to Place2
        //and removing Place1 and Tau
        String tauRemoval2 = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS_PN]->(s:PetriNet)-[*]->(t{type:'tau'})\n" +
                "WITH DISTINCT t\n" +
                "MATCH ()-[a:MODEL_EDGE]->(t)\n" +
                "WITH t,COUNT(DISTINCT a) AS inTau\n" +
                "MATCH (t)-[b:MODEL_EDGE]->()\n" +
                "WITH t,inTau,COUNT(DISTINCT b) AS outTau\n" +
                "WHERE inTau = 1 AND outTau = 1\n" +
                "MATCH (p1)-->(t)\n" +
                "MATCH (p1)-[c:MODEL_EDGE]->()\n" +
                "WITH p1,t,COUNT(c) AS outP1\n" +
                "WHERE outP1 = 1\n" +
                "MATCH (p1)-->(t)-->(p2)\n" +
                "MATCH (inP1)-->(p1)\n" +
                "MERGE (inP1)-[:MODEL_EDGE]->(p2)\n" +
                "DETACH DELETE p1,t";

        try ( Session session = driver.session() ) {
            session.run(tauRemoval1);
            session.run(tauRemoval2);
        }
    }
}
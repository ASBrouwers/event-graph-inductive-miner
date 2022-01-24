package EventGraphs.Neo4j;

import EventGraphs.GUI.ModelFinder;
import EventGraphs.Utils;
import javafx.scene.control.Alert;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

import java.util.*;

public class N4JQueries {
    Driver driver;

    public N4JQueries(Driver d){
        driver = d;
    }

    public Driver getDriver(){
        return driver;
    }

    /*====================================================
    * GENERAL QUERIES
    * ====================================================*/
    public void createConstraints(){
        // Create unique constraints
        String modelConstraint = "CREATE CONSTRAINT UniqueModels IF NOT EXISTS ON (m:Model) ASSERT m.ID IS UNIQUE;";
        String logConstraint = "CREATE CONSTRAINT UniqueLogs IF NOT EXISTS ON (l:Log) ASSERT l.ID IS UNIQUE;";

        try ( Session session = driver.session() ) {
            session.run(modelConstraint);
            session.run(logConstraint);
        }
    }

    public void deleteConstraints(){
        String delModelConstraint = "DROP CONSTRAINT UniqueModels IF EXISTS;";
        String delLogConstraint = "DROP CONSTRAINT UniqueLogs IF EXISTS;";

        try ( Session session = driver.session() ) {
            session.run(delModelConstraint);
            session.run(delLogConstraint);
        }
    }

    public boolean areConstraintsActive(){
        List<Record> queryAnswer;
        String checkConstraints = "SHOW CONSTRAINTS";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(checkConstraints);
                return result.list();
            });
        }

        // The Cypher query "SHOW CONSTRAINTS" returns an empty list if no constraints have been set in the database.
        return queryAnswer.size() > 0;
    }

    public void clearDatabase(){
        String deleteAll = "CALL apoc.periodic.iterate(\n" +
                "\t\"MATCH (n) RETURN n\",\n" +
                "\t\"DETACH DELETE n\",\n" +
                "\t{batchSize:500})\n" +
                "YIELD batches, total\n" +
                "RETURN batches, total";
        try ( Session session = driver.session() ) {
            session.run(deleteAll);
        }
    }

    public void deleteLog(String logName){
        String deleteClasses = "CALL apoc.periodic.iterate(\n" +
                "\t\"MATCH (l:Log{ID:'"+logName+"'})-[:HAS]->(:Event)-[:OBSERVES]->(c) RETURN c\",\n" +
                "\t\"DETACH DELETE c\",\n" +
                "    {batchSize:500})\n" +
                "YIELD batches, total return batches, total";
        String deleteEntities = "CALL apoc.periodic.iterate(\n" +
                "\t\"MATCH (l:Log{ID:'"+logName+"'})-[:HAS]->(:Event)-[:CORR]->(n) RETURN n\",\n" +
                "\t\"DETACH DELETE n\",\n" +
                "    {batchSize:500})\n" +
                "YIELD batches, total return batches, total";
        String deleteEvents = "CALL apoc.periodic.iterate(\n" +
                "\t\"MATCH (l:Log{ID:'"+logName+"'})-[:HAS]->(e) RETURN e\",\n" +
                "\t\"DETACH DELETE e\",\n" +
                "    {batchSize:500})\n" +
                "YIELD batches, total return batches, total";
        String deleteAlgorithms = "CALL apoc.periodic.iterate(\n" +
                "\t\"MATCH (l:Log{ID:'"+logName+"'})<-[:MAPS]->(a) RETURN a\",\n" +
                "\t\"DETACH DELETE a\",\n" +
                "    {batchSize:500})\n" +
                "YIELD batches, total return batches, total";
        String deleteLog = "MATCH (l:Log{ID:'"+logName+"'}) DETACH DELETE l";

        try ( Session session = driver.session() ) {
            session.run(deleteClasses);
            session.run(deleteEntities);
            session.run(deleteEvents);
            session.run(deleteAlgorithms);
            session.run(deleteLog);
        }
    }

    public void deleteModel(String modelName){
        String deleteModel = "MATCH (m:Model{ID:\""+modelName+"\"})\n" +
                                "OPTIONAL MATCH (m)-[:PRODUCES]-(a)\n" +
                                "OPTIONAL MATCH (m)-[:CONTAINS]-(n)\n" +
                                "OPTIONAL MATCH (m)-[:CONTAINS_PN]-(o)\n" +
                                "OPTIONAL MATCH (a)-[:ALGORITHM_NODE]-(p)\n" +
                                "DETACH DELETE a,m,n,o,p";

        try ( Session session = driver.session() ) {
            session.run(deleteModel);
        }
    }

    /*====================================================
     * GUI QUERIES
     * ====================================================*/
    public List<String> getDBLogs(){
        List<Record> queryAnswer;
        List<String> logNames = new ArrayList<>();

        try ( Session session = driver.session() ) {
            queryAnswer = session.readTransaction(tx -> {
                Result result = tx.run("MATCH (l:Log) RETURN l.ID as LogNames ORDER BY LogNames");
                return result.list();
            });
        }

        for(Record l : queryAnswer){
            String logName = String.valueOf(l.get("LogNames")).replace("\"","");
            logNames.add(logName);
        }

        return logNames;
    }

    public Map<String,Object> getModelDetails(String modelName){
        List<Record> queryAnswer;
        Map<String,Object> modelDetails = new HashMap<>();

        // Since both the Algorithm and Model nodes contain relevant properties of the model,
        // we merge the properties of each node in one result
        String getModelDetails = "MATCH (m:Model{ID:\""+modelName+"\"})\n" +
                "OPTIONAL MATCH (a:Algorithm)--(m)\n" +
                "RETURN apoc.map.merge(PROPERTIES(a),PROPERTIES(m)) AS attributes";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getModelDetails);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            modelDetails = new HashMap<>(r.get("attributes").asMap());
        }

        return modelDetails;
    }

    public Map<String,Object> getLogDetails(String logName){
        List<Record> queryAnswer;
        Map<String,Object> logDetails = new HashMap<>();
        String getLogDetails = "MATCH (l:Log) WHERE l.ID = \""+logName+"\"\n" +
                "OPTIONAL MATCH (l)--(e:Event)--(en:Entity)\n" +
                "OPTIONAL MATCH (l)--(e)--(c:Class)\n" +
                "RETURN properties(l) AS attributes, COLLECT(DISTINCT(en.EntityType)) AS entities, COLLECT(DISTINCT(c.Type)) AS classes";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getLogDetails);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            logDetails = new HashMap<>(r.get("attributes").asMap());

            List<Object> entities = r.get("entities").asList();
            int i = 1;
            for(Object o : entities){
                logDetails.put("Entity_"+i,o);
                i++;
            }

            List<Object> classes = r.get("classes").asList();
            int j = 1;
            for(Object o : classes){
                logDetails.put("Class"+j,o);
                j++;
            }
        }

        return logDetails;
    }

    public List<String> getExistingEntities(String logName){
        List<Record> queryAnswer;
        List<String> existingEntities = new ArrayList<>();

        String existingEntitiesQuery = "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                                            "WHERE l.ID = \""+logName+"\"\n" +
                                            "RETURN en.EntityType AS Entity, COUNT(DISTINCT(en)) AS DistinctValues ORDER BY toLower(Entity)";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(existingEntitiesQuery);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            String entity = r.get("Entity").toString().replace("\"","");
            String distinctValues = r.get("DistinctValues").toString();

            existingEntities.add(entity + " (" + distinctValues + ")");
        }

        return existingEntities;
    }

    public Utils.PropertySamples getExistingEntitiesWithSamples(String logName, List<String> selProperties){
        List<Record> queryAnswer;
        List<String> distinctProperties = new ArrayList<>();
        Map<String,List<String>> propertySamples = new HashMap<>();

        // Properties selected to create the class are not displayed as available options for the :DF_C
        // These properties are excluded by adding them to the query in a collection ["Prop1", ..., "PropN"]
        String selProp = "[";
        for(int i = 0; i < selProperties.size(); i++){
            selProp += "\""+selProperties.get(i)+"\"";
            if(i != selProperties.size()-1) selProp += ",";
        }
        selProp += "]";

        String existingEntitiesQuery = "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                "WHERE l.ID = \""+logName+"\" AND NOT en.EntityType IN "+selProp+"\n" +
                "RETURN en.EntityType AS Property, COUNT(DISTINCT(en)) AS DistinctValues, COLLECT(DISTINCT(en.uID))[0..5] AS Samples";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(existingEntitiesQuery);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            String property = r.get("Property").toString().replace("\"","");

            String distinctValues = r.get("DistinctValues").toString();
            distinctProperties.add(property + " (" + distinctValues + ")");

            List<Object> samplesObj = r.get("Samples").asList();
            List<String> samplesStr = new ArrayList<>();
            for(Object o: samplesObj)
                samplesStr.add(o.toString());
            propertySamples.put(property,samplesStr);
        }

        return new Utils.PropertySamples(distinctProperties,propertySamples);
    }

    public Map<String,List<String>> getExistingClasses(String logName){
        List<Record> queryAnswer;
        Map<String,List<String>> existingClasses = new HashMap<>();

        String existingClassesQuery = "MATCH (:Log{ID:\""+logName+"\"})--(:Event)--(c:Class)-[df:DF_C]-()\n" +
                "WITH DISTINCT c.Type AS Class, df.EntityType AS DF ORDER BY toLower(Class),toLower(DF)\n" +
                "RETURN Class, COLLECT(DISTINCT DF) AS DF";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(existingClassesQuery);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            String cl = r.get("Class").toString().replace("\"","");

            List<String> dFs = new ArrayList<>();
            for(Object o : r.get("DF").asList()){
                dFs.add(o.toString());
            }

            existingClasses.put(cl,dFs);
        }

        return existingClasses;
    }

    public Utils.PropertySamples getDistinctPropertyValues(String logName, boolean forClass){
        List<Record> queryAnswer;
        List<String> distinctProperties = new ArrayList<>();
        Map<String,List<String>> propertySamples = new HashMap<>();
        String filterActivity = "\n";
        if(!forClass) filterActivity = " AND p <> \"Activity\"\n"; //For Entities, filter the Activity property

        String distinctPropertyValuesQuery = "MATCH (l:Log)--(e:Event) WHERE l.ID = \""+logName+"\"\n" +
                                            "WITH DISTINCT keys(e) AS keys, size(keys(e)) AS numProperties\n" +
                                            "ORDER BY numProperties DESC\n" +
                                            "UNWIND keys AS Properties\n" +
                                            "WITH DISTINCT(Properties) as p\n" +
                                            "MATCH (l:Log)--(e:Event) \n" +
                                            "WHERE l.ID = \""+logName+"\" AND p <> \"Timestamp\"" + filterActivity +
                                            "RETURN p AS Property, COUNT(DISTINCT e[p]) AS DistinctValues, COLLECT(DISTINCT e[p])[0..5] AS Samples";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(distinctPropertyValuesQuery);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            String property = r.get("Property").toString().replace("\"","");

            String distinctValues = r.get("DistinctValues").toString();
            distinctProperties.add(property + " (" + distinctValues + ")");

            List<Object> samplesObj = r.get("Samples").asList();
            List<String> samplesStr = new ArrayList<>();
            for(Object o: samplesObj)
                samplesStr.add(o.toString());
            propertySamples.put(property,samplesStr);
        }

        return new Utils.PropertySamples(distinctProperties,propertySamples);
    }

    public List<String> getExistingModels(){
        List<Record> queryAnswer;
        List<String> modelNames = new ArrayList<>();

        String getAllModels = "MATCH (m:Model)\n" +
                                "RETURN m.ID AS modelID\n" +
                                "ORDER BY modelID";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getAllModels);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            modelNames.add(r.get("modelID").asString().replace("\"",""));
        }

        return modelNames;
    }

    public String getAlgorithmName(String modelName){
        List<Record> queryAnswer;

        String getAlgName = "MATCH (m:Model{ID:\""+modelName+"\"})\n" +
                "RETURN m.Algorithm AS Algorithm";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getAlgName);
                return result.list();
            });
        }

        return queryAnswer.get(0).get("Algorithm").asString().replace("\"","");
    }

    public List<String> getExistingActivities(){
        List<Record> queryAnswer;
        List<String> activities = new ArrayList<>();

        // To get all the model activities, we retrieve all the transitions from the Petri net
        // Transitions can be identified by the existance of the property "t"
        String getAllActivities = "MATCH (m:Model)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE*0..]->(n:PetriNet)\n" +
                "WHERE n.t IS NOT NULL\n" +
                "WITH DISTINCT n.t AS Activity\n" +
                "WITH Activity, toLower(Activity) AS lcAct\n" +
                "RETURN Activity\n" +
                "ORDER BY lcAct";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getAllActivities);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            activities.add(r.get("Activity").asString().replace("\"",""));
        }

        return activities;
    }

    public List<String> getFilteredModels(List<ModelFinder.Filter> filterList){
        List<String> remainingModels = new ArrayList<>();
        List<Record> queryAnswer;
        boolean firstIteration = true;
        String modelListQuery;

        try ( Session session = driver.session() ) {
            // We create a loop to execute each query individually, using the result of the previous as input for the next
            for(ModelFinder.Filter f : filterList){
                // Queries stored in filterList do not have the initial MATCH statement, since it changes depending on the iteration
                String patternQuery = f.getFilterQuery();
                String fullQuery;

                if(firstIteration){
                    // For the first iteration, we use the MATCH to retrieve all the existing models from the database
                    firstIteration = false;

                    String initialModelListQuery = "MATCH (m:Model) WITH m\n";
                    fullQuery = initialModelListQuery + patternQuery;
                }else{
                    // For the following iterations, we retrieve only those models that remain from the previous filter
                    modelListQuery = "WITH [\"" + String.join("\",\"",remainingModels) + "\"] AS modelList\n" +
                            "MATCH (m:Model) WHERE m.ID IN modelList WITH m\n";
                    fullQuery = modelListQuery + patternQuery;
                }

                String finalFullQuery = fullQuery;
                queryAnswer = session.readTransaction(tx -> {
                    Result result = tx.run(finalFullQuery);
                    return result.list();
                });

                remainingModels.clear();
                for(Record r : queryAnswer){
                    remainingModels.add(r.get("modelID").asString());
                }
                // If the current filter does not return any model, we break the loop
                if(remainingModels.size() == 0) break;
            }
        } catch(Exception e){
            // Since we use a read-only transaction to execute the queries, we should catch
            // the error in case the queries try to write into the database, which could happen
            // depending on the modifications made by users through the UI
            Utils.showAlert("Application Error",Alert.AlertType.ERROR,"Error Description:\n\n"+e.getMessage());
            return new ArrayList<>();
        }

        return remainingModels;
    }

    /*====================================================
     * GRAPH ANALYSIS QUERIES
     * ====================================================*/
    public int loadEvents(List<String> logParams, List<String> attributes, List<String> dimensions){
        //logParams -> 0: fileName, 1: timestampName, 2: activityName
        String loadLogNode = "CREATE (:Log {ID:\""+logParams.get(0)+"\", " +
                                            "timestampCol:\""+logParams.get(1)+"\", " +
                                            "activityCol:\""+logParams.get(2)+"\", " +
                                            "attributesSelected:\"" + String.join("|",attributes) + "\"";

        if(dimensions != null) loadLogNode += ", Dimensions:\"" + String.join("|",dimensions) + "\"})";
        else loadLogNode += "})";

        String loadEventNodes = "USING PERIODIC COMMIT LOAD CSV WITH HEADERS FROM \"file:///"+logParams.get(0)+"\" AS line CREATE (:Event {";
        loadEventNodes += "Log: \""+logParams.get(0)+"\",";
        loadEventNodes += "Timestamp: datetime(line."+logParams.get(1)+"),";
        loadEventNodes += "Activity: line."+logParams.get(2);

        for(String att : attributes){
            // Use "`" to enclose attributes with characters that cause conflict with Cypher queries ("-")
            if(att.contains("-"))
                att = "`"+att+"`";
            loadEventNodes += ","+att+": line."+att;
        }

        loadEventNodes += "})";

        String createEdgeEventLog = "MATCH (e:Event {Log: \""+logParams.get(0)+"\"})" +
                                    "MATCH (l:Log {ID: \""+logParams.get(0)+"\"})" +
                                    "CREATE (l)-[:HAS]->(e)";

        String deleteLogProperty = "MATCH (e:Event)--(l:Log)\n" +
                                    "WHERE l.ID = \""+logParams.get(0)+"\"\n" +
                                    "SET e.Log = NULL";


        try ( Session session = driver.session() ) {
            session.run(loadLogNode);
        }catch(Exception e){
            // We catch the exception where the user tries to upload a log whose ID already exists in the DB
            Utils.showAlert("Application Error",Alert.AlertType.ERROR,"Error Description:\n\n"+e.getMessage());
            return 1;
        }

        try ( Session session = driver.session() ) {
            session.run(loadEventNodes);
            session.run(createEdgeEventLog);
            session.run(deleteLogProperty);
        }

        return 0;
    }

    public void createEntity(String logName, String attribute){
        List<Record> queryAnswer;
        String getDimensions = "MATCH (l:Log{ID:'"+logName+"'}) RETURN l.Dimensions AS Dimensions";
        List<String> dimensions = new ArrayList<>();
        boolean multiDimension = false;

        String createEntityNodes = "";
        String createEventEntityEdge = "";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getDimensions);
                return result.list();
            });
        }

        if(!queryAnswer.get(0).get("Dimensions").isNull()){
            String strDimensions = queryAnswer.get(0).get("Dimensions").asString();
            dimensions.addAll(Arrays.asList(strDimensions.split("\\|")));

            // If dimensions were defined for the event log, we check (1) if the attribute to be used to create the Entity nodes
            // is included in those dimensions and (2) if the attribute has the top priority in the dimensions specified.
            // If any of those is true, we execute the queries considering this dimensionality.
            if(dimensions.contains(attribute)) multiDimension = true;
            if(!dimensions.get(0).equals(attribute)) multiDimension = true;
        }

        if(multiDimension){
            int dimIndex = dimensions.indexOf(attribute);
            dimensions.subList(dimIndex,dimensions.size()).clear();
            String strDimensions = String.join("\",\"",dimensions);
            createEntityNodes = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)\n" +
                    "WITH e,keys(e) AS properties\n" +
                    "WHERE NOT ANY(x IN properties WHERE x IN [\""+strDimensions+"\"])\n" +
                    "WITH e WHERE EXISTS(e.`"+attribute+"`)\n" +
                    "WITH DISTINCT e.`"+attribute+"` AS id\n" +
                    "CREATE (en:Entity {ID:id, uID:(\""+attribute+"\"+toString(id)), EntityType:\""+attribute+"\", log:\""+logName+"\"})";
            createEventEntityEdge = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)\n" +
                    "WITH e,keys(e) AS properties\n" +
                    "WHERE NOT ANY(x IN properties WHERE x IN [\""+strDimensions+"\"])\n" +
                    "WITH e WHERE EXISTS(e.`"+attribute+"`)\n" +
                    "MATCH (n:Entity {EntityType: \""+attribute+"\" })\n" +
                    "WHERE e.`"+attribute+"` = n.ID AND n.log = \""+logName+"\"\n" +
                    "CREATE (e)-[:CORR]->(n)";
        }else{
            //All the events are considered to generate the entities if:
            //1) No dimensions were specified
            //2) The attribute selected to create the entity is not on the stored dimensions
            //3) The attribute is in the stored dimensions but has top priority
            createEntityNodes = "MATCH (e:Event)--(l:Log)\n" +
                    "WHERE l.ID = \""+logName+"\" AND EXISTS(e.`"+attribute+"`)\n" +
                    "WITH DISTINCT e.`"+attribute+"` AS id\n" +
                    "CREATE (en:Entity {ID:id, uID:(\""+attribute+"\"+toString(id)), EntityType:\""+attribute+"\", log:\""+logName+"\"})";
            createEventEntityEdge = "MATCH (e:Event)--(l:Log)\n" +
                    "WHERE l.ID = \""+logName+"\" AND EXISTS(e.`"+attribute+"`)\n" +
                    "MATCH (n:Entity {EntityType: \""+attribute+"\" })\n" +
                    "WHERE e.`"+attribute+"` = n.ID AND n.log = \""+logName+"\"\n" +
                    "CREATE (e)-[:CORR]->(n)";
        }

        try ( Session session = driver.session() ) {
            session.run(createEntityNodes);
            session.run(createEventEntityEdge);
        }

        createDFEdges(logName, attribute);
    }

    public void createDerivedEntity(String logName, List<String> entities){
        Collections.sort(entities);
        String n1 = entities.get(0);
        String n2 = entities.get(1);
        String derivedEntityType = n1+n2;

        String createDerivedEntity = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)\n" +
                "WHERE EXISTS(e.`"+n1+"`) AND EXISTS(e.`"+n2+"`)\n" +
                "WITH DISTINCT e.`"+n1+"` AS nID1, e.`"+n2+"` AS nID2\n" +
                "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(n1:Entity)\n" +
                "MATCH (l)--(:Event)--(n2:Entity)\n" +
                "WHERE n1.uID = '"+n1+"'+nID1 AND n2.uID = '"+n2+"'+nID2\n" +
                "WITH DISTINCT n1.ID as n1_id, n2.ID as n2_id\n" +
                "CREATE (:Entity{EntityType:\""+derivedEntityType+"\", uID:'"+derivedEntityType+"'+'_'+toString(n1_id)+'_'+toString(n2_id), "+n1+"ID:n1_id, "+n2+"ID:n2_id, log:\""+logName+"\"})";
        try ( Session session = driver.session() ) {
            session.run(createDerivedEntity);
            // After the derived Entity node is created, we connect the events of each original entity to the new derived Entity nodes
            for(String entity : entities){
                String correlateEventsToDerivedEntity = "MATCH (l:Log)--(e1:Event)-[:CORR]->(n1:Entity) WHERE l.ID = \""+logName+"\" AND n1.EntityType=\""+entity+"\"\n" +
                        "MATCH (derived:Entity) WHERE derived.EntityType = \""+derivedEntityType+"\" AND n1.ID = derived."+entity+"ID AND derived.log = \""+logName+"\"\n" +
                        "MERGE (e1)-[:CORR]->(derived)";
                session.run(correlateEventsToDerivedEntity);
            }
        }

        //Create :REL edges between entities and the created derived entity
        try ( Session session = driver.session() ) {
            for (int i = 0; i < entities.size(); i++) {
                if (i == entities.size() - 1) break;
                String en1 = entities.get(i);
                for (int j = i + 1; j < entities.size(); j++) {
                    String en2 = entities.get(j);
                    String createRELedges = "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(en:Entity{EntityType:'"+derivedEntityType+"'})\n" +
                            "WITH DISTINCT en\n" +
                            "MATCH (en1:Entity)--(:Event)--(en)--(:Event)--(en2:Entity)\n" +
                            "WHERE en1.ID = en."+en1+"ID AND en2.ID = en."+en2+"ID AND en1.EntityType <> en2.EntityType\n" +
                            "WITH DISTINCT en AS dEnt, [en1,en2] AS ents\n" +
                            "WITH DISTINCT dEnt, apoc.coll.sortNodes(ents, 'uID') AS ents\n" +
                            "WITH dEnt,ents[0] AS ent1,ents[1] AS ent2\n" +
                            "MERGE (dEnt)-[:REL{Type:'Reified'}]->(ent1)\n" +
                            "MERGE (dEnt)-[:REL{Type:'Reified'}]->(ent2)\n" +
                            "MERGE (ent1)-[:REL{Type:'"+derivedEntityType+"'}]->(ent2)";
                    session.run(createRELedges);
                }
            }
        }

        createDFEdges(logName, derivedEntityType);
    }

    private void createDFEdges(String logName, String entity){
        String createDFEdges = "MATCH (l:Log)--(e:Event)--(n:Entity) " +
                "WHERE l.ID = \""+logName+"\" AND n.EntityType=\""+entity+"\"\n" +
                "WITH n, e as nodes ORDER BY e.Timestamp,ID(e)\n" +
                "WITH n, COLLECT(DISTINCT(nodes)) as nodeList\n" +
                "UNWIND RANGE(0,SIZE(nodeList)-2) AS i\n" +
                "WITH n, nodeList[i] AS first, nodeList[i+1] AS second\n" +
                "MERGE (first) -[:DF{EntityType:\""+entity+"\"}]->(second)";

        try ( Session session = driver.session() ) {
            session.run(createDFEdges);
        }
    }

    public List<String> findEntityTypeAttributes(String logName, String entity){
        List<Record> queryAnswer;
        String getDistinctValues = "MATCH (l:Log)--(e:Event)\n" +
                "WHERE l.ID = \""+logName+"\"\n" +
                "WITH DISTINCT keys(e) AS keys\n" +
                "UNWIND keys AS Properties\n" +
                "WITH DISTINCT(Properties) as p\n" +
                "MATCH (l:Log)--(e:Event)--(en:Entity{EntityType:\""+entity+"\"})\n" +
                "WHERE l.ID = \""+logName+"\" AND NOT p IN ['ID', 'Timestamp', 'Activity', '"+entity+"']\n" +
                "WITH p AS property, en.ID AS entityID, COUNT(e) AS numEvents, COUNT(e[p]) AS numExistingValues, COUNT(DISTINCT(e[p])) AS distinctValues\n" +
                "RETURN property, COLLECT([numEvents,numExistingValues,distinctValues]) AS distinctValuesInfo";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getDistinctValues);
                return result.list();
            });
        }

        // The queryAnswer contains the following format:
        //      property     |   distinctValuesInfo
        //      -------------|--------------------------
        //      Property1    |   [[a,b,c],[d,e,f],...]
        // Where "Property1" is a property found in at least one of the events of the :DF path, "a/d" is the number
        // of events on the :DF of each entity type, "b/e" is the number of values available in the events of the :DF
        // path, and "c/f" is the number of distinct values for that attribute. For example, having Entity Type E, with
        // two distinct entities "z" and "w" we have the following :DF paths:
        // (a{x:1,y:2})-[z:DF]->(b{x:1,y:3})-[z:DF]->(c{x:1})
        // (d{x:4,y:5})-[w:DF]->(e{x:4,y:5})
        // The queryAnswer would look like this:
        //      property     |   distinctValuesInfo
        //      -------------|----------------------
        //      x            |   [[3,3,1],[2,2,1]]
        //                       (For entity "z", there are 3 events, the "x" property appears in 3 events and there is 1 distinct value)
        //                       (For entity "w", there are 2 events, the "x" property appears in 2 events and there is 1 distinct value)
        //      y            |   [[3,2,2],[2,2,1]]
        //                       (For entity "z", there are 3 events, the "y" property appears in 2 events and there are 2 distinct values)
        //                       (For entity "w", there are 2 events, the "y" property appears in 2 events and there is 1 distinct value)
        // Based on this example, property "x" can be considered an entity type attribute and can be moved from the events to the entity
        List<String> entityTypeAttributes = new ArrayList<>();
        boolean isEntityTypeCandidate = true;
        for(Record r : queryAnswer){
            List<Object> attributeInfo = r.get("distinctValuesInfo").asList();
            for(Object o : attributeInfo){
                List<Long> entityInfo = new ArrayList<>((Collection<Long>) o);
                // If the number of events on the :DF path does not match with the number of values
                // or the number of distinct values is different from 1, it means there are events in
                // the :DF that do not have that attribute or that have more than one distinct value,
                // so it is discarded as a possible entity type attribute.
                if(!entityInfo.get(0).equals(entityInfo.get(1)) || entityInfo.get(2) != 1){
                    isEntityTypeCandidate = false;
                    break;
                }
            }
            if(isEntityTypeCandidate) entityTypeAttributes.add(r.get("property").asString());
            isEntityTypeCandidate = true;
        }

        return entityTypeAttributes;
    }

    public void createEntityTypeAttributes(String logName, String entity, List<String> entityTypeAttributes){
        try ( Session session = driver.session() ) {
            for(String property : entityTypeAttributes){
                String addPropertyToEntity = "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                        "WHERE l.ID = \""+logName+"\" AND en.EntityType = \""+entity+"\"\n" +
                        "WITH DISTINCT en, e.`"+property+"` AS property\n" +
                        "SET en.`"+property+"` = property";
                session.run(addPropertyToEntity);
                String deletePropertyFromEvents = "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                        "WHERE l.ID = \""+logName+"\" AND en.EntityType = \""+entity+"\"\n" +
                        "SET e.`"+property+"` = null";
                session.run(deletePropertyFromEvents);
            }
        }
    }

    public List<String> getExistingEntityTypeAttributes(String logName){
        List<Record> queryAnswer;
        List<String> existingETAttributes = new ArrayList<>();

        String getExistingETAttributes = "MATCH (l:Log)--(e:Event)--(en:Entity) WHERE l.ID = \""+logName+"\"\n" +
                                            "WITH DISTINCT keys(en) as keys\n" +
                                            "UNWIND keys AS key\n" +
                                            "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                                            "WHERE l.ID = \""+logName+"\" AND NOT key ENDS WITH \"ID\" AND NOT key = \"EntityType\" AND NOT en[key] IS NULL\n" +
                                            "RETURN key AS EntityTypeAttr,COUNT(DISTINCT(e)) AS NumEvents";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getExistingETAttributes);
                return result.list();
            });
        }

        for(Record r : queryAnswer){
            String etAttrName = r.get("EntityTypeAttr").asString().replace("\"","");
            int numEvents = r.get("NumEvents").asInt(); //Number of events that share the ET Attribute
            existingETAttributes.add(etAttrName+" ("+numEvents+")");
        }

        return existingETAttributes;
    }

    public void returnETAttrToEvents(String logName, List<String> etAttributes){
        try ( Session session = driver.session() ) {
            for(String property : etAttributes){
                String addPropertyToEvents = "MATCH (l:Log)--(e:Event)--(en:Entity)\n" +
                                                "WHERE l.ID = \""+logName+"\" AND EXISTS(en."+property+")\n" +
                                                "SET e."+property+" = en."+property;
                session.run(addPropertyToEvents);

                String deletePropertyFromEntities = "MATCH (l:Log)--(:Event)--(en:Entity)\n" +
                                                    "WHERE l.ID = \""+logName+"\"\n" +
                                                    "SET en."+property+" = null";
                session.run(deletePropertyFromEntities);
            }
        }
    }

    public void createClass(String logName, List<String> classTypes, String dfEntity){
        Collections.sort(classTypes);
        String typeName = String.join("+", classTypes);
        String distinct = "WITH DISTINCT ";
        String properties = "";
        String id = "ID: ";
        String nullCheck = "WITH c, ";

        // For each property selected by the user to define the class type, we define add on different parts of the final query
        for(int i = 0; i < classTypes.size(); i++){
            distinct += "e.`"+classTypes.get(i)+"` AS `"+classTypes.get(i)+"`";
            properties += "`"+classTypes.get(i)+"`:`"+classTypes.get(i)+"`";;
            nullCheck += "`"+classTypes.get(i)+"`";
            id += "`"+classTypes.get(i)+"`";

            if(i == classTypes.size()-1){
                distinct += "\n";
            }else{
                distinct += ", ";
                properties += ", ";
                nullCheck += ", ";
                id += "+\"+\"+";
            }
        }
        nullCheck += " WHERE c IS NULL\n";

        // The parts of the query that relate to the multiple properties are then joined in the main query that creates the Class nodes
        String createClassNodes = "MATCH (l:Log{ID:\""+logName+"\"})--(e:Event)\n" + distinct +
                "OPTIONAL MATCH (:Log{ID:\""+logName+"\"})--(:Event)--(c:Class{"+id+"})\n" +
                nullCheck +
                "CREATE (:Class {" + properties + ", Type:\"" + typeName + "\", " + id + ", Log:\""+logName+"\"})";

        // Similarly, we use a loop to build the part of the query that compares the event attribute with the class attribute
        // to create the :OBSERVES relations
        String where = "WHERE c.Log = \""+logName+"\" AND c.Type = \""+typeName+"\" AND ";
        for(int i = 0; i < classTypes.size(); i++){
            where += "e.`"+classTypes.get(i)+"` = c.`"+classTypes.get(i)+"`";
            if(i == classTypes.size()-1){
                where += "\n";
            }else{
                where += " AND ";
            }
        }
        String relateClassToEvents = "MATCH (l:Log{ID:\""+logName+"\"})--(e:Event)\n" +
                                        "MATCH (c:Class) " + where + "MERGE (e)-[:OBSERVES]->(c)";

        String createDF_C = "MATCH (c1:Class)<-[:OBSERVES]-(e1:Event)-[df:DF]->(e2:Event)-[:OBSERVES]->(c2:Class)\n" +
                "MATCH (e1)-[:CORR]->(n)<-[:CORR]-(e2)\n" +
                "MATCH (e1)--(l:Log)--(e2) WHERE l.ID = \""+logName+"\"\n"+
                "AND n.EntityType = \""+dfEntity+"\" AND df.EntityType = \""+dfEntity+"\" AND c1.Type = \""+typeName+"\" AND c2.Type=\""+typeName+"\"\n" +
                "WITH n.EntityType AS EType, c1, COUNT(df) AS df_freq, c2\n" +
                "MERGE (c1)-[rel2:DF_C{EntityType:EType}]->(c2) ON CREATE SET rel2.count=df_freq";

        String setEventAttributes = "MATCH (l:Log{ID:\""+logName+"\"})--(e:Event)--(c:Class)\n" +
                "WHERE c.Type = \""+typeName+"\"\n" +
                "WITH DISTINCT e,c\n" +
                "SET e.`"+typeName+"` = c.ID";

        System.out.println("createClassNodes = " + createClassNodes);
        System.out.println("relateClassToEvents = " + relateClassToEvents);
        System.out.println("createDF_C = " + createDF_C);
        System.out.println("setEventAttributes = " + setEventAttributes);

        try ( Session session = driver.session() ) {
            session.run(createClassNodes);
            session.run(relateClassToEvents);
            session.run(createDF_C);
            if(classTypes.size() > 1) session.run(setEventAttributes);
        }
    }

    //------------------ GRAPHSTREAM VISUALIZATION QUERIES -----------------------//
    public List<Node> getEventsSample(String logName){
        List<Record> queryAnswer;
        String getEvents = "MATCH (e:Event)--(l:Log) " +
                "WHERE l.ID = \""+logName+"\" " +
                "RETURN e AS Event LIMIT " + Utils.EVENT_SAMPLE_SIZE;

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getEvents);
                return result.list();
            });
        }

        List<Node> eventNodes = new ArrayList<>();
        for(Record r : queryAnswer){
            eventNodes.add(r.get("Event").asNode());
        }

        return eventNodes;
    }

    public Utils.Instance getDFNodes(String logName, List<String> entities){
        List<Record> queryAnswer;
        String listEntities = "['"+String.join("','",entities)+"']";

        // We retrieve the complete list of Event nodes and their :DF for each Entity node
        // We also retrieve the :CORR relation between the Entity node and the first Event node in the :DF path
        String getDFNodes = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)-[corr:CORR]-(n:Entity)\n" +
                "WHERE n.EntityType IN "+listEntities+" AND NOT EXISTS (()-[:DF{EntityType:n.EntityType}]->(e))\n" +
                "WITH n,corr\n" +
                "MATCH (n)--(e:Event)\n" +
                "MATCH (n)--(:Event)-[df:DF{EntityType:n.EntityType}]-(:Event)\n" +
                "RETURN n AS Entity,corr AS CORR,COLLECT(DISTINCT e) AS Events,COLLECT(DISTINCT df) AS DF\n" +
                "ORDER BY n.EntityType,n.ID";

        try ( Session session = driver.session() ) {
            queryAnswer = session.readTransaction(tx -> {
                Result result = tx.run(getDFNodes);
                return result.list();
            });
        }

        int numNodes = 0;
        String entityType = "";
        boolean addNodes = true;

        // We only display a sample of the Entity Type paths, but we make sure that the samples represent full paths.
        // Therefore, we include all the Event nodes for each Entity node until the total amount of nodes goes above the threshold.
        List<Node> entityNodes = new ArrayList<>();
        List<Relationship> e_ENEdges = new ArrayList<>();
        List<Node> eventNodes = new ArrayList<>();
        List<Relationship> dfEdges = new ArrayList<>();
        for(Record r : queryAnswer){
            Node n = r.get("Entity").asNode();
            String eT = n.get("EntityType").asString();
            if(!entityType.equals(eT)){
                entityType = eT;
                numNodes = 0;
                addNodes = true;
            }

            if(addNodes){
                entityNodes.add(n);
                e_ENEdges.add(r.get("CORR").asRelationship());

                List<Object> objEvents = r.get("Events").asList();
                for(Object o : objEvents){
                    eventNodes.add((Node) o);
                }
                List<Object> objDFs = r.get("DF").asList();
                for(Object o : objDFs){
                    dfEdges.add((Relationship) o);
                }

                numNodes += objEvents.size();
                if(numNodes > Utils.ENTITY_SAMPLE_SIZE) addNodes = false;
            }
        }

        return new Utils.Instance(entityNodes,e_ENEdges,eventNodes,dfEdges);
    }

    public Utils.Instance getECNodes(String logName, List<String> classes){
        List<Record> queryAnswer;
        String listClasses = "['"+String.join("','",classes)+"']";
        String getECNodes = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)-[obs:OBSERVES]-(c:Class)\n" +
                "WHERE c.Type IN "+listClasses+"\n" +
                "RETURN c AS Class,COLLECT([e,obs]) AS Event_OBS\n" +
                "ORDER BY c.Type,c.ID";

        try ( Session session = driver.session() ) {
            queryAnswer = session.readTransaction(tx -> {
                Result result = tx.run(getECNodes);
                return result.list();
            });
        }

        // For every Class node, we only return a maximum of CLASS_SAMPLE_SIZE Event nodes
        // to provide a better visualization in the UI
        List<Node> classNodes = new ArrayList<>();
        List<Relationship> e_CEdges = new ArrayList<>();
        List<Node> eventNodes = new ArrayList<>();
        for(Record r : queryAnswer){
            classNodes.add(r.get("Class").asNode());

            int numNodes = 0;
            List<Object> objEvent_EC = r.get("Event_OBS").asList();
            for(Object o : objEvent_EC){
                List<Object> listEvent_EC = new ArrayList<>((Collection<Object>) o);
                Node event = (Node) listEvent_EC.get(0);
                Relationship e_C = (Relationship) listEvent_EC.get(1);

                eventNodes.add(event);
                e_CEdges.add(e_C);

                numNodes++;
                if(numNodes == Utils.CLASS_SAMPLE_SIZE) break;
            }
        }

        return new Utils.Instance(classNodes,e_CEdges,eventNodes,null);
    }

    public List<Node> getSelectedEntities(String logName, List<String> entities){
        List<Record> queryAnswer;
        String listEntities = "['"+String.join("','",entities)+"']";
        String getEntities = "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(en:Entity) " +
                "WHERE en.EntityType IN " + listEntities + " " +
                "RETURN DISTINCT en AS Entity";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getEntities);
                return result.list();
            });
        }

        List<Node> entityNodes = new ArrayList<>();
        for(Record r : queryAnswer){
            entityNodes.add(r.get("Entity").asNode());
        }

        return entityNodes;
    }

    public List<Node> getSelectedClasses(String logName, List<String> classes){
        List<Record> queryAnswer;
        String listClasses = "['"+String.join("','",classes)+"']";
        String getClasses = "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(c:Class) " +
                "WHERE c.Type IN " + listClasses + " " +
                "RETURN DISTINCT c AS Class";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getClasses);
                return result.list();
            });
        }

        List<Node> classNodes = new ArrayList<>();
        for(Record r : queryAnswer){
            classNodes.add(r.get("Class").asNode());
        }

        return classNodes;
    }

    public Map<Integer,String> getE_EN_EntityType(String logName, List<String> entities){
        List<Record> queryAnswer;
        String listEntities = "['"+String.join("','",entities)+"']";
        String getE_EN_EntityType = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)-[corr:CORR]-(n:Entity)\n" +
                "WHERE n.EntityType IN "+listEntities+" AND NOT EXISTS (()-[:DF{EntityType:n.EntityType}]->(e))\n" +
                "RETURN ID(corr) AS CORR_ID, n.EntityType AS EntityType";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getE_EN_EntityType);
                return result.list();
            });
        }

        Map<Integer,String> e_EN_EntityTypes = new HashMap<>();
        for(Record r : queryAnswer){
            e_EN_EntityTypes.put(r.get("CORR_ID").asInt(),r.get("EntityType").asString());
        }

        return e_EN_EntityTypes;
    }

    public Map<Integer,String> getE_C_Type(String logName, List<String> classes){
        List<Record> queryAnswer;
        String listClasses = "['"+String.join("','",classes)+"']";
        String getE_C_Type = "MATCH (l:Log{ID:'"+logName+"'})--(e:Event)-[obs:OBSERVES]-(c:Class)\n" +
                "WHERE c.Type IN "+listClasses+"\n" +
                "RETURN ID(obs) AS OBS_ID,c.Type AS Type";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getE_C_Type);
                return result.list();
            });
        }

        Map<Integer,String> e_C_Type = new HashMap<>();
        for(Record r : queryAnswer){
            e_C_Type.put(r.get("OBS_ID").asInt(),r.get("Type").asString());
        }

        return e_C_Type;
    }

    public List<Record> getRelEdges(String logName, List<String> entities){
        List<Record> queryAnswer;
        String listEntities = "['"+String.join("','",entities)+"']";
        String getRelEdges = "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(n:Entity)-[r]-(:Entity)\n" +
                                "WHERE n.EntityType IN " + listEntities + "\n" +
                                "RETURN DISTINCT r AS REL";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getRelEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getDF_CEdges(String logName, List<String> dFCs){
        List<Record> queryAnswer;
        String listDFCs = "['"+String.join("','",dFCs)+"']";
        String getDF_CEdges = "MATCH (l:Log{ID:'"+logName+"'})--(:Event)--(:Class)-[df_c]-(:Class)\n" +
                "WHERE df_c.EntityType IN " + listDFCs + "\n" +
                "RETURN DISTINCT df_c AS DF_C";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getDF_CEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getModelNodes(String modelName){
        List<Record> queryAnswer;
        String getModelNodes = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS]->(mn:Model_node)\n" +
                                "RETURN DISTINCT mn AS MNode";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getModelNodes);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getModelEdges(String modelName){
        List<Record> queryAnswer;
        String getModelEdges = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS]->(:Model_node)-[me]-(:Model_node)\n" +
                                "RETURN DISTINCT me AS MODEL_EDGE";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getModelEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getPTNodes(String modelName){
        List<Record> queryAnswer;
        String getPTNodes = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS]->(p:Model_node)-[:MODEL_EDGE*]->(c)\n" +
                "WITH COLLECT(DISTINCT p)+COLLECT(DISTINCT c) AS nodes\n" +
                "UNWIND nodes AS PT_Nodes\n" +
                "RETURN DISTINCT PT_Nodes";

        System.out.println(getPTNodes);

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getPTNodes);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getChildEdges(String modelName){
        List<Record> queryAnswer;
        String getChildEdges = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS]->(p:Model_node)-[c:MODEL_EDGE*]- (n)\n" +
                "WITH DISTINCT c AS edges\n" +
                "UNWIND edges AS Child\n" +
                "RETURN DISTINCT Child";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getChildEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getSeqEdges(String modelName){
        List<Record> queryAnswer;
        String getSeqEdges = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS]->(p:Model_node)-[c:PT_SEQ*]- (n)\n" +
                "WITH DISTINCT c AS edges\n" +
                "UNWIND edges AS Seq\n" +
                "RETURN DISTINCT Seq";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getSeqEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getPetriNetNodes(String modelName){
        List<Record> queryAnswer;
        String getPNNodes = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS_PN]->(p:PetriNet)\n" +
                "WITH COLLECT(DISTINCT p) AS nodes\n" +
                "UNWIND nodes AS PN_Nodes\n" +
                "RETURN DISTINCT PN_Nodes";
        System.out.println(getPNNodes);
        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getPNNodes);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getPetriNetEdges(String modelName){
        List<Record> queryAnswer;
        String getPNEdges = "MATCH (m:Model{ID:\""+modelName+"\"})-[:CONTAINS_PN]->(:PetriNet)-[p:MODEL_EDGE]->(n)\n" +
                "WITH DISTINCT p AS edges\n" +
                "UNWIND edges AS PN\n" +
                "RETURN DISTINCT PN";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getPNEdges);
                return result.list();
            });
        }

        return queryAnswer;
    }

    public List<Record> getSharedActivities(String modelName1, String modelName2){
        List<Record> queryAnswer;
        String getSharedActs = "MATCH (:Model{ID:\""+modelName1+"\"})-[:CONTAINS_PN]->(:PetriNet)-[:MODEL_EDGE*]->(p)\n" +
                "MATCH (:Model{ID:\""+modelName2+"\"})-[:CONTAINS_PN]->(:PetriNet)-[:MODEL_EDGE*]->(q)\n" +
                "WHERE p.t = q.t\n" +
                "RETURN DISTINCT ID(p) AS m1Node, ID(q) AS m2Node";

        try ( Session session = driver.session() ) {
            queryAnswer = session.writeTransaction(tx -> {
                Result result = tx.run(getSharedActs);
                return result.list();
            });
        }

        return queryAnswer;
    }
}

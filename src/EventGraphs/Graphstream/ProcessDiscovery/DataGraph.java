package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseManager;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.graphstream.ui.view.util.InteractiveElement;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Relationship;

import java.util.*;
import java.util.stream.Stream;

public class DataGraph extends GSGraphBuilder implements ViewerListener {
    HBox hbProperties;
    Label labelNodeType;
    TableView<Utils.LogAttribute> tvProperties;

    String logName;

    public DataGraph(HBox hb, Label l, TableView<Utils.LogAttribute> tv, N4JQueries n4JQueries, String logName) {
        super(n4JQueries);
        this.hbProperties = hb;
        this.labelNodeType = l;
        this.tvProperties = tv;
        this.logName = logName;

        t = new Timer();

        graph.setAttribute("ui.stylesheet", "url(file:"+ Utils.GRAPH_STYLE_FILE+")");
        configureGraphPane();
    }

    public void configureGraphPane(){
        FxViewer viewer = new FxViewer(graph, FxViewer.ThreadingModel.GRAPH_IN_GUI_THREAD);
        viewer.disableAutoLayout();

        FxViewPanel panel = (FxViewPanel) viewer.addDefaultView( false );
        setMouseEvents(panel);
        viewer.getDefaultView().setMouseManager(new FxMouseManager(EnumSet.of(InteractiveElement.EDGE, InteractiveElement.NODE, InteractiveElement.SPRITE)));

        ViewerPipe pipe = viewer.newViewerPipe();
        pipe.addAttributeSink(graph);

        graph.setAttribute("ui.antialias");

        pipe.addViewerListener(this);

        graphPane.getChildren().addAll(panel);

        t.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(pipe::pump);
                    }
                },0,200);
    }

    public void buildModelGraph(){
    }

    public void addInstances(boolean addDF, Map<String,Boolean> logDF, boolean addEC, Map<String,Boolean> logEC){
        int dfCount = 0;
        int ecCount = 0;
        for(boolean sel : logDF.values()) if(sel) dfCount++;
        for(boolean sel : logEC.values()) if(sel) ecCount++;

        if((!addDF && !addEC) ||
                (dfCount == 0 && !addEC) ||
                (ecCount == 0 && !addDF)){
            List<org.neo4j.driver.types.Node> eventNodes = n4JQueries.getEventsSample(logName);
            createEventNodes(eventNodes);
            arrangeNodesSugiyama(graph,0,false);
            return;
        }

        List<org.neo4j.driver.types.Node> eventNodes = new ArrayList<>();
        List<org.neo4j.driver.types.Node> entityNodes = new ArrayList<>();
        List<org.neo4j.driver.types.Node> classNodes = new ArrayList<>();
        List<Relationship> e_ENEdges = new ArrayList<>();
        List<Relationship> dfEdges = new ArrayList<>();
        List<Relationship> ecEdges = new ArrayList<>();
        Map<Integer,String> e_EN_EntityTypes = new HashMap<>();
        Map<Integer,String> e_C_Types = new HashMap<>();

        if(addDF){
            // If the user check the :DF/:CORR box, we retrieve all the corresponding Event and Entity nodes
            // together with the Events :DF relations and the :CORR relation between the Entity node and the first Event node
            // in the entity type path
            List<String> selectedEntities = new ArrayList<>();
            for(String en : logDF.keySet()) {
                if(logDF.get(en)) selectedEntities.add(en);
            }

            Utils.Instance instance = n4JQueries.getDFNodes(logName,selectedEntities);
            e_EN_EntityTypes = n4JQueries.getE_EN_EntityType(logName,selectedEntities);
            entityNodes = new ArrayList<>(new HashSet<>(instance.getTypeNodes())); //Remove duplicates
            e_ENEdges = instance.getTypeRelations();
            dfEdges = new ArrayList<>(new HashSet<>(instance.getEventRelations())); //Remove duplicates
            eventNodes.addAll(instance.getEventNodes());
        }

        if(addEC){
            // If the user checks the :OBSERVES box, we retrieve the corresponding Event and Class nodes,
            // together with their :OBSERVES relations
            List<String> selectedClasses = new ArrayList<>();
            for(String c : logEC.keySet()) {
                if(logEC.get(c)) selectedClasses.add(c);
            }

            Utils.Instance instance = n4JQueries.getECNodes(logName,selectedClasses);
            e_C_Types = n4JQueries.getE_C_Type(logName,selectedClasses);
            classNodes = new ArrayList<>(new HashSet<>(instance.getTypeNodes())); //Remove duplicates
            ecEdges = instance.getTypeRelations();
            eventNodes.addAll(instance.getEventNodes());
        }

        eventNodes = new ArrayList<>(new HashSet<>(eventNodes)); //Remove duplicates
        createEventNodes(eventNodes);

        if(addDF){
            createEntityNodes(entityNodes);
            createDFEdges(dfEdges);
            createE_ENEdges(e_ENEdges,e_EN_EntityTypes);
        }

        if(addEC){
            createClassNodes(classNodes);
            createE_CEdges(ecEdges,e_C_Types);
        }

        arrangeNodesSugiyama(graph,0,false);
    }

    public void addEntitiesAndClasses(Map<String,Boolean> logEntities, Map<String,Boolean> logClasses){
        List<String> selectedEntities = new ArrayList<>();
        for(String en : logEntities.keySet()) {
            if(logEntities.get(en)) selectedEntities.add(en);
        }
        List<org.neo4j.driver.types.Node> entities = n4JQueries.getSelectedEntities(logName,selectedEntities);
        createEntityNodes(entities);
        createRELEdges(selectedEntities);
        int offset = arrangeNodesSugiyama(graph,0,false);


        List<String> selectedClasses = new ArrayList<>();
        List<String> selectedDFCs = new ArrayList<>();
        for(String c : logClasses.keySet()) {
            if(logClasses.get(c)){
                String[] arrClass = c.split(",");
                selectedClasses.add(arrClass[0]);
                selectedDFCs.add(arrClass[1]);
            }
        }
        List<org.neo4j.driver.types.Node> classes = n4JQueries.getSelectedClasses(logName,selectedClasses);
        createClassNodes(classes);
        createDF_CEdges(selectedDFCs);

        // We define a new Graph instance called gModel so the Class nodes can be arranged independently of the arrangement that has
        // already been done for the Entity nodes. This is why we use a forEach statement to retrieve exclusively the Class nodes and
        // the :DF_C relations from the main graph.
        Graph gModel = new MultiGraph("Model");
        graph.nodes().forEach(n->{
            String gsClass = n.getAttribute("ui.class").toString();
            if(gsClass.contains("class")) gModel.addNode(n.getId());
        });
        graph.edges().forEach(e->{
            String gsLabel = e.getAttribute("ui.label").toString();
            if(gsLabel.contains(":DF_C")) gModel.addEdge(e.getId(),e.getSourceNode().getId(),e.getTargetNode().getId(),true);
        });
        arrangeNodesSugiyama(gModel,offset,false);
    }

    public void addEntitiesModel(Map<String,Boolean> logEntities){
        List<String> selectedEntities = new ArrayList<>();
        for(String en : logEntities.keySet()) {
            if(logEntities.get(en)) selectedEntities.add(en);
        }

        List<org.neo4j.driver.types.Node> entities = n4JQueries.getSelectedEntities(logName,selectedEntities);
        createEntityNodes(entities);
        createRELEdges(selectedEntities);

        arrangeNodesSugiyama(graph,0,false);
    }

    public void addClassesModel(Map<String,Boolean> logClasses){
        List<String> selectedClasses = new ArrayList<>();
        List<String> selectedDFCs = new ArrayList<>();
        for(String c : logClasses.keySet()) {
            if(logClasses.get(c)){
                String[] arrClass = c.split(",");
                selectedClasses.add(arrClass[0]);
                selectedDFCs.add(arrClass[1]);
            }
        }
        List<org.neo4j.driver.types.Node> classes = n4JQueries.getSelectedClasses(logName,selectedClasses);
        createClassNodes(classes);
        createDF_CEdges(selectedDFCs);

        arrangeNodesSugiyama(graph,0,false);
    }

    private void createEventNodes(List<org.neo4j.driver.types.Node> eventNodes){
        for(org.neo4j.driver.types.Node n4jEventNode : eventNodes){
            String eventID = Long.toString(n4jEventNode.id());
            String activity = String.valueOf(n4jEventNode.get("Activity"));
            graph.addNode(eventID);

            Node gsEventNode = graph.getNode(eventID);
            gsEventNode.setAttributes(n4jEventNode.asMap());
            gsEventNode.setAttribute("ui.class", "event");
            gsEventNode.setAttribute("ui.label", activity);
            gsEventNode.setAttribute("layout.weight", 0.1);
        }
    }

    private void createEntityNodes(List<org.neo4j.driver.types.Node> entityNodes){
        if(entityNodes.size() == 0) return;

        int entityNum;
        for(org.neo4j.driver.types.Node n4jEntityNode : entityNodes) {
            String entityID = Long.toString(n4jEntityNode.id());
            graph.addNode(entityID);

            String entityType = String.valueOf(n4jEntityNode.get("EntityType")).replace("\"","");
            if(!nodeColor.containsKey(entityType)){
                entityNum = nodeColor.size()%8 + 1;
                nodeColor.put(entityType,entityNum);
            }else{
                entityNum = nodeColor.get(entityType);
            }

            Node gsEntityNode = graph.getNode(entityID);
            gsEntityNode.setAttributes(n4jEntityNode.asMap());
            gsEntityNode.setAttribute("ui.class", "entity,type"+entityNum);
            gsEntityNode.setAttribute("ui.label", n4jEntityNode.get("uID"));
            gsEntityNode.setAttribute("layout.weight",0.1);
        }
    }

    private void createClassNodes(List<org.neo4j.driver.types.Node> classNodes){
        if(classNodes.size() == 0) return;

        int classNum;
        for(org.neo4j.driver.types.Node n4jClassNode : classNodes) {
            String classID = Long.toString(n4jClassNode.id());
            graph.addNode(classID);

            String classType = String.valueOf(n4jClassNode.get("Type")).replace("\"","");
            if(!nodeColor.containsKey(classType)){
                classNum = nodeColor.size()%8 + 1;
                nodeColor.put(classType,classNum);
            }else{
                classNum = nodeColor.get(classType);
            }

            Node gsClassNode = graph.getNode(classID);
            gsClassNode.setAttributes(n4jClassNode.asMap());
            gsClassNode.setAttribute("ui.class", "class,type"+classNum);
            gsClassNode.setAttribute("ui.label", n4jClassNode.get("ID"));
            gsClassNode.setAttribute("layout.weight",0.1);
        }
    }

    private void createDFEdges(List<Relationship> dfEdges){
        if(dfEdges.size() == 0) return;

        for (Relationship n4jDFEdge : dfEdges) {
            String edgeID = Long.toString(n4jDFEdge.id());
            String entityType = String.valueOf(n4jDFEdge.get("EntityType")).replace("\"","");
            String fromNode = String.valueOf(n4jDFEdge.startNodeId());
            String toNode = String.valueOf(n4jDFEdge.endNodeId());

            graph.addEdge(edgeID, fromNode, toNode, true);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("EntityType", entityType);
            gsEdge.setAttribute("ui.label", ":DF");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "type" + nodeColor.get(entityType));
        }
    }

    private void createE_ENEdges(List<Relationship> e_ENEdges, Map<Integer,String> e_EN_EntityTypes){
        if(e_ENEdges.size() == 0) return;

        for (Relationship n4jE_ENEdge : e_ENEdges) {
            int id = (int) n4jE_ENEdge.id();
            String edgeID = String.valueOf(id);
            //Invert from/to nodes for better visual representation
            String toNode = String.valueOf(n4jE_ENEdge.startNodeId());
            String fromNode = String.valueOf(n4jE_ENEdge.endNodeId());
            String entityType = e_EN_EntityTypes.get(id);

            graph.addEdge(edgeID, fromNode, toNode);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("ui.label", ":E_EN");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "E_EN, type" + nodeColor.get(entityType));
        }
    }

    private void createRELEdges(List<String> selEntities){
        List<Record> relEdges = n4JQueries.getRelEdges(logName,selEntities);

        if(relEdges.size() != 0) {
            for (Record r : relEdges) {
                org.neo4j.driver.types.Relationship n4jRELEdge = r.get("REL").asRelationship();
                String edgeID = Long.toString(n4jRELEdge.id());
                String fromNode = String.valueOf(n4jRELEdge.startNodeId());
                String toNode = String.valueOf(n4jRELEdge.endNodeId());
                String type = String.valueOf(n4jRELEdge.get("Type")).replace("\"", "");
                if(type.equals("Reified")) type = "_R";
                else type = "";

                graph.addEdge(edgeID, fromNode, toNode);
                Edge gsEdge = graph.getEdge(edgeID);
                gsEdge.setAttribute("ui.label", ":REL");
                gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
                gsEdge.setAttribute("ui.class", "REL" + type);
            }
        }
    }

    private void createE_CEdges(List<Relationship> ecEdges, Map<Integer,String> e_C_Types){
        if(ecEdges.size() == 0) return;

        for (Relationship n4jE_CEdge : ecEdges) {
            int id = (int) n4jE_CEdge.id();
            String edgeID = String.valueOf(id);
            String fromNode = String.valueOf(n4jE_CEdge.startNodeId());
            String toNode = String.valueOf(n4jE_CEdge.endNodeId());
            String type = e_C_Types.get(id);

            graph.addEdge(edgeID, fromNode, toNode);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("ui.label", ":E_C");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "E_C, type" + nodeColor.get(type));
        }
    }

    private void createDF_CEdges(List<String> selDFCs){
        List<Record> df_cEdges = n4JQueries.getDF_CEdges(logName,selDFCs);

        if(df_cEdges.size() != 0) {
            for (Record r : df_cEdges) {
                org.neo4j.driver.types.Relationship n4jE_CEdge = r.get("DF_C").asRelationship();
                String edgeID = Long.toString(n4jE_CEdge.id());
                String fromNode = String.valueOf(n4jE_CEdge.startNodeId());
                String toNode = String.valueOf(n4jE_CEdge.endNodeId());
                String entityType = String.valueOf(n4jE_CEdge.get("EntityType")).replace("\"", "");

                if(!nodeColor.containsKey(entityType)){
                    int entityNum = nodeColor.size()%8 + 1;
                    nodeColor.put(entityType,entityNum);
                    addClassReference(entityType,entityNum);
                }

                graph.addEdge(edgeID, fromNode, toNode, true);
                Edge gsEdge = graph.getEdge(edgeID);
                gsEdge.setAttribute("ui.label", ":DF_C");
                gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
                gsEdge.setAttribute("ui.class", "DF_C, type" + nodeColor.get(entityType));
            }
        }
    }

    private void addClassReference(String entityType, int entityNum){
        // In this function we create virtual nodes to provide a visual description of what the :DF_C edge color between
        // Class nodes represents. This is only done when the Entity nodes that correspond to that :DF_C are not
        // included in the visualization.
        String nodeID_A = "a"+entityNum;
        String nodeID_B = "b"+entityNum;
        graph.addNode(nodeID_A);
        graph.addNode(nodeID_B);
        Node a = graph.getNode(nodeID_A);
        Node b = graph.getNode(nodeID_B);
        a.setAttribute("ui.class", "class");
        b.setAttribute("ui.class", "class");

        graph.addEdge(entityType,a,b,true);
        Edge e = graph.getEdge(entityType);
        e.setAttribute("ui.label", ":DF_C\nEntityType: "+entityType);
        e.setAttribute("ui.class", "DF_C, type" + nodeColor.get(entityType));
    }

    public void buttonPushed(String id) {
    }

    public void buttonReleased(String id) {
        Node gsNode = graph.getNode(id);

        String[] nodeClasses = String.valueOf(gsNode.getAttribute("ui.class")).split(",");
        String type = nodeClasses[0];
        String color = "#FFFFFF";
        String typeLabel = "";
        if(type.contains("event")){
            color = Utils.EVENT_NODE_COLOR;
            typeLabel = "EVENT";
        }else if(type.contains("entity")){
            int typeNum = Integer.parseInt(nodeClasses[1].split("e")[1]); //Split "type1" into "typ" and "1". Then, turn 1 into Integer
            color = Utils.ENTITY_COLORS[typeNum-1];
            typeLabel = "ENTITY";
        }else if(type.contains("class") && nodeClasses.length > 1){ //Nodes that indicate the EntityType do not have more than one class
            int typeNum = Integer.parseInt(nodeClasses[1].split("e")[1]); //Split "type1" into "typ" and "1". Then, turn 1 into Integer
            color = Utils.ENTITY_COLORS[typeNum-1];
            typeLabel = "CLASS";
        }

        hbProperties.setStyle("-fx-border-style: solid inside;" + "-fx-border-width: 2;" +
                "-fx-border-color: "+color+";" + "-fx-background-color: #FFFFFF;");
        labelNodeType.setText(typeLabel);

        tvProperties.getItems().clear();

        Stream<String> attrKeys = gsNode.attributeKeys();
        attrKeys.forEach(attr -> {
            if(attr.contains("ui.") || attr.contains("layout.") || attr.contains("xyz")) return;

            tvProperties.getItems().add(new Utils.LogAttribute(attr,gsNode.getAttribute(attr).toString()));
        });
        tvProperties.sort();
    }

    public void mouseOver(String id) {
    }

    public void mouseLeft(String id) {
    }

    public void viewClosed(String viewName) {
    }
}

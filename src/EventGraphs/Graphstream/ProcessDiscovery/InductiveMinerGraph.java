package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.application.Platform;
import javafx.scene.control.TableView;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseManager;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.graphstream.ui.view.util.InteractiveElement;
import org.neo4j.driver.Record;

import java.util.*;

public class InductiveMinerGraph extends GSGraphBuilder implements ViewerListener {
    String modelName;
    TableView<Utils.LogAttribute> tvModelDetails;

    public InductiveMinerGraph(N4JQueries n4JQueries, String modelName, TableView<Utils.LogAttribute> tvModelDetails) {
        super(n4JQueries);
        this.modelName = modelName;
        this.tvModelDetails = tvModelDetails;

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
        createModelNodes();
        createChildEdges();
        arrangeNodesSugiyama(graph,0,false, true);
        createSeqEdges();

    }

    private void createModelNodes(){
        List<Record> ptNodes = n4JQueries.getPTNodes(modelName);
        Map<String, String> typeChars = new HashMap<>();
        typeChars.put("Parallel", "\u2227");
        typeChars.put("Sequence", "\u2192");
        typeChars.put("Exclusive", "X");
        typeChars.put("Loop", "\u21BB");

        System.out.println(typeChars.toString());

        int modelNum;
        for(Record r : ptNodes) {
            org.neo4j.driver.types.Node n4jPTNode = r.get("PT_Nodes").asNode();
            String ptNodeID = Long.toString(n4jPTNode.id());
            String search = "\nSearch: "+n4jPTNode.get("search").asBoolean();

            String type = "";
//            if(n4jPTNode.containsKey("type")) type = "\nType: "+ typeChars.get(n4jPTNode.get("type").asString().replace("\"",""));
            if(n4jPTNode.containsKey("type")) type = typeChars.get(n4jPTNode.get("type").asString().replace("\"","").replace(" ", ""));
            if(n4jPTNode.containsKey("ClassName")) {
                type = n4jPTNode.get("ClassName").asString().replace("\"","");
            }

            boolean main = false;
            if(n4jPTNode.containsKey("main")) main = n4jPTNode.get("main").asBoolean();

            graph.addNode(ptNodeID);

            if(!nodeColor.containsKey(modelName)){
                modelNum = nodeColor.size()%8 + 1;
                nodeColor.put(modelName,modelNum);
            }else{
                modelNum = nodeColor.get(modelName);
            }
            String nodeClass = "ptNode,type"+modelNum;
            if (main) {
                nodeClass += ",main";
            }
            
            Node gsModelActNode = graph.getNode(ptNodeID);
            gsModelActNode.setAttributes(n4jPTNode.asMap());
            gsModelActNode.setAttribute("ui.class", nodeClass);
            gsModelActNode.setAttribute("ui.label", type);
            gsModelActNode.setAttribute("layout.weight",0.1);

            if (n4jPTNode.containsKey("type")) {
                gsModelActNode.setAttribute("ui.style", "text-alignment: center;\n" +
                        "    text-size: 20;\n" +
                        "    text-color: #ffffff;");
            }
        }
    }

    private void createChildEdges(){
        List<Record> childEdges = n4JQueries.getChildEdges(modelName);

        for (Record r : childEdges) {
            org.neo4j.driver.types.Relationship n4jChildEdge = r.get("Child").asRelationship();
            String edgeID = Long.toString(n4jChildEdge.id());
            String fromNode = String.valueOf(n4jChildEdge.startNodeId());
            String toNode = String.valueOf(n4jChildEdge.endNodeId());

            graph.addEdge(edgeID, fromNode, toNode, true);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("ui.label", ":MODEL_EDGE");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "DF_M");
        }
    }

    private void createSeqEdges(){
        List<Record> seqEdges = n4JQueries.getSeqEdges(modelName);

        for (Record r : seqEdges) {
            org.neo4j.driver.types.Relationship n4jChildEdge = r.get("Seq").asRelationship();
            String edgeID = Long.toString(n4jChildEdge.id());
            String fromNode = String.valueOf(n4jChildEdge.startNodeId());
            String toNode = String.valueOf(n4jChildEdge.endNodeId());

            graph.addEdge(edgeID, fromNode, toNode, true);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("ui.label", ":SEQ");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "PT_SEQ");
            gsEdge.setAttribute("ui.style", "stroke-mode: dashes; size: 0px;");
        }
    }

    public void buttonPushed(String id) {
    }

    public void buttonReleased(String id) {
        tvModelDetails.getItems().clear();

        Node node = graph.getNode(id);
        String main;
        String name;
        String type;

        if (node.hasAttribute("main")) {
            main = node.getAttribute("main").toString();
            tvModelDetails.getItems().add(new Utils.LogAttribute("Main", main));
        }

        if (node.hasAttribute("ClassName")) {
            name = node.getAttribute("ClassName").toString();
            tvModelDetails.getItems().add(new Utils.LogAttribute("Class", name));
        }

        if (node.hasAttribute("type")) {
            type = node.getAttribute("type").toString();
            tvModelDetails.getItems().add(new Utils.LogAttribute("Cut type", type));
        }


        tvModelDetails.getItems().add(new Utils.LogAttribute("ID", node.getId()));


    }

    public void mouseOver(String id) {
    }

    public void mouseLeft(String id) {
    }

    public void viewClosed(String viewName) {
    }
}

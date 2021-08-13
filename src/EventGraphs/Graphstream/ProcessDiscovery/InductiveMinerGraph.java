package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseManager;
import org.graphstream.ui.view.ViewerPipe;
import org.graphstream.ui.view.util.InteractiveElement;
import org.neo4j.driver.Record;

import java.util.EnumSet;
import java.util.List;

public class InductiveMinerGraph extends GSGraphBuilder {
    String modelName;

    public InductiveMinerGraph(N4JQueries n4JQueries, String modelName) {
        super(n4JQueries);
        this.modelName = modelName;

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

        graphPane.getChildren().addAll(panel);
    }

    public void buildModelGraph(){
        createModelNodes();
        createChildEdges();
        arrangeNodesSugiyama(graph,0,false);
    }

    private void createModelNodes(){
        List<Record> ptNodes = n4JQueries.getPTNodes(modelName);

        int modelNum;
        for(Record r : ptNodes) {
            org.neo4j.driver.types.Node n4jPTNode = r.get("PT_Nodes").asNode();
            String ptNodeID = Long.toString(n4jPTNode.id());
            String search = "Search: "+n4jPTNode.get("Search").asBoolean();

            String type = "";
            if(n4jPTNode.containsKey("Type")) type = "\nType: "+n4jPTNode.get("Type").asString().replace("\"","");

            graph.addNode(ptNodeID);

            if(!nodeColor.containsKey(modelName)){
                modelNum = nodeColor.size()%8 + 1;
                nodeColor.put(modelName,modelNum);
            }else{
                modelNum = nodeColor.get(modelName);
            }
            String nodeClass = "ptNode,type"+modelNum;

            Node gsModelActNode = graph.getNode(ptNodeID);
            gsModelActNode.setAttributes(n4jPTNode.asMap());
            gsModelActNode.setAttribute("ui.class", nodeClass);
            gsModelActNode.setAttribute("ui.label", search+type);
            gsModelActNode.setAttribute("layout.weight",0.1);
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
            gsEdge.setAttribute("ui.label", ":Child");
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "DF_M");
        }
    }
}

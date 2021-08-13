package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.application.Platform;
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

import java.util.*;

public class ModelComparison extends GSGraphBuilder implements ViewerListener {
    Map<String,List<String>> sharedActs, sharedEdges;
    String currentNodeSel, modelName1, modelName2;

    public ModelComparison(N4JQueries n4JQueries, String modelName1, String modelName2) {
        super(n4JQueries);
        this.modelName1 = modelName1;
        this.modelName2 = modelName2;

        sharedActs = new HashMap<>();
        sharedEdges = new HashMap<>();
        currentNodeSel = "";

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
        createPNNodes(modelName1,2);
        createPNEdges(modelName1);
        int offset = arrangeNodesSugiyama(graph,0,true);

        createPNNodes(modelName2,4);
        createPNEdges(modelName2);

        // We define a new Graph instance so the Petri net nodes of the second model can be arranged
        // independently of the arrangement already done for the Petri net nodes of the first model
        Graph gModel2 = new MultiGraph("Model2");
        List<Record> petriNetNodes = n4JQueries.getPetriNetNodes(modelName2);
        for(Record r : petriNetNodes) {
            org.neo4j.driver.types.Node n4jPetriNetNode = r.get("PN_Nodes").asNode();
            String pnNodeID = Long.toString(n4jPetriNetNode.id());
            gModel2.addNode(pnNodeID);
        }
        List<Record> petriNetEdges = n4JQueries.getPetriNetEdges(modelName2);
        for (Record r : petriNetEdges) {
            org.neo4j.driver.types.Relationship n4jPNEdge = r.get("PN").asRelationship();
            String edgeID = Long.toString(n4jPNEdge.id());
            String fromNode = String.valueOf(n4jPNEdge.startNodeId());
            String toNode = String.valueOf(n4jPNEdge.endNodeId());

            gModel2.addEdge(edgeID, fromNode, toNode, true);
        }

        arrangeNodesSugiyama(gModel2,offset,true);

        findSharedActivities(modelName1,modelName2);
    }

    private void findSharedActivities(String modelName1, String modelName2){
        List<Record> petriNetIDs = n4JQueries.getSharedActivities(modelName1,modelName2);
        int edgeID = -1;

        for(Record r : petriNetIDs) {
            String m1NodeID = String.valueOf(r.get("m1Node").asInt());
            String m2NodeID = String.valueOf(r.get("m2Node").asInt());

            // We assign the same color to each pair of nodes that represent the same transition
            replaceNodeColorClass(m1NodeID,2,1);
            replaceNodeColorClass(m2NodeID,4,1);

            // We create a new virtual edge between these two nodes
            String strEdgeID = String.valueOf(edgeID);
            graph.addEdge(strEdgeID, m1NodeID, m2NodeID, false);
            Edge gsEdge = graph.getEdge(strEdgeID);
            gsEdge.setAttribute("ui.class", "PNComp");
            edgeID--;

            // We add a double reference between the nodes and edges so we know later which edges and
            // nodes to highlight in the visualization independently of which node is selected
            if(!sharedActs.containsKey(m1NodeID))
                sharedActs.put(m1NodeID,new ArrayList<>());
            sharedActs.get(m1NodeID).add(m2NodeID);

            if(!sharedActs.containsKey(m2NodeID))
                sharedActs.put(m2NodeID,new ArrayList<>());
            sharedActs.get(m2NodeID).add(m1NodeID);

            if(!sharedEdges.containsKey(m1NodeID))
                sharedEdges.put(m1NodeID,new ArrayList<>());
            sharedEdges.get(m1NodeID).add(strEdgeID);

            if(!sharedEdges.containsKey(m2NodeID))
                sharedEdges.put(m2NodeID,new ArrayList<>());
            sharedEdges.get(m2NodeID).add(strEdgeID);
        }
    }

    private void replaceNodeColorClass(String nodeID, int colNumOld, int colNumNew){
        Node gsNode = graph.getNode(nodeID);
        String nodeClass = gsNode.getAttribute("ui.class").toString();
        nodeClass = nodeClass.replace("type"+colNumOld,"type"+colNumNew);
        gsNode.setAttribute("ui.class", nodeClass);
    }

    private void replaceEdgeColorClass(String edgeID, String type){
        Edge gsEdge = graph.getEdge(edgeID);
        gsEdge.setAttribute("ui.class", "PNComp"+type);
    }

    public void buttonPushed(String id) {
    }

    public void buttonReleased(String id) {
        String m1Node;

        if(sharedActs.containsKey(id)){
            if(!currentNodeSel.equals("")){
                //Replace highlight color for regular color for previously selected nodes
                m1Node = currentNodeSel;
                replaceNodeColorClass(m1Node,3,1);

                for(String m2Node : sharedActs.get(currentNodeSel)){
                    replaceNodeColorClass(m2Node,3,1);

                    for(String edge : sharedEdges.get(currentNodeSel)){
                        replaceEdgeColorClass(edge,"");
                    }
                }
            }

            //Replace regular color for highlight color for currently selected nodes
            m1Node = id;
            replaceNodeColorClass(m1Node,1,3);

            for(String m2Node : sharedActs.get(id)){
                replaceNodeColorClass(m2Node,1,3);

                for(String edge : sharedEdges.get(id)){
                    replaceEdgeColorClass(edge,"1");
                }
            }

            currentNodeSel = m1Node;
        }
    }

    public void mouseOver(String id) {
    }

    public void mouseLeft(String id) {
    }

    public void viewClosed(String viewName) {
    }
}

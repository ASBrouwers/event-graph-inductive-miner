package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Graphstream.Sugiyama;
import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.neo4j.driver.Record;

import java.util.*;

public abstract class GSGraphBuilder {
    Timer t;
    StackPane graphPane;
    Graph graph;
    Layout layout;
    MouseEvent lastDrag;
    Map<String,Integer> nodeColor;
    N4JQueries n4JQueries;

    public GSGraphBuilder(N4JQueries n4JQueries){
        this.n4JQueries = n4JQueries;

        graphPane = new StackPane();
        layout = new SpringBox();
        nodeColor = new HashMap<>();

        graph = new MultiGraph("MyGraph");
    }

    public abstract void configureGraphPane();
    public abstract void buildModelGraph();

    public StackPane getGraphPane(){
        return graphPane;
    }

    public void stopTimer(){
        t.cancel();
        t.purge();
    }

    public void setMouseEvents(FxViewPanel panel){
        //Zoom on graph by scrolling
        panel.setOnScroll(event -> {
            event.consume();
            if (event.getDeltaY() == 0) return;

            double scale = (event.getDeltaY() > 0) ? 0.9 : 1.1;
            double currentZoom = panel.getCamera().getViewPercent();
            panel.getCamera().setViewPercent(currentZoom*scale);
        });

        //Move around in graph by dragging the Right click
        panel.setOnMouseDragged(event ->{
            if(!event.isSecondaryButtonDown()) return;
            if(lastDrag!=null) {
                Point3 p1 = panel.getCamera().getViewCenter();
                Point3 p2 = panel.getCamera().transformGuToPx(p1.x, p1.y, 0);
                double xDelta = event.getX() - lastDrag.getX();//determine direction
                double yDelta = event.getY() - lastDrag.getY();//determine direction
                p2.x -= xDelta;
                p2.y -= yDelta;
                Point3 p3 = panel.getCamera().transformPxToGu(p2.x, p2.y);
                panel.getCamera().setViewCenter(p3.x, p3.y, 0);
            }
            lastDrag = event;
        });

        panel.setOnMousePressed(event -> {
            if(event.isSecondaryButtonDown()) this.lastDrag=null;
        });
    }

    private int arrangeNodesNet(Graph g){
        int nodePosX = 0;
        int nodePosY = 0;
        int numNodes = g.getNodeCount();
        int limit = (int)Math.round(Math.sqrt(numNodes*1.2)); // To display nodes in a rectangle-type shape

        limit *= 2;
        boolean oddRow = false;

        for(Node node : g) {
            node.setAttribute("xyz", nodePosX, nodePosY, 0);
            nodePosX += 2;
            if(nodePosX > limit){
                oddRow = !oddRow;
                if(oddRow) nodePosX = 1;
                else nodePosX = 0;
                nodePosY--;
            }
        }

        return nodePosY;
    }


    public int arrangeNodesSugiyama(Graph g, int offset, boolean isPetriNet) {
        return arrangeNodesSugiyama(g, offset, isPetriNet, false);
    }

    public int arrangeNodesSugiyama(Graph g, int offset, boolean isPetriNet, boolean vertical){
        int newOffset = offset;
        int vSpacing = 1;
        boolean removeCycles = true;

        if(isPetriNet) removeCycles = false;

        Sugiyama s = new Sugiyama(g, removeCycles);
        s.applySugiyamaAlgorithm();
        List<List<Node>> layers = s.getLayers();
        Map<String,Integer> nodeYPositions = s.getNodeYPositions();

        // If the Sugiyama algorithm only returns one layer, we define a new arrangement for the nodes,
        // so they are not all displayed in one vertical line
        if(layers.size() == 1){
            newOffset = arrangeNodesNet(g);
            newOffset--;
            return newOffset;
        }

        if(isPetriNet) {
            int width = layers.size();
            int maxLength = 0;
            for (List<Node> l : layers) {
                if (l.size() > maxLength) maxLength = l.size();
            }
            vSpacing = (int) (width*0.4/maxLength); //Adjust vertical spacing between nodes so they are spread throughout the panel
            if(vSpacing < 2) vSpacing = 2;
        }

        // The starting corrdinate is (X:0,Y:0). From there, the position moves to the right on the X-axis and
        // down in the Y-axis. We also keep track of the node with the lowest Y positioning to account for the offset
        // that could be needed to display a second graph below the current one.
        int x = 0;
        int prevY = 1 + offset;
        for(List<Node> layer : layers){
            for(Node n : layer){
                int y;
                try{
                    y = nodeYPositions.get(n.getId());
                }catch(NullPointerException e){
                    y = 0;
                }
                y = y + offset;
                if(y >= prevY) y = prevY - 1;
                prevY = y;

                Node graphNode = graph.getNode(n.getId());
                if(graphNode != null){
                    if (vertical) {
                        graphNode.setAttribute("xyz", y * vSpacing, -x, 0);
                    } else {
                        graphNode.setAttribute("xyz", x, y * vSpacing, 0);
                    }
                }
            }
            if(newOffset > prevY) newOffset = prevY;
            prevY = 1 + offset;
            x += 2;
        }

        newOffset -= 2;
        return newOffset;
    }

    public void createPNNodes(String modelName, int colorNum){
        List<Record> petriNetNodes = n4JQueries.getPetriNetNodes(modelName);

        for(Record r : petriNetNodes) {
            org.neo4j.driver.types.Node n4jPetriNetNode = r.get("PN_Nodes").asNode();
            String type = String.valueOf(n4jPetriNetNode.get("type")).replace("\"","");

            String tName = "";
            if(n4jPetriNetNode.containsKey("t")) tName = n4jPetriNetNode.get("t").asString().replace("\"","");

            String startEnd = "Start";
            if(n4jPetriNetNode.containsKey("isEnd")) startEnd = "End";

            String pnNodeID = Long.toString(n4jPetriNetNode.id());
            graph.addNode(pnNodeID);

            Node gsPetriNetNode = graph.getNode(pnNodeID);
            gsPetriNetNode.setAttributes(n4jPetriNetNode.asMap());
            gsPetriNetNode.setAttribute("layout.weight",0.1);

            String nodeClass = "";
            String label = "";
            switch(type){
                case "p": //Place
                    nodeClass += "pnPlace";
                    break;
                case "t": //Transition
                    nodeClass += "pnTransition,type"+colorNum;
                    if(tName.contains("+")) tName = tName.replace("+","+\n");
                    label = tName;
                    break;
                case "tau": //Tau transition
                    nodeClass += "pnTau";
                    break;
                default: //"s_e" (Start/End) nodes
                    nodeClass += "pnStartEnd";
                    label = startEnd;
                    if(startEnd.equals("Start")) label += "\n" + modelName;
                    break;
            }
            gsPetriNetNode.setAttribute("ui.class", nodeClass);
            gsPetriNetNode.setAttribute("ui.label", label);
        }
    }

    public void createPNEdges(String modelName){
        List<Record> petriNetEdges = n4JQueries.getPetriNetEdges(modelName);

        for (Record r : petriNetEdges) {
            org.neo4j.driver.types.Relationship n4jPNEdge = r.get("PN").asRelationship();
            String edgeID = Long.toString(n4jPNEdge.id());
            String fromNode = String.valueOf(n4jPNEdge.startNodeId());
            String toNode = String.valueOf(n4jPNEdge.endNodeId());

            graph.addEdge(edgeID, fromNode, toNode, true);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "PN");
        }
    }
}

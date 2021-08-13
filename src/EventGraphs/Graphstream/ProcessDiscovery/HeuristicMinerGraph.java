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

public class HeuristicMinerGraph extends GSGraphBuilder implements ViewerListener {
    String modelName;
    TableView<Utils.LogAttribute> tvModelDetails;

    public HeuristicMinerGraph(N4JQueries n4JQueries, String modelName, TableView<Utils.LogAttribute> tvModelDetails) {
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
        createModelEdges();
        arrangeNodesSugiyama(graph,0,false);
    }

    private void createModelNodes(){
        List<Record> modelActNodes = n4JQueries.getModelNodes(modelName);

        int modelNum;
        for(Record r : modelActNodes) {
            org.neo4j.driver.types.Node n4jModelActNode = r.get("MNode").asNode();
            String idAttr = n4jModelActNode.get("ID").asString().replace("\"","");
            if(idAttr.contains("+")) idAttr = idAttr.replace("+","+\n");
            String mActID = Long.toString(n4jModelActNode.id());
            graph.addNode(mActID);

            if(!nodeColor.containsKey(modelName)){
                modelNum = nodeColor.size()%8 + 1;
                nodeColor.put(modelName,modelNum);
            }else{
                modelNum = nodeColor.get(modelName);
            }
            String nodeClass = "modelNode,type"+modelNum;
            if(n4jModelActNode.containsKey("isStart")){
                nodeClass = "artificial";
                idAttr = "Start";
            }else if(n4jModelActNode.containsKey("isEnd")){
                nodeClass = "artificial";
                idAttr = "End";
            }

            Node gsModelActNode = graph.getNode(mActID);
            gsModelActNode.setAttributes(n4jModelActNode.asMap());
            gsModelActNode.setAttribute("ui.class", nodeClass);
            gsModelActNode.setAttribute("ui.label", idAttr);
            gsModelActNode.setAttribute("layout.weight",0.1);
        }
    }

    private void createModelEdges(){
        List<Record> df_mEdges = n4JQueries.getModelEdges(modelName);

        for (Record r : df_mEdges) {
            org.neo4j.driver.types.Relationship n4jModelEdge = r.get("MODEL_EDGE").asRelationship();
            String edgeID = Long.toString(n4jModelEdge.id());
            String fromNode = String.valueOf(n4jModelEdge.startNodeId());
            String toNode = String.valueOf(n4jModelEdge.endNodeId());

            graph.addEdge(edgeID, fromNode, toNode, true);
            Edge gsEdge = graph.getEdge(edgeID);
            gsEdge.setAttribute("layout.weight", Utils.EDGE_WEIGHT);
            gsEdge.setAttribute("ui.class", "MODEL_EDGE");
        }
    }

    public void buttonPushed(String id) {
    }

    public void buttonReleased(String id) {
        tvModelDetails.getItems().clear();

        Node n = graph.getNode(id);

        List<String> inputBindings = new ArrayList<>();
        List<String> outputBindings = new ArrayList<>();
        if(n.hasAttribute("InputBindings")){
            inputBindings = new ArrayList<>((Collection<String>)n.getAttribute("InputBindings"));
        }
        if(n.hasAttribute("OutputBindings")){
            outputBindings = new ArrayList<>((Collection<String>)n.getAttribute("OutputBindings"));
        }

        // For each input and output binding, we create a new entry in the TableView tvModelDetails
        // We replace the "|" character by a "," to improve its readability
        if(inputBindings.size() > 0) tvModelDetails.getItems().add(new Utils.LogAttribute("Input Bindings",""));
        for(String i : inputBindings){
            i = i.replace("|"," , ");
            tvModelDetails.getItems().add(new Utils.LogAttribute("",i));
        }

        if(outputBindings.size() > 0) tvModelDetails.getItems().add(new Utils.LogAttribute("Output Bindings",""));
        for(String i : outputBindings){
            i = i.replace("|"," , ");
            tvModelDetails.getItems().add(new Utils.LogAttribute("",i));
        }
    }

    public void mouseOver(String id) {
    }

    public void mouseLeft(String id) {
    }

    public void viewClosed(String viewName) {
    }
}

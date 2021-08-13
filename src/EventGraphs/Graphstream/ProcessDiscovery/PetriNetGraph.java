package EventGraphs.Graphstream.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.application.Platform;
import org.graphstream.ui.fx_viewer.FxViewPanel;
import org.graphstream.ui.fx_viewer.FxViewer;
import org.graphstream.ui.fx_viewer.util.FxMouseManager;
import org.graphstream.ui.view.ViewerListener;
import org.graphstream.ui.view.ViewerPipe;
import org.graphstream.ui.view.util.InteractiveElement;

import java.util.*;

public class PetriNetGraph extends GSGraphBuilder implements ViewerListener {
    String modelName;

    public PetriNetGraph(N4JQueries n4JQueries, String modelName) {
        super(n4JQueries);
        this.modelName = modelName;

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
        createPNNodes(modelName,1);
        createPNEdges(modelName);
        arrangeNodesSugiyama(graph,0,true);
    }

    public void buttonPushed(String id) {
    }

    public void buttonReleased(String id) {
    }

    public void mouseOver(String id) {
    }

    public void mouseLeft(String id) {
    }

    public void viewClosed(String viewName) {
    }
}

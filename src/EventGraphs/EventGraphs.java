package EventGraphs;

import EventGraphs.GUI.GUIMain;
import EventGraphs.GUI.Neo4jConnectionStatus;
import EventGraphs.Graphstream.GSConfig;

import EventGraphs.Neo4j.N4JConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class EventGraphs extends Application {
    public static void main(String[] args) {
        GSConfig.setSystemProperties();

        Application.launch(EventGraphs.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        N4JConfig n4jConfig = new N4JConfig();

        Neo4jConnectionStatus n4jConStatus = new Neo4jConnectionStatus();
        n4jConStatus.getStatusStage().setOnShown(event -> {
            try {
                n4jConfig.startConnection(n4jConStatus);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        n4jConStatus.getStatusStage().showAndWait();

        GUIMain guiMain = new GUIMain(n4jConfig.getDriver());

        primaryStage.setOnCloseRequest(windowEvent -> {
            n4jConfig.getDriver().close();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.setScene(guiMain.buildMainDisplay());
        primaryStage.setTitle("Process Mining on Event Graphs");
        primaryStage.show();
    }
}
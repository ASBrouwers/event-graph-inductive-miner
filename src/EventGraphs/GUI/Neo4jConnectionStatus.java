package EventGraphs.GUI;

import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class Neo4jConnectionStatus {
    Stage statusStage;
    TextArea taStatusUpdates;

    public Neo4jConnectionStatus(){
        statusStage = new Stage();
        statusStage.setTitle("Neo4j Connection Status");

        taStatusUpdates = new TextArea();
        taStatusUpdates.setPrefSize(500,300);
        taStatusUpdates.setEditable(false);
        taStatusUpdates.setWrapText(true);

        BorderPane border = new BorderPane();
        border.setCenter(taStatusUpdates);
        Scene scene = new Scene(border);
        statusStage.setScene(scene);
    }

    public Stage getStatusStage(){
        return this.statusStage;
    }

    public void appendStatusUpdate(String update){
        System.out.println(update);
        this.taStatusUpdates.appendText(update);
    }
}

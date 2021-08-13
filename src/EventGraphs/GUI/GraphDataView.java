package EventGraphs.GUI;

import EventGraphs.Utils;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphDataView {
    Label lbSelected;
    Map<String,Boolean> options;
    String windowMessage;
    Stage optionsStage;
    List<CheckBox> listCBOptions;

    public GraphDataView(String type, Map<String,Boolean> options, Label lbSelected){
        this.lbSelected = lbSelected;
        this.options = options;

        listCBOptions = new ArrayList<>();

        switch (type){
            case "DF":
                windowMessage = "Choose which :DF relations will be used to connect the Event nodes.";
                break;
            case "EC":
                windowMessage = "Choose which :E_C relations will be shown together with the Event nodes.";
                break;
            case "Entities":
                windowMessage = "Choose which Entity nodes will be shown.";
                break;
            case "Classes":
            default:
                windowMessage = "Choose which combination of Class nodes and :DF_C relations will be shown.";
                break;
        }
    }

    public void showEditWindow(){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop(windowMessage);
        vBox.getChildren().add(top);

        //--------- Middle -------------------
        VBox vbMiddle = new VBox();
        vbMiddle.setSpacing(10);
        vbMiddle.setPadding(new Insets(10,10,10,10));

        for(String opt : options.keySet()){
            CheckBox cb = new CheckBox(opt);
            cb.setSelected(options.get(opt));
            listCBOptions.add(cb);
            vbMiddle.getChildren().add(cb);
        }

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bApply = new Button("Apply");
        bApply.setPrefSize(100, 20);
        bApply.addEventHandler(MouseEvent.MOUSE_CLICKED,applyChanges());

        hBoxBottom.getChildren().add(bApply);

        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(vbMiddle);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        optionsStage = new Stage();
        optionsStage.setScene(scene);
        optionsStage.setTitle("Graph Data View: Display Options");
        optionsStage.showAndWait();
    }

    public EventHandler<Event> applyChanges(){
        return event -> {
            int numSelected = 0;
            for(CheckBox cb : listCBOptions){
                options.put(cb.getText(),cb.isSelected());
                if(cb.isSelected()) numSelected++;
            }
            lbSelected.setText("("+numSelected+"/"+listCBOptions.size()+")");
            optionsStage.close();
        };
    }
}

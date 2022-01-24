package EventGraphs.GUI.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Neo4j.ProcessDiscovery.HeuristicMiner;
import EventGraphs.Utils;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

public class HeuristicMinerUI extends ProcessModelUI {
    String logName;
    N4JQueries n4JQueries;
    Stage hmStage;
    ComboBox<String> comboClass, comboDF;
    Slider slFreq, slDep, slL1L, slL2L, slRel, slBin;
    CheckBox cbAdvOptions;
    Accordion accAdvOptions;
    Map<String,List<String>> existingClasses;

    public HeuristicMinerUI(N4JQueries n4JQueries, String logName){
        this.n4JQueries = n4JQueries;
        this.logName = logName;

        hmStage = new Stage();
        comboClass = new ComboBox<>();
        comboDF = new ComboBox<>();
        slFreq = new Slider(0, 1, 0.1);
        slDep = new Slider(0, 1, 0.9);
        slL1L = new Slider(0, 1, 0.9);
        slL2L = new Slider(0, 1, 0.9);
        slRel = new Slider(0, 1, 0.0);
        slBin = new Slider(0, 1, 0.1);
        cbAdvOptions = new CheckBox("Use Advanced Thresholds");
        accAdvOptions = new Accordion();

        existingClasses = new HashMap<>();
    }

    public void parameterSelection(){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Define the parameters needed to execute the Heuristic Miner algorithm:");
        vBox.getChildren().add(top);

        //--------- Center -------------------
        VBox vbCenter = new VBox();
        vbCenter.setPrefSize(350,250);
        vbCenter.setSpacing(10);
        vbCenter.setPadding(new Insets(15, 12, 15, 12));

        existingClasses = n4JQueries.getExistingClasses(logName);

        if(existingClasses.size() == 0){
            TextArea taMessage = new TextArea("\nA class must be created in order to generate a Process Model with the Heuristic Miner algorithm");
            taMessage.setEditable(false);
            taMessage.setWrapText(true);
            taMessage.setPrefWidth(100);

            vbCenter.getChildren().add(taMessage);
        }else{
            //--------- Center: Class/DF -------------------
            GridPane gpComboBox = new GridPane();
            gpComboBox.setHgap(10);
            gpComboBox.setVgap(10);

            List<String> classList = new ArrayList<>(existingClasses.keySet());
            Label lbClass = new Label("Class Type:");
            comboClass.setPrefWidth(200);
            comboClass.getItems().addAll(FXCollections.observableList(classList));
            comboClass.getSelectionModel().select(0);
            comboClass.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null) {
                    updateComboBox(t1);
                }
            });

            Label lbDF = new Label("Entity Type:");
            comboDF.setPrefWidth(200);
            comboDF.getItems().addAll(FXCollections.observableList(existingClasses.get(classList.get(0))));
            comboDF.getSelectionModel().select(0);

            gpComboBox.add(lbClass,0,0);
            gpComboBox.add(comboClass,1,0);
            gpComboBox.add(lbDF,0,1);
            gpComboBox.add(comboDF,1,1);

            //--------- Center: Basic Thresholds -------------------
            Separator sep1 = new Separator();

            HBox hbLabelThr = new HBox();
            hbLabelThr.setAlignment(Pos.CENTER);
            Label lbThresholds = new Label("Thresholds");
            hbLabelThr.getChildren().add(lbThresholds);

            GridPane gpCenter = new GridPane();
            gpCenter.setHgap(10);
            gpCenter.setVgap(10);

            Label lbFreq = new Label("Frequency:");
            Label lbFreqVal = new Label("0.1");
            lbFreqVal.textProperty().bind(Bindings.format("%.2f",slFreq.valueProperty()));

            Label lbDep = new Label("Dependency:");
            Label lbDepVal = new Label("0.9");
            lbDepVal.textProperty().bind(Bindings.format("%.2f",slDep.valueProperty()));

            gpCenter.add(lbFreq,0,0);
            gpCenter.add(slFreq,1,0);
            gpCenter.add(lbFreqVal,2,0);

            gpCenter.add(lbDep,0,1);
            gpCenter.add(slDep,1,1);
            gpCenter.add(lbDepVal,2,1);

            gpCenter.getColumnConstraints().add(new ColumnConstraints());
            ColumnConstraints colBasic = new ColumnConstraints();
            colBasic.setHgrow(Priority.ALWAYS);
            gpCenter.getColumnConstraints().add(colBasic);

            //--------- Center: Advanced Options -------------------
            VBox vbAdvConfig = new VBox();
            vbAdvConfig.setSpacing(10);

            cbAdvOptions.setSelected(false);
            cbAdvOptions.setOnAction(e -> enableAdvOptions());

            GridPane gpAdvOptions = new GridPane();
            gpAdvOptions.setHgap(10);
            gpAdvOptions.setVgap(10);

            Label lbL1L = new Label("Length-1 Loop:");
            Label lbL1LVal = new Label("0.9");
            lbL1LVal.textProperty().bind(Bindings.format("%.2f",slL1L.valueProperty()));

            Label lbL2L = new Label("Length-2 Loop:");
            Label lbL2LVal = new Label("0.9");
            lbL2LVal.textProperty().bind(Bindings.format("%.2f",slL2L.valueProperty()));

            Label lbRel = new Label("Relative-To-Best:");
            Label lbRelVal = new Label("0.0");
            lbRelVal.textProperty().bind(Bindings.format("%.2f",slRel.valueProperty()));

            Label lbBin = new Label("Bindings:");
            Label lbBinVal = new Label("0.1");
            lbBinVal.textProperty().bind(Bindings.format("%.2f",slBin.valueProperty()));

            gpAdvOptions.add(lbL1L,0,0);
            gpAdvOptions.add(slL1L,1,0);
            gpAdvOptions.add(lbL1LVal,2,0);

            gpAdvOptions.add(lbL2L,0,1);
            gpAdvOptions.add(slL2L,1,1);
            gpAdvOptions.add(lbL2LVal,2,1);

            gpAdvOptions.add(lbRel,0,2);
            gpAdvOptions.add(slRel,1,2);
            gpAdvOptions.add(lbRelVal,2,2);

            gpAdvOptions.add(lbBin,0,3);
            gpAdvOptions.add(slBin,1,3);
            gpAdvOptions.add(lbBinVal,2,3);

            gpAdvOptions.getColumnConstraints().add(new ColumnConstraints());
            ColumnConstraints colAdv = new ColumnConstraints();
            colAdv.setHgrow(Priority.ALWAYS);
            gpAdvOptions.getColumnConstraints().add(colAdv);

            vbAdvConfig.getChildren().addAll(cbAdvOptions,gpAdvOptions);
            TitledPane tpAdvConfig = new TitledPane("Advanced Configuration",vbAdvConfig);
            accAdvOptions.getPanes().add(tpAdvConfig);

            //--------- Center: Final -------------------
            enableAdvOptions();
            vbCenter.getChildren().addAll(gpComboBox,sep1,hbLabelThr,gpCenter,accAdvOptions);
        }

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bGenModel = new Button("Generate Model");
        bGenModel.setPrefSize(150, 20);
        if(existingClasses.size() != 0)
            bGenModel.addEventHandler(MouseEvent.MOUSE_CLICKED,generateModel());

        hBoxBottom.getChildren().add(bGenModel);

        //--------- Final -------------------
        ScrollPane spCenter = new ScrollPane(vbCenter);
        spCenter.setFitToHeight(true);
        spCenter.setFitToWidth(true);

        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(spCenter);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        hmStage.setScene(scene);
        hmStage.setTitle("Heuristic Miner: Parameter Selection");
        hmStage.showAndWait();
    }

    public void enableAdvOptions(){
        if(cbAdvOptions.isSelected()){
            slL1L.setDisable(false);
            slL2L.setDisable(false);
            slRel.setDisable(false);
            slBin.setDisable(false);
        }else{
            slL1L.setDisable(true);
            slL2L.setDisable(true);
            slRel.setDisable(true);
            slBin.setDisable(true);

            slL1L.setValue(0.9);
            slL2L.setValue(0.9);
            slRel.setValue(0.0);
            slBin.setValue(0.1);
        }
    }

    private void updateComboBox(String className){
        comboDF.getItems().clear();
        comboDF.getItems().addAll(FXCollections.observableList(existingClasses.get(className)));
        comboDF.getSelectionModel().select(0);
    }

    public EventHandler<Event> generateModel(){
        return event -> {
            String className = comboClass.getSelectionModel().getSelectedItem();
            String dfType = comboDF.getSelectionModel().getSelectedItem();
            String freq = String.format(Locale.US, "%.2f", slFreq.getValue());
            String dep = String.format(Locale.US, "%.2f", slDep.getValue());
            String l1l = String.format(Locale.US, "%.2f", slL1L.getValue());
            String l2l = String.format(Locale.US, "%.2f", slL2L.getValue());
            String rel = String.format(Locale.US, "%.2f", slRel.getValue());
            String bind = String.format(Locale.US, "%.2f", slBin.getValue());

            Map<String,String> algParams = new HashMap<>();
            algParams.put("logName",logName);
            algParams.put("className",className);
            algParams.put("dfType",dfType);
            algParams.put("freq",freq);
            algParams.put("dep",dep);
            algParams.put("l1l",l1l);
            algParams.put("l2l",l2l);
            algParams.put("rel",rel);
            algParams.put("bind",bind);

            HeuristicMiner hmModel = new HeuristicMiner(n4JQueries.getDriver());
            hmModel.generateProcessModel(algParams);
            hmModel.generatePetriNet();

            Utils.printTime("End ModelCreation: ");
            Utils.showAlert("Application Message",Alert.AlertType.INFORMATION,"Process model \""+hmModel.getModelName()+"\" generated successfully.");
            hmStage.close();
        };
    }
}

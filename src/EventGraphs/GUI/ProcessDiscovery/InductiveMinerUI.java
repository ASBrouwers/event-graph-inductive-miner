package EventGraphs.GUI.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Neo4j.ProcessDiscovery.HeuristicMiner;
import EventGraphs.Neo4j.ProcessDiscovery.InductiveMiner.InductiveMiner;
import EventGraphs.Utils;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.text.Text;
import jdk.jfr.EventType;

import java.util.*;

public class InductiveMinerUI extends ProcessModelUI {
    String logName;
    N4JQueries n4JQueries;
    Stage imStage;
    ComboBox<String> comboClass, comboDF;
    ToggleGroup group;
    CheckBox toggleFilter, cbRemoveMinTraces;
    Slider filterSlider;
    Map<String,List<String>> existingClasses;

    public InductiveMinerUI(N4JQueries n4JQueries, String logName){
        this.n4JQueries = n4JQueries;
        this.logName = logName;

        imStage = new Stage();
        comboClass = new ComboBox<>();
        comboDF = new ComboBox<>();
    }

    private void updateComboBox(String className){
        comboDF.getItems().clear();
        comboDF.getItems().addAll(FXCollections.observableList(existingClasses.get(className)));
        comboDF.getSelectionModel().select(0);
    }

    public void parameterSelection() {
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Specify the parameters needed to execute the Inductive Miner algorithm:");
        vBox.getChildren().add(top);
        //--------- Center -------------------
        VBox vbCenter = new VBox();
        vbCenter.setPrefSize(350, 250);
        vbCenter.setSpacing(10);
        vbCenter.setPadding(new Insets(15, 12, 15, 12));

        existingClasses = n4JQueries.getExistingClasses(logName);
        System.out.println("classes: " + existingClasses.size());
        if (existingClasses.size() == 0) {
            TextArea taMessage = new TextArea("\nA class must be created in order to generate a Process Model with the Inductive Miner algorithm");
            taMessage.setEditable(false);
            taMessage.setWrapText(true);
            taMessage.setPrefWidth(100);

            vbCenter.getChildren().add(taMessage);
        } else {
            //--------- Center: Class/DF -------------------
            GridPane gpComboBox = new GridPane();
            gpComboBox.setHgap(10);
            gpComboBox.setVgap(10);

            List<String> classList = new ArrayList<>(existingClasses.keySet());
            Label lbClass = new Label("Class:");
            comboClass.setPrefWidth(200);
            comboClass.getItems().addAll(FXCollections.observableList(classList));
            comboClass.getSelectionModel().select(0);
            comboClass.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null) {
                    updateComboBox(t1);
                }
            });

            Label lbDF = new Label("DF:");
            comboDF.setPrefWidth(200);
            comboDF.getItems().addAll(FXCollections.observableList(existingClasses.get(classList.get(0))));
            comboDF.getSelectionModel().select(0);

            gpComboBox.add(lbClass, 0, 0);
            gpComboBox.add(comboClass, 1, 0);
            gpComboBox.add(lbDF, 0, 1);
            gpComboBox.add(comboDF, 1, 1);

            javafx.scene.text.Text taRadio = new Text("\nSelect Inductive Miner DFG-splitting or log-splitting:");

            HBox radioBox = new HBox(10);
            group = new ToggleGroup();
            RadioButton rbDF = new RadioButton("DFG");
            rbDF.setUserData(false);
            RadioButton rbLog = new RadioButton("Log");
            rbLog.setUserData(true);
            rbDF.setToggleGroup(group);
            rbDF.setSelected(true);
            rbLog.setToggleGroup(group);

            group.selectedToggleProperty().addListener(e -> changedSplit((boolean) group.getSelectedToggle().getUserData()));

            javafx.scene.text.Text taFilter = new Text("\nFilter:");
            toggleFilter = new CheckBox("Enable IM infrequent filtering");
            toggleFilter.setDisable(true);
            toggleFilter.setOnAction(e -> toggleFilter(toggleFilter.isSelected()));
            cbRemoveMinTraces = new CheckBox("Filter out min-freq traces from log");
            cbRemoveMinTraces.setOnAction(e -> toggleMinTrace(cbRemoveMinTraces.isSelected()));

            GridPane gpCenter = new GridPane();
            gpCenter.setHgap(10);
            gpCenter.setVgap(10);

            Label lbFreq = new Label("Filter threshold: ");
            filterSlider = new Slider(0, 1, 0.2);
            filterSlider.setBlockIncrement(0.05);
            filterSlider.setDisable(!toggleFilter.isSelected());
            Label lbFreqVal = new Label(String.valueOf(filterSlider.getValue()));
            lbFreqVal.textProperty().bind(Bindings.format(
                    "%.2f",
                    filterSlider.valueProperty()
            ));

            gpCenter.add(lbFreq, 0, 0);
            gpCenter.add(filterSlider, 1, 0);
            gpCenter.add(lbFreqVal, 2, 0);

            gpCenter.getColumnConstraints().add(new ColumnConstraints());
            ColumnConstraints colBasic = new ColumnConstraints();
            colBasic.setHgrow(Priority.ALWAYS);
            gpCenter.getColumnConstraints().add(colBasic);

            radioBox.getChildren().addAll(rbDF, rbLog);
            vbCenter.getChildren().addAll(gpComboBox);
            vbCenter.getChildren().add(taRadio);
            vbCenter.getChildren().add(radioBox);
            vbCenter.getChildren().addAll(taFilter, toggleFilter, gpCenter);

    }
        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: " + Utils.BACKGROUND_COLOR_MAIN + ";");

        Button bGenModel = new Button("Generate Model");
        bGenModel.setPrefSize(150, 20);
        bGenModel.addEventHandler(MouseEvent.MOUSE_CLICKED, generateModel());
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
        imStage.setScene(scene);
        imStage.setTitle("Inductive Miner: Model Generation");
        imStage.showAndWait();

    }

    public EventHandler<Event> generateModel() {
        return event -> {
            String className = comboClass.getSelectionModel().getSelectedItem();
            String dfType = comboDF.getSelectionModel().getSelectedItem();
            String logSplit = group.getSelectedToggle().getUserData().toString();
            boolean doFilter = toggleFilter.isSelected();
            boolean minTrace = cbRemoveMinTraces.isSelected();
            double filter = doFilter ? filterSlider.getValue() : -1;
            Map<String,String> algParams = new HashMap<>();

            algParams.put("logName",logName);
            algParams.put("className",className);
            algParams.put("dfType",dfType);
            algParams.put("logSplit", logSplit);
            algParams.put("filter", String.format(Locale.US, "%.2f", filter));
            algParams.put("minTrace", String.valueOf(minTrace));

            InductiveMiner hmModel = new InductiveMiner(n4JQueries.getDriver());
            hmModel.generateProcessModel(algParams);
            hmModel.generatePetriNet();

            Utils.showAlert("Application Message", Alert.AlertType.INFORMATION, "Process model \""+hmModel.getModelName()+"\" generated successfully.");
            imStage.close();
        };
    }

    private void toggleFilter(boolean enabled) {
        filterSlider.setDisable(!enabled);
    }

    private void toggleMinTrace(boolean enabled) {

    }

    private void changedSplit(boolean logSplit) {
        toggleFilter.setDisable(!logSplit);
    }

}

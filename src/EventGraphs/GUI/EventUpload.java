package EventGraphs.GUI;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EventUpload {
    N4JQueries n4JQueries;
    Stage activityChooser, attributesChooser, dimensionsChooser;
    String fileName, timestampName, activityName;
    List<String> attributeList, selectedAttributes, possibleDimensions, selectedDimensions;
    ListView<String> lvActivityAttribute, lvEventAttributes, lvAttributes, lvDimensions;
    boolean chooseDimensions;
    Button bUpload;

    public EventUpload(N4JQueries n4JQueries){
        this.n4JQueries = n4JQueries;
        fileName = "";
        timestampName = "";
        activityName = "";
        lvEventAttributes = new ListView<>();
        lvActivityAttribute = new ListView<>();
        attributeList = new ArrayList<>();
        selectedAttributes = new ArrayList<>();
        possibleDimensions = new ArrayList<>();
        selectedDimensions = new ArrayList<>();
        lvAttributes = new ListView<>();
        lvDimensions = new ListView<>();
        chooseDimensions = false;
    }

    public void startEventUpload(){
        parseEventFile();
        if(!activityName.equals("")) {
            selectEventAttributes();

            if(chooseDimensions){
                try {
                    selectDimensions();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void parseEventFile(){
        Stage stage = new Stage();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose a CSV File");
        fileChooser.setInitialDirectory(new File(Utils.NEO4J_IMPORT_PATH));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV","*.csv"));
        File csvFile = fileChooser.showOpenDialog(stage);
        if(csvFile == null) return;
        fileName = csvFile.getName();

        try {
            CSVReader reader = new CSVReader(new FileReader(csvFile.getPath()));
            String[] headerRow = reader.readNext();
            int timestampCol = getTimestampColumn(headerRow);

            timestampName = headerRow[timestampCol];

            HBox top = Utils.createDefaultBorderTop("Select \"Activity\" Attribute: \n" +
                                                                "Timestamp = "+timestampName);

            attributeList = new ArrayList<>(Arrays.asList(headerRow));
            attributeList.remove(timestampCol);
            lvActivityAttribute = new ListView<>(FXCollections.observableList(attributeList));
            lvActivityAttribute.setPrefHeight(200);

            HBox hboxBottom = new HBox();
            hboxBottom.setPadding(new Insets(15, 12, 15, 12));
            hboxBottom.setSpacing(10);
            hboxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

            Button bContinue = new Button("Continue");
            bContinue.setPrefSize(100, 20);
            bContinue.addEventHandler(MouseEvent.MOUSE_CLICKED,chooseAttributes());

            hboxBottom.getChildren().add(bContinue);

            BorderPane border = new BorderPane();
            border.setTop(top);
            border.setCenter(lvActivityAttribute);
            border.setBottom(hboxBottom);

            Scene scene = new Scene(border);
            activityChooser = new Stage();
            activityChooser.setScene(scene);
            activityChooser.setTitle("Select Activity Attribute");
            activityChooser.showAndWait();

        } catch (CsvValidationException | IOException e) {
            e.printStackTrace();
        }
    }

    private void selectEventAttributes(){
        HBox top = Utils.createDefaultBorderTop("Select Attributes to include in the graph: \n" +
                                                            "Timestamp = "+timestampName+"\n" +
                                                            "Activity = "+activityName);

        lvEventAttributes.setItems(FXCollections.observableList(attributeList));
        lvEventAttributes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lvEventAttributes.setPrefHeight(200);

        HBox hboxBottom = new HBox();
        hboxBottom.setPadding(new Insets(15, 12, 15, 12));
        hboxBottom.setSpacing(10);
        hboxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bNext = new Button("Next");
        bNext.setPrefSize(100, 20);
        bNext.addEventHandler(MouseEvent.MOUSE_CLICKED,defineDimensions());

        Button bUpload = new Button("Finish");
        bUpload.setPrefSize(100, 20);
        bUpload.addEventHandler(MouseEvent.MOUSE_CLICKED,uploadEvents1Dim());

        hboxBottom.getChildren().addAll(bNext, bUpload);

        BorderPane border = new BorderPane();
        border.setTop(top);
        border.setCenter(lvEventAttributes);
        border.setBottom(hboxBottom);

        Scene scene = new Scene(border);
        attributesChooser = new Stage();
        attributesChooser.setScene(scene);
        attributesChooser.setTitle("Select Event Attributes");
        attributesChooser.showAndWait();
    }

    private void selectDimensions() throws FileNotFoundException {
        //-------------------Top----------------------------
        HBox top = Utils.createDefaultBorderTop("Choose at least 2 attributes to define the log as Multi-Dimensional Event Data.\n" +
                "Skip to treat the log as one-dimensional.");

        //-------------------Middle----------------------------
        VBox vbMiddle = new VBox();
        vbMiddle.setPadding(new Insets(0,10,0,10));
        vbMiddle.setPrefHeight(400);

        Label lbExample = new Label("Example:");
        lbExample.setFont(Font.font("Arial", FontWeight.BOLD,12));

        HBox hbExamples = new HBox();
        hbExamples.setSpacing(10);

        VBox oneDim = new VBox();
        oneDim.setPrefWidth(90);
        Label lbOneDim = new Label("No dimension specified.\n  ");
        lbOneDim.setWrapText(true);
        FileInputStream input1 = new FileInputStream("src/EventGraphs/resources/1Dimension.png");
        Image image1 = new Image(input1);
        ImageView ivOneDim = new ImageView(image1);
        ivOneDim.setFitHeight(80);
        ivOneDim.setPreserveRatio(true);
        Label lbDesc1 = new Label("Events with ID = 2 and ID = 3 will be connected to both Order and Delivery entities.");
        lbDesc1.setFont(Font.font("Arial", FontPosture.ITALIC,12));
        lbDesc1.setWrapText(true);
        oneDim.getChildren().addAll(lbOneDim, ivOneDim, lbDesc1);
        VBox.setVgrow(lbDesc1, Priority.ALWAYS);
        VBox.setVgrow(lbOneDim, Priority.SOMETIMES);

        VBox multiDim = new VBox();
        multiDim.setPrefWidth(90);
        Label lbMultiDim = new Label("Two dimensions specified.\n1st Priority: Delivery. 2nd Priority: Order.");
        lbMultiDim.setWrapText(true);
        FileInputStream input2 = new FileInputStream("src/EventGraphs/resources/multiDimension.png");
        Image image2 = new Image(input2);
        ImageView ivMultiDim = new ImageView(image2);
        ivMultiDim.setFitHeight(80);
        ivMultiDim.setPreserveRatio(true);
        Label lbDesc2 = new Label("Events with ID = 2 and ID = 3 will be connected only to Delivery entities.");
        lbDesc2.setFont(Font.font("Arial", FontPosture.ITALIC,12));
        lbDesc2.setWrapText(true);
        multiDim.getChildren().addAll(lbMultiDim, ivMultiDim, lbDesc2);
        VBox.setVgrow(lbDesc2, Priority.ALWAYS);
        VBox.setVgrow(lbMultiDim, Priority.SOMETIMES);

        hbExamples.getChildren().addAll(oneDim,multiDim);

        HBox hbAttributes = new HBox();
        hbAttributes.setSpacing(10);
        hbAttributes.setPadding(new Insets(10,0,10,0));

        possibleDimensions = new ArrayList<>(selectedAttributes);
        lvAttributes.setItems(FXCollections.observableList(possibleDimensions));
        lvAttributes.setPrefHeight(200);

        lvDimensions.setPrefHeight(200);

        VBox vbAddRemove = new VBox();
        vbAddRemove.setAlignment(Pos.CENTER);
        vbAddRemove.setSpacing(10);
        Button bAdd = new Button("Add");
        bAdd.disableProperty().bind(lvAttributes.getSelectionModel().selectedItemProperty().isNull());
        bAdd.addEventHandler(MouseEvent.MOUSE_CLICKED,addDimension());
        Button bRemove = new Button("Remove");
        bRemove.disableProperty().bind(lvDimensions.getSelectionModel().selectedItemProperty().isNull());
        bRemove.addEventHandler(MouseEvent.MOUSE_CLICKED,removeDimension());
        vbAddRemove.getChildren().addAll(bAdd,bRemove);

        hbAttributes.getChildren().addAll(lvAttributes,vbAddRemove,lvDimensions);

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        vbMiddle.getChildren().addAll(lbExample,sep1,hbExamples,sep2,hbAttributes);

        //-------------------Bottom----------------------------
        HBox hboxBottom = new HBox();
        hboxBottom.setAlignment(Pos.BASELINE_RIGHT);
        hboxBottom.setPadding(new Insets(15, 12, 15, 12));
        hboxBottom.setSpacing(10);
        hboxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bSkip = new Button("Skip");
        bSkip.setPrefSize(100, 20);
        bSkip.addEventHandler(MouseEvent.MOUSE_CLICKED,uploadEvents1Dim());

        bUpload = new Button("Finish");
        bUpload.setPrefSize(100, 20);
        bUpload.setDisable(true);
        bUpload.addEventHandler(MouseEvent.MOUSE_CLICKED,uploadEventsMultiDim());

        hboxBottom.getChildren().addAll(bSkip,bUpload);

        //-------------------Final----------------------------
        BorderPane border = new BorderPane();
        border.setTop(top);
        border.setCenter(vbMiddle);
        border.setBottom(hboxBottom);

        Scene scene = new Scene(border);
        dimensionsChooser = new Stage();
        dimensionsChooser.setScene(scene);
        dimensionsChooser.setTitle("Select Log Dimensions");
        dimensionsChooser.showAndWait();
    }

    private int getTimestampColumn(String[] cols){
        int colTimestamp = 0;

        for(String colName : cols){
            String colNameLC = colName.toLowerCase();
            if(colNameLC.contains("timestamp") ||
                    colNameLC.contains("time") ||
                    colNameLC.contains("start") ||
                    colNameLC.contains("end")) break;
            colTimestamp++;
        }

        return colTimestamp;
    }

    public EventHandler<Event> chooseAttributes(){
        return event -> {
            activityName = lvActivityAttribute.getSelectionModel().getSelectedItem();
            attributeList.remove(activityName);
            activityChooser.close();
        };
    }

    public EventHandler<Event> uploadEvents1Dim() {
        return event -> {
            selectedAttributes = lvEventAttributes.getSelectionModel().getSelectedItems();
            List<String> logParams = Arrays.asList(fileName, timestampName, activityName);
            int status = n4JQueries.loadEvents(logParams, selectedAttributes, null);

            if(status == 1) return;

            if(attributesChooser != null) attributesChooser.close();
            if(dimensionsChooser != null) dimensionsChooser.close();

            Utils.printTime("End UploadCSV: ");
            Utils.showAlert("Application Message", Alert.AlertType.INFORMATION,"Events from \""+fileName+"\" have been uploaded to database");
        };
    }

    public EventHandler<Event> defineDimensions() {
        return event -> {
            selectedAttributes = lvEventAttributes.getSelectionModel().getSelectedItems();

            attributesChooser.close();

            chooseDimensions = true;
        };
    }

    public EventHandler<Event> uploadEventsMultiDim() {
        return event -> {
            List<String> logParams = Arrays.asList(fileName, timestampName, activityName);
            List<String> dimensions = lvDimensions.getItems();
            int status = n4JQueries.loadEvents(logParams, selectedAttributes, dimensions);

            if(status == 1) return;

            dimensionsChooser.close();

            Utils.printTime("End UploadCSV: ");
            Utils.showAlert("Application Message", Alert.AlertType.INFORMATION,"Events from \""+fileName+"\" have been uploaded to database");
        };
    }

    public EventHandler<Event> addDimension() {
        return event -> {
            String selectedAttribute = lvAttributes.getSelectionModel().getSelectedItem();

            possibleDimensions.remove(selectedAttribute);
            lvAttributes.setItems(FXCollections.observableList(possibleDimensions));

            selectedDimensions.add(selectedAttribute);
            lvDimensions.setItems(FXCollections.observableList(selectedDimensions));

            if(lvDimensions.getItems().size() >= 2) bUpload.setDisable(false);
        };
    }

    public EventHandler<Event> removeDimension() {
        return event -> {
            String selectedDimension = lvDimensions.getSelectionModel().getSelectedItem();

            possibleDimensions.add(selectedDimension);
            lvAttributes.setItems(FXCollections.observableList(possibleDimensions));

            selectedDimensions.remove(selectedDimension);
            lvDimensions.setItems(FXCollections.observableList(selectedDimensions));

            if(lvDimensions.getItems().size() < 2) bUpload.setDisable(true);
        };
    }
}

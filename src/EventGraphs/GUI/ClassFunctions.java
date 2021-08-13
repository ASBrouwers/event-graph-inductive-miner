package EventGraphs.GUI;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

public class ClassFunctions {
    N4JQueries n4JQueries;
    String logName;
    Stage classStage,dfStage;
    ListView<String> lvAvailableAttributes, lvExistingEntities;
    TextArea taSamples, taExistingSamples;
    Map<String, List<String>> existingClasses, attributeSamples, existingAttrSamples;
    List<String> selectedAttributes;

    public ClassFunctions(N4JQueries n4JQueries, String logName) {
        this.n4JQueries = n4JQueries;
        this.logName = logName;

        lvAvailableAttributes = new ListView<>();
        lvExistingEntities = new ListView<>();

        existingClasses = new HashMap<>();
        attributeSamples = new HashMap<>();
        existingAttrSamples = new HashMap<>();

        taSamples = new TextArea();
        taExistingSamples = new TextArea();

        selectedAttributes = new ArrayList<>();
    }

    public void createNewClass(){
        classSelection();
        if(selectedAttributes.size()>0){
            //Show window to select :DF to use for :DF_C
            dfSelection();
        }
    }

    private void classSelection(){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Choose the attribute(s) from which the class will be created.\n" +
                "Number of distinct values is shown inside '()'");
        vBox.getChildren().add(top);

        existingClasses = n4JQueries.getExistingClasses(logName);
        if(existingClasses.size() > 0){
            vBox.getChildren().add(new Label("Existing classes:"));
            for(String act : existingClasses.keySet()){
                for(String df : existingClasses.get(act)){
                    HBox hbLabel = new HBox(new Label("\tClass: "+act+", DF: "+df));
                    hbLabel.setStyle("-fx-background-color: #FFFFFF;");
                    vBox.getChildren().add(hbLabel);
                }
            }
        }

        //--------- Middle -------------------
        HBox hbProperties = new HBox();
        hbProperties.setPadding(new Insets(15, 12, 15, 12));
        hbProperties.setSpacing(10);
        hbProperties.setPrefHeight(300);

        VBox vbAvailableAttributes = new VBox();

        Utils.PropertySamples attributeInfo = n4JQueries.getDistinctPropertyValues(logName,true);
        attributeSamples = attributeInfo.getPropertySamples();
        List<String> distinctProperties = attributeInfo.getProperties();
        Collections.sort(distinctProperties);
        lvAvailableAttributes = new ListView<>(FXCollections.observableList(distinctProperties));
        lvAvailableAttributes.setPrefWidth(150);
        lvAvailableAttributes.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> updateTextArea(taSamples,lvAvailableAttributes,attributeSamples));
        lvAvailableAttributes.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        vbAvailableAttributes.getChildren().add(new Label("Available attributes:"));
        vbAvailableAttributes.getChildren().add(lvAvailableAttributes);

        VBox vbSamplesLabel = new VBox();

        Label labelSamples = new Label("Example Values:");

        taSamples.setPrefWidth(150);
        taSamples.setEditable(false);

        vbSamplesLabel.getChildren().addAll(labelSamples,taSamples);
        VBox.setVgrow(taSamples, Priority.ALWAYS);

        hbProperties.getChildren().addAll(vbAvailableAttributes,vbSamplesLabel);
        HBox.setHgrow(vbSamplesLabel,Priority.ALWAYS);

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bContinue = new Button("Continue");
        bContinue.setPrefSize(100, 20);
        bContinue.addEventHandler(MouseEvent.MOUSE_CLICKED,selectClass());

        hBoxBottom.getChildren().add(bContinue);

        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(hbProperties);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        classStage = new Stage();
        classStage.setScene(scene);
        classStage.setTitle("Class: Attribute Selection");
        classStage.showAndWait();
    }

    private void dfSelection(){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Class attribute(s): "+selectedAttributes+"\n\nChoose the entity from which the :DF_C will be created.\n" +
                "Number of distinct entity nodes is shown inside '()'");
        vBox.getChildren().add(top);

        if(existingClasses.size() > 0){
            vBox.getChildren().add(new Label("Existing classes:"));
            for(String act : existingClasses.keySet()){
                for(String df : existingClasses.get(act)){
                    HBox hbLabel = new HBox(new Label("\tClass: "+act+", DF: "+df));
                    hbLabel.setStyle("-fx-background-color: #FFFFFF;");
                    vBox.getChildren().add(hbLabel);
                }
            }
        }

        //--------- Middle -------------------
        HBox hbProperties = new HBox();
        hbProperties.setPadding(new Insets(15, 12, 15, 12));
        hbProperties.setSpacing(10);
        hbProperties.setPrefHeight(300);

        Utils.PropertySamples entitiesInfo = n4JQueries.getExistingEntitiesWithSamples(logName, selectedAttributes);

        List<String> distinctEntities = entitiesInfo.getProperties();
        if(distinctEntities.size() == 0){
            //Put a message indicating that at least 1 entity must be created before creating a class
            TextArea taMessage = new TextArea("\nIn order to create a class, create an entity from an attribute different " +
                    "from the one selected for the Class.");
            taMessage.setEditable(false);
            taMessage.setWrapText(true);
            taMessage.setPrefWidth(200);

            hbProperties.getChildren().addAll(taMessage);
            HBox.setHgrow(taMessage,Priority.ALWAYS);
        }else{
            // Remove from list those entities that have already been used to create the :DF_C for the Class selected by the user
            String className = String.join("+",selectedAttributes);
            List<String> distinctEntitiesCopy = new ArrayList<>(distinctEntities);
            if(existingClasses.containsKey(className)){
                for(String df : existingClasses.get(className)){
                    for(String ent : distinctEntities){
                        String e = ent.split(" ")[0];
                        if(e.equals(df)){
                            distinctEntitiesCopy.remove(ent);
                        }
                    }
                }
            }
            distinctEntities = distinctEntitiesCopy;

            if(distinctEntities.size() == 0){
                TextArea taMessage = new TextArea("\nAll existing Entity Types have already been used to create the " +
                        ":DF_C relations of Class \"" + className + "\".\n\nCreate a new Entity to generate different :DF_C " +
                        "relations or choose a different attribute to use as Class.");
                taMessage.setEditable(false);
                taMessage.setWrapText(true);
                taMessage.setPrefWidth(200);

                hbProperties.getChildren().addAll(taMessage);
                HBox.setHgrow(taMessage,Priority.ALWAYS);
            }else {
                existingAttrSamples = entitiesInfo.getPropertySamples();

                VBox vbAvailableEntities = new VBox();

                lvExistingEntities = new ListView<>(FXCollections.observableList(distinctEntities));
                lvExistingEntities.setPrefWidth(150);
                lvExistingEntities.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> updateTextArea(taExistingSamples, lvExistingEntities, existingAttrSamples));

                vbAvailableEntities.getChildren().add(new Label("Available entities:"));
                vbAvailableEntities.getChildren().add(lvExistingEntities);

                VBox vbSamplesLabel = new VBox();

                Label labelSamples = new Label("Example Entity Nodes:");

                taExistingSamples.setPrefWidth(150);
                taExistingSamples.setEditable(false);

                vbSamplesLabel.getChildren().addAll(labelSamples, taExistingSamples);
                VBox.setVgrow(taExistingSamples, Priority.ALWAYS);

                hbProperties.getChildren().addAll(vbAvailableEntities, vbSamplesLabel);
                HBox.setHgrow(vbSamplesLabel, Priority.ALWAYS);
            }
        }

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bCreateClass = new Button("Create Class");
        bCreateClass.setPrefSize(100, 20);
        if(distinctEntities.size() != 0) bCreateClass.addEventHandler(MouseEvent.MOUSE_CLICKED,createClass());

        hBoxBottom.getChildren().add(bCreateClass);

        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(hbProperties);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        dfStage = new Stage();
        dfStage.setScene(scene);
        dfStage.setTitle("Entity: Attribute Selection");
        dfStage.showAndWait();
    }

    private EventHandler<Event> selectClass(){
        return event -> {
            List<String> selectedItems = lvAvailableAttributes.getSelectionModel().getSelectedItems();

            for(String item : selectedItems) selectedAttributes.add(item.split(" ")[0]);
            Collections.sort(selectedAttributes);
            classStage.close();
        };
    }

    private EventHandler<Event> createClass(){
        return event -> {
            String selectedEntity = lvExistingEntities.getSelectionModel().getSelectedItem().split(" ")[0];

            n4JQueries.createClass(logName,selectedAttributes,selectedEntity);

            dfStage.close();

            Utils.printTime("End ClassCreation: ");
            Utils.showAlert("Application Message", Alert.AlertType.INFORMATION, "Class created.\n\n" +
                    "Properties Used:\t" + selectedAttributes + "\n" +
                    "Entity used for :DF_C:\t[" + selectedEntity + "]");
        };
    }

    private void updateTextArea(TextArea ta, ListView<String> lv, Map<String, List<String>> map){
        String samplesText = "";
        String selectedAttribute = lv.getSelectionModel().getSelectedItem();
        selectedAttribute = selectedAttribute.split(" ")[0];

        List<String> samples = map.get(selectedAttribute);
        for(String s : samples){
            samplesText += s + "\n";
        }

        ta.setText(samplesText);
    }
}

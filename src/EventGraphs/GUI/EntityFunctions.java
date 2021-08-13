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
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityFunctions {
    N4JQueries n4JQueries;
    Stage entityStage, derivedEntityStage, entityTypeAttributeStage;
    String logName;
    ListView<String> lvAvailableAttributes, lvAvailableEntities, lvCandidateETAttr, lvExistingETAttr;
    TextArea taSamples;
    Map<String,List<String>> attributeSamples;
    boolean entityCreated;

    // Entity Type Attribute Variables
    List<String> entityTypeAttributes, etAttributesSelected;
    String entitySelected;

    public EntityFunctions(N4JQueries n4JQueries, String logName){
        this.n4JQueries = n4JQueries;
        this.logName = logName;

        entityCreated = false;

        attributeSamples = new HashMap<>();

        lvAvailableAttributes = new ListView<>();
        lvAvailableEntities = new ListView<>();
        lvCandidateETAttr = new ListView<>();
        lvExistingETAttr = new ListView<>();

        taSamples = new TextArea();

        entityTypeAttributes = new ArrayList<>();
        etAttributesSelected = new ArrayList<>();
        entitySelected = "";
    }

    public void createNewEntity(){
        entitySelection();
        if(entityTypeAttributes.size() > 0){
            //Ask the user if entity type attributes should be created
            entityTypeAttributeSelection(entitySelected, entityTypeAttributes);
        }else if(entityCreated) {
            Utils.printTime("End EntityCreation: ");
            Utils.showAlert("Application Message", Alert.AlertType.INFORMATION, "Entities for \"" + logName + "\" have been created based on the following attribute: " + entitySelected);
        }
    }

    public void createNewDerivedEntity(){
        derivedEntitySelection();
        if(entityTypeAttributes.size() > 0){
            //Ask the user if entity type attributes should be created
            entityTypeAttributeSelection(entitySelected, entityTypeAttributes);
        }else if(entityCreated){
            Utils.printTime("End EntityCreation: ");
            Utils.showAlert("Application Message",Alert.AlertType.INFORMATION, "Derived entities for \""+logName+"\" have been created based on the following entity: "+entitySelected);
        }
    }

    public void returnETAttributes(){
        entityTypeAttributeSelection();
    }

    private void entitySelection(){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Choose the attribute from which the entity will be created.\n" +
                                                            "Number of distinct values is shown inside '()'");
        vBox.getChildren().add(top);

        List<String> existingEntities = n4JQueries.getExistingEntities(logName);
        if(existingEntities.size() > 0){
            vBox.getChildren().add(new Label("Existing entities:"));

            for(String ent : existingEntities){
                HBox hbLabel = new HBox(new Label("\t"+ent));
                hbLabel.setStyle("-fx-background-color: #FFFFFF;");
                vBox.getChildren().add(hbLabel);
            }
        }

        //--------- Middle -------------------
        HBox hbProperties = new HBox();
        hbProperties.setPadding(new Insets(15, 12, 15, 12));
        hbProperties.setSpacing(10);
        hbProperties.setPrefHeight(300);

        VBox vbAvailableAttributes = new VBox();

        Utils.PropertySamples attributeInfo = n4JQueries.getDistinctPropertyValues(logName,false);
        attributeSamples = attributeInfo.getPropertySamples();
        List<String> distinctProperties = attributeInfo.getProperties();
        distinctProperties.removeAll(existingEntities);
        lvAvailableAttributes = new ListView<>(FXCollections.observableList(distinctProperties));
        lvAvailableAttributes.setPrefWidth(150);
        lvAvailableAttributes.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> updateTextArea());

        vbAvailableAttributes.getChildren().add(new Label("Available attributes:"));
        vbAvailableAttributes.getChildren().add(lvAvailableAttributes);

        VBox vbSamplesLabel = new VBox();

        Label labelSamples = new Label("Example Values:");

        taSamples.setPrefWidth(150);
        taSamples.setEditable(false);

        vbSamplesLabel.getChildren().addAll(labelSamples,taSamples);
        VBox.setVgrow(taSamples,Priority.ALWAYS);

        hbProperties.getChildren().addAll(vbAvailableAttributes,vbSamplesLabel);
        HBox.setHgrow(vbSamplesLabel,Priority.ALWAYS);

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bCreateEntity = new Button("Create Entity");
        bCreateEntity.setPrefSize(100, 20);
        bCreateEntity.addEventHandler(MouseEvent.MOUSE_CLICKED,createEntity());

        hBoxBottom.getChildren().add(bCreateEntity);

        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(hbProperties);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        entityStage = new Stage();
        entityStage.setScene(scene);
        entityStage.setTitle("Entity: Attribute Selection");
        entityStage.showAndWait();
    }

    private void derivedEntitySelection(){
        BorderPane border = new BorderPane();

        //--------- Top -------------------
        HBox top = Utils.createDefaultBorderTop("Choose the entities from which the derived entity will be created:");

        //--------- Middle -------------------
        List<String> entities = n4JQueries.getExistingEntities(logName);
        if(entities.size() > 1){
            lvAvailableEntities = new ListView<>(FXCollections.observableList(entities));
            lvAvailableEntities.setPrefSize(100,150);
            lvAvailableEntities.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            border.setCenter(lvAvailableEntities);
        }else{
            //Put a message indicating that at least 2 entities must be created before creating derived entities
            TextArea taMessage = new TextArea("\nIn order to create a derived entity, create at least 2 entities first.");
            taMessage.setEditable(false);
            taMessage.setWrapText(true);
            taMessage.setPrefWidth(100);

            border.setCenter(taMessage);
        }

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bCreateDerivedEntity = new Button("Create Derived Entity");
        bCreateDerivedEntity.setPrefHeight(20);
        if(entities.size() > 1) bCreateDerivedEntity.addEventHandler(MouseEvent.MOUSE_CLICKED,createDerivedEntity());

        hBoxBottom.getChildren().add(bCreateDerivedEntity);

        //--------- Stage Creation -------------------
        border.setTop(top);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        derivedEntityStage = new Stage();
        derivedEntityStage.setScene(scene);
        derivedEntityStage.setTitle("Derived Entity: Entity Selection");
        derivedEntityStage.showAndWait();
    }

    private void entityTypeAttributeSelection() {
        // This stage appears during the return ET attribute process
        BorderPane border = new BorderPane();
        border.setPrefWidth(300);

        //--------- Top -------------------
        HBox top = Utils.createDefaultBorderTop("Existing entity type attributes for log '"+logName+"'.\n\n" +
                "Number of events related to each attribute is indicated between '()'");

        //--------- Middle -------------------
        VBox vBox = new VBox();

        Label labelInstructions = new Label("Selected attributes will be re-assigned to the events and deleted from the entity properties.");
        labelInstructions.setWrapText(true);
        labelInstructions.setPrefWidth(250);

        vBox.getChildren().add(labelInstructions);
        VBox.setVgrow(labelInstructions,Priority.SOMETIMES);

        entityTypeAttributes = n4JQueries.getExistingEntityTypeAttributes(logName);
        if(entityTypeAttributes.size() == 0){
            //Put a message indicating that at least 2 entities must be created before creating derived entities
            TextArea taMessage = new TextArea("\nNo Entity Type Attributes found for entities of log '"+logName+"'.");
            taMessage.setEditable(false);
            taMessage.setWrapText(true);
            taMessage.setPrefWidth(100);

            vBox.getChildren().add(taMessage);
            VBox.setVgrow(taMessage,Priority.ALWAYS);
        }else{
            lvExistingETAttr = new ListView<>(FXCollections.observableList(entityTypeAttributes));
            lvExistingETAttr.setPrefHeight(150);
            lvExistingETAttr.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            vBox.getChildren().add(lvExistingETAttr);
            VBox.setVgrow(lvExistingETAttr,Priority.ALWAYS);
        }

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bContinue = new Button("Continue");
        bContinue.setPrefSize(100,20);
        if(entityTypeAttributes.size() != 0) bContinue.addEventHandler(MouseEvent.MOUSE_CLICKED,returnAttributes());

        hBoxBottom.getChildren().addAll(bContinue);

        //--------- Stage Creation -------------------
        border.setTop(top);
        border.setCenter(vBox);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        entityTypeAttributeStage = new Stage();
        entityTypeAttributeStage.setScene(scene);
        entityTypeAttributeStage.setTitle("Entity Type Attribute Return");
        entityTypeAttributeStage.showAndWait();
    }

    private void entityTypeAttributeSelection(String entity, List<String> etAttributes){
        // This stage appears during the entity creation process
        BorderPane border = new BorderPane();

        //--------- Top -------------------
        HBox top = Utils.createDefaultBorderTop("Entity type attributes were found for entity '"+entity+"'.\n");

        //--------- Middle -------------------
        VBox vBox = new VBox();
        vBox.setPrefWidth(200);

        Label labelInstructions = new Label("Selected attributes will be assigned to the entity and deleted from the event properties.");
        labelInstructions.setWrapText(true);

        lvCandidateETAttr = new ListView<>(FXCollections.observableList(etAttributes));
        lvCandidateETAttr.setPrefHeight(150);
        lvCandidateETAttr.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        vBox.getChildren().addAll(labelInstructions, lvCandidateETAttr);
        VBox.setVgrow(lvCandidateETAttr,Priority.ALWAYS);
        VBox.setVgrow(labelInstructions,Priority.SOMETIMES);

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Button bSkip = new Button("Skip");
        bSkip.setPrefSize(100,20);
        bSkip.addEventHandler(MouseEvent.MOUSE_CLICKED,createEntityTypeAttributes(false));

        Button bContinue = new Button("Continue");
        bContinue.setPrefSize(100,20);
        bContinue.addEventHandler(MouseEvent.MOUSE_CLICKED,createEntityTypeAttributes(true));

        hBoxBottom.getChildren().addAll(bSkip, bContinue);

        //--------- Stage Creation -------------------
        border.setTop(top);
        border.setCenter(vBox);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        entityTypeAttributeStage = new Stage();
        entityTypeAttributeStage.setScene(scene);
        entityTypeAttributeStage.setTitle("Entity Type Attribute Selection");
        entityTypeAttributeStage.showAndWait();
    }

    private EventHandler<Event> createEntity(){
        return event -> {
            String selectedAttribute = lvAvailableAttributes.getSelectionModel().getSelectedItem();
            selectedAttribute = selectedAttribute.split(" ")[0];

            // Create entity
            n4JQueries.createEntity(logName, selectedAttribute);
            entityCreated = true;

            // Find entity type attributes
            entitySelected = selectedAttribute;
            entityTypeAttributes = n4JQueries.findEntityTypeAttributes(logName, entitySelected);

            entityStage.close();
        };
    }

    private EventHandler<Event> createDerivedEntity(){
        return event -> {
            List<String> selectedItems = lvAvailableEntities.getSelectionModel().getSelectedItems();
            if(selectedItems.size() < 2) return;

            List<String> selectedEntities = new ArrayList<>();
            for(String item : selectedItems){
                selectedEntities.add(item.split(" ")[0]);
            }
            n4JQueries.createDerivedEntity(logName,selectedEntities);
            entityCreated = true;

            // Find entity type attributes
            entitySelected = String.join("", selectedEntities);
            entityTypeAttributes = n4JQueries.findEntityTypeAttributes(logName, entitySelected);

            derivedEntityStage.close();
        };
    }

    private EventHandler<Event> createEntityTypeAttributes(boolean createETAttr){
        return event -> {
            etAttributesSelected = lvCandidateETAttr.getSelectionModel().getSelectedItems();
            if(createETAttr && etAttributesSelected.size() != 0){
                n4JQueries.createEntityTypeAttributes(logName,entitySelected,etAttributesSelected);
                Utils.showAlert("Application Message",Alert.AlertType.INFORMATION,
                        "Entities for \""+logName+"\" have been created based on the following attribute: ["+entitySelected+"]\n\n"+
                                    "The following attributes were assigned to their respective entity as entity type attributes: "+etAttributesSelected);
            }else{
                Utils.printTime("End EntityCreation: ");
                Utils.showAlert("Application Message",Alert.AlertType.INFORMATION, "Entities for \""+logName+"\" have been created based on the following attribute: "+entitySelected);
            }

            entityTypeAttributeStage.close();
        };
    }

    private EventHandler<Event> returnAttributes(){
        return event -> {
            List<String> selectedItems = lvExistingETAttr.getSelectionModel().getSelectedItems();
            if(selectedItems.size() > 0){
                List<String> selectedAttributes = new ArrayList<>();
                for(String attr : selectedItems){
                    selectedAttributes.add(attr.split(" ")[0]);
                }
                n4JQueries.returnETAttrToEvents(logName, selectedAttributes);
                Utils.showAlert("Application Message",Alert.AlertType.INFORMATION, "The following entity type attributes have been returned to the events: "+selectedAttributes);
            }
            entityTypeAttributeStage.close();
        };
    }

    private void updateTextArea(){
        String samplesText = "";
        String selectedAttribute = lvAvailableAttributes.getSelectionModel().getSelectedItem();
        selectedAttribute = selectedAttribute.split(" ")[0];

        List<String> samples = attributeSamples.get(selectedAttribute);
        for(String s : samples){
            samplesText += s + "\n";
        }

        taSamples.setText(samplesText);
    }
}

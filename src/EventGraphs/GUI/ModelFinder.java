package EventGraphs.GUI;

import EventGraphs.Neo4j.N4JQueries;
import EventGraphs.Utils;
import javafx.beans.binding.Bindings;
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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public class ModelFinder {
    N4JQueries n4JQueries;
    Stage filterStage, patternStage, queryStage;
    ListView<String> lvFilters, lvModels;
    List<String> selectedModels;
    ComboBox<String> comboActs1, comboActs2;
    VBox vbRight;
    String currentFilter, filterSelected;
    Map<String,ImageView> patternImages;
    List<Filter> filterList;
    Button bArrow;
    TextArea taQuery;

    public ModelFinder(N4JQueries n4JQueries) throws FileNotFoundException {
        this.n4JQueries = n4JQueries;

        lvFilters = new ListView<>();
        lvModels = new ListView<>();

        vbRight = new VBox();
        vbRight.setPrefSize(250,250);
        vbRight.setSpacing(10);
        vbRight.setPadding(new Insets(15, 12, 15, 12));
        vbRight.setAlignment(Pos.CENTER);

        selectedModels = new ArrayList<>();
        currentFilter = "start";
        filterSelected = "";

        patternImages = new HashMap<>();
        setImages();

        filterList = new ArrayList<>();
    }

    private void setImages() throws FileNotFoundException {
        FileInputStream input = new FileInputStream("src/EventGraphs/resources/startsWith.png");
        Image image = new Image(input);
        ImageView imageView = new ImageView(image);
        patternImages.put("start",imageView);

        input = new FileInputStream("src/EventGraphs/resources/endsWith.png");
        image = new Image(input);
        imageView = new ImageView(image);
        patternImages.put("end",imageView);

        input = new FileInputStream("src/EventGraphs/resources/directlyFollows.png");
        image = new Image(input);
        imageView = new ImageView(image);
        patternImages.put("direct",imageView);

        input = new FileInputStream("src/EventGraphs/resources/eventuallyFollows.png");
        image = new Image(input);
        imageView = new ImageView(image);
        patternImages.put("eventual",imageView);

        input = new FileInputStream("src/EventGraphs/resources/parallelSplit.png");
        image = new Image(input);
        imageView = new ImageView(image);
        patternImages.put("parallel",imageView);

        input = new FileInputStream("src/EventGraphs/resources/exclusiveChoice.png");
        image = new Image(input);
        imageView = new ImageView(image);
        patternImages.put("exclusive",imageView);
    }

    public List<String> getModels() throws FileNotFoundException {
        showModelFinder();
        return selectedModels;
    }

    public void showModelFinder() throws FileNotFoundException {
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Find Models based on patterns");
        vBox.getChildren().add(top);

        //--------- Middle -------------------
        HBox hbMiddle = new HBox();
        hbMiddle.setSpacing(10);

        //---------

        VBox vbListFunctions = new VBox();
        vbListFunctions.setSpacing(10);
        vbListFunctions.setPadding(new Insets(15, 12, 15, 12));

        Button bAdd = new Button("Add");
        bAdd.setPrefSize(100, 20);
        bAdd.addEventHandler(MouseEvent.MOUSE_CLICKED,addFilter());

        Button bRemove = new Button("Remove");
        bRemove.setPrefSize(100, 20);
        bRemove.disableProperty().bind(lvFilters.getSelectionModel().selectedItemProperty().isNull());
        bRemove.addEventHandler(MouseEvent.MOUSE_CLICKED,removeFilter());

        Button bMoveUp = new Button("Up");
        bMoveUp.setPrefSize(100, 20);
        bMoveUp.disableProperty().bind(lvFilters.getSelectionModel().selectedItemProperty().isNull());
        bMoveUp.addEventHandler(MouseEvent.MOUSE_CLICKED,moveFilterUp());

        Button bMoveDown = new Button("Down");
        bMoveDown.setPrefSize(100, 20);
        bMoveDown.disableProperty().bind(lvFilters.getSelectionModel().selectedItemProperty().isNull());
        bMoveDown.addEventHandler(MouseEvent.MOUSE_CLICKED,moveFilterDown());

        Button bEditQuery = new Button("Edit Query");
        bEditQuery.setPrefSize(100, 20);
        bEditQuery.disableProperty().bind(lvFilters.getSelectionModel().selectedItemProperty().isNull());
        bEditQuery.addEventHandler(MouseEvent.MOUSE_CLICKED,editQuery());

        vbListFunctions.getChildren().addAll(bAdd,bRemove,bMoveUp,bMoveDown,bEditQuery);

        //---------

        VBox vbFilters = new VBox();
        vbFilters.setPrefHeight(200);
        vbFilters.setPadding(new Insets(0, 0, 10, 0));

        Label lbFilter = new Label("Filters");

        vbFilters.getChildren().addAll(lbFilter,lvFilters);
        VBox.setVgrow(lvFilters, Priority.ALWAYS);

        //---------

        VBox vbArrow = new VBox();
        vbArrow.setAlignment(Pos.CENTER);

        FileInputStream input = new FileInputStream("src/EventGraphs/resources/rightArrow.png");
        Image image = new Image(input,50,25,true,false);
        ImageView imageView = new ImageView(image);

        bArrow = new Button("",imageView);
        bArrow.setPrefSize(50, 25);
        bArrow.setTooltip(new Tooltip("Apply filters"));
        bArrow.setDisable(true);
        bArrow.addEventHandler(MouseEvent.MOUSE_CLICKED,applyFilters());

        vbArrow.getChildren().addAll(bArrow);

        //---------

        VBox vbModels = new VBox();
        vbModels.setPrefHeight(200);
        vbModels.setPadding(new Insets(0, 10, 10, 0));

        Label lbModel = new Label("Models");

        List<String> existingModels = n4JQueries.getExistingModels();
        lvModels.setItems(FXCollections.observableList(existingModels));
        lvModels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        vbModels.getChildren().addAll(lbModel,lvModels);
        VBox.setVgrow(lvModels, Priority.ALWAYS);

        hbMiddle.getChildren().addAll(vbListFunctions,vbFilters,vbArrow,vbModels);

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");
        hBoxBottom.setAlignment(Pos.BASELINE_RIGHT);

        Button bShow = new Button("Show Petri Net");
        bShow.setPrefSize(100, 20);
        bShow.disableProperty().bind(Bindings.or(
                Bindings.size(lvModels.getSelectionModel().getSelectedItems()).isEqualTo(0),
                Bindings.size(lvModels.getSelectionModel().getSelectedItems()).greaterThan(2)));
        bShow.addEventHandler(MouseEvent.MOUSE_CLICKED,setModelsSelected());

        hBoxBottom.getChildren().add(bShow);

        //--------- Final -------------------
        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(hbMiddle);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        filterStage = new Stage();
        filterStage.setScene(scene);
        filterStage.setTitle("Find Models");
        filterStage.showAndWait();
    }

    private void showPatterns() {
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Choose a pattern to define the filter");
        vBox.getChildren().add(top);

        //--------- Middle -------------------
        HBox hbMiddlePattern = new HBox();
        hbMiddlePattern.setSpacing(10);

        //---------

        VBox vbListPatterns = new VBox();
        vbListPatterns.setSpacing(12);
        vbListPatterns.setPadding(new Insets(15, 12, 15, 12));

        Button bStart = new Button("Starts with");
        bStart.setPrefSize(150, 30);
        bStart.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("start"));

        Button bEnd = new Button("Ends with");
        bEnd.setPrefSize(150, 30);
        bEnd.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("end"));

        Button bDirect = new Button("Directly follows");
        bDirect.setPrefSize(150, 30);
        bDirect.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("direct"));

        Button bEventual = new Button("Eventually follows");
        bEventual.setPrefSize(150, 30);
        bEventual.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("eventual"));

        Button bParallel = new Button("Parallel split");
        bParallel.setPrefSize(150, 30);
        bParallel.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("parallel"));

        Button bExclusive = new Button("Exclusive choice");
        bExclusive.setPrefSize(150, 30);
        bExclusive.addEventHandler(MouseEvent.MOUSE_CLICKED,updateVBox("exclusive"));

        vbListPatterns.getChildren().addAll(bStart,bEnd,bDirect,bEventual,bParallel,bExclusive);

        //---------
        setVBox("start");
        hbMiddlePattern.getChildren().addAll(vbListPatterns,vbRight);

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");
        hBoxBottom.setAlignment(Pos.BASELINE_RIGHT);

        Button bAdd = new Button("Add Filter");
        bAdd.setPrefSize(100, 20);
        bAdd.addEventHandler(MouseEvent.MOUSE_CLICKED,setFilterSelected());

        hBoxBottom.getChildren().add(bAdd);

        //--------- Final -------------------
        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(hbMiddlePattern);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        patternStage = new Stage();
        patternStage.initModality(Modality.APPLICATION_MODAL);
        patternStage.setScene(scene);
        patternStage.setTitle("Add Filter");
        patternStage.showAndWait();
    }

    private void showQuery(int idxSelected, String query){
        //--------- Top -------------------
        VBox vBox = new VBox();

        HBox top = Utils.createDefaultBorderTop("Make changes to the query to retrieve a custom list of process models.\n" +
                "Queries must return the Model names in a list with 'modelID' as the alias.");
        vBox.getChildren().add(top);

        //--------- Middle -------------------
        Button bApply = new Button("Apply");

        taQuery = new TextArea(query);
        taQuery.setPrefSize(400,200);
        taQuery.textProperty().addListener((observableValue, s, t1) -> bApply.setDisable(false));

        //--------- Bottom -------------------
        HBox hBoxBottom = new HBox();
        hBoxBottom.setPadding(new Insets(15, 12, 15, 12));
        hBoxBottom.setSpacing(10);
        hBoxBottom.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");
        hBoxBottom.setAlignment(Pos.BASELINE_RIGHT);

        bApply.setPrefSize(100, 20);
        bApply.addEventHandler(MouseEvent.MOUSE_CLICKED,applyQueryChanges(idxSelected));
        bApply.setDisable(true);

        hBoxBottom.getChildren().add(bApply);

        //--------- Final -------------------
        BorderPane border = new BorderPane();
        border.setTop(vBox);
        border.setCenter(taQuery);
        border.setBottom(hBoxBottom);

        Scene scene = new Scene(border);
        queryStage = new Stage();
        queryStage.initModality(Modality.APPLICATION_MODAL);
        queryStage.setScene(scene);
        queryStage.setTitle("Edit Query");
        queryStage.showAndWait();
    }

    private EventHandler<Event> updateVBox(String pattern){
        return event ->{
            currentFilter = pattern;
            setVBox(pattern);
        };
    }

    private void setVBox(String pattern){
        vbRight.getChildren().clear();

        String name = "";
        String description = "";
        int numComboBoxes = 2;
        switch (pattern) {
            case "start":
                name = "STARTS WITH";
                description = "Find models that start with the selected activity.";
                numComboBoxes = 1;
                break;
            case "end":
                name = "ENDS WITH";
                description = "Find models that end with the selected activity.";
                numComboBoxes = 1;
                break;
            case "direct":
                name = "DIRECTLY FOLLOWS";
                description = "Find models where activity \"A\" is directly followed by activity \"B\".";
                break;
            case "eventual":
                name = "EVENTUALLY FOLLOWS";
                description = "Find models where activity \"A\" is eventually followed by activity \"B\".";
                break;
            case "parallel":
                name = "PARALLEL SPLIT";
                description = "Find models where activity \"A\" can be enabled concurrently with activity \"B\".";
                break;
            case "exclusive":
                name = "EXCLUSIVE CHOICE";
                description = "Find models where only activity \"A\" or activity \"B\" can enabled.";
                break;
        }

        Label lbPattern = new Label(name);

        Label lbDesc = new Label(description);
        lbDesc.setFont(Font.font("Arial", FontPosture.ITALIC,12));
        lbDesc.setWrapText(true);

        List<String> existingActs = n4JQueries.getExistingActivities();

        comboActs1 = new ComboBox<>();
        comboActs1.setItems(FXCollections.observableList(existingActs));
        new AutoCompleteBox(comboActs1);
        comboActs1.getSelectionModel().select(0);

        HBox hb1 = new HBox();
        hb1.setSpacing(10);
        hb1.setAlignment(Pos.CENTER_LEFT);
        Label lb1 = new Label("A:");
        hb1.getChildren().addAll(lb1,comboActs1);

        comboActs2 = new ComboBox<>();
        comboActs2.setItems(FXCollections.observableList(existingActs));
        new AutoCompleteBox(comboActs2);
        comboActs2.getSelectionModel().select(0);

        HBox hb2 = new HBox();
        hb2.setSpacing(10);
        hb2.setAlignment(Pos.CENTER_LEFT);
        Label lb2 = new Label("B:");
        hb2.getChildren().addAll(lb2,comboActs2);

        VBox vbComboBoxes = new VBox();
        vbComboBoxes.setSpacing(10);
        vbComboBoxes.setAlignment(Pos.CENTER);
        if(numComboBoxes == 1)
            vbComboBoxes.getChildren().add(hb1);
        else
            vbComboBoxes.getChildren().addAll(hb1,hb2);

        vbRight.getChildren().addAll(lbPattern,patternImages.get(pattern),lbDesc,vbComboBoxes);
    }

    public EventHandler<Event> setModelsSelected(){
        return event -> {
            selectedModels.addAll(lvModels.getSelectionModel().getSelectedItems());
            filterStage.close();
        };
    }

    public EventHandler<Event> addFilter(){
        return event -> {
            showPatterns();
            if(!filterSelected.equals("")){
                Filter f = filterList.get(filterList.size()-1);
                lvFilters.getItems().add(f.getFilterName());
                bArrow.setDisable(false);
            }
            filterSelected = "";
        };
    }

    public EventHandler<Event> removeFilter(){
        return event -> {
            int idxSelected = lvFilters.getSelectionModel().getSelectedIndex();
            lvFilters.getItems().remove(idxSelected);
            filterList.remove(idxSelected);
            bArrow.setDisable(false);
        };
    }

    public EventHandler<Event> moveFilterUp() {
        return event -> {
            List<String> filters = lvFilters.getItems();
            int idxSelected = lvFilters.getSelectionModel().getSelectedIndex();
            if(idxSelected != 0) {
                Collections.swap(filters, idxSelected, idxSelected - 1);
                Collections.swap(filterList, idxSelected, idxSelected - 1);
                lvFilters.setItems(FXCollections.observableList(filters));
                lvFilters.getSelectionModel().select(idxSelected - 1);
                bArrow.setDisable(false);
            }
        };
    }

    public EventHandler<Event> moveFilterDown() {
        return event -> {
            List<String> filters = lvFilters.getItems();
            int idxSelected = lvFilters.getSelectionModel().getSelectedIndex();
            if(idxSelected != filters.size()-1) {
                Collections.swap(filters, idxSelected, idxSelected + 1);
                Collections.swap(filterList, idxSelected, idxSelected + 1);
                lvFilters.setItems(FXCollections.observableList(filters));
                lvFilters.getSelectionModel().select(idxSelected + 1);
                bArrow.setDisable(false);
            }
        };
    }

    public EventHandler<Event> editQuery() {
        return event -> {
            int idxSelected = lvFilters.getSelectionModel().getSelectedIndex();
            String query = filterList.get(idxSelected).getFilterQuery();

            showQuery(idxSelected, query);
        };
    }

    public EventHandler<Event> applyQueryChanges(int idxSelected) {
        return event -> {
            String newQuery = taQuery.getText();

            filterList.get(idxSelected).setFilterName("Custom query");
            filterList.get(idxSelected).setFilterQuery(newQuery);

            List<String> filters = lvFilters.getItems();
            filters.set(idxSelected,filterList.get(idxSelected).getFilterName());
            lvFilters.setItems(FXCollections.observableList(filters));
            lvFilters.getSelectionModel().select(idxSelected);
            bArrow.setDisable(false);

            queryStage.close();
        };
    }

    public EventHandler<Event> setFilterSelected(){
        return event -> {
            filterSelected = currentFilter;
            String paramA = comboActs1.getSelectionModel().getSelectedItem();
            String paramB = comboActs2.getSelectionModel().getSelectedItem();
            String filterQuery = "";
            String filterName = "";

            switch (filterSelected) {
                case "start":
                    filterQuery = getStartPattern(paramA);
                    filterName = "Starts with: " + paramA;
                    break;
                case "end":
                    filterQuery = getEndPattern(paramA);
                    filterName = "Ends with: " + paramA;
                    break;
                case "direct":
                    filterQuery = getDirectPattern(paramA, paramB);
                    filterName = "Directly follows: " + paramA + " -> " + paramB;
                    break;
                case "eventual":
                    filterQuery = getEventualPattern(paramA, paramB);
                    filterName = "Eventually follows: " + paramA + " (->) " + paramB;
                    break;
                case "parallel":
                    filterQuery = getParallelPattern(paramA, paramB);
                    filterName = "Parallel: " + paramA + " AND " + paramB;
                    break;
                case "exclusive":
                    filterQuery = getExclusivePattern(paramA, paramB);
                    filterName = "Exclusive: " + paramA + " OR " + paramB;
                    break;
            }

            Filter f = new Filter(filterName,filterQuery);
            filterList.add(f);
            patternStage.close();
        };
    }

    public EventHandler<Event> applyFilters() {
        return event -> {
            if(filterList.size() == 0){
                List<String> existingModels = n4JQueries.getExistingModels();
                lvModels.setItems(FXCollections.observableList(existingModels));
            }else{
                List<String> filteredModels = n4JQueries.getFilteredModels(filterList);
                lvModels.setItems(FXCollections.observableList(filteredModels));
            }
            bArrow.setDisable(true);
        };
    }

    private String getStartPattern(String paramA){
        // Get models that start with "paramA"
        return "MATCH (m)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE]->(p:PetriNet)\n" +
                "WHERE s.isStart IS NOT NULL AND p.t = \""+paramA+"\"\n" +
                "RETURN DISTINCT m.ID AS modelID\n" +
                "ORDER BY modelID";
    }

    private String getEndPattern(String paramA){
        // Get models that end with "paramA"
        return "MATCH (m)-[:CONTAINS_PN]->(:PetriNet)-[:MODEL_EDGE*]->(p:PetriNet)-[:MODEL_EDGE]->(e:PetriNet)\n" +
                "WHERE e.isEnd IS NOT NULL AND p.t = \""+paramA+"\"\n" +
                "RETURN DISTINCT m.ID AS modelID\n" +
                "ORDER BY modelID";
    }

    private String getDirectPattern(String paramA, String paramB){
        // Get models where "paramA" is directly followed by "paramB"
        return "MATCH (m)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE*]->(p:PetriNet)\n" +
                "MATCH path = (p)-[:MODEL_EDGE*]->(q)\n" +
                "WHERE p.t = \""+paramA+"\" AND q.t = \""+paramB+"\"\n" +
                "WITH DISTINCT m.ID AS modelID, nodes(path) AS path\n" +
                //Remove first element of path
                "WITH modelID, tail(path) AS path\n" +
                //Remove last element of path
                "WITH modelID, apoc.coll.remove(path, size(path)-1) AS path\n" +
                //Remove paths if there is an intermediate transition in the path
                "WHERE ALL(n IN path WHERE n.t IS NULL)\n" +
                "RETURN DISTINCT modelID\n" +
                "ORDER BY modelID";
    }

    private String getEventualPattern(String paramA, String paramB){
        // Get models where "paramA" is eventually followed by "paramB"
        return "MATCH (m)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE*]->(p:PetriNet)\n" +
                "MATCH (p)-[:MODEL_EDGE*]->(q)\n" +
                "WHERE p.t = \""+paramA+"\" AND q.t = \""+paramB+"\"\n" +
                "RETURN DISTINCT m.ID AS modelID\n" +
                "ORDER BY modelID";
    }

    private String getParallelPattern(String paramA, String paramB){
        // Get models where "paramA" is in a Parallel split with "paramB"
        return "MATCH (m)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE*]->(ps:PetriNet)-[e:MODEL_EDGE]->()\n" +
                "MATCH (m)-[:CONTAINS_PN]->(s:PetriNet)-[:MODEL_EDGE*]->()-[f:MODEL_EDGE]->(pj:PetriNet)\n" +
                // Get all transition splits (ps) and transition joins (pj), this is, all transitions with more than one output/input respectively
                "WHERE (ps.type = \"tau\" OR ps.type = \"t\") AND (pj.type = \"tau\" OR pj.type = \"t\")\n" +
                "WITH m.ID AS modelID, ps, COUNT(DISTINCT e) AS numSplit, pj, COUNT(DISTINCT f) AS numJoin\n" +
                "WHERE numSplit > 1 AND numJoin > 1\n" +
                "WITH DISTINCT modelID, ps, pj\n" +
                // Find the existing paths from split until join
                "MATCH path = (ps)-[:MODEL_EDGE*]->(p)-[:MODEL_EDGE*]->(pj)\n" +
                "MATCH path2 = (ps)-[:MODEL_EDGE*]->(q)-[:MODEL_EDGE*]->(pj)\n" +
                // Keep those models that have both parameters inside the split/join path and do not follow each other
                "WHERE p.t = \""+paramA+"\" AND q.t = \""+paramB+"\" AND NOT q IN nodes(path) AND NOT p IN nodes(path2)\n" +
                "RETURN DISTINCT modelID\n" +
                "ORDER BY modelID";
    }

    private String getExclusivePattern(String paramA, String paramB){
        // Get models where "paramA" is in an Exclusive choice with "paramB"
        return "MATCH (m)-[:CONTAINS_PN]->(:PetriNet)-[:MODEL_EDGE*0..]->(xs:PetriNet)-[e:MODEL_EDGE]->()\n" +
                "MATCH (m)-[:CONTAINS_PN]->(:PetriNet)-[:MODEL_EDGE*0..]->()-[f:MODEL_EDGE]->(xj:PetriNet)\n" +
                // Get all place splits (xs) and place joins (xj), this is, all places with more than one output/input respectively
                "WHERE (xs.type = \"p\" OR xs.type = \"s_e\") AND (xj.type = \"p\" OR xj.type = \"s_e\")\n" +
                "WITH m.ID AS modelID, xs, COUNT(DISTINCT e) AS numSplit, xj, COUNT(DISTINCT f) AS numJoin\n" +
                "WHERE numSplit > 1 AND numJoin > 1\n" +
                "WITH DISTINCT modelID, xs, xj\n" +
                // Find the existing paths from split until join
                "MATCH path = (xs)-[:MODEL_EDGE*]->(p)-[:MODEL_EDGE*]->(xj)\n" +
                "MATCH path2 = (xs)-[:MODEL_EDGE*]->(q)-[:MODEL_EDGE*]->(xj)\n" +
                // Keep those models that have both parameters inside the split/join path and do not follow each other
                "WHERE p.t = \""+paramA+"\" AND q.t = \""+paramB+"\" AND NOT q IN nodes(path) AND NOT p IN nodes(path2)\n" +
                "RETURN DISTINCT modelID\n" +
                "ORDER BY modelID";
    }

    public static class Filter{
        private String filterName;
        private String filterQuery;

        public Filter(String filterName, String filterQuery){
            this.filterName = filterName;
            this.filterQuery = filterQuery;
        }

        public String getFilterName() {
            return filterName;
        }

        public String getFilterQuery() {
            return filterQuery;
        }

        public void setFilterName(String n){
            filterName = n;
        }

        public void setFilterQuery(String q){
            filterQuery = q;
        }
    }
}

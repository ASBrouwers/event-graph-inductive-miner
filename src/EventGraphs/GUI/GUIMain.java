package EventGraphs.GUI;

import EventGraphs.GUI.ProcessDiscovery.ProcessModelUI;
import EventGraphs.Graphstream.ProcessDiscovery.DataGraph;
import EventGraphs.Graphstream.ProcessDiscovery.GSGraphBuilder;
import EventGraphs.Graphstream.ProcessDiscovery.ModelComparison;
import EventGraphs.Graphstream.ProcessDiscovery.PetriNetGraph;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.neo4j.driver.Driver;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.*;

public class GUIMain {
    Driver driver;
    N4JQueries n4JQueries;
    BorderPane border;
    ListView<String> lvLogs, lvModels;
    Label labelSelLog, labelNodeType;
    Label lbDF,lbEC,lbEntities,lbClasses;
    TableView<Utils.LogAttribute> tvLogDetails, tvModelDetails, tvProperties;
    DataGraph dataGraph;
    GSGraphBuilder gsGraph;
    ModelComparison compGraph;
    PetriNetGraph petriNetGraph;
    HBox hbProperties;
    MenuItem miCreateConstraints, miDropConstraints;
    VBox mainContainer;
    Accordion accMenu;
    ComboBox<String> comboAlgs;
    Map<String,Class<?>> algorithmGraphClass, algorithmUIClass;
    Map<String,Boolean> logDF,logEC,logEntities,logClasses;
    CheckBox cbDF,cbEC,cbEntities,cbClasses;

    public GUIMain(Driver d){
        driver = d;
        n4JQueries = new N4JQueries(driver);
        border = new BorderPane();
        lvLogs = new ListView<>();
        lvModels = new ListView<>();
        labelSelLog = new Label("No Log Selected");
        tvLogDetails = new TableView<>();
        tvModelDetails = new TableView<>();

        mainContainer = new VBox();
        accMenu = new Accordion();

        comboAlgs = new ComboBox<>();

        logDF = new LinkedHashMap<>();
        logEC = new LinkedHashMap<>();
        logEntities = new LinkedHashMap<>();
        logClasses = new LinkedHashMap<>();

        try{
            setClassMaps();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /** setClassMaps()
     * This function is used to include the Java classes of all the discovery algorithms in a Map. This way the appropriate functions
     * can be called to display the graph or parameter selection window of each algorithm. The labels for each algorithm is set inside
     * the Utils.java file.*/
    private void setClassMaps() throws Exception {
        algorithmGraphClass = new HashMap<String,Class<?>>(){{
            put(Utils.ALGORITHM_LABEL.get("HM"), Class.forName("EventGraphs.Graphstream.ProcessDiscovery.HeuristicMinerGraph"));
            put(Utils.ALGORITHM_LABEL.get("IM"), Class.forName("EventGraphs.Graphstream.ProcessDiscovery.InductiveMinerGraph"));
        }};
        algorithmUIClass = new HashMap<String,Class<?>>(){{
            put(Utils.ALGORITHM_LABEL.get("HM"), Class.forName("EventGraphs.GUI.ProcessDiscovery.HeuristicMinerUI"));
//            put(Utils.ALGORITHM_LABEL.get("IM"), Class.forName("EventGraphs.GUI.ProcessDiscovery.InductiveMinerUI"));
        }};
    }

    public Scene buildMainDisplay(){
        border.setTop(buildMainTop());
        buildMainLeft();
        border.setLeft(mainContainer);

        StackPane tempPane = new StackPane();
        tempPane.setPrefSize(Utils.GRAPH_WINDOW_SIZE_X, Utils.GRAPH_WINDOW_SIZE_Y);
        tempPane.setStyle("-fx-background-color: #FFFFFF;");
        border.setCenter(tempPane);

        Scene guiMain = new Scene(border);
        guiMain.getStylesheets().add(Utils.GUI_STYLE_FILE);

        return guiMain;
    }

    private VBox buildMainTop(){
        VBox vbTop = new VBox();

        //------------ MENU ------------------
        MenuBar mbMain = new MenuBar();

        //------------ File ------------------
        Menu file = new Menu("File");

        MenuItem miUploadCSV = new MenuItem("Upload CSV File");
        miUploadCSV.setOnAction(e-> uploadCSV());

        MenuItem miClearDB = new MenuItem("Clear Database");
        miClearDB.setOnAction(e-> clearDatabase());

        file.getItems().addAll(miUploadCSV,miClearDB);

        //------------ Edit ------------------
        Menu edit = new Menu("Edit");
        MenuItem start = new MenuItem("Start timer");
        start.setOnAction(e-> Utils.printTime("Start activity: "));
        edit.getItems().add(start);

        //------------ Config ------------------
        Menu config = new Menu("Config");

        Menu mConstraints = new Menu("Constraints");

        miCreateConstraints = new MenuItem("Create Constraints");
        miCreateConstraints.setOnAction(e-> createConstraints());

        miDropConstraints = new MenuItem("Drop Constraints");
        miDropConstraints.setOnAction(e-> deleteConstraints());

        disableConstraintButton();
        mConstraints.getItems().addAll(miCreateConstraints,miDropConstraints);
        config.getItems().add(mConstraints);

        //------------ Main ------------------
        mbMain.getMenus().addAll(file,edit,config);

        //------------ HEADER ------------------
        HBox hbHeader = new HBox();
        hbHeader.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");
        hbHeader.setAlignment(Pos.CENTER);

        Label labelTitle = new Label("PROCESS MINING ON EVENT GRAPHS");
        labelTitle.setTextFill(Color.web("#FFFFFF"));
        labelTitle.setFont(new Font("Arial",30));

        hbHeader.getChildren().addAll(labelTitle);

        vbTop.getChildren().addAll(mbMain,hbHeader);

        return vbTop;
    }

    private void buildMainLeft(){
        mainContainer.setPrefWidth(300);
        mainContainer.setMaxWidth(300);
        mainContainer.setSpacing(10);
        mainContainer.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+"; " +
                "-fx-border-color:"+Utils.BACKGROUND_COLOR_MAIN+"; -fx-border-width:2;");

        //------------- MAIN 1 -----------------------
        HBox hbLogName = new HBox();
        hbLogName.setPadding(new Insets(10,10,10,10));
        hbLogName.setStyle("-fx-background-color: #FFFFFF");
        hbLogName.setAlignment(Pos.CENTER);

        labelSelLog.setFont(Font.font("Arial", FontWeight.BOLD,14));

        hbLogName.getChildren().addAll(labelSelLog);

        //------------- MAIN 2 -----------------------
        accMenu.getPanes().add(new TitledPane("Logs",logsDisplay()));
        accMenu.getPanes().add(new TitledPane("Graph Data",graphDisplay()));
        accMenu.getPanes().add(new TitledPane("Entities",entitiesDisplay()));
        accMenu.getPanes().add(new TitledPane("Classes",classesDisplay()));
        accMenu.getPanes().add(new TitledPane("Filters",new Label("PENDING")));
        accMenu.getPanes().add(new TitledPane("Algorithms",algorithmsDisplay()));
        accMenu.getPanes().add(new TitledPane("Models",modelsDisplay()));

        //------------- MAIN FINAL -----------------------
        mainContainer.getChildren().addAll(hbLogName,accMenu);
        VBox.setVgrow(accMenu, Priority.ALWAYS);
    }

    private VBox logsDisplay(){
        VBox logs = new VBox();
        logs.setSpacing(10);
        logs.setAlignment(Pos.TOP_CENTER);

        //------------- LOGS 1 -----------------------
        HBox hbTitle = new HBox();
        hbTitle.setPadding(new Insets(10,0,0,0));
        hbTitle.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Label labelTitle = new Label("Available Logs in DB");
        labelTitle.setTextFill(Color.web("#FFFFFF"));
        labelTitle.setFont(Font.font("Arial", FontWeight.BOLD,14));

        hbTitle.getChildren().addAll(labelTitle);

        //------------- LOGS 2 -----------------------
        lvLogs.setPrefHeight(150);
        setLogList();
        lvLogs.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> updateLogData());

        //------------- LOGS 3 -----------------------
        Button bDelLog = new Button("Delete Log");
        bDelLog.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bDelLog.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bDelLog.addEventHandler(MouseEvent.MOUSE_CLICKED,deleteLog());

        //------------- LOGS 4 -----------------------
        Button bDetails = new Button("View Log Details");
        bDetails.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bDetails.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bDetails.addEventHandler(MouseEvent.MOUSE_CLICKED,getLogDetails());

        //------------- LOGS 5 -----------------------
        TableColumn<Utils.LogAttribute,String> col1 = new TableColumn<>("Attribute");
        col1.setCellValueFactory(new PropertyValueFactory<>("attrName"));

        TableColumn<Utils.LogAttribute,String> col2 = new TableColumn<>("Value");
        col2.setCellValueFactory(new PropertyValueFactory<>("attrValue"));

        tvLogDetails.getColumns().add(col1);
        tvLogDetails.getColumns().add(col2);
        tvLogDetails.setPrefHeight(200);
        tvLogDetails.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tvLogDetails.getSortOrder().add(col1);

        //------------- LOGS Final -----------------------
        logs.getChildren().addAll(hbTitle,lvLogs,bDelLog,bDetails,tvLogDetails);
        VBox.setVgrow(tvLogDetails,Priority.ALWAYS);

        return logs;
    }

    private VBox graphDisplay(){
        VBox graph = new VBox();
        graph.setSpacing(10);
        graph.setAlignment(Pos.TOP_CENTER);

        //------------- GRAPH 1 -----------------------
        VBox vbInstance = new VBox();
        vbInstance.setSpacing(5);
        vbInstance.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());

        HBox hbInstance = new HBox();
        hbInstance.setAlignment(Pos.CENTER_LEFT);
        hbInstance.setSpacing(10);
        Label lbInstance = new Label("Instance Level");
        lbInstance.setTextFill(Color.web("#FFFFFF"));
        lbInstance.setFont(Font.font("Arial", FontWeight.BOLD,14));
        Button bViewInstance = new Button("View");
        bViewInstance.setPrefSize(100, 20);
        bViewInstance.addEventHandler(MouseEvent.MOUSE_CLICKED,viewSelectedGraph("Instances"));
        hbInstance.getChildren().addAll(lbInstance,bViewInstance);

        HBox hbDF = new HBox();
        hbDF.setSpacing(10);
        hbDF.setAlignment(Pos.CENTER_LEFT);
        hbDF.setPadding(new Insets(0,0,0,10));
        cbDF = new CheckBox(":DF/:CORR");
        cbDF.setMnemonicParsing(false);
        cbDF.setSelected(true);
        lbDF = new Label("(0/0)");
        lbDF.setTextFill(Color.web("#FFFFFF"));
        Button bDF = new Button("Edit");
        bDF.setPrefSize(50, 20);
        bDF.addEventHandler(MouseEvent.MOUSE_CLICKED,editOptions("DF"));
        hbDF.getChildren().addAll(cbDF,lbDF,bDF);

        HBox hbEC = new HBox();
        hbEC.setSpacing(10);
        hbEC.setAlignment(Pos.CENTER_LEFT);
        hbEC.setPadding(new Insets(0,0,0,10));
        cbEC = new CheckBox(":OBSERVES");
        cbEC.setMnemonicParsing(false);
        lbEC = new Label("(0/0)");
        lbEC.setTextFill(Color.web("#FFFFFF"));
        Button bEC = new Button("Edit");
        bEC.setPrefSize(50, 20);
        bEC.addEventHandler(MouseEvent.MOUSE_CLICKED,editOptions("EC"));
        hbEC.getChildren().addAll(cbEC,lbEC,bEC);

        vbInstance.getChildren().addAll(hbInstance,hbDF,hbEC);

        //------------- GRAPH 2 -----------------------
        VBox vbModel = new VBox();
        vbModel.setSpacing(5);
        vbModel.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());

        HBox hbModel = new HBox();
        hbModel.setAlignment(Pos.CENTER_LEFT);
        hbModel.setSpacing(10);
        Label lbModel = new Label("Model Level");
        lbModel.setTextFill(Color.web("#FFFFFF"));
        lbModel.setFont(Font.font("Arial", FontWeight.BOLD,14));
        Button bViewModel = new Button("View");
        bViewModel.setPrefSize(100, 20);
        bViewModel.addEventHandler(MouseEvent.MOUSE_CLICKED,viewSelectedGraph("Model"));
        hbModel.getChildren().addAll(lbModel,bViewModel);

        HBox hbEntities = new HBox();
        hbEntities.setSpacing(10);
        hbEntities.setAlignment(Pos.CENTER_LEFT);
        hbEntities.setPadding(new Insets(0,0,0,10));
        cbEntities = new CheckBox("Entities");
        cbEntities.setMnemonicParsing(false);
        cbEntities.setSelected(true);
        lbEntities = new Label("(0/0)");
        lbEntities.setTextFill(Color.web("#FFFFFF"));
        Button bEntities = new Button("Edit");
        bEntities.setPrefSize(50, 20);
        bEntities.addEventHandler(MouseEvent.MOUSE_CLICKED,editOptions("Entities"));
        hbEntities.getChildren().addAll(cbEntities,lbEntities,bEntities);

        HBox hbClasses = new HBox();
        hbClasses.setSpacing(10);
        hbClasses.setAlignment(Pos.CENTER_LEFT);
        hbClasses.setPadding(new Insets(0,0,0,10));
        cbClasses = new CheckBox("Classes");
        cbClasses.setMnemonicParsing(false);
        cbClasses.setSelected(true);
        lbClasses = new Label("(0/0)");
        lbClasses.setTextFill(Color.web("#FFFFFF"));
        Button bClasses = new Button("Edit");
        bClasses.setPrefSize(50, 20);
        bClasses.addEventHandler(MouseEvent.MOUSE_CLICKED,editOptions("Classes"));
        hbClasses.getChildren().addAll(cbClasses,lbClasses,bClasses);

        vbModel.getChildren().addAll(hbModel,hbEntities,hbClasses);

        //------------- GRAPH 3 -----------------------
        HBox hbGraphDetails = new HBox();
        hbGraphDetails.setSpacing(10);
        hbGraphDetails.setAlignment(Pos.CENTER_LEFT);
        hbGraphDetails.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Label labelGraphDetails = new Label("Node Details");
        labelGraphDetails.setTextFill(Color.web("#FFFFFF"));
        labelGraphDetails.setFont(Font.font("Arial", FontWeight.BOLD,14));

        hbProperties = new HBox();
        hbProperties.setAlignment(Pos.CENTER);
        labelNodeType = new Label();
        hbProperties.setStyle("-fx-border-style: solid inside;" + "-fx-border-width: 2;" +
                "-fx-border-color: #FFFFFF;" + "-fx-background-color: #FFFFFF;");
        hbProperties.getChildren().addAll(labelNodeType);

        hbGraphDetails.getChildren().addAll(labelGraphDetails,hbProperties);
        HBox.setHgrow(hbProperties,Priority.ALWAYS);

        //------------- GRAPH 4 -----------------------
        tvProperties = new TableView<>();
        tvProperties.setPrefHeight(200);

        TableColumn<Utils.LogAttribute,String> col1 = new TableColumn<>("Attribute");
        col1.setCellValueFactory(new PropertyValueFactory<>("attrName"));

        TableColumn<Utils.LogAttribute,String> col2 = new TableColumn<>("Value");
        col2.setCellValueFactory(new PropertyValueFactory<>("attrValue"));

        tvProperties.getColumns().add(col1);
        tvProperties.getColumns().add(col2);
        tvProperties.getSortOrder().add(col1);

        //------------- GRAPH Final -----------------------
        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        graph.getChildren().addAll(vbInstance,sep1,vbModel,sep2,hbGraphDetails,tvProperties);
        VBox.setVgrow(tvProperties, Priority.ALWAYS);

        return graph;
    }

    private VBox entitiesDisplay(){
        VBox entities = new VBox();
        entities.setSpacing(10);
        entities.setAlignment(Pos.TOP_CENTER);

        //------------- ENTITIES 1 -----------------------
        Button bNewEntity = new Button("New Entity");
        bNewEntity.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bNewEntity.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bNewEntity.addEventHandler(MouseEvent.MOUSE_CLICKED,entityFunction("Create Entity"));

        //------------- ENTITIES 2 -----------------------
        Button bNewDerivedEntity = new Button("New Derived Entity");
        bNewDerivedEntity.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bNewDerivedEntity.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bNewDerivedEntity.addEventHandler(MouseEvent.MOUSE_CLICKED,entityFunction("Create Derived Entity"));

        //------------- ENTITIES 3 -----------------------
        Button bReturnETAttributes = new Button("Return Entity Attribute");
        bReturnETAttributes.setTooltip(new Tooltip("Return Entity Type Attributes to entity events as properties"));
        bReturnETAttributes.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bReturnETAttributes.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bReturnETAttributes.addEventHandler(MouseEvent.MOUSE_CLICKED,entityFunction("Return ET Attribute"));

        entities.getChildren().addAll(bNewEntity,bNewDerivedEntity,bReturnETAttributes);

        return entities;
    }

    private VBox classesDisplay(){
        VBox classes = new VBox();
        classes.setSpacing(10);
        classes.setAlignment(Pos.TOP_CENTER);

        //------------- CLASS 1 -----------------------
        Button bNewClass = new Button("New Class");
        bNewClass.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bNewClass.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bNewClass.addEventHandler(MouseEvent.MOUSE_CLICKED,classFunction("Create Class"));

        classes.getChildren().addAll(bNewClass);

        return classes;
    }

    private VBox algorithmsDisplay(){
        VBox algorithms = new VBox();
        algorithms.setSpacing(10);
        algorithms.setAlignment(Pos.TOP_CENTER);

        //------------- ALGORITHMS 1 -----------------------
        Label labelAlgs = new Label("Select a Process Discovery Algorithm");
        labelAlgs.setTextFill(Color.web("#FFFFFF"));
        labelAlgs.setFont(Font.font("Arial", FontWeight.BOLD,14));

        //------------- ALGORITHMS 2 -----------------------
        comboAlgs.setPrefWidth(Utils.BUTTON_SIZE_X);
        for (String algName : Utils.ALGORITHM_LABEL.values())
            comboAlgs.getItems().add(algName);
        comboAlgs.getSelectionModel().select(0);

        //------------- ALGORITHMS 3 -----------------------
        Button bNewModel = new Button("Generate Model");
        bNewModel.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bNewModel.disableProperty().bind(lvLogs.getSelectionModel().selectedItemProperty().isNull());
        bNewModel.addEventHandler(MouseEvent.MOUSE_CLICKED,algFunction());

        //------------- ALGORITHMS FINAL -----------------------
        algorithms.getChildren().addAll(labelAlgs, comboAlgs, bNewModel);

        return algorithms;
    }

    public VBox modelsDisplay(){
        VBox models = new VBox();
        models.setSpacing(10);
        models.setAlignment(Pos.TOP_CENTER);

        //------------- MODELS 1 -----------------------
        Label lbExistingModels = new Label("Existing Database Models");
        lbExistingModels.setTextFill(Color.web("#FFFFFF"));
        lbExistingModels.setFont(Font.font("Arial", FontWeight.BOLD,14));

        //------------- MODELS 2 -----------------------
        lvModels.setPrefHeight(150);
        lvModels.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setModelList();

        //------------- MODELS 3 -----------------------
        HBox hbModels1 = new HBox();
        hbModels1.setSpacing(10);
        hbModels1.setAlignment(Pos.CENTER);

        Button bShowModel = new Button("Show Model");
        bShowModel.setPrefSize(120, 20);
        bShowModel.disableProperty().bind(Bindings.size(lvModels.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        bShowModel.addEventHandler(MouseEvent.MOUSE_CLICKED,viewSelectedModel());

        Button bShowPetriNet = new Button("Show Petri Net");
        bShowPetriNet.setPrefSize(120, 20);
        bShowPetriNet.disableProperty().bind(Bindings.or(
                Bindings.size(lvModels.getSelectionModel().getSelectedItems()).isEqualTo(0),
                Bindings.size(lvModels.getSelectionModel().getSelectedItems()).greaterThan(2)));
        bShowPetriNet.addEventHandler(MouseEvent.MOUSE_CLICKED,viewPetriNet());

        hbModels1.getChildren().addAll(bShowModel,bShowPetriNet);

        //------------- MODELS 4 -----------------------
        HBox hbModels2 = new HBox();
        hbModels2.setSpacing(10);
        hbModels2.setAlignment(Pos.CENTER);

        Button bModelDetails = new Button("View Model Details");
        bModelDetails.setPrefSize(120, 20);
        bModelDetails.disableProperty().bind(Bindings.size(lvModels.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        bModelDetails.addEventHandler(MouseEvent.MOUSE_CLICKED,getModelDetails());

        Button bDeleteModel = new Button("Delete Model");
        bDeleteModel.setPrefSize(120, 20);
        bDeleteModel.disableProperty().bind(Bindings.size(lvModels.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        bDeleteModel.addEventHandler(MouseEvent.MOUSE_CLICKED,deleteModel());

        hbModels2.getChildren().addAll(bModelDetails,bDeleteModel);

        //------------- MODELS 5 -----------------------
        Button bFindModels = new Button("Find Models");
        bFindModels.setPrefSize(Utils.BUTTON_SIZE_X, 20);
        bFindModels.addEventHandler(MouseEvent.MOUSE_CLICKED,modelSelection());

        //------------- MODELS 6 -----------------------
        TableColumn<Utils.LogAttribute,String> col1 = new TableColumn<>("Attribute");
        col1.setCellValueFactory(new PropertyValueFactory<>("attrName"));

        TableColumn<Utils.LogAttribute,String> col2 = new TableColumn<>("Value");
        col2.setCellValueFactory(new PropertyValueFactory<>("attrValue"));

        tvModelDetails.getColumns().add(col1);
        tvModelDetails.getColumns().add(col2);
        tvModelDetails.setPrefHeight(150);
        tvModelDetails.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tvModelDetails.getSortOrder().add(col1);

        //------------- MODELS FINAL -----------------------
        models.getChildren().addAll(lbExistingModels,lvModels,hbModels1,hbModels2,bFindModels,tvModelDetails);
        VBox.setVgrow(tvModelDetails, Priority.ALWAYS);

        return models;
    }

    /** removeGSReference()
     * The tables and the graph panel are cleared before displaying a new graph in the user interface.
     * The interaction with the nodes of the graph depends on a timer that constantly checks for clicks on the panel.
     * This timer must be stopped and deleted to prevent multiple timers being active on the same session.*/
    public void removeGSReference(){
        tvModelDetails.getItems().clear();
        tvProperties.getItems().clear();

        if(dataGraph != null){
            dataGraph.stopTimer();
            dataGraph = null;
        }

        if(gsGraph != null){
            gsGraph.stopTimer();
            gsGraph = null;
        }

        if(compGraph != null){
            compGraph.stopTimer();
            compGraph = null;
        }

        if(petriNetGraph != null){
            petriNetGraph.stopTimer();
            petriNetGraph = null;
        }
    }

    public void clearDatabase(){
        n4JQueries.clearDatabase();
        Utils.showAlert("Application Message",Alert.AlertType.INFORMATION,"Neo4j Database has been deleted");
    }

    public void uploadCSV(){
        EventUpload eventUpload = new EventUpload(n4JQueries);
        eventUpload.startEventUpload();
        setLogList();
    }

    public EventHandler<Event> getLogDetails(){
        return event -> {
            String logName = lvLogs.getSelectionModel().getSelectedItem();
            Map<String, Object> mapLogDetails = n4JQueries.getLogDetails(logName);

            // The labels of these 3 attributes is modified to make them more intuitive in the UI
            Map<String,String> attributeLabels = new HashMap<String, String>(){{
                put("ID","Log Name");
                put("timestampCol","Timestamp");
                put("activityCol","Activity");
            }};

            tvLogDetails.getItems().clear();

            List<String> selAttributes = new ArrayList<>();
            for(Map.Entry<String,Object> attr : mapLogDetails.entrySet()){
                if(attr.getKey().equals("attributesSelected")){
                    // The string containing the list of attributes selected by the user is split and stored in
                    // an array so they can be displayed as individual items.
                    selAttributes.addAll(Arrays.asList(String.valueOf(attr.getValue()).split("\\|")));
                }else if(attributeLabels.containsKey(attr.getKey())){
                    String attrLabel = attributeLabels.get(attr.getKey());
                    tvLogDetails.getItems().add(new Utils.LogAttribute(attrLabel,String.valueOf(attr.getValue())));
                }else{
                    tvLogDetails.getItems().add(new Utils.LogAttribute(attr.getKey(),String.valueOf(attr.getValue())));
                }
            }

            int i = 1;
            for(String selAttr : selAttributes){
                tvLogDetails.getItems().add(new Utils.LogAttribute("Attribute_"+i,selAttr));
                i++;
            }

            tvLogDetails.sort();
        };
    }

    public EventHandler<Event> deleteLog(){
        return event -> {
            String logName = lvLogs.getSelectionModel().getSelectedItem();
            n4JQueries.deleteLog(logName);

            Utils.showAlert("Application Message",Alert.AlertType.INFORMATION, "Events from \""+logName+"\" have been deleted from the database");

            setLogList();
            tvLogDetails.getItems().clear();
        };
    }

    public EventHandler<Event> viewSelectedGraph(String graphDisplay){
        return event -> {
            removeGSReference();

            String logName = lvLogs.getSelectionModel().getSelectedItem();
            dataGraph = new DataGraph(hbProperties,labelNodeType,tvProperties,n4JQueries,logName);

            if(graphDisplay.equals("Instances")){
                dataGraph.addInstances(cbDF.isSelected(),logDF,cbEC.isSelected(),logEC);
            }else if(graphDisplay.equals("Model")){
                if(cbEntities.isSelected() && cbClasses.isSelected()) dataGraph.addEntitiesAndClasses(logEntities,logClasses);
                else if(cbEntities.isSelected()) dataGraph.addEntitiesModel(logEntities);
                else if(cbClasses.isSelected()) dataGraph.addClassesModel(logClasses);
            }
            border.setCenter(dataGraph.getGraphPane());
        };
    }

    public EventHandler<Event> entityFunction(String function){
        return event -> {
            String logName = lvLogs.getSelectionModel().getSelectedItem();
            EntityFunctions entityFunctions = new EntityFunctions(n4JQueries,logName);

            switch (function){
                case "Create Entity":
                    entityFunctions.createNewEntity();
                    break;
                case "Create Derived Entity":
                    entityFunctions.createNewDerivedEntity();
                    break;
                case "Return ET Attribute":
                    entityFunctions.returnETAttributes();
                    break;
            }
            updateLogData();
        };
    }

    public EventHandler<Event> classFunction(String function){
        return event -> {
            String logName = lvLogs.getSelectionModel().getSelectedItem();
            ClassFunctions classFunctions = new ClassFunctions(n4JQueries,logName);

            if(function.equals("Create Class"))
                classFunctions.createNewClass();

            updateLogData();
        };
    }

    public EventHandler<Event> algFunction(){
        return event -> {
            String logName = lvLogs.getSelectionModel().getSelectedItem();
            String algSelected = comboAlgs.getSelectionModel().getSelectedItem();

            ProcessModelUI processModelUI;
            try {
                Class<?> c = algorithmUIClass.get(algSelected);
                Constructor<?> constructor = c.getConstructor(N4JQueries.class, String.class);
                processModelUI = (ProcessModelUI) constructor.newInstance(n4JQueries,logName);
                processModelUI.parameterSelection();
            } catch (Exception e) {
                e.printStackTrace();
            }

            setModelList();
        };
    }

    public EventHandler<Event> getModelDetails(){
        return event -> {
            String modelName = lvModels.getSelectionModel().getSelectedItem();
            Map<String, Object> mapModelDetails = n4JQueries.getModelDetails(modelName);

            tvModelDetails.getItems().clear();

            for(Map.Entry<String,Object> attr : mapModelDetails.entrySet()){
                tvModelDetails.getItems().add(new Utils.LogAttribute(attr.getKey(),String.valueOf(attr.getValue())));
            }

            tvModelDetails.sort();
        };
    }

    public EventHandler<Event> viewSelectedModel(){
        return event -> {
            String modelName = lvModels.getSelectionModel().getSelectedItem();
            String algorithm = n4JQueries.getAlgorithmName(modelName);

            try {
                removeGSReference();

                Class<?> c = algorithmGraphClass.get(algorithm);
                Constructor<?> constructor = c.getConstructor(N4JQueries.class, String.class, TableView.class);
                gsGraph = (GSGraphBuilder) constructor.newInstance(n4JQueries,modelName,tvModelDetails);
                gsGraph.buildModelGraph();
                border.setCenter(gsGraph.getGraphPane());
            } catch (Exception e) {
                e.printStackTrace();
            }
            Utils.printTime("End ShowModel: ");
        };
    }

    public EventHandler<Event> viewPetriNet(){
        return event -> showPetriNet(lvModels.getSelectionModel().getSelectedItems());
    }

    public EventHandler<Event> deleteModel(){
        return event -> {
            String modelName = lvModels.getSelectionModel().getSelectedItem();
            n4JQueries.deleteModel(modelName);

            Utils.showAlert("Application Message",Alert.AlertType.INFORMATION, "Process model \""+modelName+"\" has been deleted from the database");

            setModelList();
            tvModelDetails.getItems().clear();
        };
    }

    public EventHandler<Event> modelSelection(){
        return event -> {
            try {
                ModelFinder modelFinder = new ModelFinder(n4JQueries);
                List<String> selectedModels = modelFinder.getModels();
                showPetriNet(selectedModels);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        };
    }

    public EventHandler<Event> editOptions(String type){
        return event -> {
            GraphDataView gdv;
            switch(type){
                case "DF":
                    gdv = new GraphDataView(type,logDF,lbDF);
                    break;
                case "EC":
                    gdv = new GraphDataView(type,logEC,lbEC);
                    break;
                case "Entities":
                    gdv = new GraphDataView(type,logEntities,lbEntities);
                    break;
                case "Classes":
                default:
                    gdv = new GraphDataView(type,logClasses,lbClasses);
                    break;
            }
            gdv.showEditWindow();
        };
    }

    public void createConstraints(){
        n4JQueries.createConstraints();
        disableConstraintButton();
        Utils.showAlert("Application Message",Alert.AlertType.INFORMATION,
                "The following constraints are now active on the database:\n" +
                        "\tUniqueLogs: ASSERT Log.ID is UNIQUE\n" +
                        "\tUniqueModels: ASSERT Model.ID is UNIQUE");
    }

    public void deleteConstraints(){
        n4JQueries.deleteConstraints();
        disableConstraintButton();
        Utils.showAlert("Application Message",Alert.AlertType.INFORMATION,
                "The following constraints are no longer active on the database:\n" +
                        "\tUniqueLogs: ASSERT Log.ID is UNIQUE\n" +
                        "\tUniqueModels: ASSERT Model.ID is UNIQUE");
    }

    /** updateLogData()
     * The information displayed on the UI is updated based on the selected log on the ListView lvLogs.
     * First, the label at the top left of the UI is updated with the name of the log selected.
     * Then, we run a query to identify the Entity Types and Classes that have already been generated
     * for that particular log. Based on this list, the Labels lbDF, lbEntities, lbEC, and lbClasses are
     * updated to show the correct numbers. The default number of selected items varies for each option.*/
    public void updateLogData(){
        String logName = lvLogs.getSelectionModel().getSelectedItem();

        if(logName == null) labelSelLog.setText("No Log Selected");
        else labelSelLog.setText("Log: "+logName);

        logDF.clear();
        logEntities.clear();
        List<String> existingEntities = n4JQueries.getExistingEntities(logName);
        if(existingEntities.size() == 0){
            lbDF.setText("(0/0)");
            lbEntities.setText("(0/0)");
        }else{
            boolean selected = true;
            for(String n : existingEntities){
                logDF.put(n.split(" ")[0],selected);
                if(selected) selected = false;
            }
            lbDF.setText("(1/"+existingEntities.size()+")");

            for(String n : existingEntities) logEntities.put(n.split(" ")[0],true);
            lbEntities.setText("("+existingEntities.size()+"/"+existingEntities.size()+")");
        }

        logEC.clear();
        logClasses.clear();
        Map<String,List<String>> existingClasses = n4JQueries.getExistingClasses(logName);
        if(existingClasses.size() == 0){
            lbEC.setText("(0/0)");
            lbClasses.setText("(0/0)");
        }else{
            boolean selected = true;
            for(String c : existingClasses.keySet()) {
                logEC.put(c,selected);
                if(selected) selected = false;
            }
            lbEC.setText("(1/"+existingClasses.size()+")");

            int totalDFCs = 0;
            for(String c : existingClasses.keySet()){
                selected = true;
                List<String> dfCList = existingClasses.get(c);
                totalDFCs += dfCList.size();
                for(String dfC : dfCList){
                    logClasses.put(c+","+dfC,selected);
                    if(selected) selected = false;
                }
            }
            lbClasses.setText("("+existingClasses.size()+"/"+totalDFCs+")");
        }
    }

    private void setLogList(){
        lvLogs.setItems(FXCollections.observableList(n4JQueries.getDBLogs()));
    }

    private void setModelList(){
        lvModels.setItems(FXCollections.observableList(n4JQueries.getExistingModels()));
    }

    private void disableConstraintButton(){
        if(n4JQueries.areConstraintsActive()){
            miCreateConstraints.setDisable(true);
            miDropConstraints.setDisable(false);
        }else{
            miCreateConstraints.setDisable(false);
            miDropConstraints.setDisable(true);
        }
    }

    private void showPetriNet(List<String> models){
        if(models.size() == 0) return;
        removeGSReference();

        String modelName = models.get(0);
        if(models.size() == 1){
            petriNetGraph = new PetriNetGraph(n4JQueries,modelName);
            petriNetGraph.buildModelGraph();
            border.setCenter(petriNetGraph.getGraphPane());
        }else{
            String modelName2 = models.get(1);

            compGraph = new ModelComparison(n4JQueries,modelName,modelName2);
            compGraph.buildModelGraph();
            border.setCenter(compGraph.getGraphPane());
        }
        Utils.printTime("End ShowPetriNet(s): ");
    }
}

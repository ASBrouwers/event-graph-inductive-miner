package EventGraphs;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    public static final String PATH = System.getProperty("user.dir");
    public static final String GRAPH_STYLE_FILE = PATH+"\\src\\EventGraphs\\resources\\GraphStyle.css";
    public static final String GUI_STYLE_FILE = "/EventGraphs/resources/GUIStyle.css";
    public static final String NEO4J_BAT_PATH = "\"C:\\Program Files\\neo4j-community-4.2.3\\bin\\neo4j.bat\"";
    public static final String NEO4J_IMPORT_PATH = "C:\\Program Files\\neo4j-community-4.2.3\\import";

    public static final String DEFAULT_URL = "bolt://localhost:7687";
    public static final String DEFAULT_DATABASE = "";
    public static final String DEFAULT_USER = "neo4j";
    public static final String DEFAULT_PASS = "password";

    public static final int GRAPH_WINDOW_SIZE_X = 600;
    public static final int GRAPH_WINDOW_SIZE_Y = 600;
    public static final int BUTTON_SIZE_X = 200;

    public static final String BACKGROUND_COLOR_DEFAULT = "#EEEEEE";
    public static final String BACKGROUND_COLOR_MAIN = "#336699";

    public static final String EVENT_NODE_COLOR = "#AAAAAA";
    public static final String[] ENTITY_COLORS = {"#003594", //Blue
                                                        "#ffd100", //Yellow
                                                        "#d43333", //Red
                                                        "#05982a", //Green
                                                        "#db6127", //Orange
                                                        "#1b9fcf", //Light Blue
                                                        "#d32f77", //Pink
                                                        "#532ea9"}; //Purple
    public static final double EDGE_WEIGHT = 1.5;

    public static final Map<String,String> ALGORITHM_LABEL = new HashMap<String,String>(){{
        put("HM", "Heuristic Miner");
        put("IM", "Inductive Miner");
    }};
    public static final int EVENT_SAMPLE_SIZE = 100;//Number of Event nodes to retrieve without Entity or Class relations
    public static final int ENTITY_SAMPLE_SIZE = 50;//Number of Event nodes to retrieve per EntityType
    public static final int CLASS_SAMPLE_SIZE = 3;//Number of Event nodes to retrieve per Class Type

    public static HBox createDefaultBorderTop(String labelText){
        HBox hbox = new HBox();
        hbox.setPadding(new Insets(15, 12, 15, 12));
        hbox.setSpacing(10);
        hbox.setPrefWidth(200);
        hbox.setStyle("-fx-background-color: "+Utils.BACKGROUND_COLOR_MAIN+";");

        Label label = new Label(labelText);
        label.setWrapText(true);
        label.setTextFill(Color.web("#FFFFFF"));
        hbox.getChildren().add(label);

        return hbox;
    }

    public static void showAlert(String title, Alert.AlertType type, String message){
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);

        Text text = new Text("\n"+message);
        text.setWrappingWidth(350);
        alert.getDialogPane().setContent(text);

        alert.showAndWait();
    }

    public static void printTime(String s){
        Date time = new java.util.Date(System.currentTimeMillis());
        System.out.println(s + new SimpleDateFormat("HH:mm:ss").format(time));
    }

    public static class PropertySamples{
        List<String> properties;
        Map<String,List<String>> propertySamples;

        public PropertySamples(List<String> properties, Map<String,List<String>> propertySamples){
            this.properties = properties;
            this.propertySamples = propertySamples;
        }

        public List<String> getProperties(){
            return properties;
        }

        public Map<String,List<String>> getPropertySamples(){
            return propertySamples;
        }
    }

    public static class LogAttribute{
        private String attrName;
        private String attrValue;

        public LogAttribute(String attrName, String attrValue){
            this.attrName = attrName;
            this.attrValue = attrValue;
        }

        public String getAttrName() {
            return attrName;
        }

        public String getAttrValue() {
            return attrValue;
        }
    }

    public static class Instance{
        private List<org.neo4j.driver.types.Node> typeNodes;
        private List<org.neo4j.driver.types.Relationship> typeRelations;
        private List<org.neo4j.driver.types.Node> eventNodes;
        private List<org.neo4j.driver.types.Relationship> eventRelations;

        public Instance(List<org.neo4j.driver.types.Node> types, List<org.neo4j.driver.types.Relationship> typeRels,
                        List<org.neo4j.driver.types.Node> events, List<org.neo4j.driver.types.Relationship> eventRels){
            this.typeNodes = types;
            this.typeRelations = typeRels;
            this.eventNodes = events;
            this.eventRelations = eventRels;
        }

        public List<org.neo4j.driver.types.Node> getTypeNodes(){
            return  typeNodes;
        }

        public List<org.neo4j.driver.types.Relationship> getTypeRelations(){
            return  typeRelations;
        }

        public List<org.neo4j.driver.types.Node> getEventNodes(){
            return  eventNodes;
        }

        public List<org.neo4j.driver.types.Relationship> getEventRelations(){
            return  eventRelations;
        }
    }
}

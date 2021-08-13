package EventGraphs.Graphstream;

public class GSConfig {
    public static void setSystemProperties(){
        System.setProperty("org.graphstream.ui", "javafx");
        System.setProperty("org.graphstream.debug","true");
    }
}

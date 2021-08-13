package EventGraphs.Neo4j;

import EventGraphs.GUI.Neo4jConnectionStatus;
import EventGraphs.Utils;
import javafx.application.Platform;
import org.neo4j.driver.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.*;

public class N4JConfig {
    Driver driver;

    public N4JConfig(){
        driver = null;
    }

    public Driver getDriver(){
        return this.driver;
    }

    public static String getNeo4jUrl() {
        String url = System.getenv("NEO4J_URL");
        if (url == null || url.isEmpty()) {
            return Utils.DEFAULT_URL;
        }
        return url;
    }

    public static String getNeo4jUsername() {
        String user = System.getenv("NEO4J_USER");
        if (user == null || user.isEmpty()) {
            return Utils.DEFAULT_USER;
        }
        return user;
    }

    public static String getNeo4jPassword() {
        String password = System.getenv("NEO4J_PASSWORD");
        if (password == null || password.isEmpty()) {
            return Utils.DEFAULT_PASS;
        }
        return password;
    }

    public void startDriver(){
        String n4jUrl = getNeo4jUrl();
        String n4jUser = getNeo4jUsername();
        String n4jPassword = getNeo4jPassword();

        driver = GraphDatabase.driver(n4jUrl, AuthTokens.basic( n4jUser, n4jPassword ));
    }

    public void startConnection(Neo4jConnectionStatus n4jConStatus) throws Exception{
        n4jConStatus.appendStatusUpdate("Checking if Neo4j is running...\n");
        String pid = getNeo4jPID();
        if(!pid.equals("")){
            startDriver();
            n4jConStatus.appendStatusUpdate("Neo4j has been started (PID:"+pid+")...\n");
            n4jConStatus.appendStatusUpdate("This window can be closed now.\n");
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(new StartNeo4j());

        try {
            n4jConStatus.appendStatusUpdate("Attempting to start Neo4j...\n");
            String callResult = future.get(120, TimeUnit.SECONDS);
            n4jConStatus.appendStatusUpdate(callResult);
            if(callResult.contains("Remote interface available")){
                startDriver();
                pid = getNeo4jPID();
                n4jConStatus.appendStatusUpdate("Neo4j started on PID:"+pid+"...\n");
                n4jConStatus.appendStatusUpdate("This window can be closed now.\n");
            }else
                n4jConStatus.getStatusStage().setOnCloseRequest(event -> {
                    Platform.exit();
                    System.exit(0);
                });
        } catch (TimeoutException e) {
            future.cancel(true);
            killNeo4jProcess();
            n4jConStatus.appendStatusUpdate("Neo4j startup timeout...\n");
            n4jConStatus.getStatusStage().setOnCloseRequest(event -> {
                Platform.exit();
                System.exit(0);
            });
        }

        executor.shutdownNow();
    }

    static class StartNeo4j implements Callable<String> {
        @Override
        public String call() throws Exception {
            String neo4jStartCommand = Utils.NEO4J_BAT_PATH + " console";

            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", neo4jStartCommand);
            builder.redirectErrorStream(true);
            Process p = builder.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            StringBuilder status = new StringBuilder();
            while (true) {
                line = r.readLine();
                if (line == null) break;

                status.append(line).append("\n");
                if(line.contains("Remote interface available")) break;
            }

            return status.toString();
        }
    }

    private static String getNeo4jPID() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", "netstat -n -a -o | find \"7474\"");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line;
        line = r.readLine();

        String pid = "";
        if(line != null){
            String[] arrayLine = line.split(" ");
            pid = arrayLine[arrayLine.length-1];
        }

        return pid;
    }

    public static void killNeo4jProcess() throws Exception {
        String pid = getNeo4jPID();

        if(!pid.equals("")){
            Runtime.getRuntime().exec("taskkill /F /PID " + pid);
        }
    }
}
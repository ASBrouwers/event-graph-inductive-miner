package EventGraphs.Neo4j.ProcessDiscovery;

import org.neo4j.driver.Driver;

import java.util.Map;

public abstract class DiscoveryAlgorithm {
    String modelName;
    Driver driver;

    public abstract void generateProcessModel(Map<String,String> modelParams);
    public abstract void generatePetriNet();

    public String getModelName(){
        return modelName;
    }
}

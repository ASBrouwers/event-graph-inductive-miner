package EventGraphs.Neo4j.ProcessDiscovery;

import EventGraphs.Neo4j.N4JQueries;
import org.neo4j.driver.Driver;

import java.util.Map;

public abstract class DiscoveryAlgorithm {
    String modelName;
    protected Driver driver;

    public DiscoveryAlgorithm(Driver drv) {
        this.driver = drv;
    }

    public abstract void generateProcessModel(Map<String,String> modelParams);
    public abstract void generatePetriNet();

    public String getModelName(){
        return modelName;
    }
}

package ebi.spot.neo4j2owl.exporter;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure that wraps the return value of neo4j2owl
 */
public class N2OReturnValue {
    public List<String> o;
    public String log;

    public N2OReturnValue() {
    	o = new ArrayList<>();
    }

    public void setOntology(List<String> o) {
        this.o = o;
    }
    
    public void addOntologyChunk(String chunk) {
        this.o.add(chunk);
    }

    public void setLog(String log) {
        this.log = log;
    }
}

package ebi.spot.neo4j2owl.exporter;

/**
 * Datastructure that wraps the returnvalue of neo4j2owl
 */
public class N2OReturnValue {
    public String o;
    public String log;

    public N2OReturnValue() {

    }

    public void setOntology(String o) {
        this.o = o;
    }

    public void setLog(String log) {
        this.log = log;
    }

    @Override
    public String toString() {
        return o;
    }
}

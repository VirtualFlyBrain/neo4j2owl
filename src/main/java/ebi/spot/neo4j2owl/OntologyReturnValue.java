package ebi.spot.neo4j2owl;

public class OntologyReturnValue {
    public final String o;
    public final String log;

    public OntologyReturnValue(String o, String log) {
        this.o = o;
        this.log = log;
    }

    @Override
    public String toString() {
        return o;
    }
}

package ebi.spot.neo4j2owl;

/**
 * Datastructure that wraps the returnvalue of neo4j2owl
 */
public class N2OReturnValue {
    public final String o;
    public final String log;

    public N2OReturnValue(String o, String log) {
        this.o = o;
        this.log = log;
    }

    @Override
    public String toString() {
        return o;
    }
}

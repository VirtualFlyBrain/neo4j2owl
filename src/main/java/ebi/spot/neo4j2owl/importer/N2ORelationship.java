package ebi.spot.neo4j2owl.importer;

import java.util.Map;
import java.util.Set;

class N2ORelationship {
    private final N2OEntity start;
    private final N2OEntity end;
    private final String relation;
    private final Map<String, Set<Object>> props;

    N2ORelationship(N2OEntity iri_start, N2OEntity iri_end, String relation,Map<String, Set<Object>> props) {
        this.start = iri_start;
        this.end = iri_end;
        this.relation = relation;
        this.props = props;
    }

    N2OEntity getStart() {
        return start;
    }
    N2OEntity getEnd() {
        return end;
    }
    String getRelationId() {
        return relation;
    }
    Map<String, Set<Object>> getProps() {
        return props;
    }

}

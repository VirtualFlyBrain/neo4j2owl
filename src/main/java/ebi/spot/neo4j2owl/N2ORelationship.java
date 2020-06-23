package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;

import java.util.HashMap;
import java.util.Map;

public class N2ORelationship {
    private final N2OEntity start;
    private final N2OEntity end;
    private final String relation;
    private final Map<String, Object> props;

    N2ORelationship(N2OEntity iri_start, N2OEntity iri_end, String relation,Map<String, Object> props) {
        this.start = iri_start;
        this.end = iri_end;
        this.relation = relation;
        this.props = props;
    }

    public N2OEntity getStart() {
        return start;
    }
    public N2OEntity getEnd() {
        return end;
    }
    public String getRelationId() {
        return relation;
    }
    public Map<String, Object> getProps() {
        return props;
    }

}

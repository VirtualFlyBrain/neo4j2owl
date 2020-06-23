package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;

import java.util.HashMap;
import java.util.Map;

public class N2OOWLRelationship {
    private final OWLEntity start;
    private final OWLEntity end;
    private final String relation;
    N2OOWLRelationship(OWLEntity iri_start, OWLEntity iri_end, String relation) {
        this.start = iri_start;
        this.end = iri_end;
        this.relation = relation;
    }

    public OWLEntity getStart() {
        return start;
    }
    public OWLEntity getEnd() {
        return end;
    }
    public String getRelationId() {
        return relation;
    }

}

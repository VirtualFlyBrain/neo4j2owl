package ebi.spot.neo4j2owl.importer;

import org.semanticweb.owlapi.model.OWLEntity;

class N2OOWLRelationship {
    private final OWLEntity start;
    private final OWLEntity end;
    private final String relation;
    N2OOWLRelationship(OWLEntity iri_start, OWLEntity iri_end, String relation) {
        this.start = iri_start;
        this.end = iri_end;
        this.relation = relation;
    }

    OWLEntity getStart() {
        return start;
    }
    OWLEntity getEnd() {
        return end;
    }
    String getRelationId() {
        return relation;
    }

}

package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.Map;

public class N2OEntity {
    private final String ns;
    private final String iri;
    private final String safe_label;
    private final String qualified_safe_label;
    private final String label;
    private final String type;
    private final String short_form;
    private final String curie;
    private final OWLEntity entity;


    N2OEntity(OWLEntity e, OWLOntology o, CurieManager curies) {
        iri = e.getIRI().toString();
        safe_label = curies.getSafeLabel(e,o);
        label = curies.getLabel(e,o);
        type = OWL2NeoMapping.getNeoType(e);
        ns = curies.getNamespace(e);
        qualified_safe_label = curies.getQualifiedSafeLabel(e,o);
        short_form = curies.getShortForm(e);
        curie = curies.getCurie(e);
        entity = e;
    }


    public String getIri() {
        return iri;
    }

    public String getSafe_label() {
        return safe_label;
    }

    public String getQualified_safe_label() {
        return qualified_safe_label;
    }

    public String getShort_form() {
        return short_form;
    }

    public String getCurie() {
        return curie;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }


    public String toString() {
        return "E[IRI:"+getIri()+"|SL:"+getSafe_label()+"]";
    }

    public OWLEntity getEntity() {
        return entity;
    }
}

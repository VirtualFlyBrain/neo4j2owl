package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.Map;

public class N2OEntity {
    private final String iri;
    private final String safe_label;
    private final String label;
    private final String type;


    N2OEntity(OWLEntity e, OWLOntology o, CurieManager curies) {
        iri = e.getIRI().toString();
        safe_label = curies.generateSafeLabel(e,o);
        label = Util.getPreferredLabel(e,o);
        type = OWL2NeoMapping.getNeoType(e);
    }


    public String getIri() {
        return iri;
    }

    public String getSafe_label() {
        return safe_label;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public String getSafeNormalised_label() {
        return getSafe_label().replaceAll("[^A-Za-z0-9]","_");
    }

    public String toString() {
        return "E[IRI:"+getIri()+"|SL:"+getSafe_label()+"]";
    }
}

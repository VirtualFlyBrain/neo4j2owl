package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OStatic;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class N2OEntity {
    private final String ns;
    private final String iri;
    private final String safe_label;
    private final String qualified_safe_label;
    private final String label;
    private final Set<String> types;
    private final String short_form;
    private final String curie;
    private final OWLEntity entity;


    N2OEntity(OWLEntity e, OWLOntology o, IRIManager curies) {
        iri = e.getIRI().toString();
        safe_label = curies.getSafeLabel(e,o);
        label = curies.getLabel(e,o);
        types = new HashSet<>();
        types.add(N2OStatic.getNeoType(e));
        ns = curies.getNamespace(e.getIRI());
        qualified_safe_label = curies.getQualifiedSafeLabel(e,o);
        short_form = curies.getShortForm(e.getIRI());
        curie = curies.getCurie(e);
        entity = e;
    }

    Map<String, Object> getNodeBuiltInMetadataAsMap() {
        Map<String, Object> props = new HashMap<>();
        props.put(N2OStatic.ATT_LABEL, getLabel());
        props.put(N2OStatic.ATT_SAFE_LABEL, getSafe_label());
        props.put(N2OStatic.ATT_QUALIFIED_SAFE_LABEL, getQualified_safe_label());
        props.put(N2OStatic.ATT_SHORT_FORM, getShort_form());
        props.put(N2OStatic.ATT_CURIE, getCurie());
        props.put(N2OStatic.ATT_IRI, getIri());
        return props;
    }

    public String getIri() {
        return iri;
    }

    String getSafe_label() {
        return safe_label;
    }

    String getQualified_safe_label() {
        return qualified_safe_label;
    }

    private String getShort_form() {
        return short_form;
    }

    private String getCurie() {
        return curie;
    }

    public String getLabel() {
        return label;
    }

    Set<String> getTypes() {
        return types;
    }

    @Override
    public String toString() {
        return "N2OEntity{" +
                "ns='" + ns + '\'' +
                ", iri='" + iri + '\'' +
                ", safe_label='" + safe_label + '\'' +
                ", qualified_safe_label='" + qualified_safe_label + '\'' +
                ", label='" + label + '\'' +
                ", type='" + types + '\'' +
                ", short_form='" + short_form + '\'' +
                ", curie='" + curie + '\'' +
                '}';
    }

    public OWLEntity getEntity() {
        return entity;
    }

    String getEntityType() {
        if (getEntity() instanceof OWLAnnotationProperty) {
            return "Annotation";
        } else if (getEntity() instanceof OWLObjectProperty) {
            return "Related";
        }
        return "";
    }

    void addLabels(Set<String> labels) {
        types.addAll(labels);
    }
}

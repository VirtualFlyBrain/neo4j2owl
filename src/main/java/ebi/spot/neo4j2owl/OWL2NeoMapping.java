package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.*;

public class OWL2NeoMapping {
    public static final String NODETYPE_NAMEDINDIVIDUAL = "Individual";
    public static final String NODETYPE_OWLCLASS = "Class";
    public static final String NODETYPE_OWLOBJECTPROPERTY = "ObjectProperty";
    public static final String NODETYPE_OWLANNOTATIONPROPERTY = "AnnotationProperty";
    public static final String NODETYPE_OWLDATAPROPERTY = "DataProperty";
    public static final String RELTYPE_SUBCLASSOF = "SubClassOf";
    public static final String RELTYPE_INSTANCEOF = "Type";

    public static final String ATT_LABEL = "label";
    public static final String ATT_SAFE_LABEL = "sl";
    public static final String ATT_QUALIFIED_SAFE_LABEL = "qsl";
    public static final String ATT_CURIE = "curie";
    public static final String ATT_IRI = "iri";
    public static final String ATT_SHORT_FORM = "short_form";
    public static final String ATT_NAMESPACE = "ns";
    public static final String ANNOTATION_DELIMITER  = "~|§";


    public static String getNeoType(OWLEntity e) {
        if(e instanceof OWLClass) {
            return OWL2NeoMapping.NODETYPE_OWLCLASS;
        } else if(e instanceof OWLIndividual) {
            return OWL2NeoMapping.NODETYPE_NAMEDINDIVIDUAL;
        } else if(e instanceof OWLObjectProperty) {
            return OWL2NeoMapping.NODETYPE_OWLOBJECTPROPERTY;
        } else if(e instanceof OWLAnnotationProperty) {
            return OWL2NeoMapping.NODETYPE_OWLANNOTATIONPROPERTY;
        } else if(e instanceof OWLDataProperty) {
            return OWL2NeoMapping.NODETYPE_OWLDATAPROPERTY;
        }
        return "UnknownType";
    }


}

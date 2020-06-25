package ebi.spot.neo4j2owl.importer;

import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.model.*;

import java.util.Map;

public class N2OEntityChecker implements OWLEntityChecker {

    private final Map<String, OWLEntity> entities;

    N2OEntityChecker(Map<String, OWLEntity> entities) {
        this.entities = entities;
    }

    @Override
    public OWLObjectProperty getOWLObjectProperty(String name) {
        OWLEntity o = entities.get(name);
        if (o != null && o.isOWLObjectProperty()) {
            return o.asOWLObjectProperty();
        }
        return null;
    }

    @Override
    public OWLNamedIndividual getOWLIndividual(String name) {
        return null;
    }

    @Override
    public OWLDatatype getOWLDatatype(String name) {
        return null;
    }

    @Override
    public OWLDataProperty getOWLDataProperty(String name) {
        return null;
    }

    @Override
    public OWLClass getOWLClass(String name) {
        OWLEntity o = entities.get(name);
        if (o != null && o.isOWLClass()) {
            return o.asOWLClass();
        }
        return null;
    }

    @Override
    public OWLAnnotationProperty getOWLAnnotationProperty(String name) {
        return null;
    }
}

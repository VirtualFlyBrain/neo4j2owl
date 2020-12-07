package ebi.spot.neo4j2owl.importer;

import org.semanticweb.owlapi.model.*;

public class N2OImportResult {

    public long classesLoaded = 0;
    public long individualsLoaded = 0;
    public long objPropsLoaded = 0;
    public long annotationPropertiesloaded = 0;
    public long dataPropsLoaded = 0;
    public String terminationStatus = "OK";
    public long elementsLoaded = 0;
    public String extraInfo = "";

    void countLoaded(OWLEntity e) {
        if (e instanceof OWLClass) {
            classesLoaded++;
        } else if (e instanceof OWLNamedIndividual) {
            individualsLoaded++;
        } else if (e instanceof OWLObjectProperty) {
            objPropsLoaded++;
        } else if (e instanceof OWLDataProperty) {
            dataPropsLoaded++;
        } else if (e instanceof OWLAnnotationProperty) {
            annotationPropertiesloaded++;
        }
        elementsLoaded++;
    }

    public void setTerminationKO(String message) {
        this.terminationStatus = "KO";
        this.extraInfo = message;
    }
}

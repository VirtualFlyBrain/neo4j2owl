package ebi.spot.neo4j2owl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.*;
import java.util.stream.Stream;

/**
 * Created by jbarrasa on 21/03/2016.
 *
 * Importer of basic ontology (RDFS & OWL) elements:
 *
 */
public class OWL2OntologyImporter {

    @Context
    public GraphDatabaseService db;
    public static OWLDataFactory df = OWLManager.getOWLDataFactory();
    public static final String PREFIX_SEPARATOR = "__";
    public static final SaveLabelSupplier saveLabelSupplier = new SaveLabelSupplier();

    /*
    Constants
    */
    private static final String NODETYPE_NAMEDINDIVIDUAL = "NamedIndividual";
    private static final String NODETYPE_OWLCLASS = "Class";
    private static final String NODETYPE_OWLOBJECTPROPERTY = "ObjectProperty";
    private static final String NODETYPE_OWLANNOTATIONPROPERTY = "AnnotationProperty";
    private static final String ATT_LABEL = "label";
    private static final String SAVE_LABEL = "sl";

    @Procedure(mode = Mode.WRITE)
    public Stream<ImportResults> owl2Import(@Name("url") String url, @Name("format") String format) {
        ImportResults importResults = new ImportResults();
        int classesLoaded = 0;
        int individualsLoaded = 0;
        int objPropsLoaded = 0;
        try {
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(IRI.create(url));
            OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
            individualsLoaded = extractSignature(o,r);
            extractIndividualAnnotations(o,r);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            importResults.setElementsLoaded(classesLoaded + individualsLoaded + objPropsLoaded);
        }
        return Stream.of(importResults);
    }

    private int extractSignature(OWLOntology o, OWLReasoner r) {
        int loaded = 0;
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for ( OWLEntity e : entities) {
            String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                    getEntityTypeNeo(e),
                    e.getIRI().toString());
            Map<String, Object> props = new HashMap<>();
            props.put(SAVE_LABEL,saveLabelSupplier.getSaveLabel(e,o));
            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
            loaded++;
        }
        return loaded;
    }

    private String getEntityTypeNeo(OWLEntity e) {
        if(e instanceof OWLClass) {
            return NODETYPE_OWLCLASS;
        }
        if(e instanceof OWLNamedIndividual) {
            return NODETYPE_NAMEDINDIVIDUAL;
        }
        if(e instanceof OWLObjectProperty) {
            return NODETYPE_OWLOBJECTPROPERTY;
        }
        if(e instanceof OWLAnnotationProperty) {
            return NODETYPE_OWLANNOTATIONPROPERTY;
        }
        return "UnknownType";
    }

    private int extractIndividualAnnotations(OWLOntology o, OWLReasoner r) {
        // loads properties
        int loaded = 0;
        Set<OWLEntity> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));

        for ( OWLEntity e : entities) {
            String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                    NODETYPE_OWLCLASS,
                    e.getIRI().toString());
            Map<String, Object> props = new HashMap<>();

            Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e,o);

            for(OWLAnnotation a:annos)
            {
                props.put(saveLabelSupplier.getSaveLabel(a.getProperty(),o),a.annotationValue().asLiteral().or(df.getOWLLiteral("unknownX")).getLiteral());
            }
            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
            loaded++;
        }
        return loaded;
    }


    public static class ImportResults {
        public String terminationStatus = "OK";
        public long elementsLoaded = 0;
        public String extraInfo = "";

        public void setElementsLoaded(long elementsLoaded) {
            this.elementsLoaded = elementsLoaded;
        }

        public void setTerminationKO(String message) {
            this.terminationStatus = "KO";
            this.extraInfo = message;
        }

    }
}

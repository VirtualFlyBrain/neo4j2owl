package ebi.spot.neo4j2owl;

import com.google.common.base.Optional;
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
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.util.*;
import java.util.stream.Stream;

/**
 * Created by jbarrasa on 21/03/2016.
 * <p>
 * Importer of basic ontology (RDFS & OWL) elements:
 */
public class OWL2OntologyImporter {

    @Context
    public GraphDatabaseService db;
    public static OWLDataFactory df = OWLManager.getOWLDataFactory();
    public static final String PREFIX_SEPARATOR = "__";
    public static final CurieManager curies = new CurieManager();
    public static Map<OWLEntity, N2OEntity> nodemap = new HashMap<>();
    public static Set<OWLClass> filterout = new HashSet<>();

    static Map<N2OEntity, Map<String, Set<N2OEntity>>> existential = new HashMap<>();

    static int classesLoaded = 0;
    static int individualsLoaded = 0;
    static int objPropsLoaded = 0;
    static int annotationPropertiesloaded = 0;
    static int dataPropsLoaded = 0;

    /*
    Constants
    */


    @Procedure(mode = Mode.WRITE)
    public Stream<ImportResults> owl2Import(@Name("url") String url, @Name("format") String format) {
        ImportResults importResults = new ImportResults();

        try {
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(IRI.create(url));
            OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLThing());
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
            extractSignature(o, r);
            extractIndividualAnnotationsToLiterals(o, r);
            addSubclassRelations(o, r);
            addClassAssertions(o, r);
            addExistentialRelationships(o, r);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            importResults.setElementsLoaded(classesLoaded + individualsLoaded + objPropsLoaded + annotationPropertiesloaded + dataPropsLoaded);
        }
        return Stream.of(importResults);
    }

    private void addSubclassRelations(OWLOntology o, OWLReasoner r) {
        Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
        for (OWLClass e : entities) {
            if (filterout.contains(e)) {
                continue;
            }
            for (OWLClass sub : r.getSubClasses(e, true).getFlattened()) {
                if (filterout.contains(sub)) {
                    continue;
                }
                Map<String, Object> props = new HashMap<>();
                updateRelationship(nodemap.get(sub), nodemap.get(e), OWL2NeoMapping.RELTYPE_SUBCLASSOF, props);
            }
        }
    }

    private void addExistentialRelationships(OWLOntology o, OWLReasoner r) {
        getConnectedEntities(r, o);
        for (N2OEntity e : existential.keySet()) {
            if (filterout.contains(e)) {
                continue;
            }
            for (String rel : existential.get(e).keySet()) {
                for (N2OEntity ec : existential.get(e).get(rel)) {

                    Map<String, Object> props = new HashMap<>();
                    updateRelationship(e, ec, rel, props);
                }
            }
        }
    }

    private void getConnectedEntities(OWLReasoner r, OWLOntology o) {

        for (OWLAxiom ax : o.getAxioms(Imports.INCLUDED)) {
            if (ax instanceof OWLSubClassOfAxiom) {
                // CLASS-CLASS: Simple existential "class" restrictions on classes
                // CLASS-INDIVIDUAL: Simple existential "individual" restrictions on classes
                OWLSubClassOfAxiom sax = (OWLSubClassOfAxiom) ax;
                OWLClassExpression s_super = sax.getSuperClass();
                OWLClassExpression s_sub = sax.getSubClass();
                if (s_sub.isClassExpressionLiteral()) {
                    if (s_super instanceof OWLObjectSomeValuesFrom) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) s_super, s_sub.asOWLClass());
                    } else if(s_super instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom)((OWLObjectHasValue) s_super).asSomeValuesFrom(), s_sub.asOWLClass());
                    }
                }
            } else if (ax instanceof OWLEquivalentClassesAxiom) {
                // CLASS-CLASS: Simple existential "class" restrictions on classes
                // CLASS-INDIVIDUAL: Simple existential "individual" restrictions on classes
                OWLEquivalentClassesAxiom eqax = (OWLEquivalentClassesAxiom) ax;
                Set<OWLClass> names = new HashSet<>();
                eqax.getClassExpressions().stream().filter(OWLClassExpression::isClassExpressionLiteral).forEach(e -> names.add(e.asOWLClass()));
                for (OWLClass c : names) {
                    for (OWLClassExpression e : eqax.getClassExpressionsAsList()) {
                        if (e instanceof OWLObjectSomeValuesFrom) {
                            processExistentialRestriction((OWLObjectSomeValuesFrom) e, c);
                        }
                    }
                }
            } else if (ax instanceof OWLClassAssertionAxiom) {
                // INDIVIDUAL-CLASS: Simple existential "individual" restrictions on individuals
                OWLClassAssertionAxiom eqax = (OWLClassAssertionAxiom) ax;
                OWLIndividual i = eqax.getIndividual();
                if(i.isNamed()) {
                    OWLClassExpression type = eqax.getClassExpression();
                    if(type instanceof OWLObjectSomeValuesFrom) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) type, i.asOWLNamedIndividual());
                    } else if(type instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom)((OWLObjectHasValue) type).asSomeValuesFrom(), i.asOWLNamedIndividual());
                    }
                }
            } else if(ax instanceof OWLObjectPropertyAssertionAxiom) {
                // INDIVIDUAL-INDIVIDUAL Object Property Assertion
                OWLObjectPropertyAssertionAxiom eqax = (OWLObjectPropertyAssertionAxiom) ax;
                OWLIndividual from = eqax.getSubject();
                if(from.isNamed()) {
                    OWLIndividual to = eqax.getObject();
                    if(to.isNamed()) {
                        if(!eqax.getProperty().isAnonymous()) {
                            indexRelation(from.asOWLNamedIndividual(), to.asOWLNamedIndividual(), nodemap.get(eqax.getProperty().asOWLObjectProperty()));
                        }
                    }
                }
            }
        }
    }

    private void processExistentialRestriction(OWLObjectSomeValuesFrom s_super, OWLEntity s_sub) {
        OWLObjectSomeValuesFrom svf = s_super;
        if (!svf.getProperty().isAnonymous() ) {
            OWLObjectProperty op = svf.getProperty().asOWLObjectProperty();
            OWLClassExpression filler = svf.getFiller();
            if(filler.isClassExpressionLiteral()) {
                // ENTITY-CLASS: A SubClassOf R some B, i:R some B
                OWLClass c = svf.getFiller().asOWLClass();
                indexRelation(s_sub, c, nodemap.get(op));
            } else {
                // ENTITY-INDIVIDUAL: A SubClassOf R some {i}, i: R some {j}
                if(filler instanceof OWLObjectOneOf) {
                    OWLObjectOneOf ce =(OWLObjectOneOf)filler;
                    if(ce.getIndividuals().size()==1) { // If there is more than one, we cannot assume a relationship.
                        for (OWLIndividual i : ce.getIndividuals()) {
                            if (i.isNamed()) {
                                indexRelation(s_sub,i.asOWLNamedIndividual(),nodemap.get(op));
                            }
                        }
                    }
                }
            }
        }
    }

    private void indexRelation(OWLEntity from, OWLEntity to, N2OEntity rel) {
        if (filterout.contains(from)) {
            return;
        } else if (filterout.contains(to)) {
            return;
        }
        N2OEntity from_n = nodemap.get(from);
        N2OEntity to_n = nodemap.get(to);
        String roletype = rel.getSafeNormalised_label();


        if (!existential.containsKey(from_n)) {
            existential.put(from_n, new HashMap<>());
        }
        if (!existential.get(from_n).containsKey(roletype)) {
            existential.get(from_n).put(roletype, new HashSet<>());
        }
        existential.get(from_n).get(roletype).add(to_n);
    }

    private void addClassAssertions(OWLOntology o, OWLReasoner r) {
        Set<OWLNamedIndividual> entities = new HashSet<>(o.getIndividualsInSignature(Imports.INCLUDED));
        for (OWLNamedIndividual e : entities) {
            if (filterout.contains(e)) {
                continue;
            }
            for (OWLClass type : r.getTypes(e, true).getFlattened()) {
                if (filterout.contains(type)) {
                    continue;
                }
                Map<String, Object> props = new HashMap<>();
                updateRelationship(nodemap.get(e), nodemap.get(type), OWL2NeoMapping.RELTYPE_INSTANCEOF, props);
            }
        }
    }

    private void extractSignature(OWLOntology o, OWLReasoner r) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for (OWLEntity e : entities) {
            N2OEntity ne = new N2OEntity(e, o, curies);
            nodemap.put(e, ne);
            Map<String, Object> props = new HashMap<>();
            props.put(OWL2NeoMapping.ATT_LABEL, ne.getLabel());
            props.put(OWL2NeoMapping.SAVE_LABEL, ne.getSafe_label());
            updatePropertyAttributes(ne, props);
            countLoaded(e);
        }
    }

    private void extractIndividualAnnotationsToLiterals(OWLOntology o, OWLReasoner r) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for (OWLEntity e : entities) {
            Map<String, Object> props = new HashMap<>();
            Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
            for (OWLAnnotation a : annos) {
                OWLAnnotationValue aval = a.annotationValue();
                if (!aval.isIRI()) {
                    props.put(neoPropertyKey(o, a), aval.asLiteral().or(df.getOWLLiteral("unknownX")).getLiteral());
                } else {
                    IRI iri = aval.asIRI().or(IRI.create("WRONGANNOTATIONPROPERTY"));
                    indexRelation(e,typedEntity(iri,o),nodemap.get(a.getProperty()));
                }
            }
            updatePropertyAttributes(nodemap.get(e), props);
        }
    }

    private OWLEntity typedEntity(IRI iri, OWLOntology o) {
        for(OWLEntity e:nodemap.keySet()) {
          if(e.getIRI().equals(iri)) {
              return e;
          }
        }
        OWLClass c = df.getOWLClass(iri);
        nodemap.put(c,new N2OEntity(c,o,curies));
        return c;
    }

    private void updatePropertyAttributes(N2OEntity e, Map<String, Object> props) {
        String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                e.getType(),
                e.getIri());
        Map<String, Object> params = new HashMap<>();
        params.put("props", props);
        db.execute(cypher, params);
    }

    private void updateRelationship(N2OEntity start_neo, N2OEntity end_neo, String rel, Map<String, Object> props) {
        String cypher = String.format(
                "MATCH (p { uri:'%s'}), (c { uri:'%s'}) CREATE (p)-[:%s]->(c)",
                // c can be a class or an object property
                start_neo.getIri(), end_neo.getIri(), rel);

        Map<String, Object> params = new HashMap<>();
        params.put("props", props);
        db.execute(cypher, params);
    }

    private String neoPropertyKey(OWLOntology o, OWLAnnotation a) {
        return curies.generateSafeLabel(a.getProperty(), o);
    }

    private void countLoaded(OWLEntity e) {
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

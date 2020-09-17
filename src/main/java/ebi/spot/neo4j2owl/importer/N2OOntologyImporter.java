package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import ebi.spot.neo4j2owl.exporter.N2OException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

class N2OOntologyImporter {

    private Set<OWLEntity> filterout = new HashSet<>();
    private N2OImportManager manager;
    private N2OLog log = N2OLog.getInstance();
    private GraphDatabaseAPI dbapi;
    private GraphDatabaseService db;
    private RelationTypeCounter relationTypeCounter;

    N2OOntologyImporter(GraphDatabaseAPI dbapi, GraphDatabaseService db) {
        this.dbapi = dbapi;
        this.db = db;
    }

    /**
     * Imports an ontology into neo4j
     *
     * @param exService executor services for concurrent processing of batches
     * @param importdir neo4j import dir location for storing the CSV files
     * @param o         ontology object that contains the axioms to be processed
     * @throws IOException thrown when CSV cant be read for some reason
     * @throws InterruptedException thrown when import takes too long
     * @throws java.util.concurrent.ExecutionException thrown when individual CSV load failed for some reason
     */
    void importOntology(ExecutorService exService, File importdir, OWLOntology o, N2OImportResult result) throws IOException, InterruptedException, ExecutionException, N2OException {
        IRIManager iriManager = new IRIManager();
        manager = new N2OImportManager(o, iriManager);
        this.relationTypeCounter = new RelationTypeCounter(N2OConfig.getInstance().getRelationTypeThreshold());

        log.log("Preparing reasoner");
        OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
        filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLThing());
        filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
        filterout.addAll(r.getUnsatisfiableClasses().getEntities());

        log.log("Extracting signature");
        extractSignature(o, result);
        log.log("Extracting annotations to literals");
        indexIndividualAnnotationsToEntities(o);
        log.log("Extracting subclass relations");
        addSubclassRelations(o, r);
        log.log("Extracting class assertions");
        addClassAssertions(o, r);
        log.log("Extracting existential relations");
        addExistentialRelationships(o, r);
        log.log("Computing dynamic node labels..");
        addDynamicNodeLabels(r);
        if (N2OConfig.getInstance().isBatch()) {
            log.log("Loading in Database: " + importdir.getAbsolutePath());

            N2OCSVWriter csvWriter = new N2OCSVWriter(manager,importdir);
            csvWriter.exportOntologyToCSV();

            exService.submit(() -> {
                String cypher = "CREATE INDEX ON :Entity(iri)";
                try {
                    dbapi.execute(cypher);
                } catch (QueryExecutionException e) {
                    throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE+cypher, e);
                }
                return N2OStatic.CYPHER_EXECUTED_SUCCESSFULLY+cypher;
            });

            log.log("Loading nodes to neo from CSV.");
            N2ONeoCSVLoader csvLoader = new N2ONeoCSVLoader(dbapi,manager,relationTypeCounter);
            csvLoader.loadNodesToNeoFromCSV(exService, importdir);

            log.log("Loading relationships to neo from CSV.");
            csvLoader.loadRelationshipsToNeoFromCSV(exService, importdir);
            log.log("Loading done..");
        }
    }

    private void addDynamicNodeLabels(OWLReasoner r) throws N2OException {
        Map<String, String> classExpressionLabelMap = N2OConfig.getInstance().getClassExpressionNeoLabelMap();
        for (String ces : classExpressionLabelMap.keySet()) {
            String label = classExpressionLabelMap.get(ces);
            log.info("Adding label " + label + " to " + ces + ".");
            try {
                OWLClassExpression ce = manager.parseExpression(ces);
                if (label.isEmpty()) {
                    if (ce.isClassExpressionLiteral()) {
                        label = formatAsNeoNodeLabel(ce.asOWLClass());
                    } else {
                        log.warning("During adding of dynamic neo labels, an empty label was encountered in conjunction with a complex class expression (" + N2OUtils.render(ce) + "). The label was not added.");
                    }
                }
                if (!label.isEmpty()) {
                    for (OWLClass sc : getSubClasses(r, ce, false)) manager.addNodeLabel(sc, label);
                    for (OWLNamedIndividual sc : getInstances(r, ce)) manager.addNodeLabel(sc, label);
                }
            } catch (Exception e) {
                throw new N2OException("FAILED adding label " + label + " to " + ces ,e);
            }
        }

    }

    private Set<OWLNamedIndividual> getInstances(OWLReasoner r, OWLClassExpression e) {
        Set<OWLNamedIndividual> instances = new HashSet<>(r.getInstances(e, false).getFlattened());
        instances.removeAll(filterout.stream().filter(OWLEntity::isOWLNamedIndividual).map(OWLEntity::asOWLNamedIndividual).collect(Collectors.toSet()));
        return instances;
    }


    private Set<OWLClass> getSubClasses(OWLReasoner r, OWLClassExpression e, boolean direct) {
        Set<OWLClass> subclasses = new HashSet<>(r.getSubClasses(e, direct).getFlattened());
        subclasses.addAll(r.getEquivalentClasses(e).getEntities());
        subclasses.removeAll(filterout.stream().filter(OWLEntity::isOWLClass).map(OWLEntity::asOWLClass).collect(Collectors.toSet()));
        if (e.isClassExpressionLiteral()) {
            subclasses.remove(e.asOWLClass());
        }
        return subclasses;
    }

    private String formatAsNeoNodeLabel(OWLClass c) {
        String s = manager.getNode(c).map(N2OEntity::getLabel).orElse(c.getIRI().getShortForm());
        s = s.replaceAll("[^A-Za-z0-9]", "_");
        s = StringUtils.capitalize(s.toLowerCase());
        return s;
    }

    /**
     * Iterates through all entities in the ontologies' signature and, for each entity,
     * 1. Stores them as node objects
     * 2. Extract annotations directly on the entity and stores them
     * 3.
     *
     * @param o      Ontology from which the signature is extracted
     * @param result result object to take care of gathering some statistics
     */
    private void extractSignature(OWLOntology o, N2OImportResult result) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        int i = 0;
        for (OWLEntity e : entities) {
            i++;
            if (i % 1000 == 0) {
                log.log(i + " out of " + entities.size());
            }
            Optional<N2OEntity> one = manager.getNode(e);
            if (one.isPresent()) {
                N2OEntity ne = one.get();
                Map<String, Object> props = ne.getNodeBuiltInMetadataAsMap();
                props.put(N2OStatic.ATT_QUALIFIED_SAFE_LABEL, manager.prepareQSL(ne));
                extractIndividualAnnotations(e, props, o);
                createNode(ne, props);
                result.countLoaded(e);
            }
        }
        if (!N2OConfig.getInstance().safeLabelMode().equals(LABELLING_MODE.QSL))
            manager.checkUniqueSafeLabel(N2OConfig.getInstance().safeLabelMode());
    }

    /**
     * @param e     The entity from which the annotations are extracted
     * @param props The property map of the entity (neo properties)
     * @param o the ontology
     */
    private void extractIndividualAnnotations(OWLEntity e, Map<String, Object> props, OWLOntology o) {
        Collection<OWLAnnotationAssertionAxiom> annos = EntitySearcher.getAnnotationAssertionAxioms(e, o);
        Map<String, Set<Object>> propertyAnnotationValueMap = new HashMap<>();
        for (OWLAnnotationAssertionAxiom ax : annos) {
            OWLAnnotation a = ax.getAnnotation();
            OWLAnnotationValue aval = a.annotationValue();
            if (!aval.asIRI().isPresent()) {
                Optional<String> opt_sl_annop = manager.getSLFromAnnotation(a);
                if (opt_sl_annop.isPresent()) {
                    String sl_annop = opt_sl_annop.get();
                    Object value = N2OUtils.extractValueFromOWLAnnotationValue(aval);
                    relationTypeCounter.increment(sl_annop,value);
                    if (a.getProperty().equals(N2OStatic.ap_neo4jLabel)) {
                        manager.addNodeLabel(e, value.toString());
                    } else {
                        value = removeAnnotationDelimitersFromAnnotationValue(value);
                        Set<OWLAnnotation> axiomAnnotations = ax.getAnnotations();
                        if (N2OConfig.getInstance().isShouldPropertyBeRolledAsJSON(a.getProperty())) {
                            Map<String, Set<Object>> axAnnos = manager.extractAxiomAnnotationsIntoValueMap(axiomAnnotations,true);
                            axAnnos.forEach((k,v)->v.forEach(obj->relationTypeCounter.increment(k,obj)));
                            String valueAnnotated = createAxiomAnnotationJSONString(value, axAnnos);
                            //log.info(valueAnnotated);
                            addAnnotationValueToValueMap(propertyAnnotationValueMap, sl_annop, valueAnnotated);
                        } else {
                            addAnnotationValueToValueMap(propertyAnnotationValueMap, sl_annop, value);
                        }
                    }
                }
            }
        }
        convertPropertyAnnotationValueMapToEntityPropertyMap(props, propertyAnnotationValueMap);
    }

    private void convertPropertyAnnotationValueMapToEntityPropertyMap(Map<String, Object> props, Map<String, Set<Object>> propertyAnnotationValueMap) {
        propertyAnnotationValueMap.forEach((k, v) -> {
            if(!v.isEmpty()) {
                if (v.size() == 1) {
                    props.put(k, v.iterator().next());
                } else {
                    Set<String> s = v.stream().map(Object::toString).collect(Collectors.toSet());
                    props.put(k, String.join(N2OStatic.ANNOTATION_DELIMITER, s));
                }
            }
        });
    }

    private Object removeAnnotationDelimitersFromAnnotationValue(Object value) {
        if (value.toString().contains(N2OStatic.ANNOTATION_DELIMITER)) {
            System.err.println("Warning: annotation value " + value + " contains delimiter sequence " + N2OStatic.ANNOTATION_DELIMITER + " which will not be preserved!");
            value = value.toString().replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED, "|Content removed during Neo4J Import|");
        }
        return value;
    }



    private String createAxiomAnnotationJSONString(Object value, Map<String, Set<Object>> axAnnos) {
        JSONObject json = new JSONObject();
        JSONObject annotations = new JSONObject();

        //{ "value": "def...", "annotations": {"database_cross_reference": [ "FlyBase:FBrf0052913","FlyBase:FBrf0064800" ]}}

        json.put("value", value);
        json.put("annotations", annotations);

        for (String axAnnosRel : axAnnos.keySet()) {

            Set<Object> values = new HashSet<>();
            for (Object ov : axAnnos.get(axAnnosRel)) {
                if (ov instanceof String) {
                    ov = ov.toString().replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED,
                            "|Content removed during Neo4J Import|");
                }
                values.add(ov);
            }
            annotations.put(axAnnosRel,new ArrayList<>(values));
        }

        return json.toString();
    }

    private void addAnnotationValueToValueMap(Map<String, Set<Object>> propertyAnnotationValueMap, String sl_annop, Object value) {
        if (!propertyAnnotationValueMap.containsKey(sl_annop))
            propertyAnnotationValueMap.put(sl_annop, new HashSet<>());
        propertyAnnotationValueMap.get(sl_annop).add(value);
    }


    private void createNode(N2OEntity e, Map<String, Object> props) {
        if (N2OConfig.getInstance().isBatch()) {
            manager.updateNode(e.getEntity(), props);
            /*
            props.put(N2OStatic.ATT_IRI,e.getIri());
            long id = inserter.createNode(props, ()->e.getRelationId());
            nodeIndex.put(e.getEntity(),id);
            */
        } else {
            String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                    N2OUtils.concat(e.getTypes(), ":"),
                    e.getIri());
            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
        }
    }

    /**
     * @param o Ontology whose signature is indexed
     */
    private void indexIndividualAnnotationsToEntities(OWLOntology o) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for (OWLEntity e : entities) {
            //Map<String, Object> props = new HashMap<>();
            Collection<OWLAnnotationAssertionAxiom> annos = EntitySearcher.getAnnotationAssertionAxioms(e, o);
            for (OWLAnnotationAssertionAxiom a : annos) {
                OWLAnnotationValue aval = a.annotationValue();
                if (aval.asIRI().isPresent()) {
                    IRI iri = aval.asIRI().or(IRI.create("WRONGANNOTATIONPROPERTY"));
                    Optional<N2OEntity> n2OEntity = manager.getNode(a.getProperty());
                    n2OEntity.ifPresent(oEntity -> indexRelation(e, manager.typedEntity(iri, o), oEntity, a.getAnnotations()));
                }
            }
        }
    }

    private void indexRelation(OWLEntity from, OWLEntity to, N2OEntity rel, Set<OWLAnnotation> annos) {
        if (filterout.contains(from)) {
            return;
        } else if (filterout.contains(to)) {
            return;
        }
        Optional<N2OEntity> from_n = manager.getNode(from);
        if (!from_n.isPresent()) {
            return;
        }
        Optional<N2OEntity> to_n = manager.getNode(to);
        if (!to_n.isPresent()) {
            return;
        }

        String roletype = manager.prepareQSL(rel);

        Map<String, Set<Object>> props = manager.extractAxiomAnnotationsIntoValueMap(annos,true);
        props.forEach((k,v)->v.forEach(obj->relationTypeCounter.increment(k,obj)));
        manager.addRelation(new N2ORelationship(from_n.get(), to_n.get(), roletype, props));
    }

    private void addSubclassRelations(OWLOntology o, OWLReasoner r) {
        Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
        for (OWLClass e : entities) {
            if (filterout.contains(e)) {
                continue;
            }
            for (OWLClass sub : getSubClasses(r, e, true)) {

                //System.out.println(e+" sub: "+sub);
                Map<String, Object> props = new HashMap<>();
                props.put("id", N2OStatic.RELTYPE_SUBCLASSOF);
                Optional<N2OEntity> n2OEntitySub = manager.getNode(sub);
                Optional<N2OEntity> n2OEntity = manager.getNode(e);
                if (n2OEntitySub.isPresent() && n2OEntity.isPresent()) {
                    updateRelationship(n2OEntitySub.get(), n2OEntity.get(), props);
                }

            }
        }
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
                props.put("id", N2OStatic.RELTYPE_INSTANCEOF);

                Optional<N2OEntity> n2OEntityType = manager.getNode(type);
                Optional<N2OEntity> n2OEntity = manager.getNode(e);
                if (n2OEntityType.isPresent() && n2OEntity.isPresent()) {
                    updateRelationship(n2OEntity.get(), n2OEntityType.get(), props);
                }
            }
        }
    }


    private void addExistentialRelationships(OWLOntology o, OWLReasoner r) {
        processLogicallyConnectedEntities(r, o);
        updateMetadataOfAllIndexedRelationships();
    }

    private void updateMetadataOfAllIndexedRelationships() {
        for (N2ORelationship relationship : manager.getRelationships()) {
            N2OEntity e = relationship.getStart();
            if (filterout.contains(e.getEntity())) {
                continue;
            }

            N2OEntity ec = relationship.getEnd();

            if (filterout.contains(ec.getEntity())) {
                continue;
            }

            Map<String, Object> props = prepareRelationshipProperties(relationship);
            updateRelationship(e, ec, props);
        }
    }






    /**
     * @param relationship Relationship object whose properties are being processed
     * @return A map of all the properties, including the updated built-in ones.
     */
    private Map<String, Object> prepareRelationshipProperties(N2ORelationship relationship) {
        String rel = relationship.getRelationId();
        Optional<N2OEntity> relEntity = manager.fromSL(rel);
        Map<String, Object> props = new HashMap<>();
        Map<String, Set<Object>> props_rel = relationship.getProps();
        props_rel.remove(N2OStatic.ATT_IRI);
        props_rel.remove(N2OStatic.ATT_LABEL);
        props_rel.remove(N2OStatic.ATT_NODE_TYPE);
        props_rel.remove(N2OStatic.ATT_SHORT_FORM);
        props.put("id", rel);
        convertPropertyAnnotationValueMapToEntityPropertyMap(props, props_rel);
        if (relEntity.isPresent()) {
            props.put(N2OStatic.ATT_IRI, relEntity.get().getIri());
            props.put(N2OStatic.ATT_NODE_TYPE, relEntity.get().getEntityType());
            props.put(N2OStatic.ATT_LABEL, relEntity.get().getLabel());
            props.put(N2OStatic.ATT_SHORT_FORM, relEntity.get().getShort_form());
        }
        return props;
    }

    private void processLogicallyConnectedEntities(OWLReasoner r, OWLOntology o) {
        log.info("processLogicallyConnectedEntities currently does not use the reasoner to infer additional existential restrictions "+r.getReasonerName());

        for (OWLAxiom ax : o.getAxioms(Imports.INCLUDED)) {
            if (ax instanceof OWLSubClassOfAxiom) {
                // CLASS-CLASS: Simple existential "class" restrictions on classes
                // CLASS-INDIVIDUAL: Simple existential "individual" restrictions on classes
                OWLSubClassOfAxiom sax = (OWLSubClassOfAxiom) ax;
                OWLClassExpression s_super = sax.getSuperClass();
                OWLClassExpression s_sub = sax.getSubClass();
                if (s_sub.isClassExpressionLiteral()) {
                    if (s_super instanceof OWLObjectSomeValuesFrom) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) s_super, s_sub.asOWLClass(), ax.getAnnotations());
                    } else if (s_super instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) ((OWLObjectHasValue) s_super).asSomeValuesFrom(), s_sub.asOWLClass(), ax.getAnnotations());
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
                            processExistentialRestriction((OWLObjectSomeValuesFrom) e, c, ax.getAnnotations());
                        }
                    }
                }
            } else if (ax instanceof OWLClassAssertionAxiom) {
                // INDIVIDUAL-CLASS: Simple existential "individual" restrictions on individuals
                OWLClassAssertionAxiom eqax = (OWLClassAssertionAxiom) ax;
                OWLIndividual i = eqax.getIndividual();
                if (i.isNamed()) {
                    OWLClassExpression type = eqax.getClassExpression();
                    if (type instanceof OWLObjectSomeValuesFrom) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) type, i.asOWLNamedIndividual(), ax.getAnnotations());
                    } else if (type instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) ((OWLObjectHasValue) type).asSomeValuesFrom(), i.asOWLNamedIndividual(), ax.getAnnotations());
                    }
                }
            } else if (ax instanceof OWLObjectPropertyAssertionAxiom) {
                // INDIVIDUAL-INDIVIDUAL Object Property Assertion
                OWLObjectPropertyAssertionAxiom eqax = (OWLObjectPropertyAssertionAxiom) ax;
                OWLIndividual from = eqax.getSubject();
                if (from.isNamed()) {
                    OWLIndividual to = eqax.getObject();
                    if (to.isNamed()) {
                        if (!eqax.getProperty().isAnonymous()) {
                            Optional<N2OEntity> e = manager.getNode(eqax.getProperty().asOWLObjectProperty());
                            e.ifPresent(n2OEntity -> indexRelation(from.asOWLNamedIndividual(), to.asOWLNamedIndividual(), n2OEntity, ax.getAnnotations()));
                        }
                    }
                }
            }
        }
    }

    private void processExistentialRestriction(OWLObjectSomeValuesFrom svf, OWLEntity s_sub, Set<OWLAnnotation> annos) {
        if (!svf.getProperty().isAnonymous()) {
            OWLObjectProperty op = svf.getProperty().asOWLObjectProperty();
            OWLClassExpression filler = svf.getFiller();
            if (filler.isClassExpressionLiteral()) {
                // ENTITY-CLASS: A SubClassOf R some B, i:R some B
                OWLClass c = svf.getFiller().asOWLClass();
                Optional<N2OEntity> e = manager.getNode(op);
                e.ifPresent(n2OEntity -> indexRelation(s_sub, c, n2OEntity, annos));
            } else {
                // ENTITY-INDIVIDUAL: A SubClassOf R some {i}, i: R some {j}
                if (filler instanceof OWLObjectOneOf) {
                    OWLObjectOneOf ce = (OWLObjectOneOf) filler;
                    if (ce.getIndividuals().size() == 1) { // If there is more than one, we cannot assume a relationship.
                        for (OWLIndividual i : ce.getIndividuals()) {
                            if (i.isNamed()) {
                                Optional<N2OEntity> e = manager.getNode(op);
                                e.ifPresent(n2OEntity -> indexRelation(s_sub, i.asOWLNamedIndividual(), n2OEntity, annos));
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateRelationship(N2OEntity start_neo, N2OEntity end_neo, Map<String, Object> rel) {
        if (N2OConfig.getInstance().isBatch()) {
            manager.updateRelation(start_neo, end_neo, rel);
        }
    }



}

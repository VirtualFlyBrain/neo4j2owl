package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

class N2OOntologyImporter {

    private Set<OWLEntity> filterout = new HashSet<>();
    private N2OImportManager manager;
    private N2OLog log = N2OLog.getInstance();

    private static OWLDataFactory df = OWLManager.getOWLDataFactory();

    private GraphDatabaseAPI dbapi;
    private GraphDatabaseService db;

    public N2OOntologyImporter(GraphDatabaseAPI dbapi, GraphDatabaseService db) {
        this.dbapi = dbapi;
        this.db = db;
    }

    /**
     *
     * Imports an ontology into neo4j
     *
     * @param exService executor services for concurrent processing of batches
     * @param importdir neo4j import dir location for storing the CSV files
     * @param o ontology object that contains the axioms to be processed
     * @throws IOException
     * @throws InterruptedException
     * @throws java.util.concurrent.ExecutionException
     */
    public void importOntology(ExecutorService exService, File importdir, OWLOntology o, N2OImportResult result) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        IRIManager iriManager = new IRIManager();
        iriManager.setStrict(N2OConfig.getInstance().isStrict());


        manager = new N2OImportManager(o, iriManager);

        log.log("Preparing reasoner");
        OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
        filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLThing());
        filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
        filterout.addAll(r.getUnsatisfiableClasses().getEntities());

        log.log("Extracting signature");
        extractSignature(o,result);
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

            manager.exportOntologyToCSV(importdir);

            exService.submit(() -> {
                dbapi.execute("CREATE INDEX ON :Entity(iri)");
                return "done";
            });

            log.log("Loading nodes to neo from CSV.");
            loadNodesToNeoFromCSV(exService, importdir);

            log.log("Loading relationships to neo from CSV.");
            loadRelationshipsToNeoFromCSV(exService, importdir);
            log.log("Loading done..");
        }
    }

    private void addDynamicNodeLabels(OWLReasoner r) {
        Map<String,String> classExpressionLabelMap = N2OConfig.getInstance().getClassExpressionNeoLabelMap();
        for(String ces:classExpressionLabelMap.keySet()) {
            String label = classExpressionLabelMap.get(ces);
            log.info("Adding label "+label+" to "+ces+".");
            OWLClassExpression ce = manager.parseExpression(ces);
            if(label.isEmpty()) {
                if(ce.isClassExpressionLiteral()) {
                    label = formatAsNeoNodeLabel(ce.asOWLClass());
                } else {
                    log.warning("During adding of dynamic neo labels, an empty label was encountered in conjunction with a complex class expression ("+N2OUtils.render(ce)+"). The label was not added.");
                }
            }
            Set<OWLClass> subclasses = getSubClasses(r, ce);
            for(OWLClass sc:subclasses) {
                manager.addNodeLabel(sc,label);
            }
        }


    }

    private String formatAsNeoNodeLabel(OWLClass c) {
        String s = manager.getNode(c).get().getLabel();
        s = s.replaceAll("[^A-Za-z0-9]", "_");
        s = StringUtils.capitalize(s.toLowerCase());
        return s;
    }

    /**
     * Iterates through all entities in the ontologies' signature and, for each entity,
     *  1. Stores them as node objects
     *  2. Extract annotations directly on the entity and stores them
     *  3.
     * @param o Ontology from which the signature is extracted
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
            if(one.isPresent()) {
                N2OEntity ne = one.get();
                Map<String, Object> props = ne.getNodeBuiltInMetadataAsMap();
                props.put(N2OStatic.ATT_QUALIFIED_SAFE_LABEL,manager.prepareQSL(ne));
                extractIndividualAnnotations(e, props, o);
                createNode(ne, props);
                result.countLoaded(e);
            }
        }
        if (!N2OConfig.getInstance().safeLabelMode().equals(LABELLING_MODE.QSL))
            manager.checkUniqueSafeLabel(N2OConfig.getInstance().safeLabelMode());
    }

    /**
     *
     * @param e The entity from which the annotations are extracted
     * @param props
     * @param o
     */
    private void extractIndividualAnnotations(OWLEntity e, Map<String, Object> props, OWLOntology o) {
        //Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
        Collection<OWLAnnotationAssertionAxiom> annos = EntitySearcher.getAnnotationAssertionAxioms(e, o);
        Map<String, String> propertyAnnotationValueMap = new HashMap<>();
        for (OWLAnnotationAssertionAxiom ax : annos) {
            OWLAnnotation a = ax.getAnnotation();
            OWLAnnotationValue aval = a.annotationValue();
            if (!aval.asIRI().isPresent()) {
                Optional<String> opt_sl_annop = manager.getSLFromAnnotation(a);
                if(opt_sl_annop.isPresent()) {
                    String sl_annop = opt_sl_annop.get();
                    Object value = N2OUtils.extractValueFromOWLAnnotationValue(aval);
                    if (a.getProperty().equals(N2OStatic.ap_neo4jLabel)) {
                        manager.addNodeLabel(e, value.toString());
                    } else {
                        if (!propertyAnnotationValueMap.containsKey(sl_annop)) {
                            propertyAnnotationValueMap.put(sl_annop, "");
                        }
                        if (value.toString().contains(N2OStatic.ANNOTATION_DELIMITER)) {
                            System.err.println("Warning: annotation value " + value + " contains delimiter sequence " + N2OStatic.ANNOTATION_DELIMITER + " which will not be preserved!");
                            value = value.toString().replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED, "|Content removed during Neo4J Import|");
                        }
                        Set<OWLAnnotation> axiomAnnotations = ax.getAnnotations();
                        if (!axiomAnnotations.isEmpty() && N2OConfig.getInstance().isOBOAssumption() && N2OConfig.getInstance().isPropertyInOBOAssumption(a.getProperty())) {
                            Map<String, Set<Object>> axAnnos = new HashMap<>();
                            for (OWLAnnotation axAnn : axiomAnnotations) {
                                OWLAnnotationValue avalAx = axAnn.annotationValue();
                                OWLAnnotationProperty ap = axAnn.getProperty();

                                if (!avalAx.asIRI().isPresent()) {
                                    Object valueAxAnn = N2OUtils.extractValueFromOWLAnnotationValue(avalAx);
                                    Optional<String> opt_sl_axiom_anno = manager.getSLFromAnnotation(axAnn);
                                    if(opt_sl_axiom_anno.isPresent()) {
                                        String sl_axiom_anno = opt_sl_axiom_anno.get();
                                        if (valueAxAnn instanceof String) {
                                            valueAxAnn = valueAxAnn.toString().replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED,
                                                    "|Content removed during Neo4J Import|");
                                            valueAxAnn = String.format("\"%s\"", valueAxAnn);
                                        }
                                        //dbxref: [], seeAlso: []]
                                        if (!axAnnos.containsKey(sl_axiom_anno)) {
                                            axAnnos.put(sl_axiom_anno, new HashSet<>());
                                        }
                                        axAnnos.get(sl_axiom_anno).add(valueAxAnn);
                                    }
                                }
                            }
                            if(!axAnnos.isEmpty()) {
                                String valueAnnotated = String.format("{ \"value\": \"%s\", \"annotations\": {", value);                        Set<String> annoSet = new HashSet<>();
                                for (String axAnnosRel : axAnnos.keySet()) {
                                    Set<String> values = new HashSet<>();
                                    for(Object ov:axAnnos.get(axAnnosRel)) {
                                        values.add(ov.toString());
                                    }
                                    String va = String.join(",", values);
                                    annoSet.add(String.format("\"%s\": [ %s ]", axAnnosRel, va));
                                }
                                valueAnnotated += String.join(",", annoSet);
                                valueAnnotated += "}}";
                                propertyAnnotationValueMap.put(sl_annop, propertyAnnotationValueMap.get(sl_annop) + valueAnnotated + N2OStatic.ANNOTATION_DELIMITER);
                            } else {
                                propertyAnnotationValueMap.put(sl_annop, propertyAnnotationValueMap.get(sl_annop) + value + N2OStatic.ANNOTATION_DELIMITER);
                            }
                        } else {
                            propertyAnnotationValueMap.put(sl_annop, propertyAnnotationValueMap.get(sl_annop) + value + N2OStatic.ANNOTATION_DELIMITER);
                        }
                    }
                }
            }
        }
        propertyAnnotationValueMap.forEach((k, v) -> props.put(k, v.replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED + "$", "")));
        //props.forEach((k,v)->System.out.println("KK "+v));
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
     *
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
                    if(n2OEntity.isPresent()) {
                        indexRelation(e, manager.typedEntity(iri, o), n2OEntity.get(), a.getAnnotations());
                    }
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
        Optional<N2OEntity>  from_n = manager.getNode(from);
        if(!from_n.isPresent()) {
            return;
        }
        Optional<N2OEntity>  to_n = manager.getNode(to);
        if(!to_n.isPresent()) {
            return;
        }

        String roletype = manager.prepareQSL(rel);

        Map<String, Object> props = manager.owlAnnotationsToMapOfProperties(annos);
        manager.addRelation(new N2ORelationship(from_n.get(), to_n.get(), roletype, props));
    }

    private void addSubclassRelations(OWLOntology o, OWLReasoner r) {
        Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
        for (OWLClass e : entities) {
            if (filterout.contains(e)) {
                continue;
            }
            for (OWLClass sub : getSubClasses(r, e)) {

                //System.out.println(e+" sub: "+sub);
                Map<String, Object> props = new HashMap<>();
                props.put("id", N2OStatic.RELTYPE_SUBCLASSOF);
                Optional<N2OEntity> n2OEntitySub = manager.getNode(sub);
                Optional<N2OEntity> n2OEntity = manager.getNode(e);
                if(n2OEntitySub.isPresent() && n2OEntity.isPresent()) {
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
                if(n2OEntityType.isPresent() && n2OEntity.isPresent()) {
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

    private void loadNodesToNeoFromCSV(ExecutorService exService, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : FileUtils.listFiles(importdir,null,false)) {
            String filename = f.getName();
            if (filename.startsWith("nodes_")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    log.warning("CURRENTLY RUNNING IN TESTMODE, should set to testmode: false.");
                    FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        // OLD VERSION: "MERGE (n:Entity { iri: cl.iri }) SET n +={"+composeSETQuery(manager.getHeadersForNodes(type),"cl.")+"} SET n :" + type;
                        "MERGE (n:Entity { iri: cl.iri }) " + uncomposedSetClauses("cl", "n", manager.getHeadersForNodes(type)) + " SET n :" + type;
                log.log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    dbapi.execute(cypher);
                    return "Finished: " + filename;
                });
                log.log(cf.get());
                /*if(fn.contains("Class")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                }
                else if(fn.contains("Indivi")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/

            }
        }
    }


    private void loadRelationshipsToNeoFromCSV(ExecutorService exService, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : importdir.listFiles()) {
            String filename = f.getName();
            if (filename.startsWith("relationship")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    log.warning("CURRENTLY RUNNING IN TESTMODE, should set to testmode: false.");
                    FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                //TODO USING PERIODIC COMMIT 1000
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        "MATCH (s:Entity { iri: cl.start}),(e:Entity { iri: cl.end})\n" +
                        "MERGE (s)-[r:" + type + "]->(e) " + uncomposedSetClauses("cl", "r", manager.getHeadersForRelationships(type));
                log.log(f);
                log.log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    dbapi.execute(cypher);
                    return "Finished: " + filename;
                });
                log.log(cf.get());
                /*if(fn.contains("Individual")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/
            }
        }
    }


    private Set<OWLClass> getSubClasses(OWLReasoner r, OWLClassExpression e) {
        Set<OWLClass> subclasses = new HashSet<>(r.getSubClasses(e, true).getFlattened());
        subclasses.addAll(r.getEquivalentClasses(e).getEntities());
        subclasses.removeAll(filterout.stream().filter(OWLEntity::isOWLClass).map(OWLEntity::asOWLClass).collect(Collectors.toSet()));
        if(e.isClassExpressionLiteral()) {
            subclasses.remove(e.asOWLClass());
        }
        return subclasses;
    }


    /**
     *
     * @param relationship Relationship object whose properties are being processed
     * @return A map of all the properties, including the updated built-in ones.
     */
    private Map<String, Object> prepareRelationshipProperties(N2ORelationship relationship) {
        String rel = relationship.getRelationId();
        Optional<N2OEntity> relEntity = manager.fromSL(rel);
        Map<String, Object> props = relationship.getProps();
        props.remove(N2OStatic.ATT_IRI);
        props.remove(N2OStatic.ATT_LABEL);
        props.remove(N2OStatic.ATT_NODE_TYPE);
        props.put("id", rel);
        if (relEntity.isPresent()) {
            props.put(N2OStatic.ATT_IRI, relEntity.get().getIri());
            props.put(N2OStatic.ATT_NODE_TYPE, relEntity.get().getEntityType());
            props.put(N2OStatic.ATT_LABEL, relEntity.get().getLabel());
        }
        return props;
    }

    private void processLogicallyConnectedEntities(OWLReasoner r, OWLOntology o) {

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
                            if(e.isPresent()) {
                                indexRelation(from.asOWLNamedIndividual(), to.asOWLNamedIndividual(), e.get(), ax.getAnnotations());
                            }
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
                if(e.isPresent()) {
                    indexRelation(s_sub, c, e.get(), annos);
                }
            } else {
                // ENTITY-INDIVIDUAL: A SubClassOf R some {i}, i: R some {j}
                if (filler instanceof OWLObjectOneOf) {
                    OWLObjectOneOf ce = (OWLObjectOneOf) filler;
                    if (ce.getIndividuals().size() == 1) { // If there is more than one, we cannot assume a relationship.
                        for (OWLIndividual i : ce.getIndividuals()) {
                            if (i.isNamed()) {
                                Optional<N2OEntity> e = manager.getNode(op);
                                if(e.isPresent()) {
                                    indexRelation(s_sub, i.asOWLNamedIndividual(), e.get(), annos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateRelationship(N2OEntity start_neo, N2OEntity end_neo, Map<String, Object> rel) {
        if (N2OConfig.getInstance().isBatch()) {
            //inserter.createRelationship(nodeIndex.get(start_neo.getEntity()), nodeIndex.get(end_neo.getEntity()), () -> rel, props);
            manager.updateRelation(start_neo, end_neo, rel);
        } else {
            String cypher = String.format(
                    "MATCH (p { uri:'%s'}), (c { uri:'%s'}) CREATE (p)-[:%s]->(c)",
                    // c can be a class or an object property
                    start_neo.getIri(), end_neo.getIri(), rel.get("id"));

            Map<String, Object> params = new HashMap<>();
            //params.put("props", props);
            db.execute(cypher, params);
        }
    }

    private String uncomposedSetClauses(String csvalias, String neovar, Set<String> headers) {
        StringBuilder sb = new StringBuilder();
        for (String h : headers) {
            if (N2OStatic.isN2OBuiltInProperty(h)) {
                sb.append("SET " + neovar + "." + h + " = " + csvalias + "." + h + " ");
            } else {
                String function = "to" + N2OConfig.getInstance().slToDatatype(h);
                //TODO somevalue = [ x in split(cl.somevalue) | colaesce(apoc.util.toBoolean(x), apoc.util.toInteger(x), apoc.util.toFloat(x), x) ]
                sb.append("SET " + neovar + "." + h + " = [value IN split(" + csvalias + "." + h + ",\"" + N2OStatic.ANNOTATION_DELIMITER + "\") | " + function + "(trim(value))] ");
            }
        }
        return sb.toString().trim().replaceAll(",$", "");
    }

}

package ebi.spot.neo4j2owl;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Created by jbarrasa on 21/03/2016.
 * <p>
 * Importer of basic ontology (RDFS & OWL) elements:
 */
public class OWL2OntologyImporter {

    @Context
    public GraphDatabaseService db;

    @Context
    public GraphDatabaseAPI dbapi;

    // This gives us a log instance that outputs messages to the
    // standard log, `neo4j.log`
    @Context
    public Log log;

    private static OWLDataFactory df = OWLManager.getOWLDataFactory();
    private static final IRIManager iriManager = new IRIManager();

    private static Set<OWLClass> filterout = new HashSet<>();

    private static Map<N2OEntity, Map<String, Set<N2OEntity>>> existential = new HashMap<>();
    private static Map<OWLEntity, Long> nodeIndex = new HashMap<>();
    private static N2OManager manager;
    //private static BatchInserter inserter;

    private static int classesLoaded = 0;
    private static int individualsLoaded = 0;
    private static int objPropsLoaded = 0;
    private static int annotationPropertiesloaded = 0;
    private static int dataPropsLoaded = 0;
    private static long start = System.currentTimeMillis();

    private static boolean batch = true;
    private static boolean testmode = true;

    /*
    Constants
    */


    @Procedure(mode = Mode.DBMS)
    public Stream<ImportResults> owl2Import(@Name("url") String url, @Name("strict") String strict) {
        ImportResults importResults = new ImportResults();
        final ExecutorService exService = Executors.newSingleThreadExecutor();
        iriManager.setStrict(strict.equals("strict"));

        File importdir = new File(dbapi.getStoreDir(),"import");
        log.info("Import dir: "+importdir.getAbsolutePath());

        //Integer timeout_in_minutes = Integer.valueOf(timeout_str);

        System.out.println(dbapi.getStoreDir().getAbsolutePath());
        batch = true;//insert.equals("batch");
        Map<String,String> params = dbapi.getDependencyResolver().resolveDependency(Config.class).getRaw();

        String par_neo4jhome = "unsupported.dbms.directories.neo4j_home";
        String par_importdirpath = "dbms.directories.import";
        if(params.containsKey(par_neo4jhome)&&params.containsKey(par_importdirpath)) {
            String neo4j_home_path = params.get(par_neo4jhome);
            String import_dir_path = params.get(par_importdirpath);
            File i = new File(import_dir_path);
            if(i.isAbsolute()) {
                importdir = i;
            } else {
                importdir = new File(neo4j_home_path,import_dir_path);
            }
        } else {
            log.error("Import directory (or base neo4j directory) not set.");
        }
        //System.out.println(params);
        //System.out.println(dbapi.getStoreDir().getAbsolutePath());


        try {
            if (batch) {
                if(!importdir.exists()) {
                    log.error(importdir.getAbsolutePath()+" does not exist! Select valid import dir.");
                } else {
                    deleteCSVFilesInImportsDir(importdir);
                }
            }
            //inserter = BatchInserters.inserter( inserttmp);
            log("Loading Ontology");
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(IRI.create(url));
            manager = new N2OManager(o, iriManager);
            log("Creating reasoner");
            OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLThing());
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
            log("Set indices");
            /*
            db.execute("CREATE INDEX ON :Individual(iri)");
            db.execute("CREATE INDEX ON :Class(iri)");
            db.execute("CREATE INDEX ON :ObjectProperty(iri)");
            db.execute("CREATE INDEX ON :DataProperty(iri)");
            db.execute("CREATE INDEX ON :AnnotationProperty(iri)");
            */

//            db.execute("CREATE CONSTRAINT ON (c:Individual) ASSERT c.iri IS UNIQUE");
            //   db.execute("CREATE CONSTRAINT ON (c:Class) ASSERT c.iri IS UNIQUE");
            //   db.execute("CREATE CONSTRAINT ON (c:ObjectProperty) ASSERT c.iri IS UNIQUE");
            //   db.execute("CREATE CONSTRAINT ON (c:DataProperty) ASSERT c.iri IS UNIQUE");
            //   db.execute("CREATE CONSTRAINT ON (c:AnnotationProperty) ASSERT c.iri IS UNIQUE");

            log("Extracting signature");
            extractSignature(o);
            log("Extracting annotations to literals");
            indexIndividualAnnotationsToEntities(o, r);
            log("Extracting subclass relations");
            addSubclassRelations(o, r);
            log("Extracting class assertions");
            addClassAssertions(o, r);
            log("Extracting existential relations");
            addExistentialRelationships(o, r);
            if (batch) {
                log("Loading in Database: " + importdir.getAbsolutePath());

                manager.exportCSV(importdir);

                /*
                GraphDatabase.driver()
                try ( Session session = db.session() )
                {
                    session.run( "USING PERIODIC COMMIT ..." );
                }*/

                exService.submit(()->{dbapi.execute("CREATE INDEX ON :Entity(iri)"); return "done";});

                for (File f : importdir.listFiles()) {
                    String filename = f.getName();
                    if (filename.startsWith("nodes_")) {
                        String fn = filename;
                        if(testmode) {
                            fn = "/" + new File(importdir, filename).getAbsolutePath().toString();
                            System.err.println("UNCOMMENT FILENAME");
                        }
                        String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                        String cypher = "USING PERIODIC COMMIT 5000\n" +
                                "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                                "MERGE (n:" + type + ":Entity { iri: cl.iri }) SET n +={"+composeSETQuery(manager.getHeadersForNodes(type),"cl.")+"}";
                        log(cypher);
                        final Future<String> cf = exService.submit(()->{dbapi.execute(cypher); return "Finished: "+filename;});
                        System.out.println(cf.get());
                    }
                }
                for (File f : importdir.listFiles()) {
                    String filename = f.getName();
                    if (filename.startsWith("relationship")) {
                        String fn = filename;
                        if(testmode) {
                            fn = "/" + new File(importdir, filename).getAbsolutePath().toString();
                            System.err.println("UNCOMMENT FILENAME");
                        }
                        String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                        //TODO USING PERIODIC COMMIT 1000
                        String cypher = "USING PERIODIC COMMIT 5000\n" +
                                "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                                "MATCH (s:Entity { iri: cl.start}),(e:Entity { iri: cl.end})\n" +
                                "MERGE (s)-[:"+type+"]->(e)";
                        log(cypher);
                        final Future<String> cf = exService.submit(()->{dbapi.execute(cypher); return "Finished: "+filename;});
                        System.out.println(cf.get());
                    }
                }

                exService.shutdown();
                try {
                    exService.awaitTermination(180, TimeUnit.MINUTES);
                    log("All done..");
                } catch(InterruptedException e) {
                    log.error("Query interrupted");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            log.info("done (Undo delete)");
            deleteCSVFilesInImportsDir(importdir);
            importResults.setElementsLoaded(classesLoaded + individualsLoaded + objPropsLoaded + annotationPropertiesloaded + dataPropsLoaded);
        }
        return Stream.of(importResults);
    }

    private void deleteCSVFilesInImportsDir(File importdir) {
        if(importdir.exists()) {
            for (File f : importdir.listFiles()) {
                if(f.getName().startsWith("nodes_")||f.getName().startsWith("relationship_")) {
                    FileUtils.deleteQuietly(f);
                }
            }
        }
    }


    private String composeSETQuery(Set<String> headersForNodes, String alias) {
        StringBuilder sb = new StringBuilder();

        for(String h:headersForNodes) {
            if(h.equals("iri")) {
                continue;
            } else if(manager.getPrimaryEntityPropertyKeys().contains(h)) {
                sb.append(h+":"+alias+h+", ");
            } else {

                sb.append(h+":split("+alias+h+",\""+OWL2NeoMapping.ANNOTATION_DELIMITER+"\"), ");
            }
        }
        return sb.toString().trim().replaceAll(",$","");
    }

    private void log(String msg) {
        log.info(msg);
        System.out.println(msg + " " + getTimePassed());
    }

    private String getTimePassed() {
        long time = System.currentTimeMillis() - start;
        return ((double) time / 1000.0) + " sec";
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
                updateRelationship(manager.getNode(sub), manager.getNode(e), OWL2NeoMapping.RELTYPE_SUBCLASSOF, props);
            }
        }
    }

    private Set<OWLClass> getSubClasses(OWLReasoner r, OWLClass e) {
        Set<OWLClass> subclasses = new HashSet<>(r.getSubClasses(e, true).getFlattened());
        subclasses.addAll(r.getEquivalentClasses(e).getEntities());
        subclasses.removeAll(filterout);
        return subclasses;
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
                    } else if (s_super instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) ((OWLObjectHasValue) s_super).asSomeValuesFrom(), s_sub.asOWLClass());
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
                if (i.isNamed()) {
                    OWLClassExpression type = eqax.getClassExpression();
                    if (type instanceof OWLObjectSomeValuesFrom) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) type, i.asOWLNamedIndividual());
                    } else if (type instanceof OWLObjectHasValue) {
                        processExistentialRestriction((OWLObjectSomeValuesFrom) ((OWLObjectHasValue) type).asSomeValuesFrom(), i.asOWLNamedIndividual());
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
                            indexRelation(from.asOWLNamedIndividual(), to.asOWLNamedIndividual(), manager.getNode(eqax.getProperty().asOWLObjectProperty()));
                        }
                    }
                }
            }
        }
    }

    private void processExistentialRestriction(OWLObjectSomeValuesFrom svf, OWLEntity s_sub) {
        if (!svf.getProperty().isAnonymous()) {
            OWLObjectProperty op = svf.getProperty().asOWLObjectProperty();
            OWLClassExpression filler = svf.getFiller();
            if (filler.isClassExpressionLiteral()) {
                // ENTITY-CLASS: A SubClassOf R some B, i:R some B
                OWLClass c = svf.getFiller().asOWLClass();
                indexRelation(s_sub, c, manager.getNode(op));
            } else {
                // ENTITY-INDIVIDUAL: A SubClassOf R some {i}, i: R some {j}
                if (filler instanceof OWLObjectOneOf) {
                    OWLObjectOneOf ce = (OWLObjectOneOf) filler;
                    if (ce.getIndividuals().size() == 1) { // If there is more than one, we cannot assume a relationship.
                        for (OWLIndividual i : ce.getIndividuals()) {
                            if (i.isNamed()) {
                                indexRelation(s_sub, i.asOWLNamedIndividual(), manager.getNode(op));
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
        N2OEntity from_n = manager.getNode(from);
        N2OEntity to_n = manager.getNode(to);
        String roletype = rel.getQualified_safe_label();


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
                updateRelationship(manager.getNode(e), manager.getNode(type), OWL2NeoMapping.RELTYPE_INSTANCEOF, props);
            }
        }
    }

    private void extractSignature(OWLOntology o) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        int i = 0;
        for (OWLEntity e : entities) {
            i++;
            if (i % 1000 == 0) {
                log(i + " out of " + entities.size());
            }
            N2OEntity ne = manager.getNode(e);
            Map<String, Object> props = new HashMap<>();
            props.put(OWL2NeoMapping.ATT_LABEL, ne.getLabel());
            props.put(OWL2NeoMapping.ATT_SAFE_LABEL, ne.getSafe_label());
            props.put(OWL2NeoMapping.ATT_QUALIFIED_SAFE_LABEL, ne.getQualified_safe_label());
            props.put(OWL2NeoMapping.ATT_SHORT_FORM, ne.getShort_form());
            props.put(OWL2NeoMapping.ATT_CURIE, ne.getCurie());
            props.put(OWL2NeoMapping.ATT_IRI, ne.getIri());
            extractIndividualAnnotations(e, props, o);
            createNode(ne, props);
            countLoaded(e);
        }
    }


    private void extractIndividualAnnotations(OWLEntity e, Map<String, Object> props, OWLOntology o) {
        Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
        Map<String,String> annoString = new HashMap<>();
        for (OWLAnnotation a : annos) {

            OWLAnnotationValue aval = a.annotationValue();
            if (!aval.isIRI()) {
                String p = neoPropertyKey(a);
                String value = aval.asLiteral().or(df.getOWLLiteral("unknownX")).getLiteral();
                if(!annoString.containsKey(p)) {
                    annoString.put(p,"");
                }
                if (value.contains(OWL2NeoMapping.ANNOTATION_DELIMITER)) {
                    System.err.println("Warning: annotation value "+value+" contains delimiter sequence "+OWL2NeoMapping.ANNOTATION_DELIMITER+" which will not be preserved!");
                    value.replaceAll(OWL2NeoMapping.ANNOTATION_DELIMITER_ESCAPED,"|Content removed during Neo4J Import|");
                }
                annoString.put(p,annoString.get(p)+value+OWL2NeoMapping.ANNOTATION_DELIMITER);


            }
        }
        annoString.forEach((k,v)->props.put(k,v.replaceAll(OWL2NeoMapping.ANNOTATION_DELIMITER_ESCAPED+"$","")));
        //props.forEach((k,v)->System.out.println("KK "+v));
    }

    private void indexIndividualAnnotationsToEntities(OWLOntology o, OWLReasoner r) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for (OWLEntity e : entities) {
            Map<String, Object> props = new HashMap<>();
            Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
            for (OWLAnnotation a : annos) {
                OWLAnnotationValue aval = a.annotationValue();
                if (aval.isIRI()) {
                    IRI iri = aval.asIRI().or(IRI.create("WRONGANNOTATIONPROPERTY"));
                    indexRelation(e, manager.typedEntity(iri, o), manager.getNode(a.getProperty()));
                }
            }
        }
    }

    private void createNode(N2OEntity e, Map<String, Object> props) {
        if (batch) {
            manager.updateNode(e.getEntity(), props);
            /*
            props.put(OWL2NeoMapping.ATT_IRI,e.getIri());
            long id = inserter.createNode(props, ()->e.getType());
            nodeIndex.put(e.getEntity(),id);
            */
        } else {
            String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                    e.getType(),
                    e.getIri());
            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
        }
    }

    private void updateRelationship(N2OEntity start_neo, N2OEntity end_neo, String rel, Map<String, Object> props) {
        if (batch) {
            //inserter.createRelationship(nodeIndex.get(start_neo.getEntity()), nodeIndex.get(end_neo.getEntity()), () -> rel, props);
            manager.updateRelation(start_neo.getEntity(), end_neo.getEntity(), rel, props);
        } else {
            String cypher = String.format(
                    "MATCH (p { uri:'%s'}), (c { uri:'%s'}) CREATE (p)-[:%s]->(c)",
                    // c can be a class or an object property
                    start_neo.getIri(), end_neo.getIri(), rel);

            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
        }
    }

    private String neoPropertyKey(OWLAnnotation a) {
        return manager.getNode(a.getProperty()).getQualified_safe_label();
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

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
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static ebi.spot.neo4j2owl.RELATION_TYPE.QSL;
import static ebi.spot.neo4j2owl.RELATION_TYPE.SL_STRICT;

/**
 * Created by Nicolas Matentzoglu for EMBL-EBI and Virtual Fly Brain. Code roughly based on jbarrasa neosemantics.
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
    private static OWLAnnotationProperty ap_neo4jLabel = df.getOWLAnnotationProperty(IRI.create(OWL2NeoMapping.NEO4J_LABEL));
    private static final IRIManager iriManager = new IRIManager();

    private static Set<OWLClass> filterout = new HashSet<>();

    private static Map<N2OEntity, Map<String, Set<N2OEntity>>> existential = new HashMap<>();
    private static N2OManager manager;

    private static int classesLoaded = 0;
    private static int individualsLoaded = 0;
    private static int objPropsLoaded = 0;
    private static int annotationPropertiesloaded = 0;
    private static int dataPropsLoaded = 0;
    private static long start = System.currentTimeMillis();


    /*
    Constants
    */


    @Procedure(mode = Mode.DBMS)
    public Stream<ImportResults> owl2Import(@Name("url") String url, @Name("config") String config) {

        start = System.currentTimeMillis();

        final ExecutorService exService = Executors.newSingleThreadExecutor();

        File importdir = prepareImportDirectory();

        try {
            prepareConfig(url, config, importdir);
        } catch (IOException e) {
            log.error("Config could not be loaded, using defaults.");
            e.printStackTrace();
        }

        ImportResults importResults = new ImportResults();
        try {

            //inserter = BatchInserters.inserter( inserttmp);
            log("Loading Ontology");
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(getOntologyIRI(url, importdir));
            manager = new N2OManager(o, iriManager);

            log("Preparing reasoner");
            OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLThing());
            filterout.add(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
            filterout.addAll(r.getUnsatisfiableClasses().getEntities());

            log("Preparing of Indices: " + N2OConfig.getInstance().prepareIndex());
            if (N2OConfig.getInstance().prepareIndex()) {
                prepareIndices();
            }

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
            if (N2OConfig.getInstance().isBatch()) {
                log("Loading in Database: " + importdir.getAbsolutePath());

                manager.exportOntologyToCSV(importdir);

                exService.submit(() -> {
                    dbapi.execute("CREATE INDEX ON :Entity(iri)");
                    return "done";
                });

                log("Loading nodes to neo from CSV.");
                loadNodesToNeoFromCSV(exService, importdir);

                log("Loading relationships to neo from CSV.");
                loadRelationshipsToNeoFromCSV(exService, importdir);

                exService.shutdown();
                try {
                    exService.awaitTermination(N2OConfig.getInstance().getTimeoutInMinutes(), TimeUnit.MINUTES);
                    log("All done..");
                } catch (InterruptedException e) {
                    log.error("Query interrupted");
                }
            }
        } catch (Exception e) {
            importResults.setTerminationKO(e.getMessage());
            e.printStackTrace();
        } finally {
            log("done (Undo delete)");
            deleteCSVFilesInImportsDir(importdir);
            importResults.setElementsLoaded(classesLoaded + individualsLoaded + objPropsLoaded + annotationPropertiesloaded + dataPropsLoaded);
        }
        return Stream.of(importResults);
    }

    private void loadRelationshipsToNeoFromCSV(ExecutorService exService, File importdir) throws InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : importdir.listFiles()) {
            String filename = f.getName();
            if (filename.startsWith("relationship")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    System.err.println("UNCOMMENT FILENAME");
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                //TODO USING PERIODIC COMMIT 1000
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        "MATCH (s:Entity { iri: cl.start}),(e:Entity { iri: cl.end})\n" +
                        "MERGE (s)-[:" + type + "]->(e)";
                log(f);
                log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    dbapi.execute(cypher);
                    return "Finished: " + filename;
                });
                System.out.println(cf.get());
                /*if(fn.contains("Individual")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/
            }
        }
    }

    private void loadNodesToNeoFromCSV(ExecutorService exService, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : importdir.listFiles()) {
            String filename = f.getName();
            if (filename.startsWith("nodes_")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    System.err.println("CURRENTLY RUNNING IN TESTMODE, UNCOMMENT BEFORE COMPILING");
                    FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        // OLD VERSION: "MERGE (n:Entity { iri: cl.iri }) SET n +={"+composeSETQuery(manager.getHeadersForNodes(type),"cl.")+"} SET n :" + type;
                        "MERGE (n:Entity { iri: cl.iri }) " + uncomposedSetClauses(type, "cl.") + " SET n :" + type;
                log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    dbapi.execute(cypher);
                    return "Finished: " + filename;
                });
                System.out.println(cf.get());
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

    private void prepareIndices() {
        db.execute("CREATE INDEX ON :Individual(iri)");
        db.execute("CREATE INDEX ON :Class(iri)");
        db.execute("CREATE INDEX ON :ObjectProperty(iri)");
        db.execute("CREATE INDEX ON :DataProperty(iri)");
        db.execute("CREATE INDEX ON :AnnotationProperty(iri)");
        db.execute("CREATE CONSTRAINT ON (c:Individual) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:Class) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:ObjectProperty) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:DataProperty) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:AnnotationProperty) ASSERT c.iri IS UNIQUE");
    }

    private File prepareImportDirectory() {
        Map<String, String> params = dbapi.getDependencyResolver().resolveDependency(Config.class).getRaw();
        String par_neo4jhome = "unsupported.dbms.directories.neo4j_home";
        String par_importdirpath = "dbms.directories.import";
        File importdir = new File(dbapi.getStoreDir(), "import");
        log.info("Import dir: " + importdir.getAbsolutePath());
        if (params.containsKey(par_neo4jhome) && params.containsKey(par_importdirpath)) {
            String neo4j_home_path = params.get(par_neo4jhome);
            String import_dir_path = params.get(par_importdirpath);
            File i = new File(import_dir_path);
            if (i.isAbsolute()) {
                importdir = i;
            } else {
                importdir = new File(neo4j_home_path, import_dir_path);
            }
        } else {
            log.error("Import directory (or base neo4j directory) not set.");
        }
        if (!importdir.exists()) {
            log.error(importdir.getAbsolutePath() + " does not exist! Select valid import dir.");
        } else {
            deleteCSVFilesInImportsDir(importdir);
        }
        return importdir;
    }

    private IRI getOntologyIRI(@Name("url") String url, File importdir) {
        IRI iri = null;
        if (url.startsWith("file://")) {
            File ontology = new File(importdir, url.replaceAll("file://", ""));
            iri = IRI.create(ontology.toURI());
        } else {
            iri = IRI.create(url);
        }
        return iri;
    }

    private void prepareConfig(String url, String config, File importdir) throws IOException {
        File configfile = new File(importdir, "config.yaml");
        N2OConfig n2o_config = N2OConfig.getInstance();
        if (config.startsWith("file://")) {
            File config_ = new File(importdir, url.replaceAll("file://", ""));
            FileUtils.copyFile(config_, configfile);
        } else {
            URL url_ = new URL(config);
            FileUtils.copyURLToFile(
                    url_,
                    configfile);
        }

        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(configfile);
        Map<String, Object> configs = yaml.load(inputStream);
        if (configs.containsKey("strict")) {
            if (configs.get("strict") instanceof Boolean) {
                n2o_config.setStrict((Boolean) configs.get("strict"));
                iriManager.setStrict((Boolean) configs.get("strict"));
            } else {
                log("CONFIG: strict value is not boolean");
            }
        }
        if (configs.containsKey("property_mapping")) {
            Object pm = configs.get("property_mapping");
            if (pm instanceof ArrayList) {
                for (Object pmm : ((ArrayList) pm)) {
                    if (pmm instanceof HashMap) {
                        HashMap<String, Object> pmmhm = (HashMap<String, Object>) pmm;
                        if (pmmhm.containsKey("iris")) {
                            ArrayList iris = (ArrayList) pmmhm.get("iris");
                            for (Object iri : iris) {
                                if (pmmhm.containsKey("id")) {
                                    String id = pmmhm.get("id").toString();
                                    n2o_config.setIriToSl(IRI.create(iri.toString()), id);
                                    if (pmmhm.containsKey("datatype")) {
                                        String datatype = pmmhm.get("datatype").toString();
                                        n2o_config.setSlToDatatype(id, datatype);
                                    }
                                }

                            }
                        }
                    }
                }

            }
        }
        if (configs.containsKey("index")) {
            if (configs.get("index") instanceof Boolean) {
                N2OConfig.getInstance().setPrepareIndex((Boolean) configs.get("index"));
            }
        }

        if (configs.containsKey("testmode")) {
            if (configs.get("testmode") instanceof Boolean) {
                N2OConfig.getInstance().setTestmode((Boolean) configs.get("testmode"));
            }
        }

        if (configs.containsKey("batch")) {
            if (configs.get("batch") instanceof Boolean) {
                N2OConfig.getInstance().setBatch((Boolean) configs.get("batch"));
            }
        }

        if (configs.containsKey("safe_label")) {
            N2OConfig.getInstance().setSafeLabelMode(configs.get("safe_label").toString());
        }

    }

    private String uncomposedSetClauses(String sl, String alias) {
        StringBuilder sb = new StringBuilder();

        for (String h : manager.getHeadersForNodes(sl)) {
            if (h.equals("iri")) {
                continue;
            } else if (manager.getPrimaryEntityPropertyKeys().contains(h)) {
                sb.append("SET n." + h + " = " + alias + h + " ");
            } else {
                String function = "to" + N2OConfig.getInstance().slToDatatype(h);

                //TODO somevalue = [ x in split(cl.somevalue) | colaesce(apoc.util.toBoolean(x), apoc.util.toInteger(x), apoc.util.toFloat(x), x) ]
                sb.append("SET n." + h + " = [value IN split(" + alias + h + ",\"" + OWL2NeoMapping.ANNOTATION_DELIMITER + "\") | " + function + "(trim(value))] ");
            }
        }
        return sb.toString().trim().replaceAll(",$", "");
    }

    private void deleteCSVFilesInImportsDir(File importdir) {
        if (importdir.exists()) {
            for (File f : importdir.listFiles()) {
                if (f.getName().startsWith("nodes_") || f.getName().startsWith("relationship_")) {
                    FileUtils.deleteQuietly(f);
                }
            }
        }
    }


    private String composeSETQuery(Set<String> headersForNodes, String alias) {
        StringBuilder sb = new StringBuilder();

        for (String h : headersForNodes) {
            if (h.equals("iri")) {
                continue;
            } else if (manager.getPrimaryEntityPropertyKeys().contains(h)) {
                sb.append(h + ":" + alias + h + ", ");
            } else {

                sb.append(h + ":split(" + alias + h + ",\"" + OWL2NeoMapping.ANNOTATION_DELIMITER + "\"), ");
            }
        }
        return sb.toString().trim().replaceAll(",$", "");
    }

    private void log(Object msg) {
        log.info(msg.toString());
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
        subclasses.remove(e);
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
        String roletype = getRoleType(rel);

        /*
        System.out.println(roletype);
        System.out.println(to_n);
        if(roletype.equals("source_ns3")) {
            //System.out.println(to_n);
            System.out.println("--------------------------");
            //System.exit(0);
        }
        */
        if (!existential.containsKey(from_n)) {
            existential.put(from_n, new HashMap<>());
        }
        if (!existential.get(from_n).containsKey(roletype)) {
            existential.get(from_n).put(roletype, new HashSet<>());
        }
        existential.get(from_n).get(roletype).add(to_n);
    }

    /*
    The role type is picked in the following order:
    1. If set in config
    2. In case of SL_Lose, if unsafe (clash), use QSL
    3. else use whatever was configured (SL/QSL).
     */
    private String getRoleType(N2OEntity rel) {
        Optional<String> sl = N2OConfig.getInstance().iriToSl(IRI.create(rel.getIri()));
        switch (N2OConfig.getInstance().safeLabelMode()) {
            case QSL:
                return rel.getQualified_safe_label();
            case SL_STRICT:
                if (sl.isPresent()) {
                    return sl.get();
                } else {
                    return rel.getSafe_label();
                }
            case SL_LOSE:
                if (sl.isPresent()) {
                    return sl.get();
                } else {
                    if (manager.isUnsafeRelation(rel.getEntity())) {
                        return rel.getQualified_safe_label();
                    } else {
                        return rel.getSafe_label();
                    }
                }
            default:
                return rel.getQualified_safe_label();
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
        if (!N2OConfig.getInstance().safeLabelMode().equals(QSL))
            manager.checkUniqueSafeLabel(N2OConfig.getInstance().safeLabelMode());
    }


    private void extractIndividualAnnotations(OWLEntity e, Map<String, Object> props, OWLOntology o) {
        Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
        Map<String, String> propertyAnnotationValueMap = new HashMap<>();
        for (OWLAnnotation a : annos) {

            OWLAnnotationValue aval = a.annotationValue();
            if (!aval.asIRI().isPresent()) {
                String p = neoPropertyKey(a);
                Object value = extractValueFromOWLAnnotationValue(aval);
                OWLAnnotationProperty ap = a.getProperty();
                if (ap.equals(ap_neo4jLabel)) {
                    manager.addNodeLabel(e, value.toString());
                } else {
                    if (!propertyAnnotationValueMap.containsKey(p)) {
                        propertyAnnotationValueMap.put(p, "");
                    }
                    if (value.toString().contains(OWL2NeoMapping.ANNOTATION_DELIMITER)) {
                        System.err.println("Warning: annotation value " + value + " contains delimiter sequence " + OWL2NeoMapping.ANNOTATION_DELIMITER + " which will not be preserved!");
                        value = value.toString().replaceAll(OWL2NeoMapping.ANNOTATION_DELIMITER_ESCAPED, "|Content removed during Neo4J Import|");
                    }
                    propertyAnnotationValueMap.put(p, propertyAnnotationValueMap.get(p) + value + OWL2NeoMapping.ANNOTATION_DELIMITER);
                }

            }
        }
        propertyAnnotationValueMap.forEach((k, v) -> props.put(k, v.replaceAll(OWL2NeoMapping.ANNOTATION_DELIMITER_ESCAPED + "$", "")));
        //props.forEach((k,v)->System.out.println("KK "+v));
    }

    private Object extractValueFromOWLAnnotationValue(OWLAnnotationValue aval) {
        if (aval.isLiteral()) {
            OWLLiteral literal = aval.asLiteral().or(df.getOWLLiteral("unknownX"));
            if (literal.isBoolean()) {
                return literal.parseBoolean();
            } else if (literal.isDouble()) {
                return literal.parseDouble();
            } else if (literal.isFloat()) {
                return literal.parseFloat();
            } else if (literal.isInteger()) {
                return literal.parseInteger();
            } else {
                return literal.getLiteral();
            }
        }
        return "neo4j2owl_UnknownValue";
    }

    private void indexIndividualAnnotationsToEntities(OWLOntology o, OWLReasoner r) {
        Set<OWLEntity> entities = new HashSet<>(o.getSignature(Imports.INCLUDED));
        for (OWLEntity e : entities) {
            //Map<String, Object> props = new HashMap<>();
            Collection<OWLAnnotation> annos = EntitySearcher.getAnnotations(e, o);
            for (OWLAnnotation a : annos) {
                OWLAnnotationValue aval = a.annotationValue();
                if (aval.asIRI().isPresent()) {
                    IRI iri = aval.asIRI().or(IRI.create("WRONGANNOTATIONPROPERTY"));
                    indexRelation(e, manager.typedEntity(iri, o), manager.getNode(a.getProperty()));
                }
            }
        }
    }

    private void createNode(N2OEntity e, Map<String, Object> props) {
        if (N2OConfig.getInstance().isBatch()) {
            manager.updateNode(e.getEntity(), props);
            /*
            props.put(OWL2NeoMapping.ATT_IRI,e.getIri());
            long id = inserter.createNode(props, ()->e.getType());
            nodeIndex.put(e.getEntity(),id);
            */
        } else {
            String cypher = String.format("MERGE (p:%s { uri:'%s'}) SET p+={props}",
                    Util.concat(e.getTypes(), ":"),
                    e.getIri());
            Map<String, Object> params = new HashMap<>();
            params.put("props", props);
            db.execute(cypher, params);
        }
    }

    private void updateRelationship(N2OEntity start_neo, N2OEntity end_neo, String rel, Map<String, Object> props) {
        if (N2OConfig.getInstance().isBatch()) {
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
        N2OEntity n = manager.getNode(a.getProperty());
        String key = getRoleType(n);
        return key;
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

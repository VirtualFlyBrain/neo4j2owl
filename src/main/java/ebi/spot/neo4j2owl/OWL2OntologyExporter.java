package ebi.spot.neo4j2owl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Procedure;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by matentzn on 05/03/2018.
 * <p>
 */
public class OWL2OntologyExporter {

    @Context
    public GraphDatabaseService db;

    @Context
    public Log log;

    static OWLDataFactory df = OWLManager.getOWLDataFactory();
    //static IRIManager iriManager = new IRIManager();
    static N2OEntityManager n2OEntityManager = new N2OEntityManager();
    static Set<String> qsls_with_no_matching_properties = new HashSet<>();


    static long start = System.currentTimeMillis();

    /*
    @Procedure(mode = Mode.WRITE)
    public Stream<ProgressInfo> makeN2OCompliant(@Name("url") String url) throws Exception {
        log("Making N2O Compliant");
        try {
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(IRI.create(url));

            Map<String, OWLEntity> mapIRIEntity = new HashMap<>();
            o.getSignature(Imports.INCLUDED).forEach(e -> mapIRIEntity.put(e.getIRI().toString(), e));
            Map<String, N2OEntity> mapIRIN2O = new HashMap<>();

            updateNodes(o, mapIRIEntity, mapIRIN2O);
            updateRelations(o, mapIRIEntity, mapIRIN2O);
        } catch (Throwable e) {
            log(e.getMessage());
            e.printStackTrace();
        }

        return (Stream.of(new ProgressInfo(url)));
    }

    private void updateRelations(OWLOntology o, Map<String, OWLEntity> mapIRIEntity, Map<String, N2OEntity> mapIRIN2O) {
        getEdgeIRIs().stream().map(x->x.get("iri")).forEach(iri->updateRelationship(o, mapIRIEntity, mapIRIN2O, iri));
    }


    private Result getEdgeIRIs() {
        String cypher_get_edge_types = "MATCH (n)-[r]->(x) RETURN distinct r.iri as iri";
        log(cypher_get_edge_types);
        return db.execute(cypher_get_edge_types);
    }

    private void updateRelationship(OWLOntology o, Map<String, OWLEntity> mapIRIEntity, Map<String, N2OEntity> mapIRIN2O, Object i) {
        if (i == null) {
            throw new IllegalArgumentException("All edges must have an iri property!");
        }
        String iri = (String)i;
        N2OEntity n = createNewEntity(o, mapIRIEntity, mapIRIN2O, iri);

        String cypher_update_node = String.format(
                "MATCH (n)-[r {iri:\"%s\"}]->(m) " +
                        "CREATE (n)-[r2:%s]->(m) " +
                        "SET r2 = r "
                //+ "WITH r "+
                // "DELETE r"
                ,
                n.getIri(),
                n.getQualified_safe_label());
        log(cypher_update_node);
        db.execute(cypher_update_node);
    }

    private N2OEntity createNewEntity(OWLOntology o, Map<String, OWLEntity> mapIRIEntity, Map<String, N2OEntity> mapIRIN2O, String iri) {
        if (!mapIRIN2O.containsKey(iri)) {
            OWLEntity e = mapIRIEntity.get(iri);
            N2OEntity n = new N2OEntity(e, o, iriManager, -1);
            mapIRIN2O.put(iri, n);
        }
        return mapIRIN2O.get(iri);
    }

*/
    private void log(Object msg) {
        log.info(msg.toString());
        System.out.println(msg + " " + getTimePassed());
    }

    private String getTimePassed() {
        long time = System.currentTimeMillis() - start;
        return ((double) time / 1000.0) + " sec";
    }

    /*
    private void updateNodes(OWLOntology o, Map<String, OWLEntity> mapIRIEntity, Map<String, N2OEntity> mapIRIN2O) {
        String cypher_getAllNodes = "MATCH (n) RETURN distinct n";
        Result res_nodes = db.execute(cypher_getAllNodes);
        int ct_entitiesnotin = 0;
        while (res_nodes.hasNext()) {
            Map<String, Object> r = res_nodes.next();
            NodeProxy np = (NodeProxy) r.get("n");
            //log(np);
            //log( np.getAllProperties());
            String iri = np.getAllProperties().get("iri").toString();
            //log(iri);
            if(mapIRIEntity.containsKey(iri)) {
                OWLEntity e = mapIRIEntity.get(iri);
                //log(e);
                N2OEntity n = new N2OEntity(e, o, iriManager, np.getId());
                mapIRIN2O.put(iri, n);
                String cypher_update_node = String.format("MERGE (n {iri:\"%s\"}) " +
                                "ON MATCH SET n:%s " +
                                "ON MATCH SET n +={ " +
                                "qsl:\"%s\" " +
                                //"short_form:'%s', " +
                                //"label:'%s', " +
                                //"sl:'%s', " +
                                //"curie:'%s'" +
                                " }",
                        iri,
                        Util.concat(n.getTypes(),":"),
                        n.getQualified_safe_label(),
                        n.getShort_form(),
                        n.getLabel(),
                        n.getSafe_label(),
                        n.getCurie()
                );
                //log(cypher_update_node);
                db.execute(cypher_update_node);
            }
            else {
                ct_entitiesnotin++;
            }
        }
        log("Entities not in ontology: "+ct_entitiesnotin);
    }
*/

    @Procedure(mode = Mode.WRITE)
    public Stream<OntologyReturnValue> exportOWL() throws Exception { //@Name("file") String fileName
        try {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();

        OWLOntology o = man.createOntology();
        addEntities(o);
        addAnnotations(o);
        addRelation(o,OWL2NeoMapping.RELTYPE_SUBCLASSOF);
        addRelation(o,OWL2NeoMapping.RELTYPE_INSTANCEOF);
        for(String rel_qsl:getRelations(OWLAnnotationProperty.class)) {
            addRelation(o,rel_qsl);
        }
        for(String rel_qsl:getRelations(OWLObjectProperty.class)) {
            addRelation(o,rel_qsl);
        }
        for(String rel_qsl:getRelations(OWLDataProperty.class)) {
            addRelation(o,rel_qsl);
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream(); //new FileOutputStream(new File(fileName))
        man.saveOntology(o, new RDFXMLDocumentFormat(), os);
        qsls_with_no_matching_properties.forEach(this::log);
        return Stream.of(new OntologyReturnValue(os.toString(java.nio.charset.StandardCharsets.UTF_8.name()),o.getLogicalAxiomCount()+"")); }
        catch (Exception e) {
            e.printStackTrace();
            return Stream.of(new OntologyReturnValue(e.getClass().getSimpleName(),getStackTrace(e)));
        }
    }

    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    private Set<String> getRelations(Class cl) {
        return n2OEntityManager.relationshipQSLs().stream().filter(k->cl.isInstance(n2OEntityManager.getRelationshipByQSL(k))).collect(Collectors.toSet());
    }

    private void addRelation(OWLOntology o, String RELTYPE) throws N2OException {
        //log("addRelation():"+RELTYPE);
        //log(mapIdEntity);
        String cypher = String.format("MATCH (n:Entity)-[r:" + RELTYPE + "]->(x:Entity) Return n,r,x");
        Result s = db.execute(cypher);
        List<OWLOntologyChange> changes = new ArrayList<>();
        while (s.hasNext()) {
            Map<String, Object> r = s.next();
            //log(r);
            Long nid = ((NodeProxy) r.get("n")).getId();
            Long xid = ((NodeProxy) r.get("x")).getId();
            changes.add(createAddAxiom(o, nid, xid,RELTYPE));
        }
        if(!changes.isEmpty()) {
            try {
                o.getOWLOntologyManager().applyChanges(changes);
            } catch (Exception e) {
                String msg = "";
                for(OWLOntologyChange c:changes) {
                    msg+=c.toString()+"\n";
                }
                throw new N2OException(msg,e);
            }
        }
    }

    private AddAxiom createAddAxiom(OWLOntology o, Long from, Long to, String type) throws N2OException {

        OWLEntity e_from = n2OEntityManager.getEntity(from);
        OWLEntity e_to = n2OEntityManager.getEntity(to);
        if(type.equals(OWL2NeoMapping.RELTYPE_SUBCLASSOF)) {
            return new AddAxiom(o, df.getOWLSubClassOfAxiom((OWLClass) e_from, (OWLClass) e_to));
        } else if(type.equals(OWL2NeoMapping.RELTYPE_INSTANCEOF)) {
            return new AddAxiom(o, df.getOWLClassAssertionAxiom((OWLClass) e_to, (OWLIndividual) e_from));
        } else {
            OWLEntity p = n2OEntityManager.getRelationshipByQSL(type);
            if(p instanceof OWLObjectProperty) {
                if (e_from instanceof OWLClass) {
                    if (e_to instanceof OWLClass) {
                        return new AddAxiom(o, df.getOWLSubClassOfAxiom((OWLClass) e_from, df.getOWLObjectSomeValuesFrom((OWLObjectProperty)p,(OWLClass)e_to)));
                    } else if (e_to instanceof OWLNamedIndividual) {
                        return new AddAxiom(o, df.getOWLSubClassOfAxiom((OWLClass) e_from, df.getOWLObjectHasValue((OWLObjectProperty)p,(OWLNamedIndividual)e_to)));
                    } else {
                       log("Not deal with OWLClass-"+type+"-X");
                    }
                } else if (e_from instanceof OWLNamedIndividual) {
                    if (e_to instanceof OWLClass) {
                        return new AddAxiom(o, df.getOWLClassAssertionAxiom(df.getOWLObjectSomeValuesFrom((OWLObjectProperty)p,(OWLClass)e_to),(OWLNamedIndividual)e_from));
                    } else if (e_to instanceof OWLNamedIndividual) {
                        return new AddAxiom(o, df.getOWLObjectPropertyAssertionAxiom((OWLObjectProperty)p,(OWLNamedIndividual) e_from,(OWLNamedIndividual)e_to));
                    } else {
                        log("Not deal with OWLClass-"+type+"-X");
                    }
                } else {
                    log("Not deal with X-"+type+"-X");
                }
            } if(p instanceof OWLAnnotationProperty) {
                return new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(e_from.getIRI(), df.getOWLAnnotation((OWLAnnotationProperty)p,e_to.getIRI())));
            }
        }
        throw new N2OException("Unknown relationship type: "+type,new NullPointerException());
    }


    private void addAnnotations(OWLOntology o) {
        List<OWLOntologyChange> changes = new ArrayList<>();
        n2OEntityManager.entities().forEach(e->addAnnotationsForEntity(o, changes, e));
        o.getOWLOntologyManager().applyChanges(changes);
    }

    private void addAnnotationsForEntity(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e) {
        n2OEntityManager.annotationsProperties(e).forEach(qsl_anno-> addEntityForEntityAndAnnotationProperty(o, changes, e, qsl_anno));
        OWLAnnotationProperty annop = df.getOWLAnnotationProperty(IRI.create(OWL2NeoMapping.NEO4J_LABEL));
        n2OEntityManager.nodeLabels(e).forEach(type->changes.add(new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(annop, e.getIRI(), df.getOWLLiteral(type)))));
        n2OEntityManager.nodeLabels(e).forEach(type->changes.add(new AddAxiom(o, df.getOWLDeclarationAxiom(e))));
    }

    private void addEntityForEntityAndAnnotationProperty(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e, String qsl_anno) {
        //log("Q:" + qsl_anno);
        Object annos = n2OEntityManager.annotationValues(e,qsl_anno);
        //log("A:" + annos + " " + annos.getClass());
        if (annos instanceof Collection) {
            for (Object aa : (Collection) annos) {
                OWLEntity annoP = getAnnotationProperty(qsl_anno);
                if(annoP == null) {
                    qsls_with_no_matching_properties.add(qsl_anno);
                } else {
                    addAnnotationForEntityAndAnnotationAndValueProperty(o, changes, e, getAnnotationProperty(qsl_anno), aa);
                }
            }
        }
    }

    private void addAnnotationForEntityAndAnnotationAndValueProperty(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e, OWLAnnotationProperty annop, Object aa) {
        if (aa instanceof String[]) {
            for (String value : (String[]) aa) {
                //log("V:" + value + " " + value.getClass());
                changes.add(new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(annop, e.getIRI(), df.getOWLLiteral(value))));
            }
        }
    }

    private OWLAnnotationProperty getAnnotationProperty(String qsl_anno) {
        OWLEntity e = n2OEntityManager.getRelationshipByQSL(qsl_anno);
        if (e instanceof OWLAnnotationProperty) {
            return (OWLAnnotationProperty) e;
        }
        log("Warning: QSL "+qsl_anno+" was not found!");
        return null;
    }

    private void addEntities(OWLOntology o) {
        String nodevariable = "n";
        String cypher = String.format("MATCH (n:Entity) Return %s",nodevariable);
        db.execute(cypher).stream().forEach(r->createEntityForEachLabel(r,nodevariable));
        n2OEntityManager.entities().forEach((e->addDeclaration(e,o)));
    }

    private void addDeclaration(OWLEntity e, OWLOntology o) {
        o.getOWLOntologyManager().addAxiom(o,df.getOWLDeclarationAxiom(e));
    }

    private void createEntityForEachLabel(Map<String, Object> r, String nodevar) {
        NodeProxy n = (NodeProxy) r.get(nodevar);
        n.getLabels().forEach(l->n2OEntityManager.createEntity(n,l.name()));
    }





}

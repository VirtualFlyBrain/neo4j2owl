package ebi.spot.neo4j2owl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.File;
import java.io.FileOutputStream;
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
    static IRIManager iriManager = new IRIManager();
    static N2OEntityManager n2OEntityManager = new N2OEntityManager();


    static long start = System.currentTimeMillis();

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


    private void log(Object msg) {
        log.info(msg.toString());
        System.out.println(msg + " " + getTimePassed());
    }

    private String getTimePassed() {
        long time = System.currentTimeMillis() - start;
        return ((double) time / 1000.0) + " sec";
    }

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
                        n.getType(),
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

    @Procedure(mode = Mode.WRITE)
    public Stream<ProgressInfo> exportOWL(@Name("file") String fileName) throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();

        OWLOntology o = man.createOntology();
        createEntities(o);
        addAnnotations(o);
        addRelation(o,OWL2NeoMapping.RELTYPE_SUBCLASSOF);
        addRelation(o,OWL2NeoMapping.RELTYPE_INSTANCEOF);
        for(String rel:getRelations(OWLAnnotationProperty.class)) {
            addRelation(o,rel);
        }
        for(String rel:getRelations(OWLObjectProperty.class)) {
            addRelation(o,rel);
        }
        for(String rel:getRelations(OWLDataProperty.class)) {
            addRelation(o,rel);
        }
        man.saveOntology(o, new RDFXMLDocumentFormat(), new FileOutputStream(new File(fileName)));
        return (Stream.of(new ProgressInfo(fileName)));
    }

    private Set<String> getRelations(Class cl) {
        return n2OEntityManager.entitiesQsls().stream().filter(k->cl.isInstance(n2OEntityManager.getEntity(k))).collect(Collectors.toSet());
    }

    private void addRelation(OWLOntology o, String RELTYPE) {
        log("addRelation():"+RELTYPE);
        //log(mapIdEntity);
        String cypher = String.format("MATCH (n:Entity)-[r:" + RELTYPE + "]->(x:Entity) Return n,r,x");
        Result s = db.execute(cypher);
        List<OWLOntologyChange> changes = new ArrayList<>();
        while (s.hasNext()) {
            Map<String, Object> r = s.next();
            log(r);
            Long nid = ((NodeProxy) r.get("n")).getId();
            Long xid = ((NodeProxy) r.get("x")).getId();
            changes.add(createAddAxiom(o, nid, xid,RELTYPE));
        }
        o.getOWLOntologyManager().applyChanges(changes);
    }

    private AddAxiom createAddAxiom(OWLOntology o, Long from, Long to, String type) {

        OWLEntity e_from = n2OEntityManager.getEntity(from);
        OWLEntity e_to = n2OEntityManager.getEntity(to);
        if(type.equals(OWL2NeoMapping.RELTYPE_SUBCLASSOF)) {
            return new AddAxiom(o, df.getOWLSubClassOfAxiom((OWLClass) e_from, (OWLClass) e_to));
        } else if(type.equals(OWL2NeoMapping.RELTYPE_INSTANCEOF)) {
            return new AddAxiom(o, df.getOWLClassAssertionAxiom((OWLClass) e_to, (OWLIndividual) e_from));
        } else {
            OWLEntity p = n2OEntityManager.getEntity(type);
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
            }
        }
        return null;
    }


    private void addAnnotations(OWLOntology o) {
        List<OWLOntologyChange> changes = new ArrayList<>();
        n2OEntityManager.entities().forEach(e->addAnnotationsForEntity(o, changes, e));
        o.getOWLOntologyManager().applyChanges(changes);
    }

    private void addAnnotationsForEntity(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e) {
        n2OEntityManager.annotationsProperties(e).forEach(qsl_anno-> addEntityForEntityAndAnnotationProperty(o, changes, e, qsl_anno));
    }

    private void addEntityForEntityAndAnnotationProperty(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e, String qsl_anno) {
        log("Q:" + qsl_anno);
        Object annos = n2OEntityManager.annotationValues(e,qsl_anno);
        log("A:" + annos + " " + annos.getClass());
        if (annos instanceof Collection) {
            for (Object aa : (Collection) annos) {
                addAnnotationForEntityAndAnnotationAndValueProperty(o, changes, e, qsl_anno, aa);
            }
        }
    }

    private void addAnnotationForEntityAndAnnotationAndValueProperty(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e, String qsl_anno, Object aa) {
        if (aa instanceof String[]) {
            for (String value : (String[]) aa) {
                log("V:" + value + " " + value.getClass());
                changes.add(new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(getAnnotationProperty(qsl_anno), e.getIRI(), df.getOWLLiteral(value.toString()))));
            }
        }
    }

    private OWLAnnotationProperty getAnnotationProperty(String qsl_anno) {
        OWLEntity e = n2OEntityManager.getEntity(qsl_anno);
        if (e instanceof OWLAnnotationProperty) {
            return (OWLAnnotationProperty) e;
        }
        return null;
    }

    private void createEntities(OWLOntology o) {
        String nodevariable = "n";
        String cypher = String.format("MATCH (n:Entity) Return %s",nodevariable);
        db.execute(cypher).stream().forEach(r->createEntityForEachLabel(r,nodevariable));
    }

    private void createEntityForEachLabel(Map<String, Object> r, String nodevar) {
        NodeProxy n = (NodeProxy) r.get(nodevar);
        n.getLabels().forEach(l->n2OEntityManager.createEntity(n,l.name()));
    }





}

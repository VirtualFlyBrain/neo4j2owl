package ebi.spot.neo4j2owl;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;

import static org.junit.Assert.assertEquals;

/**
 * Created by jbarrasa on 21/03/2016.
 */
public class OWL2OntologyExporterTest {

    @Test
    public void owl2Export() throws Exception {
        GraphDatabaseService db = prepareImporterExporterDB();
        Result importResult = db.execute("CALL ebi.spot.neo4j2owl.owl2Import('https://raw.githubusercontent.com/matentzn/ontologies/master/smalltest.owl','https://raw.githubusercontent.com/VirtualFlyBrain/vfb-prod/master/neo4j2owl-config.yaml')");
        Result exportResult = db.execute("CALL ebi.spot.neo4j2owl.exportOWL()");
        System.out.println("Export Results");
        exportResult.stream().forEach(m->System.out.println(m.get("o")));
    }

    private GraphDatabaseService prepareImporterExporterDB() throws KernelException {
        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(OWL2OntologyImporter.class);
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(OWL2OntologyExporter.class);
        return db;
    }

    private GraphDatabaseService prepareExporterDB() throws KernelException {
        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(OWL2OntologyExporter.class);
        return db;
    }

    //@Test
    public void equivalentSubclassesELK() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df =  man.getOWLDataFactory();
        OWLClass A = df.getOWLClass(IRI.create("cl:A"));
        OWLClass B = df.getOWLClass(IRI.create("cl:B"));
        OWLOntology o = man.createOntology();
        man.addAxiom(o,df.getOWLEquivalentClassesAxiom(A,B));
        OWLReasoner r = new ElkReasonerFactory().createReasoner(o);
        r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        assertEquals(r.getEquivalentClasses(A).contains(B),true);
        assertEquals(r.getSubClasses(A,true).containsEntity(B),true);
        assertEquals(r.getSubClasses(A,false).containsEntity(B),true);
    }

    //@Test
    public void equivalentSubclassesHermiT() throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df =  man.getOWLDataFactory();
        OWLClass A = df.getOWLClass(IRI.create("cl:A"));
        OWLClass B = df.getOWLClass(IRI.create("cl:B"));
        OWLOntology o = man.createOntology();
        man.addAxiom(o,df.getOWLEquivalentClassesAxiom(A,B));
        OWLReasoner r = new ReasonerFactory().createReasoner(o);
        r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        assertEquals(r.getEquivalentClasses(A).contains(B),true);
        assertEquals(r.getSubClasses(A,false).containsEntity(B),true);
        assertEquals(r.getSubClasses(A,true).containsEntity(B),true);
    }
}

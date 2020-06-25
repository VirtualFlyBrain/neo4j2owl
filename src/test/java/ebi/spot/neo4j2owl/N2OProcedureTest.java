package ebi.spot.neo4j2owl;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * Created by jbarrasa on 21/03/2016.
 */
public class N2OProcedureTest {


    private static String test_resources_web = "https://raw.githubusercontent.com/VirtualFlyBrain/neo4j2owl/master/src/test/resources/";



    private GraphDatabaseService setUpDB() throws KernelException {
        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(N2OProcedure.class);
        return db;
    }

    @Test
    public void owl2ImportFromWeb() throws Exception {
        String ontologyUrl = test_resources_web+"smalltest.owl";
        String configUrl = test_resources_web+"smalltest-config.yaml";
        runSmallTest(ontologyUrl,configUrl);
    }

    @Test
    public void owl2ImportFromLocal() throws Exception {
        URI uriOntology = getClass().getClassLoader().getResource("smalltest.owl").toURI();
        URI uriConfig = getClass().getClassLoader().getResource("smalltest-config.yaml").toURI();
        runSmallTest(uriOntology.toURL().toString(),uriConfig.toURL().toString());
    }

    private void runSmallTest(String ontologyUrl, String configUrl) throws KernelException {
        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')",ontologyUrl, configUrl);

        Result importResult = db.execute(call);

        System.out.println(db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));
        Result res = db.execute("MATCH (n)-[p]->(x)  RETURN p");
        while(res.hasNext()) {
            System.out.println(res.next().get("p"));
        }

        Result res2 = db.execute("MATCH (n)  RETURN n.label_rdfs");
        while(res2.hasNext()) {
            System.out.println(res2.next().get("n.label_rdfs"));
        }

        //assertEquals(new Long(16), importResult.next().get("elementsLoaded"));

        //assertEquals(new Long(2), db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(5), db.execute("MATCH (n:DatatypeProperty)-[:DOMAIN]->(:Class)  RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(3), db.execute("MATCH (n:DatatypeProperty)-[:DOMAIN]->(:ObjectProperty) RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(6), db.execute("MATCH (n:ObjectProperty) RETURN count(n) AS count").next().get("count"));
        db.shutdown();
    }

   @Test
    public void owl2ImportLarge() throws Exception {

        String ontologyUrl = "https://raw.githubusercontent.com/matentzn/ontologies/master/issue2_neo2owl.owl";
        String configUrl = "https://raw.githubusercontent.com/VirtualFlyBrain/vfb-prod/master/neo4j2owl-config.yaml";

        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')",ontologyUrl, configUrl);

        Result importResult = db.execute(call);
        System.out.println(db.execute("MATCH (n:Entity) RETURN count(n) AS count").next().get("count"));
        Result res = db.execute("MATCH (n)-[p]->(x)  RETURN p LIMIT 20");
        while(res.hasNext()) {
            System.out.println(res.next().get("n"));
        }
        db.shutdown();
    }


    @Test
    public void owl2Export() throws Exception {
        String ontologyUrl = "https://raw.githubusercontent.com/matentzn/ontologies/master/issue2_neo2owl.owl";
        String configUrl = "https://raw.githubusercontent.com/VirtualFlyBrain/vfb-prod/master/neo4j2owl-config.yaml";

        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')",ontologyUrl, configUrl);

        Result importResult = db.execute(call);
        Result exportResult = db.execute("CALL ebi.spot.neo4j2owl.exportOWL()");
        System.out.println("Export Results");
        exportResult.stream().forEach(m->System.out.println(m.get("o")));
        db.shutdown();
    }

}

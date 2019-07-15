package ebi.spot.neo4j2owl;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Created by jbarrasa on 21/03/2016.
 */
public class OWL2OntologyImporterTest {



    //@Test
    public void owl2ImportSmall() throws Exception {

       GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(OWL2OntologyImporter.class);

        /*Result importResult = db.execute("CALL ebi.spot.neo4j2owl.owl2Import('" +
                OWL2OntologyImporterTest.class.getClassLoader().getResource("moviesontology.owl").toURI()
                + "','RDF/XML')"); */

        Result importResult = db.execute("CALL ebi.spot.neo4j2owl.owl2Import('https://raw.githubusercontent.com/matentzn/ontologies/master/smalltest.owl','n')");
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

    }


   @Test
    public void owl2ImportLarge() throws Exception {

        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI)db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(OWL2OntologyImporter.class);

        /*Result importResult = db.execute("CALL ebi.spot.neo4j2owl.owl2Import('" +
                OWL2OntologyImporterTest.class.getClassLoader().getResource("moviesontology.owl").toURI()
                + "','RDF/XML')");
        https://github.com/VirtualFlyBrain/VFB_owl/raw/master/src/owl/2016-12-01/vfb.owl.gz
       https://raw.githubusercontent.com/FlyBase/flybase-controlled-vocabulary/master/releases/fbcv.owl*/

        Result importResult = db.execute("CALL ebi.spot.neo4j2owl.owl2Import('https://raw.githubusercontent.com/FlyBase/flybase-controlled-vocabulary/master/releases/fbcv.owl','n')");
        System.out.println(db.execute("MATCH (n:Entity) RETURN count(n) AS count").next().get("count"));
        Result res = db.execute("MATCH (n)-[p]->(x)  RETURN p LIMIT 20");
        while(res.hasNext()) {
            System.out.println(res.next().get("n"));
        }


        //assertEquals(new Long(16), importResult.next().get("elementsLoaded"));

        //assertEquals(new Long(2), db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(5), db.execute("MATCH (n:DatatypeProperty)-[:DOMAIN]->(:Class)  RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(3), db.execute("MATCH (n:DatatypeProperty)-[:DOMAIN]->(:ObjectProperty) RETURN count(n) AS count").next().get("count"));

        //assertEquals(new Long(6), db.execute("MATCH (n:ObjectProperty) RETURN count(n) AS count").next().get("count"));

    }

}

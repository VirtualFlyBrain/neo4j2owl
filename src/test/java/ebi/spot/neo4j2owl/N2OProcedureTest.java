package ebi.spot.neo4j2owl;

import ebi.spot.neo4j2owl.exporter.N2OException;
import ebi.spot.neo4j2owl.importer.N2OCSVWriter;
import ebi.spot.neo4j2owl.importer.N2OImportResult;
import ebi.spot.neo4j2owl.importer.N2OImportService;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class N2OProcedureTest {

    private static final String test_resources_web = "https://raw.githubusercontent.com/VirtualFlyBrain/neo4j2owl/master/src/test/resources/";

    private GraphDatabaseService setUpDB() throws KernelException {
        GraphDatabaseService db = new TestGraphDatabaseFactory().newImpermanentDatabase();
        ((GraphDatabaseAPI) db).getDependencyResolver().resolveDependency(Procedures.class).registerProcedure(N2OProcedure.class);
        return db;
    }

    @Test
    public void owl2ImportFromWeb() throws Exception {
        String ontologyUrl = test_resources_web + "smalltest.owl";
        String configUrl = test_resources_web + "smalltest-config.yaml";
        runSmallTest(ontologyUrl, configUrl);
    }

    @Test
    public void owl2ImportFromLocal() throws Exception {
        URI uriOntology = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest.owl")).toURI();
        URI uriConfig = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest-config.yaml")).toURI();
        runSmallTest(uriOntology.toURL().toString(), uriConfig.toURL().toString());
    }

    private void runSmallTest(String ontologyUrl, String configUrl) throws KernelException {
        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

        Result importResult = db.execute(call);
        Map<String, Object> resMap = importResult.next();
        assertEquals(37L, resMap.get("elementsLoaded"));
        assertEquals(16L, resMap.get("classesLoaded"));
        assertEquals("", resMap.get("extraInfo"));
        assertEquals(16L, db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));
        db.shutdown();
    }

    @Test
    public void owl2ImportAPI() throws Exception {
        File importdir = new File("results");
        importdir.mkdir();
        String ontologyUrl = test_resources_web + "smalltest.owl";
        String configUrl = test_resources_web + "smalltest-config.yaml";
        N2OImportService importService = new N2OImportService();
        N2OImportResult importResults = new N2OImportResult();
        importService.prepareConfig(configUrl, importdir);
        N2OCSVWriter csvWriter = importService.prepareCSVFilesForImport(ontologyUrl, importdir, importResults);
        csvWriter.exportN2OImportConfig(new File(importdir,"csv_imports_config.yaml"));
    }

    @Test
    public void owl2ImportLarge() throws Exception {

        String ontologyUrl = test_resources_web + "issue2.owl";
        String configUrl = test_resources_web + "issue2-config.yaml";

        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

        Result importResult = db.execute(call);
        Map<String, Object> resMap = importResult.next();
        assertEquals(22L, resMap.get("elementsLoaded"));
        assertEquals(9L, resMap.get("classesLoaded"));
        assertEquals("", resMap.get("extraInfo"));
        assertEquals(9L, db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));
        db.shutdown();
    }


    @Test
    public void owl2Export() throws Exception {
        //String ontologyUrl = test_resources_web + "smalltest.owl";
        //String configUrl = test_resources_web + "smalltest-config.yaml";
        String ontologyUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest.owl")).toURI().toURL().toString();
        String configUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest-config.yaml")).toURI().toURL().toString();

        GraphDatabaseService db = setUpDB();
        String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

        Result importResult = db.execute(call);
        Map<String, Object> resMap = importResult.next();
        Result exportResult = db.execute("CALL ebi.spot.neo4j2owl.exportOWL()");
        Map<String, Object> resMapExport = exportResult.next();

        assertEquals(37L, resMap.get("elementsLoaded"));
        assertEquals(16L, resMap.get("classesLoaded"));
        assertEquals("", resMap.get("extraInfo"));
        assertEquals(16L, db.execute("MATCH (n:Class) RETURN count(n) AS count").next().get("count"));

        String ontologyString = (String) resMapExport.get("o");
        OWLOntologyManager man1 = OWLManager.createOWLOntologyManager();
        OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();
        OWLOntology o_orginal = man1.loadOntologyFromOntologyDocument(IRI.create(ontologyUrl));
        InputStream is = new ByteArrayInputStream(ontologyString.getBytes());
        OWLOntology o_neoexport = man2.loadOntologyFromOntologyDocument(is);

        equalOntologies(o_orginal, o_neoexport);
        db.shutdown();
    }

    /*
    The importer does not really roundtrip, because of the use of reasoner, and because we dont import equivalent class axioms. Still, a few check can be run, especially on annotations.
     */
    private void equalOntologies(OWLOntology o_orginal, OWLOntology o_neoexport) {
        assertFalse(o_orginal.isEmpty());
        //assertEquals(o_orginal.getSignature(Imports.INCLUDED), o_neoexport.getSignature(Imports.INCLUDED));
        Set<OWLAxiom> axioms_original = new HashSet<>(o_orginal.getAxioms().stream().filter(ax -> !(ax instanceof OWLDeclarationAxiom)).filter(ax2 -> !(ax2 instanceof OWLEquivalentClassesAxiom)).collect(Collectors.toSet()));
        Set<OWLAxiom> axioms_export = new HashSet<>(o_neoexport.getAxioms().stream().filter(ax -> !(ax instanceof OWLDeclarationAxiom)).filter(ax2 -> !(ax2 instanceof OWLEquivalentClassesAxiom)).collect(Collectors.toSet()));
        axioms_original.removeAll(o_neoexport.getAxioms());
        System.out.println("------------");
        axioms_original.forEach(System.out::println);
        System.out.println("------------");
        axioms_export.removeAll(o_orginal.getAxioms());
        //axioms_export.forEach(System.out::println);
        assertTrue(axioms_original.isEmpty());
        //assertTrue(axioms_export.isEmpty());
        //assertEquals(o_orginal.getAxiomCount(), o_neoexport.getAxiomCount());
    }

}

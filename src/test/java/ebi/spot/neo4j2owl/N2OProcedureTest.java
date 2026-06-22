package ebi.spot.neo4j2owl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.exceptions.KernelException;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import ebi.spot.neo4j2owl.importer.N2OCSVWriter;
import ebi.spot.neo4j2owl.importer.N2OImportResult;
import ebi.spot.neo4j2owl.importer.N2OImportService;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class N2OProcedureTest {

	private static final String test_resources_web = "https://raw.githubusercontent.com/VirtualFlyBrain/neo4j2owl/master/src/test/resources/";

	private static final Config driverConfig = Config.builder().withoutEncryption().build();
	private Driver driver;
	private Neo4j embeddedDatabaseServer;

	@BeforeAll
	void initializeNeo4j() {
		this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
				.withProcedure(N2OProcedure.class).build();

		this.driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI(), driverConfig);
	}

	@AfterAll
	void closeDriver() {
		this.driver.close();
		this.embeddedDatabaseServer.close();
	}

	@AfterEach
	void cleanDb() {
		try (Session session = driver.session()) {
			session.run("MATCH (n) DETACH DELETE n");
		}
	}

	@BeforeEach
	void resetConfig() {
		// N2OConfig is a process-wide singleton that prepareConfig mutates rather than
		// replaces. In production each import is its own process, but the test suite
		// shares one JVM, so reset it between methods to stop one test's config (e.g.
		// smalltest's nic: node labelling) leaking into another's import.
		N2OConfig.resetConfig();
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
		URI uriConfig = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest-config.yaml"))
				.toURI();
		runSmallTest(uriOntology.toURL().toString(), uriConfig.toURL().toString());
	}

	private void runSmallTest(String ontologyUrl, String configUrl) throws KernelException {
		try (Session session = driver.session()) {
			String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

			Result importResult = session.run(call);
			Map<String, Object> resMap = importResult.next().asMap();
			assertEquals(37L, resMap.get("elementsLoaded"));
			assertEquals(16L, resMap.get("classesLoaded"));
			assertEquals("", resMap.get("extraInfo"));
			assertEquals(16, session.run("MATCH (n:Class) RETURN count(n) AS count").next().get("count").asInt());
		}
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
		File cypherDir = new File(importdir, "transactions");
		if (!cypherDir.isDirectory()) {
			cypherDir.mkdir();
		}
		csvWriter.exportN2OImportConfig(cypherDir);
	}

	@Test
	public void owl2ImportLarge() throws Exception {
		try (Session session = driver.session()) {
			String ontologyUrl = test_resources_web + "issue2.owl";
			String configUrl = test_resources_web + "issue2-config.yaml";

			String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

			Result importResult = session.run(call);
			Map<String, Object> resMap = importResult.next().asMap();
			assertEquals(22L, resMap.get("elementsLoaded"));
			assertEquals(9L, resMap.get("classesLoaded"));
			assertEquals("", resMap.get("extraInfo"));
			assertEquals(9, session.run("MATCH (n:Class) RETURN count(n) AS count").next().get("count").asInt());
		}
	}

	/**
	 * Regression test for issue #407: the CSV importer must keep parallel
	 * relationships of the same type between the same pair of nodes distinct
	 * rather than collapsing them onto a single edge whose properties overwrite
	 * each other. Exercises the full owl2Import path (CSV write + LOAD CSV MERGE)
	 * over a fixture that encodes both reported reproductions.
	 */
	@Test
	public void issue407ParallelEdgesSurvive() throws Exception {
		String ontologyUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("issue407.owl")).toURI()
				.toURL().toString();
		String configUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("issue407-config.yaml"))
				.toURI().toURL().toString();

		try (Session session = driver.session()) {
			session.run(String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl))
					.consume();

			// Case A: all four database_cross_reference accessions survive, including
			// the three to the same DoOR Site that previously collapsed onto one edge.
			assertEquals("all four xref edges should survive", 4L, count(session,
					"MATCH (:Class {short_form:'FBbt_00067011'})-[r:database_cross_reference]->() RETURN count(r) AS count"));
			assertEquals("three parallel xref edges to the same Site should survive", 3L, count(session,
					"MATCH (:Class {short_form:'FBbt_00067011'})-[r:database_cross_reference]->({short_form:'DoOR'}) RETURN count(r) AS count"));
			Set<String> accessions = new HashSet<>(session.run(
					"MATCH (:Class {short_form:'FBbt_00067011'})-[r:database_cross_reference]->({short_form:'DoOR'}) "
							+ "UNWIND r.accession AS a RETURN collect(DISTINCT a) AS accs")
					.next().get("accs").asList(v -> v.asString()).stream().collect(Collectors.toSet()));
			assertEquals("no accession should be overwritten",
					new HashSet<>(java.util.Arrays.asList("DoOR:Or65a", "DoOR:Or65b", "DoOR:Or65c")), accessions);

			// Case B: definition and synonym references to the same publication stay
			// distinct, and the definition edge carries no synonym typing/value.
			assertEquals("def and syn references to one pub should be two edges", 2L, count(session,
					"MATCH (:Class {short_form:'FBbt_00049999'})-[r:has_reference]->({short_form:'FBrf0224194'}) RETURN count(r) AS count"));
			org.neo4j.driver.Record defRec = session.run(
					"MATCH (:Class {short_form:'FBbt_00049999'})-[r:has_reference]->({short_form:'FBrf0224194'}) "
							+ "WHERE 'def' IN r.typ RETURN r.has_synonym_type AS hst, r.value AS value").next();
			assertTrue("definition edge must not carry a synonym type", defRec.get("hst").isNull());
			assertTrue("definition edge must not carry a synonym value", defRec.get("value").isNull());

			// Systemic: no has_reference edge carries synonym typing on a non-syn edge.
			assertEquals("no contaminated reference edges should remain", 0L, count(session,
					"MATCH ()-[r:has_reference]->() WHERE r.has_synonym_type IS NOT NULL AND NOT ('syn' IN r.typ) RETURN count(r) AS count"));
		}
	}

	private long count(Session session, String query) {
		return session.run(query).next().get("count").asLong();
	}

	@Test
	public void owl2Export() throws Exception {
		// String ontologyUrl = test_resources_web + "smalltest.owl";
		// String configUrl = test_resources_web + "smalltest-config.yaml";
		String ontologyUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest.owl")).toURI()
				.toURL().toString();
		String configUrl = Objects.requireNonNull(getClass().getClassLoader().getResource("smalltest-config.yaml"))
				.toURI().toURL().toString();

		try (Session session = driver.session()) {
			String call = String.format("CALL ebi.spot.neo4j2owl.owl2Import('%s','%s')", ontologyUrl, configUrl);

			Result importResult = session.run(call);
			Map<String, Object> resMap = importResult.next().asMap();
			Result exportResult = session.run("CALL ebi.spot.neo4j2owl.exportOWL()");
			Map<String, Object> resMapExport = exportResult.next().asMap();

			assertEquals(37L, resMap.get("elementsLoaded"));
			assertEquals(16L, resMap.get("classesLoaded"));
			assertEquals("", resMap.get("extraInfo"));
			assertEquals(16, session.run("MATCH (n:Class) RETURN count(n) AS count").next().get("count").asInt());

			// exportOWL() returns the ontology as chunked List<String> (N2OReturnValue.o);
			// join the chunks rather than casting the list to String.
			@SuppressWarnings("unchecked")
			String ontologyString = String.join("", (java.util.List<String>) resMapExport.get("o"));
			OWLOntologyManager man1 = OWLManager.createOWLOntologyManager();
			OWLOntologyManager man2 = OWLManager.createOWLOntologyManager();
			OWLOntology o_orginal = man1.loadOntologyFromOntologyDocument(IRI.create(ontologyUrl));
			InputStream is = new ByteArrayInputStream(ontologyString.getBytes());
			OWLOntology o_neoexport = man2.loadOntologyFromOntologyDocument(is);

			equalOntologies(o_orginal, o_neoexport);
		}
	}

	/*
	 * The importer does not really roundtrip, because of the use of reasoner, and
	 * because we dont import equivalent class axioms. Still, a few check can be
	 * run, especially on annotations.
	 */
	private void equalOntologies(OWLOntology o_orginal, OWLOntology o_neoexport) {
		assertFalse(o_orginal.isEmpty());
		// assertEquals(o_orginal.getSignature(Imports.INCLUDED),
		// o_neoexport.getSignature(Imports.INCLUDED));
		Set<OWLAxiom> axioms_original = new HashSet<>(
				o_orginal.getAxioms().stream().filter(ax -> !(ax instanceof OWLDeclarationAxiom))
						.filter(ax2 -> !(ax2 instanceof OWLEquivalentClassesAxiom)).collect(Collectors.toSet()));
		Set<OWLAxiom> axioms_export = new HashSet<>(
				o_neoexport.getAxioms().stream().filter(ax -> !(ax instanceof OWLDeclarationAxiom))
						.filter(ax2 -> !(ax2 instanceof OWLEquivalentClassesAxiom)).collect(Collectors.toSet()));
		axioms_original.removeAll(o_neoexport.getAxioms());
		System.out.println("------------");
		axioms_original.forEach(System.out::println);
		System.out.println("------------");
		axioms_export.removeAll(o_orginal.getAxioms());
		// axioms_export.forEach(System.out::println);
		assertTrue(axioms_original.isEmpty());
		// assertTrue(axioms_export.isEmpty());
		// assertEquals(o_orginal.getAxiomCount(), o_neoexport.getAxiomCount());
	}

}

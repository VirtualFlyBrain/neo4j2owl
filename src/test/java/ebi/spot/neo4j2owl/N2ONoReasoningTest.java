package ebi.spot.neo4j2owl;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import ebi.spot.neo4j2owl.importer.IRIManager;
import ebi.spot.neo4j2owl.importer.N2OImportManager;
import ebi.spot.neo4j2owl.importer.N2OImportResult;
import ebi.spot.neo4j2owl.importer.N2OOntologyLoader;

/**
 * Test that query based functions provides the same results with the reasoning
 * based functions.
 * 
 * @author huseyin
 */
class N2ONoReasoningTest {

	private static final File TEST_ONTOLOGY = new File("./src/test/resources/bigtest_reasoned_with_tags.owl");
	private static OWLOntology o;
	private static N2OOntologyLoader ontologyImporter;
	private static OWLReasoner r;
	private static final String CONFIG = "file://bigtest_config.yaml";
	private static File test_output = new File("./src/test/resources/test_output");

	@BeforeAll
	public static void setup() {
		try {
			o = OWLManager.createOWLOntologyManager()
					.loadOntologyFromOntologyDocument(IRI.create(TEST_ONTOLOGY.toURI()));
			ontologyImporter = new N2OOntologyLoader();

			r = new ElkReasonerFactory().createReasoner(o);
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
	}

	@Test
	void testGetDirectSubClasses() throws OWLOntologyCreationException {
		Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
		for (OWLClass e : entities) {
			Set<OWLClass> reasonedClasses = ontologyImporter.getSubClasses(r, e, true, true);
			reasonedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
			Set<OWLClass> queriedClasses = ontologyImporter.querySubClasses(o, e, true, true);
			queriedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());

			printDifference(reasonedClasses, queriedClasses);
			assertEquals(reasonedClasses.size(), queriedClasses.size());

			for (OWLClass clazz : reasonedClasses) {
				assertTrue(queriedClasses.contains(clazz));
			}
		}
	}

	@Test
	void testGetAllSubClasses() throws OWLOntologyCreationException {
		Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
		for (OWLClass e : entities) {
			Set<OWLClass> reasonedClasses = ontologyImporter.getSubClasses(r, e, false, false);
			reasonedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
			Set<OWLClass> queriedClasses = ontologyImporter.querySubClasses(o, e, false, false);
			queriedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());

			printDifference(reasonedClasses, queriedClasses);
			assertEquals(reasonedClasses.size(), queriedClasses.size());

			for (OWLClass clazz : reasonedClasses) {
				assertTrue(queriedClasses.contains(clazz));
			}
		}
	}

	@Test
	void testGetTypes() throws OWLOntologyCreationException {
		Set<OWLNamedIndividual> entities = new HashSet<>(o.getIndividualsInSignature(Imports.INCLUDED));
		for (OWLNamedIndividual e : entities) {
			Set<OWLClass> reasonedTypes = r.getTypes(e, true).getFlattened();
			Set<OWLClass> queriedTypes = ontologyImporter.queryTypes(o, e, true);

			printDifference(reasonedTypes, queriedTypes);
			assertEquals(reasonedTypes.size(), queriedTypes.size());

			for (OWLClass clazz : reasonedTypes) {
				assertTrue(queriedTypes.contains(clazz));
			}
		}
	}

	@Test
	void testGetIndividuals() throws OWLOntologyCreationException {
		Set<OWLClass> entities = new HashSet<>(o.getClassesInSignature(Imports.INCLUDED));
		for (OWLClass e : entities) {
			Set<OWLNamedIndividual> reasonedIndvs = ontologyImporter.getInstances(r, e);
			Set<OWLNamedIndividual> queriedIndvs = ontologyImporter.queryInstances(o, e);

			printDifference(reasonedIndvs, queriedIndvs);
			assertEquals(reasonedIndvs.size(), queriedIndvs.size());

			for (OWLNamedIndividual indv : reasonedIndvs) {
				assertTrue(queriedIndvs.contains(indv));
			}
		}
	}

	@Test
	void testAddingDynamicAnnotations() throws OWLOntologyCreationException, N2OException, IOException {
		String annotationIRI = "http://n2o.neo/property/nodeLabel";

		N2OConfig.getInstance().prepareConfig(CONFIG, test_output);

		N2OOntologyLoader reasonedLoader = new N2OOntologyLoader();
		reasonedLoader.importOntology(o, new N2OImportResult(), true, null);

		N2OOntologyLoader prereasonedLoader = new N2OOntologyLoader();
		prereasonedLoader.importOntology(o, new N2OImportResult(), false, annotationIRI);

		Map<OWLEntity, Set<String>> reasonedLabels = reasonedLoader.getImportManager().getNodeLabels();
		Map<OWLEntity, Set<String>> queriedLabels = prereasonedLoader.getImportManager().getNodeLabels();

		assertTrue(reasonedLabels.keySet().size() > 0);
		printDifference(reasonedLabels.keySet(), queriedLabels.keySet());
		assertEquals(reasonedLabels.keySet().size(), queriedLabels.keySet().size());
		for (OWLEntity entity : reasonedLabels.keySet()) {
			System.out.println(entity + "   " + reasonedLabels.get(entity));
			assertTrue(queriedLabels.containsKey(entity));
			assertEquals(reasonedLabels.get(entity), queriedLabels.get(entity));
		}

	}

	@Test
	void testGetSubClassesOfExpression() throws OWLOntologyCreationException {
		IRIManager iriManager = new IRIManager();
		N2OImportManager manager = new N2OImportManager(o, iriManager);
		List<String> expressions = new ArrayList<>();
		expressions.add("RO:0000053 some PATO:0070030");
		expressions.add("RO:0015002 some UBERON:0002616");
		expressions.add("RO:0015002 some PCL:0010001");
		expressions.add("RO:0000053 some PATO:0070011");
		expressions.add("RO:0015002 some CL:0011005");
		expressions.add("RO:0015002 some CL:0000359");
		expressions.add("RO:0000053 some PATO:0070019");
		expressions.add("RO:0002100 some UBERON:0002771");
		// following crashes because, query cannot handle sub-property based class
		// expressions
		// 'in taxonomy' some NCBITaxon:10090 vs 'only in taxonomy' some NCBITaxon:10090
//		expressions.add("RO:0002162 some NCBITaxon:10090");

		for (String expression : expressions) {
			System.out.println(expression);
			OWLClassExpression e = manager.parseExpression(expression);

			Set<OWLClass> reasonedClasses = ontologyImporter.getSubClasses(r, e, false, false);
			reasonedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());
			Set<OWLClass> queriedClasses = ontologyImporter.querySubClassesOfClassExpression(o, e, false, false);
			queriedClasses.remove(o.getOWLOntologyManager().getOWLDataFactory().getOWLNothing());

			printDifference(reasonedClasses, queriedClasses);
			assertEquals(reasonedClasses.size(), queriedClasses.size());

			for (OWLClass clazz : reasonedClasses) {
				assertTrue(queriedClasses.contains(clazz));
			}
		}

	}

	private void printDifference(Set<?> reasonedResult, Set<?> queriedResult) {
		if (reasonedResult.size() != queriedResult.size()) {
			System.out.println("Reasoned: ");
			for (Object indv : reasonedResult) {
				System.out.println(indv);
			}

			System.out.println("Queried: ");
			for (Object indv : queriedResult) {
				System.out.println(indv);
			}
			System.out.println("Diff: ");
			Set<?> reasonedResultCopy = new HashSet<>(reasonedResult);
			reasonedResultCopy.removeAll(queriedResult);
			for (Object indv : reasonedResultCopy) {
				System.out.println(indv);
			}
		}
	}

}

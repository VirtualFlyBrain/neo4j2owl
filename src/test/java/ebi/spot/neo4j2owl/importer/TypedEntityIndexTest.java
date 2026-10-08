package ebi.spot.neo4j2owl.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

/**
 * typedEntity() used to scan the whole node index for every IRI-valued
 * annotation. It now uses an IRI index; these tests check the answers are
 * identical to the old scan (including punned IRIs and unknown IRIs) and that
 * lookups no longer scale with the size of the node index.
 */
class TypedEntityIndexTest {

	private static final File TEST_ONTOLOGY = new File("./src/test/resources/bigtest_reasoned_with_tags.owl");

	/** The pre-index implementation, kept here as the reference. */
	private static OWLEntity referenceScan(N2OImportManager manager, IRI iri, OWLDataFactory df) {
		for (OWLEntity e : manager.getNodeIndex().keySet()) {
			if (e.getIRI().equals(iri)) {
				return e;
			}
		}
		return df.getOWLClass(iri);
	}

	@Test
	void sameAnswersAsLinearScan() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology o = m.loadOntologyFromOntologyDocument(IRI.create(TEST_ONTOLOGY.toURI()));
		OWLDataFactory df = m.getOWLDataFactory();
		N2OImportManager manager = new N2OImportManager(o, new IRIManager());

		for (OWLEntity e : o.getSignature(Imports.INCLUDED)) {
			manager.getNode(e);
		}
		// Punning: one IRI registered as both a class and an individual.
		IRI punned = IRI.create("http://example.org/punned_1");
		manager.getNode(df.getOWLClass(punned));
		manager.getNode(df.getOWLNamedIndividual(punned));

		List<IRI> probes = new ArrayList<>();
		for (OWLEntity e : o.getSignature(Imports.INCLUDED)) {
			probes.add(e.getIRI());
		}
		probes.add(punned);
		probes.add(IRI.create("http://rdf.rhea-db.org/12140"));
		probes.add(IRI.create("https://orcid.org/0000-0001-7476-6306"));

		assertTrue(probes.size() > 50, "test ontology should give a meaningful number of probes");
		for (IRI iri : probes) {
			OWLEntity expected = referenceScan(manager, iri, df);
			OWLEntity actual = manager.typedEntity(iri, o);
			assertEquals(expected, actual, "typedEntity differs from the linear scan for " + iri);
			// An unknown IRI is registered as a class; asking again must return the same.
			assertEquals(actual, manager.typedEntity(iri, o));
		}
	}

	@Test
	void lookupDoesNotScaleWithNodeIndex() throws Exception {
		OWLOntologyManager m = OWLManager.createOWLOntologyManager();
		OWLOntology o = m.createOntology(IRI.create("http://example.org/scale"));
		OWLDataFactory df = m.getOWLDataFactory();
		N2OImportManager manager = new N2OImportManager(o, new IRIManager());

		int n = 100_000;
		for (int i = 0; i < n; i++) {
			manager.getNode(df.getOWLNamedIndividual(IRI.create("http://example.org/ind_" + i)));
		}
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			IRI iri = IRI.create("http://example.org/ind_" + i);
			assertEquals(df.getOWLNamedIndividual(iri), manager.typedEntity(iri, o));
		}
		double seconds = (System.nanoTime() - start) / 1e9;
		// The old scan needed ~n^2/2 = 5e9 IRI comparisons here (minutes); the index is
		// well under a second. The bound is loose so slow CI runners do not flake.
		assertTrue(seconds < 20, "100k typedEntity lookups took " + seconds + " s");
	}
}

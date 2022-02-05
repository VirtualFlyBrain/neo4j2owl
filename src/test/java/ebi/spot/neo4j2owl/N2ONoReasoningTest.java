package ebi.spot.neo4j2owl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import ebi.spot.neo4j2owl.importer.N2OOntologyLoader;

/**
 * Test that query based functions provides the same results with the reasoning
 * based functions.
 * 
 * @author huseyin
 */
class N2ONoReasoningTest {

	private static final File TEST_ONTOLOGY = new File("./src/test/resources/bigtest_reasoned.owl");
	private static OWLOntology o;
	private static N2OOntologyLoader ontologyImporter;
	private static OWLReasoner r;

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
			Set<OWLClass> queriedClasses = querySubClasses(o, e, true, true);
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
			Set<OWLClass> queriedClasses = querySubClasses(o, e, false, false);
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
			Set<OWLClass> queriedTypes = queryTypes(o, e, true);

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
			Set<OWLNamedIndividual> queriedIndvs = queryIndividuals(o, e);

			printDifference(reasonedIndvs, queriedIndvs);
			assertEquals(reasonedIndvs.size(), queriedIndvs.size());

			for (OWLNamedIndividual indv : reasonedIndvs) {
				assertTrue(queriedIndvs.contains(indv));
			}
		}
	}

	private Set<OWLClass> querySubClasses(OWLOntology o, OWLClass e, boolean direct, boolean excludeEquivalentClasses) {
		return querySubClasses(o, e.asOWLClass(), direct, excludeEquivalentClasses, new HashSet<OWLClass>());
	}

	private Set<OWLClass> querySubClasses(OWLOntology o, OWLClass e, boolean direct, boolean excludeEquivalentClasses,
			Set<OWLClass> scannedClasses) {
		Set<OWLSubClassOfAxiom> subClassAxioms = o.getSubClassAxiomsForSuperClass(e);
		Set<OWLClass> subClasses = subClassAxioms.stream().filter(scoa -> !scoa.getSubClass().isAnonymous())
				.map(scoa -> scoa.getSubClass().asOWLClass()).collect(Collectors.toSet());

		subClasses.addAll(queryEquivalentClasses(o, e));

		if (!direct) {
			subClasses.add(e);
			Set<OWLClass> indepthSubClasses = new HashSet<>();
			for (OWLClass subclass : subClasses) {
				if (!scannedClasses.contains(subclass)) {
					scannedClasses.add(subclass);
					indepthSubClasses
							.addAll(querySubClasses(o, subclass, direct, excludeEquivalentClasses, scannedClasses));
				}
			}
			subClasses.addAll(indepthSubClasses);
		}

		if (excludeEquivalentClasses && e.isClassExpressionLiteral()) {
			subClasses.remove(e.asOWLClass());
		}

		return subClasses;
	}

	private Set<OWLClass> queryEquivalentClasses(OWLOntology o, OWLClassExpression e) {
		Set<OWLEquivalentClassesAxiom> equivalentClassesAxioms = o.getEquivalentClassesAxioms(e.asOWLClass());
		Set<OWLClass> eqClasses = new HashSet<>();
		for (OWLEquivalentClassesAxiom eca : equivalentClassesAxioms) {
			eqClasses.addAll(eca.getNamedClasses());
		}

		return eqClasses;
	}

	private Set<OWLClass> queryTypes(OWLOntology o, OWLNamedIndividual e, boolean direct) {
		Collection<OWLClassExpression> types = EntitySearcher.getTypes(e, o);
		return types.stream().filter(type -> !type.isAnonymous()).map(type -> type.asOWLClass())
				.collect(Collectors.toSet());
	}

	private Set<OWLNamedIndividual> queryIndividuals(OWLOntology o, OWLClass e) {
		Set<OWLClass> subClasses = querySubClasses(o, e, false, false);
		Set<OWLNamedIndividual> indvs = new HashSet<>();
		for (OWLClass clazz : subClasses) {
			Collection<OWLIndividual> individuals = EntitySearcher.getIndividuals(clazz, o);
			for (OWLIndividual indv : individuals) {
				if (indv instanceof OWLNamedIndividual) {
					indvs.add((OWLNamedIndividual) indv);
				}
			}
		}
		return indvs;
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
		}
	}

}

package ebi.spot.neo4j2owl.exporter;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import ebi.spot.neo4j2owl.N2OException;
import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;

public class N2OExportService {

	private GraphDatabaseService db;
	private final static N2OLog logger = N2OLog.getInstance();
	private final OWLDataFactory df = OWLManager.getOWLDataFactory();
	// static IRIManager iriManager = new IRIManager();
	private N2OExportManager n2OEntityManager;
	private Set<String> qsls_with_no_matching_properties;

	public N2OExportService(GraphDatabaseService db) {
		this.db = db;
	}

	public N2OReturnValue owl2Export() {
		n2OEntityManager = new N2OExportManager();
		qsls_with_no_matching_properties = new HashSet<>();
		logger.resetTimer();
		N2OReturnValue returnValue = new N2OReturnValue();

		try {
			OWLOntologyManager man = OWLManager.createOWLOntologyManager();

			OWLOntology o = man.createOntology();
			addEntities(o);
			addAnnotations(o);
			addRelation(o, N2OStatic.RELTYPE_SUBCLASSOF);
			addRelation(o, N2OStatic.RELTYPE_INSTANCEOF);
			for (String rel_qsl : getRelations(OWLAnnotationProperty.class)) {
				addRelation(o, rel_qsl);
			}
			for (String rel_qsl : getRelations(OWLObjectProperty.class)) {
				addRelation(o, rel_qsl);
			}
			ByteArrayOutputStream os = new ByteArrayOutputStream(); // new FileOutputStream(new File(fileName))
			man.saveOntology(o, new RDFXMLDocumentFormat(), os);
			qsls_with_no_matching_properties.forEach(logger::log);
//			String osString = os.toString(java.nio.charset.StandardCharsets.UTF_16.name());
			List<String> ontologyChunks = createArrayChunks(os.toByteArray());
			returnValue.setOntology(ontologyChunks);
			returnValue.setLog(o.getLogicalAxiomCount() + "");
		} catch (Exception e) {
			e.printStackTrace();
			returnValue.setLog(logger.getStackTrace(e));
		}
		return returnValue;
	}

	private Set<String> getRelations(Class cl) {
		return n2OEntityManager.relationshipQSLs().stream()
				.filter(k -> cl.isInstance(n2OEntityManager.getRelationshipByQSL(k))).collect(Collectors.toSet());
	}

	private void addRelation(OWLOntology o, String RELTYPE) throws N2OException {
		// log("addRelation():"+RELTYPE);
		// log(mapIdEntity);
		String cypher = String.format("MATCH (n:Entity)-[r:" + RELTYPE + "]->(x:Entity) Return n,r,x");
		Result s;
		try (Transaction tx = db.beginTx()) {
			s = tx.execute(cypher);
			Objects.requireNonNull(s);
			List<OWLOntologyChange> changes = new ArrayList<>();
			while (s.hasNext()) {
				Map<String, Object> r = s.next();
				Object object = r.get("n");
				// log(r);
	            Long nid = ((Node) r.get("n")).getId();
	            Long xid = ((Node) r.get("x")).getId();
	            Relationship rp = (Relationship) r.get("r");

	            OWLAxiom ax = createAxiom(n2OEntityManager.getEntity(nid), n2OEntityManager.getEntity(xid), RELTYPE);
	            Set<OWLAnnotation> axiomAnnotations = getAxiomAnnotations(rp);
	            changes.add(new AddAxiom(o, ax.getAnnotatedAxiom(axiomAnnotations)));
			}
			if (!changes.isEmpty()) {
				try {
					o.getOWLOntologyManager().applyChanges(changes);
				} catch (Exception e) {
					String msg = "";
					for (OWLOntologyChange c : changes) {
						msg += c.toString() + "\n";
					}
					throw new N2OException(msg, e);
				}
			}
		} catch (Exception e) {
			throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE + cypher, e);
		}
	}

	private Set<OWLAnnotation> getAxiomAnnotations(Relationship rp) {
		Set<OWLAnnotation> axiomAnnotations = new HashSet<>();
		Map<String, Object> rpros = rp.getAllProperties();
		for (String propertykey : rpros.keySet()) {
			if (!N2OStatic.isN2OBuiltInProperty(propertykey)) {
				OWLAnnotationProperty ap = getAnnotationProperty(propertykey);
				Object v = rpros.get(propertykey);
				if (v.getClass().isArray()) {
					for (Object val : toObjectArray(v)) {
						OWLAnnotationValue value = getLiteral(val);
						axiomAnnotations.add(df.getOWLAnnotation(ap, value));
					}
				} else {
					OWLAnnotationValue value = getLiteral(v);
					axiomAnnotations.add(df.getOWLAnnotation(ap, value));
				}
			}
		}
		return axiomAnnotations;
	}

	private OWLAxiom createAxiom(OWLEntity e_from, OWLEntity e_to, String type) throws N2OException {

		if (type.equals(N2OStatic.RELTYPE_SUBCLASSOF)) {
			return df.getOWLSubClassOfAxiom((OWLClass) e_from, (OWLClass) e_to);
		} else if (type.equals(N2OStatic.RELTYPE_INSTANCEOF)) {
			return df.getOWLClassAssertionAxiom((OWLClass) e_to, (OWLIndividual) e_from);
		} else {
			OWLEntity p = n2OEntityManager.getRelationshipByQSL(type);
			if (p instanceof OWLObjectProperty) {
				if (e_from instanceof OWLClass) {
					if (e_to instanceof OWLClass) {
						return df.getOWLSubClassOfAxiom((OWLClass) e_from,
								df.getOWLObjectSomeValuesFrom((OWLObjectProperty) p, (OWLClass) e_to));
					} else if (e_to instanceof OWLNamedIndividual) {
						return df.getOWLSubClassOfAxiom((OWLClass) e_from,
								df.getOWLObjectHasValue((OWLObjectProperty) p, (OWLNamedIndividual) e_to));
					} else {
						logger.warning("Not deal with OWLClass-" + type + "-X");
					}
				} else if (e_from instanceof OWLNamedIndividual) {
					if (e_to instanceof OWLClass) {
						return df.getOWLClassAssertionAxiom(
								df.getOWLObjectSomeValuesFrom((OWLObjectProperty) p, (OWLClass) e_to),
								(OWLNamedIndividual) e_from);
					} else if (e_to instanceof OWLNamedIndividual) {
						return df.getOWLObjectPropertyAssertionAxiom((OWLObjectProperty) p, (OWLNamedIndividual) e_from,
								(OWLNamedIndividual) e_to);
					} else {
						logger.warning("Not deal with OWLClass-" + type + "-X");
					}
				} else {
					logger.warning("Not deal with X-" + type + "-X");
				}
			}
			if (p instanceof OWLAnnotationProperty) {
				return df.getOWLAnnotationAssertionAxiom(e_from.getIRI(),
						df.getOWLAnnotation((OWLAnnotationProperty) p, e_to.getIRI()));
			}
		}
		throw new N2OException("Unknown relationship type: " + type, new NullPointerException());
	}

	private void addAnnotations(OWLOntology o) {
		List<OWLOntologyChange> changes = new ArrayList<>();
		n2OEntityManager.entities().forEach(e -> addAnnotationsForEntity(o, changes, e));
		o.getOWLOntologyManager().applyChanges(changes);
	}

	private void addAnnotationsForEntity(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e) {
		n2OEntityManager.annotationsProperties(e)
				.forEach(qsl_anno -> addEntityForEntityAndAnnotationProperty(o, changes, e, qsl_anno));

		// Add all neo4jlabels to node
		n2OEntityManager.nodeLabels(e).forEach(
				type -> changes.add(createAnnotationAxiom(o, e, N2OStatic.ap_neo4jLabel, df.getOWLLiteral(type))));

		// Add entity declarations for all entities
		// TODO this is probably redundant with the initial addEntities(o); call.
		n2OEntityManager.nodeLabels(e).forEach(type -> changes.add(new AddAxiom(o, df.getOWLDeclarationAxiom(e))));
	}

	private void addEntityForEntityAndAnnotationProperty(OWLOntology o, List<OWLOntologyChange> changes, OWLEntity e,
			String qsl_anno) {
		if (!N2OStatic.isN2OBuiltInProperty(qsl_anno)) {
			Object annos = n2OEntityManager.annotationValues(e, qsl_anno);
			OWLAnnotationProperty annoP = getAnnotationProperty(qsl_anno);
			if (annos instanceof Collection) {
				for (Object aa : (Collection) annos) {
					if (annoP == null) {
						qsls_with_no_matching_properties.add(qsl_anno);
					} else {
						addAnnotationForEntityAndAnnotationAndValueProperty(o, changes, e, annoP, aa);
					}
				}
			}
		}
	}

	/*
	 * This method maps neo4j property value to OWL
	 */
	private void addAnnotationForEntityAndAnnotationAndValueProperty(OWLOntology o, List<OWLOntologyChange> changes,
			OWLEntity e, OWLAnnotationProperty annop, Object aa) {
		if (aa.getClass().isArray()) {
			aa = toObjectArray(aa);
			for (Object value : (Object[]) aa) {
				// System.out.println("AVV: " + value.getClass());
				// System.out.println("IRI: " + annop.getIRI());
				changes.add(createAnnotationAxiom(o, e, annop, getLiteral(value)));
			}
		} else {
			changes.add(createAnnotationAxiom(o, e, annop, getLiteral(aa)));
		}
	}

	public boolean isValidJSONObject(String test) {
		try {
			JSONObject o = new JSONObject(test);
			return (o.has("value") && o.has("annotations"));
		} catch (JSONException ex) {
			return false;
		}
	}

	private AddAxiom createAnnotationAxiom(OWLOntology o, OWLEntity e, OWLAnnotationProperty annop,
			OWLAnnotationValue literal) {
		Set<OWLAnnotation> annotations = new HashSet<>();
		if (literal.isLiteral()) {
			String lit = literal.asLiteral().or(df.getOWLLiteral("UNKNOWN")).getLiteral();
			if (isValidJSONObject(lit)) {
				JSONObject c = new JSONObject(lit);
				if (c.has("value")) {
					OWLAnnotationValue val = getLiteral(c.get("value"));
					if (c.has("annotations")) {
						Object pm = c.get("annotations");
						if (pm instanceof JSONObject) {
							for (String anno_sl : ((JSONObject) pm).keySet()) {
								if (!N2OStatic.isN2OBuiltInProperty(anno_sl)) {
									OWLAnnotationProperty annoP = getAnnotationProperty(anno_sl);
									Object annoSetValues = ((JSONObject) pm).get(anno_sl);
									if (annoSetValues instanceof JSONArray) {
										for (Object s : ((JSONArray) annoSetValues)) {
											annotations.add(df.getOWLAnnotation(annoP, getLiteral(s)));
										}
									}
								}
							}
						}
						if (!annotations.isEmpty()) {
							return new AddAxiom(o,
									df.getOWLAnnotationAssertionAxiom(annop, e.getIRI(), val, annotations));
						} else {
							return new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(annop, e.getIRI(), val));
						}
					}

				}
			}
		}
		return new AddAxiom(o, df.getOWLAnnotationAssertionAxiom(annop, e.getIRI(), literal));
	}

	/*
	 * From https://stackoverflow.com/a/5608477/2451542
	 */
	private Object[] toObjectArray(Object val) {
		if (val instanceof Object[])
			return (Object[]) val;
		int arrlength = Array.getLength(val);
		Object[] outputArray = new Object[arrlength];
		for (int i = 0; i < arrlength; ++i) {
			outputArray[i] = Array.get(val, i);
		}
		return outputArray;
	}

	private OWLAnnotationValue getLiteral(Object value) {
		if (value instanceof Boolean) {
			return df.getOWLLiteral((Boolean) value);
		} else if (value instanceof Long) {
			return df.getOWLLiteral((Long) value);
		} else if (value instanceof Integer) {
			return df.getOWLLiteral((Integer) value);
		} else if (value instanceof Float) {
			return df.getOWLLiteral((Float) value);
		} else if (value instanceof Double) {
			return df.getOWLLiteral((Double) value);
		} else {
			return df.getOWLLiteral(value.toString());
		}
	}

	private OWLAnnotationProperty getAnnotationProperty(String qsl_anno) {
		// logger.info("QSL::::"+qsl_anno);
		OWLEntity e = n2OEntityManager.getRelationshipByQSL(qsl_anno);
		// logger.info("E::::"+e.getIRI().toString());
		if (e instanceof OWLAnnotationProperty) {
			return (OWLAnnotationProperty) e;
		}
		// log("Warning: QSL "+qsl_anno+" was not found!");
		return df.getOWLAnnotationProperty(IRI.create(N2OStatic.NEO4J_UNMAPPED_PROPERTY_PREFIX_URI + qsl_anno));
	}

	/*
	 * For every node labelled "Entity" in the KB, create a corresponding OWL
	 * entity, and a declaration in the ontology Nothing else is added at this step
	 * - just declarations. The main purpose is to index all entities for the next
	 * Steps in the pipeline
	 */
	private void addEntities(OWLOntology o) throws N2OException {
		String cypher = "MATCH (n:Entity) Return n";
		Result s;
		try (Transaction tx = db.beginTx()) {
			s = tx.execute(cypher);
			Objects.requireNonNull(s);
			s.stream().forEach(r -> createEntityForEachLabel((Node) r.get("n")));
			n2OEntityManager.entities().stream().filter(e -> !e.isBuiltIn()).forEach((e -> addDeclaration(e, o)));
		} catch (Exception e) {
			throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE + cypher, e);
		}
	}

	private void addDeclaration(OWLEntity e, OWLOntology o) {
		o.getOWLOntologyManager().addAxiom(o, df.getOWLDeclarationAxiom(e));
	}

	private void createEntityForEachLabel(Node n) {
		n.getLabels().forEach(l -> n2OEntityManager.createEntity(n, l.name()));
	}
	
	/**
	 * Ontology size can be bigger than max size of String. Chunks ontology into
	 * sub-strings and represents ontology as multiple strings.
	 * 
	 * @param byteArray ontology in byte array representation.
	 * @return ontology sub-string chunks
	 */
	private List<String> createArrayChunks(byte[] byteArray) {
		int maxArraySize = 500000000;
		int start = 0;
		List<String> chunks = new ArrayList<>();
		while(start < byteArray.length) {
			int exclusive_end = start + maxArraySize;
			if (exclusive_end > byteArray.length) {
				exclusive_end = byteArray.length;	
			}
			byte[] chunk = Arrays.copyOfRange(byteArray, start, exclusive_end);
			chunks.add(new String(chunk, java.nio.charset.StandardCharsets.UTF_8));
			start += maxArraySize;
		}
		return chunks;
	}
}

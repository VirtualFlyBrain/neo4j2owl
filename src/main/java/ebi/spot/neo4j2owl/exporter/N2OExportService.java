package ebi.spot.neo4j2owl.exporter;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Set;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import ebi.spot.neo4j2owl.N2OException;
import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;

public class N2OExportService {

    private GraphDatabaseService db;
    private final static N2OLog logger = N2OLog.getInstance();
    private final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private N2OExportManager n2OEntityManager;
    private Set<String> qsls_with_no_matching_properties;

    public N2OExportService(GraphDatabaseService db) {
        this.db = db;
    }

    private byte[][] createArrayChunks(byte[] data, int chunkSize) {
        if (data == null || data.length == 0) {
            return new byte[0][];
        }

        int numberOfChunks = (int) Math.ceil((double) data.length / chunkSize);
        byte[][] chunks = new byte[numberOfChunks][];

        for (int i = 0; i < numberOfChunks; i++) {
            int start = i * chunkSize;
            int length = Math.min(data.length - start, chunkSize);
            chunks[i] = new byte[length];
            System.arraycopy(data, start, chunks[i], 0, length);
        }

        return chunks;
    }

    public void owl2Export(byte[] data) {
        int chunkSize = 1024 * 1024; 
        byte[][] chunks = createArrayChunks(data, chunkSize);
        for (byte[] chunk : chunks) {
            // Processing each chunk
        }
    }

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

    private OWLAnnotationProperty getAnnotationProperty(String qsl_anno) {
        OWLEntity e = n2OEntityManager.getRelationshipByQSL(qsl_anno);
        if (e instanceof OWLAnnotationProperty) {
            return (OWLAnnotationProperty) e;
        }
        return df.getOWLAnnotationProperty(IRI.create(N2OStatic.NEO4J_UNMAPPED_PROPERTY_PREFIX_URI + qsl_anno));
    }

    private OWLAnnotation createAnnotation(OWLAnnotationProperty property, Object value) {
        OWLAnnotationValue annotationValue = createAnnotationValue(value);
        return df.getOWLAnnotation(property, annotationValue);
    }

    private OWLAnnotationValue createAnnotationValue(Object value) {
        if (value instanceof String) {
            return df.getOWLLiteral((String) value);
        } else if (value instanceof Boolean) {
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
}

package ebi.spot.neo4j2owl;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by jbarrasa on 21/03/2016.
 *
 * Importer of basic ontology (RDFS & OWL) elements:
 *
 */
public class OWL2OntologyExporter {

    @Context
    public GraphDatabaseService db;
    public static OWLDataFactory df = OWLManager.getOWLDataFactory();
    public static final String PREFIX_SEPARATOR = "__";

    /*
    Constants
    */
    private static final String NODETYPE_NAMEDINDIVIDUAL = "NamedIndividual";
    private static final String NODETYPE_OWLCLASS = "Class";
    private static final String NODETYPE_OWLOBJECTPROPERTY = "ObjectProperty";
    private static final String NODETYPE_OWLANNOTATIONPROPERTY = "AnnotationProperty";
    private static final String ATT_LABEL = "label";
    private static final String SAVE_LABEL = "sl";

    @Procedure(mode = Mode.WRITE)
    public Stream<ProgressInfo> exportOWL(@Name("file") String fileName) throws Exception {
        OWLOntologyManager man = OWLManager.createOWLOntologyManager();

        OWLOntology o = man.createOntology();

        String cypher = String.format("MATCH (n) Return n");
        Result s= db.execute(cypher);
        while(s.hasNext()) {
            Map<String, Object> r = s.next();
            NodeProxy n = (NodeProxy) r.get("n");
            Util.p(n.getAllProperties());
        }
        return(Stream.of(new ProgressInfo(fileName)));
    }
}

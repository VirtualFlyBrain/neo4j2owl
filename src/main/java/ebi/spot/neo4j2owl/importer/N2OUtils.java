package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.exporter.N2OException;
import org.apache.commons.io.FileUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mh
 * @since 04.07.17
 */
public class N2OUtils {

    private static  final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private static final DLSyntaxObjectRenderer ren = new DLSyntaxObjectRenderer();


    static Set<String> getLabels(OWLEntity c, OWLOntology o) {
        Set<String> labels = new HashSet<>();

        for (OWLAnnotation a : EntitySearcher.getAnnotations(c, o, df.getRDFSLabel())) {
            OWLAnnotationValue value = a.getValue();
            if (value instanceof OWLLiteral) {
                String val = ((OWLLiteral) value).getLiteral();
                labels.add(val);
            }
        }

        return labels;
    }


    public static Object extractValueFromOWLAnnotationValue(OWLAnnotationValue aval) {
        if (aval.isLiteral()) {
            OWLLiteral literal = aval.asLiteral().or(df.getOWLLiteral("unknownX"));
            if (literal.isBoolean()) {
                return literal.parseBoolean();
            } else if (literal.isDouble()) {
                @SuppressWarnings("WrapperTypeMayBePrimitive")
                Double d = literal.parseDouble();
                //because neo does not have a double datatype, lets cast this to float:
                return d.floatValue();
            } else if (literal.isFloat()) {
                return literal.parseFloat();
            } else if (literal.isInteger()) {
                return literal.parseInteger();
            } else {
                return literal.getLiteral();
            }
            // "xsd:long", literal.getDatatypePrefixedName()
        }
        return "neo4j2owl_UnknownValue";
    }


    public static void writeToFile(File dir, Map<String, List<String>> dataout, N2OCSVWriter.CSV_TYPE nodeclass) throws N2OException {
        for (String type : dataout.keySet()) {
            File f = constructFileHandle(dir, nodeclass.name, type);
            try {
                FileUtils.writeLines(f, dataout.get(type));
            } catch (IOException e) {
                throw new N2OException("Writing to file "+f+" failed..",e);
            }
        }
    }

    static File constructFileHandle(File dir, String nodeclass, String type) {
        return new File(dir, nodeclass + "_" + type + ".txt");
    }

    public static String render(OWLClassExpression ce) {
        return ren.render(ce);
    }

}

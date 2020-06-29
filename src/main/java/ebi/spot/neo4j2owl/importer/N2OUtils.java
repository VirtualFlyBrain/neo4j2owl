package ebi.spot.neo4j2owl.importer;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/**
 * @author mh
 * @since 04.07.17
 */
public class N2OUtils {

    private static final Label[] NO_LABELS = new Label[0];
    private static  final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private static final DLSyntaxObjectRenderer ren = new DLSyntaxObjectRenderer();

    public static <T> T inTx(GraphDatabaseService db, Callable<T> callable) {
        try {
            return inTxFuture(DEFAULT, db, callable).get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error executing in separate transaction: "+e.getMessage(), e);
        }
    }

    public static Set<String> getLabels(OWLEntity c, OWLOntology o) {
        Set<String> labels = new HashSet();
        Iterator var3 = EntitySearcher.getAnnotations(c, o, df.getRDFSLabel()).iterator();

        while(var3.hasNext()) {
            OWLAnnotation a = (OWLAnnotation)var3.next();
            OWLAnnotationValue value = a.getValue();
            if (value instanceof OWLLiteral) {
                String val = ((OWLLiteral)value).getLiteral();
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
                return literal.parseDouble();
            } else if (literal.isFloat()) {
                return literal.parseFloat();
            } else if (literal.isInteger()) {
                return literal.parseInteger();
            } else {
                return literal.getLiteral();
            }
        }
        return "neo4j2owl_UnknownValue";
    }


    public static void writeToFile(File dir, Map<String, List<String>> dataout, String nodeclass) {
        for (String type : dataout.keySet()) {
            try {
                FileUtils.writeLines(new File(dir, nodeclass + "_" + type + ".txt"), dataout.get(type));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static <T> Future<T> inTxFuture(ExecutorService pool, GraphDatabaseService db, Callable<T> callable) {
        try {
            return pool.submit(() -> {
                try (Transaction tx = db.beginTx()) {
                    T result = callable.call();
                    tx.success();
                    return result;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error executing in separate transaction", e);
        }
    }

    public final static ExecutorService DEFAULT = createDefaultPool();

    public static ExecutorService createDefaultPool() {
        int threads = Runtime.getRuntime().availableProcessors()*2;
        int queueSize = threads * 25;
        return new ThreadPoolExecutor(threads / 2, threads, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueSize),
                new CallerBlocksPolicy());
//                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static String neo4jAttributeString(String s) {
        return s.replace("'", "\'");
    }

    public static void p(Object r) {
        System.out.println(r);
    }

    public static String concat(Set<String> strings, String delim) {
        String out = "";
        for(String s:strings) {
            out+=(s+":");
        }
        return out.replaceAll("[:]$","");
    }

    public static String render(OWLClassExpression ce) {
        return ren.render(ce);
    }


    static class CallerBlocksPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                // block caller for 100ns
                LockSupport.parkNanos(100);
                try {
                    // submit again
                    executor.submit(r).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    public static Label[] labels(Object labelNames) {
        if (labelNames==null) return NO_LABELS;
        if (labelNames instanceof List) {
            List names = (List) labelNames;
            Label[] labels = new Label[names.size()];
            int i = 0;
            for (Object l : names) {
                if (l==null) continue;
                labels[i++] = Label.label(l.toString());
            }
            if (i <= labels.length) return Arrays.copyOf(labels,i);
            return labels;
        }
        return new Label[]{Label.label(labelNames.toString())};
    }
}

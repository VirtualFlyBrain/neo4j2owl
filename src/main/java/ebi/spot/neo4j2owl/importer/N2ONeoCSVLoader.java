package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import ebi.spot.neo4j2owl.exporter.N2OException;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class N2ONeoCSVLoader {

    private final N2OLog log = N2OLog.getInstance();
    private final GraphDatabaseAPI dbapi;


    N2ONeoCSVLoader(GraphDatabaseAPI dbapi) {
        this.dbapi = dbapi;
    }

    void loadRelationshipsToNeoFromCSV(ExecutorService exService, N2OImportCSVConfig config, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        runQueriesForQueryType(exService, importdir, config, "relationship_");
    }

    void loadNodesToNeoFromCSV(ExecutorService exService, N2OImportCSVConfig config , File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        runQueriesForQueryType(exService, importdir, config, "nodes_");
    }

    private void runQueriesForQueryType(ExecutorService exService, File importdir, N2OImportCSVConfig config, String typeToQuery) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (N2OImportCSVConfig.N2OCSVImport importQuery: config.getImportList()) {
            String filename = importQuery.getCsvFilename();
            if (filename.startsWith(typeToQuery)) {
                runQuery(exService, importdir, importQuery, filename);
            }
        }
    }

    private void runQuery(ExecutorService exService, File importdir, N2OImportCSVConfig.N2OCSVImport importQuery, String filename) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        filename = handleTestMode(importdir, filename);
        String finalCypher = importQuery.getCypherQuery().replaceAll("[$]FILENAME[$]", filename);
        log.log(finalCypher);
        final Future<String> cf = exService.submit(() -> {
            try {
                dbapi.execute(finalCypher);
            } catch (QueryExecutionException e) {
                throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE+ finalCypher, e);
            }
            return N2OStatic.CYPHER_EXECUTED_SUCCESSFULLY + finalCypher;
        });
        log.log(cf.get());
                /*if(fn.contains("Individual")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/
    }

    private String handleTestMode(File importdir, String filename) throws IOException {
        if (N2OConfig.getInstance().isTestmode()) {
            String fn = "/" + new File(importdir, filename).getAbsolutePath();
            log.warning("CURRENTLY RUNNING IN TESTMODE, should set to testmode: false.");
            FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
            return fn;
        }
        return filename;
    }


}

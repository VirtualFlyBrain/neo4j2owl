package ebi.spot.neo4j2owl;

import ebi.spot.neo4j2owl.exporter.N2OExportService;
import ebi.spot.neo4j2owl.exporter.N2OReturnValue;
import ebi.spot.neo4j2owl.importer.*;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Created by Nicolas Matentzoglu for EMBL-EBI and Virtual Fly Brain. Code roughly based on jbarrasa neosemantics.
 */
public class N2OProcedure {

    @Context
    public GraphDatabaseService db;

    @Context
    public GraphDatabaseAPI dbapi;

    private static final N2OLog logger = N2OLog.getInstance();


    @SuppressWarnings("unused")
    @Procedure(mode = Mode.DBMS)
    public Stream<N2OImportResult> owl2Import(@Name("url") String url, @Name("config") String config) {
        logger.resetTimer();
        N2OImportResult result = owl2Import(url, config, dbapi);
        return Stream.of(result);
    }

    @SuppressWarnings("unused")
    @Procedure(mode = Mode.DBMS)
    public Stream<N2OImportResult> importOntology(@Name("url") String url) {
        logger.resetTimer();
        N2OImportService importService = new N2OImportService();
        N2OImportResult result = owl2Import(url, null, dbapi);
        return Stream.of(result);
    }

    @SuppressWarnings("unused")
    @Procedure(mode = Mode.WRITE)
    public Stream<N2OReturnValue> exportOWL() {
        logger.resetTimer();
        N2OExportService importService = new N2OExportService(db);
        N2OReturnValue result = importService.owl2Export();
        return Stream.of(result);
    }


    public N2OImportResult owl2Import(String url, String config, GraphDatabaseAPI dbapi) {
        N2OImportService importService = new N2OImportService();
        File importdir = prepareImportDirectory(dbapi);
        N2OImportResult importResults = new N2OImportResult();
        try {
            importService.prepareConfig(config, importdir);

            logger.log("Preprocessing...");
            final ExecutorService exService = Executors.newSingleThreadExecutor();
            for(String cypher:N2OConfig.getInstance().getPreprocessingCypherQueries()) {
                try {
                    runQuery(dbapi, exService, cypher);
                } catch (QueryExecutionException e) {
                    throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE + cypher, e);
                }
            }

            //inserter = BatchInserters.inserter( inserttmp);
            logger.log("Converting OWL ontology to CSV..");
            N2OCSVWriter csvWriter = importService.prepareCSVFilesForImport(url, importdir, importResults);

            logger.log("Create necessary indices..");
            runQuery(dbapi, exService,"CREATE INDEX ON :Entity(iri)");

            logger.log("Loading nodes to neo from CSV.");
            N2ONeoCSVLoader csvLoader = new N2ONeoCSVLoader(dbapi);
            csvLoader.loadNodesToNeoFromCSV(exService, csvWriter.getCSVImportConfig(), importdir);

            logger.log("Loading relationships to neo from CSV.");
            csvLoader.loadRelationshipsToNeoFromCSV(exService, csvWriter.getCSVImportConfig(), importdir);

            logger.log("Loading done..");
            exService.shutdown();
            try {
                logger.log("Stopping executor..");
                exService.awaitTermination(N2OConfig.getInstance().getTimeoutInMinutes(), TimeUnit.MINUTES);
                logger.log("All done..");
            } catch (InterruptedException e) {
                throw new N2OException("Query interrupted for some reason..",e);
            }

        } catch (Exception e) {
            logger.error("An error has occurred..");
            e.printStackTrace();
            logger.error(e.getMessage());
            importResults.setTerminationKO("ERROR: "+e.getMessage());
        } finally {
            logger.log("done");
            N2OConfig.resetConfig();
            //logger.error("delete CSV file IN IMPORTS DIR IS UNCOMMENTED! COMMENT!!!");
            deleteCSVFilesInImportsDir(importdir);
        }
        return importResults;
    }

    private File prepareImportDirectory(GraphDatabaseAPI dbapi) {
        Map<String, String> params = dbapi.getDependencyResolver().resolveDependency(Config.class).getRaw();
        String par_neo4jhome = "unsupported.dbms.directories.neo4j_home";
        String par_importdirpath = "dbms.directories.import";
        File importdir = new File(dbapi.getStoreDir(), "import");
        logger.info("Import dir: " + importdir.getAbsolutePath());
        if (params.containsKey(par_neo4jhome) && params.containsKey(par_importdirpath)) {
            String neo4j_home_path = params.get(par_neo4jhome);
            String import_dir_path = params.get(par_importdirpath);
            File i = new File(import_dir_path);
            if (i.isAbsolute()) {
                importdir = i;
            } else {
                importdir = new File(neo4j_home_path, import_dir_path);
            }
        } else {
            logger.warning("Import directory path (or base neo4j directory) not in neo4j config. Trying to find manually.");
        }
        if (importdir.getAbsoluteFile().exists()) {
            deleteCSVFilesInImportsDir(importdir);
        }
        return importdir;
    }

    private void deleteCSVFilesInImportsDir(File importdir) {
        if (importdir.exists()) {
            for (File f : FileUtils.listFiles(importdir,null,false)) {
                if (f.getName().startsWith(N2OCSVWriter.CSV_TYPE.NODES.name()) || f.getName().startsWith(N2OCSVWriter.CSV_TYPE.RELATIONSHIPS.name())) {
                    FileUtils.deleteQuietly(f);
                }
            }
        }
    }

    private void runQuery(GraphDatabaseAPI dbapi, ExecutorService exService, String cypher) {
        exService.submit(() -> {
            try {
                dbapi.execute(cypher);
            } catch (QueryExecutionException e) {
                throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE+cypher, e);
            }
            return N2OStatic.CYPHER_EXECUTED_SUCCESSFULLY+cypher;
        });
    }
}

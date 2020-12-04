package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import ebi.spot.neo4j2owl.exporter.N2OException;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.Name;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class N2OImportService {

    private final static N2OLog logger = N2OLog.getInstance();

    public N2OImportService() {
    }

    public N2OImportResult owl2Import(String url, String config, GraphDatabaseAPI dbapi) {
        File importdir = prepareImportDirectory(dbapi);
        N2OImportResult importResults = new N2OImportResult();
        try {
            prepareConfig(config, importdir);

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
            N2OCSVWriter csvWriter = prepareCSVFilesForImport(url, importdir, importResults);

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

    public void prepareConfig(String config, File importdir) throws IOException, N2OException {
        if(config !=null) {
            logger.log("Loading config: " + config);
            N2OConfig.getInstance().prepareConfig(config, importdir);
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

    public N2OCSVWriter prepareCSVFilesForImport(String url, File importdir, N2OImportResult importResults) throws OWLOntologyCreationException, IOException, InterruptedException, ExecutionException, N2OException {
        logger.log("Loading Ontology");
        OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(getOntologyIRI(url, importdir));
        logger.log("Size ontology: " + o.getAxiomCount());

        N2OOntologyLoader ontologyImporter = new N2OOntologyLoader();
        ontologyImporter.importOntology(o, importResults);

        logger.log("Loading in Database: " + importdir.getAbsolutePath());

        N2OCSVWriter csvWriter = new N2OCSVWriter(ontologyImporter.getImportManager(), ontologyImporter.getRelationTypeCounter(), importdir);
        csvWriter.exportOntologyToCSV();
        return csvWriter;
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

    private IRI getOntologyIRI(@Name("url") String url, File importdir) {
        IRI iri;
        if (url.startsWith("file://")) {
            File ontology = new File(importdir, url.replaceAll("file://", ""));
            iri = IRI.create(ontology.toURI());
        } else {
            iri = IRI.create(url);
        }
        return iri;
    }

    private void deleteCSVFilesInImportsDir(File importdir) {
        if (importdir.exists()) {
            for (File f : FileUtils.listFiles(importdir,null,false)) {
                if (f.getName().startsWith("nodes_") || f.getName().startsWith("relationship_")) {
                    FileUtils.deleteQuietly(f);
                }
            }
        }
    }


    public static void main(String[] args) {
        String url = args[0];
        String config = args[1];
        File importdir = new File(args[2]);

        N2OImportService importService = new N2OImportService();
        N2OImportResult importResults = new N2OImportResult();
        try {
            importService.prepareConfig(config, importdir);
            logger.log("Converting OWL ontology to CSV..");
            N2OCSVWriter csvWriter = importService.prepareCSVFilesForImport(url, importdir, importResults);
            csvWriter.exportN2OImportConfig(new File(importdir,"csv_imports_config.yaml"));

        } catch (IOException | N2OException | InterruptedException | ExecutionException | OWLOntologyCreationException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }
}

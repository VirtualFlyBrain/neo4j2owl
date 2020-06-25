package ebi.spot.neo4j2owl.importer;

import com.google.common.collect.Iterables;
import ebi.spot.neo4j2owl.N2OLog;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.Name;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class N2OImportService {

    private GraphDatabaseService db;
    private GraphDatabaseAPI dbapi;
    private static N2OLog logger = N2OLog.getInstance();

    public N2OImportService(GraphDatabaseService db, GraphDatabaseAPI dbapi) {
        this.db = db;
        this.dbapi = dbapi;

    }

    public N2OImportResult owl2Import(String url, String config) {
        final ExecutorService exService = Executors.newSingleThreadExecutor();

        File importdir = prepareImportDirectory();
        N2OImportResult importResults = new N2OImportResult();
        try {
            logger.log("Loading config: " + config);
            N2OConfig.getInstance().prepareConfig(url, config, importdir);

            logger.log("Preparing of Indices: " + N2OConfig.getInstance().prepareIndex());
            if (N2OConfig.getInstance().prepareIndex()) {
                prepareIndices();
            }

            //inserter = BatchInserters.inserter( inserttmp);
            logger.log("Loading Ontology");
            OWLOntology o = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(getOntologyIRI(url, importdir));
            logger.log("Size ontology: " + o.getAxiomCount());
            List<OWLOntology> chunks = chunk(o, N2OConfig.getInstance().getBatch_size());
            for (OWLOntology c : chunks) {
                N2OOntologyImporter ontologyImporter = new N2OOntologyImporter(dbapi,db);
                ontologyImporter.importOntology(exService, importdir, c, importResults);
            }
            exService.shutdown();
            try {
                logger.log("Stopping executor..");
                exService.awaitTermination(N2OConfig.getInstance().getTimeoutInMinutes(), TimeUnit.MINUTES);
                logger.log("All done..");
            } catch (InterruptedException e) {
                logger.error("Query interrupted");
            }

        } catch (Exception e) {
            logger.error("An error has occurred..");
            e.printStackTrace();
            logger.error(e.getMessage());
            importResults.setTerminationKO(e.getMessage());
        } finally {
            logger.log("done");
            //logger.error("delete CSV file IN IMPORTS DIR IS UNCOMMENTED! COMMENT!!!");
            deleteCSVFilesInImportsDir(importdir);
        }
        return importResults;
    }


    private void prepareIndices() {
        db.execute("CREATE INDEX ON :Individual(iri)");
        db.execute("CREATE INDEX ON :Class(iri)");
        db.execute("CREATE INDEX ON :ObjectProperty(iri)");
        db.execute("CREATE INDEX ON :DataProperty(iri)");
        db.execute("CREATE INDEX ON :AnnotationProperty(iri)");
        db.execute("CREATE CONSTRAINT ON (c:Individual) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:Class) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:ObjectProperty) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:DataProperty) ASSERT c.iri IS UNIQUE");
        db.execute("CREATE CONSTRAINT ON (c:AnnotationProperty) ASSERT c.iri IS UNIQUE");
    }

    private List<OWLOntology> chunk(OWLOntology o, int chunksize) {
        List<OWLOntology> chunks = new ArrayList<>();
        if (o.getAxioms(Imports.INCLUDED).size() < chunksize) {
            chunks.add(o);
        } else {
            Set<OWLAxiom> declarations = new HashSet<>(o.getAxioms(AxiomType.DECLARATION));
            Set<OWLAxiom> axioms = o.getAxioms(Imports.INCLUDED);
            axioms.removeAll(declarations);
            for (List<OWLAxiom> partition : Iterables.partition(axioms, chunksize)) {
                try {
                    chunks.add(OWLManager.createOWLOntologyManager().createOntology(new HashSet<>(partition)));
                } catch (OWLOntologyCreationException e) {
                    e.printStackTrace();
                }
            }
        }
        return chunks;
    }

    private File prepareImportDirectory() {
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
        if (!importdir.exists()) {
            logger.error(importdir.getAbsolutePath() + " does not exist! Select valid import dir.");
        } else {
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
}

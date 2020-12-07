package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OConfig;
import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class N2OImportService {

    private final static N2OLog logger = N2OLog.getInstance();

    public N2OImportService() {
    }

    public void prepareConfig(String config, File importdir) throws IOException, N2OException {
        if(config !=null) {
            logger.log("Loading config: " + config);
            N2OConfig.getInstance().prepareConfig(config, importdir);
        }
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



    private IRI getOntologyIRI(String url, File importdir) {
        IRI iri;
        if (url.startsWith("file://")) {
            File ontology = new File(importdir, url.replaceAll("file://", ""));
            iri = IRI.create(ontology.toURI());
        } else if(new File(url).isFile()) {
            iri = IRI.create(new File(url).toURI());
        }
        else {
            iri = IRI.create(url);
        }
        return iri;
    }





}

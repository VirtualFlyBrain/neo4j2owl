package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class N2OImporterRunner {
	public static void main(String[] args) {
		String url = args[0];
		String config = args[1];
		File importdir = new File(args[2]);
		Boolean enableReasoning = true;
		String annotation_iri = null;

		if (args.length > 3) {
			enableReasoning = Boolean.parseBoolean(args[3]);
			annotation_iri = args[4];
		}

		if (config.equals("none")) {
			config = null;
		}

		N2OImportService importService = new N2OImportService();
		N2OImportResult importResults = new N2OImportResult();
		try {
			importService.prepareConfig(config, importdir);
			N2OCSVWriter csvWriter = importService.prepareCSVFilesForImport(url, importdir, importResults, enableReasoning, annotation_iri);
			File cypherDir = new File(importdir, "transactions");
			if (!cypherDir.isDirectory()) {
				boolean created = cypherDir.mkdir();
				if (!created) {
					throw new IllegalStateException("Directory could not be created: " + cypherDir);
				}
			}
			csvWriter.exportN2OImportConfig(cypherDir);

		} catch (IOException | N2OException | InterruptedException | ExecutionException
				| OWLOntologyCreationException e) {
			e.printStackTrace();
			System.exit(1);
		}

	}
}

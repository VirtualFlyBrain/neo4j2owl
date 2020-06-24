package ebi.spot.neo4j2owl;

import ebi.spot.neo4j2owl.exporter.N2OExportService;
import ebi.spot.neo4j2owl.exporter.N2OReturnValue;
import ebi.spot.neo4j2owl.importer.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import java.util.stream.Stream;

/**
 * Created by Nicolas Matentzoglu for EMBL-EBI and Virtual Fly Brain. Code roughly based on jbarrasa neosemantics.
 */
public class N2OProcedure {

    @Context
    public GraphDatabaseService db;

    @Context
    public GraphDatabaseAPI dbapi;

    private static N2OLog logger = N2OLog.getInstance();


    @Procedure(mode = Mode.DBMS)
    public Stream<N2OImportResult> owl2Import(@Name("url") String url, @Name("config") String config) {
        logger.resetTimer();
        N2OImportService importService = new N2OImportService(db, dbapi);
        N2OImportResult result = importService.owl2Import(url, config);
        return Stream.of(result);
    }

    @Procedure(mode = Mode.WRITE)
    public Stream<N2OReturnValue> exportOWL() throws Exception { //@Name("file") String fileName
        logger.resetTimer();
        N2OExportService importService = new N2OExportService(db);
        N2OReturnValue result = importService.owl2Export();
        return Stream.of(result);
    }
}

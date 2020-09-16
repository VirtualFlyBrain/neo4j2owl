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

    private N2OLog log = N2OLog.getInstance();
    private final GraphDatabaseAPI dbapi;
    private final N2OImportManager manager;
    private final RelationTypeCounter relationTypeCounter;

    N2ONeoCSVLoader(GraphDatabaseAPI dbapi, N2OImportManager manager, RelationTypeCounter relationTypeCounter) {
        this.dbapi = dbapi;
        this.manager = manager;
        this.relationTypeCounter = relationTypeCounter;
    }

    void loadRelationshipsToNeoFromCSV(ExecutorService exService, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : FileUtils.listFiles(importdir,new String[] { "txt" },false)) {
            String filename = f.getName();
            if (filename.startsWith("relationship")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    log.warning("CURRENTLY RUNNING IN TESTMODE, should set to testmode: false.");
                    FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                //TODO USING PERIODIC COMMIT 1000
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        "MATCH (s:Entity { iri: cl.start}),(e:Entity { iri: cl.end})\n" +
                        "MERGE (s)-[r:" + type + "]->(e) " + uncomposedSetClauses("cl", "r", manager.getHeadersForRelationships(type));
                log.log(f);
                log.log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    try {
                        dbapi.execute(cypher);
                    } catch (QueryExecutionException e) {
                        throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE+cypher, e);
                    }
                    return N2OStatic.CYPHER_EXECUTED_SUCCESSFULLY + cypher;
                });
                log.log(cf.get());
                /*if(fn.contains("Individual")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/
            }
        }
    }

    void loadNodesToNeoFromCSV(ExecutorService exService, File importdir) throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        for (File f : FileUtils.listFiles(importdir, new String[] { "txt" }, false)) {
            String filename = f.getName();
            if (filename.startsWith("nodes_")) {
                String fn = filename;
                if (N2OConfig.getInstance().isTestmode()) {
                    fn = "/" + new File(importdir, filename).getAbsolutePath();
                    log.warning("CURRENTLY RUNNING IN TESTMODE, should set to testmode: false.");
                    FileUtils.readLines(new File(importdir, filename), "utf-8").forEach(System.out::println);
                }
                String type = filename.substring(f.getName().indexOf("_") + 1).replaceAll(".txt", "");
                String cypher = "USING PERIODIC COMMIT 5000\n" +
                        "LOAD CSV WITH HEADERS FROM \"file:/" + fn + "\" AS cl\n" +
                        // OLD VERSION: "MERGE (n:Entity { iri: cl.iri }) SET n +={"+composeSETQuery(manager.getHeadersForNodes(type),"cl.")+"} SET n :" + type;
                        "MERGE (n:Entity { iri: cl.iri }) " + uncomposedSetClauses("cl", "n", manager.getHeadersForNodes(type)) + " SET n :" + type;
                log.log(cypher);
                final Future<String> cf = exService.submit(() -> {
                    try {
                        dbapi.execute(cypher);
                    } catch (QueryExecutionException e) {
                        throw new N2OException(N2OStatic.CYPHER_FAILED_TO_EXECUTE+cypher, e);
                    }
                    return N2OStatic.CYPHER_EXECUTED_SUCCESSFULLY + cypher;
                });
                log.log(cf.get());
                /*if(fn.contains("Class")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                }
                else if(fn.contains("Indivi")) {
                    FileUtils.readLines(new File(fn),"utf-8").forEach(System.out::println);
                    System.exit(0);
                }*/

            }
        }
    }

    private String uncomposedSetClauses(String csvalias, String neovar, Set<String> headers) {
        StringBuilder sb = new StringBuilder();
        for (String h : headers) {
            if (N2OStatic.isN2OBuiltInProperty(h)) {
                sb.append("SET ").append(neovar).append(".").append(h).append(" = ").append(csvalias).append(".").append(h).append(" ");
            } else {
                String function = "to" + computeDatatype(h);
                //TODO somevalue = [ x in split(cl.somevalue) | colaesce(apoc.util.toBoolean(x), apoc.util.toInteger(x), apoc.util.toFloat(x), x) ]
                sb.append("SET ").append(neovar).append(".").append(h).append(" = [value IN split(").append(csvalias).append(".").append(h).append(",\"").append(N2OStatic.ANNOTATION_DELIMITER).append("\") | ").append(function).append("(trim(value))] ");
            }
        }
        return sb.toString().trim().replaceAll(",$", "");
    }

    private String computeDatatype(String h) {
        Optional<String> dt_config = N2OConfig.getInstance().slToDatatype(h);
        if(dt_config.isPresent()) {
            return dt_config.get();
        } else {
            Optional<String> dt_counter = this.relationTypeCounter.computeTypeForRelation(h);
            String explanation = this.relationTypeCounter.getExplanationForTyping(h);
            if(dt_counter.isPresent()) {
                String dt = dt_counter.get();
                log.info(h+ " relation was assigned to the '"+dt+"' datatype." );
                log.info(explanation);
                return dt;
            } else {
                log.warning("Cant decide datatype of annotation property. Ambiguous typing for "+h+": "+explanation);
            }
        }
        return "String";
    }
}

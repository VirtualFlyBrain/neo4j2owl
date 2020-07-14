package ebi.spot.neo4j2owl;

import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;

import java.io.PrintWriter;
import java.io.StringWriter;

public class N2OLog {

    //private Log log;

    private N2OLog() {}
    private static N2OLog n2olog = null;
    private long start = System.currentTimeMillis();

    public static N2OLog getInstance() {
        if(n2olog==null) {
            n2olog = new N2OLog();
        }
        return n2olog;
    }

    public void resetTimer() {
        start = System.currentTimeMillis();
    }

    public void log(Object msg) {
        //log.info(msg.toString());
        System.out.println(msg + " " + getTimePassed());
    }

    public void error(Object msg) {
        //log.error(msg.toString());
        System.err.println("ERROR: "+msg + " " + getTimePassed());
        errorInterpreter(msg.toString());
    }

    private void errorInterpreter(String msg) {
        if(msg.contains("Property values can only be of primitive types or arrays thereof")) {
            System.err.println("Debugging assistant: This error often (not always!) occurs in conjunction with the dynamic type coercing function (example: properties with more than 1 datatype associated with it in the ontology, say 200 Boolean values and 10 String values, get coerced to Boolean). Search the log the the 'Typing report for' the property for more information.");
        }
    }

    public void info(Object msg) {
        //log.info(msg.toString());
        System.out.println("INFO: "+msg + " " + getTimePassed());
    }

    private String getTimePassed() {
        long time = System.currentTimeMillis() - start;
        return ((double) time / 1000.0) + " sec";
    }

    public String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public void warning(String s) {
        System.out.println("WARNING: "+s + " " + getTimePassed());
    }
}

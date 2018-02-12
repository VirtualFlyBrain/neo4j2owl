package ebi.spot.neo4j2owl;

public class ProgressInfo {
    public static final ProgressInfo EMPTY = new ProgressInfo(null);
    public final String file;

    public ProgressInfo(String file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return String.format("file = %s", file);
    }
}
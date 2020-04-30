package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.IRI;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class N2OConfig {
    private boolean strict = true;
    private boolean batch = true;
    private boolean testmode = true;
    private RELATION_TYPE relation_type = RELATION_TYPE.SL_LOSE;
    private boolean index = false;
    private long timeoutinminutes = 180;
    private Map<String, String> mapRelationshipToDatatype = new HashMap<>();
    private Map<IRI, String> mapIRIToSL = new HashMap<>();


    private static N2OConfig config = null;

    private N2OConfig() {

    }

    public static N2OConfig getInstance() {
        if (config == null) {
            config = new N2OConfig();
        }
        return config;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public void setSlToDatatype(String iri, String datatype) {
        this.mapRelationshipToDatatype.put(iri, datatype);
    }

    public String slToDatatype(String sl) {
        if (this.mapRelationshipToDatatype.containsKey(sl)) {
            return this.mapRelationshipToDatatype.get(sl);
        }
        return "String";
    }

    public void setIriToSl(IRI e, String relationship) {
        this.mapIRIToSL.put(e, relationship);
    }

    public Optional<String> iriToSl(IRI iri) {
        if (this.mapIRIToSL.containsKey(iri)) {
            return Optional.of(mapIRIToSL.get(iri));
        }
        return Optional.empty();
    }

    public boolean prepareIndex() {
        return index;
    }

    public long getTimeoutInMinutes() {
        return timeoutinminutes;
    }

    public void setPrepareIndex(Boolean index) {
        this.index = index;
    }

    public boolean isBatch() {
        return batch;
    }

    public boolean isTestmode() {
        return testmode;
    }

    public RELATION_TYPE safeLabelMode() {
        return relation_type;
    }

    public void setTestmode(boolean testmode) {
        this.testmode = testmode;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public void setSafeLabelMode(String sl_mode) {
        if(sl_mode.equals("strict")) {
            relation_type = RELATION_TYPE.SL_STRICT;
        } else if(sl_mode.equals("lose")) {
            relation_type = RELATION_TYPE.SL_LOSE;
        } else if(sl_mode.equals("qsl")) {
            relation_type = RELATION_TYPE.QSL;
        } else {
            System.out.println("Warning: "+sl_mode+ " not a valid mode, keeping default");
        }
    }
}

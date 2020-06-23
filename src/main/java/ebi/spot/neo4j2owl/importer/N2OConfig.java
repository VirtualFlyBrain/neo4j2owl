package ebi.spot.neo4j2owl.importer;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;

import java.util.*;

public class N2OConfig {
    private boolean strict = true;
    private boolean batch = true;
    private boolean testmode = true;
    private boolean oboassumption = false;
    private RELATION_TYPE relation_type = RELATION_TYPE.SL_LOSE;
    private boolean index = false;
    private long timeoutinminutes = 180;
    private int batch_size = 999000000;
    private Map<String, String> mapRelationshipToDatatype = new HashMap<>();
    private Map<IRI, String> mapIRIToSL = new HashMap<>();
    private Set<IRI> oboProperties = new HashSet<>();


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
        } else if(sl_mode.equals("loose")) {
            relation_type = RELATION_TYPE.SL_LOSE;
        } else if(sl_mode.equals("qsl")) {
            relation_type = RELATION_TYPE.QSL;
        } else {
            System.out.println("Warning: "+sl_mode+ " not a valid mode, keeping default");
        }
    }

    public void addPropertyForOBOAssumption(IRI prop) {
        this.oboProperties.add(prop);
    }

    public Set<IRI> getOboAssumptionProperties() {
        return new HashSet<>(this.oboProperties);
    }


    public boolean isOBOAssumption() {
        return this.oboassumption;
    }

    public void setOBOAssumption(boolean oboassumption) {
        this.oboassumption = oboassumption;
    }

    public boolean isStrict() {
        return this.strict;
    }

    public boolean isPropertyInOBOAssumption(OWLAnnotationProperty ap) {
        return this.getOboAssumptionProperties().contains(ap.getIRI());
    }

    public void setBatchSize(int batch_size) {
        this.batch_size = batch_size;
    }

    public int getBatch_size() {
        return this.batch_size;
    }
}

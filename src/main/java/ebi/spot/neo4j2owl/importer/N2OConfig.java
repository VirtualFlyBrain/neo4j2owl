package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import org.apache.commons.io.FileUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

class N2OConfig {
    private N2OLog log = N2OLog.getInstance();
    private boolean strict = true;
    private boolean batch = true;
    private boolean testmode = true;
    private boolean oboassumption = false;
    private LABELLING_MODE LABELLINGMODE = LABELLING_MODE.SL_LOSE;
    private boolean index = false;
    private long timeoutinminutes = 180;
    private int batch_size = 999000000;
    private Map<String, String> mapRelationshipToDatatype = new HashMap<>();
    private Map<IRI, String> mapIRIToSL = new HashMap<>();
    private Set<IRI> oboProperties = new HashSet<>();
    private Map<String,String> classExpressionNeoLabelMap = new HashMap<>(); //this is for the dynamic neo typing feature: an OWLClassExpression string that maps to a a neo node label


    private static N2OConfig config = null;

    private N2OConfig() {
    }

    static void resetConfig() {
        config = null;
    }

    static N2OConfig getInstance() {
        if (config == null) {
            config = new N2OConfig();
        }
        return config;
    }

    private void setStrict(boolean strict) {
        this.strict = strict;
    }

    private void setSlToDatatype(String iri, String datatype) {
        this.mapRelationshipToDatatype.put(iri, datatype);
    }

    String slToDatatype(String sl) {
        if (this.mapRelationshipToDatatype.containsKey(sl)) {
            return this.mapRelationshipToDatatype.get(sl);
        }
        return "String";
    }

    private void setIriToSl(IRI e, String relationship) {
        this.mapIRIToSL.put(e, relationship);
    }

    Optional<String> iriToSl(IRI iri) {
        if (this.mapIRIToSL.containsKey(iri)) {
            return Optional.of(mapIRIToSL.get(iri));
        }
        return Optional.empty();
    }

    boolean prepareIndex() {
        return index;
    }

    long getTimeoutInMinutes() {
        return timeoutinminutes;
    }

    private void setTimeoutInMinutes(long timeout) {
        this.timeoutinminutes = timeout;
    }

    private void setPrepareIndex(Boolean index) {
        this.index = index;
    }

    boolean isBatch() {
        return batch;
    }

    boolean isTestmode() {
        return testmode;
    }

    LABELLING_MODE safeLabelMode() {
        return LABELLINGMODE;
    }

    private void setTestmode(boolean testmode) {
        this.testmode = testmode;
    }

    private void setBatch(boolean batch) {
        this.batch = batch;
    }

    private void setSafeLabelMode(String sl_mode) {
        if(sl_mode.equals("strict")) {
            LABELLINGMODE = LABELLING_MODE.SL_STRICT;
        } else if(sl_mode.equals("loose")) {
            LABELLINGMODE = LABELLING_MODE.SL_LOSE;
        } else if(sl_mode.equals("qsl")) {
            LABELLINGMODE = LABELLING_MODE.QSL;
        } else {
            log.info("Warning: "+sl_mode+ " not a valid mode, keeping default");
        }
    }

    private void addPropertyForOBOAssumption(IRI prop) {
        this.oboProperties.add(prop);
    }

    private Set<IRI> getOboAssumptionProperties() {
        return new HashSet<>(this.oboProperties);
    }

    Map<String,String> getClassExpressionNeoLabelMap() {
        return this.classExpressionNeoLabelMap;
    }

    boolean isOBOAssumption() {
        return this.oboassumption;
    }

    private void setOBOAssumption(boolean oboassumption) {
        this.oboassumption = oboassumption;
    }

    boolean isStrict() {
        return this.strict;
    }

    boolean isPropertyInOBOAssumption(OWLAnnotationProperty ap) {
        return this.getOboAssumptionProperties().contains(ap.getIRI());
    }

    private void setBatchSize(int batch_size) {
        this.batch_size = batch_size;
    }

    int getBatch_size() {
        return this.batch_size;
    }

    void prepareConfig(String url, String config, File importdir) throws IOException {
        File configfile = new File(importdir, "config.yaml");
        N2OConfig n2o_config = N2OConfig.getInstance();
        if (config.startsWith("file://")) {
            File config_ = new File(importdir, url.replaceAll("file://", ""));
            FileUtils.copyFile(config_, configfile);
        } else {
            URL url_ = new URL(config);
            FileUtils.copyURLToFile(
                    url_,
                    configfile);
        }

        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(configfile);
        Map<String, Object> configs = yaml.load(inputStream);
        if (configs.containsKey("strict")) {
            if (configs.get("strict") instanceof Boolean) {
                n2o_config.setStrict((Boolean) configs.get("strict"));
            } else {
                log.log("CONFIG: strict value is not boolean");
            }
        }
        if (configs.containsKey("property_mapping")) {
            Object pm = configs.get("property_mapping");
            if (pm instanceof ArrayList) {
                for (Object pmm : ((ArrayList) pm)) {
                    if (pmm instanceof HashMap) {
                        HashMap<String, Object> pmmhm = (HashMap<String, Object>) pmm;
                        if (pmmhm.containsKey("iris")) {
                            ArrayList iris = (ArrayList) pmmhm.get("iris");
                            for (Object iri : iris) {
                                if (pmmhm.containsKey("id")) {
                                    String id = pmmhm.get("id").toString();
                                    if(N2OStatic.isN2OBuiltInProperty(id)) {
                                        throw new RuntimeException("ERROR: trying to use a built-in property ("+id+") as safe label..");
                                    }
                                    n2o_config.setIriToSl(IRI.create(iri.toString()), id);
                                    if (pmmhm.containsKey("datatype")) {
                                        String datatype = pmmhm.get("datatype").toString();
                                        n2o_config.setSlToDatatype(id, datatype);
                                    }
                                }

                            }
                        }
                    }
                }

            }
        }
        if (configs.containsKey("neo_node_labelling")) {
            Object pm = configs.get("neo_node_labelling");
            if (pm instanceof ArrayList) {
                for (Object pmm : ((ArrayList) pm)) {
                    if (pmm instanceof HashMap) {
                        HashMap<String, Object> pmmhm = (HashMap<String, Object>) pmm;
                        if (pmmhm.containsKey("classes")) {
                            ArrayList expressions = (ArrayList) pmmhm.get("classes");
                            String label = "";
                            if (pmmhm.containsKey("label")) {
                                label = pmmhm.get("label").toString();
                            }
                            for(Object o:expressions) {
                                String s = o.toString();
                                classExpressionNeoLabelMap.put(s,label);
                            }
                        }
                    }
                }
            }
        }
        if (configs.containsKey("index")) {
            if (configs.get("index") instanceof Boolean) {
                N2OConfig.getInstance().setPrepareIndex((Boolean) configs.get("index"));
            }
        }

        if (configs.containsKey("obo_assumption")) {
            if (configs.get("obo_assumption") instanceof Boolean) {
                N2OConfig.getInstance().setOBOAssumption((Boolean) configs.get("obo_assumption"));
            }
        }

        if (configs.containsKey("timeout")) {
            if (configs.get("timeout") instanceof Long) {
                N2OConfig.getInstance().setTimeoutInMinutes((Long) configs.get("timeout"));
            }
        }

        if (configs.containsKey("batch_size")) {
            if (configs.get("batch_size") instanceof Integer) {
                N2OConfig.getInstance().setBatchSize((Integer) configs.get("batch_size"));
            } else if (configs.get("batch_size") instanceof Long) {
                N2OConfig.getInstance().setBatchSize((Integer) configs.get("batch_size"));
            }
        }

        if (configs.containsKey("obo_assumption_overwrite_defaults")) {
            if (configs.get("obo_assumption_overwrite_defaults") instanceof HashMap) {
                HashMap<String, Object> pmmhm = (HashMap<String, Object>) configs.get("obo_assumption_overwrite_defaults");
                if (pmmhm.containsKey("iris")) {
                    ArrayList iris = (ArrayList) pmmhm.get("iris");
                    for (Object iri : iris) {
                        N2OConfig.getInstance().addPropertyForOBOAssumption(IRI.create(iri.toString()));
                    }
                }
                N2OConfig.getInstance().setOBOAssumption((Boolean) configs.get("obo_assumption"));
            }
        }

        if (configs.containsKey("testmode")) {
            if (configs.get("testmode") instanceof Boolean) {
                N2OConfig.getInstance().setTestmode((Boolean) configs.get("testmode"));
                //log.error("TESTMODE!!!");
                //N2OConfig.getInstance().setTestmode(true);
            }
        }

        if (configs.containsKey("batch")) {
            if (configs.get("batch") instanceof Boolean) {
                N2OConfig.getInstance().setBatch((Boolean) configs.get("batch"));
            }
        }

        if (configs.containsKey("safe_label")) {
            N2OConfig.getInstance().setSafeLabelMode(configs.get("safe_label").toString());
        }

    }
}

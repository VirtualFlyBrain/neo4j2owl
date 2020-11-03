package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import ebi.spot.neo4j2owl.exporter.N2OException;
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
    private boolean allow_entities_without_labels = true;
    private boolean batch = true;
    private boolean addPropertyLabel = true;
    private boolean testmode = false;
    private LABELLING_MODE LABELLINGMODE = LABELLING_MODE.SL_LOSE;
    private long timeoutinminutes = 180;
    private int batch_size = 999000000;
    private double relationTypeThreshold = 0.95;
    private Map<String, String> mapRelationshipToDatatype = new HashMap<>();
    private Map<String, String> customCurieMap = new HashMap<>();
    private Map<IRI, String> mapIRIToSL = new HashMap<>();
    private Set<IRI> propertiesForJSONrepresentation = new HashSet<>();
    private Set<String> preprocessingCypherQueries = new HashSet<>();
    private Map<String,Set<String>> classExpressionNeoLabelMap = new HashMap<>(); //this is for the dynamic neo typing feature: an OWLClassExpression string that maps to a a neo node label


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

    private void setAllowEntitiesWithoutLabels(boolean allow_entities_without_labels) {
        this.allow_entities_without_labels = allow_entities_without_labels;
    }

    private void setSlToDatatype(String iri, String datatype) {
        this.mapRelationshipToDatatype.put(iri, datatype);
    }

    Optional<String> slToDatatype(String sl) {
        if (this.mapRelationshipToDatatype.containsKey(sl)) {
            return Optional.of(this.mapRelationshipToDatatype.get(sl));
        }
        return Optional.empty();
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

    long getTimeoutInMinutes() {
        return timeoutinminutes;
    }

    private void setTimeoutInMinutes(long timeout) {
        this.timeoutinminutes = timeout;
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
        switch (sl_mode) {
            case "strict":
                LABELLINGMODE = LABELLING_MODE.SL_STRICT;
                break;
            case "loose":
                LABELLINGMODE = LABELLING_MODE.SL_LOSE;
                break;
            case "qsl":
                LABELLINGMODE = LABELLING_MODE.QSL;
                break;
            default:
                log.info("Warning: " + sl_mode + " not a valid mode, keeping default");
                break;
        }
    }

    private void addPropertyForJSONRepresentation(IRI prop) {
        this.propertiesForJSONrepresentation.add(prop);
    }

    private Set<IRI> getPropertiesForJSONRepresentation() {
        return new HashSet<>(this.propertiesForJSONrepresentation);
    }

    Set<String> getPreprocessingCypherQueries() {
        return new HashSet<>(this.preprocessingCypherQueries);
    }

    Map<String,Set<String>> getClassExpressionNeoLabelMap() {
        return this.classExpressionNeoLabelMap;
    }

    boolean isAllowEntitiesWithoutLabels() {
        return this.allow_entities_without_labels;
    }

    boolean isShouldPropertyBeRolledAsJSON(OWLAnnotationProperty ap) {
        return this.getPropertiesForJSONRepresentation().contains(ap.getIRI());
    }

    private void setBatchSize(int batch_size) {
        this.batch_size = batch_size;
    }

    int getBatch_size() {
        return this.batch_size;
    }

    void prepareConfig(String url, String config, File importdir) throws IOException, N2OException {
        File configfile = new File(importdir, "config.yaml");
        N2OConfig n2o_config = N2OConfig.getInstance();
        if (config.startsWith("file://")) {
            File config_ = new File(importdir, url.replaceAll("file://", ""));
            FileUtils.copyFile(config_, configfile);
        } else {
            try {
                URL url_ = new URL(config);
                FileUtils.copyURLToFile(
                        url_,
                        configfile);
            }
            catch(Exception e) {
                throw new N2OException("No valid config provided: "+config,e);
            }
        }

        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(configfile);
        Map<String, Object> configs = yaml.load(inputStream);
        if (configs.containsKey("allow_entities_without_labels")) {
            if (configs.get("allow_entities_without_labels") instanceof Boolean) {
                n2o_config.setAllowEntitiesWithoutLabels((Boolean) configs.get("allow_entities_without_labels"));
            } else {
                log.warning("CONFIG: allow_entities_without_labels value is not boolean");
            }
        }
        if (configs.containsKey("property_mapping")) {
            Object pm = configs.get("property_mapping");
            if (pm instanceof ArrayList) {
                for (Object pmm : ((ArrayList) pm)) {
                    if (pmm instanceof HashMap) {
                        @SuppressWarnings("unchecked")
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
                        @SuppressWarnings("unchecked")
                        HashMap<String, Object> pmmhm = (HashMap<String, Object>) pmm;
                        if (pmmhm.containsKey("classes")) {
                            ArrayList expressions = (ArrayList) pmmhm.get("classes");
                            String label = "";
                            if (pmmhm.containsKey("label")) {
                                label = pmmhm.get("label").toString();
                            }
                            for(Object o:expressions) {
                                String s = o.toString();
                                if(!classExpressionNeoLabelMap.containsKey(s)) {
                                    classExpressionNeoLabelMap.put(s,new HashSet<>());
                                }
                                classExpressionNeoLabelMap.get(s).add(label);
                            }
                        }
                    }
                }
            }
        }

        if (configs.containsKey("preprocessing")) {
            if (configs.get("preprocessing") instanceof ArrayList) {
                ((ArrayList) configs.get("preprocessing")).forEach(q->preprocessingCypherQueries.add(q.toString()));
            }
        }

        if (configs.containsKey("add_property_label")) {
            if (configs.get("add_property_label") instanceof Boolean) {
                N2OConfig.getInstance().setIsPropertyLabel((Boolean) configs.get("add_property_label"));
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

        if (configs.containsKey("represent_values_and_annotations_as_json")) {
            if (configs.get("represent_values_and_annotations_as_json") instanceof HashMap) {
                HashMap<String, Object> pmmhm = (HashMap<String, Object>) configs.get("represent_values_and_annotations_as_json");
                if (pmmhm.containsKey("iris")) {
                    ArrayList iris = (ArrayList) pmmhm.get("iris");
                    for (Object iri : iris) {
                        N2OConfig.getInstance().addPropertyForJSONRepresentation(IRI.create(iri.toString()));
                    }
                }
            }
        }

        if (configs.containsKey("testmode")) {
            if (configs.get("testmode") instanceof Boolean) {
                N2OConfig.getInstance().setTestmode((Boolean) configs.get("testmode"));
                //log.error("TESTMODE!!!");
                //N2OConfig.getInstance().setTestmode(true);
            }
        }

        /*if (configs.containsKey("batch")) {
            if (configs.get("batch") instanceof Boolean) {
                N2OConfig.getInstance().setBatch((Boolean) configs.get("batch"));
            }
        }*/

        if (configs.containsKey("relation_type_threshold")) {
            if (configs.get("relation_type_threshold") instanceof Double) {
                N2OConfig.getInstance().setRelationTypeThreshold((Double) configs.get("relation_type_threshold"));
            }
        }

        if (configs.containsKey("safe_label")) {
            N2OConfig.getInstance().setSafeLabelMode(configs.get("safe_label").toString());
        }

        if (configs.containsKey("curie_map")) {
            if (configs.get("curie_map") instanceof HashMap) {
                HashMap<String, String> map = (HashMap<String, String>) configs.get("curie_map");
                for(String k:map.keySet()) {
                    N2OConfig.getInstance().addCustomPrefix(k,map.get(k));
                }
            }
        }

    }

    private void setIsPropertyLabel(boolean addPropertyLabel) {
        this.addPropertyLabel = addPropertyLabel;
    }

    private void addCustomPrefix(String k, String s) {
        this.customCurieMap.put(k,s);
    }

    Map<String,String> getCustomCurieMap() {
        return this.customCurieMap;
    }

    private void setRelationTypeThreshold(double relationTypeThreshold) {
        this.relationTypeThreshold = relationTypeThreshold;
    }

    double getRelationTypeThreshold() {
        return this.relationTypeThreshold;
    }

    boolean isAddPropertyLabel() {
        return this.addPropertyLabel;
    }
}

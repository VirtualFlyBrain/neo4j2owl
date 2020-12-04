package ebi.spot.neo4j2owl.importer;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class N2OImportCSVConfig {
    private List<N2OCSVImport> importList = new ArrayList<>();

    private static final String CSV_CONFIG_ELEMENT_ROOT="import_batches";
    private static final String CSV_CONFIG_ELEMENT_CYPHER_QUERY="cql";
    private static final String CSV_CONFIG_ELEMENT_CSV_FILENAME="csv_file";


    class N2OCSVImport {
        private final String cypherQuery;
        private final String csvFilename;

        N2OCSVImport(String cypherQuery, String csvFilename) {
            this.cypherQuery = cypherQuery;
            this.csvFilename = csvFilename;
        }

        public String getCypherQuery() {
            return cypherQuery;
        }

        public String getCsvFilename() {
            return csvFilename;
        }
    }

    public N2OImportCSVConfig clone() {
        N2OImportCSVConfig cloned = new N2OImportCSVConfig();
        cloned.importList = this.importList;
        return cloned;
    }


    public void putImport(String cypherQuery, String csvFilename) {
        importList.add(new N2OCSVImport(cypherQuery,csvFilename));
    }


    void loadConfig(File configFile) throws FileNotFoundException {
        getImportList().clear();
        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream(configFile);
        Map<String, Object> configs = yaml.load(inputStream);
        if (configs.containsKey(CSV_CONFIG_ELEMENT_ROOT)) {
            Object pm = configs.get(CSV_CONFIG_ELEMENT_ROOT);
            if (pm instanceof ArrayList) {
                for (Object pmm : ((ArrayList) pm)) {
                    if (pmm instanceof HashMap) {
                        @SuppressWarnings("unchecked") HashMap<String, Object> pmmhm = (HashMap<String, Object>) pmm;
                        if (pmmhm.containsKey(CSV_CONFIG_ELEMENT_CYPHER_QUERY) && pmmhm.containsKey(CSV_CONFIG_ELEMENT_CSV_FILENAME)) {
                            String cypherQuery = pmmhm.get(CSV_CONFIG_ELEMENT_CYPHER_QUERY).toString();
                            String csvFilename = pmmhm.get(CSV_CONFIG_ELEMENT_CSV_FILENAME).toString();
                            N2OCSVImport n2OCSVImport = new N2OCSVImport(cypherQuery, csvFilename);
                            getImportList().add(n2OCSVImport);
                        }
                    }
                }
            }
        }
    }

    void saveConfig(File configFile) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        Yaml yaml = new Yaml(options);
        List<Object> queries = new ArrayList<>();
        for(N2OCSVImport n2OCSVImport: getImportList()) {
            Map<String, Object> import_ = new HashMap<>();
            import_.put(CSV_CONFIG_ELEMENT_CYPHER_QUERY,n2OCSVImport.getCypherQuery());
            import_.put(CSV_CONFIG_ELEMENT_CSV_FILENAME,n2OCSVImport.getCsvFilename());
            queries.add(import_);
        }
        Map<String, Object> data = new HashMap<>();
        data.put(CSV_CONFIG_ELEMENT_ROOT,queries);
        FileWriter writer = new FileWriter(configFile);
        yaml.dump(data, writer);
    }

    public List<N2OCSVImport> getImportList() {
        return importList;
    }

}

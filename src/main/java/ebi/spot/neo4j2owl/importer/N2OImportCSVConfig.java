package ebi.spot.neo4j2owl.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class N2OImportCSVConfig {
    private List<N2OCSVImport> importList = new ArrayList<>();

    private static final String CSV_CONFIG_ELEMENT_ROOT="statements";
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

    void saveConfig(File configDir) throws IOException {
        for(N2OCSVImport n2OCSVImport: getImportList()) {
            Map<String, Object> data = new HashMap<>();
            List<Object> queries = new ArrayList<>();
            Map<String, Object> import_ = new HashMap<>();
            String cypher = n2OCSVImport.getCypherQuery();
            import_.put("statement", cypher);
            queries.add(import_);
            data.put(CSV_CONFIG_ELEMENT_ROOT,queries);
            File transaction = new File(configDir, n2OCSVImport.csvFilename+".neo4j");
            FileUtils.write(transaction, new JSONObject(data).toString(2),"utf-8");
        }
    }

    public List<N2OCSVImport> getImportList() {
        return importList;
    }

}

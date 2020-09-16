package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.exporter.N2OException;
import org.codehaus.jackson.io.JsonStringEncoder;
import org.semanticweb.owlapi.model.OWLEntity;

import java.io.File;
import java.util.*;

class N2OCSVWriter {

    private final N2OImportManager manager;
    private final File dir;

    N2OCSVWriter(N2OImportManager manager, File dir) {
        this.manager = manager;
        this.dir = dir;
    }

    void exportOntologyToCSV() throws N2OException {
        processExportForNodes();
        processExportForRelationships();
    }

    private void processExportForRelationships() throws N2OException {
        Map<String, List<N2OOWLRelationship>> relationships = indexRelationshipsByType();
        Map<String, List<String>> dataout_rel = prepareRelationCSVsForExport(relationships);
        N2OUtils.writeToFile(getImportDir(), dataout_rel, "relationship");
    }

    private void processExportForNodes() throws N2OException {
        Map<String, List<OWLEntity>> entities = indexEntitiesByType();
        Map<String, List<String>> dataout = prepareNodeCSVsForExport(entities);
        N2OUtils.writeToFile(dir, dataout, "nodes");
    }

    private File getImportDir() {
        return this.dir;
    }

    /*
        Access to Import Manager
     */

    private N2OImportManager getManager() {
        return manager;
    }

    private Map<String, Set<String>> getNodeColumns() {
        return getManager().getNodeColumns();
    }

    private Map<String, Set<String>> getPropertyColumns() {
        return getManager().getPropertyColumns();
    }

    private Map<OWLEntity, Map<String, Object>> getNodeProperties() {
        return getManager().getNodeProperties();
    }

    private Map<OWLEntity, N2OEntity> getNodeIndex() {
        return getManager().getNodeIndex();
    }

    private Set<N2OOWLRelationship> getN2OWLRelationships() {
        return getManager().getN2OWLRelationships();
    }

    private Map<String, Object> getRelationshipProperties(N2OOWLRelationship e) {
        return getManager().getRelationshipProperties(e);
    }

    private Map<String, Object> getNodeProperties(OWLEntity e) {
        return getManager().getNodeProperties().get(e);
    }

    /**
     *
     * @return Map of relationship types (ids) and all corresponding Relationship. This will be imported one by one into neo.
     */
    private Map<String, List<N2OOWLRelationship>> indexRelationshipsByType() {
        Map<String, List<N2OOWLRelationship>> relationships = new HashMap<>();
        for (N2OOWLRelationship e : getN2OWLRelationships()) {
            String type = e.getRelationId();
            getManager().indexRelationshipColumns(e);
            if (!relationships.containsKey(type)) {
                relationships.put(type, new ArrayList<>());
            }
            relationships.get(type).add(e);
        }
        return relationships;
    }



    private Map<String, List<String>> prepareRelationCSVsForExport(Map<String, List<N2OOWLRelationship>> relationships) {
        Map<String,Set<String>> prop_columns = getPropertyColumns();
        Map<OWLEntity, N2OEntity> nodeindex = getNodeIndex();
        Map<String, List<String>> dataout = new HashMap<>();
        for (String type : prop_columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(prop_columns.get(type));
            Collections.sort(columns_sorted);
            String headerrow = constructHeaderForRelationships(columns_sorted);
            csvout.add(headerrow);
            for (N2OOWLRelationship e : relationships.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = getRelationshipProperties(e);
                writeCSVRowFromColumns(columns_sorted, sb, rec);
                sb.append(nodeindex.get(e.getStart()).getIri()).append(",");
                sb.append(nodeindex.get(e.getEnd()).getIri()).append(",");
                //sb.append(e.getRelationId());
                String s = sb.toString();
                csvout.add(s.substring(0, s.length() - 1));
            }
            dataout.put(type, csvout);
        }
        return dataout;
    }



    private void writeCSVRowFromColumns(List<String> columns_sorted, StringBuilder sb, Map<String, Object> rec) {
        for (String column : columns_sorted) {
            String value = "";
            if (rec.containsKey(column)) {
                value = csvCellValue(rec.get(column));
            }
            sb.append(value).append(",");
        }
    }

    private Map<String, List<OWLEntity>> indexEntitiesByType() {
        Map<String, List<OWLEntity>> entities = new HashMap<>();
        Map<String,Set<String>> columns = getNodeColumns();
        Map<OWLEntity, Map<String, Object>> node_properties = getNodeProperties();
        for (OWLEntity e : node_properties.keySet()) {
            //System.out.println(e.getIRI());
            Optional<N2OEntity> oe = getManager().getNode(e);
            if (oe.isPresent()) {
                N2OEntity entity = oe.get();

                for (String type : entity.getTypes()) {
                    if (!columns.containsKey(type)) {
                        columns.put(type, new HashSet<>());
                    }
                    columns.get(type).addAll(node_properties.get(e).keySet());
                    if (!entities.containsKey(type)) {
                        entities.put(type, new ArrayList<>());
                    }
                    entities.get(type).add(e);
                }
            }
        }
        //System.exit(0);
        return entities;
    }

    private String csvCellValue(Object o) {
        // {\"X\":660,\"Y\":1290,\"Z\":382}
        //System.out.println("csvCellValue()");
        String raw_value = o.toString();
        //System.out.println(raw_value);
       /*
        String json = new String(JsonStringEncoder.getInstance().quoteAsString(raw_value));

        if(!raw_value.equals(json)) {
            System.out.println("JSON DIFFERENT: " + json);
        }
        String val = json;
        */
        // see https://neo4j.com/developer/kb/space-in-import-filename-for-load-csv/
        String val = raw_value.replaceAll("\"","\"\"");
        //
        //System.out.println(val);
        return "\"" + val + "\"";
    }

    private Map<String, List<String>> prepareNodeCSVsForExport(Map<String, List<OWLEntity>> entities) {
        Map<String, List<String>> dataout = new HashMap<>();
        Map<String,Set<String>> columns = getNodeColumns();
        for (String type : columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(columns.get(type));
            Collections.sort(columns_sorted);
            csvout.add(constructHeaderForEntities(columns_sorted));

            for (OWLEntity e : entities.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = getNodeProperties(e);
                writeCSVRowFromColumns(columns_sorted, sb, rec);
                sb.append(type);
                String s = sb.toString();
                //System.out.println("KKA"+s);
                csvout.add(s);//.substring(0, s.length() - 1)
            }
            dataout.put(type, csvout);
        }
        return dataout;
    }

    private String constructHeaderForEntities(List<String> columns_sorted) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns_sorted) {
            sb.append(column).append(",");
        }
        sb.append(":LABEL");
        return sb.toString();
    }

    private String constructHeaderForRelationships(List<String> columns_sorted) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns_sorted) {
            sb.append(column).append(",");
        }
        sb.append("start,").append("end");
        //sb.append("type");
        return sb.toString();
    }

}

package ebi.spot.neo4j2owl;

import org.apache.commons.io.FileUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class N2OManager {
    private final Map<OWLEntity, N2OEntity> nodeindex = new HashMap<>();
    private final Map<OWLEntity, Map<String, Object>> node_properties = new HashMap<>();
    private final Map<N2ORelationship, Map<String, Object>> relationships = new HashMap<>();
    private final Set<String> primaryEntityPropertyKeys = new HashSet<>();
    private final IRIManager curies;
    private final OWLOntology o;
    long nextavailableid = 1;

    N2OManager(OWLOntology o, IRIManager curies) {
        this.curies = curies;
        this.o = o;
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_LABEL);
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_SAFE_LABEL);
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_QUALIFIED_SAFE_LABEL);
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_SHORT_FORM);
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_CURIE);
        primaryEntityPropertyKeys.add(OWL2NeoMapping.ATT_IRI);
    }

    public N2OEntity updateNode(OWLEntity entity, Map<String, Object> props) {
        N2OEntity e = getNode(entity);
        if (!node_properties.containsKey(e.getEntity())) {
            node_properties.put(e.getEntity(), new HashMap<>());
        }
        node_properties.get(e.getEntity()).putAll(props);

        return e;
    }

    public N2ORelationship updateRelation(OWLEntity start, OWLEntity end, String relation, Map<String, Object> props) {
        N2OEntity es = getNode(start);
        N2OEntity ee = getNode(end);

        N2ORelationship rel = new N2ORelationship(es.getEntity(), ee.getEntity(), relation);
        if (!relationships.containsKey(rel)) {
            relationships.put(rel, new HashMap<>());
        }
        relationships.get(rel).putAll(props);
        return rel;
    }

    public N2OEntity getNode(OWLEntity e) {
        if (!nodeindex.containsKey(e.toString())) {
            nodeindex.put(e, new N2OEntity(e, o, curies,nextavailableid));
            nextavailableid ++;
            //System.out.println(nodeindex.get(e));
        }
        return nodeindex.get(e);
    }

    Map<String, Set<String>> node_columns = new HashMap();
    Map<String, Set<String>> prop_columns = new HashMap();

    public void exportCSV(File dir) {
        processExportForNodes(dir);
        processExportForRelationships(dir);
    }

    private void processExportForRelationships(File dir) {
        Map<String, List<N2ORelationship>> relationships = new HashMap<>();
        indexRelationshipsByType(prop_columns, relationships);
        Map<String, List<String>> dataout_rel = prepareRelationCSVsForExport(prop_columns,relationships);
        writeToFile(dir, dataout_rel, "relationship");
    }

    private void processExportForNodes(File dir) {
        Map<String, List<OWLEntity>> entities = new HashMap<>();
        indexEntitiesByType(node_columns, entities);
        Map<String, List<String>> dataout = prepareNodeCSVsForExport(node_columns, entities);
        writeToFile(dir, dataout, "nodes");
    }

    private void writeToFile(File dir, Map<String, List<String>> dataout, String nodeclass) {
        for (String type : dataout.keySet()) {
            try {
                FileUtils.writeLines(new File(dir, nodeclass+"_" + type + ".txt"), dataout.get(type));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void indexRelationshipsByType(Map<String, Set<String>> columns, Map<String, List<N2ORelationship>> relationships) {
        for (N2ORelationship e : this.relationships.keySet()) {
            String type = e.getType();
            if(!columns.containsKey(type)) {
                columns.put(type,new HashSet<>());
            }
            columns.get(type).addAll(this.relationships.get(e).keySet());
            if(!relationships.containsKey(type)) {
                relationships.put(type,new ArrayList<>());
            }
            relationships.get(type).add(e);
        }
    }

    public OWLEntity typedEntity(IRI iri, OWLOntology o) {
        for(OWLEntity e:nodeindex.keySet()) {
            if(e.getIRI().equals(iri)) {
                return e;
            }
        }
        OWLClass c = o.getOWLOntologyManager().getOWLDataFactory().getOWLClass(iri);
        getNode(c);
        return c;
    }


    private String constructHeaderForRelationships(List<String> columns_sorted) {
        StringBuilder sb = new StringBuilder();
        for(String column:columns_sorted) {
            sb.append(markupColumn(column,"")+",");
        }
        sb.append("start,");
        sb.append("end");
        //sb.append("type");
        return sb.toString();
    }


    private Map<String, List<String>> prepareRelationCSVsForExport(Map<String, Set<String>> columns, Map<String, List<N2ORelationship>> relationships) {
        Map<String, List<String>> dataout = new HashMap<>();
        for (String type : columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(columns.get(type));
            csvout.add(constructHeaderForRelationships(columns_sorted));
            Collections.sort(columns_sorted);
            for (N2ORelationship e : relationships.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = this.relationships.get(e);
                writeCSVRowFromColumns(columns_sorted, sb, rec);
                sb.append(nodeindex.get(e.getStart()).getIri()+ ",");
                sb.append(nodeindex.get(e.getEnd()).getIri()+",");
                //sb.append(e.getType());
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
            sb.append(value + ",");
        }
    }

    private Map<String, List<String>> prepareNodeCSVsForExport(Map<String, Set<String>> columns, Map<String, List<OWLEntity>> entities) {
        Map<String, List<String>> dataout = new HashMap<>();
        for (String type : columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(columns.get(type));
            Collections.sort(columns_sorted);
            csvout.add(constructHeaderForEntities(columns_sorted,type));

            for (OWLEntity e : entities.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = node_properties.get(e);
                writeCSVRowFromColumns(columns_sorted, sb, rec);
                sb.append(type);
                String s = sb.toString();
                csvout.add(s);//.substring(0, s.length() - 1)
            }
            dataout.put(type, csvout);
        }
        return dataout;
    }

    private String constructHeaderForEntities(List<String> columns_sorted, String type) {
        StringBuilder sb = new StringBuilder();
        for(String column:columns_sorted) {
            sb.append(markupColumn(column, type)+",");
        }
        sb.append(":LABEL");
        return sb.toString();
    }

    private String markupColumn(String column, String type) {
        if(false) {
            return "iri:ID("+type+")";
        } else {
            return column;
        }
    }

    private void indexEntitiesByType(Map<String, Set<String>> columns, Map<String, List<OWLEntity>> entities) {
        for (OWLEntity e : node_properties.keySet()) {
            String type = nodeindex.get(e).getType();
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

    private String csvCellValue(Object o) {
        //TODO Properly escape
        // ATM replacing "" instead of escaping.
        String val = o.toString().replaceAll("\"","'");
        /*if(val.contains("Expressivity")) {
            //System.out.println(val);
        }*/
        return "\"" + val + "\"";
    }

    public Set<String> getHeadersForNodes(String type) {
        //System.out.println(type);
        return node_columns.get(type);
    }

    public Set<String> getHeadersForRelationships(String type) {
        return prop_columns.get(type);
    }

    public Set<String> getPrimaryEntityPropertyKeys() {
        return primaryEntityPropertyKeys;
    }
}

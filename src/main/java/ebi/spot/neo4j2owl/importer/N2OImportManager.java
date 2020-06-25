package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OStatic;
import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.io.JsonStringEncoder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;

import java.io.File;
import java.io.IOException;
import java.util.*;

class N2OImportManager {
    private final DLSyntaxObjectRenderer ren = new DLSyntaxObjectRenderer();
    private final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private final ManchesterOWLSyntaxParser parser = OWLManager.createManchesterParser();
    private final Map<String, Set<String>> prop_columns = new HashMap<>();
    private final Map<String, Set<String>> node_columns = new HashMap<>();
    private final Map<OWLEntity, N2OEntity> nodeindex = new HashMap<>();
    private final Map<String,N2OEntity> qslEntityIndex = new HashMap<>();
    private final Map<N2OEntity,String> entityQSLIndex = new HashMap<>();
    private final Map<OWLEntity, Set<String>> nodeLabels = new HashMap<>();
    private final Map<OWLEntity, Map<String, Object>> node_properties = new HashMap<>();
    private final List<N2ORelationship> rels = new ArrayList<>();
    private final Map<N2OOWLRelationship, Map<String, Object>> relationship_properties = new HashMap<>();
    private final Set<OWLEntity> entitiesWithClashingSafeLabels = new HashSet<>();
    private final IRIManager curies;
    private final OWLOntology o;
    private long nextavailableid = 1;

    N2OImportManager(OWLOntology o, IRIManager curies) {
        this.curies = curies;
        Map<String,OWLEntity> entityMap = prepareEntityMap(o);
        parser.setOWLEntityChecker(new N2OEntityChecker(entityMap));
        this.o = o;
    }

    private Map<String, OWLEntity> prepareEntityMap(OWLOntology o) {
        Map<String, OWLEntity> entityMap = new HashMap<>();
        for(OWLEntity e:o.getSignature(Imports.INCLUDED)) {
            String iri = e.getIRI().toString();
            String curie = curies.getCurie(e);
            entityMap.put(iri,e);
            entityMap.put(curie,e);
        }
        return  entityMap;
    }

    void updateNode(OWLEntity entity, Map<String, Object> props) {
        Optional<N2OEntity> oe = getNode(entity);
        if(oe.isPresent()) {
            N2OEntity e = oe.get();
            if (!node_properties.containsKey(e.getEntity())) {
                node_properties.put(e.getEntity(), new HashMap<>());
            }
            node_properties.get(e.getEntity()).putAll(props);
        }
    }

    void updateRelation(N2OEntity start, N2OEntity end, Map<String,Object> rel_data) {

        N2OOWLRelationship rel = new N2OOWLRelationship(start.getEntity(), end.getEntity(), rel_data.get("id").toString());
        if (!relationship_properties.containsKey(rel)) {
            relationship_properties.put(rel, new HashMap<>());
        }
        for(String k:rel_data.keySet()) {
            if (!k.equals("id")) {
                relationship_properties.get(rel).put(k,rel_data.get(k));
            }
        }
    }

    Optional<N2OEntity> getNode(OWLEntity e) {
        //System.out.println("|||"+e.getIRI().toString());
        if(N2OStatic.isN2OBuiltInProperty(e)) {
            return Optional.empty();
        }
        if (!nodeindex.containsKey(e)) {
            nodeindex.put(e, new N2OEntity(e, o, curies, nextavailableid));
            nextavailableid++;
            //System.out.println(nodeindex.get(e));
        }
        N2OEntity en = nodeindex.get(e);
        en.addLabels(getLabels(e));
        qslEntityIndex.put(prepareQSL(en),en);
        return Optional.of(en);
    }

    private Set<String> getLabels(OWLEntity e) {
        return nodeLabels.getOrDefault(e, Collections.emptySet());
    }



    public void exportOntologyToCSV(File dir) {
        processExportForNodes(dir);
        processExportForRelationships(dir);
    }

    private void processExportForRelationships(File dir) {
        Map<String, List<N2OOWLRelationship>> relationships = indexRelationshipsByType();
        Map<String, List<String>> dataout_rel = prepareRelationCSVsForExport(relationships);
        writeToFile(dir, dataout_rel, "relationship");
    }

    private void processExportForNodes(File dir) {
        Map<String, List<OWLEntity>> entities = indexEntitiesByType(node_columns);
        Map<String, List<String>> dataout = prepareNodeCSVsForExport(node_columns, entities);
        writeToFile(dir, dataout, "nodes");
    }

    private void writeToFile(File dir, Map<String, List<String>> dataout, String nodeclass) {
        for (String type : dataout.keySet()) {
            try {
                FileUtils.writeLines(new File(dir, nodeclass + "_" + type + ".txt"), dataout.get(type));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @return Map of relationship types (ids) and all corresponding Relationship. This will be imported one by one into neo.
     */
    private Map<String, List<N2OOWLRelationship>> indexRelationshipsByType() {
        Map<String, List<N2OOWLRelationship>> relationships = new HashMap<>();
        for (N2OOWLRelationship e : this.relationship_properties.keySet()) {
            String type = e.getRelationId();
            indexRelationshipColumns(e);
            if (!relationships.containsKey(type)) {
                relationships.put(type, new ArrayList<>());
            }
            relationships.get(type).add(e);
        }
        return relationships;
    }

    private void indexRelationshipColumns(N2OOWLRelationship e) {
        if (!this.prop_columns.containsKey(e.getRelationId())) {
            prop_columns.put(e.getRelationId(), new HashSet<>());
        }
        prop_columns.get(e.getRelationId()).addAll(this.relationship_properties.get(e).keySet());
    }

    public OWLEntity typedEntity(IRI iri, OWLOntology o) {
        for (OWLEntity e : nodeindex.keySet()) {
            if (e.getIRI().equals(iri)) {
                return e;
            }
        }
        // If its nowhere on the node index, pretend its a class, and add it to the node index.
        OWLClass c = o.getOWLOntologyManager().getOWLDataFactory().getOWLClass(iri);
        getNode(c);
        return c;
    }

    Map<String, Object> owlAnnotationsToMapOfProperties(Set<OWLAnnotation> owlans) {
        Map<String, Object> ans = new HashMap<>();
        for (OWLAnnotation a : owlans) {
            OWLAnnotationValue aval = a.annotationValue();
            String value = "UNKNOWN_ANNOTATION_VALUE";
            if (aval.asIRI().isPresent()) {
                value = aval.asIRI().or(IRI.create("UNKNOWN_ANNOTATION_IRI_VALUE")).toString();
            } else if (aval.isLiteral()) {
                value = aval.asLiteral().or(df.getOWLLiteral("UNKNOWN_ANNOTATION_LITERAL_VALUE")).getLiteral();
            }
            Optional<String> sl = getSLFromAnnotation(a);
            if(sl.isPresent()) {
                ans.put(sl.get(), value);
            }
        }
        return ans;
    }


    private String constructHeaderForRelationships(List<String> columns_sorted) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns_sorted) {
            sb.append(markupColumn(column, "") + ",");
        }
        sb.append("start,");
        sb.append("end");
        //sb.append("type");
        return sb.toString();
    }


    private Map<String, List<String>> prepareRelationCSVsForExport(Map<String, List<N2OOWLRelationship>> relationships) {
        Map<String, List<String>> dataout = new HashMap<>();
        for (String type : prop_columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(prop_columns.get(type));
            Collections.sort(columns_sorted);
            String headerrow = constructHeaderForRelationships(columns_sorted);
            csvout.add(headerrow);
            for (N2OOWLRelationship e : relationships.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = this.relationship_properties.get(e);
                writeCSVRowFromColumns(columns_sorted, sb, rec);
                sb.append(nodeindex.get(e.getStart()).getIri() + ",");
                sb.append(nodeindex.get(e.getEnd()).getIri() + ",");
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
            sb.append(value + ",");
        }
    }

    private Map<String, List<String>> prepareNodeCSVsForExport(Map<String, Set<String>> columns, Map<String, List<OWLEntity>> entities) {
        Map<String, List<String>> dataout = new HashMap<>();
        for (String type : columns.keySet()) {
            List<String> csvout = new ArrayList<>();
            List<String> columns_sorted = new ArrayList<>(columns.get(type));
            Collections.sort(columns_sorted);
            csvout.add(constructHeaderForEntities(columns_sorted, type));

            for (OWLEntity e : entities.get(type)) {
                StringBuilder sb = new StringBuilder();
                Map<String, Object> rec = node_properties.get(e);
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

    private String constructHeaderForEntities(List<String> columns_sorted, String type) {
        StringBuilder sb = new StringBuilder();
        for (String column : columns_sorted) {
            sb.append(markupColumn(column, type) + ",");
        }
        sb.append(":LABEL");
        return sb.toString();
    }

    private String markupColumn(String column, String type) {
        if (false) {
            return "iri:ID(" + type + ")";
        } else {
            return column;
        }
    }

    private Map<String, List<OWLEntity>> indexEntitiesByType(Map<String, Set<String>> columns) {
        Map<String, List<OWLEntity>> entities = new HashMap<>();
        for (OWLEntity e : node_properties.keySet()) {
            //System.out.println(e.getIRI());
            Optional<N2OEntity> oe = getNode(e);
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
        String val = new String(JsonStringEncoder.getInstance().quoteAsString(o.toString()));
        return "\"" + val + "\"";
    }

    public Set<String> getHeadersForNodes(String type) {
        Set<String> headers = node_columns.get(type);
        headers.remove("iri");
        return headers;
    }

    public Set<String> getHeadersForRelationships(String type) {
        return prop_columns.get(type);
    }


    public void addNodeLabel(OWLEntity e, String label) {
        if (!nodeLabels.containsKey(e)) {
            nodeLabels.put(e, new HashSet<>());
        }
        nodeLabels.get(e).add(label);
    }

    /*
    Checks whether the safe labels are unique in the context of the current import (for Properties).
    This is important so that not two distinct properties are mapped to the same neo edge type.
     */
    public void checkUniqueSafeLabel(LABELLING_MODE LABELLINGMODE) {
        Map<String,OWLEntity> sls = new HashMap<>();
        Set<String> non_unique = new HashSet<>();
        Set<String> non_unique_iri = new HashSet<>();
        for (OWLEntity e : nodeindex.keySet()) {
            if (nodeindex.get(e).getEntity().isOWLObjectProperty() || nodeindex.get(e).getEntity().isOWLDataProperty() || nodeindex.get(e).getEntity().isOWLAnnotationProperty()) {
                String sl = nodeindex.get(e).getSafe_label();
                if (sls.keySet().contains(sl)) {
                    non_unique.add(sl);
                    non_unique_iri.add(nodeindex.get(e).getIri());
                    non_unique_iri.add(nodeindex.get(sls.get(sl)).getIri());
                    this.entitiesWithClashingSafeLabels.add(e);
                } else {
                    sls.put(sl,e);
                }
            }
        }
        if (!non_unique.isEmpty()) {
            String nu = String.join("\n ", non_unique);
            String nuiri = String.join("\n ", non_unique_iri);
            String msg = String.format("There are %d non-unique safe labels \n (%s), pertaining to the following properties: \n %s", non_unique.size(), nu, nuiri);
            if(LABELLINGMODE.equals(LABELLING_MODE.SL_STRICT)) {
                throw new IllegalStateException(msg);
            } else {
                System.out.println("WARNING: "+msg);
            }
        }
    }

    /*
    The entity id, or role type, is picked in the following order:
    1. If set in config
    2. In case of SL_Lose, if unsafe (clash), use QSL
    3. else use whatever was configured (SL/QSL).
     */
    public String prepareQSL(N2OEntity n2OEntity) {
        if(entityQSLIndex.containsKey(n2OEntity)) {
            return entityQSLIndex.get(n2OEntity);
        }
        Optional<String> sl = N2OConfig.getInstance().iriToSl(IRI.create(n2OEntity.getIri()));
        String sls = n2OEntity.getQualified_safe_label();
        switch (N2OConfig.getInstance().safeLabelMode()) {
            case QSL:
                sls = n2OEntity.getQualified_safe_label();
                break;
            case SL_STRICT:
                if (sl.isPresent()) {
                    sls = sl.get();
                } else {
                    sls = n2OEntity.getSafe_label();
                }
                break;
            case SL_LOSE:
                if (sl.isPresent()) {
                    sls = sl.get();
                } else {
                    if (this.isUnsafeRelation(n2OEntity.getEntity())) {
                        sls = n2OEntity.getQualified_safe_label();
                    } else {
                        sls = n2OEntity.getSafe_label();
                        if(N2OStatic.isN2OBuiltInProperty(sls)) {
                            sls = n2OEntity.getQualified_safe_label();
                        }
                    }
                }
                break;
            default:
                sls = n2OEntity.getQualified_safe_label();
                break;
        }
        entityQSLIndex.put(n2OEntity,sls);
        return sls;
    }

    public Optional<N2OEntity> fromSL(String sl) {
        if(qslEntityIndex.containsKey(sl)) {
            return Optional.of(qslEntityIndex.get(sl));
        }
        return Optional.empty();
    }

    OWLClassExpression parseExpression(String manchesterSyntaxString) {
        parser.setStringToParse(manchesterSyntaxString);
        return parser.parseClassExpression();
    }

    private boolean isUnsafeRelation(OWLEntity entity) {
        return entitiesWithClashingSafeLabels.contains(entity);
    }

    void addRelation(N2ORelationship nr) {
        rels.add(nr);
    }

    Optional<String> getSLFromAnnotation(OWLAnnotation a) {
        Optional<N2OEntity> n = getNode(a.getProperty());
        if(n.isPresent()) {
            return Optional.of(prepareQSL(n.get()));
        } else {
            return Optional.empty();
        }
    }

    public Iterable<? extends N2ORelationship> getRelationships() {
        return rels;
    }
}

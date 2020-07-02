package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OStatic;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;

import java.util.*;

class N2OImportManager {
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
    //private long nextavailableid = 1;

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
            nodeindex.put(e, new N2OEntity(e, o, curies));
            //nextavailableid++;
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


    OWLEntity typedEntity(IRI iri, OWLOntology o) {
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


    Map<String, Set<Object>> extractAxiomAnnotationsIntoValueMap(Set<OWLAnnotation> axiomAnnotations, boolean includeiri) {
        Map<String, Set<Object>> axAnnos = new HashMap<>();
        for (OWLAnnotation axAnn : axiomAnnotations) {
            Optional<String> opt_sl_axiom_anno = getSLFromAnnotation(axAnn);
            if (opt_sl_axiom_anno.isPresent()) {
                String sl_axiom_anno = opt_sl_axiom_anno.get();
                boolean iri = false;
                OWLAnnotationValue avalAx = axAnn.annotationValue();

                Object value;
                if (avalAx.asIRI().isPresent()) {
                    value = avalAx.asIRI().or(IRI.create("UNKNOWN_ANNOTATION_IRI_VALUE")).toString();
                    iri=true;
                } else {
                    value = N2OUtils.extractValueFromOWLAnnotationValue(avalAx);
                }

                if (value instanceof String) {
                    value = value.toString().replaceAll(N2OStatic.ANNOTATION_DELIMITER_ESCAPED,
                            "|Content removed during Neo4J Import|");
                    //value = String.format("\"%s\"", value);
                }

                if (!axAnnos.containsKey(sl_axiom_anno)) {
                    axAnnos.put(sl_axiom_anno, new HashSet<>());
                }
                if(!(!includeiri && iri)) {
                    axAnnos.get(sl_axiom_anno).add(value);
                }
            }

        }
        return axAnnos;
    }

    Set<String> getHeadersForNodes(String type) {
        Set<String> headers = node_columns.get(type);
        headers.remove("iri");
        return headers;
    }

    Set<String> getHeadersForRelationships(String type) {
        return prop_columns.get(type);
    }


    void addNodeLabel(OWLEntity e, String label) {
        if (!nodeLabels.containsKey(e)) {
            nodeLabels.put(e, new HashSet<>());
        }
        nodeLabels.get(e).add(label);
    }

    /*
    Checks whether the safe labels are unique in the context of the current import (for Properties).
    This is important so that not two distinct properties are mapped to the same neo edge type.
     */
    void checkUniqueSafeLabel(LABELLING_MODE LABELLINGMODE) {
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
    String prepareQSL(N2OEntity n2OEntity) {
        if(entityQSLIndex.containsKey(n2OEntity)) {
            return entityQSLIndex.get(n2OEntity);
        }
        Optional<String> sl = N2OConfig.getInstance().iriToSl(IRI.create(n2OEntity.getIri()));
        String sls = computeSafeLabel(n2OEntity, sl);
        entityQSLIndex.put(n2OEntity,sls);
        return sls;
    }

    private String computeSafeLabel(N2OEntity n2OEntity, Optional<String> sl) {
        String sls;
        switch (N2OConfig.getInstance().safeLabelMode()) {
            case QSL:
                sls = n2OEntity.getQualified_safe_label();
                break;
            case SL_STRICT:
                sls = sl.orElseGet(n2OEntity::getSafe_label);
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
        return sls;
    }

    Optional<N2OEntity> fromSL(String sl) {
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
        return n.map(this::prepareQSL);
    }

    Iterable<? extends N2ORelationship> getRelationships() {
        return rels;
    }

    Set<N2OOWLRelationship> getN2OWLRelationships() {
        return relationship_properties.keySet();
    }

    Map<String, Set<String>> getNodeColumns() {
        return node_columns;
    }

    Map<String, Set<String>> getPropertyColumns() {
        return prop_columns;
    }

    Map<OWLEntity, Map<String, Object>> getNodeProperties() {
        return node_properties;
    }

    void indexRelationshipColumns(N2OOWLRelationship e) {
        if (!this.prop_columns.containsKey(e.getRelationId())) {
            prop_columns.put(e.getRelationId(), new HashSet<>());
        }
        prop_columns.get(e.getRelationId()).addAll(this.relationship_properties.get(e).keySet());
    }

    Map<OWLEntity, N2OEntity> getNodeIndex() {
        return this.nodeindex;
    }

    Map<String, Object> getRelationshipProperties(N2OOWLRelationship e) {
        return this.relationship_properties.get(e);
    }
}

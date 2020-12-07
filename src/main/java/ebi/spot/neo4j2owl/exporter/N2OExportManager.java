package ebi.spot.neo4j2owl.exporter;

import ebi.spot.neo4j2owl.N2OException;
import ebi.spot.neo4j2owl.N2OStatic;
import org.neo4j.graphdb.Label;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import java.util.*;

class N2OExportManager {

    private final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private final Map<Long, OWLEntity> mapIdEntity = new HashMap<>();
    private final Map<String, OWLEntity> qslEntity = new HashMap<>();
    private final Map<IRI, OWLEntity> iriEntity = new HashMap<>();
    private final Map<OWLEntity, Map<String, Set<Object>>> mapAnnotations = new HashMap<>();
    private final Map<OWLEntity, Set<String>> mapTypes = new HashMap<>();

    N2OExportManager() {
        prepare_built_ins();
    }

    private void prepare_built_ins() {
        qslEntity.put("label_rdfs", df.getRDFSLabel());
        qslEntity.put("comment_rdfs", df.getRDFSComment());
        qslEntity.put("seealso_rdfs", df.getRDFSSeeAlso());
        qslEntity.put("isdefinedby_rdfs", df.getRDFSIsDefinedBy());
        qslEntity.put("deprecated_owl", df.getOWLDeprecated());
        qslEntity.put("backwardscompatiblewith_owl", df.getOWLBackwardCompatibleWith());
        qslEntity.put("incompatiblewith_owl", df.getOWLIncompatibleWith());
    }

    OWLEntity getEntity(Long e) throws N2OException {
        if(!mapIdEntity.containsKey(e)) {
            throw new N2OException("Node with key "+e+" not in map! This can happen if the node does not have one of the valid base types.",new NullPointerException());
        }
        return mapIdEntity.get(e);
    }

    OWLEntity getRelationshipByQSL(String qsl) {
        return qslEntity.get(qsl);
    }


    Set<String> relationshipQSLs() {
        return qslEntity.keySet();
    }

    Collection<OWLEntity> entities() {
        return new HashSet<>(iriEntity.values());
    }

    void createEntity(NodeProxy n, String l) {
        switch (l) {
            case "Individual":
                createIndividual(n.getId(), n.getAllProperties(),n.getLabels());
                break;
            case "ObjectProperty":
                createObjectProperty(n.getId(), n.getAllProperties(),n.getLabels());
                break;
            case "DataProperty":
                createDataProperty(n.getId(), n.getAllProperties(),n.getLabels());
                break;
            case "AnnotationProperty":
                createAnnotationProperty(n.getId(), n.getAllProperties(),n.getLabels());
                break;
            case "Class":
                createClass(n.getId(), n.getAllProperties(),n.getLabels());
                break;

        }
    }

    private void createIndividual(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        OWLNamedIndividual i = df.getOWLNamedIndividual(getIRI(allProperties));
        createEntity(i, id, allProperties,labels);
    }

    /*
    This method is the key method that indexes a neo4j node as an OWL entity.
     */
    private void createEntity(OWLEntity e, long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        indexEntityAnnotations(allProperties, e);
        mapIdEntity.put(id, e);
        iriEntity.put(e.getIRI(), e);
        indexEntityQSLIfExists(e, allProperties);
        indexNeo4JNodeLabels(e, labels);
    }

    private void indexEntityQSLIfExists(OWLEntity e, Map<String, Object> allProperties) {
        if(allProperties.containsKey("qsl")) {
            qslEntity.put(allProperties.get("qsl").toString(), e);
        }
    }

    private void indexNeo4JNodeLabels(OWLEntity e, Iterable<Label> labels) {
        if(!mapTypes.containsKey(e)) {
            mapTypes.put(e,new HashSet<>());
        }
        for(Label l:labels) {
            if(!N2OStatic.isOWLPropertyTypeLabel(l.name())) {
                mapTypes.get(e).add(l.name());
            }
        }
    }

    private void indexEntityAnnotations(Map<String, Object> allProperties, OWLEntity i) {
        if (!mapAnnotations.containsKey(i)) {
            mapAnnotations.put(i, new HashMap<>());
        }
        for (String key : allProperties.keySet()) {
            if (!N2OStatic.isN2OBuiltInProperty(key)) {
                if (!mapAnnotations.get(i).containsKey(key)) {
                    mapAnnotations.get(i).put(key, new HashSet<>());
                }
                mapAnnotations.get(i).get(key).add(allProperties.get(key));
            }
        }
    }

    private void createClass(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLClass(getIRI(allProperties)), id, allProperties,labels);
    }

    private IRI getIRI(Map<String, Object> allProperties) {
        String iri = allProperties.get("iri").toString();
        if(!iri.equals(iri.trim())) {
            System.out.println("IRI contains illegal whitspace, stripping: |"+iri+"|");
        }
        return IRI.create(iri.trim());
    }

    private void createObjectProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLObjectProperty(getIRI(allProperties)), id, allProperties,labels);
    }

    private void createDataProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLDataProperty(getIRI(allProperties)), id, allProperties,labels);
    }

    private void createAnnotationProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLAnnotationProperty(getIRI(allProperties)), id, allProperties,labels);
    }

    Set<String> annotationsProperties(OWLEntity e) {
        return mapAnnotations.containsKey(e) ? mapAnnotations.get(e).keySet() : new HashSet<>();
    }

    Object annotationValues(OWLEntity e, String qsl_anno) {
        return mapAnnotations.containsKey(e) && mapAnnotations.get(e).containsKey(qsl_anno) ? mapAnnotations.get(e).get(qsl_anno) : new HashSet<>();
    }

    Set<String> nodeLabels(OWLEntity e) {
        return mapTypes.getOrDefault(e, Collections.emptySet());
    }
}

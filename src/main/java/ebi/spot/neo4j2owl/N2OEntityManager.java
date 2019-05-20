package ebi.spot.neo4j2owl;

import org.neo4j.graphdb.Label;
import org.neo4j.kernel.impl.core.NodeProxy;
import org.neo4j.values.virtual.MapValue;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;

import java.util.*;

public class N2OEntityManager {

    private final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private final Map<Long, OWLEntity> mapIdEntity = new HashMap<>();
    private final Map<String, OWLEntity> qslEntity = new HashMap<>();
    private final Map<IRI, OWLEntity> iriEntity = new HashMap<>();
    private final Map<OWLEntity, Map<String, Set<Object>>> mapAnnotations = new HashMap<>();
    private final Map<OWLEntity, Set<String>> mapTypes = new HashMap<>();
    private final Set<String> definedProperties = new HashSet<>(Arrays.asList("short_form", "curie", "iri", "sl", "qsl", "label"));



    public OWLEntity getEntity(Long e) throws N2OException {
        if(!mapIdEntity.containsKey(e)) {
            throw new N2OException("Node with key "+e+" not in map! This can happen if the node does not have one of the valid base types.",new NullPointerException());
        }
        return mapIdEntity.get(e);
    }

    public OWLEntity getEntity(IRI iri) {
        return iriEntity.get(iri);
    }

    public OWLEntity getRelationshipByQSL(String qsl) {
        return qslEntity.get(qsl);
    }

    public Set<IRI> entityKeys() {
        return iriEntity.keySet();
    }

    public Set<String> relationshipQSLs() {
        return qslEntity.keySet();
    }

    public Collection<OWLEntity> entities() {
        return qslEntity.values();
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
        OWLNamedIndividual i = df.getOWLNamedIndividual(IRI.create(allProperties.get("iri").toString()));
        createEntity(i, id, allProperties,labels);
    }

    private void createEntity(OWLEntity e, long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        mapAnnotations(allProperties, e);
        mapIdEntity.put(id, e);
        iriEntity.put(e.getIRI(), e);
        if(allProperties.containsKey("qsl")) {
            qslEntity.put(allProperties.get("qsl").toString(), e);
        }
        for(Label l:labels) {
            if(!mapTypes.containsKey(e)) {
                mapTypes.put(e,new HashSet<>());
            }
            mapTypes.get(e).add(l.name());
        }
    }

    private void mapAnnotations(Map<String, Object> allProperties, OWLEntity i) {
        if (!mapAnnotations.containsKey(i)) {
            mapAnnotations.put(i, new HashMap<>());
        }
        for (String key : allProperties.keySet()) {
            if (isAnnotationProperty(key)) {
                if (!mapAnnotations.get(i).containsKey(i)) {
                    mapAnnotations.get(i).put(key, new HashSet<>());
                }
                mapAnnotations.get(i).get(key).add(allProperties.get(key));
            }
        }
    }

    private boolean isAnnotationProperty(String key) {
        return !definedProperties.contains(key);
    }

    private void createClass(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLClass(IRI.create(allProperties.get("iri").toString())), id, allProperties,labels);
    }

    private void createObjectProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLObjectProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties,labels);
    }

    private void createDataProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLDataProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties,labels);
    }

    private void createAnnotationProperty(long id, Map<String, Object> allProperties, Iterable<Label> labels) {
        createEntity(df.getOWLAnnotationProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties,labels);
    }

    public Set<String> annotationsProperties(OWLEntity e) {
        return mapAnnotations.containsKey(e) ? mapAnnotations.get(e).keySet() : new HashSet<>();
    }

    public Object annotationValues(OWLEntity e, String qsl_anno) {
        return mapAnnotations.containsKey(e) && mapAnnotations.get(e).containsKey(qsl_anno) ? mapAnnotations.get(e).get(qsl_anno) : new HashSet<>();
    }

    public Set<String> nodeLabels(OWLEntity e) {
        if(mapTypes.containsKey(e)) {
            return mapTypes.get(e);
        } else {
            return Collections.emptySet();
        }
    }
}

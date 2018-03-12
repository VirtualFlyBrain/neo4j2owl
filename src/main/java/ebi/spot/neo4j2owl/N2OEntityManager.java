package ebi.spot.neo4j2owl;

import org.neo4j.kernel.impl.core.NodeProxy;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import sun.misc.SoftCache;

import java.util.*;

public class N2OEntityManager {

    private final OWLDataFactory df = OWLManager.getOWLDataFactory();
    private final Map<Long, OWLEntity> mapIdEntity = new HashMap<>();
    private final Map<String, OWLEntity> qslEntity = new HashMap<>();
    private final Map<OWLEntity, Map<String, Set<Object>>> mapAnnotations = new HashMap<>();
    private final Set<String> definedProperties = new HashSet<>(Arrays.asList("short_form", "curie", "iri", "sl", "qsl", "label"));



    public OWLEntity getEntity(Long e) {
        return mapIdEntity.get(e);
    }

    public OWLEntity getEntity(String qsl) {
        return qslEntity.get(qsl);
    }

    public Set<String> entitiesQsls() {
        return qslEntity.keySet();
    }

    public Collection<OWLEntity> entities() {
        return qslEntity.values();
    }

    void createEntity(NodeProxy n, String l) {
        switch (l) {
            case "Individual":
                createIndividual(n.getId(), n.getAllProperties());
                break;
            case "ObjectProperty":
                createObjectProperty(n.getId(), n.getAllProperties());
                break;
            case "DataProperty":
                createDataProperty(n.getId(), n.getAllProperties());
                break;
            case "AnnotationProperty":
                createAnnotationProperty(n.getId(), n.getAllProperties());
                break;
            case "Class":
                createClass(n.getId(), n.getAllProperties());
                break;

        }
    }

    private void createIndividual(long id, Map<String, Object> allProperties) {
        OWLNamedIndividual i = df.getOWLNamedIndividual(IRI.create(allProperties.get("iri").toString()));
        createEntity(i, id, allProperties);
    }

    private void createEntity(OWLEntity e, long id, Map<String, Object> allProperties) {
        mapAnnotations(allProperties, e);
        mapIdEntity.put(id, e);
        qslEntity.put(allProperties.get("qsl").toString(), e);
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

    private void createClass(long id, Map<String, Object> allProperties) {
        createEntity(df.getOWLClass(IRI.create(allProperties.get("iri").toString())), id, allProperties);
    }

    private void createObjectProperty(long id, Map<String, Object> allProperties) {
        createEntity(df.getOWLObjectProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties);
    }

    private void createDataProperty(long id, Map<String, Object> allProperties) {
        createEntity(df.getOWLDataProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties);
    }

    private void createAnnotationProperty(long id, Map<String, Object> allProperties) {
        createEntity(df.getOWLAnnotationProperty(IRI.create(allProperties.get("iri").toString())), id, allProperties);
    }

    public Set<String> annotationsProperties(OWLEntity e) {
        return mapAnnotations.containsKey(e) ? mapAnnotations.get(e).keySet() : new HashSet<>();
    }

    public Object annotationValues(OWLEntity e, String qsl_anno) {
        return mapAnnotations.containsKey(e) && mapAnnotations.get(e).containsKey(qsl_anno) ? mapAnnotations.get(e).get(qsl_anno) : new HashSet<>();
    }
}

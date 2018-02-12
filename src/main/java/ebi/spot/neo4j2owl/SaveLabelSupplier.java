package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.HashMap;
import java.util.Map;

public class SaveLabelSupplier {
    final Map<String,String> curies = new HashMap<>();

    int nsct = 1;

    SaveLabelSupplier() {
        curies.put("test1","te1");
    }

    public String getSaveLabel(OWLEntity e, OWLOntology o) {

        String es = e.getIRI().toString();
        for(String iri:curies.keySet()) {
            if(es.contains(iri)) {
                return safeString(iri,e,o);
            }
        }
        String nsiri = es.replaceAll(e.getIRI().getRemainder().or(""),"");
        curies.put(nsiri,"ns"+nsct);
        nsct++;
        return safeString(nsiri,e,o);
    }

    private String getLabel(OWLEntity e, OWLOntology o) {
        for(String l:Util.getLabels(e,o)) {
            return l;
        }
        //TODO is getShortForm save?
        return e.getIRI().getRemainder().or(e.getIRI().getShortForm());
    }

    private String safeString(String iri,OWLEntity e,OWLOntology o) {
        return curies.get(iri)+":"+getLabel(e,o);
    }
}

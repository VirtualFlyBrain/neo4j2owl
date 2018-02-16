package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.NamespaceUtil;

import java.util.HashMap;
import java.util.Map;

public class CurieManager {
    private static final Map<String,String> curies = new HashMap<>();
    PrefixManager dm = new DefaultPrefixManager();

    private String getPrefix(OWLEntity e) {
        String es = e.getIRI().toString();
        for(String iri:curies.keySet()) {
            if(es.contains(iri)) {
                return curies.get(iri);
            }
        }
        String nsiri = es.replaceAll(e.getIRI().getRemainder().or(""),"");
        for(int nsct = 0;nsct<999;nsct++) {
            boolean exist = false;
            for(String val:curies.values()) {
                if(val.equals("ns"+nsct)) {
                    exist = true;
                    break;
                }
            }
            if(!exist) {
                curies.put(nsiri,"ns"+nsct);
                break;
            }
        }
        if(!curies.containsKey(nsiri)) {
            curies.put(nsiri,"vfb");
        }
        return curies.get(nsiri);
    }


    public String generateSafeLabel(OWLEntity e, OWLOntology o) {
        String prefix = dm.getPrefixIRI(e.getIRI());
        if(prefix!=null) {
            return prefix;
        }
        return getPrefix(e)+":"+ Util.getPreferredLabel(e,o);
    }
}

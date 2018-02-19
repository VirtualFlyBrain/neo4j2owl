package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.NamespaceUtil;

import java.util.HashMap;
import java.util.Map;

public class CurieManager {
    PrefixManager dm = new DefaultPrefixManager();
    int NAMESPACECOUNTER = 0;

    CurieManager() {
        dm.setPrefix("nic:","http://www.semanticweb.org/matentzn/ontologies/2018/1/untitled-ontology-73#");
        dm.setPrefix("obo:"," http://purl.obolibrary.org/obo/");
    }

    private String getPrefix(OWLEntity e) {
        //TODO A BIT OF A HACK? WHY IS THERE NO get Curie Prefix?
        String curie = getCurie(e);
        return curie.substring(0,curie.indexOf(":"));
    }


    public String getCurie(OWLEntity e) {
        String prefix = dm.getPrefixIRI(e.getIRI());
        if(prefix!=null) {
            return prefix;
        } else {
            createPrefix(e);
            return dm.getPrefixIRI(e.getIRI());
        }
    }

    private void createPrefix(OWLEntity e) {
        if(dm.getPrefixIRI(e.getIRI())==null) {
            String nsiri = getNamespace(e);
            String prefix = "ns" + NAMESPACECOUNTER + ":";
            NAMESPACECOUNTER++;
            dm.setPrefix(prefix, nsiri);
        }
    }

    public String getSafeLabel(OWLEntity e, OWLOntology o) {
        String sl = getLabel(e,o).replaceAll("[^A-Za-z0-9]","_");
        sl = sl.replaceAll("^_+", "").replaceAll("_+$", "");
        return sl;
    }

    public String getQualifiedSafeLabel(OWLEntity e, OWLOntology o) {
        return getSafeLabel(e,o)+"_"+getPrefix(e);
    }

    public String getShortForm(OWLEntity e) {
        return e.getIRI().getShortForm();
    }


    public String getLabel(OWLEntity e, OWLOntology o) {
        for(String l:Util.getLabels(e,o)) {
            return l;
        }
        String shortform = getShortForm(e);
        if(shortform==null||shortform.isEmpty()) {
            return e.getIRI().toString();
        } else {
            return shortform;
        }
    }

    public String getNamespace(OWLEntity e) {
        return e.getIRI().toString().replaceAll(getShortForm(e),"");
    }
}

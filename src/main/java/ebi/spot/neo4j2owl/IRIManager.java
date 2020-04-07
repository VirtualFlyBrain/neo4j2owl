package ebi.spot.neo4j2owl;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import sun.security.provider.PolicyParser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class IRIManager {

    private final String OBONS = "http://purl.obolibrary.org/obo/";
    private final Pattern p = Pattern.compile("[a-zA-Z]+[_]+[0-9]+");
    private final Map<String,String> prefixNamespaceMap = new HashMap<>();
    private final Map<String,String> namespacePrefixMap = new HashMap<>();
    private int NAMESPACECOUNTER = 0;
    private boolean strict = false;

    IRIManager() {
        prefixNamespaceMap.put("nic:","http://www.semanticweb.org/matentzn/ontologies/2018/1/untitled-ontology-73#");
        prefixNamespaceMap.put("obo:"," http://purl.obolibrary.org/obo/");
        prefixNamespaceMap.put("vfb:","http://www.virtualflybrain.org/owl/");
        prefixNamespaceMap.put("fbcv:","http://purl.obolibrary.org/obo/fbcv#");
        prefixNamespaceMap.put("oio:","http://www.geneontology.org/formats/oboInOwl#");
        prefixNamespaceMap.putAll(new DefaultPrefixManager().getPrefixName2PrefixMap());
        prefixNamespaceMap.forEach((k,v)->namespacePrefixMap.put(v,k));
        //System.out.println(prefixNamespaceMap);
    }


    private String getPrefix(IRI iri) {
        String ns = getNamespace(iri);
        if(!namespacePrefixMap.containsKey(ns)) {
            if(isOBOesque(iri)) {
                    String obo = ns.substring(0,ns.lastIndexOf("/")+1);
                    //System.out.println(obo);
                    String init = ns.replaceAll(obo,"");
                    //System.out.println(init);
                    String prefix = init.replaceAll("_","") + ":";
                    if(prefixNamespaceMap.containsKey(prefix)) {
                        System.err.println(prefix+" prefix for uri "+ns+" was already present for differnt uri: "+prefixNamespaceMap.get(prefix));
                        prefix = "ns" + NAMESPACECOUNTER + ":";
                        NAMESPACECOUNTER++;

                    }
                   // System.out.println(prefix);
                    namespacePrefixMap.put(ns, prefix);
                    prefixNamespaceMap.put(prefix, ns);

            } else {
                String prefix = "ns" + NAMESPACECOUNTER + ":";
                NAMESPACECOUNTER++;
                namespacePrefixMap.put(ns, prefix);
                prefixNamespaceMap.put(prefix, ns);
            }
        }
        return namespacePrefixMap.get(ns);
    }

    public String getCurie(OWLEntity e) {
        String shortform = getShortForm(e.getIRI());
        if(isOBOesque(e.getIRI())) {
            return shortform.replaceAll("_",":");
        } else {
            String prefix = getPrefix(e.getIRI());
            return prefix + shortform;
        }
    }


    public String getSafeLabel(OWLEntity e, OWLOntology o) {
        String label = getLabel(e,o).trim();
        String sl = label.chars().collect(StringBuilder::new, (sb, c) -> sb.append(encode(c)), StringBuilder::append).toString();
        return sl;
    }

    private char encode(int c) {
        char ch = (char)c;
        if(Character.isLetterOrDigit(ch)) {
            return ch;
        }   else if(ch=='_') {
            return ch;
        }   else if(ch==' ') {
            return ("_").charAt(0);
        }
        else {
         return (c+"").charAt(0);
        }
    }

    public String getQualifiedSafeLabel(OWLEntity e, OWLOntology o) {
        return getSafeLabel(e,o)+"_"+getPrefix(e.getIRI()).replaceAll(":","");
    }

    public String getShortForm(IRI e) {
       return e.getShortForm();
    }


    public String getLabel(OWLEntity e, OWLOntology o) {
        for(String l:Util.getLabels(e,o)) {
            return l;
        }
        if(strict) {
            throw new RuntimeException("No label for entity "+e.getIRI()+", which is not allowed in 'strict' mode!");
        }
        String shortform = getShortForm(e.getIRI());
        if(shortform==null||shortform.isEmpty()) {
            return e.getIRI().toString().replaceAll("[^A-Za-z0-9]","");
        } else {
            return shortform;
        }
    }

    public String getNamespace(IRI e) {
        if(isOBOesque(e)) {
            String remain = getShortForm(e);
            String id = remain.substring(remain.indexOf("_") + 1);
            return e.toString().replaceAll(id+"$","");
        } else {
            /*
            String short_form = e.getShortForm();
            String namespace = e.getNamespace();
            try {
                namespace = e.toString().replaceAll(getShortForm(e), "");
            } catch (Exception ex) {
                new IllegalArgumentException(e+" ("+short_form+") does not have a legal short form!",ex);
            }*/
            return e.getNamespace();
        }
    }


    private boolean isOBOesque(IRI e) {
        String s = e.toString();
        if(s.startsWith(OBONS)) {
            String remain = s.replaceAll(OBONS,"");
            return p.matcher(remain).matches();
        }
        return false;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }


}

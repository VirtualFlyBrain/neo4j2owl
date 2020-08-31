package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class IRIManager {

    private final Pattern p = Pattern.compile("[a-zA-Z]+[_]+[0-9]+");
    private final Map<String,String> prefixNamespaceMap = new HashMap<>();
    private final Map<String,String> namespacePrefixMap = new HashMap<>();
    private int NAMESPACECOUNTER = 0;

    IRIManager() {
        prefixNamespaceMap.put("nic","http://www.semanticweb.org/matentzn/ontologies/2018/1/untitled-ontology-73#");
        prefixNamespaceMap.put("obo"," http://purl.obolibrary.org/obo/");
        prefixNamespaceMap.put("oio","http://www.geneontology.org/formats/oboInOwl#");
        prefixNamespaceMap.put("n2oc",N2OStatic.NEO4J_UNMAPPED_PROPERTY_PREFIX_URI);
        prefixNamespaceMap.put("n2o",N2OStatic.NEO4J_BUILTIN_PROPERTY_PREFIX_URI);

        N2OConfig.getInstance().getCustomCurieMap().forEach(prefixNamespaceMap::put);
        new DefaultPrefixManager().getPrefixName2PrefixMap().forEach((k,v)->prefixNamespaceMap.put(k.replaceAll(":",""),v));

        prefixNamespaceMap.forEach((k,v)->namespacePrefixMap.put(v,k));
    }


    // The namespace is the first part of the url like http://purl.obolibrary.org/obo/RO_
    private String getUrlNamespace(IRI iri) {
        String iris = iri.toString();

        if(isOBOesque(iris)) {
            String obopre = iris.split("_")[0];
            String obons = obopre+"_";
            if(namespacePrefixMap.containsKey(obons)) {
                return obons;
            }
            String prefix = obopre.replaceAll(N2OStatic.OBONS,"");
            addPrefixNamespacePair(obons,prefix);
            return obons;
        }

        String ns = iri.getNamespace();

        if(namespacePrefixMap.containsKey(ns)) {
            return ns;
        }



        for(String urlNamespace:namespacePrefixMap.keySet()) {
            if(iris.startsWith(urlNamespace)) {
                return urlNamespace;
            }
        }
        createNewPrefixForNamespace(ns);
        return ns;
    }

    private void createNewPrefixForNamespace(String ns) {
        String prefix = "ns" + NAMESPACECOUNTER;
        NAMESPACECOUNTER++;
        addPrefixNamespacePair(ns, prefix);
    }

    private void addPrefixNamespacePair(String ns, String prefix) {
        N2OLog.getInstance().info("Adding NS: "+ns+" to "+prefix);
        namespacePrefixMap.put(ns, prefix);
        prefixNamespaceMap.put(prefix, ns);
    }

    private boolean isOBOesque(String iri) {
        if(iri.startsWith(N2OStatic.OBONS)) {
            String remain = iri.replaceAll(N2OStatic.OBONS,"");
            return p.matcher(remain).matches();
        }
        return false;
    }

    private String getPrefix(IRI iri) {
        return namespacePrefixMap.get(getUrlNamespace(iri));
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

    String getCurie(OWLEntity e) {
        String iri = e.getIRI().toString();
        String namespace = getUrlNamespace(e.getIRI());
        String prefix = namespacePrefixMap.get(namespace);
        return iri.replaceAll(namespace,prefix+":");
    }

    String getLabel(OWLEntity e, OWLOntology o) {
        Set<String> labels = N2OUtils.getLabels(e,o);
        if(!labels.isEmpty()) {
            return labels.iterator().next();
        }
        if(!N2OConfig.getInstance().isAllowEntitiesWithoutLabels() && N2OConfig.getInstance().safeLabelMode().equals(LABELLING_MODE.SL_STRICT)) {
            throw new RuntimeException("No label for entity "+e.getIRI()+", which is not allowed in 'strict' mode!");
        }
        String shortform = getShortForm(e.getIRI());
        if(shortform==null||shortform.isEmpty()) {
            return e.getIRI().toString().replaceAll("[^A-Za-z0-9]","");
        } else {
            return shortform;
        }
    }

    String getSafeLabel(OWLEntity e, OWLOntology o) {
        String label = getLabel(e,o).trim();
        return label.chars().collect(StringBuilder::new, (sb, c) -> sb.append(encode(c)), StringBuilder::append).toString();
    }

    String getQualifiedSafeLabel(OWLEntity e, OWLOntology o) {
        return getSafeLabel(e,o)+"_"+ getPrefix(e.getIRI());
    }

    String getShortForm(IRI e) {
       return e.getShortForm();
    }








}

package ebi.spot.neo4j2owl.importer;

import ebi.spot.neo4j2owl.N2OLog;
import ebi.spot.neo4j2owl.N2OStatic;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.util.*;
import java.util.regex.Pattern;

class IRIManager {

    private final Pattern p = Pattern.compile("[a-zA-Z]+[_]+[0-9]+");
    private final Map<String,String> prefixNamespaceMap = new HashMap<>();
    private final Map<String,String> namespacePrefixMap = new HashMap<>();
    private final List<String> sortedUrlNamespaces = new ArrayList<>();
    private int NAMESPACECOUNTER = 0;
    private static N2OLog logger = N2OLog.getInstance();

    IRIManager() {
        addPrefixNamespacePair(N2OStatic.NEO4J_UNMAPPED_PROPERTY_PREFIX_URI, "n2oc");
        addPrefixNamespacePair(N2OStatic.NEO4J_BUILTIN_PROPERTY_PREFIX_URI, "n2o");

        N2OConfig.getInstance().getCustomCurieMap().forEach((k,v)->addPrefixNamespacePair(v,k));
        new DefaultPrefixManager().getPrefixName2PrefixMap().forEach((k,v)->addPrefixNamespacePair(v, k.replaceAll(":","")));
    }


    // The namespace is the first part of the url like http://purl.obolibrary.org/obo/RO_
    private String getUrlNamespace(IRI iri) {
        String iris = iri.toString();

        for(String urlNamespace:sortedUrlNamespaces) {
            if(iris.startsWith(urlNamespace)) {
                return urlNamespace;
            }
        }

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

        // If the getNamespace() method returns the whole IRI, this mains a suitable remainder could not be identified.
        // In this case we go the other way, extract a shortform (which will look for the suffix after the last /), and
        // determine a "namespace" by saying: the namespace is whatever is left when you remove the shortform.
        if(ns.equals(iris)) {
            logger.info("A namespace does not have a legal namespace; we will attempt to extract one by looking at the remainder after the last slash: "+ns);
            String short_form = iri.getShortForm();

            if(short_form.isEmpty()) {
                throw new IllegalStateException("A namespace could not be correctly extracted for " + ns + ", please provide a curie map!");
            }
            ns = iris.replace(short_form,"");
        }

        if(namespacePrefixMap.containsKey(ns)) {
            return ns;
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
        sortedUrlNamespaces.add(ns);
        sortedUrlNamespaces.sort(Collections.reverseOrder());
        //sortedUrlNamespaces.forEach(System.out::println);
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
        String short_form = iri.replaceAll(namespace,"").replaceAll("[^0-9a-zA-Z_]+", "_");
        return prefix+":" +short_form;
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
            return e.getIRI().toString().replaceAll("[^A-Za-z0-9_]","_");
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
        String iri = e.toString();
        String namespace = getUrlNamespace(e);
        String prefix = namespacePrefixMap.get(namespace);
        String short_form = iri.replaceAll(namespace,"").replaceAll("[^0-9a-zA-Z_]+", "_");
        if(short_form.endsWith(prefix+"_") || Character.isDigit(short_form.charAt(0))) {
            short_form = prefix+"_"+short_form;
        }
        return short_form;
    }








}

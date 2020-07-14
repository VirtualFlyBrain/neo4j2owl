package ebi.spot.neo4j2owl.importer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class RelationTypeCounter {

    private final double threshold;

    RelationTypeCounter(double threshold) {
        this.threshold = threshold;
    }

    private Map<String,Map<String,Integer>> mapCounter = new HashMap<>();

    void increment(String rel, Object value) {
        if(!mapCounter.containsKey(rel)) {
            mapCounter.put(rel,new HashMap<>());
        }
        Map<String, Integer> ct = mapCounter.get(rel);
        String type = value.getClass().getSimpleName();
        if(!ct.containsKey(type)) {
            ct.put(type,0);
        }
        ct.put(type,ct.get(type)+1);
    }

    Optional<String> computeTypeForRelation(String rel) {
        if(mapCounter.containsKey(rel)) {
            Map<String, Integer> ctm = mapCounter.get(rel);
            int sum = ctm.values().stream().mapToInt(Integer::intValue).sum();
            if(sum>0) {
                for (String type : ctm.keySet()) {
                    Integer ct = ctm.get(type);
                    double d = ((double) ct) / sum;
                    if(d>threshold) {
                        return Optional.of(type);
                    }
                }
            }
        }
        return Optional.empty();
    }

    String getExplanationForTyping(String rel) {
        StringBuilder s = new StringBuilder("Typing report for " + rel + " (threshold " + threshold + "): ");
        if(mapCounter.containsKey(rel)) {
            Map<String, Integer> ctm = mapCounter.get(rel);
            int sum = ctm.values().stream().mapToInt(Integer::intValue).sum();
            if(sum>0) {
                if(ctm.keySet().size()!=1) {
                    s.append("STRONG WARNING: More than one type recorded, ambiguous typing! ");
                }
                for (String type : ctm.keySet()) {
                    Integer ct = ctm.get(type);
                    double d = ((double) ct) / sum;
                    s.append(type).append(": ").append(ctm.get(type)).append(" (").append(String.format("%.2f", d)).append("); ");
                }
            } else {
                s.append("No typing information recorded.");
            }
        } else {
            s.append("Relationship was not counted!");
        }
        return s.toString();
    }
}

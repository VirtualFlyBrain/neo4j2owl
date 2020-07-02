package ebi.spot.neo4j2owl.importer;

import java.util.HashMap;
import java.util.Map;

public class MapCounter {

    private Map<String,Integer> mapCounter = new HashMap<>();

    void increment(String s) {
        if(!mapCounter.containsKey(s)) {
            mapCounter.put(s,0);
        }
        mapCounter.put(s,(mapCounter.get(s)+1));
    }

    int getCount(String s) {
        if(mapCounter.containsKey(s)) {
            return mapCounter.get(s);
        }
        return 0;
    }
}

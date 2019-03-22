package org.fog.placement;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SmallTest {

    public static void main(String[] args) {

        Map<String, String> map = new LinkedHashMap<String, String>();

        map.put("a3", "aa");
        map.put("a2", "bb");
        map.put("b1", "cc");

        for (Iterator iterator = map.values().iterator(); iterator.hasNext(); ) {
            String name = (String) iterator.next();
            System.out.println(name);
        }

    }
}

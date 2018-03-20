import com.hazelcast.core.IList;
import com.hazelcast.jet.IMapJet;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.stream.DistributedCollectors;
import com.hazelcast.jet.stream.DistributedStream;

import java.util.Arrays;
import java.util.Map;

import static com.hazelcast.jet.stream.DistributedCollectors.toMap;

public class JUS {
    static void s1() {
        JetInstance jet = Jet.newJetInstance();
        try {
            //tag::s1[]
            IMapJet<String, Integer> map = jet.getMap("latitudes");
            map.put("London", 51);
            map.put("Paris", 48);
            map.put("NYC", 40);
            map.put("Sydney", -34);
            map.put("Sao Paulo", -23);
            map.put("Jakarta", -6);
            DistributedStream.fromMap(map)
                             .filter(e -> e.getValue() < 0)
                             .forEach(System.out::println);
            //end::s1[]
        } finally {
            Jet.shutdownAll();
        }
    }

    static void s2() {
        JetInstance jet = Jet.newJetInstance();
        try {
            //tag::s2[]
            DistributedStream
                    .fromSource(jet, Sources.files("books"), false)
                    .flatMap(line -> Arrays.stream(line.split("\\W+")))
                    .forEach(System.out::println);
            //end::s2[]
        } finally {
            Jet.shutdownAll();
        }
    }

    static void s3() {
        JetInstance jet = Jet.newJetInstance();
        //tag::s3[]
        IMapJet<String, String> map = jet.getMap("large_map"); // <1>
        Map<String, String> result = DistributedStream
                .fromMap(map)
                .map(e -> e.getKey() + e.getValue())
                .collect(toMap(v -> v, v -> v)); // <2>
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        //end::s4[]
    }

    static void s5() {
        //tag::s5[]
        //end::s5[]
    }

    static void s6() {
        //tag::s6[]
        //end::s6[]
    }

    static void s7() {
        //tag::s7[]
        //end::s7[]
    }

    static void s8() {
        //tag::s8[]
        //end::s8[]
    }

    static void s9() {
        //tag::s9[]
        //end::s9[]
    }

    static void s10() {
        //tag::s10[]
        //end::s10[]
    }

    static void s11() {
        //tag::s11[]
        //end::s11[]
    }

}

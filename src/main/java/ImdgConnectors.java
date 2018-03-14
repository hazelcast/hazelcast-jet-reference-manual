import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;

import java.util.Map.Entry;

public class ImdgConnectors {
    static void s1() {
        //tag::s1[]
        Pipeline p = Pipeline.create();
        BatchStage<Entry<String, Long>> stage =
                p.drawFrom(Sources.<String, Long>map("myMap"));
        stage.drainTo(Sinks.map("myMap"));
        //end::s1[]
    }

    static void s2() {
        //tag::s2[]
        Pipeline p = Pipeline.create();
        BatchStage<Entry<String, Long>> stage =
                p.drawFrom(Sources.<String, Long>cache("inCache"));
        stage.drainTo(Sinks.cache("outCache"));
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
        Pipeline pipeline = Pipeline.create();
        pipeline.drawFrom(Sources.<String, Long>map("inMap"))
                .drainTo(Sinks.mapWithMerging("outMap",
                        Entry::getKey,
                        Entry::getValue,
                        (oldValue, newValue) -> oldValue + newValue)
                );
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        Pipeline pipeline = Pipeline.create();
        pipeline.drawFrom(Sources.<String, Long>map("inMap"))
            .drainTo(Sinks.<Entry<String, Long>, String, Long>mapWithUpdating(
                "outMap", Entry::getKey,
                (oldV, item) -> (oldV != null ? oldV : 0L) + item.getValue())
            );
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

    static void s12() {
        //tag::s12[]
        //end::s12[]
    }

}

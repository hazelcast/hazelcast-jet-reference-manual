import com.hazelcast.config.SerializerConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.server.JetBootstrap;
import com.hazelcast.nio.serialization.Serializer;

import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Considerations {
    static
    //tag::s1[]
    class JetExample {
        static Job createJob(JetInstance jet) {
            JobConfig jobConfig = new JobConfig();
            jobConfig.addClass(JetExample.class);
            return jet.newJob(buildPipeline(), jobConfig);
        }

        static Pipeline buildPipeline() {
            Pipeline p = Pipeline.create();
            // ...
            return p;
        }
    }
    //end::s1[]

    static
    //tag::s2[]
    class CustomJetJob {
        public static void main(String[] args) {
            JetInstance jet = JetBootstrap.getInstance();
            jet.newJob(buildPipeline()).join();
        }

        static Pipeline buildPipeline() {
            Pipeline p = Pipeline.create();
            // ...
            return p;
        }
    }
    //end::s2[]

    static
    //tag::s3[]
    class JetJob1 {
        private String instanceVar;

        Pipeline buildPipeline() {
            Pipeline p = Pipeline.create();
            p.drawFrom(Sources.list("input"))
             .filter(item -> item.equals(instanceVar)); // <1>
            return p;
        }
    }
    //end::s3[]

    static
    //tag::s4[]
    class JetJob2 {
        private String instanceVar;
        private OutputStream fileOut; // <1>

        Pipeline buildPipeline() {
            Pipeline p = Pipeline.create();
            p.drawFrom(Sources.list("input"))
             .filter(item -> item.equals(instanceVar)); // <2>
            return p;
        }
    }
    //end::s4[]

    static
    //tag::s5[]
    class JetJob3 {
        private String instanceVar;

        Pipeline buildPipeline() {
            Pipeline p = Pipeline.create();
            String findMe = instanceVar; // <1>
            p.drawFrom(Sources.list("input"))
             .filter(item -> item.equals(findMe)); // <2>
            return p;
        }
    }
    //end::s5[]

    static void s6() {
        //tag::s6[]
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("HH:mm:ss.SSS")
                .withZone(ZoneId.systemDefault());
        Pipeline p = Pipeline.create();
        BatchStage<Long> src = p.drawFrom(Sources.list("input"));
        src.map((Long tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp))); // <1>
        //end::s6[]
    }

    static void s7() {
        BatchStage<Long> src = Pipeline.create().drawFrom(Sources.list("a"));
        //tag::s7[]
        src.map((Long tstamp) -> DateTimeFormatter.ISO_LOCAL_TIME // <1>
                .format(Instant.ofEpochMilli(tstamp).atZone(ZoneId.systemDefault())));
        //end::s7[]
    }

    static void s8() {
        //tag::s8[]
        Pipeline p = Pipeline.create();
        BatchStage<Long> src = p.drawFrom(Sources.list("input"));
        ContextFactory<DateTimeFormatter> contextFactory = ContextFactory.withCreateFn( // <1>
                x -> DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                                      .withZone(ZoneId.systemDefault()));
        src.mapUsingContext(contextFactory, // <2>
                (formatter, tstamp) -> formatter.format(Instant.ofEpochMilli(tstamp))); // <3>
        //end::s8[]
    }

    static void s9() {
        //tag::s9[]
        SerializerConfig serializerConfig = new SerializerConfig()
                .setImplementation(new MyItemSerializer())
                .setTypeClass(MyItem.class);
        JetConfig config = new JetConfig();
        config.getHazelcastConfig().getSerializationConfig()
              .addSerializerConfig(serializerConfig);
        JetInstance jet = Jet.newJetInstance(config);
        //end::s9[]
    }
    private static class MyItem {}
    private static class MyItemSerializer implements Serializer {
        @Override public int getTypeId() { return 0; }
        @Override public void destroy() { }
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

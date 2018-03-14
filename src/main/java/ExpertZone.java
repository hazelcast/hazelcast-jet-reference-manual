import com.hazelcast.jet.Util;
import com.hazelcast.jet.config.EdgeConfig;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.Partitioner;
import com.hazelcast.jet.core.Vertex;
import com.hazelcast.jet.core.processor.Processors;
import com.hazelcast.jet.core.processor.SinkProcessors;
import com.hazelcast.jet.core.processor.SourceProcessors;
import com.hazelcast.jet.core.test.TestSupport;
import com.hazelcast.jet.function.DistributedFunctions;
import com.hazelcast.jet.pipeline.Sources;

import static com.hazelcast.jet.Traversers.traverseIterable;
import static com.hazelcast.jet.Util.entry;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.core.Edge.between;
import static com.hazelcast.jet.core.Edge.from;
import static com.hazelcast.jet.core.processor.DiagnosticProcessors.peekInputP;
import static com.hazelcast.jet.core.processor.Processors.combineByKeyP;
import static com.hazelcast.jet.core.processor.Processors.mapP;
import static com.hazelcast.jet.core.processor.SinkProcessors.writeFileP;
import static com.hazelcast.jet.core.processor.SinkProcessors.writeMapP;
import static com.hazelcast.jet.core.processor.SourceProcessors.readFilesP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

class ExpertZone {

    private static DAG dag = new DAG();

    //tag::ap1[]
    class ItemAndSuccessorP extends AbstractProcessor {
        private final FlatMapper<Integer, Integer> flatMapper =
                flatMapper(i -> traverseIterable(asList(i, i + 1)));

        @Override
        protected boolean tryProcess(int ordinal, Object item) {
            return flatMapper.tryProcess((int) item);
        }
    }
    //end::ap1[]

    static void bp1() {
        //tag::bp1[]
        Vertex combine = dag.newVertex("combine",
                combineByKeyP(counting(), Util::entry)
        );
        //end::bp1[]
    }

    static void bp2() {
        //tag::bp2[]
        Vertex combine = dag.newVertex("combine",
                peekInputP(combineByKeyP(counting(), Util::entry))
        );
        //end::bp2[]
    }

    static void bp3() {
        Vertex tokenize = null;
        //tag::bp3[]
        Vertex diagnose = dag.newVertex("diagnose", writeFileP("tokenize-output"))
                             .localParallelism(1);
        dag.edge(from(tokenize, 1).to(diagnose));
        //end::bp3[]
    }

    static void bp4() {
        //tag::bp4[]
        TestSupport.verifyProcessor(mapP((String s) -> s.toUpperCase()))
                   .disableCompleteCall()             // enabled by default
                   .disableLogging()                  // enabled by default
                   .disableProgressAssertion()        // enabled by default
                   .disableSnapshots()                // enabled by default
                   .cooperativeTimeout(2000) // default is 1000ms
                   .outputChecker(TestSupport.SAME_ITEMS_ANY_ORDER)         // default is `Objects::equal`
                   .input(asList("foo", "bar"))       // default is `emptyList()`
                   .expectOutput(asList("FOO", "BAR"));
        //end::bp4[]
    }

    static void dag1() {
        //tag::dag1[]
        //tag::dag1b[]
        DAG dag = new DAG();
        //end::dag1b[]
        // 1. Create vertices
        Vertex source = dag.newVertex("source",
                SourceProcessors.readFilesP(".", UTF_8, "*", (file, line) -> line)
        );
        Vertex transform = dag.newVertex("transform", Processors.mapP(
                (String line) -> entry(line, line.length())
        ));
        Vertex sink = dag.newVertex("sink", SinkProcessors.writeMapP("sinkMap"));

        // 2. Configure local parallelism
        source.localParallelism(1);

        // 3. Create edges
        dag.edge(between(source, transform));
        dag.edge(between(transform, sink));
        //end::dag1[]
    }

    static void dag2() {
        // auto-indent doesn't work with partial includes
        //tag::dag2[]
DAG dag = new DAG();

Vertex source = dag.newVertex("source",
        SourceProcessors.readFilesP(".", UTF_8, "*", (file, line) -> line)
);
        //end::dag2[]
        Vertex v1 = dag.newVertex("v1", Processors.noopP());
        Vertex v2 = dag.newVertex("v2", Processors.noopP());

        //tag::dag2b[]
        dag.edge(between(source, v1));
        dag.edge(from(source, 1).to(v2));
        //end::dag2b[]
    }

    static void dag3() {
        Vertex source = vertex();
        Vertex tokenizer = vertex();
        Vertex accumulate = vertex();
        Vertex combine = vertex();
        Vertex sink = vertex();
        //tag::dag3[]
        dag.edge(between(source, tokenizer))
           //tag::dag3b[]
           .edge(between(tokenizer, accumulate)
                   .partitioned(DistributedFunctions.wholeItem(), Partitioner.HASH_CODE))
           .edge(between(accumulate, combine)
                   .distributed()
                   .partitioned(DistributedFunctions.entryKey()))
           //end::dag3b[]
           .edge(between(combine, sink));
        //end::dag3[]
    }

    private static Vertex vertex() {
        return new Vertex("v", Processors.noopP());
    }

    static void dag4() {
        Vertex tickerSource = vertex();
        Vertex generateTrades = vertex();
        //tag::dag4[]
        dag.edge(between(tickerSource, generateTrades)
                .setConfig(new EdgeConfig().setQueueSize(512)));
        //end::dag4[]
    }
}

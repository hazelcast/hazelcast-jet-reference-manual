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

import static com.hazelcast.jet.Traversers.traverseIterable;
import static com.hazelcast.jet.Util.entry;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.core.Edge.between;
import static com.hazelcast.jet.core.Edge.from;
import static com.hazelcast.jet.core.Partitioner.HASH_CODE;
import static com.hazelcast.jet.core.processor.DiagnosticProcessors.peekInputP;
import static com.hazelcast.jet.core.processor.Processors.combineByKeyP;
import static com.hazelcast.jet.core.processor.Processors.mapP;
import static com.hazelcast.jet.core.processor.SinkProcessors.writeFileP;
import static com.hazelcast.jet.function.DistributedFunctions.entryKey;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

class ExpertZoneBestPractices {

    private static
    //tag::s1[]
    DAG dag = new DAG();
    //end::s1[]

    static void s1() {
        //tag::s1[]
        Vertex combine = dag.newVertex("combine",
                combineByKeyP(counting(), Util::entry)
        );
        //end::s1[]
    }

    static void s2() {
        //tag::s2[]
        Vertex combine = dag.newVertex("combine",
                peekInputP(combineByKeyP(counting(), Util::entry))
        );
        //end::s2[]
    }

    static void s3() {
        Vertex tokenize = null;
        //tag::s3[]
        Vertex diagnose = dag.newVertex("diagnose", writeFileP("tokenize-output"))
                             .localParallelism(1);
        dag.edge(from(tokenize, 1).to(diagnose));
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        TestSupport.verifyProcessor(mapP((String s) -> s.toUpperCase()))
                   .disableCompleteCall()             // enabled by default
                   .disableLogging()                  // enabled by default
                   .disableProgressAssertion()        // enabled by default
                   .disableSnapshots()                // enabled by default
                   .cooperativeTimeout(2000) // default is 1000ms
                   .outputChecker(TestSupport.SAME_ITEMS_ANY_ORDER)         // default is `Objects::equal`
                   .input(asList("foo", "bar"))       // default is `emptyList()`
                   .expectOutput(asList("FOO", "BAR"));
        //end::s4[]
    }
}

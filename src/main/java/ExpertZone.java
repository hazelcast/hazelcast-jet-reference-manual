import com.hazelcast.jet.Util;
import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.Vertex;
import com.hazelcast.jet.core.processor.Processors;
import com.hazelcast.jet.core.test.TestSupport;

import static com.hazelcast.jet.Traversers.traverseIterable;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.core.Edge.from;
import static com.hazelcast.jet.core.processor.DiagnosticProcessors.peekInputP;
import static com.hazelcast.jet.core.processor.Processors.combineByKeyP;
import static com.hazelcast.jet.core.processor.SinkProcessors.writeFileP;
import static java.util.Arrays.asList;

class ExpertZone {

    private static DAG dag = new DAG();

    //tag::ItemAndSuccessorP[]
    class ItemAndSuccessorP extends AbstractProcessor {
        private final FlatMapper<Integer, Integer> flatMapper =
                flatMapper(i -> traverseIterable(asList(i, i + 1)));

        @Override
        protected boolean tryProcess(int ordinal, Object item) {
            return flatMapper.tryProcess((int) item);
        }
    }
    //end::ItemAndSuccessorP[]

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
        TestSupport.verifyProcessor(Processors.mapP((String s) -> s.toUpperCase()))
                   .disableCompleteCall()             // enabled by default
                   .disableLogging()                  // enabled by default
                   .disableProgressAssertion()        // enabled by default
                   .disableSnapshots()                // enabled by default
                   .cooperativeTimeout(2000) // default is 1000ms
                   .outputChecker(TestSupport.SAME_ITEMS_ANY_ORDER)         // default is `Objects::equal`
                   .input(asList("foo", "bar"))       // default is `emptyList()`
                   .expectOutput(asList("FOO", "BAR"));
        //end:bp4[]
    }
}

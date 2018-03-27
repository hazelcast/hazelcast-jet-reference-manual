import com.hazelcast.jet.core.AbstractProcessor;
import com.hazelcast.jet.core.DAG;

import static com.hazelcast.jet.Traversers.traverseIterable;
import static java.util.Arrays.asList;

class ExpertZoneAbstractProcessor {
    static
    //tag::s1[]
    class ItemAndSuccessorP extends AbstractProcessor {
        private final FlatMapper<Integer, Integer> flatMapper =
                flatMapper(i -> traverseIterable(asList(i, i + 1)));

        @Override
        protected boolean tryProcess(int ordinal, Object item) {
            return flatMapper.tryProcess((int) item);
        }
    }
    //end::s1[]
}

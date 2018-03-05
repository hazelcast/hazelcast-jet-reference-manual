import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;

class BuildComputation {
    public static void main(String[] args) {
// tag::simple[]
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("input"))
         .map(String::toUpperCase)
         .drainTo(Sinks.list("result"));
// end::simple[]
    }
}
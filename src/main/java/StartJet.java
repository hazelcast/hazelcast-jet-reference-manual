import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.pipeline.Pipeline;

class StartTwoInstances {
    static void s1() {
        //tag::s1[]
        JetInstance jet = Jet.newJetInstance();
        Jet.newJetInstance();
        //end::s1[]
        Pipeline pipeline = Pipeline.create();
        //tag::s3[]
        jet.newJob(pipeline).join();
        //end::s3[]
        DAG dag = pipeline.toDag();
        //tag::s4[]
        JobConfig cfg = new JobConfig();
        cfg.setName("my job");
        jet.newJob(pipeline, cfg);
        //end::s4[]
    }
}

//tag::s2[]
class UseTwoLocalJetInstances {
    public static void main(String[] args) {
        try {
            JetInstance jet = Jet.newJetInstance();
            Jet.newJetInstance();

            // work with Jet

        } finally {
            Jet.shutdownAll();
        }
    }
}
//end::s2[]

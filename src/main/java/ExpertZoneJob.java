import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.processor.Processors;

public class ExpertZoneJob {
    static void s1() {
        //tag::s1[]
        DAG dag = buildDag();
        JetInstance jet = Jet.newJetInstance(); // or Jet.newJetClient();
        jet.newJob(dag).join();
        //end::s1[]
    }

    static DAG buildDag() {
        return new DAG();
    }

    static void s2() {
        JetInstance jet = Jet.newJetInstance(); // or Jet.newJetClient();
        DAG dag = buildDag();
        //tag::s2[]
        JobConfig config = new JobConfig();
        config.addJar("the-jar-i-built.jar");
        jet.newJob(dag, config).join();
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
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
}

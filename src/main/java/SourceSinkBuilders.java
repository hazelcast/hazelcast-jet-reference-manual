import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.SourceBuilder;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;

public class SourceSinkBuilders {
    static void s1() {
        //tag::s1[]
        BatchSource<String> fileSource = SourceBuilder
            .batch("file-source", x ->
                    new BufferedReader(new FileReader("input.txt")))
            .<String>fillBufferFn((in, buf) -> {
                String line = in.readLine();
                if (line != null) {
                    buf.add(line);
                } else {
                    buf.close();
                }
            })
            .destroyFn(BufferedReader::close)
            .build();
        Pipeline p = Pipeline.create();
        BatchStage<String> srcStage = p.drawFrom(fileSource);
        //end::s1[]
    }

    static void s1a() {
        //tag::s1a[]
        BatchSource<String> fileSource = SourceBuilder
            .batch("file-source", x ->
                    new BufferedReader(new FileReader("input.txt")))
            .<String>fillBufferFn((in, buf) -> {
                for (int i = 0; i < 128; i++) {
                    String line = in.readLine();
                    if (line == null) {
                        buf.close();
                        return;
                    }
                    buf.add(line);
                }
            })
            .destroyFn(BufferedReader::close)
            .build();
        //end::s1a[]
    }

    static void s2() {
        //tag::s2[]
        StreamSource<String> socketSource = SourceBuilder
            .stream("http-source", ctx -> HttpClients.createDefault())
            .<String>fillBufferFn((httpc, buf) ->
                new BufferedReader(new InputStreamReader(
                    httpc.execute(new HttpGet("localhost:8008"))
                         .getEntity().getContent()))
                    .lines()
                    .forEach(buf::add)
                )
            .destroyFn(CloseableHttpClient::close)
            .build();
        Pipeline p = Pipeline.create();
        StreamStage<String> srcStage = p.drawFrom(socketSource);
        //end::s2[]
    }

    static void s2a() {
        //tag::s2a[]
        StreamSource<String> socketSource = SourceBuilder
            .timestampedStream("http-source", ctx -> HttpClients.createDefault())
            .<String>fillBufferFn((httpc, buf) ->
                new BufferedReader(new InputStreamReader(
                    httpc.execute(new HttpGet("localhost:8008"))
                         .getEntity().getContent()))
                    .lines()
                    .forEach(item -> {
                        long timestamp = Long.valueOf(item.substring(0, 9));
                        buf.add(item.substring(9), timestamp);
                    })
                )
            .destroyFn(CloseableHttpClient::close)
            .allowedLateness(2000)
            .build();
        //end::s2a[]
    }

    static void s3() {
        //tag::s3[]
        class SourceState {
            final CloseableHttpClient client = HttpClients.createDefault();
            final int myIndex;
            final int count;

            SourceState(Processor.Context ctx) {
                this.myIndex = ctx.globalProcessorIndex();
                this.count = ctx.totalParallelism();
            }
        }
        StreamSource<String> socketSource = SourceBuilder
            .stream("http-source", SourceState::new)
            .<String>fillBufferFn((st, buf) ->
                new BufferedReader(new InputStreamReader(
                    st.client.execute(new HttpGet(String.format(
                            "localhost:8008?index=%d&count=%d", st.myIndex, st.count)))
                         .getEntity().getContent()))
                    .lines()
                    .forEach(buf::add)
                )
            .destroyFn(st -> st.client.close())
            .distributed(2)
            .build();
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

}

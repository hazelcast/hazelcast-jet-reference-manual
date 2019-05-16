package integration;

import com.hazelcast.jet.Util;
import com.hazelcast.jet.core.Processor;
import com.hazelcast.jet.core.Processor.Context;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.SourceBuilder;
import com.hazelcast.jet.pipeline.SourceBuilder.SourceBuffer;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamSourceStage;
import com.hazelcast.util.MutableInteger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static com.hazelcast.jet.pipeline.SinkBuilder.sinkBuilder;
import static com.hazelcast.jet.pipeline.Sources.list;
import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpResponse.BodyHandlers.ofLines;

public class SourceSinkBuilders {
    static void s1() {
        //tag::s1[]
        BatchSource<String> fileSource = SourceBuilder
            .batch("file-source", x ->                               //<1>
                    new BufferedReader(new FileReader("input.txt")))
            .<String>fillBufferFn((in, buf) -> {                          //<2>
                String line = in.readLine();
                if (line != null) {
                    buf.add(line);
                } else {
                    buf.close();                                     //<3>
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
        StreamSource<String> httpSource = SourceBuilder
            .stream("http-source", ctx -> newHttpClient())
            .<String>fillBufferFn((httpc, buf) ->
                    httpc
                         .send(HttpRequest.newBuilder()
                                 .uri(URI.create("http://localhost:8008"))
                                 .build(), ofLines())
                         .body()
                         .forEach(buf::add)
                )
            .build();
        Pipeline p = Pipeline.create();
        StreamSourceStage<String> srcStage = p.drawFrom(httpSource);
        //end::s2[]
    }

    static void s2a() {
        //tag::s2a[]
        StreamSource<String> httpSource = SourceBuilder
            .timestampedStream("http-source", ctx -> newHttpClient())
                .<String>fillBufferFn((httpc, buf) ->
                        httpc
                             .send(HttpRequest.newBuilder()
                                    .uri(URI.create("http://localhost:8008"))
                                    .build(), ofLines())
                             .body()
                             .forEach(item -> {
                                 long timestamp =
                                         Long.valueOf(item.substring(0, 9));
                                 buf.add(item.substring(9), timestamp);
                             })
                )
                .build();
        //end::s2a[]
    }

    static void s3() {
        //tag::s3[]
        class SourceState {
            final int limit;
            final int step;
            int currentValue;

            SourceState(Processor.Context ctx, int limit) {
                this.limit = limit;
                currentValue = ctx.globalProcessorIndex();
                step = ctx.totalParallelism();
            }

            void addToBuffer(SourceBuffer<Integer> buffer) {
                if (currentValue < limit) {
                    buffer.add(currentValue);
                    currentValue += step;
                } else {
                    buffer.close();
                }
            }
        }

        BatchSource<Integer> sequenceSource = SourceBuilder
                .batch("seq-source", procCtx -> new SourceState(procCtx, 1_000))
                .fillBufferFn(SourceState::addToBuffer)
                .distributed(2)  //<1>
                .build();
        //end::s3[]
    }

    static void s3a() {
        //tag::s3a[]
        StreamSource<Integer> faultTolerantSource = SourceBuilder
                .stream("ft-source", procCtx -> new MutableInteger())
                .<Integer>fillBufferFn((ctx, buffer) ->
                        buffer.add(ctx.getAndInc()))
                // save the current value to the state
                .createSnapshotFn(ctx -> ctx.value)
                // restore saved value to the context
                .restoreSnapshotFn((ctx, states) -> ctx.value = states.get(0))
                .build();
        //end::s3a[]
    }

    static void s4() {
        //tag::s4[]
        Sink<Object> sink = sinkBuilder(
                "file-sink", x -> new PrintWriter(new FileWriter("output.txt")))
            .receiveFn((out, item) -> out.println(item.toString()))
            .destroyFn(PrintWriter::close)
            .build();
        Pipeline p = Pipeline.create();
        p.drawFrom(list("input"))
         .drainTo(sink);
        //end::s4[]
    }

    static void s5() {
        //tag::s5[]
        Sink<Object> sink = sinkBuilder("file-sink", x -> new StringBuilder())
            .receiveFn((buf, item) -> buf.append(item).append('\n'))
            .flushFn(buf -> {
                try (Writer out = new FileWriter("output.txt", true)) {
                    out.write(buf.toString());
                    buf.setLength(0);
                }
            })
            .build();
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

import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;

public class WhatIsDistributedComputing {
    static void s1() {
        //tag::s1[]
        Pattern delimiter = Pattern.compile("\\W+");
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<Long, String>map("book-lines"))
         .flatMap(e -> traverseArray(delimiter.split(e.getValue().toLowerCase())))
         .filter(word -> !word.isEmpty())
         .groupingKey(wholeItem())
         .aggregate(AggregateOperations.counting())
         .drainTo(Sinks.map("counts"));
        //end::s1[]
    }

    static void s2() {
        List<String> lines = new ArrayList<>();
        //tag::s2[]
        Map<String, Long> counts =
                lines.stream()
                     .flatMap(line -> Arrays.stream(line.toLowerCase().split("\\W+")))
                     .filter(word -> !word.isEmpty())
                     .collect(Collectors.groupingBy(word -> word, Collectors.counting()));
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
        List<String> lines = someExistingList();
        Map<String, Long> counts = new HashMap<>();
        for (String line : lines) {
            for (String word : line.toLowerCase().split("\\W+")) {
                if (!word.isEmpty()) {
                    counts.merge(word, 1L, (count, one) -> count + one);
                }
            }
        }
        //end::s3[]
    }

    private static List<String> someExistingList() {
        return null;
    }
}

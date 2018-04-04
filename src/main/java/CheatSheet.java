import com.hazelcast.jet.datamodel.TimestampedEntry;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.TwoBags;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StageWithGrouping;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.jet.pipeline.StreamStageWithGrouping;
import datamodel.Broker;
import datamodel.PageVisit;
import datamodel.Payment;
import datamodel.Trade;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.aggregate.AggregateOperations.toTwoBags;
import static com.hazelcast.jet.function.DistributedFunctions.entryValue;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static com.hazelcast.jet.pipeline.JoinClause.joinMapEntries;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_OLDEST;
import static com.hazelcast.jet.pipeline.Sources.list;
import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;

public class CheatSheet {
    static Pipeline p;

    static void s1() {
        //tag::s1[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        lines.map(String::toLowerCase);
        //end::s1[]
    }

    static void s2() {
        //tag::s2[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        lines.filter(string -> !string.isEmpty());
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        lines.flatMap(line -> traverseArray(line.split("\\W+")));
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        BatchStage<Trade> trades = p.drawFrom(list("trades"));
        BatchStage<Entry<Integer, Broker>> brokers = p.drawFrom(list("brokers"));
        BatchStage<Tuple2<Trade, Broker>> joined = trades.hashJoin(brokers,
                joinMapEntries(Trade::brokerId),
                Tuple2::tuple2);
        //end::s4[]
    }

    static void s5() {
        //tag::s5[]
        BatchStage<String> lines = p.drawFrom(list("lines"));
        BatchStage<Long> count = lines.aggregate(counting());
        //end::s5[]
    }

    static void s6() {
        //tag::s6[]
        BatchStage<String> words = p.drawFrom(list("words"));
        BatchStage<Entry<String, Long>> wordsAndCounts =
                words.groupingKey(wholeItem())
                     .aggregate(counting());
        //end::s6[]
    }

    static void s7() {
        //tag::s7[]
        StreamStage<Entry<Long, String>> tweetWords = p.drawFrom(
                Sources.mapJournal("tweet-words", START_FROM_OLDEST));
        StreamStage<TimestampedEntry<String, Long>> wordFreqs =
                tweetWords.addTimestamps(e -> e.getKey(), 1000)
                          .window(sliding(1000, 10))
                          .groupingKey(entryValue())
                          .aggregate(counting());
        //end::s7[]
    }

    static void s8() {
        //tag::s8[]
        StageWithGrouping<PageVisit, Integer> pageVisits =
                p.drawFrom(Sources.<PageVisit>list("pageVisit"))
                 .groupingKey(pageVisit -> pageVisit.userId());
        StageWithGrouping<Payment, Integer> payments =
                p.drawFrom(Sources.<Payment>list("payment"))
                 .groupingKey(payment -> payment.userId());
        BatchStage<Entry<Integer, TwoBags<PageVisit, Payment>>> joined =
                pageVisits.aggregate2(payments, toTwoBags());
        //end::s8[]
    }

    static void s9() {
        //tag::s9[]
        StreamStageWithGrouping<PageVisit, Integer> pageVisits =
                p.<PageVisit>drawFrom(Sources.mapJournal("pageVisits",
                        mapPutEvents(), mapEventNewValue(), START_FROM_OLDEST))
                        .addTimestamps(PageVisit::timestamp, 1000)
                        .groupingKey(PageVisit::userId);
        StreamStageWithGrouping<Payment, Integer> payments =
                p.<Payment>drawFrom(Sources.mapJournal("payments",
                        mapPutEvents(), mapEventNewValue(), START_FROM_OLDEST))
                        .addTimestamps(Payment::timestamp, 1000)
                        .groupingKey(Payment::userId);
        StreamStage<TimestampedEntry<Integer, TwoBags<PageVisit, Payment>>>
                joined = pageVisits.window(sliding(60_000, 1000))
                                   .aggregate2(payments, toTwoBags());
        //end::s9[]
    }

    static void s10() {
        //tag::s10[]
        //end::s10[]
    }

    static void s11() {
        //tag::s11[]
        //end::s11[]
    }
}

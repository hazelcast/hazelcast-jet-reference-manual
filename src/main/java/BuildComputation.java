import com.hazelcast.jet.accumulator.LongAccumulator;
import com.hazelcast.jet.aggregate.AggregateOperation;
import com.hazelcast.jet.aggregate.AggregateOperation2;
import com.hazelcast.jet.datamodel.BagsByTag;
import com.hazelcast.jet.datamodel.ItemsByTag;
import com.hazelcast.jet.datamodel.Tag;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.GroupAggregateBuilder;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StageWithGrouping;
import com.hazelcast.jet.pipeline.StreamHashJoinBuilder;
import com.hazelcast.jet.pipeline.StreamStage;
import datamodel.AddToCart;
import datamodel.Broker;
import datamodel.Delivery;
import datamodel.Market;
import datamodel.PageVisit;
import datamodel.Payment;
import datamodel.Product;
import datamodel.Trade;

import java.util.Map.Entry;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.aggregate.AggregateOperations.toBagsByTag;
import static com.hazelcast.jet.function.DistributedFunctions.entryValue;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static com.hazelcast.jet.pipeline.JoinClause.joinMapEntries;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_CURRENT;

class BuildComputation {
    static void s1() {
        //tag::s1[]
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("input"))
         .map(String::toUpperCase)
         .drainTo(Sinks.list("result"));
        //end::s1[]
    }

    static void s2() {
        //tag::s2[]
        Pipeline p = Pipeline.create();
        StreamStage<Trade> trades = p.drawFrom(Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT));
        BatchStage<Entry<Integer, Product>> products =
                p.drawFrom(Sources.<Integer, Product>map("products"));
        StreamStage<Tuple2<Trade, Product>> joined = trades.hashJoin(
                products,
                joinMapEntries(Trade::productId),
                Tuple2::tuple2
        );
        //end::s2[]
    }

    static void s3() {
        //tag::s3[]
        Pipeline p = Pipeline.create();
        BatchStage<String> src = p.drawFrom(Sources.list("src"));
        src.map(String::toUpperCase)
           .drainTo(Sinks.list("uppercase"));
        src.map(String::toLowerCase)
           .drainTo(Sinks.list("lowercase"));
        //end::s3[]
    }

    static void s4() {
        //tag::s4[]
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.list("text"))
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s4[]
    }

    static void s5() {
        //tag::s5[]
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("text"))
         .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
         .filter(word -> !word.isEmpty())
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s5[]
    }

    static void s6() {
        //tag::s6[]
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.<String>list("text"))
         .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
         .filter(word -> !word.isEmpty())
         .groupingKey(wholeItem())
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s6[]
    }

    //tag::s7[]
    static void wordCountTwoSources() {
        Pipeline p = Pipeline.create();
        BatchStage<String> src1 = p.drawFrom(Sources.list("src1"));
        BatchStage<String> src2 = p.drawFrom(Sources.list("src2"));

        StageWithGrouping<String, String> grouped1 = groupByWord(src1);
        StageWithGrouping<String, String> grouped2 = groupByWord(src2);

        grouped1.aggregate2(grouped2, counting2())
                .drainTo(Sinks.map("result"));
    }

    private static StageWithGrouping<String, String> groupByWord(
            BatchStage<String> lineSource
    ) {
        return lineSource
                .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
                .filter(word -> !word.isEmpty())
                .groupingKey(wholeItem());
    }
    //end::s7[]

    //tag::s8[]
    private static AggregateOperation2<Object, Object, LongAccumulator, Long> counting2Weighted() {
        return AggregateOperation
                .withCreate(LongAccumulator::new)
                .andAccumulate0((count, item) -> count.add(1))
                .andAccumulate1((count, item) -> count.add(10))
                .andCombine(LongAccumulator::add)
                .andFinish(LongAccumulator::get);
    }
    //end::s8[]

    static void s9() {
        //tag::s9[]
        Pipeline p = Pipeline.create();

        // Create three source streams
        StageWithGrouping<PageVisit, Integer> pageVisits =
                p.drawFrom(Sources.<PageVisit>list("pageVisit"))
                 .groupingKey(PageVisit::userId);
        StageWithGrouping<AddToCart, Integer> addToCarts =
                p.drawFrom(Sources.<AddToCart>list("addToCart"))
                 .groupingKey(AddToCart::userId);
        StageWithGrouping<Payment, Integer> payments =
                p.drawFrom(Sources.<Payment>list("payment"))
                 .groupingKey(Payment::userId);
        StageWithGrouping<Delivery, Integer> deliveries =
                p.drawFrom(Sources.<Delivery>list("delivery"))
                 .groupingKey(Delivery::userId);

        // Obtain a builder object for the co-group transform
        GroupAggregateBuilder<PageVisit, Integer> builder = pageVisits.aggregateBuilder();
        Tag<PageVisit> visitTag = builder.tag0();

        // Add the co-grouped streams to the builder.
        Tag<AddToCart> cartTag = builder.add(addToCarts);
        Tag<Payment> payTag = builder.add(payments);
        Tag<Delivery> deliveryTag = builder.add(deliveries);

        // Build the co-group transform. The aggregate operation collects all
        // the stream items inside an accumulator class called BagsByTag.
        BatchStage<Entry<Integer, BagsByTag>> coGrouped = builder.build(toBagsByTag(
                visitTag, cartTag, payTag, deliveryTag
        ));
        //end::s9[]
    }

    static void s10() {
        //tag::s10[]
        Pipeline p = Pipeline.create();

        // The stream to be enriched: trades
        StreamStage<Trade> trades = p.drawFrom(Sources.mapJournal(
                "trades", mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT));

        // The enriching streams: products and brokers
        BatchStage<Entry<Integer, Product>> prodEntries = p.drawFrom(Sources.map("products"));
        BatchStage<Entry<Integer, Broker>> brokEntries = p.drawFrom(Sources.map("brokers"));

        // Join the trade stream with the product and broker streams
        StreamStage<Tuple3<Trade, Product, Broker>> joined = trades.hashJoin2(
                prodEntries, joinMapEntries(Trade::productId),
                brokEntries, joinMapEntries(Trade::brokerId),
                Tuple3::tuple3
        );
        //end::s10[]
    }

    static void s11() {
        //tag::s11[]
        Pipeline p = Pipeline.create();

        // The stream to be enriched: trades
        StreamStage<Trade> trades = p.drawFrom(Sources.mapJournal(
                "trades", mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT));

        // The enriching streams: products, brokers and markets
        BatchStage<Entry<Integer, Product>> prodEntries = p.drawFrom(Sources.map("products"));
        BatchStage<Entry<Integer, Broker>> brokEntries = p.drawFrom(Sources.map("brokers"));
        BatchStage<Entry<Integer, Market>> marketEntries = p.drawFrom(Sources.map("markets"));

        // Obtain a hash-join builder object from the stream to be enriched
        StreamHashJoinBuilder<Trade> builder = trades.hashJoinBuilder();

        // Add enriching streams to the builder
        Tag<Product> productTag = builder.add(prodEntries, joinMapEntries(Trade::productId));
        Tag<Broker> brokerTag = builder.add(brokEntries, joinMapEntries(Trade::brokerId));
        Tag<Market> marketTag = builder.add(marketEntries, joinMapEntries(Trade::marketId));

        // Build the hash join pipeline
        StreamStage<Tuple2<Trade, ItemsByTag>> joined = builder.build(Tuple2::tuple2);
        //end::s11[]

        //tag::s12[]
        StreamStage<String> mapped = joined.map((Tuple2<Trade, ItemsByTag> tuple) -> {
            Trade trade = tuple.f0();
            ItemsByTag ibt = tuple.f1();
            Product product = ibt.get(productTag);
            Broker broker = ibt.get(brokerTag);
            Market market = ibt.get(marketTag);
            return trade + ": " + product + ", " + broker + ", " + market;
        });
        //end::s12[]
    }

    static AggregateOperation2<String, String, LongAccumulator, Long> counting2() {
        return AggregateOperation
                .withCreate(LongAccumulator::new)
                .<String>andAccumulate0((acc, s) -> acc.add(1))
                .<String>andAccumulate1((acc, s) -> acc.add(1))
                .andCombine(LongAccumulator::add)
                .andFinish(LongAccumulator::get);
    }
}


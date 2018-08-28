import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.datamodel.ItemsByTag;
import com.hazelcast.jet.datamodel.Tag;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.Tuple3;
import com.hazelcast.jet.function.DistributedComparator;
import com.hazelcast.jet.pipeline.BatchSource;
import com.hazelcast.jet.pipeline.BatchStage;
import com.hazelcast.jet.pipeline.BatchStageWithKey;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.GroupAggregateBuilder;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamHashJoinBuilder;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import datamodel.AddToCart;
import datamodel.Broker;
import datamodel.Delivery;
import datamodel.Market;
import datamodel.PageVisit;
import datamodel.Payment;
import datamodel.Person;
import datamodel.Product;
import datamodel.StockInfo;
import datamodel.Trade;
import datamodel.Tweet;
import datamodel.TweetWord;

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.jet.Traversers.traverseArray;
import static com.hazelcast.jet.Util.entry;
import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.aggregate.AggregateOperations.maxBy;
import static com.hazelcast.jet.aggregate.AggregateOperations.toList;
import static com.hazelcast.jet.function.DistributedFunctions.wholeItem;
import static com.hazelcast.jet.pipeline.JoinClause.joinMapEntries;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_CURRENT;
import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

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
                p.drawFrom(Sources.map("products"));
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

        BatchStageWithKey<String, String> grouped1 = groupByWord(src1);
        BatchStageWithKey<String, String> grouped2 = groupByWord(src2);

        BatchStage<Entry<String, Tuple2<Long, Long>>> coGrouped =
                grouped1.aggregate2(counting(), grouped2, counting());
    }

    private static BatchStageWithKey<String, String> groupByWord(
            BatchStage<String> lineSource
    ) {
        return lineSource
                .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
                .filter(word -> !word.isEmpty())
                .groupingKey(wholeItem());
    }
    //end::s7[]

    static void s7a() {
        //tag::s7a[]
        Pipeline p = Pipeline.create();

        BatchStageWithKey<PageVisit, Integer> pageVisits =
                p.drawFrom(Sources.<PageVisit>list("pageVisit"))
                 .groupingKey(PageVisit::userId);
        BatchStageWithKey<AddToCart, Integer> addToCarts =
                p.drawFrom(Sources.<AddToCart>list("addToCart"))
                 .groupingKey(AddToCart::userId);

        BatchStage<Entry<Integer, Tuple2<List<PageVisit>, List<AddToCart>>>> joined =
                pageVisits.aggregate2(toList(), addToCarts, toList(),
                        (userId, lists) -> lists.f0().isEmpty()
                                ? null : entry(userId, lists));

        joined.drainTo(Sinks.map("result"));
        //end::s7a[]
    }

    static void s9() {
        //tag::s9[]
        Pipeline p = Pipeline.create();

        //<1>
        BatchStageWithKey<PageVisit, Integer> pageVisits =
                p.drawFrom(Sources.<PageVisit>list("pageVisit"))
                 .groupingKey(PageVisit::userId);
        BatchStageWithKey<AddToCart, Integer> addToCarts =
                p.drawFrom(Sources.<AddToCart>list("addToCart"))
                 .groupingKey(AddToCart::userId);
        BatchStageWithKey<Payment, Integer> payments =
                p.drawFrom(Sources.<Payment>list("payment"))
                 .groupingKey(Payment::userId);
        BatchStageWithKey<Delivery, Integer> deliveries =
                p.drawFrom(Sources.<Delivery>list("delivery"))
                 .groupingKey(Delivery::userId);

        //<2>
        GroupAggregateBuilder<Integer, List<PageVisit>> builder =
                pageVisits.aggregateBuilder(toList());

        //<3>
        Tag<List<PageVisit>> visitTag = builder.tag0();
        Tag<List<AddToCart>> cartTag = builder.add(addToCarts, toList());
        Tag<List<Payment>> payTag = builder.add(payments, toList());
        Tag<List<Delivery>> deliveryTag = builder.add(deliveries, toList());

        //<4>
        BatchStage<Entry<Integer, ItemsByTag>> coGrouped = builder.build();
        coGrouped.map(e -> {
            ItemsByTag ibt = e.getValue();
            return String.format("User ID %d: %d visits, %d add-to-carts," +
                                 " %d payments, %d deliveries",
                    e.getKey(), ibt.get(visitTag).size(), ibt.get(cartTag).size(),
                    ibt.get(payTag).size(), ibt.get(deliveryTag).size());
        });
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
        BatchStage<Entry<Integer, Product>> prodEntries =
                p.drawFrom(Sources.map("products"));
        BatchStage<Entry<Integer, Broker>> brokEntries =
                p.drawFrom(Sources.map("brokers"));
        BatchStage<Entry<Integer, Market>> marketEntries =
                p.drawFrom(Sources.map("markets"));

        // Obtain a hash-join builder object from the stream to be enriched
        StreamHashJoinBuilder<Trade> builder = trades.hashJoinBuilder();

        // Add enriching streams to the builder
        Tag<Product> productTag = builder.add(prodEntries,
                joinMapEntries(Trade::productId));
        Tag<Broker> brokerTag = builder.add(brokEntries,
                joinMapEntries(Trade::brokerId));
        Tag<Market> marketTag = builder.add(marketEntries,
                joinMapEntries(Trade::marketId));

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

    static void s13() {
        //tag::s13[]
        Pipeline p = Pipeline.create();
        p.<String>drawFrom(Sources.mapJournal("tweets",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT))
         .flatMap(line -> traverseArray(line.toLowerCase().split("\\W+")))
         .filter(word -> !word.isEmpty())
         .addTimestamps()
         .window(sliding(MINUTES.toMillis(1), SECONDS.toMillis(1)))
         .groupingKey(wholeItem())
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s13[]
    }

    static void s14() {
        //tag::s14[]
        Pipeline p = Pipeline.create();
        p.<Tweet>drawFrom(Sources.mapJournal("tweets",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT))
         .flatMap(tweet -> traverseArray(tweet.text().toLowerCase().split("\\W+"))
                 .map(word -> new TweetWord(tweet.timestamp(), word)))
         .filter(tweetWord -> !tweetWord.word().isEmpty())
         .addTimestamps(TweetWord::timestamp, TimeUnit.SECONDS.toMillis(5))
         .window(sliding(MINUTES.toMillis(1), SECONDS.toMillis(1)))
         .groupingKey(TweetWord::word)
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s14[]
    }

    static void s15() {
        //tag::s15[]
        Pipeline p = Pipeline.create();
        p.<Tweet>drawFrom(Sources.mapJournal("tweets",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT))
         .addTimestamps(Tweet::timestamp, TimeUnit.SECONDS.toMillis(5))
         .flatMap(tweet -> traverseArray(tweet.text().toLowerCase().split("\\W+")))
         .filter(word -> !word.isEmpty())
         .window(sliding(MINUTES.toMillis(1), SECONDS.toMillis(1)))
         .groupingKey(wholeItem())
         .aggregate(counting())
         .drainTo(Sinks.list("result"));
        //end::s15[]
    }

    static void s16() {
        JetInstance jet = Jet.newJetInstance();
        //tag::s16[]
        IMap<String, StockInfo> stockMap = jet.getMap("stock-info"); //<1>
        StreamSource<Trade> tradesSource = Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT);

        Pipeline p = Pipeline.create();
        p.drawFrom(tradesSource)
         .groupingKey(Trade::ticker)
         .mapUsingIMap(stockMap, Trade::setStockInfo) //<2>
         .drainTo(Sinks.list("result"));
        //end::s16[]
    }

    static void s16a() {
        //tag::s16a[]
        ContextFactory<IMap<String, StockInfo>> ctxFac = ContextFactory
                .<IMap<String, StockInfo>>withCreateFn(x -> {
                    ClientConfig cc = new ClientConfig();
                    cc.getNearCacheConfigMap().put("stock-info", new NearCacheConfig());
                    return Jet.newJetClient(cc).getMap("stock-info");
                })
                .shareLocally()
                .nonCooperative();
        StreamSource<Trade> tradesSource = Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT);

        Pipeline p = Pipeline.create();
        p.drawFrom(tradesSource)
         .groupingKey(Trade::ticker)
         .mapUsingContext(ctxFac, (map, key, trade) -> trade.setStockInfo(map.get(key)))
         .drainTo(Sinks.list("result"));
        //end::s16a[]
    }

    static void s17() {
        //tag::s17[]
        Pipeline p = Pipeline.create();
        BatchSource<Person> personSource = Sources.list("people");
        p.drawFrom(personSource)
         .groupingKey(person -> person.getAge() / 5)
         .distinct()
         .drainTo(Sinks.list("sampleByAgeBracket"));
        //end::s17[]
    }

    static void s18() {
        //tag::s18[]
        Pipeline p = Pipeline.create();
        StreamStage<Trade> tradesNewYork = p.drawFrom(Sources.mapJournal(
                "trades-newyork", mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT));
        StreamStage<Trade> tradesTokyo = p.drawFrom(Sources.mapJournal(
                "trades-tokyo", mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT));
        StreamStage<Trade> merged = tradesNewYork.merge(tradesTokyo);
        //end::s18[]
    }

    static void s19() {
        //tag::s19[]
        Pipeline p = Pipeline.create();
        StreamSource<Trade> tradesSource = Sources.mapJournal("trades",
                mapPutEvents(), mapEventNewValue(), START_FROM_CURRENT);
        StreamStage<Trade> currLargestTrade =
                p.drawFrom(tradesSource)
                 .rollingAggregate(maxBy(DistributedComparator.comparing(Trade::worth)));
        //end::s19[]
    }
}


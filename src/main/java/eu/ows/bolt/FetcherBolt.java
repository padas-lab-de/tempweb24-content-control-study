package eu.ows.bolt;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.protocol.HttpHeaders;
import com.digitalpebble.stormcrawler.protocol.Protocol;
import com.digitalpebble.stormcrawler.protocol.ProtocolFactory;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import eu.ows.util.FetchItem;
import eu.ows.util.FetchItemQueue;
import eu.ows.util.RobotsFetchResultFactory;
import eu.ows.util.RobotsFetchResultFactory.RobotsFetchResult;
import eu.ows.util.TdmRepParser;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.Config;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetcherBolt extends BaseRichBolt {

    private static final Logger LOG = LoggerFactory.getLogger(FetcherBolt.class);

    private final int MAX_NUM_REDIRECTS = 5;
    private final int CACHE_SIZE = 100000;

    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger spinWaiting = new AtomicInteger(0);

    private OutputCollector collector;
    private FetchItemQueue fetchItemQueue;
    private Metadata fetchRobotsMd;
    private String metadataPrefix;
    private ProtocolFactory protocolFactory;
    private Cache<String, RobotsFetchResult> robotsFetchResultCache;
    private Cache<String, Metadata> tdmRepMetadataCache;
    private int maxQueueSize;
    private List<String> agentNames;
    private SimpleRobotRulesParser simpleRobotRulesParser;
    private RobotsFetchResultFactory robotsFetchResultFactory;
    private String protocolMDprefix;

    @Override
    public void prepare(
            Map<String, Object> stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;

        Config conf = new Config();
        conf.putAll(stormConf);
        protocolFactory = ProtocolFactory.getInstance(conf);
        agentNames = new ArrayList<>();
        agentNames.add("CompletelyRandomReferenceUserAgent");
        for (final String agentName : ConfUtils.loadListFromConf("fetcher.parse.agents", conf)) {
            agentNames.add(agentName.toLowerCase(Locale.ROOT));
        }
        simpleRobotRulesParser = new SimpleRobotRulesParser();
        robotsFetchResultFactory = RobotsFetchResultFactory.getInstance(conf);
        protocolMDprefix = ConfUtils.getString(conf, ProtocolResponse.PROTOCOL_MD_PREFIX_PARAM, "");

        metadataPrefix = ConfUtils.getString(conf, "fetcher.metadata.prefix", "");
        if (StringUtils.isNotBlank(metadataPrefix) && !metadataPrefix.endsWith(".")) {
            metadataPrefix += ".";
        }

        // http.content.limit for fetching the robots.txt
        fetchRobotsMd = new Metadata();
        final int robotsTxtContentLimit = ConfUtils.getInt(conf, "http.robots.content.limit", -1);
        fetchRobotsMd.addValue("http.content.limit", Integer.toString(robotsTxtContentLimit));

        maxQueueSize = ConfUtils.getInt(conf, "fetcher.max.queue.size", -1);
        if (maxQueueSize == -1) {
            maxQueueSize = Integer.MAX_VALUE;
        }
        final int threadCount = ConfUtils.getInt(conf, "fetcher.threads.number", 10);
        fetchItemQueue = new FetchItemQueue(threadCount, maxQueueSize);
        for (int i = 0; i < threadCount; i++) {
            new FetcherThread(conf, i).start();
        }

        robotsFetchResultCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(7, TimeUnit.DAYS)
                        .maximumSize(CACHE_SIZE)
                        .build();

        tdmRepMetadataCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(7, TimeUnit.DAYS)
                        .maximumSize(CACHE_SIZE)
                        .build();
    }

    @Override
    public void execute(Tuple input) {
        while (fetchItemQueue.size() >= maxQueueSize) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOG.error("Exception caught while waiting", e);
                Thread.currentThread().interrupt();
            }
        }

        final String urlString = input.getStringByField("url");
        if (StringUtils.isBlank(urlString)) {
            LOG.info("Missing value for field url in tuple {}", input);
            collector.ack(input);
            return;
        }

        Metadata metadata = (Metadata) input.getValueByField("metadata");
        if (metadata == null) {
            metadata = new Metadata();
        }

        byte[] content = (byte[]) input.getValueByField("content");
        if (content == null) {
            content = new byte[0];
        }

        final URL url;
        final FetchItem fetchItem;
        try {
            url = new URL(urlString);
            fetchItem = new FetchItem(url, input);
        } catch (MalformedURLException e) {
            final String errorMessage = String.format("%s is a malformed URL", urlString);
            LOG.error(errorMessage);

            metadata.setValue(metadataPrefix + "error", errorMessage);

            collector.emit("default", input, new Values(urlString, content, metadata));
            collector.ack(input);
            return;
        }

        final boolean added = fetchItemQueue.addFetchItem(fetchItem);
        if (!added) {
            collector.fail(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("url", "content", "metadata"));
    }

    private class FetcherThread extends Thread {

        private final long timeoutInQueue;

        public FetcherThread(Config conf, int numThread) {
            timeoutInQueue = ConfUtils.getLong(conf, "fetcher.queue.timeout", Integer.MAX_VALUE);

            this.setDaemon(true);
            this.setName("FetcherThread #" + numThread);
        }

        @Override
        public void run() {
            while (true) {
                final FetchItem fetchItem = fetchItemQueue.getFetchItem();

                if (fetchItem == null) {
                    spinWaiting.incrementAndGet();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        LOG.error("[{}] Exception caught while waiting", getName(), e);
                        Thread.currentThread().interrupt();
                    }
                    spinWaiting.decrementAndGet();
                    continue;
                }

                final Tuple tuple = fetchItem.getTuple();
                final String url = tuple.getStringByField("url");
                final String cacheKey = fetchItem.getCacheKey();

                activeThreads.incrementAndGet();

                LOG.debug(
                        "[{}] fetching {} => activeThreads={}, spinWaiting={}",
                        getName(),
                        url,
                        activeThreads,
                        spinWaiting);

                final long timeInQueue = System.currentTimeMillis() - fetchItem.getCreationTime();
                if (timeInQueue > timeoutInQueue * 1000) {
                    LOG.info("[{}] Waited in queue for too long: {}", getName(), url);
                    fetchItemQueue.finishFetchItem(fetchItem);
                    collector.fail(tuple);
                    continue;
                }

                final URL robotsUrl = fetchItem.getRobotsUrl();
                final RobotsFetchResult robotsFetchResult =
                        robotsFetchResultCache.getIfPresent(cacheKey);
                final Metadata robotsMetadata = new Metadata();
                Map<String, BaseRobotRules> robotsRules;
                if (robotsFetchResult == null) {
                    final long start = System.currentTimeMillis();

                    final RobotsFetchResult result = fetchRobotsFile(robotsUrl);
                    robotsMetadata.putAll(result.getMetadata());
                    robotsRules = result.getRules();

                    robotsFetchResultCache.put(cacheKey, result);

                    final long timeFetching = System.currentTimeMillis() - start;

                    robotsMetadata.setValue("cached", String.valueOf(false));

                    LOG.debug("[{}] Fetched {} in msec {}", getName(), robotsUrl, timeFetching);
                } else {
                    LOG.debug("[{}] Cache hit for {}", getName(), robotsUrl);
                    final Metadata cachedRobotsMetadata = robotsFetchResult.getMetadata();
                    robotsMetadata.putAll(cachedRobotsMetadata);
                    robotsRules = robotsFetchResult.getRules();

                    robotsMetadata.setValue("cached", String.valueOf(true));
                }

                for (final String agentName : agentNames) {
                    final boolean allowed = robotsRules.get(agentName).isAllowed(url.toString());
                    if (!allowed) {
                        robotsMetadata.setValue("agents." + agentName, String.valueOf(allowed));
                    }
                }

                final URL tdmRepUrl = fetchItem.getTdmRepUrl();
                Metadata tdmRepMetadata = tdmRepMetadataCache.getIfPresent(cacheKey);
                if (tdmRepMetadata == null) {
                    final long start = System.currentTimeMillis();

                    tdmRepMetadata = fetchTdmRepFile(tdmRepUrl);
                    tdmRepMetadataCache.put(cacheKey, tdmRepMetadata);

                    final long timeFetching = System.currentTimeMillis() - start;

                    LOG.debug("[{}] Fetched {} in msec {}", getName(), tdmRepUrl, timeFetching);
                } else {
                    LOG.debug("[{}] Cache hit for {}", getName(), tdmRepUrl);
                }

                Metadata metadata = (Metadata) tuple.getValueByField("metadata");
                if (metadata == null) {
                    metadata = new Metadata();
                }

                byte[] content = (byte[]) tuple.getValueByField("content");
                if (content == null) {
                    content = new byte[0];
                }

                Metadata newMetadata = new Metadata();
                newMetadata.setValue("fetchTime", Long.toString(Instant.now().toEpochMilli()));
                newMetadata.putAll(robotsMetadata, "robots.");
                newMetadata.putAll(tdmRepMetadata, "tdmrep.");
                metadata.putAll(newMetadata, metadataPrefix);

                collector.emit("default", tuple, new Values(url, content, metadata));

                fetchItemQueue.finishFetchItem(fetchItem);
                collector.ack(tuple);
            }
        }
    }

    private RobotsFetchResult fetchRobotsFile(final URL url) {
        final Metadata metadata = new Metadata();
        final Protocol protocol = protocolFactory.getProtocol(url);

        ProtocolResponse response;
        try {
            response = protocol.getProtocolOutput(url.toString(), fetchRobotsMd);
        } catch (Exception e) {
            metadata.setValue("url", url.toString());
            metadata.setValue("error", e.getMessage());
            return robotsFetchResultFactory.create(metadata);
        }

        int code = response.getStatusCode();

        int numRedirects = 0;
        URL redirectionUrl = url;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                && numRedirects < MAX_NUM_REDIRECTS) {
            numRedirects++;
            final String location =
                    response.getMetadata().getFirstValue(HttpHeaders.LOCATION, protocolMDprefix);

            if (StringUtils.isNotBlank(location)) {
                try {
                    redirectionUrl = new URL(redirectionUrl, location);
                } catch (MalformedURLException e) {
                    metadata.setValue("error", e.getMessage());
                    return robotsFetchResultFactory.create(metadata);
                }

                // Look up cache for redirection URL
                if (redirectionUrl.getPath().equals("/robots.txt")
                        && redirectionUrl.getQuery() == null) {
                    final String redirectionCacheKey = getCacheKey(redirectionUrl);
                    final RobotsFetchResult cacheResult =
                            robotsFetchResultCache.getIfPresent(redirectionCacheKey);
                    if (cacheResult != null) {
                        return cacheResult;
                    }
                }

                try {
                    response = protocol.getProtocolOutput(redirectionUrl.toString(), fetchRobotsMd);
                } catch (Exception e) {
                    metadata.setValue("url", redirectionUrl.toString());
                    metadata.setValue("error", e.getMessage());
                    return robotsFetchResultFactory.create(metadata);
                }

                code = response.getStatusCode();
            } else {
                metadata.setValue(
                        "error",
                        "Got redirect response " + code + " for " + url + " without location");
                return robotsFetchResultFactory.create(metadata);
            }
        }

        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            metadata.setValue("error", "Too many redirects");
            return robotsFetchResultFactory.create(metadata);
        }

        metadata.setValue("url", redirectionUrl.toString());
        metadata.setValue("code", String.valueOf(code));
        metadata.setValue("num-redirects", String.valueOf(numRedirects));
        final byte[] content = response.getContent();
        final String contentType =
                response.getMetadata().getFirstValue(HttpHeaders.CONTENT_TYPE, protocolMDprefix);

        final Map<String, BaseRobotRules> rules = new HashMap<>();
        for (final String agentName : agentNames) {
            rules.put(
                    agentName,
                    simpleRobotRulesParser.parseContent(
                            redirectionUrl.toString(), content, contentType, List.of(agentName)));
        }

        return robotsFetchResultFactory.create(metadata, rules);
    }

    private Metadata fetchTdmRepFile(final URL url) {
        final Metadata metadata = new Metadata();
        final Protocol protocol = protocolFactory.getProtocol(url);

        ProtocolResponse response;
        try {
            response = protocol.getProtocolOutput(url.toString(), Metadata.empty);
        } catch (Exception e) {
            metadata.setValue("url", url.toString());
            metadata.setValue("error", e.getMessage());
            return metadata;
        }

        int code = response.getStatusCode();

        int numRedirects = 0;
        URL redirectionUrl = url;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308)
                && numRedirects < MAX_NUM_REDIRECTS) {
            numRedirects++;
            final String location =
                    response.getMetadata().getFirstValue(HttpHeaders.LOCATION, protocolMDprefix);

            if (StringUtils.isNotBlank(location)) {
                try {
                    redirectionUrl = new URL(redirectionUrl, location);
                } catch (MalformedURLException e) {
                    metadata.setValue("error", e.getMessage());
                    return metadata;
                }

                // Look up cache for redirection URL
                if (redirectionUrl.getPath().endsWith("/tdmrep.json")
                        && redirectionUrl.getQuery() == null) {
                    final String redirectionCacheKey = getCacheKey(redirectionUrl);
                    final Metadata cacheMetadata =
                            tdmRepMetadataCache.getIfPresent(redirectionCacheKey);
                    if (cacheMetadata != null) {
                        return cacheMetadata;
                    }
                }

                try {
                    response = protocol.getProtocolOutput(redirectionUrl.toString(), fetchRobotsMd);
                } catch (Exception e) {
                    metadata.setValue("url", redirectionUrl.toString());
                    metadata.setValue("error", e.getMessage());
                    return metadata;
                }

                code = response.getStatusCode();
            } else {
                metadata.setValue(
                        "error",
                        "Got redirect response " + code + " for " + url + " without location");
                return metadata;
            }
        }

        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            metadata.setValue("error", "Too many redirects");
            return metadata;
        }

        metadata.setValue("url", redirectionUrl.toString());
        metadata.setValue("code", String.valueOf(code));
        metadata.setValue("num-redirects", String.valueOf(numRedirects));
        final byte[] content = response.getContent();
        final String contentType =
                response.getMetadata().getFirstValue(HttpHeaders.CONTENT_TYPE, protocolMDprefix);

        metadata.putAll(TdmRepParser.parseContent(content, contentType));

        return metadata;
    }

    public static String getCacheKey(final URL url) {
        final String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        final String host = url.getHost().toLowerCase(Locale.ROOT);

        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }

        return protocol + ":" + host + ":" + port;
    }

    @Override
    public void cleanup() {
        protocolFactory.cleanup();
    }
}

package eu.ows.bolt;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.bolt.JSoupParserBolt;
import com.digitalpebble.stormcrawler.bolt.StatusEmitterBolt;
import com.digitalpebble.stormcrawler.parse.Outlink;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseFilters;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.protocol.HttpHeaders;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import eu.ows.util.RobotsParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RobotsParserBolt extends StatusEmitterBolt {

    private static final Logger LOG = LoggerFactory.getLogger(JSoupParserBolt.class);

    private ParseFilter parseFilters = null;
    private String metadataPrefix;
    private List<String> agentNames;
    private String protocolMDprefix;

    @Override
    public void prepare(
            Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
        super.prepare(conf, context, collector);

        parseFilters = ParseFilters.fromConf(conf);

        agentNames = new ArrayList<>();
        agentNames.add("CompletelyRandomReferenceUserAgent");
        for (final String agentName : ConfUtils.loadListFromConf("fetcher.parse.agents", conf)) {
            agentNames.add(agentName.toLowerCase(Locale.ROOT));
        }

        metadataPrefix = ConfUtils.getString(conf, "parser.metadata.prefix", "");
        if (StringUtils.isNotBlank(metadataPrefix) && !metadataPrefix.endsWith(".")) {
            metadataPrefix += ".";
        }

        protocolMDprefix = ConfUtils.getString(conf, ProtocolResponse.PROTOCOL_MD_PREFIX_PARAM, "");
    }

    @Override
    public void execute(Tuple tuple) {
        final String url = tuple.getStringByField("url");
        final Metadata metadata = (Metadata) tuple.getValueByField("metadata");
        final byte[] content = (byte[]) tuple.getValueByField("content");

        final String contentType =
                metadata.getFirstValue(HttpHeaders.CONTENT_TYPE, protocolMDprefix);
        final Metadata robotsMetadata =
                RobotsParser.parseContent(url, content, contentType, agentNames);
        robotsMetadata.setValue("code", metadata.getFirstValue("fetch.statusCode"));

        final List<Outlink> outlinks = List.of();
        final ParseResult parse = new ParseResult(outlinks);

        // parse data of the parent URL
        final ParseData parseData = parse.get(url);
        parseData.setMetadata(metadata);
        parseData.setText("");
        parseData.setContent(content);

        try {
            parseFilters.filter(url, content, null, parse);
        } catch (RuntimeException e) {
            final String errorMessage =
                    "Exception while running parse filters on " + url + ": " + e;
            LOG.error(errorMessage);
            metadata.setValue(Constants.STATUS_ERROR_SOURCE, "parse filters");
            metadata.setValue(Constants.STATUS_ERROR_MESSAGE, errorMessage);
            collector.ack(tuple);
            return;
        }

        metadata.putAll(robotsMetadata, metadataPrefix);
        collector.emit(tuple, new Values(url, content, metadata, ""));
        collector.ack(tuple);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declare(new Fields("url", "content", "metadata", "text"));
    }
}

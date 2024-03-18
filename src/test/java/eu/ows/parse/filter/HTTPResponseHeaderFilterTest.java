package eu.ows.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.DocumentFragmentBuilder;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.protocol.Protocol;
import com.digitalpebble.stormcrawler.protocol.ProtocolFactory;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.CharsetIdentification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import org.apache.storm.Config;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;
import org.w3c.dom.DocumentFragment;

public class HTTPResponseHeaderFilterTest {

    @Test
    public void test() {
        try {
            final String urlString = "https://stormcrawler.net";

            final Config configuration = new Config();
            configuration.put("http.agent.name", "this-is-only-a-test");
            configuration.put("http.robots.agents", "this-is-only-a-test");

            final ProtocolFactory protocolFactory = ProtocolFactory.getInstance(configuration);
            final URL url = new URL(urlString);
            final Protocol protocol = protocolFactory.getProtocol(url);
            final Metadata metadataRequestHeaders = new Metadata();
            final ProtocolResponse response =
                    protocol.getProtocolOutput(urlString, metadataRequestHeaders);
            final byte[] content = response.getContent();
            final Metadata metadata = response.getMetadata();

            final String charset = CharsetIdentification.getCharset(metadata, content, -1);
            final String html =
                    Charset.forName(charset).decode(ByteBuffer.wrap(content)).toString();

            final Document jsoupDoc = Parser.htmlParser().parseInput(html, urlString);
            final DocumentFragment doc = DocumentFragmentBuilder.fromJsoup(jsoupDoc);

            final HTTPResponseHeaderFilter httpResponseHeaderFilter =
                    new HTTPResponseHeaderFilter();
            final ObjectMapper mapper = new ObjectMapper();
            final Map<String, Object> filterParams = new HashMap<>();
            filterParams.put("parse.http.x-robots-tag", "X-Robots-Tag");
            final JsonNode filterParamsNode = mapper.valueToTree(filterParams);
            httpResponseHeaderFilter.configure(configuration, filterParamsNode);

            final ParseResult parse = new ParseResult();

            // parse data of the parent URL
            final ParseData parseData = parse.get(urlString);
            parseData.setMetadata(metadata);
            parseData.setText(jsoupDoc.text());
            parseData.setContent(content);

            httpResponseHeaderFilter.filter(urlString, content, doc, parse);

            final ParseData filteredParseData = parse.get(urlString);
            final Metadata filteredMetadata = filteredParseData.getMetadata();

            System.out.println("filteredMetadata:");
            System.out.println(filteredMetadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

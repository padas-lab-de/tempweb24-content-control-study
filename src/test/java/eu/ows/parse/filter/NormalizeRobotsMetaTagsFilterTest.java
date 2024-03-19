package eu.ows.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.storm.Config;
import org.junit.Before;
import org.junit.Test;

public class NormalizeRobotsMetaTagsFilterTest {

    private final NormalizeRobotsMetaTagsFilter prettifyRobotsMetaTagsFilter =
            new NormalizeRobotsMetaTagsFilter();
    private final String mdKey = "robots.meta.tags";

    @Before
    public void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> filterParams = new HashMap<>();
        filterParams.put("key", mdKey);
        final JsonNode filterParamsNode = mapper.valueToTree(filterParams);
        prettifyRobotsMetaTagsFilter.configure(new Config(), filterParamsNode);
    }

    @Test
    public void test() {
        try {
            final String urlString = "test";
            final byte[] content = "test".getBytes();

            final List<String> values = new ArrayList<>();
            values.add("noindex");
            values.add("Noindex");
            values.add("Noindex ");
            values.add(" Noindex");
            values.add("max-image-preview:large");
            values.add(" max-image-preview  :  large ");

            final Metadata metadata = new Metadata();
            metadata.setValues(mdKey, values.toArray(new String[0]));

            final ParseResult parse = new ParseResult();
            final ParseData parseData = parse.get(urlString);
            parseData.setMetadata(metadata);
            parseData.setContent(content);

            prettifyRobotsMetaTagsFilter.filter(urlString, content, null, parse);

            final ParseData filteredParseData = parse.get(urlString);
            final Metadata filteredMetadata = filteredParseData.getMetadata();

            System.out.println("filteredMetadata:");
            System.out.println(filteredMetadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

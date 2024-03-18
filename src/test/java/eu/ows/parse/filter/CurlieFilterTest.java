package eu.ows.parse.filter;

import static org.junit.Assert.assertEquals;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class CurlieFilterTest {

    private final String mdKey = "curlieLabel";
    private final String datasetPath = "curlie.csv";

    private CurlieFilter curlieFilter;

    @Before
    public void setUpContext() {
        final Map<String, Object> configuration = new HashMap<>();
        final Map<String, Object> filterParams = new HashMap<>();
        filterParams.put("key", mdKey);
        filterParams.put("datasetPath", datasetPath);
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode filterParamsNode = mapper.valueToTree(filterParams);

        curlieFilter = new CurlieFilter();
        curlieFilter.configure(configuration, filterParamsNode);
    }

    @Test
    public void test() {
        final List<String> urls = new ArrayList<>();
        urls.add("http://gymnazium-kadan.cz");
        urls.add("http://www.albanywoodworks.com");
        urls.add("http://albanywoodworks.com");
        urls.add("https://www.zyxel.com/ru/ru");

        final List<String> results = new ArrayList<>();
        final List<String> expectedResults = new ArrayList<>();
        expectedResults.add(null);
        expectedResults.add("Business");
        expectedResults.add(null);
        expectedResults.add(null);

        final ParseResult parse = new ParseResult();
        for (String url : urls) {
            final ParseData parseData = parse.get(url);
            final Metadata metadata = new Metadata();
            parseData.setMetadata(metadata);

            curlieFilter.filter(url, null, null, parse);

            results.add(parseData.getMetadata().getFirstValue(mdKey));
        }

        assertEquals(results, expectedResults);
    }
}

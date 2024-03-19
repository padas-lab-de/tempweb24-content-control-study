package eu.ows.parse.filter;

import com.digitalpebble.stormcrawler.Constants;
import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.util.URLPartitioner;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.DocumentFragment;

public class CurlieFilter extends ParseFilter {

    private Cache<String, String> cache;
    private String mdKey = "curlieLabel";
    private String datasetPath = "curlie.csv";

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        JsonNode node = filterParams.get("key");
        if (node != null && node.isTextual()) {
            mdKey = node.asText("curlieLabel");
        }

        node = filterParams.get("datasetPath");
        if (node != null && node.isTextual()) {
            datasetPath = node.asText("curlie.csv");
        }

        initCache();
    }

    public void initCache() {
        final Map<String, String> tempMap = new HashMap<>();

        final InputStream inputStream =
                getClass().getClassLoader().getResourceAsStream("dataset/" + datasetPath);
        try (final BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                final String[] values = line.trim().split(",", 2);
                if (values.length == 2) {
                    final String host = values[0].trim();
                    final String label = values[1].trim();
                    if (StringUtils.isNotBlank(host) && StringUtils.isNotBlank(label)) {
                        tempMap.put(host, label);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        cache = Caffeine.newBuilder().maximumSize(tempMap.size()).build();

        for (final Entry<String, String> entry : tempMap.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void filter(
            String URL, byte[] bytes, DocumentFragment documentFragment, ParseResult parseResult) {
        final Metadata metadata = parseResult.get(URL).getMetadata();
        String host = metadata.getFirstValue("host");
        if (host == null) {
            host = URLPartitioner.getPartition(URL, Metadata.empty, Constants.PARTITION_MODE_HOST);
        }
        if (host != null) {
            final String curlieLabel = cache.getIfPresent(host);
            if (curlieLabel != null) {
                metadata.setValue(mdKey, curlieLabel);
            }
        }
    }
}

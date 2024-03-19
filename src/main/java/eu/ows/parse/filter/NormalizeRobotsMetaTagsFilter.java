package eu.ows.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.DocumentFragment;

public class NormalizeRobotsMetaTagsFilter extends ParseFilter {

    private String mdKey;

    @Override
    public void configure(Map<String, Object> stormConf, JsonNode filterParams) {
        final JsonNode node = filterParams.get("key");
        if (node != null && node.isTextual()) {
            mdKey = node.asText();
        }
    }

    @Override
    public void filter(String URL, byte[] content, DocumentFragment doc, ParseResult parse) {
        final ParseData parseData = parse.get(URL);
        final Metadata metadata = parseData.getMetadata();

        final String[] values = metadata.getValues(mdKey);
        if (values == null) {
            return;
        }

        final Set<String> newValues = new HashSet<>();
        for (final String value : values) {
            if (value == null) {
                continue;
            }

            final String newValue = trim(value.toLowerCase(Locale.ROOT));
            newValues.add(newValue);
        }

        metadata.setValues(mdKey, newValues.toArray(new String[0]));
    }

    /** Removes leading and trailing whitespaces around a colon. */
    private String trim(final String value) {
        String[] parts = value.split(":");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return String.join(":", parts);
    }
}

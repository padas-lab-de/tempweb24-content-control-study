package eu.ows.parse.filter;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.parse.ParseData;
import com.digitalpebble.stormcrawler.parse.ParseFilter;
import com.digitalpebble.stormcrawler.parse.ParseResult;
import com.digitalpebble.stormcrawler.protocol.ProtocolResponse;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.w3c.dom.DocumentFragment;

public class HTTPResponseHeaderFilter extends ParseFilter {

    private final Map<String, String> headers = new HashMap<>();

    private String protocolMDprefix = "";

    @Override
    public void configure(Map<String, Object> configuration, JsonNode filterParams) {
        protocolMDprefix =
                ConfUtils.getString(
                        configuration, ProtocolResponse.PROTOCOL_MD_PREFIX_PARAM, protocolMDprefix);

        final Iterator<Entry<String, JsonNode>> iterator = filterParams.fields();
        while (iterator.hasNext()) {
            final Entry<String, JsonNode> entry = iterator.next();
            final String key = entry.getKey();
            final JsonNode node = entry.getValue();
            if (node != null && node.isTextual()) {
                headers.put(node.asText().toLowerCase(Locale.ROOT), key);
            }
        }
    }

    @Override
    public void filter(String URL, byte[] content, DocumentFragment doc, ParseResult parse) {
        final ParseData parseData = parse.get(URL);
        final Metadata metadata = parseData.getMetadata();

        final Metadata newMetadata = new Metadata();
        for (final String key : metadata.keySet()) {
            if (!key.startsWith(protocolMDprefix)) {
                continue;
            }
            for (final Entry<String, String> entry : headers.entrySet()) {
                final String header = entry.getKey();
                final String fieldname = entry.getValue();
                if (key.equalsIgnoreCase(protocolMDprefix + header)) {
                    final String[] value = metadata.getValues(key);
                    newMetadata.setValues(fieldname, value);
                }
            }
        }

        metadata.putAll(newMetadata);
        // metadata.setValue("parse.debug.metadata", metadata.toString());
    }
}

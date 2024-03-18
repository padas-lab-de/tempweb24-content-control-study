package eu.ows.bolt;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.opensearch.bolt.IndexerBolt;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import java.util.HashMap;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;

public class CustomizedIndexerBolt extends IndexerBolt {

    private Map<String, String> metadata2field = new HashMap<>();

    @Override
    public void prepare(
            Map<String, Object> conf, TopologyContext context, OutputCollector collector) {
        super.prepare(conf, context, collector);

        for (String mapping : ConfUtils.loadListFromConf(metadata2fieldParamName, conf)) {
            int equals = mapping.indexOf('=');
            if (equals != -1) {
                String key = mapping.substring(0, equals).trim();
                String value = mapping.substring(equals + 1).trim();
                metadata2field.put(key, value);
            } else {
                mapping = mapping.trim();
                metadata2field.put(mapping, mapping);
            }
        }
    }

    /**
     * By default, all documents with RobotsTags.ROBOTS_NO_INDEX directive are filtered. This method
     * bypasses the NO_INDEX tag and any other filtering.
     */
    @Override
    protected boolean filterDocument(Metadata metadata) {
        return true;
    }

    /**
     * Returns a mapping of metadata field names to index. This method implements asterisk regular
     * expressions.
     */
    @Override
    protected Map<String, String[]> filterMetadata(Metadata metadata) {
        final Map<String, String[]> fieldValues = new HashMap<>();

        for (final Map.Entry<String, String> entry : metadata2field.entrySet()) {
            final String filterKey = entry.getKey();

            if (filterKey.endsWith("*")) {
                final String prefix = filterKey.substring(0, filterKey.length() - 1);

                for (final String key : metadata.keySet(prefix)) {
                    final String[] values = metadata.getValues(key);

                    if (values == null || values.length == 0) {
                        continue;
                    }

                    fieldValues.put(key, values);
                }
            } else {
                final String[] values = metadata.getValues(filterKey);

                if (values == null || values.length == 0) {
                    continue;
                }

                fieldValues.put(entry.getValue(), values);
            }
        }

        return fieldValues;
    }
}

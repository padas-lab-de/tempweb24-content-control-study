package eu.ows.util;

import com.digitalpebble.stormcrawler.Metadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TdmRepParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Metadata parseContent(final byte[] content, final String contentType) {
        final Metadata metadata = new Metadata();

        if (content == null) {
            return metadata;
        }

        int contentLength = content.length;
        metadata.setValue("content-length", String.valueOf(contentLength));

        if (contentLength == 0) {
            return metadata;
        }

        if (contentType != null) {
            metadata.setValue("content-type", contentType);
            if (contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                return metadata;
            }
        }

        /*
         * Analogous to RFC 9309
         */
        int offset = 0;
        Charset encoding = StandardCharsets.UTF_8;

        // Check for a UTF-8 BOM at the beginning (EF BB BF)
        if ((contentLength >= 3)
                && (content[0] == (byte) 0xEF)
                && (content[1] == (byte) 0xBB)
                && (content[2] == (byte) 0xBF)) {
            offset = 3;
            contentLength -= 3;
            encoding = StandardCharsets.UTF_8;
        }
        // Check for UTF-16LE BOM at the beginning (FF FE)
        else if ((contentLength >= 2)
                && (content[0] == (byte) 0xFF)
                && (content[1] == (byte) 0xFE)) {
            offset = 2;
            contentLength -= 2;
            encoding = StandardCharsets.UTF_16LE;
        }
        // Check for UTF-16BE BOM at the beginning (FE FF)
        else if ((contentLength >= 2)
                && (content[0] == (byte) 0xFE)
                && (content[1] == (byte) 0xFF)) {
            offset = 2;
            contentLength -= 2;
            encoding = StandardCharsets.UTF_16BE;
        }

        final String contentString = new String(content, offset, contentLength, encoding);

        final List<Group> groups = new ArrayList<>();
        try {
            final JsonNode rootNode = mapper.readTree(contentString);

            if (rootNode == null) {
                metadata.setValue("error", "Parsed JSON object is null");
                return metadata;
            }

            if (!rootNode.isArray()) {
                metadata.setValue("error", "JSON object is not an array");
                return metadata;
            }

            for (final JsonNode node : rootNode) {
                final String location;
                final int tdmReservation;
                try {
                    location = node.get("location").asText();
                    tdmReservation = node.get("tdm-reservation").asInt();
                } catch (Exception e) {
                    metadata.setValue("error", "JSON object cannot be parsed");
                    return metadata;
                }

                String tdmPolicy = null;
                try {
                    tdmPolicy = node.get("tdm-policy").asText();
                } catch (Exception e) {
                }

                groups.add(new Group(location, tdmReservation, tdmPolicy));
            }
        } catch (IOException e) {
            metadata.setValue("error", e.getMessage());
            return metadata;
        }

        metadata.setValue("num-groups", String.valueOf(groups.size()));
        metadata.setValue("groups", groups.toString());

        return metadata;
    }

    public static class Group {

        private final String location;
        private final int tdmReservation;
        private final String tdmPolicy;

        public Group(final String location, final int tdmReservation, final String tdmPolicy) {
            this.location = location;
            this.tdmReservation = tdmReservation;
            this.tdmPolicy = tdmPolicy;
        }

        public String getLocation() {
            return location;
        }

        public int getTdmReservation() {
            return tdmReservation;
        }

        public String getTdmPolicy() {
            return tdmPolicy;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Group{");
            sb.append("location='").append(location).append('\'');
            sb.append(", tdmReservation=").append(tdmReservation);
            if (tdmPolicy != null) {
                sb.append(", tdmPolicy='").append(tdmPolicy).append('\'');
            }
            sb.append('}');
            return sb.toString();
        }
    }
}

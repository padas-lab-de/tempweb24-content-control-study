package eu.ows.util;

import com.digitalpebble.stormcrawler.Metadata;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class TdmRepParserTest extends AbstractParserTest {

    @Test
    public void test() throws IOException {
        final byte[] content = readContent("tdmrep.json");
        final String contentType = "application/json";
        final Metadata metadata = TdmRepParser.parseContent(content, contentType);
        Assert.assertEquals(3, Integer.parseInt(metadata.getFirstValue("num-groups")));
    }
}

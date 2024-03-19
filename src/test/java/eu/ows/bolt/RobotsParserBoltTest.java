package eu.ows.bolt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.digitalpebble.stormcrawler.Metadata;
import eu.ows.util.AbstractParserTest;
import eu.ows.util.TestOutputCollector;
import eu.ows.util.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RobotsParserBoltTest extends AbstractParserTest {

    private RobotsParserBolt bolt;
    private TestOutputCollector output;

    @Before
    public void setUpContext() throws Exception {
        output = new TestOutputCollector();

        Map<String, Object> config = new HashMap<>();
        config.put("http.agent.name", "this-is-only-a-test");
        config.put("http.robots.agents", "this-is-only-a-test");
        config.put("fetcher.parse.agents", List.of("CCBot", "Google-Extended", "Bingbot"));

        bolt = new RobotsParserBolt();
        bolt.prepare(config, TestUtil.getMockedTopologyContext(), new OutputCollector(output));
    }

    @Test
    public void test() throws URISyntaxException, IOException {
        final byte[] content = readContent("robots.txt");

        final Tuple tuple = mock(Tuple.class);
        when(tuple.getSourceComponent()).thenReturn("source");
        when(tuple.getStringByField("url")).thenReturn("http://example.com/robots.txt");
        when(tuple.getValueByField("metadata")).thenReturn(new Metadata());
        when(tuple.getValueByField("content")).thenReturn(content);

        bolt.execute(tuple);

        while (output.getAckedTuples().size() < 1) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        final Metadata metadata = (Metadata) output.getEmitted("default").get(0).get(2);

        System.out.println(metadata);

        Assert.assertEquals(
                1, Integer.parseInt(metadata.getFirstValue("num-disallow-per-agent.asterisk")));

        Assert.assertEquals(2, Integer.parseInt(metadata.getFirstValue("num-allow")));
        Assert.assertEquals(5, Integer.parseInt(metadata.getFirstValue("num-disallow")));
        Assert.assertEquals(1, Integer.parseInt(metadata.getFirstValue("num-disallow-empty")));
        Assert.assertEquals(14, Integer.parseInt(metadata.getFirstValue("num-lines")));

        Assert.assertEquals(
                false, Boolean.parseBoolean(metadata.getFirstValue("disallow-all.asterisk")));
        Assert.assertEquals(
                true, Boolean.parseBoolean(metadata.getFirstValue("disallow-all.ccbot")));
        Assert.assertEquals(
                true, Boolean.parseBoolean(metadata.getFirstValue("disallow-all.google-extended")));
        Assert.assertEquals(
                false, Boolean.parseBoolean(metadata.getFirstValue("disallow-all.ia_archiver")));

        Assert.assertEquals(-2, Integer.parseInt(metadata.getFirstValue("bias.ccbot")));
    }
}

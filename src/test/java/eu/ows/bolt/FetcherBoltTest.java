package eu.ows.bolt;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.digitalpebble.stormcrawler.Metadata;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import eu.ows.util.TestOutputCollector;
import eu.ows.util.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.tuple.Tuple;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FetcherBoltTest {

    private static final int PORT = 8089;

    private FetcherBolt bolt;
    private TestOutputCollector output;

    @Before
    public void setUpContext() throws Exception {
        output = new TestOutputCollector();

        Map<String, Object> config = new HashMap<>();
        config.put("http.agent.name", "this-is-only-a-test");
        config.put("http.robots.agents", "this-is-only-a-test");
        config.put("fetcher.parse.agents", List.of("CCBot", "Google-Extended", "Bingbot"));

        bolt = new FetcherBolt();
        bolt.prepare(config, TestUtil.getMockedTopologyContext(), new OutputCollector(output));
    }

    @Rule public WireMockRule wireMockRule = new WireMockRule(PORT);

    @Test
    public void testMalformedURL() throws IOException {
        final Tuple tuple = mock(Tuple.class);
        when(tuple.getSourceComponent()).thenReturn("source");
        when(tuple.getStringByField("url")).thenReturn("test");
        when(tuple.getValueByField("metadata")).thenReturn(null);

        bolt.execute(tuple);

        final boolean acked = output.getAckedTuples().contains(tuple);
        final boolean failed = output.getFailedTuples().contains(tuple);

        Assert.assertEquals(true, acked);
        Assert.assertEquals(false, failed);
    }

    @Test
    public void testUserAgents() throws IOException, URISyntaxException {
        final String robotsContent =
                new String(
                        Files.readAllBytes(
                                Paths.get(getClass().getResource("/robots.txt").toURI())));

        stubFor(
                get(urlEqualTo("/robots.txt"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody(robotsContent)));

        stubFor(
                get(urlEqualTo("/.well-known/tdmrep.json"))
                        .willReturn(aResponse().withStatus(404)));

        final Tuple tuple = mock(Tuple.class);
        when(tuple.getSourceComponent()).thenReturn("source");
        when(tuple.getStringByField("url")).thenReturn("http://localhost:" + PORT);
        when(tuple.getValueByField("metadata")).thenReturn(new Metadata());

        final Tuple tuple2 = mock(Tuple.class);
        when(tuple2.getSourceComponent()).thenReturn("source");
        when(tuple2.getStringByField("url")).thenReturn("http://localhost:" + PORT);
        when(tuple2.getValueByField("metadata")).thenReturn(null);

        bolt.execute(tuple);

        // Sleep for 1 second to allow the fetch to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        bolt.execute(tuple2);

        while (output.getAckedTuples().size() + output.getFailedTuples().size() < 2) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        final List<List<Object>> defaultTuples = output.getEmitted("default");

        // System.out.println(defaultTuples);
        // System.out.println(defaultTuples.get(0));
        // System.out.println(defaultTuples.get(0).get(0));

        Assert.assertEquals(2, defaultTuples.size());

        final Metadata metadata = (Metadata) tuple.getValueByField("metadata");

        Assert.assertEquals(null, metadata.getFirstValue("robots.agents.bingbot"));
        Assert.assertEquals("false", metadata.getFirstValue("robots.agents.ccbot"));
        Assert.assertEquals("false", metadata.getFirstValue("robots.agents.google-extended"));
    }
}

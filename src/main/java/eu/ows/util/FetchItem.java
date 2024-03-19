package eu.ows.util;

import eu.ows.bolt.FetcherBolt;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.storm.tuple.Tuple;

public class FetchItem {

    private final Tuple tuple;
    private final long creationTime;
    private final URL robotsUrl;
    private final URL tdmRepUrl;
    private final String cacheKey;

    public FetchItem(final URL url, final Tuple tuple) throws MalformedURLException {
        this.tuple = tuple;

        robotsUrl = new URL(url, "/robots.txt");
        tdmRepUrl = new URL(url, "/.well-known/tdmrep.json");

        creationTime = System.currentTimeMillis();

        cacheKey = FetcherBolt.getCacheKey(url);
    }

    public Tuple getTuple() {
        return tuple;
    }

    public URL getRobotsUrl() {
        return robotsUrl;
    }

    public URL getTdmRepUrl() {
        return tdmRepUrl;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getCacheKey() {
        return cacheKey;
    }
}

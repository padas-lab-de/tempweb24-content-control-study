package eu.ows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.ClearScrollResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.Scroll;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;

public class GetSitemaps {

    private static final Logger LOG = LoggerFactory.getLogger(GetSitemaps.class);

    private static final Map<String, String> sitemapsMap = new HashMap<>();

    // @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("Start GetSitemaps script");

        final String oshost = "pappel.dimis.fim.uni-passau.de";
        final String osport = "9200";
        final String scheme = "http";
        final String user = "admin";
        final String password = "admin";

        final RestClientBuilder builder =
                RestClient.builder(new HttpHost(oshost, Integer.parseInt(osport), scheme));

        if (user != null) {
            // Establish credentials to use basic authentication.
            // Only for demo purposes. Don't specify your credentials in code.
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(user, password));

            builder.setHttpClientConfigCallback(
                    new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(
                                HttpAsyncClientBuilder httpClientBuilder) {
                            return httpClientBuilder.setDefaultCredentialsProvider(
                                    credentialsProvider);
                        }
                    });
        }

        final RestHighLevelClient client = new RestHighLevelClient(builder);

        SearchRequest searchRequest = new SearchRequest("content-robots-2023-50");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        queryBuilder.filter(
                QueryBuilders.rangeQuery("fetcher.file.robots.num-sitemaps")
                        .gte(1));

        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.size(10000);

        searchRequest.source(searchSourceBuilder);

        final Scroll scroll = new Scroll(TimeValue.timeValueMinutes(1L));
        searchRequest.scroll(scroll);

        int counter = 0;
        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            String scrollId = searchResponse.getScrollId();
            SearchHits searchHits = searchResponse.getHits();

            while (searchHits != null && counter < 30000) {
                counter += 1;
                if (counter == 1 || counter % 100 == 0) {
                    LOG.info("counter: " + counter);
                    if (counter > 1) {
                        flushFile(counter);
                    }
                }

                for (SearchHit hit : searchHits) {
                    final Map<String, Object> fields = hit.getSourceAsMap();

                    final String url = fields.get("url").toString();
                    final String curlieLabel = fields.getOrDefault("parse.curlieLabel", "-").toString();
                    final String sitemaps = fields.get("fetcher.file.robots.sitemaps").toString();
                    if (sitemaps == null || StringUtils.isBlank(sitemaps)) {
                        LOG.debug("[" + url + "] sitemaps is null or empty");
                        continue;
                    }

                    // System.out.println("sitemaps: " + sitemaps + " curlieLabel: " + curlieLabel);

                    if (sitemaps.startsWith("[") && sitemaps.endsWith("]")) {
                        // System.out.println("[" + url + "] sitemaps is array");
                        for (final String sitemap : sitemaps.substring(1, sitemaps.length() - 1).split(",")) {
                            addToMap(sitemap, curlieLabel, url);
                        }
                    } else {
                        // System.out.println("[" + url + "] sitemaps is not array");
                        addToMap(sitemaps, curlieLabel, url);
                    }
                }

                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                scrollRequest.scroll(scroll);
                searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
                scrollId = searchResponse.getScrollId();
                searchHits = searchResponse.getHits();
            }

            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            ClearScrollResponse clearScrollResponse = client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            boolean succeeded = clearScrollResponse.isSucceeded();
            System.out.println("succeeded: " + succeeded);
        } catch (IOException e) {
            e.printStackTrace();
        }

        flushFile(counter);

        try {
            client.close();
        } catch (IOException e) {
            LOG.error("Exception caught when closing the OpenSearch client: " + e.getMessage());
        }
        System.out.println("End GetSitemaps script");
    }

    private static void flushFile(final int counter) {
        final String filename = "sitemaps-" + counter + ".csv";

        try (FileWriter csvWriter = new FileWriter(filename)) {
            for (Map.Entry<String, String> entry : sitemapsMap.entrySet()) {
                String sitemap = entry.getKey();
                String curlieLabel = entry.getValue();
                csvWriter.append(sitemap)
                        .append(",")
                        .append(curlieLabel)
                        .append("\n");
            }
            csvWriter.flush();
            sitemapsMap.clear();
            LOG.info(filename + " created successfully");
        } catch (IOException e) {
            LOG.error("Exception caught when writing to CSV file: " + e.getMessage());
        }
    }

    private static void addToMap(final String sitemap, final String curlieLabel, final String url) {
        final String cleaned = sitemap.toLowerCase(Locale.ROOT).trim();
        if (StringUtils.isBlank(cleaned)) {
            LOG.debug("[" + cleaned + "] is blank");
            return;
        }
        if (cleaned.contains(" ")) {
            LOG.debug("[" + cleaned + "] contains space");
            return;
        }
        if (cleaned.startsWith("/")) {
            final String full = url.replace("/robots.txt", cleaned);
            sitemapsMap.put(full, curlieLabel);
        } else if (!cleaned.startsWith("http")) {
            LOG.debug("[" + cleaned + "] does not start with http");
        } else if (!cleaned.endsWith(".xml") && !cleaned.endsWith(".xml.gz")) {
            LOG.debug("[" + cleaned + "] does not end with .xml or .xml.gz");
        } else {
            sitemapsMap.put(cleaned, curlieLabel);
        }
    }
}


package eu.ows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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

public class GetUrls {

    private static final Logger LOG = LoggerFactory.getLogger(GetUrls.class);

    private static final Map<String, String> urlsMap = new HashMap<>();

    // @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("Start GetUrls script");

        final String oshost = "opensearch.pads.fim.uni-passau.de";
        final String osport = "443";
        final String scheme = "https";
        final String user = "admin";
        final String password = "F2H3BwEVcumLR$v?:UQ\"Cz";

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
                QueryBuilders.rangeQuery("fetcher.file.robots.num-useragents")
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
                    addToMap(url, curlieLabel);
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
        System.out.println("End GetUrls script");
    }

    private static void flushFile(final int counter) {
        final String filename = "robotstxt-" + counter + ".csv";

        try (FileWriter csvWriter = new FileWriter(filename)) {
            for (Map.Entry<String, String> entry : urlsMap.entrySet()) {
                String url = entry.getKey();
                String curlieLabel = entry.getValue();
                csvWriter.append(url)
                        .append(",")
                        .append(curlieLabel)
                        .append("\n");
            }
            csvWriter.flush();
            urlsMap.clear();
            LOG.info(filename + " created successfully");
        } catch (IOException e) {
            LOG.error("Exception caught when writing to CSV file: " + e.getMessage());
        }
    }

    private static void addToMap(final String url, final String curlieLabel) {
        final String cleaned = url.toLowerCase(Locale.ROOT).trim();
        urlsMap.put(cleaned, curlieLabel);
    }
}


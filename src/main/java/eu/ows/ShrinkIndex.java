package eu.ows;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.action.bulk.BulkProcessor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import javax.net.ssl.SSLContext;

public class ShrinkIndex {

    private static final String INDEX_NAME = "content-warc-2016-50-new";
    private static final int TOTAL_DOCS_TO_DELETE = 1643419;
    private static final long SCROLL_TIMEOUT_MS = 60000;
    private static final int BATCH_SIZE = 111;

    public static void main(String[] args) throws IOException {
        final String hostname = "opensearch.pads.fim.uni-passau.de"; // Replace with your OpenSearch host
        final int port = 443; // Replace with your OpenSearch port
        final String scheme = "https"; // Use https for SSL
        final String username = "admin"; // Replace with your username
        final String password = "F2H3BwEVcumLR$v?:UQ\"Cz"; // Replace with your password

        RestClientBuilder builder = RestClient.builder(new HttpHost(hostname, port, scheme))
                .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                    @Override
                    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                        try {
                            // SSL context for secure connection
                            SSLContextBuilder sslBuilder = SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true);
                            SSLContext sslContext;
                            sslContext = sslBuilder.build();
                            httpClientBuilder.setSSLContext(sslContext);
                        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                            throw new RuntimeException(e);
                        }
                        httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);

                        // Basic authentication
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
                        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                });

        RestHighLevelClient client = new RestHighLevelClient(builder);

        final BulkProcessor.Listener listener =
                new BulkProcessor.Listener() {
                    @Override
                    public void afterBulk(long arg0, BulkRequest request, BulkResponse response) {
                        TimeValue responseTime = response.getTook();
                        System.out.println("Bulkrequest took " + responseTime);
                    }

                    @Override
                    public void afterBulk(long arg0, BulkRequest request, Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public void beforeBulk(long arg0, BulkRequest arg1) {}
                };

        BulkProcessor bulkProcessor =
                BulkProcessor.builder(
                                (request, bulkListener) ->
                                        client.bulkAsync(
                                                request, RequestOptions.DEFAULT, bulkListener),
                                listener)
                        .setFlushInterval(TimeValue.timeValueSeconds(5))
                        .setBulkActions(2000)
                        .setConcurrentRequests(4)
                        .build();

        try {
            Set<String> allDocIds = fetchAllDocumentIds(client);
            System.out.println("Total number of documents in index: " + allDocIds.size());
            // Set<String> idsToDelete = randomlySelectIds(allDocIds, TOTAL_DOCS_TO_DELETE);
            // System.out.println("Total number of documents to delete: " + idsToDelete.size());
            // client.close();
            // System.exit(0);
            bulkDeleteDocuments(client, allDocIds, bulkProcessor);
        } finally {
            client.close();
        }
    }

    private static Set<String> fetchAllDocumentIds(RestHighLevelClient client) throws IOException {
        System.out.println("Fetching all document IDs from index: " + INDEX_NAME);
        Set<String> docIds = new HashSet<>();
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(BATCH_SIZE);
        searchRequest.source(searchSourceBuilder);
        searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_TIMEOUT_MS));

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        int counter = 0;
        while (searchHits != null && searchHits.length > 0) {
            counter += 1;
            if (counter == 1 || counter % 10 == 0) {
                System.out.println("counter: " + counter);
            }
            for (SearchHit hit : searchHits) {
                docIds.add(hit.getId());
            }

            SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
            scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_TIMEOUT_MS));
            searchResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
            
            if (counter == 1) {
                break;
            }

            // Wait for 1s
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return docIds;
    }

    private static Set<String> randomlySelectIds(Set<String> ids, int count) {
        List<String> listIds = new ArrayList<>(ids);
        Collections.shuffle(listIds);
        return new HashSet<>(listIds.subList(0, count));
    }

    private static void bulkDeleteDocuments(RestHighLevelClient client, Set<String> idsToDelete, BulkProcessor bulkProcessor) throws IOException {
        int counter = 0;
        for (String id : idsToDelete) {
            counter += 1;
            bulkProcessor.add(new DeleteRequest(INDEX_NAME, id));
            if (counter % 2000 == 0) {
                System.out.println("counter: " + counter);
                // Wait for 1s
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                    
                // client.bulk(bulkRequest, RequestOptions.DEFAULT);
            }
        }

        bulkProcessor.flush();

        // Wait for 1s
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

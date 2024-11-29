import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PythonServiceClient {
    private final String baseUrl;
    private transient HttpClient client;
    private transient ObjectMapper mapper;

    public PythonServiceClient(String host, String port) {
        this.baseUrl = String.format("http://%s:%s", host, port);
    }

    private void ensureInitialized() {
        if (client == null) {
            client = HttpClient.newHttpClient();
        }
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
    }

    public double[] getEmbedding(String text) throws IOException, InterruptedException {
        ensureInitialized();

        return new double[]{1.0, 2.0, 3.0};


        // HttpRequest request = HttpRequest.newBuilder()
        //     .uri(URI.create(baseUrl + "/embedding"))
        //     .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(
        //         Map.of("text", text)
        //     )))
        //     .header("Content-Type", "application/json")
        //     .build();

        // HttpResponse<String> response = client.send(request, 
        //     HttpResponse.BodyHandlers.ofString());

        // Map<String, Object> result = mapper.readValue(response.body(), 
        //     new TypeReference<Map<String, Object>>() {});
            
        // List<Number> embeddings = (List<Number>) result.get("embedding");
        // double[] embeddingArray = new double[embeddings.size()];
        // for (int i = 0; i < embeddings.size(); i++) {
        //     embeddingArray[i] = embeddings.get(i).floatValue();
        // }
        
        // return embeddingArray;
    }
}
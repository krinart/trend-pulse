import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import java.io.*;
import java.nio.file.Path;


public class PythonServiceClient {
    private final File socketFile;
    private final ObjectMapper mapper;

    public PythonServiceClient(String socketPath) {
        this.socketFile = new File(socketPath);
        this.mapper = new ObjectMapper();
    }

    public double[] getEmbedding(String text) throws IOException {
        Map<String, String> request = Map.of("text", text);
        
        try (AFUNIXSocket socket = AFUNIXSocket.newInstance()) {
            socket.connect(AFUNIXSocketAddress.of(socketFile));
            
            // Send request
            try (OutputStream out = socket.getOutputStream()) {
                mapper.writeValue(out, request);
                out.flush();
                socket.shutdownOutput(); // Important: signals end of request
            }
            
            // Read response
            try (InputStream in = socket.getInputStream()) {
                Map<String, Object> response = mapper.readValue(in, Map.class);
                
                if (response.containsKey("error")) {
                    throw new IOException("Server error: " + response.get("error"));
                }
                
                List<Double> embeddings = (List<Double>) response.get("embedding");
                return embeddings.stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            }
        }
    }
}
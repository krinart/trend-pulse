package com.trendpulse.processors;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.AppendBlobClient;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.core.util.BinaryData;
import com.trendpulse.TrendDetectionJob;
import com.trendpulse.schema.TrendDataEvent;
import com.trendpulse.schema.TrendDataType;
import com.trendpulse.schema.TrendDataWrittenEvent;


public class TrendWriter extends KeyedProcessFunction<CharSequence, TrendDataEvent, TrendDataWrittenEvent> {
    
    private static final Logger LOG = LoggerFactory.getLogger(TrendDetectionJob.class);

    private final String connectionString; 
    private final String basePath;
    private final String containerName;

    private transient BlobServiceClient blobServiceClient;
    private transient Map<String, AppendBlobClient> appendClients;
    
    public TrendWriter(String connectionString, String containerName, String basePath) {
        this.connectionString = connectionString;
        this.containerName = containerName;
        this.basePath = basePath;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        LOG.info("TrendWriter.open: start");

        System.setProperty("reactor.netty.native", "false");

        this.appendClients = new HashMap<>();

        this.blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient();

            LOG.info("TrendWriter.open: end");
    }

    @Override
    public void processElement(TrendDataEvent event, Context ctx, Collector<TrendDataWrittenEvent> out) throws Exception {
        String filePath = event.getPath().toString();
        String fullPath = Paths.get(basePath, filePath).toString();

        // System.out.println("TrendWriter.processElement: " + fullPath);
        // LOG.info("TrendWriter.processElement: {}", fullPath);

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        try {
            if (event.getDataType() == TrendDataType.DATA_TYPE_TIMESERIES) {
                // Get or create append blob client
                AppendBlobClient appendClient = appendClients.computeIfAbsent(
                    fullPath,
                    path -> {
                        AppendBlobClient client = containerClient
                            .getBlobClient(path)
                            .getAppendBlobClient();
                            
                        // Create if doesn't exist
                        if (!client.exists()) {
                            client.create();
                        }
                        return client;
                    }
                );
                
                // Append data
                // Convert string to InputStream
                byte[] dataBytes = event.getData().toString().getBytes(StandardCharsets.UTF_8);
                InputStream dataStream = new ByteArrayInputStream(dataBytes);
                appendClient.appendBlock(dataStream, dataBytes.length);

            } else {
                // Regular block blob for non-append writes
                BlockBlobClient blockClient = containerClient
                    .getBlobClient(fullPath)
                    .getBlockBlobClient();
                    
                blockClient.upload(BinaryData.fromString(event.getData().toString()), true);
            }

            // Emit success event
            out.collect(new TrendDataWrittenEvent(
                event.getTrendId(),
                event.getTimestamp(),
                event.getDataType()
            ));
        } catch (Exception e) {
            // Handle errors - might want to retry or emit to error stream
            // log.error("Failed to write data to {}: {}", request.path, e.getMessage());
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        appendClients.clear();
    }
}

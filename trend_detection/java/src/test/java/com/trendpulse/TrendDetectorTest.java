package com.trendpulse;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.time.*;
import java.io.IOException;

import com.trendpulse.items.InputMessage;
import com.trendpulse.items.Trend;
import com.trendpulse.lib.PythonServiceClient;

class TrendDetectorTest {
    private TrendDetector detector;
    
    @Mock
    private PythonServiceClient pythonClient;
    
    private static final String SOCKET_PATH = "/tmp/test_socket.sock";
    private static final long BASE_TIME = 1600000000000L; // Some base timestamp
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        detector = new TrendDetector(SOCKET_PATH);
        // Replace the real Python client with our mock
        TestUtils.setPrivateField(detector, "pythonClient", pythonClient);
    }
    
    @Test
    void testSingleMessageProcessing() throws Exception {
        // Arrange
        InputMessage message = createMessage("test message");
        double[] embedding = new double[]{0.1, 0.2, 0.3};
        when(pythonClient.getEmbedding(anyString())).thenReturn(embedding);
        
        // Act
        TrendDetector.ProcessingResult result = detector.processMessage(message, BASE_TIME);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.getNewTrends().isEmpty()); // Single message shouldn't create trend
        verify(pythonClient).getEmbedding(message.getText());
    }
    
    @Test
    void testClusterFormation() throws Exception {
        // Arrange
        List<InputMessage> messages = Arrays.asList(
            createMessage("earthquake in california", new double[]{0.1, 0.1, 0.1}),
            createMessage("california earthquake damage", new double[]{0.11, 0.09, 0.1}),
            createMessage("earthquake hits california coast", new double[]{0.09, 0.11, 0.1})
        );
        
        // Act - Force clustering by sending enough messages
        TrendDetector.ProcessingResult finalResult = null;
        for (InputMessage msg : messages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            finalResult = detector.processMessage(msg, BASE_TIME + TrendDetector.UNPROCESSED_MESSAGES_THRESHOLD); // Force clustering
        }
        
        // Assert
        assertNotNull(finalResult);
        assertEquals(1, finalResult.getNewTrends().size());
        Trend trend = finalResult.getNewTrends().get(0);
        assertEquals(3, trend.getMessages().size());
    }
    
    @Test
    void testMultipleClusters() throws Exception {
        // Arrange
        List<InputMessage> earthquakeMessages = Arrays.asList(
            createMessage("earthquake in california", new double[]{0.1, 0.1, 0.1}),
            createMessage("california earthquake damage", new double[]{0.11, 0.09, 0.1}),
            createMessage("earthquake hits coast", new double[]{0.09, 0.11, 0.1})
        );
        
        List<InputMessage> sportsMessages = Arrays.asList(
            createMessage("football match today", new double[]{0.8, 0.8, 0.8}),
            createMessage("great game results", new double[]{0.79, 0.81, 0.8}),
            createMessage("football score update", new double[]{0.81, 0.79, 0.8})
        );
        
        // Act
        TrendDetector.ProcessingResult finalResult = null;
        for (InputMessage msg : earthquakeMessages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            finalResult = detector.processMessage(msg, BASE_TIME);
        }
        for (InputMessage msg : sportsMessages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            finalResult = detector.processMessage(msg, BASE_TIME);
        }
        
        // Assert
        assertNotNull(finalResult);
        assertEquals(2, finalResult.getNewTrends().size());
    }
    
    @Test
    void testTrendMatching() throws Exception {
        // Arrange
        // First create a trend
        List<InputMessage> initialMessages = Arrays.asList(
            createMessage("earthquake in california", new double[]{0.1, 0.1, 0.1}),
            createMessage("california earthquake damage", new double[]{0.11, 0.09, 0.1}),
            createMessage("earthquake hits coast", new double[]{0.09, 0.11, 0.1})
        );
        
        // Process initial messages to create trend
        for (InputMessage msg : initialMessages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            detector.processMessage(msg, BASE_TIME);
        }
        
        // New similar message
        InputMessage similarMessage = createMessage(
            "new earthquake report", 
            new double[]{0.1, 0.1, 0.11}
        );
        when(pythonClient.getEmbedding(similarMessage.getText()))
            .thenReturn(similarMessage.getEmbedding());
        
        // Act
        TrendDetector.ProcessingResult result = detector.processMessage(similarMessage, BASE_TIME + 1000);
        
        // Assert
        assertTrue(result.getNewTrends().isEmpty()); // Should match existing trend
    }
    
    @Test
    void testTimeBasedClustering() throws Exception {
        // Arrange
        List<InputMessage> messages = Arrays.asList(
            createMessage("message 1", new double[]{0.1, 0.1, 0.1}),
            createMessage("message 2", new double[]{0.11, 0.09, 0.1})
        );
        
        // Act & Assert - First messages shouldn't trigger clustering
        for (InputMessage msg : messages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            TrendDetector.ProcessingResult result = detector.processMessage(msg, BASE_TIME);
            assertTrue(result.getNewTrends().isEmpty());
        }
        
        // Act & Assert - Message after time threshold should trigger clustering
        InputMessage lastMessage = createMessage("message 3", new double[]{0.09, 0.11, 0.1});
        when(pythonClient.getEmbedding(lastMessage.getText()))
            .thenReturn(lastMessage.getEmbedding());
        
        TrendDetector.ProcessingResult result = detector.processMessage(
            lastMessage, 
            BASE_TIME + (TrendDetector.CLUSTERING_INTERVAL_SECONDS * 1000 + 1)
        );
        
        assertFalse(result.getNewTrends().isEmpty());
    }
    
    @Test
    void testMessageThresholdClustering() throws Exception {
        // Arrange
        List<InputMessage> messages = new ArrayList<>();
        for (int i = 0; i < TrendDetector.UNPROCESSED_MESSAGES_THRESHOLD; i++) {
            messages.add(createMessage(
                "message " + i, 
                new double[]{0.1 + (i * 0.01), 0.1, 0.1}
            ));
        }
        
        // Act
        TrendDetector.ProcessingResult finalResult = null;
        for (InputMessage msg : messages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            finalResult = detector.processMessage(msg, BASE_TIME);
        }
        
        // Assert
        assertNotNull(finalResult);
        assertFalse(finalResult.getNewTrends().isEmpty());
    }
    
    @Test
    void testErrorHandling() throws Exception {
        // Arrange
        InputMessage message = createMessage("test message");
        when(pythonClient.getEmbedding(anyString()))
            .thenThrow(new IOException("Connection failed"));
        
        // Act
        TrendDetector.ProcessingResult result = detector.processMessage(message, BASE_TIME);
        
        // Assert
        assertNotNull(result);
        assertTrue(result.getNewTrends().isEmpty());
    }
    
    @Test
    void testEmptyCluster() throws Exception {
        // Test with messages that are too dissimilar to form clusters
        List<InputMessage> messages = Arrays.asList(
            createMessage("completely different topic 1", new double[]{0.1, 0.1, 0.1}),
            createMessage("totally unrelated topic 2", new double[]{0.5, 0.5, 0.5}),
            createMessage("another different topic 3", new double[]{0.9, 0.9, 0.9})
        );
        
        TrendDetector.ProcessingResult finalResult = null;
        for (InputMessage msg : messages) {
            when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
            finalResult = detector.processMessage(msg, BASE_TIME);
        }
        
        assertNotNull(finalResult);
        assertTrue(finalResult.getNewTrends().isEmpty());
    }
    
    // Utility method to create test messages
    private InputMessage createMessage(String text, long timestamp) {
        OffsetDateTime dateTime = Instant.ofEpochMilli(timestamp)
            .atOffset(ZoneOffset.UTC);

        InputMessage message = new InputMessage();
        message.setText(text);
        message.setDatetime(dateTime);
        
        return message;
    }

    private InputMessage createMessage(String text) {
        return createMessage(text, BASE_TIME);
    }

    private InputMessage createMessage(String text, double[] embedding) {
        InputMessage message = createMessage(text);
        message.setEmbedding(embedding);
        return message;
    }
}

// Utility class to help with testing
class TestUtils {
    static void setPrivateField(Object object, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
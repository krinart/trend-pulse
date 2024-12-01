// package com.trendpulse;

// import org.junit.jupiter.api.*;
// import org.mockito.Mock;
// import org.mockito.MockitoAnnotations;

// import static org.mockito.ArgumentMatchers.anyString;
// import static org.mockito.Mockito.*;
// import static org.junit.jupiter.api.Assertions.*;

// import static org.hamcrest.Matchers.hasItem;
// import static org.hamcrest.MatcherAssert.assertThat;

// import java.util.*;
// import java.time.*;
// import java.io.IOException;

// import com.trendpulse.items.InputMessage;
// import com.trendpulse.items.Trend;
// import com.trendpulse.lib.PythonServiceClient;

// class TrendDetectorTest {
//     private TrendDetector detector;
    
//     @Mock
//     private PythonServiceClient pythonClient;
    
//     private static final String SOCKET_PATH = "/tmp/test_socket.sock";
//     private static final long BASE_TIME = 1600000000000L; // Some base timestamp
    
//     @BeforeEach
//     void setUp() {
//         MockitoAnnotations.openMocks(this);
//         detector = new TrendDetector(SOCKET_PATH);
//         TestUtils.setPrivateField(detector, "pythonClient", pythonClient);
//     }
    
//     @Test
//     void testSingleMessageProcessing() throws Exception {
//         InputMessage message = createMessage("test message");
//         double[] embedding = new double[]{0.1, 0.2, 0.3};
//         when(pythonClient.getEmbedding(anyString())).thenReturn(embedding);
        
//         TrendDetector.ProcessingResult result = detector.processMessage(message, BASE_TIME);
        
//         assertNotNull(result);
//         assertTrue(result.getNewTrends().isEmpty()); // Single message shouldn't create trend
//         verify(pythonClient).getEmbedding(message.getText());
//     }
    
//     @Test
//     void testClusterFormation() throws Exception {
//         List<InputMessage> messages = Arrays.asList(
//             createMessage("earthquake in california", new double[]{0.1, 0.1, 0.1}),
//             createMessage("california earthquake damage", new double[]{0.11, 0.09, 0.1}),
//             createMessage("earthquake hits california coast", new double[]{0.09, 0.11, 0.1})
//         );

//         assertNotNull(messages.get(0).getEmbedding());
        
//         for (InputMessage msg : messages) {
//             when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
//             detector.processMessage(msg, BASE_TIME); // Force clustering
//         }
//         InputMessage msg = createMessage("earthquake hits california coast", new double[]{0.09, 0.11, 0.1});
//         TrendDetector.ProcessingResult finalResult = detector.processMessage(
//             msg, BASE_TIME + TrendDetector.CLUSTERING_INTERVAL_SECONDS * 1000);
        
//         assertNotNull(finalResult);
//         assertEquals(1, finalResult.getNewTrends().size());
//         Trend trend = finalResult.getNewTrends().get(0);
//         assertEquals(4, trend.getMessages().size());
//         assertThat(trend.getKeywords(), hasItem("earthquake"));
//     }
    
//     @Test
//     void testMultipleClusters() throws Exception {
//         List<InputMessage> earthquakeMessages = Arrays.asList(
//             createMessage("earthquake in california", new double[]{1.0, 1.0, 0.1}),
//             createMessage("california earthquake damage", new double[]{1.0, 0.2, 0.1}),
//             createMessage("earthquake hits coast", new double[]{0.2, 1.0, 0.1}),
//             createMessage("earthquake hits coast", new double[]{0.2, 1.0, 0.1})
//         );
        
//         List<InputMessage> sportsMessages = Arrays.asList(
//             createMessage("football match today", new double[]{-1.0, -1.0, 0.1}),
//             createMessage("great game results", new double[]{-1.0, -0.2, 0.1}),
//             createMessage("football score update", new double[]{-0.2, -1.0, 0.1})
//         );
        
//         for (InputMessage msg : earthquakeMessages) {
//             when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
//             detector.processMessage(msg, BASE_TIME);
//         }
//         for (InputMessage msg : sportsMessages) {
//             when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
//             detector.processMessage(msg, BASE_TIME);
//         }

//         InputMessage msg = createMessage("football score update", new double[]{0.15, 0.15, 0.95});
//         TrendDetector.ProcessingResult finalResult = detector.processMessage(
//             msg, BASE_TIME + TrendDetector.CLUSTERING_INTERVAL_SECONDS * 1000);
        
//         // Assert
//         assertNotNull(finalResult);
//         assertEquals(2, finalResult.getNewTrends().size());
//         assertEquals(4, finalResult.getNewTrends().get(0).getMessages().size());
//         assertEquals(4, finalResult.getNewTrends().get(1).getMessages().size());
//     }
    
//     @Test
//     void testTrendMatching() throws Exception {
//         // Arrange
//         // First create a trend
//         List<InputMessage> initialMessages = Arrays.asList(
//             createMessage("earthquake in california", new double[]{0.1, 0.1, 0.1}),
//             createMessage("california earthquake damage", new double[]{0.11, 0.09, 0.1}),
//             createMessage("earthquake hits coast", new double[]{0.09, 0.11, 0.1})
//         );
        
//         // Process initial messages to create trend
//         for (InputMessage msg : initialMessages) {
//             when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
//             detector.processMessage(msg, BASE_TIME);
//         }
        
//         // New similar message
//         InputMessage similarMessage = createMessage(
//             "new earthquake report", 
//             new double[]{0.1, 0.1, 0.11}
//         );
//         when(pythonClient.getEmbedding(similarMessage.getText()))
//             .thenReturn(similarMessage.getEmbedding());
        
//         // Act
//         TrendDetector.ProcessingResult result = detector.processMessage(similarMessage, BASE_TIME + 1000);
        
//         // Assert
//         assertTrue(result.getNewTrends().isEmpty()); // Should match existing trend
//     }
    
//     @Test
//     void testErrorHandling() throws Exception {
//         // Arrange
//         InputMessage message = createMessage("test message");
//         when(pythonClient.getEmbedding(anyString()))
//             .thenThrow(new IOException("Connection failed"));
        
//         // Act
//         TrendDetector.ProcessingResult result = detector.processMessage(message, BASE_TIME);
        
//         // Assert
//         assertNotNull(result);
//         assertTrue(result.getNewTrends().isEmpty());
//     }
    
//     @Test
//     void testEmptyCluster() throws Exception {
//         // Test with messages that are too dissimilar to form clusters
//         List<InputMessage> messages = Arrays.asList(
//             createMessage("completely different topic 1", new double[]{0.1, 0.1, 0.1}),
//             createMessage("totally unrelated topic 2", new double[]{0.5, 0.5, 0.5}),
//             createMessage("another different topic 3", new double[]{0.9, 0.9, 0.9})
//         );
        
//         TrendDetector.ProcessingResult finalResult = null;
//         for (InputMessage msg : messages) {
//             when(pythonClient.getEmbedding(msg.getText())).thenReturn(msg.getEmbedding());
//             finalResult = detector.processMessage(msg, BASE_TIME);
//         }
        
//         assertNotNull(finalResult);
//         assertTrue(finalResult.getNewTrends().isEmpty());
//     }
    
//     // Utility method to create test messages
//     private InputMessage createMessage(String text, long timestamp) {
//         OffsetDateTime dateTime = Instant.ofEpochMilli(timestamp)
//             .atOffset(ZoneOffset.UTC);

//         InputMessage message = new InputMessage();
//         message.setText(text);
//         message.setDatetime(dateTime);
        
//         return message;
//     }

//     private InputMessage createMessage(String text) {
//         return createMessage(text, BASE_TIME);
//     }

//     private InputMessage createMessage(String text, double[] embedding) {
//         InputMessage message = createMessage(text);
//         message.setEmbedding(embedding);
//         return message;
//     }
// }

// // Utility class to help with testing
// class TestUtils {
//     static void setPrivateField(Object object, String fieldName, Object value) {
//         try {
//             java.lang.reflect.Field field = object.getClass().getDeclaredField(fieldName);
//             field.setAccessible(true);
//             field.set(object, value);
//         } catch (Exception e) {
//             throw new RuntimeException(e);
//         }
//     }
// }
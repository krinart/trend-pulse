import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.Map;
import java.util.HashMap;

public class TrendDetectionProcessor extends KeyedProcessFunction<Integer, InputMessage, TrendEvent> {
    private transient TrendDetector detector;

    @Override
    public void open(Configuration conf) throws Exception {
         // Get socket path from configuration
        String socketPath = getRuntimeContext()
            .getExecutionConfig()
            .getGlobalJobParameters()
            .toMap()
            .getOrDefault("python.socket.path", "/tmp/embedding_server.sock")
            .toString();

        detector = new TrendDetector(socketPath);
    }

    @Override
    public void processElement(InputMessage message, Context ctx, Collector<TrendEvent> out) 
            throws Exception {
        // Validate inputs
        if (message == null) {
            throw new IllegalArgumentException("Input message cannot be null");
        }
        
        // out.collect(new TrendEvent(
        //     "MESSAGE_RECEIVED",
        //     message.getText(),
        //     ctx.getCurrentKey(), // or some other default key
        //     "" + ctx.timerService().currentWatermark()
        // ));

        // Use current processing time
        long timestamp = ctx.timerService().currentProcessingTime();
            
        
            // Process message and get results
            TrendDetector.ProcessingResult result = detector.processMessage(message, timestamp);
            
            // out.collect(new TrendEvent(
            //     "MESSAGE_PROCESSED",
            //     "trend-id",
            //     ctx.getCurrentKey(), // or some other default key
            //     "event-info"
            // ));

            // Emit events for new trends if any were detected
            if (result != null && result.getNewTrends() != null) {
                for (Trend trend : result.getNewTrends()) {
                    Map<String, Object> eventInfo = new HashMap<>();
                    eventInfo.put("keywords", trend.getKeywords());
                    
                    TrendEvent event = new TrendEvent(
                        "TREND_CREATED",
                        trend.getKeywords().toString(),
                        ctx.getCurrentKey(), // or some other default key
                        "" + ctx.timerService().currentWatermark()
                    );
                    
                    out.collect(event);
                }
            }
            
        
    }
    
    @Override
    public void close() throws Exception {
        if (detector != null) {
            detector = null;
        }
        super.close();
    }
}
package com.trendpulse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

class MyProcessor extends ProcessFunction<Integer, Integer> {

    private boolean print;
    public MyProcessor(boolean print) {
        this.print = print;
    }

    @Override
    public void processElement(Integer value, ProcessFunction<Integer, Integer>.Context ctx, Collector<Integer> out)
            throws Exception {
        if (print) {
            System.out.println("Regular: " + value);    
        }
        out.collect(value);
    }
    
}

class MyKeyedProcessor extends KeyedProcessFunction<Integer, Integer, Integer> {

    private boolean print;
    public MyKeyedProcessor(boolean print) {
        this.print = print;
    }
    
    @Override
    public void processElement(Integer value, KeyedProcessFunction<Integer, Integer, Integer>.Context ctx,
            Collector<Integer> out) throws Exception {
        if (print) {
            System.out.println("Keyed: " + value);    
        }
        out.collect(value);
    }

    
    
}

public class TrendTest {
    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        List<Integer> numbers = IntStream.rangeClosed(1, 100)
                                .boxed()
                                .collect(Collectors.toList());
        DataStream<Integer> input = env.fromCollection(numbers);

        input
            // .process(new MyProcessor(false))
            // .setParallelism(1)
            .keyBy(el -> 1)
            .process(new MyKeyedProcessor(true))
            // .setParallelism(1)
        ;

        env.execute("Trend Detection Job");

    }

}

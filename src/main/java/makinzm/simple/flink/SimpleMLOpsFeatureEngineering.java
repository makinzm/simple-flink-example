package makinzm.simple.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.TypeHint;

/**
 * Simple MLOps Feature Engineering with Apache Flink
 * 
 * このアプリケーションは、Kafkaからユーザーの特徴量データを読み込み、
 * ユーザー別に移動平均を計算するMLOps風の処理を実装しています。
 */
public class SimpleMLOpsFeatureEngineering {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Kafka Source の設定
        String inputTopic = "raw-features"; // Kafkaの入力トピック名
        String bootstrapServers = "localhost:9092"; // Docker Composeで起動したKafka

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("flink-mlops-group")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.earliest()) // 常に最初から読み込み
                .build();

        // 2. データストリームの処理
        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
            // 入力例: "user_A,10.5" -> Tuple2<String, Double>("user_A", 10.5) にパース
            .map(line -> {
                String[] parts = line.split(",");
                if (parts.length != 2) {
                    System.err.println("Invalid data format: " + line + " (expected: user_id,value)");
                    return null;
                }
                try {
                    return Tuple2.of(parts[0].trim(), Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException e) {
                    System.err.println("Invalid number format: " + parts[1] + " in line: " + line);
                    return null;
                }
            })
            .returns(TypeInformation.of(new TypeHint<Tuple2<String, Double>>(){}))
            // null値をフィルタリング
            .filter(tuple -> tuple != null)
            // KeyBy: ユーザーID (Tuple2の1番目の要素) でストリームを分割
            .keyBy(tuple -> tuple.f0) 
            // KeyedProcessFunction: キー（ユーザーID）ごとに状態を保持して処理
            .process(new MovingAverageFeatureFunction())
            // 結果を標準出力に表示 (ここでは特徴量ストアへの書き込みの代わり)
            .print();

        // Flink ジョブの実行
        env.execute("Simple Flink MLOps Feature Engineering Job");
    }

    /**
     * ユーザー別移動平均計算を行うKeyedProcessFunction
     * 
     * 各ユーザーの直前の特徴量値を状態として保持し、
     * 現在値との移動平均（直近2値の平均）を計算します。
     */
    private static class MovingAverageFeatureFunction 
            extends KeyedProcessFunction<String, Tuple2<String, Double>, String> {
        
        // Key（ユーザーID）ごとに直前の特徴量を保持するための状態
        private transient ValueState<Double> lastFeatureValue;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            // 状態のディスクリプタを定義
            ValueStateDescriptor<Double> descriptor = 
                    new ValueStateDescriptor<>("lastFeature", TypeInformation.of(Double.class));
            // ランタイムコンテキストから状態を取得
            lastFeatureValue = getRuntimeContext().getState(descriptor);
        }

        @Override
        public void processElement(Tuple2<String, Double> input, Context context, Collector<String> out) throws Exception {
            String userId = input.f0;
            double currentFeature = input.f1;
            
            Double previousFeature = lastFeatureValue.value();
            
            double movingAverage;

            if (previousFeature != null) {
                // 直前の値がある場合、移動平均を計算
                movingAverage = (currentFeature + previousFeature) / 2.0;
                // MLOpsでいう「新しい特徴量」を出力（ここではStringとして）
                String result = String.format("User: %s, Raw Feature: %.1f, Moving Average Feature: %.2f", 
                                                userId, currentFeature, movingAverage);
                out.collect(result);
            } else {
                // 初回の場合、移動平均は計算せず、メッセージを出力
                String result = String.format("User: %s, Raw Feature: %.1f, Moving Average Feature: (First data point)", 
                                                userId, currentFeature);
                out.collect(result);
            }

            // 現在の特徴量を次の処理のために状態に保存
            lastFeatureValue.update(currentFeature);
        }
    }
}
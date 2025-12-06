# Simple Flink Example

Apache FlinkとKafkaを使ったMLOps風ストリーム処理（ユーザー別移動平均計算）のサンプルです。

## 🚀 セットアップと実行

> **前提条件**: Dockerが別途インストールされている必要があります

以下のコマンドをコピー&ペーストで実行してください：

### 1. 環境構築とビルド

```bash
# devbox環境に入る
devbox shell

# Docker環境起動（事前にDockerのインストールが必要）
docker compose up -d

# アプリケーションビルド
mvn clean package

# Kafkaトピック作成
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic raw-features --partitions 1 --replication-factor 1
```

### 2. アプリケーション実行とテスト

```bash
# Flinkアプリケーションを起動（バックグラウンド実行）
java -cp target/simple-flink-example-1.0-SNAPSHOT.jar makinzm.simple.flink.SimpleMLOpsFeatureEngineering &

# テストデータを投入
echo -e "user_A,10.0\nuser_B,50.0\nuser_A,12.0\nuser_B,55.0\nuser_A,14.0" | docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic raw-features
```

> **注意**: 
> - `&` はプロセスをバックグラウンドで実行するシェル記号です
> - log4jの警告メッセージが表示されますが正常動作しています

### 3. 期待される出力結果

コンソールに以下のような出力が表示されます：

```
User: user_A, Raw Feature: 10.0, Moving Average Feature: (First data point)
User: user_B, Raw Feature: 50.0, Moving Average Feature: (First data point)  
User: user_A, Raw Feature: 12.0, Moving Average Feature: 11.00  # (10.0 + 12.0) / 2
User: user_B, Raw Feature: 55.0, Moving Average Feature: 52.50  # (50.0 + 55.0) / 2
User: user_A, Raw Feature: 14.0, Moving Average Feature: 13.00  # (12.0 + 14.0) / 2
```

## 🧹 クリーンアップ

```bash
# アプリケーション停止とKafka環境削除
pkill -f "SimpleMLOpsFeatureEngineering"
docker compose down -v
```


# Simple Flink Example

Apache FlinkとKafkaを使った簡単なMLOps風ストリーム処理のサンプルプロジェクトです。

## 🚀 開発環境の構築

### 1. DevboxとDockerの準備

```bash
devbox shell
```

> [!CAUTION]
> You have to install `docker` because devbox.json doesn't include it.

### 2. Kafkaの起動（Docker Compose使用）

このプロジェクトではKafka 4.1の最新KRaft（Kafka Raft）モードを使用します。
ZooKeeperは不要で、Kafka単体で動作します。

```bash
docker compose up -d
```

起動後、以下のサービスが利用可能になります：
- Kafka: `localhost:9092` （KRaftモード）

### 3. Kafkaトピックの作成

Flinkアプリケーション用のトピックを作成します：

```bash
# Kafkaコンテナに入る（最新のKRaftモード）
docker exec --workdir /opt/kafka/bin/ -it kafka sh

# 入力用トピック作成
./kafka-topics.sh --bootstrap-server localhost:9092 --create --topic raw-features --partitions 1 --replication-factor 1

# トピック一覧の確認
./kafka-topics.sh --bootstrap-server localhost:9092 --list

# コンテナから抜ける
exit
```

### 4. データ投入のテスト

Kafkaプロデューサーを使ってテストデータを投入できます：

```bash
# Kafkaコンテナに入る
docker exec --workdir /opt/kafka/bin/ -it kafka sh

# プロデューサーを起動（手動でデータを入力）
./kafka-console-producer.sh --bootstrap-server localhost:9092 --topic raw-features

# 以下のデータを一行ずつ入力してEnter
# user_A,10.0
# user_B,50.0
# user_A,12.0
# user_B,55.0
# Ctrl + C -> exit
```

### 5. データ消費のテスト

別のターミナルでコンシューマーを起動してデータを確認：

```bash
# Kafkaコンテナに入る
docker exec --workdir /opt/kafka/bin/ -it kafka sh

# Consumer groupを指定してコンシューマーを起動
./kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic raw-features --group test-consumer-group --from-beginning

# Consumer groupの状態確認
./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group test-consumer-group
```

## 💻 Flinkアプリケーションの開発

### 6. Mavenプロジェクトの構造

プロジェクトは以下の構造になっています：

```
simple-flink-example/
├── pom.xml                           # Maven設定ファイル
├── src/main/java/makinzm/simple/flink/
│   └── SimpleMLOpsFeatureEngineering.java  # Flinkアプリケーション
├── src/main/resources/
└── docker-compose.yaml              # Kafka環境設定
```

### 7. アプリケーションのビルドと実行

#### Java/Maven環境の確認
```bash
java -version   # Java 8以上が必要（当環境：Java 8対応済み）
mvn -version    # Maven 3.8.6以上推奨
```

#### 依存関係のダウンロードとコンパイル
```bash
mvn clean compile
```

#### Fat JARの作成
```bash
mvn clean package
```

作成されたJARファイル: `target/simple-flink-example-1.0-SNAPSHOT.jar`

#### Flinkアプリケーションの実行
```bash
java -cp target/simple-flink-example-1.0-SNAPSHOT.jar makinzm.simple.flink.SimpleMLOpsFeatureEngineering
```

> **注意**: log4jの警告メッセージが表示されますが、アプリケーションは正常動作しています

### 8. 完全な動作テスト手順

#### Step 1: Kafka環境を起動
```bash
docker compose up -d
```

#### Step 2: トピック作成
```bash
docker exec --workdir /opt/kafka/bin/ -it kafka sh
./kafka-topics.sh --bootstrap-server localhost:9092 --create --topic raw-features --partitions 1 --replication-factor 1
exit
```

#### Step 3: Flinkアプリケーションを起動（バックグラウンド）
```bash
java -cp target/simple-flink-example-1.0-SNAPSHOT.jar makinzm.simple.flink.SimpleMLOpsFeatureEngineering &
```

#### Step 4: テストデータ投入
```bash
docker exec --workdir /opt/kafka/bin/ -it kafka sh
./kafka-console-producer.sh --bootstrap-server localhost:9092 --topic raw-features

# 以下のデータを一行ずつ入力してEnter
user_A,10.0
user_B,50.0
user_A,12.0    # <- user_Aの移動平均が計算される
user_B,55.0    # <- user_Bの移動平均が計算される
user_A,14.0    # <- user_Aの新しい移動平均が計算される
# Ctrl+C で終了
```

#### Step 5: 結果確認
Flinkアプリケーションのコンソールに以下のような出力が表示されます：

```
User: user_A, Raw Feature: 10.0, Moving Average Feature: (First data point)
User: user_B, Raw Feature: 50.0, Moving Average Feature: (First data point)
User: user_A, Raw Feature: 12.0, Moving Average Feature: 11.00
User: user_B, Raw Feature: 55.0, Moving Average Feature: 52.50
User: user_A, Raw Feature: 14.0, Moving Average Feature: 13.00
```

### 9. アプリケーションの機能説明

- **状態管理**: ユーザー別に前回の特徴量値を保持
- **キーによる分割**: `keyBy()`でユーザーID別にストリームを分割
- **移動平均計算**: 直近2値の平均を計算（MLOps風特徴量エンジニアリング）
- **リアルタイム処理**: Kafkaからのデータを即座に処理

## 🧹 環境のクリーンアップ

作業終了後にKafka環境を停止・削除：

```bash
# Flinkアプリケーションの停止
pkill -f "makinzm.simple.flink.SimpleMLOpsFeatureEngineering"

# サービスの停止
docker compose down

# ボリュームも含めて完全削除
docker compose down -v
```


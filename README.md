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

## 🧹 環境のクリーンアップ

作業終了後にKafka環境を停止・削除：

```bash
# サービスの停止
docker compose down

# ボリュームも含めて完全削除
docker-compose down -v
```


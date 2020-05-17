# Containerized [Apache Kafka Connect](http://kafka.apache.org/documentation/#connect)

**tl;dr**: Use `./mvnw clean package` to build your container!

## Tutorial

The following tutorial for using Jib to package `ConnectDistributed` for Kafka Connect will require installation of `docker-compose`, and uses the [Bitnami](https://github.com/bitnami/bitnami-docker-kafka) Kafka+Zookeeper images, however any other Kafka or ZooKeeper Docker images should work. 

This tutorial will roughly follow the same steps as the [tutorial for Connect on Kafka's site](https://kafka.apache.org/documentation/#quickstart_kafkaconnect), except using the Distribtued Connect server instead. 

## Without Docker

If not using Docker, Kafka and ZooKeeper can be started locally using their respective start scripts. If this is done, though, the the variables for the bootstrap servers will need to be adjusted accordingly.  

The following steps can be used to run this application locally outside of Docker.  

```bash
export CONNECT_BOOTSTRAP_SERVERS=localhost:9092  # Assumes Kafka default port

export CONNECT_GROUP_ID: cg_connect-idea
export CONNECT_CONFIG_STORAGE_TOPIC: connect-jib_config
export CONNECT_OFFSET_STORAGE_TOPIC: connect-jib_offsets
export CONNECT_STATUS_STORAGE_TOPIC: connect-jib_status
# Cannot be higher than the number of brokers in the export Kafka cluster
export CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR: 1
export CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR: 1
export CONNECT_STATUS_STORAGE_REPLICATION_FACTOR: 1

# We're going to use ByteArrayConverter by default, and let individual connectors configure themselves
export CONNECT_KEY_CONVERTER: org.apache.kafka.connect.export converters.ByteArrayConverter
export CONNECT_VALUE_CONVERTER: org.apache.kafka.connect.converters.ByteArrayConverter

# Runs ConnectDistrbuted via Maven
./mvnw clean exec:java
```

### Start Kafka Cluster in Docker

> ***Note***: Sometimes the Kafka container kills itself in below steps, and the consumer commands therefore may need to be re-executed. The Streams Application should reconnect on its own. 

For this exercise, we will be using three separate termainal windows, so go ahead and open those. 

First, we start with getting our cluster running in the foreground. This starts Kafka listening on `9092` on the host, and `29092` within the Docker network. Zookeeper is available on `2181`.

> *Terminal 1*

```bash
docker-compose up zookeeper kafka
```

### Create Kafka Topics

We need to create the topics where data will be produced into. 

> *Terminal 2*

```bash
docker-compose exec kafka \
    bash -c "kafka-topics.sh --create --bootstrap-server kafka:29092 --topic input --partitions=1 --replication-factor=1"
```

Verify topics exist

```bash
docker-compose exec kafka \
    bash -c "kafka-topics.sh --list --bootstrap-server kafka:29092"
```

### Produce Lorem Ipsum into input topic

```bash
docker-compose exec kafka \
    bash -c "cat /data/lipsum.txt | kafka-console-producer.sh --topic input --broker-list kafka:9092"
```

Verify that data is there (note: hard-coding `max-messages` to the number of lines of expected text)

```bash
docker-compose exec kafka \
    bash -c "kafka-console-consumer.sh --topic input --bootstrap-server kafka:9092 --from-beginning --max-messages=9"
```

### Start Kafka Connect

Now, we can start Kafka Connect to read from the beginning of the input topic that had data sent into it, and begin processing it. 

```bash
./mvnw clean install

docker-compose up connect-jib
```

Wait for log-line `Kafka Connect Started`, then post the FileSink Connector, which when not provided a `file`, will output the stdout of the container (Terminal 1).

> *Terminal 3*

Use Kafka Connect REST API to start this process

```bash
curl -XPUT http://localhost:8083/connectors/console-sink/config -H 'Content-Type: application/json' -d '{ 
    "connector.class": "FileStreamSink",
    "tasks.max": 1,
    "topics": "input",
    "transforms": "MakeMap",
    "transforms.MakeMap.type": "org.apache.kafka.connect.transforms.HoistField$Value",
    "transforms.MakeMap.field" : "line",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
}'
```

In the output of _Terminal 2_, you should see something similar to the following.

```text
connect-jib_1  | Struct{line=Morbi eu pharetra dolor. ....}
connect-jib_1  | Struct{line=}
connect-jib_1  | Struct{line=Nullam mauris sapien, vestibulum ....}
```

This is the `toString()` representation of Kafka Connect's internal `Struct` class. Since we added a `HoistField$Value` transform, then there is a Structured Object with a field of `line` set to the value of the Kafka message that was read from the lines of the `lipsum.txt` file that was produced in the third step above. 

To repeat that process, we delete the connector and reset the consumer group.

```bash
curl -XDELETE http://localhost:8083/connectors/console-sink

docker-compose exec kafka \
    bash -c "kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group connect-console-sink --reset-offsets --all-topics --to-earliest --execute"

# re-run above 'curl -XPUT ...' command 
```

## Extra

Redo the tutorial with more input data and partitions, then play with `docker-compose scale` to add more Kafka Connect tasks in parallel.

### Extending with new Connectors

Connector plugins should preferably be placed into `/app/lib`, thus requiring an environment variable of `CONNECT_PLUGIN_PATH="/app/lib"`. 

## Maven Details 

The `exec:java` goal can be used to run Kafka Connect outside of Docker.

To rebuild the container, for example, run `./mvnw clean package`

### Disclaimer

`confluent-hub` is **not** part of this Container; it **only includes** Connector classes provided by Apache Kafka. Apache Kafka only comes with File Sink/Source and MirrorSource Connector (MirrorMaker 2.0). Therefore, think of this image as a base upon which you can [add your own Connectors](#extending-with-new-connectors). 

```bash
$ curl localhost:8083/connector-plugins | jq
[
  {
    "class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
    "type": "sink",
    "version": "2.5.0"
  },
  {
    "class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
    "type": "source",
    "version": "2.5.0"
  },
  {
    "class": "org.apache.kafka.connect.mirror.MirrorCheckpointConnector",
    "type": "source",
    "version": "1"
  },
  {
    "class": "org.apache.kafka.connect.mirror.MirrorHeartbeatConnector",
    "type": "source",
    "version": "1"
  },
  {
    "class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
    "type": "source",
    "version": "1"
  }
]
```

The File Source/Sink are **not** to be used in production, and is only really meant as a "simple, standalone example," [according to the docs](https://kafka.apache.org/documentation/#connect_developing) (emphasis added).

> A _simple **example**_ is included with the source code for Kafka in the `file` package. This connector is **_meant for use in standalone mode_**
> 
> ... 
> 
> [files] have trivially structured data -- each line is just a string. Almost **_all practical connectors_** will need schemas with more complex data formats

That being said, the MirrorSource would be a more real-world example 

## Cleanup environment

```bash
docker-compose rm -sf
# Clean up mounted docker volumes
docker volume ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker volume rm 
# Clean up networks
docker network ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker network rm
```

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [more about Apache Kafka & Kafka Connect](http://kafka.apache.org/documentation).

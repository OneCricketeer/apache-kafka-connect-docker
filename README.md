# Containerized [Apache Kafka Connect](http://kafka.apache.org/documentation/#connect)

<!-- Note: Version is listed in URL -->
[![Docker Image Version (tag latest semver)](https://img.shields.io/docker/v/cricketeerone/apache-kafka-connect/4.0.0?logo=docker&style=flat-square)](https://hub.docker.com/r/cricketeerone/apache-kafka-connect/tags)
[![Docker Image Size (latest semver)](https://img.shields.io/docker/image-size/cricketeerone/apache-kafka-connect/4.0.0?logo=docker&label=size&style=flat-square)](https://hub.docker.com/r/cricketeerone/apache-kafka-connect/tags)
[![Docker Pulls](https://img.shields.io/docker/pulls/cricketeerone/apache-kafka-connect?label=pulls&logo=docker&style=flat-square)](https://hub.docker.com/r/cricketeerone/apache-kafka-connect)

[![LICENSE](https://img.shields.io/github/license/OneCricketeer/apache-kafka-connect-docker?color=%23ce353d&logo=apache&style=flat-square)](https://github.com/OneCricketeer/apache-kafka-connect-docker/blob/master/LICENSE)

Using [GoogleContainerTools/Jib](https://github.com/GoogleContainerTools/jib) to package Apache Kafka Connect Distributed Server.

### FAQ 

#### Why this image over others?

This image is almost 10x **_smaller_** than popular Kafka Connect images. It only includes the Connect Runtime, no extra bloat!

#### When will version _X_ be available? 

The builds are automated. The version releases are not. If you find a version missing, feel free to submit a corresponding PR.

---

Docker Pull! 🐳

```sh
docker pull cricketeerone/apache-kafka-connect
```

The above image is enough for MirrorMaker2. There is also an image that includes `confluent-hub` for adding a majority of third-party connectors! See section [Extending with new Connectors](#extending-with-new-connectors) for full usage.

```sh
docker pull cricketeerone/apache-kafka-connect:latest-confluent-hub
```

Alpine variants are also available. Check [Docker Hub](https://hub.docker.com/r/cricketeerone/apache-kafka-connect/tags) for all tags and versions.

**Table of Contents**
- [Image Details](#image-details)
- [Build it locally](#build-it-locally)
- [Tutorial](#tutorial)
  - [Without Docker](#without-docker)
  - [Starting Kafka in Docker](#start-kafka-cluster-in-docker)
- Extra
  - [Scaling Up](#scaling-up)
  - [Scaling Out](#scaling-out)
- [Extending with new Connectors](#extending-with-new-connectors)
- [HTTP Authentication](#http-authentication)

## Image Details

Much like the `confluentinc/cp-kafka-connect` images, this container uses environment variables starting with `CONNECT_`, followed by the Kafka Connect Worker properties to be configured.

For example, these are the bare minimum variables necessary to get a Connect Distributed Server running, 
but assumes it is connected to Kafka cluster with at least three brokers (replication factor for the three Connect topics). 
Additional variables for replication factor of the three Connect topics can be added, as described below for 
testing against less than three brokers.

```txt
CONNECT_BOOTSTRAP_SERVERS
CONNECT_GROUP_ID
CONNECT_KEY_CONVERTER
CONNECT_VALUE_CONVERTER
CONNECT_CONFIG_STORAGE_TOPIC
CONNECT_OFFSET_STORAGE_TOPIC
CONNECT_STATUS_STORAGE_TOPIC
```

See [`docker-compose.yml`](docker-compose.yml) for a full example of these variables' usage with the container while connected to a Kafka broker.

## Build it locally

Looking to build your own image? **tl;dr** - Clone repo, and use `./mvnw clean compile jib:dockerBuild` or `MVN_BUILD_CMD='compile jib:dockerBuild' make` and you're done!

**Multi-platform builds (buildx)**

By default, with the above commands, an image will be built for a `linux/amd64` Ubuntu-based container.  
The following builds and pushes multi-platform images to your personal Docker Hub account via Docker Buildx.

```sh
BUILDX_PLATFORMS=linux/arm64,linux/amd64 DOCKER_USER=$(whoami) make
```

As of May 2023, Alpine variants of Eclipse Temurin Java 17 images do not support `arm64`.

## Push to a private registry

To push to a private Docker Registry, you'll need to `docker login` to that address. The following commands will push the `apache-kafka-connect` image to a Docker Registry under your local username.
Feel free to change `DOCKER_USER` to a custom repo name in the Registry.

```sh
$ docker login <registry-address> --username=$(whoami)

$ DOCKER_REGISTRY=<registry-address> DOCKER_USER=$(whoami) \
  make
```

## Tutorial

The following tutorial uses Jib to package `ConnectDistributed` class for running Kafka Connect Distributed mode workers. 
The following instructions use the [Bitnami](https://github.com/bitnami/bitnami-docker-kafka) Kafka images, however any other Kafka Docker images should work.

This tutorial will roughly follow the same steps as the [tutorial for Connect on Kafka's site](https://kafka.apache.org/documentation/#quickstart_kafkaconnect), 
except using the Distributed Connect server instead.

### Without Docker

If not using Docker, Kafka (and ZooKeeper, if not using Kraft) can be started locally using their respective start scripts. 
If this is done, though, the variables for the bootstrap servers will need to be adjusted accordingly.

The following steps can be used to run this application locally outside of Docker.

```bash
# Assumes Kafka default port
export CONNECT_BOOTSTRAP_SERVERS=127.0.0.1:9092

export CONNECT_GROUP_ID=cg_connect-jib
export CONNECT_CONFIG_STORAGE_TOPIC=connect-jib_config
export CONNECT_OFFSET_STORAGE_TOPIC=connect-jib_offsets
export CONNECT_STATUS_STORAGE_TOPIC=connect-jib_status
# Cannot be higher than the number of brokers in the export Kafka cluster
export CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR=1
export CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR=1
export CONNECT_STATUS_STORAGE_REPLICATION_FACTOR=1

# We're going to use ByteArrayConverter by default, and let individual connectors configure themselves
export CONNECT_KEY_CONVERTER=org.apache.kafka.connect.converters.ByteArrayConverter
export CONNECT_VALUE_CONVERTER=org.apache.kafka.connect.converters.ByteArrayConverter

# Runs ConnectDistributed via Maven
./mvnw clean exec:java
```

### Start Kafka Cluster in Docker

> ***Note***: Sometimes the Kafka container kills itself in below steps, and the consumer commands therefore may need to be re-executed. The Connect worker should reconnect on its own.

For this exercise, we will be using three separate terminal windows, so go ahead and open those.

First, we start with getting our cluster running in the foreground. This starts Kafka listening on `9092` on the host, and `29092` within the Docker network.

> *Terminal 1*

```bash
docker compose up kafka
```

### Create Kafka Topics

We need to create the topics where data will be produced into.

> *Terminal 2*

```bash
docker compose exec kafka \
    bash -c "kafka-topics.sh --create --bootstrap-server kafka:29092 --topic input --partitions=1 --replication-factor=1"
```

Verify topics exist

```bash
docker compose exec kafka \
    bash -c "kafka-topics.sh --list --bootstrap-server kafka:29092"
```

Should include `input` topic in the list.

### Produce Lorem Ipsum into input topic

```bash
docker compose exec kafka \
    bash -c "cat /data/lipsum.txt | kafka-console-producer.sh --topic input --broker-list kafka:29092"
```

Verify that data is there (note: hard-coding `max-messages` to the number of lines of expected text)

```bash
docker compose exec kafka \
    bash -c "kafka-console-consumer.sh --topic input --bootstrap-server kafka:29092 --from-beginning --max-messages=9"
```

Should see last line `Processed a total of 9 messages`.

### Start Kafka Connect

Now, we can build the Kafka Connect image and start it.

```bash
./mvnw clean install

docker compose up connect-jib-1
```

Wait for log-line `Kafka Connect Started`, then post the FileSink Connector. When not provided a `file`, the connector tasks will write data to the stdout of the container (Terminal 1).

> *Terminal 3*

Use Kafka Connect REST API to start this process

```bash
curl -XPUT http://localhost:8083/connectors/console-sink/config -H 'Content-Type: application/json' -d '{
    "connector.class": "FileStreamSink",
    "tasks.max": 1,
    "topics": "input",
    "transforms": "MakeMap,AddPartition",
    "transforms.MakeMap.type": "org.apache.kafka.connect.transforms.HoistField$Value",
    "transforms.MakeMap.field" : "line",
    "transforms.AddPartition.type": "org.apache.kafka.connect.transforms.InsertField$Value",
    "transforms.AddPartition.partition.field" : "partition!",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
}'
```

This will read from the beginning of the `input` topic that had data sent into it, and begin processing it.

In the output of _Terminal 2_, you should see something similar to the following.

```text
connect-jib_1  | Struct{line=Morbi eu pharetra dolor. ....,partition=0}
connect-jib_1  | Struct{line=,partition=0}
connect-jib_1  | Struct{line=Nullam mauris sapien, vestibulum ....,partition=0}
```

This is the `toString()` representation of Kafka Connect's internal `Struct` class. Since we added a `HoistField$Value` transform, 
then there is a Structured Object with a field of `line` set to the value of the Kafka message that was read from the lines of the `lipsum.txt` file that was produced in the third step above, 
as well as a `partition` field set to the consumed record partition. The topic was only created with one partition.

To repeat that process, we delete the connector and reset the consumer group.

```bash
curl -XDELETE http://localhost:8083/connectors/console-sink

docker compose exec kafka \
    bash -c "kafka-consumer-groups.sh --bootstrap-server kafka:29092 --group connect-console-sink --reset-offsets --all-topics --to-earliest --execute"
```

Re-run above console-producer and `curl -XPUT ...` command, but this time, there will be more than 9 total messages printed.

## Extra

### Scaling up

Redo the tutorial with a new topic having more than one partition. Produce more input data to it, then increase `max.tasks` of the connector. 
Notice that the `partition` field in the output may change (you may need to produce data multiple times to randomize the record batches).

### Scaling out

Scaling the workers will require adding another container with a unique `CONNECT_ADVERTISED_HOST_NAME` variable. I.e.

```yml
connect-jib-2:
    image:  *connect-image
    hostname: connect-jib-2
    depends_on:
        - kafka
    environment:
        <<: *connect-vars
        CONNECT_REST_ADVERTISED_HOST_NAME: connect-jib-2
```

A reverse proxy should be added in front of all instances. See an example using Traefik in [`docker-compose.cluster.yml`](./docker-compose.cluster.yml). 
It can be started via `docker compose -f docker-compose.cluster.yml up` and tested with `curl -H Host:connect-jib.docker.localhost http://127.0.0.1/`.

## Extending with new Connectors

> ***Disclaimer***  It is best to think of this image as a base upon which you can add your own Connectors. Below is the output of the default connector plugins, as provided by Apache Kafka project.

Connector plugins should preferably be placed into `/app/libs`, thus requiring an environment variable of `CONNECT_PLUGIN_PATH="/app/libs"`. Kafka Connect plugins are often distributed as Tarballs/ZIP/JAR files that need to be extracted or added to this path, via a volume mount or downloaded with `curl`.

When using the `confluent-hub` image tags, you can extend those images like so

```Dockerfile
FROM cricketeerone/apache-kafka-connect:latest-confluent-hub

# Example connector installation from Confluent Hub
RUN confluent-hub install --no-prompt \
    --component-dir /app/libs --worker-configs /app/resources/connect-distributed.properties -- \
    <connector-id>
```

Where `<connector-id>` is copied from one of the available sources on [Confluent Hub](https://www.confluent.io/hub/). 
There is no guarantee in compatibility with the Kafka Connect base version and any version of a plugin that you install.

To re-iterate, `confluent-hub` is **not** part of the base image versions; they **only include** Connector classes provided by Apache Kafka. 
These are limited to File Sink/Source and MirrorSource Connector (MirrorMaker 2.0). In general, you'll probably want to add your own Connectors, as above, rather than use this image by itself.

As of 3.6.0 release, the `confluent-hub` tags include `unzip` shell command for extracting other third-party connectors. <!-- hold-version -->

For a full example of adding plugins, and using the [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html), 
please [refer to the `schema-registry` branch](https://github.com/OneCricketeer/apache-kafka-connect-docker/blob/schema-registry/Dockerfile.schema-registry).

#### Default Plugins

```bash
$ curl localhost:8083/connector-plugins | jq
[
    {
        "class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
        "type": "sink",
        "version": "4.0.0"
    },
    {
        "class": "org.apache.kafka.connect.file.FileStreamSourceConnector",
        "type": "source",
        "version": "4.0.0"
    },
    {
        "class": "org.apache.kafka.connect.mirror.MirrorCheckpointConnector",
        "type": "source",
        "version": "4.0.0"
    },
    {
        "class": "org.apache.kafka.connect.mirror.MirrorHeartbeatConnector",
        "type": "source",
        "version": "4.0.0"
    },
    {
        "class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
        "type": "source",
        "version": "4.0.0"
    }
]
```

The File Source/Sink are **not** to be used in production, 
and is only really meant as a "simple, standalone example," [according to the docs](https://kafka.apache.org/documentation/#connect_developing) (emphasis added).

> A _simple **example**_ is included with the source code for Kafka in the `file` package. This connector is **_meant for use in standalone mode_**
>
> ...
>
> files have trivially structured data -- each line is just a string. Almost **_all practical connectors_** will need schemas with more complex data formats.

That being said, the MirrorSource would be a more real-world example

## HTTP Authentication

[Confluent documentation covers this for Basic Auth](https://docs.confluent.io/platform/current/security/basic-auth.html#kconnect-rest-api).

Create files

```shell
$ cat /tmp/connect-jaas.conf
KafkaConnect {
    org.apache.kafka.connect.rest.basic.auth.extension.PropertyFileLoginModule required
    file="/tmp/connect.password";
};
$ cat /tmp/connect.password  # add as many lines as needed
admin: OneCricketeer
```

Add environment variables and mounts (`JAVA_TOOL_OPTIONS` comes from Eclipse Temurin base image)

```yaml
    environment:
      ...
      # Auth
      CONNECT_REST_EXTENSION_CLASSES: org.apache.kafka.connect.rest.basic.auth.extension.BasicAuthSecurityRestExtension
      JAVA_TOOL_OPTIONS: "-Djava.security.auth.login.config=/app/connect-jaas.conf"
    volumes:
      # Auth
      - /tmp/connect-jaas.conf:/app/connect-jaas.conf:ro
      - /tmp/connect.password:/tmp/connect.password:ro
```

`docker compose up` and test it

```shell
$ curl -w'\n' http://localhost:8083
User cannot access the resource.
$ curl -w'\n' -uadmin:OneCricketeer http://localhost:8083
{"version":"4.0.0","commit":"60e845626d8a465a","kafka_cluster_id":"nA5eYC5WSrSHjaKgw1BpHg"}
```

## Maven Details

The `exec:java` goal can be used to run Kafka Connect outside of Docker.

To rebuild the container, for example, run `./mvnw clean install` or `make`.

## Cleanup environment

```bash
docker compose rm -sf
# Clean up mounted docker volumes
docker volume ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker volume rm
# Clean up networks
docker network ls | grep $(basename `pwd`) | awk '{print $2}' | xargs docker network rm
```

## More information

Learn [more about Jib](https://github.com/GoogleContainerTools/jib).

Learn [more about Apache Kafka & Kafka Connect](http://kafka.apache.org/documentation).

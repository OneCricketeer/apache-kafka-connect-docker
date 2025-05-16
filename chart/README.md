# Kafka Connect Helm Chart

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square&logo=helm) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square&logo=helm) ![AppVersion: 3.8.1](https://img.shields.io/badge/AppVersion-3.8.1-informational?style=flat-square&logo=docker)

A Helm chart for Apache Kafka Connect on Kubernetes

## Prerequisites
- Kubernetes 1.9.2+ (tested on 1.25)
- Helm 3+
- A healthy and accessible Kafka Cluster. (Tested with [Strimzi](https://strimzi.io/))

## Docker Image Source:
- [DockerHub -> cricketeerone](https://hub.docker.com/r/cricketeerone/apache-kafka-connect)

## Installing the Chart

### Install with an existing Kafka cluster

```sh
git clone https://github.com/OneCricketeer/apache-kafka-connect-docker.git
helm install --set bootstrapServers="PLAINTEXT://external.kafka:9092",groupId="connect-group" apache-kafka-connect-docker/chart
```

Or supply your own values file

```sh
helm install --values /path/to/custom-values.yaml
```

## Values

Refer [`values.yaml`](./values.yaml) for defaults.

### Required Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| bootstrapServers | string | `""` | Kafka cluster to communicate with. In the form of `PROTOCOL://fqdn.kafka:9092` |
| groupId | string | `""` |  |

### Configuration

In general, use `configurationOverrides` to modify the worker config. Here are the defaults.

See <https://kafka.apache.org/documentation/#connectconfigs>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| configurationOverrides."config.storage.replication.factor" | string | `"3"` |  |
| configurationOverrides."key.converter" | string | `"org.apache.kafka.connect.converters.ByteArrayConverter"` |  |
| configurationOverrides."offset.storage.replication.factor" | string | `"3"` |  |
| configurationOverrides."plugin.path" | string | `"/app/libs"` |  |
| configurationOverrides."status.storage.replication.factor" | string | `"3"` |  |
| configurationOverrides."value.converter" | string | `"org.apache.kafka.connect.converters.ByteArrayConverter"` |  |

Otherwise, there are a few other ways to supply configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| customEnv | object | `{}` | Environment variable mapping |
| envValueFrom | object | `{}` | Allows using `valueFrom`. Useful for K8s Downward API |
| envFromSecret | string | `""` | Allows using `secretRef` |
| envFromSecrets | list | `[]` | List option of `envFromSecret` |
| envRenderSecret | object | `{}` |  |

### JVM Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| heapOptions | string | `"-Xms256M -Xmx2G"` | JVM Heap Options |

Use `KAFKA_JMX_OPTS` or `KAFKA_OPTS` in `customEnv` to set arbitrary values.

### Utilization

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| replicaCount | int | `1` |  |
| resources | object | `{}` |  |

### Registry Mirrors

See <https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| global.imagePullSecrets | list | `[]` |  |

### Deployment

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| image.repository | string | `"cricketeerone/apache-kafka-connect"` |  |
| image.tag | string | `""` | Defaults to the Chart `appVersion` |
| image.pullPolicy | string | `"IfNotPresent"` |  |

### Monitoring

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| jmx.enabled | bool | `false` | Exposes Kafka Connect JMX port |
| jmx.port | int | `5555` | The port to expose |

See [JVM configuration](#jvm-configuration) for setting JMX settings.

### Metadata

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| fullnameOverride | string | `""` |  |
| nameOverride | string | `""` |  |
| podAnnotations | object | `{}` |  |

### Autoscaling

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| autoscaling.enabled | bool | `false` |  |
| autoscaling.maxReplicas | int | `10` |  |
| autoscaling.minReplicas | int | `1` |  |
| autoscaling.targetCPUUtilizationPercentage | int | `80` |  |

### Deployment Strategy

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| deploymentStrategy.type | string | `"RollingUpdate"` |  |

### Security

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| podSecurityContext | object | `{}` |  |
| securityContext | object | `{}` |  |

### Networking

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| service.port | int | `8083` |  |
| service.type | string | `"ClusterIP"` |  |

### RBAC

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.create | bool | `true` |  |
| serviceAccount.name | string | `""` |  |

### Placement

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| tolerations | list | `[]` |  |
| nodeSelector | object | `{}` |  |
| affinity | object | `{}` |  |

### Storage

This container is meant to be ephemeral. Only use this feature to mount config files or extensions.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| volumeMounts | string | `nil` |  |
| volumes | string | `nil` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)

package org.apache.kafka.connect.cli;

import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.connect.converters.ByteArrayConverter;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.runtime.distributed.DistributedConfig;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ConnectDistributedWrapperTest implements WithAssertions {

    @SuppressWarnings({"unused", "ConstantConditions"})
    @Test
    void connectEnvVarToProp_nullOrEmpty_throws() {
        final String ex = "Input cannot be null or empty";
        assertThatThrownBy(() -> {
            String prop = ConnectDistributedWrapper.connectEnvVarToProp(null);
        }).isInstanceOf(IllegalArgumentException.class).hasMessage(ex);

        assertThatThrownBy(() -> {
            String prop = ConnectDistributedWrapper.connectEnvVarToProp("");
        }).isInstanceOf(IllegalArgumentException.class).hasMessage(ex);
    }

    @SuppressWarnings("unused")
    @Test
    void connectEnvVarToProp_nonCONNECTShort_throws() {
        final String ex = "Input does not start with '" + ConnectDistributedWrapper.CONNECT_ENV_PREFIX + "'";
        String input = "kafka";
        assertThatThrownBy(() -> {
            String prop = ConnectDistributedWrapper.connectEnvVarToProp(input);
        }).isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith(ex);

        String input2 = ConnectDistributedWrapper.CONNECT_ENV_PREFIX;
        String ex2 = ex + " or does not define a property";
        assertThatThrownBy(() -> {
            String prop = ConnectDistributedWrapper.connectEnvVarToProp(input2);
        }).isInstanceOf(IllegalArgumentException.class).hasMessage(ex2);
    }

    private String propToConnectEnv(String prop) {
        return ConnectDistributedWrapper.CONNECT_ENV_PREFIX
                .concat(prop
                        .replace('.', '_')
                        .toUpperCase()
                );
    }

    @ParameterizedTest
    @MethodSource("workerConfigProvider")
    void connectEnvVarToProp_connectConfigs(String prop) {
        String input = propToConnectEnv(prop);
        assertThat(ConnectDistributedWrapper.connectEnvVarToProp(input))
                .isEqualTo(prop);
    }

    static Stream<String> workerConfigProvider() {
        return Stream.of(
                // Kafka connection details
                WorkerConfig.BOOTSTRAP_SERVERS_CONFIG,
                DistributedConfig.GROUP_ID_CONFIG,
                // REST server
                WorkerConfig.REST_ADVERTISED_LISTENER_CONFIG,
                WorkerConfig.LISTENERS_CONFIG,
                // Plugins
                WorkerConfig.PLUGIN_PATH_CONFIG,
                // Converters
                WorkerConfig.KEY_CONVERTER_CLASS_CONFIG,
                WorkerConfig.VALUE_CONVERTER_CLASS_CONFIG,
                WorkerConfig.HEADER_CONVERTER_CLASS_CONFIG,
                // Internal topics
                DistributedConfig.STATUS_STORAGE_TOPIC_CONFIG,
                DistributedConfig.CONFIG_STORAGE_REPLICATION_FACTOR_CONFIG,
                DistributedConfig.OFFSET_STORAGE_TOPIC_CONFIG,
                DistributedConfig.OFFSET_STORAGE_REPLICATION_FACTOR_CONFIG,
                DistributedConfig.STATUS_STORAGE_TOPIC_CONFIG,
                DistributedConfig.STATUS_STORAGE_REPLICATION_FACTOR_CONFIG
        );
    }

    @SuppressWarnings({"ConstantConditions", "unused"})
    @Test
    void createConnectProperties_throws() {
        final String ex = "Provided argument cannot be null or empty";
        assertThatThrownBy(() -> {
            final File propFile = ConnectDistributedWrapper.createConnectProperties(null);
        }).isInstanceOf(IllegalArgumentException.class).hasMessage(ex);
    }

    @Test
    void createConnectProperties_createsFile() throws IOException, URISyntaxException {
        Map<String, String> propMap = Stream.of(
                new AbstractMap.SimpleImmutableEntry<>(
                        DistributedConfig.BOOTSTRAP_SERVERS_CONFIG, DistributedConfig.BOOTSTRAP_SERVERS_DEFAULT),
                new AbstractMap.SimpleImmutableEntry<>(
                        DistributedConfig.GROUP_ID_CONFIG, "junit"),
                new AbstractMap.SimpleImmutableEntry<>(
                        DistributedConfig.KEY_CONVERTER_CLASS_CONFIG, ByteArrayConverter.class.getName()),
                new AbstractMap.SimpleImmutableEntry<>(
                        DistributedConfig.VALUE_CONVERTER_CLASS_CONFIG, ByteArrayConverter.class.getName())
        ).map(e -> new AbstractMap.SimpleImmutableEntry<>(propToConnectEnv(e.getKey()), e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (prev, next) -> next, HashMap::new));

        final File propFile = ConnectDistributedWrapper.createConnectProperties(propMap);
        assertThat(propFile.exists()).isTrue();
        final String fname = "connect-distributed";
        assertThat(propFile.getName())
                .startsWith("tmp-" + fname)
                .endsWith(".properties");
        final Properties properties = Utils.loadProps(propFile.getAbsolutePath());

        final URI expectedPropertiesURI = Objects.requireNonNull(getClass().getResource(
                "expected-" + fname + ".properties")).toURI();
        final File expectedPropertiesFile = new File(expectedPropertiesURI);
        final Properties expectedProperties = Utils.loadProps(expectedPropertiesFile.getAbsolutePath());

        assertThat(properties).containsExactlyEntriesOf(expectedProperties);
    }

}
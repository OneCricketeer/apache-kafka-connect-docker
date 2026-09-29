package org.apache.kafka.connect.cli;

/**
 * The Kafka Connect runtime mode that {@link ConnectWrapper} should launch.
 */
enum ConnectMode {
    DISTRIBUTED("connect-distributed"),
    STANDALONE("connect-standalone");

    private final String propertiesFileNamePrefix;

    ConnectMode(String propertiesFileNamePrefix) {
        this.propertiesFileNamePrefix = propertiesFileNamePrefix;
    }

    String propertiesFileNamePrefix() {
        return propertiesFileNamePrefix;
    }
}

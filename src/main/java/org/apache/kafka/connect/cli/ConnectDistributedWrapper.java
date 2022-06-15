package org.apache.kafka.connect.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Wrapper class for starting <code>connect-distributed</code> using <code>'CONNECT_'</code> properties from
 * {@link System#getenv()}
 **/
public class ConnectDistributedWrapper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectDistributedWrapper.class);

    /**
     * Environment variables for Kafka Connect properties start with 'CONNECT_',
     * then are upper-cased and separated with underscores instead of periods.
     */
    static final String CONNECT_ENV_PREFIX = "CONNECT_";

    /**
     * Predicate for filtering environment variables.
     */
    private static final Predicate<Map.Entry<String, ?>> CONNECT_ENV_FILTER =
            e -> e.getKey().startsWith(CONNECT_ENV_PREFIX);

    public static void main(final String[] args) {
        log.debug("Starting Connect Wrapper");
        final ConnectDistributedWrapper wrapper = new ConnectDistributedWrapper();
        Runtime.getRuntime().addShutdownHook(new Thread(wrapper::stop));
        wrapper.run();
    }

    /**
     * Take an environment variable starting with <code>'CONNECT_'</code> and convert it into a
     * {@link org.apache.kafka.connect.runtime.WorkerConfig} or
     * {@link org.apache.kafka.connect.runtime.ConnectorConfig} value.
     *
     * @param k An Environment variable key
     * @return A config value from {@link org.apache.kafka.connect.runtime.WorkerConfig}
     * or {@link org.apache.kafka.connect.runtime.ConnectorConfig}
     */
    static String connectEnvVarToProp(String k) {
        if (k == null || k.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        final int prefixLength = CONNECT_ENV_PREFIX.length();
        if (k.length() < prefixLength || k.equals(CONNECT_ENV_PREFIX)) {
            throw new IllegalArgumentException(String.format(
                "Input does not start with '%s' or does not define a property", CONNECT_ENV_PREFIX));
        }
        return k.toLowerCase().substring(prefixLength).replace('_', '.');
    }

    /**
     * Write all Environment variables starting with <code>'CONNECT_'</code> into a temporary property file to be
     * used with {@link ConnectDistributed}.
     *
     * @param env A Map containing key-value pairs. Any key's starting with 'CONNECT_' will end up in the output file.
     * @return A {@link File} instance to be used with {@link ConnectDistributed#main(String[])}
     * @throws IOException If the property file cannot be created.
     */
    static File createConnectProperties(Map<String, String> env) throws IOException {
        if (env == null || env.isEmpty()) {
            throw new IllegalArgumentException("Provided argument cannot be null or empty");
        }
        final File workerPropFile = File.createTempFile("tmp-connect-distributed", ".properties");
        workerPropFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(workerPropFile))) {
            log.trace("Writing Connect worker properties '{}'", workerPropFile.getAbsolutePath());
            env.entrySet()
                    .stream()
                    .filter(CONNECT_ENV_FILTER)
                    .map(e -> new AbstractMap.SimpleEntry<>(connectEnvVarToProp(e.getKey()), e.getValue()))
                    .forEach(e -> {
                        final String k = e.getKey();
                        final String v = e.getValue();
                        log.debug("{}={}", k, v);
                        pw.printf("%s=%s%n", k, v);
                    });
            pw.flush();
            log.trace("Connect worker properties written");
            return workerPropFile;
        }
    }

    @Override
    public void run() {
        try {
            ConnectDistributed.main(new String[]{createConnectProperties(System.getenv()).getAbsolutePath()});
        } catch (Exception e) {
            log.error("Error starting {}", ConnectDistributed.class.getSimpleName(), e);
        }
    }

    private void stop() {
        log.debug("Stopping Connect Wrapper");
    }
}

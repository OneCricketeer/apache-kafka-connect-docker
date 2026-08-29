package org.apache.kafka.connect.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

/**
 * Wrapper class for starting <code>connect-distributed</code> using <code>'CONNECT_'</code> properties from
 * {@link System#getenv()}
 **/
public class Entrypoint implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Entrypoint.class);

    /**
     * Environment variables for Kafka Connect properties start with 'CONNECT_',
     * then are upper-cased and separated with underscores instead of periods.
     */
    static final String CONNECT_ENV_PREFIX = "CONNECT_";

    /**
     * Environment variable switch that allows the container to run under a different runtime mode.
     */
    static final String RUNTIME_MODE = "RUNTIME_MODE";

    /**
     * Predicate for filtering environment variables.
     */
    private static final Predicate<Map.Entry<String, ?>> CONNECT_ENV_FILTER =
            new Predicate<Entry<String, ?>>() {

        final int requiredLen = CONNECT_ENV_PREFIX.length();

        @Override
        public boolean test(Entry<String, ?> e) {
            final String k = e.getKey();
            if (k.length() <= requiredLen) {
                return false;
            }
            return k.startsWith(CONNECT_ENV_PREFIX);
        }
    };

    public static void main(final String[] args) {
        log.debug("Starting Connect Wrapper");
        final Entrypoint wrapper = new Entrypoint();
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
     * used with Kafka Connect.
     *
     * @param env A Map containing key-value pairs. Any key's starting with 'CONNECT_' will end up in the output file.
     * @return A {@link File} instance to be used with Kafka Connect.
     * @throws IOException If the property file cannot be created.
     */
    static File createConnectProperties(Map<String, String> env) throws IOException {
        if (env == null || env.isEmpty()) {
            throw new IllegalArgumentException("Provided argument cannot be null or empty");
        }
        final File workerPropFile = File.createTempFile("tmp-connect", ".properties");
        workerPropFile.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(workerPropFile))) {
            log.trace("Writing Connect worker properties '{}'", workerPropFile.getAbsolutePath());
            env.entrySet()
                    .stream()
                    .filter(CONNECT_ENV_FILTER)
                    .forEach(e -> {
                        final String k = connectEnvVarToProp(e.getKey());
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
        Map<String, String> env = System.getenv();
        String mode = env.getOrDefault("RUNTIME_MODE", "distribued");
        if (!mode.matches("distributed|standalone")) {
            String ex = "RUNTIME_MODE must match one of [distributed, standalone]";
            log.error(ex);
            throw new IllegalArgumentException(ex);
        }
        Class<?> clz = null;
        try {
            final String[] args = new String[]{createConnectProperties(env).getAbsolutePath()};
            if (mode.equals("standalone")) {
                clz = ConnectStandalone.class;
                ConnectStandalone.main(args);
            } else {
                clz = ConnectDistributed.class;
                ConnectDistributed.main(args);
            }
        } catch (Exception e) {
            if (clz != null) {
                log.error("Error starting {}", clz.getSimpleName(), e);
            } else {
                log.error("Unable to execute entrypoint", e);
            }
        }
    }

    private void stop() {
        log.debug("Stopping Connect Wrapper");
    }
}

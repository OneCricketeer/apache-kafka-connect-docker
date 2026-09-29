package org.apache.kafka.connect.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Wrapper class for starting <code>connect-distributed</code> or <code>connect-standalone</code> using
 * <code>'CONNECT_'</code> properties from {@link System#getenv()}.
 * <p>
 * Pass <code>--standalone</code> as a CLI argument to run {@link ConnectStandalone} instead of the default
 * {@link ConnectDistributed}. Any other arguments are forwarded as-is (after the generated worker properties
 * file) to the underlying Connect entry point, e.g. connector config files for standalone mode.
 **/
public class ConnectWrapper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ConnectWrapper.class);

    /**
     * Environment variables for Kafka Connect properties start with 'CONNECT_',
     * then are upper-cased and separated with underscores instead of periods.
     */
    static final String CONNECT_ENV_PREFIX = "CONNECT_";

    static final String STANDALONE_FLAG = "--standalone";

    /**
     * Predicate for filtering environment variables.
     */
    private static final Predicate<Map.Entry<String, ?>> CONNECT_ENV_FILTER =
            e -> e.getKey().startsWith(CONNECT_ENV_PREFIX);

    private final ConnectMode mode;
    private final String[] extraArgs;

    ConnectWrapper(ConnectMode mode, String[] extraArgs) {
        this.mode = mode;
        this.extraArgs = extraArgs;
    }

    public static void main(final String[] args) {
        log.debug("Starting Connect Wrapper");
        final ConnectWrapper wrapper = parseArgs(args);
        Runtime.getRuntime().addShutdownHook(new Thread(wrapper::stop));
        wrapper.run();
    }

    /**
     * Parse CLI arguments into a {@link ConnectWrapper}. The <code>--standalone</code> flag selects
     * {@link ConnectMode#STANDALONE}; every other argument is kept, in order, to be forwarded to the underlying
     * Connect entry point (e.g. connector config files for standalone mode).
     */
    static ConnectWrapper parseArgs(final String[] args) {
        ConnectMode mode = ConnectMode.DISTRIBUTED;
        final List<String> remainingArgs = new ArrayList<>();
        for (String arg : args) {
            if (STANDALONE_FLAG.equals(arg)) {
                mode = ConnectMode.STANDALONE;
            } else {
                remainingArgs.add(arg);
            }
        }
        return new ConnectWrapper(mode, remainingArgs.toArray(new String[0]));
    }

    ConnectMode mode() {
        return mode;
    }

    String[] extraArgs() {
        return extraArgs;
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
     * used with {@link ConnectDistributed} or {@link ConnectStandalone}.
     *
     * @param env A Map containing key-value pairs. Any key's starting with 'CONNECT_' will end up in the output file.
     * @param mode The {@link ConnectMode} the property file is being generated for, used to name the temp file.
     * @return A {@link File} instance to be used with {@link ConnectDistributed#main(String[])} or
     * {@link ConnectStandalone#main(String[])}
     * @throws IOException If the property file cannot be created.
     */
    static File createConnectProperties(Map<String, String> env, ConnectMode mode) throws IOException {
        if (env == null || env.isEmpty()) {
            throw new IllegalArgumentException("Provided argument cannot be null or empty");
        }
        final File workerPropFile = File.createTempFile("tmp-" + mode.propertiesFileNamePrefix(), ".properties");
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
            final File workerPropFile = createConnectProperties(System.getenv(), mode);
            final String[] mainArgs = Stream.concat(
                            Stream.of(workerPropFile.getAbsolutePath()), Arrays.stream(extraArgs))
                    .toArray(String[]::new);
            log.debug("Starting Connect in {} mode with args {}", mode, Arrays.toString(mainArgs));
            switch (mode) {
                case STANDALONE -> ConnectStandalone.main(mainArgs);
                case DISTRIBUTED -> ConnectDistributed.main(mainArgs);
            }
        } catch (Exception e) {
            log.error("Error starting Connect in {} mode", mode, e);
        }
    }

    private void stop() {
        log.debug("Stopping Connect Wrapper");
    }
}

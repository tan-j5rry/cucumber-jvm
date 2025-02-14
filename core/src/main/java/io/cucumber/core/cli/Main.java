package io.cucumber.core.cli;

import io.cucumber.core.logging.Logger;
import io.cucumber.core.logging.LoggerFactory;
import io.cucumber.core.options.*;
import io.cucumber.core.runtime.Runtime;
import org.apiguardian.api.API;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cucumber Main. Runs Cucumber as a CLI.
 * <p>
 * Options can be provided in by (order of precedence):
 * <ol>
 * <li>Command line arguments</li>
 * <li>Properties from {@link System#getProperties()}</li>
 * <li>Properties from in {@link System#getenv()}</li>
 * <li>Properties from {@value Constants#CUCUMBER_PROPERTIES_FILE_NAME}</li>
 * </ol>
 * For available properties see {@link Constants}. For Command line options
 * {@link CommandlineOptions}.
 */
@API(status = API.Status.STABLE)
/*
 * Suppress false positive for PMD finding that Main class does not abide by its
 * Java class naming rules and/or standards, which is patently false. Since it
 * clear does. Suppress this error as false positive =>  The utility class name
 * 'Main' doesn't match '[A-Z][a-zA-Z0-9]+(Utils?|Helper)'
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String... argv) {

        // implement first pass at solution for issue identified in
        // https://github.com/cucumber/cucumber-jvm/issues/2307
        try {
            byte exitStatus = run(argv, Thread.currentThread().getContextClassLoader());
            System.exit(exitStatus);

            // swap out SSL for RuntimeException for now, until we can implement
            // the
            // appropriate test harness..
            // }catch(javax.net.ssl.SSLHandshakeException sslx) {
        } catch (RuntimeException rx) {
            // rx.printStackTrace();
            logger.debug(new Supplier<String>() {
                public String get() {
                    return rx.getMessage();
                }
            });
            System.getProperties().forEach((k, v) -> System.out.println(k + ":" + v));
        } // try-catch

    }// main

    /**
     * Launches the Cucumber-JVM command line.
     *
     * @param  argv runtime options. See details in the
     *              {@code cucumber.api.cli.Usage.txt} resource.
     * @return      0 if execution was successful, 1 if it was not (test
     *              failures)
     */
    public static byte run(String... argv) {
        return run(argv, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Launches the Cucumber-JVM command line.
     *
     * @param  argv        runtime options. See details in the
     *                     {@code cucumber.api.cli.Usage.txt} resource.
     * @param  classLoader classloader used to load the runtime
     * @return             0 if execution was successful, 1 if it was not (test
     *                     failures)
     */
    public static byte run(String[] argv, ClassLoader classLoader) {
        RuntimeOptions propertiesFileOptions = new CucumberPropertiesParser()
                .parse(CucumberProperties.fromPropertiesFile())
                .build();

        RuntimeOptions environmentOptions = new CucumberPropertiesParser()
                .parse(CucumberProperties.fromEnvironment())
                .build(propertiesFileOptions);

        RuntimeOptions systemOptions = new CucumberPropertiesParser()
                .parse(CucumberProperties.fromSystemProperties())
                .build(environmentOptions);

        CommandlineOptionsParser commandlineOptionsParser = new CommandlineOptionsParser(System.out);
        RuntimeOptions runtimeOptions = commandlineOptionsParser
                .parse(argv)
                .addDefaultGlueIfAbsent()
                .addDefaultFeaturePathIfAbsent()
                .addDefaultSummaryPrinterIfNotDisabled()
                .enablePublishPlugin()
                .build(systemOptions);

        Optional<Byte> exitStatus = commandlineOptionsParser.exitStatus();
        if (exitStatus.isPresent()) {
            return exitStatus.get();
        }

        final Runtime runtime = Runtime.builder()
                .withRuntimeOptions(runtimeOptions)
                .withClassLoader(() -> classLoader)
                .build();

        runtime.run();
        return runtime.exitStatus();
    }

}

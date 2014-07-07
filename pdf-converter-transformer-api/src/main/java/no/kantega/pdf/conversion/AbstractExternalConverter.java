package no.kantega.pdf.conversion;

import com.google.common.base.Joiner;
import no.kantega.pdf.throwables.ConverterAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractExternalConverter implements IExternalConverter {

    private static final Joiner ARGUMENT_JOINER = Joiner.on(' ');

    private final Logger logger;

    private final long processTimeout;
    private final File baseFolder;

    public AbstractExternalConverter(File baseFolder, long processTimeout, TimeUnit processTimeoutUnit) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.baseFolder = baseFolder;
        this.processTimeout = processTimeoutUnit.toMillis(processTimeout);
    }

    protected static String quote(String... args) {
        return String.format("\"%s\"", ARGUMENT_JOINER.join(args));
    }

    protected File getBaseFolder() {
        return baseFolder;
    }

    protected long getProcessTimeout() {
        return processTimeout;
    }

    protected ProcessExecutor makePresetProcessExecutor() {
        return new ProcessExecutor()
                .redirectOutputAsInfo(logger)
                .redirectErrorAsInfo(logger)
                .readOutput(true)
                .directory(getBaseFolder())
                .timeout(getProcessTimeout(), TimeUnit.MILLISECONDS)
                .exitValueAny();
    }

    protected int runNoArgumentScript(File script) {
        logger.trace("Execute no-argument script {}", script);
        try {
            // Do not kill this process on a JVM shut down! A script for e.g. shutting down MS Word
            // would typically be triggered from a shut down hook. Therefore, the shut down process
            // should never be killed during JVM shut down. In order to avoid an incomplete start up
            // procedure, start up processes will never be killed either.
            return makePresetProcessExecutor()
                    .command("cmd", "/C", quote(script.getAbsolutePath()))
                    .execute().exitValue();
        } catch (IOException e) {
            String message = String.format("Unable to run script: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        } catch (InterruptedException e) {
            String message = String.format("Thread responsible for running script was interrupted: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        } catch (TimeoutException e) {
            String message = String.format("Thread responsible for running script timed out: %s", script);
            logger.error(message, e);
            throw new ConverterAccessException(message, e);
        }
    }

    protected void tryDelete(File file) {
        if (!file.delete()) {
            logger.warn("Cannot delete file: {}", file);
        }
    }
}

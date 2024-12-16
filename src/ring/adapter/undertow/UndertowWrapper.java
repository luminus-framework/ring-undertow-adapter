package ring.adapter.undertow;

import io.undertow.Undertow;
import io.undertow.UndertowLogger;
import io.undertow.server.handlers.GracefulShutdownHandler;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.util.List;

/**
 * A wrapper for Undertow server that provides graceful shutdown capabilities
 * while maintaining all original Undertow functionality.
 */
public final class UndertowWrapper {
    private final Undertow undertow;
    private final GracefulShutdownHandler gracefulShutdown;
    private final Long shutdownTimeout;

    /**
     * Creates a new UndertowWrapper with graceful shutdown support.
     *
     * @param undertow The Undertow server instance to wrap
     * @param gracefulShutdown The graceful shutdown handler (optional)
     * @param shutdownTimeout Timeout in milliseconds for graceful shutdown (optional)
     * @throws IllegalArgumentException if undertow is null
     */
    public UndertowWrapper(Undertow undertow, GracefulShutdownHandler gracefulShutdown, Long shutdownTimeout) {
        if (undertow == null) {
            throw new IllegalArgumentException("Undertow instance cannot be null");
        }
        this.undertow = undertow;
        this.gracefulShutdown = gracefulShutdown;
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * Creates a new UndertowWrapper without graceful shutdown support.
     *
     * @param undertow The Undertow server instance to wrap
     * @throws IllegalArgumentException if undertow is null
     */
    public UndertowWrapper(Undertow undertow) {
        this(undertow, null, null);
    }

    public void stop() {
        if (gracefulShutdown != null) {
            try {
                // initiate graceful shutdown
                UndertowLogger.ROOT_LOGGER.info("Attempting graceful Undertow handler shutdown");
                gracefulShutdown.shutdown();
                if (shutdownTimeout != null) {
                    gracefulShutdown.awaitShutdown(shutdownTimeout);
                }
            } catch (Exception e) {
                if (shutdownTimeout != null) {
                    UndertowLogger.ROOT_LOGGER.errorf("Graceful shutdown timed out after %s, stopping Undertow", shutdownTimeout);
                }

                UndertowLogger.ROOT_LOGGER.error("Failed to gracefully shutdown, stopping Undertow");
            } finally {
                undertow.stop();
            }
        } else {
            undertow.stop();
        }
    }

    // exposed in case the original Undertow object is required
    public Undertow getUndertow() {
        return undertow;
    }

    public void start() {
        undertow.start();
    }

    public Xnio getXnio() {
        return undertow.getXnio();
    }

    public XnioWorker getWorker() {
        return undertow.getWorker();
    }

    public List<Undertow.ListenerInfo> getListenerInfo() {
        return undertow.getListenerInfo();
    }
}



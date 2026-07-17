import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Permite detener la simulación en curso desde la UI.
 * Se entrega a la Interfaz cuando se inicia la simulación.
 */
public final class SimulationHandle {
    private final ExecutorService executor;
    private final AtomicBoolean   stopRequested;

    SimulationHandle(ExecutorService executor, AtomicBoolean stopRequested) {
        this.executor      = executor;
        this.stopRequested = stopRequested;
    }

    public void stop() {
        stopRequested.set(true);
        executor.shutdownNow();
    }
}

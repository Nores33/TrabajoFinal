import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MazeAgent — hilo de exploración del laberinto.
 *
 * Sin IA: recorre por búsqueda en profundidad (DFS) con backtracking real.
 * Con IA: recorre de forma codiciosa (greedy, sin backtracking), siguiendo el
 * orden de prioridad evolucionado por el algoritmo genético.
 */
class MazeAgent implements Callable<Boolean> {

    // ── Configuración del agente (inmutable) ──
    private final int[][]              maze;
    private final Position             start;
    private final int                  agentId;
    private final SimulationListener   listener;
    private final AtomicBoolean        exitFound;
    private final AtomicBoolean        stopRequested;
    private final AtomicInteger        activeAgents;
    private final AtomicInteger        totalVisits;
    private final int[]                directionOrder;
    private final String               strategyName;
    private final int                  generation;
    private final double               fitness;
    private final boolean              aiEnabled;
    private final MetricsUpdater       metricsUpdater;
    private final int                  delay;

    // Estado interno del agente (no compartido)
    private final boolean[][]          visited;
    private int                        visitedCount;

    public MazeAgent(int[][] maze, Position start, int agentId, SimulationListener listener,
                     AtomicBoolean exitFound, AtomicBoolean stopRequested,
                     AtomicInteger activeAgents, AtomicInteger totalVisits,
                     int[] directionOrder, String strategyName,
                     int generation, double fitness, boolean aiEnabled,
                     MetricsUpdater metricsUpdater, int delay) {
        this.maze           = maze;
        this.start          = start;
        this.agentId        = agentId;
        this.listener       = listener;
        this.exitFound      = exitFound;
        this.stopRequested  = stopRequested;
        this.activeAgents   = activeAgents;
        this.totalVisits    = totalVisits;
        this.directionOrder = directionOrder;
        this.strategyName   = strategyName;
        this.generation     = generation;
        this.fitness        = fitness;
        this.aiEnabled      = aiEnabled;
        this.metricsUpdater = metricsUpdater;
        this.delay          = delay;
        this.visited        = new boolean[maze.length][maze[0].length];
    }

    // ── Ciclo de vida del agente ──

    @Override
    public Boolean call() {
        activeAgents.incrementAndGet();
        notifyMetrics();

        boolean found      = false;
        String  threadName = Thread.currentThread().getName();

        try {
            if (listener != null) {
                listener.onAgentStarted(agentId, threadName, start);
                listener.onLog("[" + threadName + "] Agente " + agentId
                        + " inicia en (" + start.row() + "," + start.col() + ")");
            }

            found = explore(start.row(), start.col());
            return found;

        } finally {
            activeAgents.decrementAndGet();
            if (listener != null) {
                listener.onAgentFinished(agentId, found, visitedCount);
            }
            notifyMetrics();
        }
    }

    // ── Algoritmos de exploración ──

    /**
     * Sin IA usa búsqueda en profundidad (DFS) con backtracking real;
     * con IA se sigue el orden de prioridad evolucionado (greedy, sin backtracking).
     */
    private boolean explore(int r, int c) {
        return aiEnabled ? exploreGreedy(r, c) : exploreDfs(r, c);
    }

    /**
     * Exploración por búsqueda en profundidad (DFS): si un vecino no lleva a la
     * salida, se descarta ese camino (backtracking) y se prueba el siguiente.
     */
    private boolean exploreDfs(int r, int c) {
        if (Thread.currentThread().isInterrupted() || stopRequested.get() || exitFound.get())
            return false;
        if (r < 0 || c < 0 || r >= maze.length || c >= maze[0].length)
            return false;
        if (maze[r][c] == 1 || visited[r][c])
            return false;

        visited[r][c] = true;
        visitedCount++;
        totalVisits.incrementAndGet();

        if (listener != null) {
            listener.onAgentVisited(agentId, new Position(r, c));
            listener.onLog("Agente " + agentId + " visita (" + r + "," + c + ")");
        }
        notifyMetrics();

        if (maze[r][c] == 9) {
            exitFound.set(true);
            if (listener != null) {
                listener.onLog("Agente " + agentId + " encuentra la salida");
            }
            return true;
        }

        // Pausa visual entre pasos
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Probar cada vecino; si ninguno llega a la salida, se hace backtracking
        return exploreDfs(r + 1, c)
                || exploreDfs(r - 1, c)
                || exploreDfs(r, c + 1)
                || exploreDfs(r, c - 1);
    }

    /**
     * Exploración codiciosa (greedy) sin backtracking.
     * Sigue el orden de prioridad estricto indicado por directionOrder.
     */
    private boolean exploreGreedy(int r, int c) {
        while (true) {
            if (Thread.currentThread().isInterrupted() || stopRequested.get() || exitFound.get())
                return false;
            if (r < 0 || c < 0 || r >= maze.length || c >= maze[0].length)
                return false;
            if (maze[r][c] == 1 || visited[r][c])
                return false;

            // Marcar visita
            visited[r][c] = true;
            visitedCount++;
            totalVisits.incrementAndGet();

            if (listener != null) {
                listener.onAgentVisited(agentId, new Position(r, c));
                listener.onLog("Agente " + agentId + " visita (" + r + "," + c + ")");
            }
            notifyMetrics();

            if (aiEnabled && listener != null) {
                listener.onAiDecision(agentId,
                        "Prioridad " + MazeAI.describeOrder(directionOrder) + " en (" + r + "," + c + ")",
                        generation, fitness);
            }

            // Comprobar si es la salida
            if (maze[r][c] == 9) {
                exitFound.set(true);
                if (listener != null) {
                    listener.onLog("Agente " + agentId + " encuentra la salida");
                }
                return true;
            }

            // Pausa visual entre pasos
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            // Expandir vecino en el orden de la estrategia
            boolean moved = false;
            for (int direction : directionOrder) {
                int nextRow = r + MazeAI.DIRECTIONS[direction][0];
                int nextCol = c + MazeAI.DIRECTIONS[direction][1];

                if (nextRow < 0 || nextCol < 0 || nextRow >= maze.length || nextCol >= maze[0].length) continue;
                if (maze[nextRow][nextCol] == 1 || visited[nextRow][nextCol]) continue;

                r = nextRow;
                c = nextCol;
                moved = true;
                break;
            }

            if (!moved) {
                if (listener != null) {
                    listener.onLog("Agente " + agentId + " se queda atascado en (" + r + "," + c + ")");
                }
                return false;
            }
        }
    }

    // ── Notificación de métricas ──

    /** Publica una foto instantánea de las métricas compartidas hacia la UI. */
    private void notifyMetrics() {
        if (metricsUpdater != null) metricsUpdater.update();
    }
}

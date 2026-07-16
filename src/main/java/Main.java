import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Application;

/**
 * Main — Núcleo de simulación concurrente del laberinto.
 *
 * Responsabilidades:
 *   - Definir el laberinto y los puntos de inicio.
 *   - Gestionar el ciclo de vida de la simulación (start / stop).
 *   - Coordinar los agentes concurrentes mediante ExecutorService.
 *   - Notificar eventos a la interfaz a través de SimulationListener.
 *   - Proveer un generador dinámico de laberintos.
 */
public class Main {

    // ─────────────────────────────────────────────
    //  Tipos auxiliares: dirección, posición
    // ─────────────────────────────────────────────

    /** Los cuatro movimientos posibles dentro del laberinto. */
    public enum Direction {
        UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1);

        private final int deltaRow;
        private final int deltaCol;

        Direction(int deltaRow, int deltaCol) {
            this.deltaRow = deltaRow;
            this.deltaCol = deltaCol;
        }

        public int deltaRow() { return deltaRow; }
        public int deltaCol() { return deltaCol; }
    }

    /** Coordenada inmutable (fila, columna) dentro del laberinto. */
    public static final class Position {
        private final int row;
        private final int col;

        public Position(int r, int c) { row = r; col = c; }

        public int row() { return row; }
        public int col() { return col; }

        @Override
        public String toString() { return "(" + row + "," + col + ")"; }
    }

    // ─────────────────────────────────────────────
    //  Laberinto y puntos de inicio
    // ─────────────────────────────────────────────

    /**
     * Matriz 15×15. Modificable en sus elementos internos para generación dinámica.
     *   0 = celda libre
     *   1 = pared
     *   9 = salida (esquina inferior derecha)
     */
    public static final int[][] MAZE = {
        {0,0,1,0,0,0,1,0,0,0,1,0,0,0,1},
        {1,0,1,0,1,0,1,0,1,0,1,0,1,0,1},
        {0,0,0,0,1,0,0,0,1,0,0,0,1,0,0},
        {0,1,1,0,1,1,1,0,1,1,1,0,1,1,0},
        {0,0,0,0,0,0,1,0,0,0,1,0,0,0,0},
        {1,1,1,1,1,0,1,1,1,0,1,1,1,1,0},
        {0,0,0,0,1,0,0,0,1,0,0,0,0,1,0},
        {0,1,1,0,1,1,1,0,1,1,1,1,0,1,0},
        {0,0,1,0,0,0,1,0,0,0,0,1,0,0,0},
        {1,0,1,1,1,0,1,1,1,1,0,1,1,1,0},
        {0,0,0,0,1,0,0,0,0,1,0,0,0,1,0},
        {0,1,1,0,1,1,1,1,0,1,1,1,0,1,0},
        {0,0,0,0,0,0,0,1,0,0,0,1,0,0,0},
        {1,1,1,1,1,1,0,1,1,1,0,1,1,1,0},
        {0,0,0,0,0,0,0,0,0,1,0,0,0,0,9}
    };

    /** Posiciones desde donde arranca cada agente. */
    public static final Position[] STARTS = {
        new Position(0, 0),
        new Position(0, 1),
        new Position(2, 0)
    };

    // ─────────────────────────────────────────────
    //  Generación dinámica de laberintos (DFS + Braiding)
    // ─────────────────────────────────────────────

    /**
     * Genera un nuevo laberinto aleatorio aseguranzo que sea resoluble desde todos los inicios.
     */
    public static void generateNewMaze(Random random) {
        do {
            // 1. Llenar todo de paredes (1)
            for (int r = 0; r < MAZE.length; r++) {
                for (int c = 0; c < MAZE[r].length; c++) {
                    MAZE[r][c] = 1;
                }
            }
            // 2. Carve desde (0, 0)
            carve(0, 0, random);

            // 3. Quitar algunas paredes aleatoriamente (braiding - 25% de probabilidad)
            for (int r = 1; r < MAZE.length - 1; r++) {
                for (int c = 1; c < MAZE[r].length - 1; c++) {
                    if (MAZE[r][c] == 1) {
                        if (random.nextDouble() < 0.25) {
                            MAZE[r][c] = 0;
                        }
                    }
                }
            }

            // 4. Asegurar salidas y entradas libres
            for (Position start : STARTS) {
                MAZE[start.row()][start.col()] = 0;
            }
            MAZE[MAZE.length - 1][MAZE[0].length - 1] = 9;

        } while (!isSolvable());
    }

    private static void carve(int r, int c, Random random) {
        MAZE[r][c] = 0;
        int[][] directions = {{0, 2}, {2, 0}, {0, -2}, {-2, 0}};
        
        // Mezclar direcciones (Fisher-Yates)
        for (int i = directions.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] temp = directions[i];
            directions[i] = directions[j];
            directions[j] = temp;
        }

        for (int[] d : directions) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr >= 0 && nr < MAZE.length && nc >= 0 && nc < MAZE[0].length) {
                if (MAZE[nr][nc] == 1) {
                    MAZE[r + d[0]/2][c + d[1]/2] = 0;
                    carve(nr, nc, random);
                }
            }
        }
    }

    public static boolean isSolvable() {
        for (Position start : STARTS) {
            if (!canReachExit(start.row(), start.col(), new boolean[MAZE.length][MAZE[0].length])) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReachExit(int r, int c, boolean[][] visited) {
        if (r < 0 || c < 0 || r >= MAZE.length || c >= MAZE[0].length) return false;
        if (MAZE[r][c] == 1 || visited[r][c]) return false;
        if (MAZE[r][c] == 9) return true;
        visited[r][c] = true;
        return canReachExit(r + 1, c, visited) ||
               canReachExit(r - 1, c, visited) ||
               canReachExit(r, c + 1, visited) ||
               canReachExit(r, c - 1, visited);
    }

    // ─────────────────────────────────────────────
    //  Contratos de comunicación (interfaces/records)
    // ─────────────────────────────────────────────

    /** Escucha eventos de la simulación para actualizar la UI. */
    public interface SimulationListener {
        void onLog(String message);
        void onAgentStarted(int agentId, String threadName, Position start);
        void onAgentVisited(int agentId, Position position);
        void onAgentFinished(int agentId, boolean found, int visitedCount);
        void onAiDecision(int agentId, String decision, int generation, double fitness);
        void onMetricsUpdated(MetricsSnapshot snapshot);
        void onSimulationFinished(SimulationSummary summary);
    }

    /** Foto instantánea de las métricas en un momento dado. */
    public record MetricsSnapshot(
            boolean aiEnabled,
            String  modeName,
            int     activeAgents,
            int     totalVisits,
            long    elapsedMillis,
            int     generation,
            double  fitness,
            double  throughput) {}

    /** Resumen final emitido al terminar la simulación. */
    public record SimulationSummary(
            boolean aiEnabled,
            String  modeName,
            boolean found,
            int     totalVisits,
            long    elapsedMillis,
            int     generation,
            double  fitness) {}

    /** Interfaz interna para notificar métricas desde dentro del agente. */
    private interface MetricsUpdater {
        void update();
    }

    // ─────────────────────────────────────────────
    //  Handle de simulación (control externo)
    // ─────────────────────────────────────────────

    /**
     * Permite detener la simulación en curso desde la UI.
     * Se entrega a la Interfaz cuando se inicia la simulación.
     */
    public static final class SimulationHandle {
        private final ExecutorService executor;
        private final AtomicBoolean   stopRequested;

        private SimulationHandle(ExecutorService executor, AtomicBoolean stopRequested) {
            this.executor      = executor;
            this.stopRequested = stopRequested;
        }

        public void stop() {
            stopRequested.set(true);
            executor.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────
    //  Agente concurrente
    // ─────────────────────────────────────────────

    /**
     * MazeAgent — hilo de exploración del laberinto.
     *
     * Cada agente recorre el laberinto de forma codiciosa (greedy, sin backtracking)
     * utilizando el orden de prioridad provisto por el algoritmo genético.
     */
    static class MazeAgent implements Callable<Boolean> {

        // Referencia al laberinto y configuración del agente
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

        /**
         * Exploración codiciosa (greedy) sin backtracking.
         * Sigue el orden de prioridad estricto indicado por directionOrder.
         */
        private boolean explore(int r, int c) {
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

        private void notifyMetrics() {
            if (metricsUpdater != null) metricsUpdater.update();
        }
    }

    // ─────────────────────────────────────────────
    //  Punto de entrada de la simulación
    // ─────────────────────────────────────────────

    /**
     * Inicia la simulación con tres agentes concurrentes usando la estrategia dada.
     */
    public static SimulationHandle startSimulation(MazeAI.Strategy strategy, int delay, boolean aiEnabled, SimulationListener listener) {
        ExecutorService executor      = Executors.newFixedThreadPool(3);
        AtomicBoolean   exitFound     = new AtomicBoolean(false);
        AtomicBoolean   stopRequested = new AtomicBoolean(false);
        AtomicInteger   activeAgents  = new AtomicInteger(0);
        AtomicInteger   totalVisits   = new AtomicInteger(0);
        long            startTime     = System.currentTimeMillis();

        if (listener != null) {
            listener.onLog((aiEnabled ? "Generación " + strategy.generation() : "Modo base")
                    + " | estrategia " + strategy.name()
                    + " | fitness "    + String.format("%.2f", strategy.fitness()));
        }

        // Actualizador de métricas compartido (lambda)
        MetricsUpdater metricsUpdater = () -> {
            if (listener != null) {
                long   elapsed    = System.currentTimeMillis() - startTime;
                double throughput = elapsed > 0 ? totalVisits.get() * 1000.0 / elapsed : 0.0;
                listener.onMetricsUpdated(new MetricsSnapshot(
                        aiEnabled,
                        strategy.name(),
                        activeAgents.get(),
                        totalVisits.get(),
                        elapsed,
                        strategy.generation(),
                        strategy.fitness(),
                        throughput));
            }
        };

        // Crear y lanzar los tres agentes
        MazeAgent agent1 = new MazeAgent(MAZE, STARTS[0], 1, listener, exitFound, stopRequested,
                activeAgents, totalVisits, strategy.directionOrder(), strategy.name(),
                strategy.generation(), strategy.fitness(), aiEnabled, metricsUpdater, delay);

        MazeAgent agent2 = new MazeAgent(MAZE, STARTS[1], 2, listener, exitFound, stopRequested,
                activeAgents, totalVisits, strategy.directionOrder(), strategy.name(),
                strategy.generation(), strategy.fitness(), aiEnabled, metricsUpdater, delay);

        MazeAgent agent3 = new MazeAgent(MAZE, STARTS[2], 3, listener, exitFound, stopRequested,
                activeAgents, totalVisits, strategy.directionOrder(), strategy.name(),
                strategy.generation(), strategy.fitness(), aiEnabled, metricsUpdater, delay);

        List<Future<Boolean>> futures = new ArrayList<>();
        futures.add(executor.submit(agent1));
        futures.add(executor.submit(agent2));
        futures.add(executor.submit(agent3));

        // Hilo monitor: espera a que terminen todos y emite el resumen final
        Thread waiter = new Thread(() -> {
            boolean found = false;
            try {
                for (Future<Boolean> future : futures) {
                    try {
                        found |= future.get();
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                executor.shutdownNow();
                if (listener != null) {
                    listener.onSimulationFinished(new SimulationSummary(
                            aiEnabled,
                            strategy.name(),
                            found || exitFound.get(),
                            totalVisits.get(),
                            System.currentTimeMillis() - startTime,
                            strategy.generation(),
                            strategy.fitness()));
                }
            }
        }, "maze-waiter");

        waiter.setDaemon(true);
        waiter.start();

        return new SimulationHandle(executor, stopRequested);
    }

    // ─────────────────────────────────────────────
    //  main — lanza la UI JavaFX
    // ─────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        Application.launch(Interfaz.class, args);
    }
}

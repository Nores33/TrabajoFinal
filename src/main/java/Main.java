import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
 *
 * Las clases y contratos auxiliares (Position, Direction, SimulationListener,
 * MetricsSnapshot, SimulationSummary, SimulationHandle, MetricsUpdater y
 * MazeAgent) viven cada uno en su propio archivo dentro del mismo paquete.
 */
public class Main {

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
     * Genera un nuevo laberinto aleatorio asegurando que sea resoluble desde todos los inicios.
     * Algoritmo: tala de pasajes (DFS aleatorizado) + braiding + verificación de solubilidad.
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

    /** Tala pasajes recursivamente desde (r, c) — DFS aleatorizado clásico de generación de laberintos. */
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

    /** Verifica que la salida sea alcanzable desde los tres puntos de inicio. */
    public static boolean isSolvable() {
        for (Position start : STARTS) {
            if (!canReachExit(start.row(), start.col(), new boolean[MAZE.length][MAZE[0].length])) {
                return false;
            }
        }
        return true;
    }

    /** DFS recursivo de solo lectura (sin visualización) usado para validar solubilidad. */
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
    //  Punto de entrada de la simulación
    // ─────────────────────────────────────────────

    /**
     * Inicia la simulación con tres agentes concurrentes usando la estrategia dada.
     */
    public static SimulationHandle startSimulation(MazeAI.Strategy strategy, int delay, boolean aiEnabled, SimulationListener listener) {
        // Estado compartido entre los 3 agentes concurrentes de esta corrida
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MazeAI — Lógica del algoritmo genético (GA).
 *
 * Responsabilidades:
 *   - Definir y entrenar estrategias de dirección mediante GA de forma interactiva.
 *   - Evaluar el fitness de cada individuo simulando su recorrido.
 *   - Proveer la estrategia base (sin IA) como punto de comparación.
 */
public final class MazeAI {

    // ─────────────────────────────────────────────
    //  Constantes
    // ─────────────────────────────────────────────

    /** Deltas de movimiento: UP, DOWN, LEFT, RIGHT (índices 0-3). */
    public static final int[][] DIRECTIONS = {
            {-1, 0},
            {1, 0},
            {0, -1},
            {0, 1}
    };

    /** Mapeo índice → dirección tipada, usado para describir órdenes. */
    private static final Main.Direction[] ORDER = {
            Main.Direction.UP,
            Main.Direction.DOWN,
            Main.Direction.LEFT,
            Main.Direction.RIGHT
    };

    /** Clase de utilidad — no instanciable. */
    private MazeAI() {}

    // ─────────────────────────────────────────────
    //  Tipo de datos: Strategy
    // ─────────────────────────────────────────────

    /**
     * Resultado del entrenamiento o de la línea base.
     *
     * @param name           nombre descriptivo de la estrategia
     * @param generation     generación del GA en que se obtuvo
     * @param fitness        puntuación de la función de evaluación
     * @param directionOrder permutación de índices [0-3] que define la prioridad de movimiento
     */
    public record Strategy(String name, int generation, double fitness, int[] directionOrder) {
        public Strategy {
            directionOrder = directionOrder.clone();
        }
    }

    // ─────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────

    /** Estrategia fija sin entrenamiento, usada en modo "sin IA". */
    public static Strategy baseline() {
        return new Strategy("Base", 0, 0.0, new int[]{1, 3, 2, 0});
    }

    /**
     * Inicializa una población de permutaciones de tamaño `size` de manera aleatoria y sin duplicados.
     */
    public static List<int[]> initializePopulation(int size, Random random) {
        List<int[]> population = new ArrayList<>();
        while (population.size() < size) {
            int[] order = {0, 1, 2, 3};
            // Mezclar (Fisher-Yates)
            for (int i = order.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int temp = order[i];
                order[i] = order[j];
                order[j] = temp;
            }
            // Verificar si ya existe en la población
            boolean exists = false;
            for (int[] existing : population) {
                if (java.util.Arrays.equals(existing, order)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                population.add(order);
            }
        }
        return population;
    }

    /**
     * Evoluciona la población a la siguiente generación mediante ordenamiento de fitness,
     * elitismo, crossover y mutación.
     */
    public static List<int[]> evolve(List<int[]> population, int[][] maze, Main.Position[] starts, Random random) {
        // Ordenar población de mayor a menor fitness
        population.sort((left, right) -> Double.compare(
                evaluate(right, maze, starts),
                evaluate(left,  maze, starts)));

        List<int[]> nextGeneration = new ArrayList<>();
        // Elitismo: los dos mejores pasan directamente
        nextGeneration.add(population.get(0).clone());
        nextGeneration.add(population.get(1).clone());

        // Crossover + mutación hasta completar la población
        while (nextGeneration.size() < population.size()) {
            int[] child = crossover(
                    population.get(random.nextInt(2)),
                    population.get(random.nextInt(2)),
                    random);
            mutate(child, random);
            nextGeneration.add(child);
        }

        return nextGeneration;
    }

    /**
     * Devuelve la mejor estrategia de la población actual.
     */
    public static Strategy getBestStrategy(List<int[]> population, int[][] maze, Main.Position[] starts, int generation) {
        // Ordenar primero para garantizar que el mejor está en el índice 0
        population.sort((left, right) -> Double.compare(
                evaluate(right, maze, starts),
                evaluate(left,  maze, starts)));
        
        int[] bestOrder = population.get(0).clone();
        double bestFitness = evaluate(bestOrder, maze, starts);

        return new Strategy("Heurística adaptativa", generation, bestFitness, bestOrder);
    }

    /**
     * Devuelve una cadena legible del orden de direcciones,
     * por ejemplo: "DOWN -> RIGHT -> LEFT -> UP".
     */
    public static String describeOrder(int[] directionOrder) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < directionOrder.length; i++) {
            if (i > 0) builder.append(" -> ");
            builder.append(ORDER[directionOrder[i]].name());
        }
        return builder.toString();
    }

    // ─────────────────────────────────────────────
    //  Lógica interna del GA
    // ─────────────────────────────────────────────

    /**
     * Fitness promedio de un individuo evaluado desde todos los puntos de inicio.
     */
    public static double evaluate(int[] order, int[][] maze, Main.Position[] starts) {
        double total = 0.0;
        for (Main.Position start : starts) {
            total += simulate(order, maze, start);
        }
        return total / starts.length;
    }

    /**
     * Simula el recorrido greedy (sin backtracking) de un individuo
     * desde un punto de inicio y devuelve su puntuación.
     */
    private static double simulate(int[] order, int[][] maze, Main.Position start) {
        boolean[][] visited = new boolean[maze.length][maze[0].length];
        int    row   = start.row();
        int    col   = start.col();
        double score = 0.0;

        for (int step = 0; step < maze.length * maze[0].length; step++) {

            // Validaciones de estado actual
            if (row < 0 || col < 0 || row >= maze.length || col >= maze[0].length) { score -= 20; break; }
            if (maze[row][col] == 1) { score -= 15; break; }
            if (maze[row][col] == 9) { score += 150; break; }
            if (visited[row][col])   { score -= 3;  break; }

            visited[row][col] = true;
            score += 2;

            // Intentar mover en el orden del individuo
            boolean moved = false;
            for (int directionIndex : order) {
                int nextRow = row + DIRECTIONS[directionIndex][0];
                int nextCol = col + DIRECTIONS[directionIndex][1];

                if (nextRow < 0 || nextCol < 0 || nextRow >= maze.length || nextCol >= maze[0].length) continue;
                if (maze[nextRow][nextCol] == 1 || visited[nextRow][nextCol]) continue;

                row   = nextRow;
                col   = nextCol;
                moved = true;
                score += 1;
                break;
            }

            if (!moved) { score -= 8; break; }
        }

        // Penalidad por distancia final a la salida
        int exitRow = maze.length - 1;
        int exitCol = maze[0].length - 1;
        score -= Math.abs(exitRow - row) + Math.abs(exitCol - col);
        return score;
    }

    /**
     * Crossover de orden preservado (OX):
     * copia el segmento [0, cutPoint) del padre A
     * y completa con los genes restantes en el orden del padre B.
     */
    private static int[] crossover(int[] parentA, int[] parentB, Random random) {
        int     cutPoint = 1 + random.nextInt(parentA.length - 1);
        int[]   child    = new int[parentA.length];
        boolean[] used   = new boolean[parentA.length];

        for (int i = 0; i < cutPoint; i++) {
            child[i]       = parentA[i];
            used[child[i]] = true;
        }

        int childIndex = cutPoint;
        for (int gene : parentB) {
            if (!used[gene]) {
                child[childIndex++] = gene;
                used[gene]          = true;
            }
        }
        return child;
    }

    /**
     * Mutación por intercambio (swap) con probabilidad 30%.
     */
    private static void mutate(int[] order, Random random) {
        if (random.nextDouble() > 0.3) return;

        int first  = random.nextInt(order.length);
        int second = random.nextInt(order.length);
        int temp   = order[first];
        order[first]  = order[second];
        order[second] = temp;
    }
}

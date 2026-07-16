import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interfaz — Capa de presentación JavaFX simplificada y optimizada.
 *
 * Responsabilidades:
 *   - Construir la interfaz de usuario con controles de simulación dinámicos.
 *   - Soportar generación dinámica de laberintos.
 *   - Implementar el bucle visual de evolución interactivo para el AG.
 */
public class Interfaz extends Application {

    // ─────────────────────────────────────────────
    //  Constantes de diseño
    // ─────────────────────────────────────────────

    private static final int CELL_SIZE = 32;

    private static final Color[] AGENT_COLORS = {
            Color.web("#e63946"),  // Agente 1 — rojo
            Color.web("#457b9d"),  // Agente 2 — azul
            Color.web("#2a9d8f")   // Agente 3 — verde
    };

    // ─────────────────────────────────────────────
    //  Estado de la UI
    // ─────────────────────────────────────────────

    // Grilla del laberinto
    private final Rectangle[][] cells = new Rectangle[Main.MAZE.length][Main.MAZE[0].length];

    // Marcadores de posición de cada agente sobre la grilla
    private final Map<Integer, Circle> agents = new HashMap<>();

    // Contadores compartidos
    private final AtomicInteger totalVisits = new AtomicInteger();

    // ─────────────────────────────────────────────
    //  Componentes de la UI
    // ─────────────────────────────────────────────

    private Label statusLabel;
    private Label modeLabel;
    private Label generationLabel;
    private Label strategyLabel;
    private Label stepsLabel;
    private Label activeLabel;
    private Label elapsedLabel;
    private TextArea logArea;
    private CheckBox aiToggle;
    private Button startButton;
    private Button stopButton;
    private Button generateButton;
    private Slider speedSlider;

    // ─────────────────────────────────────────────
    //  Estado de la simulación y GA en curso
    // ─────────────────────────────────────────────

    private volatile Main.SimulationHandle currentHandle;
    private volatile boolean stopRequestedByUser;

    private final Random random = new Random();
    private List<int[]> currentPopulation;
    private int currentGeneration;
    private MazeAI.Strategy currentStrategy;

    // ─────────────────────────────────────────────
    //  Punto de entrada JavaFX
    // ─────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f172a, #111827);");

        // ── Top: título ──
        VBox top = new VBox(2, buildTitle());
        top.setPadding(new Insets(0, 0, 14, 0));
        root.setTop(top);

        // ── Centro: laberinto + panel lateral ──
        GridPane mazeGrid = buildMazeGrid();
        ScrollPane mazeScroll = new ScrollPane(mazeGrid);
        mazeScroll.setFitToWidth(true);
        mazeScroll.setFitToHeight(true);
        mazeScroll.setPannable(true);
        mazeScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox sidePanel = buildSidePanel();
        HBox content = new HBox(16, mazeScroll, sidePanel);
        HBox.setHgrow(mazeScroll, Priority.ALWAYS);
        root.setCenter(content);

        Scene scene = new Scene(root, 1080, 680);
        stage.setTitle("Laberinto Concurrente - Aprendizaje Evolutivo");
        stage.setScene(scene);
        stage.show();

        resetBoard();
    }

    private VBox buildTitle() {
        Label title = new Label("Laberinto Concurrente");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Exploración concurrente con entrenamiento de algoritmo genético en vivo.");
        subtitle.setTextFill(Color.web("#cbd5e1"));

        return new VBox(2, title, subtitle);
    }

    private VBox buildSidePanel() {
        // ── Panel de Controles ──
        startButton = new Button("Iniciar");
        startButton.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold;");
        startButton.setOnAction(event -> startSimulationFlow());

        stopButton = new Button("Detener");
        stopButton.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
        stopButton.setDisable(true);
        stopButton.setOnAction(event -> stopSimulation());

        generateButton = new Button("Generar Laberinto");
        generateButton.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        generateButton.setOnAction(event -> generateNewMaze());

        aiToggle = new CheckBox("IA Activa (AG)");
        aiToggle.setSelected(true);
        aiToggle.setTextFill(Color.WHITE);

        HBox buttonBox = new HBox(10, startButton, stopButton, generateButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        // Slider de velocidad
        Label speedLabel = new Label("Velocidad de paso (ms):");
        speedLabel.setTextFill(Color.web("#cbd5e1"));
        speedSlider = new Slider(10, 300, 90);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        Label speedValLabel = new Label("90 ms");
        speedValLabel.setTextFill(Color.WHITE);
        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> 
            speedValLabel.setText(newVal.intValue() + " ms")
        );
        HBox speedBox = new HBox(10, speedSlider, speedValLabel);
        speedBox.setAlignment(Pos.CENTER_LEFT);

        VBox controlsCard = new VBox(10,
                header("Controles"),
                buttonBox,
                aiToggle,
                speedLabel,
                speedBox
        );
        controlsCard.setPadding(new Insets(12));
        controlsCard.setStyle("-fx-background-color: rgba(30, 41, 59, 0.6); -fx-background-radius: 12;");

        // ── Panel de Métricas Simplificado ──
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(10);
        metricsGrid.setVgap(8);
        metricsGrid.setPadding(new Insets(10));

        metricsGrid.add(metricLabel("Modo:"), 0, 0);
        metricsGrid.add(modeLabel = metricValue("-"), 1, 0);

        metricsGrid.add(metricLabel("Generación:"), 0, 1);
        metricsGrid.add(generationLabel = metricValue("0"), 1, 1);

        metricsGrid.add(metricLabel("Estrategia:"), 0, 2);
        metricsGrid.add(strategyLabel = metricValue("-"), 1, 2);

        metricsGrid.add(metricLabel("Pasos Totales:"), 0, 3);
        metricsGrid.add(stepsLabel = metricValue("0"), 1, 3);

        metricsGrid.add(metricLabel("Agentes Activos:"), 0, 4);
        metricsGrid.add(activeLabel = metricValue("0"), 1, 4);

        metricsGrid.add(metricLabel("Tiempo Transcurrido:"), 0, 5);
        metricsGrid.add(elapsedLabel = metricValue("0 ms"), 1, 5);

        statusLabel = new Label("Listo");
        statusLabel.setTextFill(Color.web("#38bdf8"));
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        VBox metricsCard = new VBox(8,
                header("Métricas en Vivo"),
                metricsGrid,
                new HBox(8, metricLabel("Estado:"), statusLabel)
        );
        metricsCard.setPadding(new Insets(12));
        metricsCard.setStyle("-fx-background-color: rgba(30, 41, 59, 0.6); -fx-background-radius: 12;");

        // ── Log de Eventos ──
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(7);
        logArea.setStyle("-fx-control-inner-background: #0b1220; -fx-text-fill: #e2e8f0; -fx-font-family: monospace;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox panel = new VBox(12,
                controlsCard,
                metricsCard,
                header("Eventos de Simulación"),
                logArea
        );
        panel.setPrefWidth(380);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.84); -fx-background-radius: 18;");
        return panel;
    }

    private Label metricLabel(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#94a3b8"));
        return l;
    }

    private Label metricValue(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.WHITE);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private GridPane buildMazeGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(8));
        grid.setStyle("-fx-background-color: rgba(15, 23, 42, 0.92); -fx-background-radius: 18;");

        // Celdas del laberinto
        for (int row = 0; row < Main.MAZE.length; row++) {
            for (int col = 0; col < Main.MAZE[row].length; col++) {
                Rectangle cell = new Rectangle(CELL_SIZE, CELL_SIZE);
                cell.setArcHeight(10);
                cell.setArcWidth(10);
                cell.setStroke(Color.web("#334155"));
                cell.setFill(colorForCell(Main.MAZE[row][col]));

                cells[row][col] = cell;
                StackPane wrapper = new StackPane(cell);
                wrapper.setAlignment(Pos.CENTER);
                grid.add(wrapper, col, row);
            }
        }

        // Marcadores iniciales de cada agente
        for (int i = 0; i < Main.STARTS.length; i++) {
            Circle marker = new Circle(6, AGENT_COLORS[i]);
            marker.setStroke(Color.WHITE);
            marker.setStrokeWidth(1.2);
            agents.put(i + 1, marker);

            Main.Position start = Main.STARTS[i];
            GridPane.setColumnIndex(marker, start.col());
            GridPane.setRowIndex(marker, start.row());
            grid.getChildren().add(marker);
        }

        return grid;
    }

    // ─────────────────────────────────────────────
    //  Control y Flujo del AG en Vivo
    // ─────────────────────────────────────────────

    private void generateNewMaze() {
        appendLog("Generando nuevo laberinto...");
        Main.generateNewMaze(random);
        resetBoard();
        appendLog("¡Nuevo laberinto listo y validado como resoluble!");
    }

    private void startSimulationFlow() {
        if (currentHandle != null) currentHandle.stop();

        stopRequestedByUser = false;
        startButton.setDisable(true);
        stopButton.setDisable(false);
        generateButton.setDisable(true);
        aiToggle.setDisable(true);

        boolean aiEnabled = aiToggle.isSelected();
        modeLabel.setText(aiEnabled ? "Algoritmo Genético (Vivo)" : "Sin IA (Base)");

        if (aiEnabled) {
            currentGeneration = 1;
            currentPopulation = MazeAI.initializePopulation(8, random);
            currentStrategy = MazeAI.getBestStrategy(currentPopulation, Main.MAZE, Main.STARTS, currentGeneration);
            runGeneration();
        } else {
            currentGeneration = 0;
            currentStrategy = MazeAI.baseline();
            runSingleSimulation();
        }
    }

    private void runGeneration() {
        if (stopRequestedByUser) return;

        Platform.runLater(() -> {
            statusLabel.setText("Ejecutando Gen " + currentGeneration);
            generationLabel.setText(String.valueOf(currentGeneration));
            strategyLabel.setText(MazeAI.describeOrder(currentStrategy.directionOrder()));
        });

        int delay = (int) speedSlider.getValue();

        currentHandle = Main.startSimulation(currentStrategy, delay, true, new Main.SimulationListener() {
            @Override
            public void onLog(String message) {
                Platform.runLater(() -> appendLog(message));
            }

            @Override
            public void onAgentStarted(int agentId, String threadName, Main.Position start) {
                Platform.runLater(() -> appendLog("Agente " + agentId + " iniciado en " + start));
            }

            @Override
            public void onAgentVisited(int agentId, Main.Position position) {
                Platform.runLater(() -> {
                    Circle marker = agents.get(agentId);
                    GridPane.setColumnIndex(marker, position.col());
                    GridPane.setRowIndex(marker, position.row());

                    if (Main.MAZE[position.row()][position.col()] == 0) {
                        cells[position.row()][position.col()]
                                .setFill(AGENT_COLORS[agentId - 1].deriveColor(0, 1, 1, 0.35));
                    }
                });
            }

            @Override
            public void onAgentFinished(int agentId, boolean found, int visitedCount) {
                Platform.runLater(() -> appendLog("Agente " + agentId + (found ? " encontró la salida" : " se detuvo")));
            }

            @Override
            public void onAiDecision(int agentId, String decision, int generation, double fitness) {}

            @Override
            public void onMetricsUpdated(Main.MetricsSnapshot snapshot) {
                Platform.runLater(() -> {
                    totalVisits.set(snapshot.totalVisits());
                    stepsLabel.setText(String.valueOf(snapshot.totalVisits()));
                    activeLabel.setText(String.valueOf(snapshot.activeAgents()));
                    elapsedLabel.setText(snapshot.elapsedMillis() + " ms");
                });
            }

            @Override
            public void onSimulationFinished(Main.SimulationSummary summary) {
                Platform.runLater(() -> {
                    if (summary.found()) {
                        statusLabel.setText("¡Solución Encontrada!");
                        appendLog("¡Éxito! Salida encontrada en la Generación " + currentGeneration);
                        finishSimulationFlow(true);
                    } else {
                        if (stopRequestedByUser) {
                            statusLabel.setText("Detenido");
                            appendLog("Simulación detenida por el usuario.");
                            finishSimulationFlow(false);
                        } else {
                            appendLog("Generación " + currentGeneration + " no encontró la salida. Evolucionando...");
                            // Evolucionar población
                            currentPopulation = MazeAI.evolve(currentPopulation, Main.MAZE, Main.STARTS, random);
                            currentGeneration++;
                            currentStrategy = MazeAI.getBestStrategy(currentPopulation, Main.MAZE, Main.STARTS, currentGeneration);

                            // Reiniciar tablero visualmente y lanzar la siguiente generación
                            resetBoardVisually();
                            runGeneration();
                        }
                    }
                });
            }
        });
    }

    private void runSingleSimulation() {
        Platform.runLater(() -> {
            statusLabel.setText("Ejecutando simulación...");
            generationLabel.setText("-");
            strategyLabel.setText(MazeAI.describeOrder(currentStrategy.directionOrder()));
        });

        int delay = (int) speedSlider.getValue();

        currentHandle = Main.startSimulation(currentStrategy, delay, false, new Main.SimulationListener() {
            @Override
            public void onLog(String message) {
                Platform.runLater(() -> appendLog(message));
            }

            @Override
            public void onAgentStarted(int agentId, String threadName, Main.Position start) {}

            @Override
            public void onAgentVisited(int agentId, Main.Position position) {
                Platform.runLater(() -> {
                    Circle marker = agents.get(agentId);
                    GridPane.setColumnIndex(marker, position.col());
                    GridPane.setRowIndex(marker, position.row());

                    if (Main.MAZE[position.row()][position.col()] == 0) {
                        cells[position.row()][position.col()]
                                .setFill(AGENT_COLORS[agentId - 1].deriveColor(0, 1, 1, 0.35));
                    }
                });
            }

            @Override
            public void onAgentFinished(int agentId, boolean found, int visitedCount) {}

            @Override
            public void onAiDecision(int agentId, String decision, int generation, double fitness) {}

            @Override
            public void onMetricsUpdated(Main.MetricsSnapshot snapshot) {
                Platform.runLater(() -> {
                    totalVisits.set(snapshot.totalVisits());
                    stepsLabel.setText(String.valueOf(snapshot.totalVisits()));
                    activeLabel.setText(String.valueOf(snapshot.activeAgents()));
                    elapsedLabel.setText(snapshot.elapsedMillis() + " ms");
                });
            }

            @Override
            public void onSimulationFinished(Main.SimulationSummary summary) {
                Platform.runLater(() -> {
                    if (summary.found()) {
                        statusLabel.setText("¡Salida Encontrada!");
                        appendLog("¡Éxito! Salida encontrada sin IA.");
                    } else {
                        statusLabel.setText(stopRequestedByUser ? "Detenido" : "Sin salida");
                        appendLog(stopRequestedByUser ? "Simulación detenida." : "Los agentes se atascaron y no encontraron la salida.");
                    }
                    finishSimulationFlow(summary.found());
                });
            }
        });
    }

    private void stopSimulation() {
        stopRequestedByUser = true;
        statusLabel.setText("Deteniendo...");
        if (currentHandle != null) currentHandle.stop();
    }

    private void finishSimulationFlow(boolean found) {
        currentHandle = null;
        startButton.setDisable(false);
        stopButton.setDisable(true);
        generateButton.setDisable(false);
        aiToggle.setDisable(false);
    }

    // ─────────────────────────────────────────────
    //  Helpers de UI e inicialización
    // ─────────────────────────────────────────────

    private void resetBoard() {
        totalVisits.set(0);
        stepsLabel.setText("0");
        activeLabel.setText("0");
        elapsedLabel.setText("0 ms");
        generationLabel.setText("0");
        strategyLabel.setText("-");
        statusLabel.setText("Listo");
        logArea.clear();

        resetBoardVisually();
    }

    /** Restaura marcadores y limpia celdas pintadas conservando el log y contadores globales del flujo */
    private void resetBoardVisually() {
        // Restaurar colores de celdas
        for (int row = 0; row < Main.MAZE.length; row++) {
            for (int col = 0; col < Main.MAZE[row].length; col++) {
                cells[row][col].setFill(colorForCell(Main.MAZE[row][col]));
            }
        }

        // Restaurar posiciones iniciales de los marcadores
        for (int i = 0; i < Main.STARTS.length; i++) {
            Circle marker = agents.get(i + 1);
            Main.Position start = Main.STARTS[i];
            GridPane.setColumnIndex(marker, start.col());
            GridPane.setRowIndex(marker, start.row());
        }
    }

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    private Label header(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        return label;
    }

    private Color colorForCell(int value) {
        return switch (value) {
            case 1  -> Color.web("#0f172a");  // pared
            case 9  -> Color.web("#facc15");  // salida (amarillo)
            default -> Color.web("#e2e8f0");  // libre
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}

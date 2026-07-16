import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interfaz — Capa de presentación JavaFX.
 *
 * Responsabilidades:
 *   - Construir y mostrar todos los componentes visuales.
 *   - Escuchar eventos de la simulación (SimulationListener) y
 *     reflejarlos en la UI via Platform.runLater().
 *   - Delegar el inicio / detención de la simulación a Main.
 *
 * Estructura de la ventana:
 *   ┌──────────────────────────────────────────┐
 *   │  Título + Toolbar                        │  ← top
 *   ├───────────────────┬──────────────────────┤
 *   │  Laberinto        │  Panel lateral        │  ← center
 *   │  (ScrollPane)     │  métricas / logs      │
 *   └───────────────────┴──────────────────────┘
 */
public class Interfaz extends Application {

    // ─────────────────────────────────────────────
    //  Constantes de diseño
    // ─────────────────────────────────────────────

    private static final int     CELL_SIZE    = 32;
    private static final int     MAX_CHART_POINTS = 60;

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

    // Etiquetas de hilo y estado por agente
    private final Map<Integer, Label> agentThreadLabels = new HashMap<>();
    private final Map<Integer, Label> agentStateLabels  = new HashMap<>();

    // Contadores compartidos con el listener
    private final AtomicInteger chartTick      = new AtomicInteger();
    private final AtomicInteger totalVisits    = new AtomicInteger();
    private final AtomicInteger finishedAgents = new AtomicInteger();

    // ─────────────────────────────────────────────
    //  Componentes de la UI (asignados al construir)
    // ─────────────────────────────────────────────

    private Label    statusLabel;
    private Label    modeLabel;
    private Label    stepsLabel;
    private Label    activeLabel;
    private Label    elapsedLabel;
    private Label    throughputLabel;
    private Label    latencyLabel;
    private Label    aiDecisionLabel;
    private Label    summaryLabel;
    private TextArea logArea;
    private CheckBox aiToggle;
    private Button   startButton;
    private Button   stopButton;

    private LineChart<Number, Number> progressChart;
    private BarChart<String, Number>  comparisonChart;

    // Series de datos para los gráficos
    private final XYChart.Series<Number, Number> throughputSeries  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> latencySeries     = new XYChart.Series<>();
    private final XYChart.Series<String, Number> comparisonSeries  = new XYChart.Series<>();

    // ─────────────────────────────────────────────
    //  Estado de la simulación en curso
    // ─────────────────────────────────────────────

    private volatile Main.SimulationHandle currentHandle;
    private volatile boolean               stopRequestedByUser;

    /** Tiempos de la última ejecución de cada modo (para el BarChart). */
    private long lastAiMillis    = -1;
    private long lastPlainMillis = -1;

    // ─────────────────────────────────────────────
    //  Punto de entrada JavaFX
    // ─────────────────────────────────────────────

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f172a, #111827);");

        // ── Top: título + toolbar ──
        VBox top = new VBox(10, buildTitle(), buildToolbar());
        top.setPadding(new Insets(0, 0, 14, 0));
        root.setTop(top);

        // ── Centro: laberinto + panel lateral ──
        GridPane  mazeGrid   = buildMazeGrid();
        ScrollPane mazeScroll = new ScrollPane(mazeGrid);
        mazeScroll.setFitToWidth(true);
        mazeScroll.setFitToHeight(true);
        mazeScroll.setPannable(true);
        mazeScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox sidePanel = buildSidePanel();
        HBox content   = new HBox(16, mazeScroll, sidePanel);
        HBox.setHgrow(mazeScroll, Priority.ALWAYS);
        root.setCenter(content);

        Scene scene = new Scene(root, 1320, 880);
        stage.setTitle("Laberinto concurrente");
        stage.setScene(scene);
        stage.show();

        resetBoard();
    }

    // ─────────────────────────────────────────────
    //  Construcción de componentes de la UI
    // ─────────────────────────────────────────────

    private VBox buildTitle() {
        Label title = new Label("Laberinto concurrente");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Prototipo funcional con visualización de agentes, métricas y modo IA.");
        subtitle.setTextFill(Color.web("#cbd5e1"));

        return new VBox(2, title, subtitle);
    }

    private HBox buildToolbar() {
        aiToggle = new CheckBox("IA activa");
        aiToggle.setSelected(true);
        aiToggle.setTextFill(Color.WHITE);

        startButton = new Button("Iniciar");
        stopButton  = new Button("Detener");
        stopButton.setDisable(true);

        startButton.setOnAction(event -> startSimulation());
        stopButton.setOnAction(event  -> stopSimulation());

        statusLabel = new Label("Listo");
        statusLabel.setTextFill(Color.web("#e2e8f0"));
        modeLabel = new Label("Modo: IA activa");
        modeLabel.setTextFill(Color.web("#e2e8f0"));

        HBox toolbar = new HBox(10, startButton, stopButton, aiToggle, spacer(), statusLabel, modeLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        return toolbar;
    }

    private VBox buildSidePanel() {
        // ── Métricas ──
        VBox metrics = new VBox(6,
                header("Métricas"),
                metricLine("Pasos",          stepsLabel      = new Label("0")),
                metricLine("Agentes activos", activeLabel     = new Label("0")),
                metricLine("Tiempo",          elapsedLabel    = new Label("0 ms")),
                metricLine("Throughput",      throughputLabel = new Label("0.0 pasos/s")),
                metricLine("Latencia",        latencyLabel    = new Label("0.0 ms/paso")),
                metricLine("Decisión IA",     aiDecisionLabel = new Label("-")),
                metricLine("Resultado",       summaryLabel    = new Label("-")));
        metrics.setPadding(new Insets(0, 0, 6, 0));

        // ── Cards de agentes / hilos ──
        VBox agentsBox = new VBox(8, header("Hilos y agentes"));
        for (int i = 1; i <= Main.STARTS.length; i++) {
            agentsBox.getChildren().add(agentCard(i, AGENT_COLORS[i - 1]));
        }

        // ── Gráficos ──
        progressChart   = buildProgressChart();
        comparisonChart = buildComparisonChart();

        // ── Log de eventos concurrentes ──
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(8);
        logArea.setStyle("-fx-control-inner-background: #0b1220; -fx-text-fill: #e2e8f0;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox panel = new VBox(12,
                metrics,
                agentsBox,
                progressChart,
                comparisonChart,
                header("Logs concurrentes"),
                logArea);
        panel.setPrefWidth(440);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.84); -fx-background-radius: 18;");
        return panel;
    }

    private VBox agentCard(int agentId, Color color) {
        Label title = new Label("Agente " + agentId);
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-weight: bold;");

        Label thread = new Label("Hilo: -");
        thread.setTextFill(Color.web("#cbd5e1"));
        Label state = new Label("Estado: listo");
        state.setTextFill(Color.web("#cbd5e1"));

        agentThreadLabels.put(agentId, thread);
        agentStateLabels.put(agentId, state);

        VBox box = new VBox(4, title, thread, state);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: rgba(30, 41, 59, 0.9); -fx-background-radius: 12;"
                + " -fx-border-color: " + toWeb(color) + "; -fx-border-radius: 12; -fx-border-width: 1;");
        return box;
    }

    private LineChart<Number, Number> buildProgressChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Evento");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Valor");

        throughputSeries.setName("Throughput");
        latencySeries.setName("Latencia");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.getData().addAll(throughputSeries, latencySeries);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setPrefHeight(220);
        return chart;
    }

    private BarChart<String, Number> buildComparisonChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Modo");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Tiempo (ms)");

        comparisonSeries.setName("Ultima ejecucion");
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.getData().add(comparisonSeries);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(180);
        return chart;
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
    //  Control de la simulación
    // ─────────────────────────────────────────────

    private void startSimulation() {
        // Detener cualquier simulación previa
        if (currentHandle != null) currentHandle.stop();

        resetBoard();
        boolean aiEnabled = aiToggle.isSelected();

        currentHandle = Main.startSimulation(aiEnabled, new Main.SimulationListener() {

            @Override
            public void onLog(String message) {
                Platform.runLater(() -> appendLog(message));
            }

            @Override
            public void onAgentStarted(int agentId, String threadName, Main.Position start) {
                Platform.runLater(() -> {
                    agentThreadLabels.get(agentId).setText("Hilo: " + threadName);
                    agentStateLabels.get(agentId).setText("Estado: activo");
                    appendLog("[" + threadName + "] agente " + agentId + " listo en " + start);
                });
            }

            @Override
            public void onAgentVisited(int agentId, Main.Position position) {
                Platform.runLater(() -> {
                    // Mover el marcador del agente
                    Circle marker = agents.get(agentId);
                    GridPane.setColumnIndex(marker, position.col());
                    GridPane.setRowIndex(marker, position.row());

                    // Pintar la celda visitada con el color del agente (semitransparente)
                    if (Main.MAZE[position.row()][position.col()] == 0) {
                        cells[position.row()][position.col()]
                                .setFill(AGENT_COLORS[agentId - 1].deriveColor(0, 1, 1, 0.35));
                    }
                });
            }

            @Override
            public void onAgentFinished(int agentId, boolean found, int visitedCount) {
                Platform.runLater(() ->
                    agentStateLabels.get(agentId)
                            .setText(found ? "Estado: salida encontrada" : "Estado: finalizado")
                );
            }

            @Override
            public void onAiDecision(int agentId, String decision, int generation, double fitness) {
                Platform.runLater(() -> {
                    aiDecisionLabel.setText("A" + agentId + " gen " + generation + " | " + decision);
                    appendLog("IA A" + agentId + " -> " + decision + " | fitness " + String.format("%.2f", fitness));
                });
            }

            /**
             * Única fuente de verdad para actualizar métricas y el contador de agentes activos.
             * (Se eliminó la actualización duplicada que había en onAgentFinished.)
             */
            @Override
            public void onMetricsUpdated(Main.MetricsSnapshot snapshot) {
                Platform.runLater(() -> {
                    totalVisits.set(snapshot.totalVisits());
                    stepsLabel.setText(String.valueOf(snapshot.totalVisits()));
                    activeLabel.setText(String.valueOf(snapshot.activeAgents()));
                    elapsedLabel.setText(snapshot.elapsedMillis() + " ms");
                    throughputLabel.setText(String.format("%.1f pasos/s", snapshot.throughput()));

                    double latency = snapshot.totalVisits() > 0
                            ? (double) snapshot.elapsedMillis() / snapshot.totalVisits()
                            : 0.0;
                    latencyLabel.setText(String.format("%.1f ms/paso", latency));
                    modeLabel.setText(snapshot.aiEnabled() ? "Modo: IA activa" : "Modo: base");
                    summaryLabel.setText(snapshot.modeName()
                            + " | gen "  + snapshot.generation()
                            + " | fit "  + String.format("%.2f", snapshot.fitness()));

                    int tick = chartTick.incrementAndGet();
                    addChartPoint(throughputSeries, tick, snapshot.throughput());
                    addChartPoint(latencySeries,    tick, latency);
                });
            }

            @Override
            public void onSimulationFinished(Main.SimulationSummary summary) {
                Platform.runLater(() -> {
                    boolean userStopped = stopRequestedByUser;
                    currentHandle        = null;
                    stopRequestedByUser  = false;
                    startButton.setDisable(false);
                    stopButton.setDisable(true);
                    aiToggle.setDisable(false);

                    if (userStopped && !summary.found()) {
                        statusLabel.setText("Simulación detenida");
                        appendLog("Simulación detenida por el usuario");
                    } else {
                        statusLabel.setText(summary.found() ? "Salida encontrada" : "Sin salida");
                        appendLog("Simulación terminada | modo=" + summary.modeName()
                                + " | tiempo=" + summary.elapsedMillis() + " ms");
                    }

                    updateComparison(summary);
                });
            }
        });

        // Actualizar estado de la UI al iniciar
        stopRequestedByUser = false;
        aiToggle.setDisable(true);
        startButton.setDisable(true);
        stopButton.setDisable(false);
        statusLabel.setText(aiEnabled ? "Ejecutando con IA" : "Ejecutando en modo base");
        finishedAgents.set(0);
        activeLabel.setText(String.valueOf(Main.STARTS.length));
        modeLabel.setText(aiEnabled ? "Modo: IA activa" : "Modo: base");
    }

    private void stopSimulation() {
        stopRequestedByUser = true;
        statusLabel.setText("Deteniendo simulación...");
        if (currentHandle != null) currentHandle.stop();
    }

    // ─────────────────────────────────────────────
    //  Helpers de UI
    // ─────────────────────────────────────────────

    /** Restaura el tablero a su estado inicial (sin visitas ni agentes en movimiento). */
    private void resetBoard() {
        totalVisits.set(0);
        finishedAgents.set(0);
        chartTick.set(0);

        stepsLabel.setText("0");
        activeLabel.setText("0");
        elapsedLabel.setText("0 ms");
        throughputLabel.setText("0.0 pasos/s");
        latencyLabel.setText("0.0 ms/paso");
        aiDecisionLabel.setText("-");
        summaryLabel.setText("-");
        statusLabel.setText("Listo");

        if (aiToggle != null) {
            modeLabel.setText(aiToggle.isSelected() ? "Modo: IA activa" : "Modo: base");
        }

        logArea.clear();
        throughputSeries.getData().clear();
        latencySeries.getData().clear();
        comparisonSeries.getData().clear();

        // Restaurar colores de celdas
        for (int row = 0; row < Main.MAZE.length; row++) {
            for (int col = 0; col < Main.MAZE[row].length; col++) {
                cells[row][col].setFill(colorForCell(Main.MAZE[row][col]));
            }
        }

        // Restaurar posiciones iniciales de los marcadores
        for (int i = 0; i < Main.STARTS.length; i++) {
            Circle        marker = agents.get(i + 1);
            Main.Position start  = Main.STARTS[i];
            GridPane.setColumnIndex(marker, start.col());
            GridPane.setRowIndex(marker, start.row());
            agentThreadLabels.get(i + 1).setText("Hilo: -");
            agentStateLabels.get(i + 1).setText("Estado: listo");
        }
    }

    /** Actualiza el BarChart de comparación con los tiempos de la última ejecución de cada modo. */
    private void updateComparison(Main.SimulationSummary summary) {
        if (summary.aiEnabled()) {
            lastAiMillis    = summary.elapsedMillis();
        } else {
            lastPlainMillis = summary.elapsedMillis();
        }

        comparisonSeries.getData().clear();
        if (lastPlainMillis >= 0) comparisonSeries.getData().add(new XYChart.Data<>("Sin IA", lastPlainMillis));
        if (lastAiMillis    >= 0) comparisonSeries.getData().add(new XYChart.Data<>("IA",     lastAiMillis));
    }

    /** Agrega un punto al gráfico de líneas y limita el historial a MAX_CHART_POINTS. */
    private void addChartPoint(XYChart.Series<Number, Number> series, Number x, Number y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > MAX_CHART_POINTS) {
            series.getData().remove(0);
        }
    }

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    // ─────────────────────────────────────────────
    //  Helpers de estilo
    // ─────────────────────────────────────────────

    private Label header(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        return label;
    }

    private HBox metricLine(String name, Label valueLabel) {
        Label label = new Label(name + ":");
        label.setTextFill(Color.web("#cbd5e1"));
        valueLabel.setTextFill(Color.web("#ffffff"));
        HBox row = new HBox(8, label, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private Color colorForCell(int value) {
        return switch (value) {
            case 1  -> Color.web("#0f172a");  // pared
            case 9  -> Color.web("#facc15");  // salida (amarillo)
            default -> Color.web("#e2e8f0");  // libre
        };
    }

    private String toWeb(Color color) {
        return String.format("rgba(%d,%d,%d,1)",
                (int) Math.round(color.getRed()   * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue()  * 255));
    }

    // ─────────────────────────────────────────────
    //  main (alternativo al launch desde Main)
    // ─────────────────────────────────────────────

    public static void main(String[] args) {
        launch(args);
    }
}

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

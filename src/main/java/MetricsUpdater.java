/** Contrato interno para notificar métricas desde dentro del agente hacia la UI. */
interface MetricsUpdater {
    void update();
}

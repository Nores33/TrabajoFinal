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

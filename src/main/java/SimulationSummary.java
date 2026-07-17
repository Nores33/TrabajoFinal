/** Resumen final emitido al terminar la simulación. */
public record SimulationSummary(
        boolean aiEnabled,
        String  modeName,
        boolean found,
        int     totalVisits,
        long    elapsedMillis,
        int     generation,
        double  fitness) {}

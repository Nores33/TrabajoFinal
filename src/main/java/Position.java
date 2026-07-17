/** Coordenada inmutable (fila, columna) dentro del laberinto. */
public final class Position {
    private final int row;
    private final int col;

    public Position(int r, int c) { row = r; col = c; }

    public int row() { return row; }
    public int col() { return col; }

    @Override
    public String toString() { return "(" + row + "," + col + ")"; }
}

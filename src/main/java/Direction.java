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

package phase3.core;
public record PieceSnapshot(int[][] cells, int x, int y) {
    public PieceSnapshot { cells=SnapshotCopies.matrix(cells); }
    @Override public int[][] cells() { return SnapshotCopies.matrix(cells); }
}

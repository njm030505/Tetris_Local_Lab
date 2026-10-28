package phase3.core;
import java.util.List;
public record PlayerSnapshot(int[][] board, PieceSnapshot current, PieceSnapshot next,
        int score, int level, int lines, int dropMillis, boolean gameOver,
        List<Integer> clearingRows, List<int[]> pendingRows) {
    public PlayerSnapshot {
        board=SnapshotCopies.matrix(board);
        clearingRows=List.copyOf(clearingRows);
        pendingRows=SnapshotCopies.rows(pendingRows);
    }
    @Override public int[][] board() { return SnapshotCopies.matrix(board); }
    @Override public List<int[]> pendingRows() { return SnapshotCopies.rows(pendingRows); }
}

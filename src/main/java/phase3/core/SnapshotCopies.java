package phase3.core;
import java.util.List;
import java.util.Objects;
final class SnapshotCopies {
    private SnapshotCopies() {}
    static int[][] matrix(int[][] input) {
        Objects.requireNonNull(input, "cells");
        int[][] copy = new int[input.length][];
        for (int row=0;row<input.length;row++) copy[row]=Objects.requireNonNull(input[row], "row").clone();
        return copy;
    }
    static List<int[]> rows(List<int[]> input) {
        return Objects.requireNonNull(input,"rows").stream().map(int[]::clone).toList();
    }
}

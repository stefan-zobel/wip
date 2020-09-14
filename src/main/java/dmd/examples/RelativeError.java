package dmd.examples;

import net.jamu.matrix.Matrices;
import net.jamu.matrix.MatrixD;

public class RelativeError {

    public static MatrixD compute(MatrixD expected, MatrixD actual) {
        MatrixD diff = expected.minus(actual);
        MatrixD avgRelErrorPerColumn = Matrices.createD(1, diff.numColumns());
        for (int col = 0; col < diff.numColumns(); ++col) {
            double sumErrPerColumn = 0.0;
            for (int row = 0; row < diff.numRows(); ++row) {
                double exp = Math.abs(expected.get(row, col));
                double delta = Math.abs(diff.get(row, col));
                double rel = delta / exp;
                sumErrPerColumn += rel;
            }
            double avgErr = sumErrPerColumn / diff.numRows();
            avgRelErrorPerColumn.set(0, col, avgErr);
        }
        return avgRelErrorPerColumn;
    }

    public static double avgRelErrorOverall(MatrixD avgRelErrorPerColumn) {
        double avgErr = 0.0;
        for (int i = 0; i < avgRelErrorPerColumn.numColumns(); ++i) {
            avgErr += avgRelErrorPerColumn.get(0, i);
        }
        return avgErr / avgRelErrorPerColumn.numColumns();
    }

    private RelativeError() {
        throw new AssertionError();
    }
}

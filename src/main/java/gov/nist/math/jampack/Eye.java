package gov.nist.math.jampack;

/**
 * Eye generates a matrix whose diagonal elements are one and whose off diagonal
 * elements are zero.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Eye {

    /**
     * Generates an identity matrix of order <tt>n</tt>.
     * 
     * @param n the order of the matrix
     */
    public static Zmat o(int n) {
        return o(n, n);
    }

    /**
     * Generates an <tt>m x n</tt> matrix whose diagonal elements are one and
     * whose off diagonal elements are zero.
     * 
     * @param m the number of rows in the matrix
     * @param n the number of columns in the matrix
     */
    public static Zmat o(int m, int n) {

        Zmat I = new Zmat(m, n);

        for (int i = 0; i < Math.min(m, n); i++) {
            I.setRe(i, i, 1.0);
            I.setIm(i, i, 0.0);
        }

        return I;
    }
}

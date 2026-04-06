package blis.gemm;

import java.util.Random;

public class Main {

    public static void main(String[] args) {
        final boolean USE_FLOAT = true;

        Random rng = new Random();
        
        final int DIM = 4321;
        
        DMatrix a = null;
        DMatrix b = null;
        FMatrix c = null;
        FMatrix d = null;
        
        if (!USE_FLOAT) {
            a = new DMatrix(DIM, DIM);
            b = new DMatrix(DIM, DIM);
        } else {
            c = new FMatrix(DIM, DIM);
            d = new FMatrix(DIM, DIM);
        }
        
        for (int row = 0; row < DIM; ++row) {
            for (int col = 0; col < DIM; ++col) {
                if (!USE_FLOAT) {
                    a.set(row, col, rng.nextDouble());
                    b.set(row, col, rng.nextDouble());
                } else {
                    c.set(row, col, rng.nextFloat());
                    d.set(row, col, rng.nextFloat());
                }
            }
        }

        DMatrix c1 = null;
        DMatrix c2 = null;
        FMatrix c3 = null;
        FMatrix c4 = null;
        
        long s1 = System.currentTimeMillis();
        if (!USE_FLOAT) {
            c1 = a.mul(b);
        } else {
            c3 = c.mul(d);
        }
        long e1 = System.currentTimeMillis();
        long s2 = e1;
        if (!USE_FLOAT) {
            c2 = a.mulBLIS(b);
        } else {
            c4 = c.mulBLIS(d);
        }
        long e2 = System.currentTimeMillis();
        
        double absTolD = 1.0e-8;
        float absTolF = 1.0e-4f;
        boolean approxEq = false;
        if (!USE_FLOAT) {
            approxEq = DMatrix.approximatelyEquals(c1, c2, absTolD);
        } else {
            approxEq = FMatrix.approximatelyEquals(c3, c4, absTolF);
        }
        System.err.println("EQUAL : " + approxEq + " (" + DIM + " x " + DIM + ") , float=" + USE_FLOAT);
        if (!approxEq) {
            if (!USE_FLOAT) {
                System.out.println(c1);
                System.out.println(c2);
            } else {
                System.out.println(c3);
                System.out.println(c4);
            }
        }
        
        System.out.println("BLIS took : " + (e2 - s2) + " ms");
        System.out.println("Netl took : " + (e1 - s1) + " ms");
    }
}

package blis.gemm.vectorapi;

import blis.gemm.P;

public class DoublePolicy extends P {

    // double
    private static final int MR_Height_Default = 4; // 4
    private static final int NR_Width_Default = 8;  // 8

    private static final int KC_Default = 256;  // 256 .. 512
    private static final int MC_Default = 128;  // 128 .. 256 (128)
    private static final int NC_Default = 3840; // 3840 or more

    public DoublePolicy() {
        this(MR_Height_Default, NR_Width_Default, MC_Default, KC_Default, NC_Default);
    }

    public DoublePolicy(int mr_Height, int nr_Width, int mc, int kc, int nc) {
        super(mr_Height, nr_Width, mc, kc, nc);
    }
}

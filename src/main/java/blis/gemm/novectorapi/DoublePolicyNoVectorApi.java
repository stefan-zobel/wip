package blis.gemm.novectorapi;

import blis.gemm.P;

public class DoublePolicyNoVectorApi extends P {

    // double
    private static final int MR_Height_Default = 4; // 4
    private static final int NR_Width_Default = 6;  // 8

    private static final int KC_Default = 256;  // 256 .. 512
    private static final int MC_Default = 96;   // 128 .. 256 (128)
    private static final int NC_Default = 3840; // 3840 or more

    public DoublePolicyNoVectorApi() {
        this(MR_Height_Default, NR_Width_Default, MC_Default, KC_Default, NC_Default);
    }

    public DoublePolicyNoVectorApi(int mr_Height, int nr_Width, int mc, int kc, int nc) {
        super(mr_Height, nr_Width, mc, kc, nc);
    }
}

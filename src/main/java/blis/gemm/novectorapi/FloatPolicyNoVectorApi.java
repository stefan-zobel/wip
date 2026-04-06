package blis.gemm.novectorapi;

import blis.gemm.P;

public class FloatPolicyNoVectorApi extends P {

    // float
    private static final int MR_Height_Default = 4; // 8 // that's extremely frustrating!
    private static final int NR_Width_Default = 6;  // 16

    private static final int KC_Default = 256;  // 512;  // 512 .. 1024 // that's extremely frustrating!
    private static final int MC_Default = 96;   // 192;  // 128 .. 256 (256) // that's extremely frustrating!
    private static final int NC_Default = 3840; // 3840 or more

    public FloatPolicyNoVectorApi() {
        this(MR_Height_Default, NR_Width_Default, MC_Default, KC_Default, NC_Default);
    }

    public FloatPolicyNoVectorApi(int mr_Height, int nr_Width, int mc, int kc, int nc) {
        super(mr_Height, nr_Width, mc, kc, nc);
    }
}

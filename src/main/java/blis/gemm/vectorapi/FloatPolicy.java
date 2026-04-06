package blis.gemm.vectorapi;

import blis.gemm.P;

public class FloatPolicy extends P {

    // float
    private static final int MR_Height_Default = 8; // fixed by AVX2 register width
    private static final int NR_Width_Default = 8;  // 16 (constrained by the number of AVX2 registers, 8 should use about 10 registers) 

    private static final int KC_Default = 512;  // 512 .. 1024 [the whole thing must fit into L1]
    private static final int MC_Default = 192;  // 128 .. 256 (256) [with 512 KiB L2]
    private static final int NC_Default = 3840; // 3840 or more

    public FloatPolicy() {
        this(MR_Height_Default, NR_Width_Default, MC_Default, KC_Default, NC_Default);
    }

    public FloatPolicy(int mr_Height, int nr_Width, int mc, int kc, int nc) {
        super(mr_Height, nr_Width, mc, kc, nc);
    }
}

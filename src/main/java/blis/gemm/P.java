package blis.gemm;

/**
 * Configuration policy.
 * We assume AVX2 and 32 KiB L1-Cache.
 */
public abstract class P {

    // affects used YMM registers (for AVX2)
    public final int MR_Height; // (either 4 for double or 8 for float in a 256bit-register)
    public final int NR_Width;  // (up to 16 YMM registers on AVX2)

    public final int MC; // L2-Cache
    public final int KC; // L1-Cache
    public final int NC; // L3-Cache

    public P(int mr_Height, int nr_Width, int mc, int kc, int nc) {
        this.MR_Height = mr_Height;
        this.NR_Width = nr_Width;

        this.MC = mc;
        this.KC = kc;
        this.NC = nc;

        check();
    }

    public final void check() {
        if (MR_Height <= 0 || NR_Width <= 0 || MC <= 0 || KC <= 0 || NC <= 0) {
            throw new IllegalStateException("Invalid block size: " + toString());
        }
        if (MC % MR_Height != 0) {
            throw new IllegalStateException("MC must be a multiple of MR_Height: " + toString());
        }
        if (NC % NR_Width != 0) {
            throw new IllegalStateException("NC must be a multiple of NR_Width: " + toString());
        }
    }

    @Override
    public final String toString() {
        return "[MR_Height=" + MR_Height + ", NR_Width=" + NR_Width + ", MC=" + MC + ", KC=" + KC + ", NC=" + NC
                + "]";
    }
}

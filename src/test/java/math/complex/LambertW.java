package math.complex;

/**
 * The Lambert W function: the solutions of w exp(w) = z on the two branches
 * that are real valued, the principal one and the one below it.
 */
public final class LambertW {

    private static final double E = 2.718281828459045235360287471352;

    /** how close to the branch point at -1/e the square root series takes over */
    private static final double NEAR = 0.3;

    /** the branch point sits at -1/e */
    private static final double INV_E = 0.36787944117144232159552377016146;

    private static final int STEPS = 40;

    private LambertW() {
        throw new AssertionError("no instances");
    }

    /**
     * The principal branch.
     *
     * @param z the argument
     * @return the w with w exp(w) = z whose imaginary part lies in (-pi, pi]
     */
    public static ComplexD w(ComplexD z) {
        return w(0, z);
    }

    /**
     * Branch 0 or branch -1.
     *
     * @param k the branch, either 0 or -1
     * @param z the argument
     * @return the w with w exp(w) = z on that branch
     */
    public static ComplexD w(int k, ComplexD z) {
        if (k != 0 && k != -1) {
            throw new IllegalArgumentException("only the branches 0 and -1 are here: " + k);
        }
        if (z.isInfinite()) {
            // the modulus is unbounded, the direction is not settled
            return ComplexD.Inf();
        }
        if (z.isNan()) {
            return ComplexD.NaN();
        }
        if (z.re() == 0.0 && z.im() == 0.0) {
            // W ~ z at the origin, so the principal branch keeps the zero it
            // was given; the other one runs off without a direction
            return (k == 0) ? z : ComplexD.Inf();
        }
        return refine(z, seed(k, z));
    }

    // the three regions that need different starting points
    private static ComplexD seed(int k, ComplexD z) {
        ComplexD b = z.scale(E).add(ComplexD.One());
        if (b.abs() < NEAR) {
            // at the branch point the two branches meet, and only the square
            // root in p = sqrt(2(e z + 1)) separates them
            ComplexD p = b.scale(2.0).sqrt();
            if (k == -1) {
                p = p.neg();
            }
            ComplexD s = p.scale(-43.0 / 540.0).add(new ComplexD(11.0 / 72.0, 0.0));
            s = p.mul(s).add(new ComplexD(-1.0 / 3.0, 0.0));
            s = p.mul(s).add(ComplexD.One());
            return p.mul(s).sub(ComplexD.One());
        }
        if (k == 0 && z.re() > -INV_E) {
            // ln(1+z) is exact at the origin and stays in the right basin all
            // the way out, but only with the branch point behind it: past that
            // it is real where the answer is not
            return ComplexD.One().add(z).ln();
        }
        if (k == -1 && z.im() == 0.0 && Math.copySign(1.0, z.im()) > 0.0 && z.re() < 0.0
                && z.re() > -INV_E) {
            // this branch is real on that segment, and the segment is the top
            // side of its cut, so +0 lands on it and -0 does not
            double l = Math.log(-z.re());
            return new ComplexD(l - Math.log(-l), 0.0);
        }
        ComplexD l1 = z.ln().add(new ComplexD(0.0, 2.0 * Math.PI * k));
        ComplexD l2 = l1.ln();
        ComplexD t = l1.sub(l2).add(l2.div(l1));
        return t.add(l2.mul(l2.sub(new ComplexD(2.0, 0.0))).div(l1.mul(l1).scale(2.0)));
    }

    // Halley on w exp(w) = z, which is cubic and needs a handful of steps
    private static ComplexD refine(ComplexD z, ComplexD start) {
        ComplexD w = start;
        for (int i = 0; i < STEPS; ++i) {
            ComplexD e = w.exp();
            ComplexD f = w.mul(e).sub(z);
            ComplexD wp1 = w.add(ComplexD.One());
            ComplexD den = e.mul(wp1).sub(w.add(new ComplexD(2.0, 0.0)).mul(f)
                    .div(wp1.scale(2.0)));
            if (den.re() == 0.0 && den.im() == 0.0) {
                return w;
            }
            ComplexD step = f.div(den);
            if (step.isNan()) {
                return w;
            }
            ComplexD next = w.sub(step);
            if (next.re() == w.re() && next.im() == w.im()) {
                return w;
            }
            w = next;
            if (step.abs() <= 0x1p-52 * w.abs()) {
                return w;
            }
        }
        return w;
    }

}

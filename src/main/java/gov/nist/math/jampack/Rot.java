/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.nist.math.jampack;

/**
 * Rot generates and manipulates plane rotations. Given a 2-vector components
 * are x and y, there is a unitary matrix P such that
 * 
 * <pre>
 *      P|x| =  |   c      s||x| = |z|
 *       |y|    |-conj(s)  c||y|   |0|
 * </pre>
 * 
 * The number c, which is always real, is the cosine of the rotation. The number
 * s, which may be complex is the sine of the rotation.
 * <p>
 * Comments: This suite will eventually contain methods for real rotations (two
 * are already in place). The only difference between real and complex rotations
 * is that si and zi are zero for the former. The final routines will do the
 * efficient thing.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class Rot {

    /** The cosine of the rotation */
    double c;
    /** The real part of the sine of the rotation */
    public double sr;
    /** The imaginary part of the sine of the rotation */
    public double si;
    /** The real part of the first component of the transformed vector */
    public double zr;
    /** The imaginary part of the first component of the transformed vector */
    public double zi;

    /**
     * Given a real 2-vector, genc generates a real plane rotation P such that
     * 
     * <pre>
     *      P|x| =  | c  s||x| = |z|
     *       |y|    |-s  c||y|   |0|
     * </pre>
     * 
     * @param x
     *            The first component of the two vector
     * @param y
     *            The second component of the two vector
     * @param P
     *            The plane rotation
     */
    public static void genc(double x, double y, Rot P) {
        P.si = 0.0;
        P.zi = 0.0;

        if (x == 0.0 & y == 0.0) {
            P.c = 1.0;
            P.sr = 0.0;
            P.zr = 0.0;
            return;
        }

        double s = Math.abs(x) + Math.abs(y);
        P.zr = s * Math.sqrt((x / s) * (x / s) + (y / s) * (y / s));
        P.c = x / P.zr;
        P.sr = y / P.zr;
    }

    /**
     * Given a real 2-vector, genr generates a plane rotation such that
     * 
     * <pre>
     *      |x y|P = |x y|| c  s||x| = |z 0|
     *                    |-s  c||y|
     * </pre>
     * 
     * @param x
     *            The first component of the 2-vector
     * @param y
     *            The second component of the 2-vector
     * @param P
     *            The rotation
     */
    public static void genr(double x, double y, Rot P) {
        P.si = 0.0;
        P.zi = 0.0;

        double s = Math.abs(x) + Math.abs(y);

        if (s == 0.0) {
            P.c = 1.0;
            P.sr = 0.0;
            P.zr = 0.0;
            return;
        }

        P.zr = s * Math.sqrt((x / s) * (x / s) + (y / s) * (y / s));
        P.c = x / P.zr;
        P.sr = -y / P.zr;
    }

    /**
     * Multiplies columns (ii1:ii2,jj1) and A(ii2:ii2,jj1) of a Zmat (altered)
     * by a plane rotation.
     * 
     * @param A
     *            The Zmat (altered)
     * @param P
     *            The rotation
     * @param ii1
     *            The first index of the column range
     * @param ii2
     *            The second index of the column range
     * @param jj1
     *            The index of the first column
     * @param jj2
     *            The index of the second column
     */
    public static void ap(Zmat A, Rot P, int ii1, int ii2, int jj1, int jj2) {
        double t1r, t1i, t2r, t2i;

        int i1 = ii1 - 1;
        int i2 = ii2 - 1;
        int j1 = jj1 - 1;
        int j2 = jj2 - 1;

        for (int i = i1; i <= i2; i++) {
            double reij1 = A.re(i, j1);
            double reij2 = A.re(i, j2);
            double imij1 = A.im(i, j1);
            double imij2 = A.im(i, j2);
            t1r = P.c * reij1 - P.sr * reij2 - P.si * imij2;
            t1i = P.c * imij1 - P.sr * imij2 + P.si * reij2;
            t2r = P.c * reij2 + P.sr * reij1 - P.si * imij1;
            t2i = P.c * imij2 + P.sr * imij1 + P.si * reij1;
            A.setRe(i, j1, t1r);
            A.setRe(i, j2, t2r);
            A.setIm(i, j1, t1i);
            A.setIm(i, j2, t2i);
        }
    }

    /**
     * Multiplies columns (ii1:ii2,jj1) and A(ii2:ii2,jj1) of a Zmat (altered)
     * by the conjugate transpose of plane rotation.
     * 
     * @param A
     *            The Zmat (altered)
     * @param P
     *            The rotation
     * @param ii1
     *            The first index of the column range
     * @param ii2
     *            The second index of the column range
     * @param jj1
     *            The index of the first column
     * @param jj2
     *            The index of the second column
     */
    public static void aph(Zmat A, Rot P, int ii1, int ii2, int jj1, int jj2) {
        double t1r, t1i, t2r, t2i;

        int i1 = ii1 - 1;
        int i2 = ii2 - 1;
        int j1 = jj1 - 1;
        int j2 = jj2 - 1;

        for (int i = i1; i <= i2; i++) {
            double reij1 = A.re(i, j1);
            double reij2 = A.re(i, j2);
            double imij1 = A.im(i, j1);
            double imij2 = A.im(i, j2);
            t1r = P.c * reij1 + P.sr * reij2 + P.si * imij2;
            t1i = P.c * imij1 + P.sr * imij2 - P.si * reij2;
            t2r = P.c * reij2 - P.sr * reij1 + P.si * imij1;
            t2i = P.c * imij2 - P.sr * imij1 - P.si * reij1;
            A.setRe(i, j1, t1r);
            A.setRe(i, j2, t2r);
            A.setIm(i, j1, t1i);
            A.setIm(i, j2, t2i);
        }
    }

    /**
     * Given the real and imaginary parts of a 2-vector, genc generates a plane
     * rotation P such that
     * 
     * <pre>
     *      P|x| =  |   c      s||x| = |z|
     *       |y|    |-conj(s)  c||y|   |0|
     * </pre>
     * 
     * @param xr
     *            The real part of the first component of the 2-vector
     * @param xi
     *            The imaginary part of the first component of the 2-vector
     * @param yr
     *            The real part of the second component of the 2-vector
     * @param yi
     *            The imaginary part of the second component of the 2-vector
     * @param P
     *            The rotation (must be initialized)
     */
    public static void genc(double xr, double xi, double yr, double yi, Rot P) {
        double s, absx, absxy;

        if (xr == 0.0 && xi == 0.0) {
            P.c = 0.0;
            P.sr = 1.0;
            P.si = 0.0;
            P.zr = yr;
            P.zi = yi;
            return;
        }
        s = Math.abs(xr) + Math.abs(xi);
        absx = s * Math.sqrt((xr / s) * (xr / s) + (xi / s) * (xi / s));
        s = Math.abs(s) + Math.abs(yr) + Math.abs(yi);
        absxy = s * Math.sqrt((absx / s) * (absx / s) + (yr / s) * (yr / s) + (yi / s) * (yi / s));
        P.c = absx / absxy;
        xr = xr / absx;
        xi = xi / absx;
        P.sr = (xr * yr + xi * yi) / absxy;
        P.si = (xi * yr - xr * yi) / absxy;
        P.zr = xr * absxy;
        P.zi = xi * absxy;
    }

    /**
     * Given a Zmat A, genc generates a plane rotation that on premultiplication
     * into rows ii1 and ii2 annihilates A(ii2,jj). The element A(ii2,jj) is
     * overwritten by zero and the element A(ii1,jj) is overwritten by its
     * transformed value.
     * 
     * @param A
     *            The Zmat (altered)
     * @param ii1
     *            The row index of the first element
     * @param ii2
     *            The row index of the second element (the one that is
     *            annihilated
     * @param jj
     *            The column index of the elements
     * @param P
     *            The plane rotation (must be initialized)
     */
    public static void genc(Zmat A, int ii1, int ii2, int jj, Rot P) {
        int i1 = ii1 - 1;
        int i2 = ii2 - 1;
        int j = jj - 1;

        Rot.genc(A.re(i1, j), A.im(i1, j), A.re(i2, j), A.im(i2, j), P);
        A.setRe(i1, j, P.zr);
        A.setRe(i2, j, 0.0);
        A.setIm(i1, j, P.zi);
        A.setIm(i2, j, 0.0);
    }

    /**
     * Multiplies rows (ii1,jj1:jj2) and (ii2,jj1:jj2) of a Zmat (altered) by a
     * plane rotation.
     * 
     * @param P
     *            The plane rotation
     * @param A
     *            The Zmat (altered)
     * @param ii1
     *            The row index of the first row.
     * @param ii2
     *            The row index of the second row.
     * @param jj1
     *            The first index of the range of the rows
     * @param jj2
     *            The second index of the range of the rows
     */
    public static void pa(Rot P, Zmat A, int ii1, int ii2, int jj1, int jj2) {
        double t1r, t1i, t2r, t2i;

        int i1 = ii1 - 1;
        int i2 = ii2 - 1;
        int j1 = jj1 - 1;
        int j2 = jj2 - 1;

        for (int j = j1; j <= j2; j++) {
            double rei1j = A.re(i1, j);
            double rei2j = A.re(i2, j);
            double imi1j = A.im(i1, j);
            double imi2j = A.im(i2, j);
            t1r = P.c * rei1j + P.sr * rei2j - P.si * imi2j;
            t1i = P.c * imi1j + P.sr * imi2j + P.si * rei2j;
            t2r = P.c * rei2j - P.sr * rei1j - P.si * imi1j;
            t2i = P.c * imi2j - P.sr * imi1j + P.si * rei1j;
            A.setRe(i1, j, t1r);
            A.setRe(i2, j, t2r);
            A.setIm(i1, j, t1i);
            A.setIm(i2, j, t2i);
        }
    }
}

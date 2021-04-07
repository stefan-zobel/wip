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
 * House provides static methods to generate and apply Householder
 * transformations.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
public final class House {

    /**
     * Generates a Householder transformation from within the part of column c
     * of a Zmat (altered) extending from rows r1 to r2. The method overwrites
     * the column with the result of applying the transformation.
     * 
     * @param A
     *            The matrix from which the transformation is to be generated
     *            (altered)
     * @param r1
     *            The index of the row in which the generating column begins
     * @param r2
     *            The index of the row in which the generating column ends
     * @param c
     *            The index of the generating column
     * @return A Z1 of length r2-r1+1 containing the Householder vector
     * @throws    ZException if anything goes wrong
     */
    public static Z1 genc(Zmat A, int r1, int r2, int c) throws ZException {
        int i, ru;
        double norm;
        double s;
        Z scale;
        Z t = new Z();
        Z t1 = new Z();

        c = c - 1;
        r1 = r1 - 1;
        r2 = r2 - 1;

        ru = r2 - r1 + 1;

        Z1 u = new Z1(r2 - r1 + 1);

        for (i = r1; i <= r2; i++) {
            u.put(i - r1, A.re(i, c), A.im(i, c));
            A.setRe(i, c, 0.0);
            A.setIm(i, c, 0.0);
        }

        norm = Norm.fro(u);

        if (r1 == r2 || norm == 0.0) {
            A.setRe(r1, c, -u.re[0]);
            A.setIm(r1, c, -u.im[0]);
            u.put(0, 1.4142135623730951, 0.0);
            return u;
        }

        scale = new Z(1 / norm, 0.0);

        if (u.re[0] != 0.0 || u.im[0] != 0.0) {
            t = u.get(0);
            scale.times(scale, t.div(t1.conj(t), Z.abs(t)));
        }

        A.put(r1 + 1, c + 1, t.minus(t.div(Z.ONE, scale)));

        for (i = 0; i < ru; i++) {
            u.times(i, scale);
        }

        u.re[0] = u.re[0] + 1.0;
        u.im[0] = 0.0;
        s = Math.sqrt(1.0 / u.re[0]);

        for (i = 0; i < ru; i++) {
            u.re[i] = s * u.re[i];
            u.im[i] = s * u.im[i];
        }

        return u;
    }

    /**
     * Generates a Householder transformation from within the part of row r of a
     * Zmat (altered) extending from columns c1 to c2. The method overwrites the
     * row with the result of applying the transformation.
     * 
     * @param A
     *            The matrix from which the transformation is to be generated
     *            (altered)
     * @param r
     *            The index of the generating row
     * @param c1
     *            The index of the column in which the generating row begins
     * @param c2
     *            The index of the column in which the generating row ends
     * @return A Z1 of length r2-r1+1 containing the Householder vector
     * @throws    ZException if anything goes wrong
     */
    public static Z1 genr(Zmat A, int r, int c1, int c2) throws ZException {
        int j, cu;
        double norm, s;
        Z scale;
        Z t = new Z();
        Z t1 = new Z();

        r = r - 1;
        c1 = c1 - 1;
        c2 = c2 - 1;

        cu = c2 - c1 + 1;

        Z1 u = new Z1(cu);

        for (j = c1; j <= c2; j++) {
            u.put(j - c1, A.re(r, j), A.im(r, j));
            A.setRe(r, j, 0.0);
            A.setIm(r, j, 0.0);
        }

        norm = Norm.fro(u);

        if (c1 == c2 || norm == 0.0) {
            A.setRe(r, c1, -u.re[0]);
            A.setIm(r, c1, -u.im[0]);
            u.put(0, 1.4142135623730951, 0.0);
            return u;
        }

        scale = new Z(1 / norm, 0.0);

        if (u.re[0] != 0.0 || u.im[0] != 0.0) {
            t = u.get(0);
            scale.times(scale, t.div(t1.conj(t), Z.abs(t)));
        }

        A.put(r + 1, c1 + 1, t.minus(t.div(Z.ONE, scale)));

        for (j = 0; j < cu; j++) {
            u.times(j, scale);
        }

        u.re[0] = u.re[0] + 1.0;
        u.im[0] = 0.0;
        s = Math.sqrt(1.0 / u.re[0]);

        for (j = 0; j < cu; j++) {
            u.re[j] = s * u.re[j];
            u.im[j] = -s * u.im[j];
        }

        return u;
    }

    /**
     * Premultiplies the Householder transformation contained in a Z1 into a
     * Zmat A[r1:r2,c1:c2] and overwrites Zmat A[r1:r2,c1:c2] with the results.
     * If r1 &gt; r2 or c1 &gt; c2 the method does nothing.
     * 
     * @param u
     *            The Householder vector
     * @param A
     *            The Zmat to which the transformation is to be applied
     *            (altered)
     * @param r1
     *            The index of the first row to which the transformation is to
     *            be applied
     * @param r2
     *            The index of the last row to which the transformation is to be
     *            applied
     * @param c1
     *            The index of the first column to which the transformation is
     *            index of the to be applied
     * @param c2
     *            The index of the last column to which the transformation is to
     *            be applied
     * @param v
     *            A work array of length at least c2-c1+1
     * @return The transformed Zmat A
     * @throws    ZException
     *                Thrown if either u or v is too short.
     */
    public static Zmat ua(Z1 u, Zmat A, int r1, int r2, int c1, int c2, Z1 v) throws ZException {
        int i, j;

        if (r2 < r1 || c2 < c1) {
            return A;
        }

        if (r2 - r1 + 1 > u.n) {
            throw new ZException("Householder vector too short.");
        }

        if (c2 - c1 + 1 > v.n) {
            throw new ZException("Work vector too short.");
        }

        r1 = r1 - 1;
        r2 = r2 - 1;
        c1 = c1 - 1;
        c2 = c2 - 1;

        for (j = c1; j <= c2; j++) {
            v.re[j - c1] = 0.0;
            v.im[j - c1] = 0.0;
        }

        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {
                double reij = A.re(i, j);
                double imij = A.im(i, j);
                v.re[j - c1] = v.re[j - c1] + u.re[i - r1] * reij + u.im[i - r1] * imij;
                v.im[j - c1] = v.im[j - c1] + u.re[i - r1] * imij - u.im[i - r1] * reij;
            }
        }

        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {
                A.addRe(i, j, -u.re[i - r1] * v.re[j - c1] + u.im[i - r1] * v.im[j - c1]);
                A.addIm(i, j, -u.re[i - r1] * v.im[j - c1] - u.im[i - r1] * v.re[j - c1]);
            }
        }
        return A;
    }

    /**
     * Postmultiplies the Householder transformation contained in a Z1 into a
     * Zmat A[r1:r2,c1:c2] and overwrites Zmat A[r1:r2,c1:c2] with the results.
     * If r1 &gt; r2 or c1 &gt; c2 the method does nothing.
     * 
     * @param u
     *            The Householder vector
     * @param A
     *            The Zmat to which the transformation is to be applied
     *            (altered)
     * @param r1
     *            The index of the first row to which the transformation is to
     *            be applied
     * @param r2
     *            The index of the last row to which the transformation is to be
     *            applied
     * @param c1
     *            The index of the first column to which the transformation is
     *            index of the to be applied
     * @param c2
     *            The index of the last column to which the transformation is to
     *            be applied
     * @param v
     *            A work array of length at least c2-c1+1
     * @return The transformed Zmat A
     * @throws    ZException
     *                Thrown if either u or v is too short.
     */
    public static Zmat au(Zmat A, Z1 u, int r1, int r2, int c1, int c2, Z1 v) throws ZException {
        int i, j;

        if (r2 < r1 || c2 < c1) {
            return A;
        }

        if (c2 - c1 + 1 > u.n) {
            throw new ZException("Householder vector too short.");
        }

        if (r2 - r1 + 1 > v.n) {
            throw new ZException("Work vector too short.");
        }

        r1 = r1 - 1;
        r2 = r2 - 1;
        c1 = c1 - 1;
        c2 = c2 - 1;

        for (i = r1; i <= r2; i++) {
            v.put(i - r1, 0.0, 0.0);
            for (j = c1; j <= c2; j++) {
                double reij = A.re(i, j);
                double imij = A.im(i, j);
                v.re[i - r1] = v.re[i - r1] + reij * u.re[j - c1] - imij * u.im[j - c1];
                v.im[i - r1] = v.im[i - r1] + reij * u.im[j - c1] + imij * u.re[j - c1];
            }
        }
        for (i = r1; i <= r2; i++) {
            for (j = c1; j <= c2; j++) {
                A.addRe(i, j, -v.re[i - r1] * u.re[j - c1] - v.im[i - r1] * u.im[j - c1]);
                A.addIm(i, j,  v.re[i - r1] * u.im[j - c1] - v.im[i - r1] * u.re[j - c1]);
            }
        }
        return A;
    }
}

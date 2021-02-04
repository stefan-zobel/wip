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
package math.complex;

import org.junit.Assert;
import org.junit.Test;

public final class ComplexBasicsTest {

    private static final double inf = Double.POSITIVE_INFINITY;
    private static final double neginf = Double.NEGATIVE_INFINITY;
    private static final double nan = Double.NaN;
    private static final double pi = Math.PI;
    private static final IComplex oneInf = new Complex(1, inf);
    private static final IComplex oneNegInf = new Complex(1, neginf);
    private static final IComplex infOne = new Complex(inf, 1);
    private static final IComplex infZero = new Complex(inf, 0);
    private static final IComplex infNegInf = new Complex(inf, neginf);
    private static final IComplex infInf = new Complex(inf, inf);
    private static final IComplex negInfInf = new Complex(neginf, inf);
    private static final IComplex negInfZero = new Complex(neginf, 0);
    private static final IComplex negInfOne = new Complex(neginf, 1);
    private static final IComplex negInfNegInf = new Complex(neginf, neginf);
    private static final IComplex oneNaN = new Complex(1, nan);
    private static final IComplex zeroInf = new Complex(0, inf);
    private static final IComplex zeroNaN = new Complex(0, nan);
    private static final IComplex nanZero = new Complex(nan, 0);

    @Test
    public void testConstructor() {
        IComplex z = new Complex(3.0, 4.0);
        Assert.assertEquals(3.0, z.re(), 1.0e-5);
        Assert.assertEquals(4.0, z.im(), 1.0e-5);
    }

    @Test
    public void testConstructorNaN() {
        IComplex z = new Complex(3.0, Double.NaN);
        Assert.assertTrue(z.isNan());

        z = new Complex(nan, 4.0);
        Assert.assertTrue(z.isNan());

        z = new Complex(3.0, 4.0);
        Assert.assertFalse(z.isNan());
    }

    @Test
    public void testAbs() {
        IComplex z = new Complex(3.0, 4.0);
        Assert.assertEquals(5.0, z.abs(), 1.0e-5);
    }

    @Test
    public void testAbsNaN() {
        Assert.assertTrue(Double.isNaN(IComplex.NaN.abs()));
        IComplex z = new Complex(nan, nan);
        double abs = z.abs();
        Assert.assertTrue(Double.isNaN(abs));
    }

    @Test
    public void testAbsNaNInf() {
        Assert.assertTrue(Double.isInfinite(IComplex.Inf.abs()));
        IComplex z = new Complex(inf, nan);
        double abs = z.abs();
        Assert.assertTrue(Double.isInfinite(abs));
    }

    @Test
    public void testAbsInfinite() {
        IComplex z = new Complex(inf, 0);
        Assert.assertEquals(inf, z.abs(), 0);
        z = new Complex(0, neginf);
        Assert.assertEquals(inf, z.abs(), 0);
        z = new Complex(inf, neginf);
        Assert.assertEquals(inf, z.abs(), 0);
    }

    @Test
    public void testAdd() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = new Complex(5.0, 6.0);
        IComplex z = x.add(y);
        Assert.assertEquals(8.0, z.re(), 1.0e-5);
        Assert.assertEquals(10.0, z.im(), 1.0e-5);
    }

    @Test
    public void testAddNaN() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.add(IComplex.NaN);
        Assert.assertEquals(IComplex.NaN, z);
        z = new Complex(1, nan);
        IComplex w = x.add(z);
        Assert.assertEquals(IComplex.NaN, w);
    }

    @Test
    public void testAddInf() {
        IComplex x = new Complex(1, 1);
        IComplex z = new Complex(inf, 0);
        IComplex w = x.add(z);
        Assert.assertEquals(w.im(), 1, 0);
        Assert.assertEquals(inf, w.re(), 0);

        x = new Complex(neginf, 0);
        Assert.assertTrue(Double.isNaN(x.add(z).re()));
    }

    @Test
    public void testConj() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.conj();
        Assert.assertEquals(3.0, z.re(), 1.0e-5);
        Assert.assertEquals(-4.0, z.im(), 1.0e-5);
    }

    @Test
    public void testConjugateNaN() {
        IComplex z = IComplex.NaN.conj();
        Assert.assertTrue(z.isNan());
    }

    @Test
    public void testConjugateInfinite() {
        IComplex z = new Complex(0, inf);
        Assert.assertEquals(neginf, z.conj().im(), 0);
        z = new Complex(0, neginf);
        Assert.assertEquals(inf, z.conj().im(), 0);
    }

    @Test
    public void testDiv() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = new Complex(5.0, 6.0);
        IComplex z = x.div(y);
        Assert.assertEquals(39.0 / 61.0, z.re(), 1.0e-5);
        Assert.assertEquals(2.0 / 61.0, z.im(), 1.0e-5);
    }

    @Test
    public void testDivideReal() {
        IComplex x = new Complex(2d, 3d);
        IComplex y = new Complex(2d, 0d);
        Assert.assertEquals(new Complex(1d, 1.5), x.div(y));
    }

    @Test
    public void testDivideImaginary() {
        IComplex x = new Complex(2d, 3d);
        IComplex y = new Complex(0d, 2d);
        Assert.assertEquals(new Complex(1.5d, -1d), x.div(y));
    }

    @Test
    public void testDivideInf() {
        IComplex x = new Complex(3, 4);
        IComplex w = new Complex(neginf, inf);
        Assert.assertTrue(x.div(w).equals(IComplex.Zero));

        IComplex z = w.div(x);
        Assert.assertTrue(Double.isNaN(z.re()));
        Assert.assertEquals(inf, z.im(), 0);

        w = new Complex(inf, inf);
        z = w.div(x);
        Assert.assertTrue(Double.isNaN(z.im()));
        Assert.assertEquals(inf, z.re(), 0);

        w = new Complex(1, inf);
        z = w.div(w);
        Assert.assertTrue(Double.isNaN(z.re()));
        Assert.assertTrue(Double.isNaN(z.im()));
    }

    @Test
    public void testDivideZero() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.div(IComplex.Zero);
        Assert.assertEquals(z, IComplex.NaN);
    }

    @Test
    public void testDivideZeroZero() {
        IComplex x = new Complex(0.0, 0.0);
        IComplex z = x.div(IComplex.Zero);
        Assert.assertEquals(z, IComplex.NaN);
    }

    @Test
    public void testDivideNaN() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.div(IComplex.NaN);
        Assert.assertTrue(z.isNan());
    }

    @Test
    public void testDivideNaNInf() {
       IComplex z = oneInf.div(IComplex.One);
       Assert.assertTrue(Double.isNaN(z.re()));
       Assert.assertEquals(inf, z.im(), 0);

       z = negInfNegInf.div(oneNaN);
       Assert.assertTrue(Double.isNaN(z.re()));
       Assert.assertTrue(Double.isNaN(z.im()));

       z = negInfInf.div(IComplex.One);
       Assert.assertTrue(Double.isNaN(z.re()));
       Assert.assertTrue(Double.isNaN(z.im()));
    }

    @Test
    public void testInv() {
        IComplex z = new Complex(5.0, 6.0);
        IComplex act = z.inv();
        double expRe = 5.0 / 61.0;
        double expIm = -6.0 / 61.0;
        Assert.assertEquals(expRe, act.re(), Math.ulp(expRe));
        Assert.assertEquals(expIm, act.im(), Math.ulp(expIm));
    }

    @Test
    public void testInvReal() {
        IComplex z = new Complex(-2.0, 0.0);
        Assert.assertEquals(new Complex(-0.5, 0.0), z.inv());
    }

    @Test
    public void testInvImaginary() {
        IComplex z = new Complex(0.0, -2.0);
        Assert.assertEquals(new Complex(0.0, 0.5), z.inv());
    }

    @Test
    public void testInvInf() {
        IComplex z = new Complex(neginf, inf);
        Assert.assertTrue(z.inv().equals(IComplex.Zero));

        z = new Complex(1, inf).inv();
        Assert.assertEquals(z, IComplex.Zero);
    }

    @Test
    public void testInvZero() {
        Assert.assertEquals(IComplex.Zero.inv(), IComplex.Inf);
    }

    @Test
    public void testInvNaN() {
        Assert.assertTrue(IComplex.NaN.inv().isNan());
    }

    @Test
    public void testMul() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = new Complex(5.0, 6.0);
        IComplex z = x.mul(y);
        Assert.assertEquals(-9.0, z.re(), 1.0e-5);
        Assert.assertEquals(38.0, z.im(), 1.0e-5);
    }

    @Test
    public void testMul2() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = x;
        IComplex z = x.mul(y);
        Assert.assertEquals(-7.0, z.re(), 1.0e-5);
        Assert.assertEquals(24.0, z.im(), 1.0e-5);
    }

    @Test
    public void testMultiplyNaN() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.mul(IComplex.NaN);
        Assert.assertEquals(IComplex.NaN, z);
        z = IComplex.NaN.scale(5);
        Assert.assertEquals(IComplex.NaN, z);
    }

    @Test
    public void testMultiplyInfInf() {
        Assert.assertTrue(infInf.mul(infInf).isInfinite());
    }

    @Test
    public void testMultiplyNaNInf() {
        IComplex z = new Complex(1,1);
        IComplex w = z.mul(infOne);
        Assert.assertEquals(w.re(), inf, 0);
        Assert.assertEquals(w.im(), inf, 0);

        // multiplications with infinity 
        Assert.assertTrue(new Complex( 1,0).mul(infInf).equals(IComplex.Inf));
        Assert.assertTrue(new Complex(-1,0).mul(infInf).equals(IComplex.Inf));
        Assert.assertTrue(new Complex( 1,0).mul(negInfZero).equals(IComplex.Inf));

        w = oneInf.mul(oneNegInf);
        Assert.assertEquals(w.re(), inf, 0);
        Assert.assertEquals(w.im(), inf, 0);

        w = negInfNegInf.mul(oneNaN);
        // TODO: better use isNaN()?
//        Assert.assertTrue(Double.isNaN(w.re()));
//        Assert.assertTrue(Double.isNaN(w.im()));
        Assert.assertTrue(Double.isInfinite(w.re()));
        Assert.assertTrue(Double.isInfinite(w.im()));

        z = new Complex(1, neginf);
        Assert.assertSame(IComplex.Inf, z.mul(z));
    }

    @Test
    public void testScale() {
        IComplex x = new Complex(3.0, 4.0);
        double yDouble = 2.0;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.mul(yComplex), x.scale(yDouble));
        int zInt = -5;
        IComplex zComplex = new Complex(zInt);
        Assert.assertEquals(x.mul(zComplex), x.scale(zInt));
    }

    @Test
    public void testScaleNaN() {
        IComplex x = new Complex(3.0, 4.0);
        double yDouble = Double.NaN;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.mul(yComplex), x.scale(yDouble));
    }

    @Test
    public void testScaleInf() {
        IComplex x = new Complex(1, 1);
        double yDouble = Double.POSITIVE_INFINITY;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.mul(yComplex), x.scale(yDouble));

        yDouble = Double.NEGATIVE_INFINITY;
        yComplex = new Complex(yDouble);
        Assert.assertEquals(x.mul(yComplex), x.scale(yDouble));
    }

    @Test
    public void testNeg() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.neg();
        Assert.assertEquals(-3.0, z.re(), 1.0e-5);
        Assert.assertEquals(-4.0, z.im(), 1.0e-5);
    }

    @Test
    public void testNegateNaN() {
        IComplex z = IComplex.NaN.neg();
        Assert.assertTrue(z.isNan());
    }

    @Test
    public void testSub() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = new Complex(5.0, 6.0);
        IComplex z = x.sub(y);
        Assert.assertEquals(-2.0, z.re(), 1.0e-5);
        Assert.assertEquals(-2.0, z.im(), 1.0e-5);
    }

    @Test
    public void testSubtractNaN() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex z = x.sub(IComplex.NaN);
        Assert.assertEquals(IComplex.NaN, z);
        z = new Complex(1, nan);
        IComplex w = x.sub(z);
        Assert.assertEquals(IComplex.NaN, w);
    }

    @Test
    public void testSubtractInf() {
        IComplex x = new Complex(1, 1);
        IComplex z = new Complex(neginf, 0);
        IComplex w = x.sub(z);
        Assert.assertEquals(w.im(), 1, 0);
        Assert.assertEquals(inf, w.re(), 0);

        x = new Complex(neginf, 0);
        Assert.assertTrue(Double.isNaN(x.sub(z).re()));
    }

    @Test
    public void testEqualsNull() {
        IComplex x = new Complex(3.0, 4.0);
        Assert.assertFalse(x.equals(null));
    }

    @Test
    public void testEqualsClass() {
        IComplex x = new Complex(3.0, 4.0);
        Assert.assertFalse(x.equals(this));
    }

    @Test
    public void testEqualsSame() {
        IComplex x = new Complex(3.0, 4.0);
        Assert.assertTrue(x.equals(x));
    }

    @Test
    public void testEqualsTrue() {
        IComplex x = new Complex(3.0, 4.0);
        IComplex y = new Complex(3.0, 4.0);
        Assert.assertTrue(x.equals(y));
    }

    @Test
    public void testEqualsRealDifference() {
        IComplex x = new Complex(0.0, 0.0);
        IComplex y = new Complex(0.0 + Double.MIN_VALUE, 0.0);
        Assert.assertFalse(x.equals(y));
    }

    @Test
    public void testEqualsImaginaryDifference() {
        IComplex x = new Complex(0.0, 0.0);
        IComplex y = new Complex(0.0, 0.0 + Double.MIN_VALUE);
        Assert.assertFalse(x.equals(y));
    }

    @Test
    public void testEqualsNaN() {
        IComplex realNaN = new Complex(Double.NaN, 0.0);
        IComplex imaginaryNaN = new Complex(0.0, Double.NaN);
        IComplex complexNaN = IComplex.NaN;
        Assert.assertTrue(realNaN.equals(imaginaryNaN));
        Assert.assertTrue(imaginaryNaN.equals(complexNaN));
        Assert.assertTrue(realNaN.equals(complexNaN));
    }

    @Test
    public void testExp() {
        IComplex z = new Complex(3, 4);
        IComplex expected = new Complex(-13.12878, -15.20078);
        TestUtils.assertEquals(expected, z.exp(), 1.0e-5);
        TestUtils.assertEquals(IComplex.One,
                IComplex.Zero.exp(), 10e-12);
        IComplex iPi = IComplex.I.mul(new Complex(pi,0));
        TestUtils.assertEquals(IComplex.One.neg(),
                iPi.exp(), 10e-12);
    }

    @Test
    public void testExpNaN() {
        Assert.assertTrue(IComplex.NaN.exp().isNan());
    }

    @Test
    public void testExpInf() {
        TestUtils.assertSame(IComplex.NaN, oneInf.exp());
        TestUtils.assertSame(IComplex.NaN, oneNegInf.exp());
        TestUtils.assertSame(infInf, infOne.exp());
        TestUtils.assertSame(IComplex.Zero, negInfOne.exp());
        TestUtils.assertSame(IComplex.NaN, infInf.exp());
        TestUtils.assertSame(IComplex.NaN, infNegInf.exp());
        TestUtils.assertSame(IComplex.NaN, negInfInf.exp());
        TestUtils.assertSame(IComplex.NaN, negInfNegInf.exp());
    }

    @Test
    public void testLn() {
        IComplex z = new Complex(3, 4);
        IComplex expected = new Complex(1.60944, 0.927295);
        TestUtils.assertEquals(expected, z.ln(), 1.0e-5);
    }

    @Test
    public void testLogNaN() {
        Assert.assertTrue(IComplex.NaN.ln().isNan());
    }

    @Test
    public void testLogInf() {
        TestUtils.assertEquals(new Complex(inf, pi / 2),
                oneInf.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, -pi / 2),
                oneNegInf.ln(), 10e-12);
        TestUtils.assertEquals(infZero, infOne.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, pi),
                negInfOne.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, pi / 4),
                infInf.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, -pi / 4),
                infNegInf.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, 3d * pi / 4),
                negInfInf.ln(), 10e-12);
        TestUtils.assertEquals(new Complex(inf, - 3d * pi / 4),
                negInfNegInf.ln(), 10e-12);
    }

    @Test
    public void testLogZero() {
        TestUtils.assertSame(negInfZero, IComplex.Zero.ln());
    }

    @Test
    public void testPow() {
        IComplex x = new Complex(3, 4);
        IComplex y = new Complex(5, 6);
        IComplex expected = new Complex(-1.860893, 11.83677);
        TestUtils.assertEquals(expected, x.pow(y), 1.0e-5);
    }

    @Test
    public void testPowNaNBase() {
        IComplex x = new Complex(3, 4);
        Assert.assertTrue(IComplex.NaN.pow(x).isNan());
    }

    @Test
    public void testPowNaNExponent() {
        IComplex x = new Complex(3, 4);
        Assert.assertTrue(x.pow(IComplex.NaN).isNan());
    }

   @Test
   public void testPowInf() {
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(oneInf));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(oneNegInf));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(infOne));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(infInf));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(infNegInf));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(negInfInf));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(negInfNegInf));
       TestUtils.assertSame(IComplex.NaN, infOne.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, negInfOne.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, negInfInf.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(infNegInf));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(negInfNegInf));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(infInf));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(infNegInf));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(negInfNegInf));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(infInf));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(infNegInf));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(negInfNegInf));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(infInf));
   }

   @Test
   public void testPowZero() {
       TestUtils.assertSame(IComplex.NaN,
               IComplex.Zero.pow(IComplex.One));
       TestUtils.assertSame(IComplex.NaN,
               IComplex.Zero.pow(IComplex.Zero));
       TestUtils.assertSame(IComplex.NaN,
               IComplex.Zero.pow(IComplex.I));
       TestUtils.assertEquals(IComplex.One,
               IComplex.One.pow(IComplex.Zero), 10e-12);
       TestUtils.assertEquals(IComplex.One,
               IComplex.I.pow(IComplex.Zero), 10e-12);
       TestUtils.assertEquals(IComplex.One,
               new Complex(-1, 3).pow(IComplex.Zero), 10e-12);
   }

    @Test
    public void testScalarPow() {
        IComplex x = new Complex(3, 4);
        double yDouble = 5.0;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.pow(yComplex), x.pow(yDouble));
    }

    @Test
    public void testScalarPowNaNBase() {
        IComplex x = IComplex.NaN;
        double yDouble = 5.0;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.pow(yComplex), x.pow(yDouble));
    }

    @Test
    public void testScalarPowNaNExponent() {
        IComplex x = new Complex(3, 4);
        double yDouble = Double.NaN;
        IComplex yComplex = new Complex(yDouble);
        Assert.assertEquals(x.pow(yComplex), x.pow(yDouble));
    }

   @Test
   public void testScalarPowInf() {
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(Double.POSITIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, IComplex.One.pow(Double.NEGATIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, infOne.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, negInfOne.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, negInfInf.pow(10));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(Double.POSITIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, negInfNegInf.pow(Double.POSITIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(Double.POSITIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, infInf.pow(Double.NEGATIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(Double.NEGATIVE_INFINITY));
       TestUtils.assertSame(IComplex.NaN, infNegInf.pow(Double.POSITIVE_INFINITY));
   }

   @Test
   public void testScalarPowZero() {
       TestUtils.assertSame(IComplex.NaN, IComplex.Zero.pow(1.0));
       TestUtils.assertSame(IComplex.NaN, IComplex.Zero.pow(0.0));
       TestUtils.assertEquals(IComplex.One, IComplex.One.pow(0.0), 10e-12);
       TestUtils.assertEquals(IComplex.One, IComplex.I.pow(0.0), 10e-12);
       TestUtils.assertEquals(IComplex.One, new Complex(-1, 3).pow(0.0), 10e-12);
   }

    @Test(expected=NullPointerException.class)
    public void testpowNull() {
        IComplex.One.pow(null);
    }

    @Test
    public void testEqualsIssue() {
        Assert.assertEquals(new Complex(0,-1), new Complex(0,1).mul(new Complex(-1,0)));
    }

    /**
     * Test standard values
     */
    @Test
    public void testArg() {
        IComplex z = new Complex(1, 0);
        Assert.assertEquals(0.0, z.arg(), 1.0e-12);

        z = new Complex(1, 1);
        Assert.assertEquals(Math.PI/4, z.arg(), 1.0e-12);

        z = new Complex(0, 1);
        Assert.assertEquals(Math.PI/2, z.arg(), 1.0e-12);

        z = new Complex(-1, 1);
        Assert.assertEquals(3 * Math.PI/4, z.arg(), 1.0e-12);

        z = new Complex(-1, 0);
        Assert.assertEquals(Math.PI, z.arg(), 1.0e-12);

        z = new Complex(-1, -1);
        Assert.assertEquals(-3 * Math.PI/4, z.arg(), 1.0e-12);

        z = new Complex(0, -1);
        Assert.assertEquals(-Math.PI/2, z.arg(), 1.0e-12);

        z = new Complex(1, -1);
        Assert.assertEquals(-Math.PI/4, z.arg(), 1.0e-12);
    }

    /**
     * Verify atan2-style handling of infinite parts
     */
    @Test
    public void testArgInf() {
        Assert.assertEquals(Math.PI/4, infInf.arg(), 1.0e-12);
        Assert.assertEquals(Math.PI/2, oneInf.arg(), 1.0e-12);
        Assert.assertEquals(0.0, infOne.arg(), 1.0e-12);
        Assert.assertEquals(Math.PI/2, zeroInf.arg(), 1.0e-12);
        Assert.assertEquals(0.0, infZero.arg(), 1.0e-12);
        Assert.assertEquals(Math.PI, negInfOne.arg(), 1.0e-12);
        Assert.assertEquals(-3.0*Math.PI/4, negInfNegInf.arg(), 1.0e-12);
        Assert.assertEquals(-Math.PI/2, oneNegInf.arg(), 1.0e-12);
    }

    /**
     * Verify that either part NaN results in NaN
     */
    @Test
    public void testArgNaN() {
        Assert.assertTrue(Double.isNaN(nanZero.arg()));
        Assert.assertTrue(Double.isNaN(zeroNaN.arg()));
        Assert.assertTrue(Double.isNaN(IComplex.NaN.arg()));
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package math.fast;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

/**
 * Performance tests for FastMath.
 */
public class FastMathTestPerformance {
    private static final int RUNS = Integer.parseInt(System.getProperty("testRuns","100000000"));
    private static final double F1 = 1d / RUNS;

    // Header format
    private static final String FMT_HDR = "%-5s %13s %13s %13s Runs=%d Java %s (%s) %s (%s)";
    // Detail format
    private static final String FMT_DTL = "%-5s %6d %6.1f %6d %6.4f %6d %6.4f";

    @BeforeClass
    public static void header() {
        System.out.println(String.format(FMT_HDR,
                "Name","StrictMath","jafama","Math",RUNS,
                System.getProperty("java.version"),
                System.getProperty("java.runtime.version","?"),
                System.getProperty("java.vm.name"),
                System.getProperty("java.vm.version")
                ));
    }

    private static void report(String name, long strictMathTime, long fastMathTime, long mathTime) {
        long unitTime = strictMathTime;
        System.out.println(String.format(FMT_DTL,
                name,
                strictMathTime / RUNS, (double) strictMathTime / unitTime,
                fastMathTime / RUNS, (double) fastMathTime / unitTime,
                mathTime / RUNS, (double) mathTime / unitTime
                ));
    }

    @Test
    public void testLog() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.log(0.01 + i);
        }
        long strictMath = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.log(0.01 + i);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.log(0.01 + i);
        }
        long mathTime = System.nanoTime() - time;

        report("log",strictMath,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testLog10() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.log10(0.01 + i);
        }
        long strictMath = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.log10(0.01 + i);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.log10(0.01 + i);
        }
        long mathTime = System.nanoTime() - time;

        report("log10",strictMath,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testLog1p() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.log1p(-0.9 + i);
        }
        long strictMath = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.log1p(-0.9 + i);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.log1p(-0.9 + i);
        }
        long mathTime = System.nanoTime() - time;

        report("log1p",strictMath,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testPow() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.pow(0.01 + i * F1, i * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.pow(0.01 + i * F1, i * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.pow(0.01 + i * F1, i * F1);
        }
        long mathTime = System.nanoTime() - time;
        report("pow",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testExp() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.exp(100 * i * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.exp(100 * i * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.exp(100 * i * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("exp",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testSin() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.sin(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.sin(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.sin(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("sin",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testAsin() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.asin(0.999 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.asin(0.999 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.asin(0.999 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("asin",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testCos() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.cos(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.cos(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.cos(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("cos",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testAcos() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.acos(0.999 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.acos(0.999 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.acos(0.999 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;
        report("acos",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testTan() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.tan(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.tan(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.tan(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("tan",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testAtan() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.atan(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.atan(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.atan(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("atan",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testAtan2() {
        double x = 0;
        long time = System.nanoTime();
        int max   = (int) FastMath.floor(FastMath.sqrt(RUNS));
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += StrictMath.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += FastMath.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += Math.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long mathTime = System.nanoTime() - time;

        report("atan2",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testHypot() {
        double x = 0;
        long time = System.nanoTime();
        int max   = (int) FastMath.floor(FastMath.sqrt(RUNS));
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += StrictMath.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += FastMath.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                x += Math.atan2((i - max/2) * (100.0 / max), (j - max/2) * (100.0 / max));
            }
        }
        long mathTime = System.nanoTime() - time;

        report("hypot",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testCbrt() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.cbrt(100 * i * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.cbrt(100 * i * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.cbrt(100 * i * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("cbrt",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testSqrt() {
        @SuppressWarnings("unused")
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.sqrt(100 * i * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.sqrt(100 * i * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.sqrt(100 * i * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("sqrt",strictTime,fastTime,mathTime);
//        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testCosh() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.cosh(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.cosh(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.cosh(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("cosh",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testSinh() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.sinh(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.sinh(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.sinh(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("sinh",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testTanh() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.tanh(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.tanh(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.tanh(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;

        report("tanh",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testExpm1() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.expm1(100 * (i - RUNS/2) * F1);
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.expm1(100 * (i - RUNS/2) * F1);
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.expm1(100 * (i - RUNS/2) * F1);
        }
        long mathTime = System.nanoTime() - time;
        report("expm1",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }

    @Test
    public void testAbs_() {
        double x = 0;
        long time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += StrictMath.abs(i * (1 - 0.5 * RUNS));
        }
        long strictTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += FastMath.abs(i * (1 - 0.5 * RUNS));
        }
        long fastTime = System.nanoTime() - time;

        x = 0;
        time = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            x += Math.abs(i * (1 - 0.5 * RUNS));
        }
        long mathTime = System.nanoTime() - time;

        report("abs",strictTime,fastTime,mathTime);
        Assert.assertTrue(!Double.isNaN(x));
    }
}

/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package misc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * A place for some of the new {@link java.util.Arrays} methods added in Java 9
 * (partially) to Java 11.
 */
public final class J9Arrays {

    /**
     * Returns true if the two specified arrays of Objects, over the specified
     * ranges, are <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if the number of elements covered by each
     * range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal. In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * <p>
     * Two objects {@code e1} and {@code e2} are considered <i>equal</i> if
     * {@code Objects.equals(e1, e2)}.
     *
     * @param a
     *            the first array to be tested for equality
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for equality
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return {@code true} if the two arrays, over the specified ranges, are
     *         equal
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static boolean equals(Object[] a, int aFromIndex, int aToIndex, Object[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength)
            return false;

        for (int i = 0; i < aLength; i++) {
            if (!Objects.equals(a[aFromIndex++], b[bFromIndex++]))
                return false;
        }

        return true;
    }

    /**
     * Returns {@code true} if the two specified arrays of Objects are
     * <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if both arrays contain the same number of
     * elements, and all corresponding pairs of elements in the two arrays are
     * equal. In other words, the two arrays are equal if they contain the same
     * elements in the same order. Also, two array references are considered
     * equal if both are {@code null}.
     *
     * <p>
     * Two objects {@code e1} and {@code e2} are considered <i>equal</i> if,
     * given the specified comparator, {@code cmp.compare(e1, e2) == 0}.
     *
     * @param a
     *            one array to be tested for equality
     * @param a2
     *            the other array to be tested for equality
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return {@code true} if the two arrays are equal
     * @throws NullPointerException
     *             if the comparator is {@code null}
     * @since 9
     */
    public static <T> boolean equals(T[] a, T[] a2, Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        if (a == a2)
            return true;
        if (a == null || a2 == null)
            return false;

        int length = a.length;
        if (a2.length != length)
            return false;

        for (int i = 0; i < length; i++) {
            if (cmp.compare(a[i], a2[i]) != 0)
                return false;
        }

        return true;
    }

    /**
     * Returns true if the two specified arrays of Objects, over the specified
     * ranges, are <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if the number of elements covered by each
     * range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal. In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * <p>
     * Two objects {@code e1} and {@code e2} are considered <i>equal</i> if,
     * given the specified comparator, {@code cmp.compare(e1, e2) == 0}.
     *
     * @param a
     *            the first array to be tested for equality
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for equality
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return {@code true} if the two arrays, over the specified ranges, are
     *         equal
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array or the comparator is {@code null}
     * @since 9
     */
    public static <T> boolean equals(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex,
            Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength)
            return false;

        for (int i = 0; i < aLength; i++) {
            if (cmp.compare(a[aFromIndex++], b[bFromIndex++]) != 0)
                return false;
        }

        return true;
    }

    /**
     * Compares two {@code Object} arrays, within comparable elements,
     * lexicographically.
     *
     * <p>
     * If the two arrays share a common prefix then the lexicographic comparison
     * is the result of comparing two elements of type {@code T} at an index
     * {@code i} within the respective arrays that is the prefix length, as if
     * by:
     * 
     * <pre>
     * {@code
     *     Comparator.nullsFirst(Comparator.<T>naturalOrder()).
     *         compare(a[i], b[i])
     * }
     * </pre>
     * 
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two array lengths. (See
     * {@link #mismatch(Object[], Object[])} for the definition of a common and
     * proper prefix.)
     *
     * <p>
     * A {@code null} array reference is considered lexicographically less than
     * a non-{@code null} array reference. Two {@code null} array references are
     * considered equal. A {@code null} array element is considered
     * lexicographically less than a non-{@code null} array element. Two
     * {@code null} array elements are considered equal.
     *
     * <p>
     * The comparison is consistent with
     * {@link java.util.Arrays#equals(Object[], Object[]) equals}, more
     * specifically the following holds for arrays {@code a} and {@code b}:
     * 
     * <pre>
     * {@code
     *     java.util.Arrays.equals(a, b) == (J9Arrays.compare(a, b) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array references and
     * elements):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, b);
     *  if (i >= 0 && i < Math.min(a.length, b.length))
     *      return a[i].compareTo(b[i]);
     *  return a.length - b.length;
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param b
     *            the second array to compare
     * @param <T>
     *            the type of comparable array elements
     * @return the value {@code 0} if the first and second array are equal and
     *         contain the same elements in the same order; a value less than
     *         {@code 0} if the first array is lexicographically less than the
     *         second array; and a value greater than {@code 0} if the first
     *         array is lexicographically greater than the second array
     * @since 9
     */
    public static <T extends Comparable<? super T>> int compare(T[] a, T[] b) {
        if (a == b)
            return 0;
        // A null array is less than a non-null array
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            T oa = a[i];
            T ob = b[i];
            if (oa != ob) {
                // A null element is less than a non-null element
                if (oa == null || ob == null)
                    return oa == null ? -1 : 1;
                int v = oa.compareTo(ob);
                if (v != 0) {
                    return v;
                }
            }
        }

        return a.length - b.length;
    }

    /**
     * Compares two {@code Object} arrays lexicographically over the specified
     * ranges.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the lexicographic comparison is the result of comparing two elements of
     * type {@code T} at a relative index {@code i} within the respective arrays
     * that is the prefix length, as if by:
     * 
     * <pre>
     * {@code
     *     Comparator.nullsFirst(Comparator.<T>naturalOrder()).
     *         compare(a[aFromIndex + i, b[bFromIndex + i])
     * }
     * </pre>
     * 
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two range lengths. (See
     * {@link #mismatch(Object[], int, int, Object[], int, int)} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * The comparison is consistent with
     * {@link #equals(Object[], int, int, Object[], int, int) equals}, more
     * specifically the following holds for arrays {@code a} and {@code b} with
     * specified ranges [{@code aFromIndex}, {@code atoIndex}) and
     * [{@code bFromIndex}, {@code btoIndex}) respectively:
     * 
     * <pre>
     * {@code
     *     J9Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     *         (J9Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array elements):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
     *  if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     *      return a[aFromIndex + i].compareTo(b[bFromIndex + i]);
     *  return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be compared
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be compared
     * @param b
     *            the second array to compare
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be compared
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be compared
     * @param <T>
     *            the type of comparable array elements
     * @return the value {@code 0} if, over the specified ranges, the first and
     *         second array are equal and contain the same elements in the same
     *         order; a value less than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically less than the second array;
     *         and a value greater than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically greater than the second
     *         array
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static <T extends Comparable<? super T>> int compare(T[] a, int aFromIndex, int aToIndex, T[] b,
            int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            T oa = a[aFromIndex++];
            T ob = b[bFromIndex++];
            if (oa != ob) {
                if (oa == null || ob == null)
                    return oa == null ? -1 : 1;
                int v = oa.compareTo(ob);
                if (v != 0) {
                    return v;
                }
            }
        }

        return aLength - bLength;
    }

    /**
     * Compares two {@code Object} arrays lexicographically using a specified
     * comparator.
     *
     * <p>
     * If the two arrays share a common prefix then the lexicographic comparison
     * is the result of comparing with the specified comparator two elements at
     * an index within the respective arrays that is the prefix length.
     * Otherwise, one array is a proper prefix of the other and, lexicographic
     * comparison is the result of comparing the two array lengths. (See
     * {@link #mismatch(Object[], Object[])} for the definition of a common and
     * proper prefix.)
     *
     * <p>
     * A {@code null} array reference is considered lexicographically less than
     * a non-{@code null} array reference. Two {@code null} array references are
     * considered equal.
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array references):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, b, cmp);
     *  if (i >= 0 && i < Math.min(a.length, b.length))
     *      return cmp.compare(a[i], b[i]);
     *  return a.length - b.length;
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param b
     *            the second array to compare
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return the value {@code 0} if the first and second array are equal and
     *         contain the same elements in the same order; a value less than
     *         {@code 0} if the first array is lexicographically less than the
     *         second array; and a value greater than {@code 0} if the first
     *         array is lexicographically greater than the second array
     * @throws NullPointerException
     *             if the comparator is {@code null}
     * @since 9
     */
    public static <T> int compare(T[] a, T[] b, Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        if (a == b)
            return 0;
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            T oa = a[i];
            T ob = b[i];
            if (oa != ob) {
                // Null-value comparison is deferred to the comparator
                int v = cmp.compare(oa, ob);
                if (v != 0) {
                    return v;
                }
            }
        }

        return a.length - b.length;
    }

    /**
     * Compares two {@code Object} arrays lexicographically over the specified
     * ranges.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the lexicographic comparison is the result of comparing with the
     * specified comparator two elements at a relative index within the
     * respective arrays that is the prefix length. Otherwise, one array is a
     * proper prefix of the other and, lexicographic comparison is the result of
     * comparing the two range lengths. (See
     * {@link #mismatch(Object[], int, int, Object[], int, int)} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array elements):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex, cmp);
     *  if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     *      return cmp.compare(a[aFromIndex + i], b[bFromIndex + i]);
     *  return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be compared
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be compared
     * @param b
     *            the second array to compare
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be compared
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be compared
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return the value {@code 0} if, over the specified ranges, the first and
     *         second array are equal and contain the same elements in the same
     *         order; a value less than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically less than the second array;
     *         and a value greater than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically greater than the second
     *         array
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array or the comparator is {@code null}
     * @since 9
     */
    public static <T> int compare(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex,
            Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            T oa = a[aFromIndex++];
            T ob = b[bFromIndex++];
            if (oa != ob) {
                // Null-value comparison is deferred to the comparator
                int v = cmp.compare(oa, ob);
                if (v != 0) {
                    return v;
                }
            }
        }

        return aLength - bLength;
    }

    /**
     * Finds and returns the index of the first mismatch between two
     * {@code Object} arrays, otherwise return -1 if no mismatch is found. The
     * index will be in the range of 0 (inclusive) up to the length (inclusive)
     * of the smaller array.
     *
     * <p>
     * If the two arrays share a common prefix then the returned index is the
     * length of the common prefix and it follows that there is a mismatch
     * between the two elements at that index within the respective arrays. If
     * one array is a proper prefix of the other then the returned index is the
     * length of the smaller array and it follows that the index is only valid
     * for the larger array. Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a common
     * prefix of length {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(a.length, b.length) &&
     *     J9Arrays.equals(a, 0, pl, b, 0, pl) &&
     *     !Objects.equals(a[pl], b[pl])
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a proper
     * prefix if the following expression is true:
     * 
     * <pre>
     * {@code
     *     a.length != b.length &&
     *     J9Arrays.equals(a, 0, Math.min(a.length, b.length),
     *                     b, 0, Math.min(a.length, b.length))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param b
     *            the second array to be tested for a mismatch
     * @return the index of the first mismatch between the two arrays, otherwise
     *         {@code -1}.
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(Object[] a, Object[] b) {
        int length = Math.min(a.length, b.length); // Check null array refs
        if (a == b)
            return -1;

        for (int i = 0; i < length; i++) {
            if (!Objects.equals(a[i], b[i]))
                return i;
        }

        return a.length != b.length ? length : -1;
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * {@code Object} arrays over the specified ranges, otherwise return -1 if
     * no mismatch is found. The index will be in the range of 0 (inclusive) up
     * to the length (inclusive) of the smaller range.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the returned relative index is the length of the common prefix and it
     * follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays. If one array is a proper
     * prefix of the other, over the specified ranges, then the returned
     * relative index is the length of the smaller range and it follows that the
     * relative index is only valid for the array with the larger range.
     * Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a common prefix of length
     * {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl) &&
     *     !Objects.equals(a[aFromIndex + pl], b[bFromIndex + pl])
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a proper prefix if the following
     * expression is true:
     * 
     * <pre>
     * {@code
     *     (aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for a mismatch
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return the relative index of the first mismatch between the two arrays
     *         over the specified ranges, otherwise {@code -1}.
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(Object[] a, int aFromIndex, int aToIndex, Object[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(a[aFromIndex++], b[bFromIndex++]))
                return i;
        }

        return aLength != bLength ? length : -1;
    }

    /**
     * Finds and returns the index of the first mismatch between two
     * {@code Object} arrays, otherwise return -1 if no mismatch is found. The
     * index will be in the range of 0 (inclusive) up to the length (inclusive)
     * of the smaller array.
     *
     * <p>
     * The specified comparator is used to determine if two array elements from
     * the each array are not equal.
     *
     * <p>
     * If the two arrays share a common prefix then the returned index is the
     * length of the common prefix and it follows that there is a mismatch
     * between the two elements at that index within the respective arrays. If
     * one array is a proper prefix of the other then the returned index is the
     * length of the smaller array and it follows that the index is only valid
     * for the larger array. Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a common
     * prefix of length {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(a.length, b.length) &&
     *     J9Arrays.equals(a, 0, pl, b, 0, pl, cmp)
     *     cmp.compare(a[pl], b[pl]) != 0
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a proper
     * prefix if the following expression is true:
     * 
     * <pre>
     * {@code
     *     a.length != b.length &&
     *     J9Arrays.equals(a, 0, Math.min(a.length, b.length),
     *                     b, 0, Math.min(a.length, b.length),
     *                     cmp)
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param b
     *            the second array to be tested for a mismatch
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return the index of the first mismatch between the two arrays, otherwise
     *         {@code -1}.
     * @throws NullPointerException
     *             if either array or the comparator is {@code null}
     * @since 9
     */
    public static <T> int mismatch(T[] a, T[] b, Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        int length = Math.min(a.length, b.length); // Check null array refs
        if (a == b)
            return -1;

        for (int i = 0; i < length; i++) {
            T oa = a[i];
            T ob = b[i];
            if (oa != ob) {
                // Null-value comparison is deferred to the comparator
                int v = cmp.compare(oa, ob);
                if (v != 0) {
                    return i;
                }
            }
        }

        return a.length != b.length ? length : -1;
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * {@code Object} arrays over the specified ranges, otherwise return -1 if
     * no mismatch is found. The index will be in the range of 0 (inclusive) up
     * to the length (inclusive) of the smaller range.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the returned relative index is the length of the common prefix and it
     * follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays. If one array is a proper
     * prefix of the other, over the specified ranges, then the returned
     * relative index is the length of the smaller range and it follows that the
     * relative index is only valid for the array with the larger range.
     * Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a common prefix of length
     * {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl, cmp) &&
     *     cmp.compare(a[aFromIndex + pl], b[bFromIndex + pl]) != 0
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a proper prefix if the following
     * expression is true:
     * 
     * <pre>
     * {@code
     *     (aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     cmp)
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for a mismatch
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @param cmp
     *            the comparator to compare array elements
     * @param <T>
     *            the type of array elements
     * @return the relative index of the first mismatch between the two arrays
     *         over the specified ranges, otherwise {@code -1}.
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array or the comparator is {@code null}
     * @since 9
     */
    public static <T> int mismatch(T[] a, int aFromIndex, int aToIndex, T[] b, int bFromIndex, int bToIndex,
            Comparator<? super T> cmp) {
        Objects.requireNonNull(cmp);
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            T oa = a[aFromIndex++];
            T ob = b[bFromIndex++];
            if (oa != ob) {
                // Null-value comparison is deferred to the comparator
                int v = cmp.compare(oa, ob);
                if (v != 0) {
                    return i;
                }
            }
        }

        return aLength != bLength ? length : -1;
    }

    /**
     * Returns true if the two specified arrays of longs, over the specified
     * ranges, are <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if the number of elements covered by each
     * range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal. In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * @param a
     *            the first array to be tested for equality
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for equality
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return {@code true} if the two arrays, over the specified ranges, are
     *         equal
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static boolean equals(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength)
            return false;

        for (int i = 0; i < aLength; i++)
            if (a[aFromIndex++] != b[bFromIndex++])
                return false;

        return true;
    }

    /**
     * Returns true if the two specified arrays of ints, over the specified
     * ranges, are <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if the number of elements covered by each
     * range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal. In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * @param a
     *            the first array to be tested for equality
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for equality
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return {@code true} if the two arrays, over the specified ranges, are
     *         equal
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static boolean equals(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength)
            return false;

        for (int i = 0; i < aLength; i++)
            if (a[aFromIndex++] != b[bFromIndex++])
                return false;

        return true;
    }

    /**
     * Returns true if the two specified arrays of doubles, over the specified
     * ranges, are <i>equal</i> to one another.
     *
     * <p>
     * Two arrays are considered equal if the number of elements covered by each
     * range is the same, and all corresponding pairs of elements over the
     * specified ranges in the two arrays are equal. In other words, two arrays
     * are equal if they contain, over the specified ranges, the same elements
     * in the same order.
     *
     * <p>
     * Two doubles {@code d1} and {@code d2} are considered equal if:
     * 
     * <pre>
     *     {@code new Double(d1).equals(new Double(d2))}
     * </pre>
     * 
     * (Unlike the {@code ==} operator, this method considers {@code NaN} equals
     * to itself, and 0.0d unequal to -0.0d.)
     *
     * @param a
     *            the first array to be tested for equality
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for equality
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return {@code true} if the two arrays, over the specified ranges, are
     *         equal
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @see Double#equals(Object)
     * @since 9
     */
    public static boolean equals(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        if (aLength != bLength)
            return false;

        for (int i = 0; i < aLength; i++) {
            double va = a[aFromIndex++], vb = b[bFromIndex++];
            if (Double.doubleToRawLongBits(va) != Double.doubleToRawLongBits(vb))
                if (!Double.isNaN(va) || !Double.isNaN(vb))
                    return false;
        }

        return true;
    }

    // Compare int

    /**
     * Compares two {@code int} arrays lexicographically.
     *
     * <p>
     * If the two arrays share a common prefix then the lexicographic comparison
     * is the result of comparing two elements, as if by
     * {@link Integer#compare(int, int)}, at an index within the respective
     * arrays that is the prefix length. Otherwise, one array is a proper prefix
     * of the other and, lexicographic comparison is the result of comparing the
     * two array lengths. (See {@link #mismatch(int[], int[])} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * A {@code null} array reference is considered lexicographically less than
     * a non-{@code null} array reference. Two {@code null} array references are
     * considered equal.
     *
     * <p>
     * The comparison is consistent with
     * {@link java.util.Arrays#equals(int[], int[]) Arrays.equals}, more
     * specifically the following holds for arrays {@code a} and {@code b}:
     * 
     * <pre>
     * {@code
     *     java.util.Arrays.equals(a, b) == (J9Arrays.compare(a, b) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array references):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, b);
     *  if (i >= 0 && i < Math.min(a.length, b.length))
     *      return Integer.compare(a[i], b[i]);
     *  return a.length - b.length;
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param b
     *            the second array to compare
     * @return the value {@code 0} if the first and second array are equal and
     *         contain the same elements in the same order; a value less than
     *         {@code 0} if the first array is lexicographically less than the
     *         second array; and a value greater than {@code 0} if the first
     *         array is lexicographically greater than the second array
     * @since 9
     */
    public static int compare(int[] a, int[] b) {
        if (a == b)
            return 0;
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i])
                return Integer.compare(a[i], b[i]);
        }

        return a.length - b.length;
    }

    /**
     * Compares two {@code int} arrays lexicographically over the specified
     * ranges.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the lexicographic comparison is the result of comparing two elements, as
     * if by {@link Integer#compare(int, int)}, at a relative index within the
     * respective arrays that is the length of the prefix. Otherwise, one array
     * is a proper prefix of the other and, lexicographic comparison is the
     * result of comparing the two range lengths. (See
     * {@link #mismatch(int[], int, int, int[], int, int)} for the definition of
     * a common and proper prefix.)
     *
     * <p>
     * The comparison is consistent with
     * {@link #equals(int[], int, int, int[], int, int) equals}, more
     * specifically the following holds for arrays {@code a} and {@code b} with
     * specified ranges [{@code aFromIndex}, {@code atoIndex}) and
     * [{@code bFromIndex}, {@code btoIndex}) respectively:
     * 
     * <pre>
     * {@code
     *     J9Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     *         (J9Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if:
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
     *  if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     *      return Integer.compare(a[aFromIndex + i], b[bFromIndex + i]);
     *  return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be compared
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be compared
     * @param b
     *            the second array to compare
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be compared
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be compared
     * @return the value {@code 0} if, over the specified ranges, the first and
     *         second array are equal and contain the same elements in the same
     *         order; a value less than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically less than the second array;
     *         and a value greater than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically greater than the second
     *         array
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int compare(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            int va = a[aFromIndex++];
            int vb = b[bFromIndex++];
            if (va != vb)
                return Integer.compare(va, vb);
        }

        return aLength - bLength;
    }

    // Compare long

    /**
     * Compares two {@code long} arrays lexicographically.
     *
     * <p>
     * If the two arrays share a common prefix then the lexicographic comparison
     * is the result of comparing two elements, as if by
     * {@link Long#compare(long, long)}, at an index within the respective
     * arrays that is the prefix length. Otherwise, one array is a proper prefix
     * of the other and, lexicographic comparison is the result of comparing the
     * two array lengths. (See {@link #mismatch(long[], long[])} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * A {@code null} array reference is considered lexicographically less than
     * a non-{@code null} array reference. Two {@code null} array references are
     * considered equal.
     *
     * <p>
     * The comparison is consistent with
     * {@link java.util.Arrays#equals(long[], long[]) Arrays.equals}, more
     * specifically the following holds for arrays {@code a} and {@code b}:
     * 
     * <pre>
     * {@code
     *     java.util.Arrays.equals(a, b) == (J9Arrays.compare(a, b) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array references):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, b);
     *  if (i >= 0 && i < Math.min(a.length, b.length))
     *      return Long.compare(a[i], b[i]);
     *  return a.length - b.length;
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param b
     *            the second array to compare
     * @return the value {@code 0} if the first and second array are equal and
     *         contain the same elements in the same order; a value less than
     *         {@code 0} if the first array is lexicographically less than the
     *         second array; and a value greater than {@code 0} if the first
     *         array is lexicographically greater than the second array
     * @since 9
     */
    public static int compare(long[] a, long[] b) {
        if (a == b)
            return 0;
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            if (a[i] != b[i])
                return Long.compare(a[i], b[i]);
        }

        return a.length - b.length;
    }

    /**
     * Compares two {@code long} arrays lexicographically over the specified
     * ranges.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the lexicographic comparison is the result of comparing two elements, as
     * if by {@link Long#compare(long, long)}, at a relative index within the
     * respective arrays that is the length of the prefix. Otherwise, one array
     * is a proper prefix of the other and, lexicographic comparison is the
     * result of comparing the two range lengths. (See
     * {@link #mismatch(long[], int, int, long[], int, int)} for the definition
     * of a common and proper prefix.)
     *
     * <p>
     * The comparison is consistent with
     * {@link #equals(long[], int, int, long[], int, int) equals}, more
     * specifically the following holds for arrays {@code a} and {@code b} with
     * specified ranges [{@code aFromIndex}, {@code atoIndex}) and
     * [{@code bFromIndex}, {@code btoIndex}) respectively:
     * 
     * <pre>
     * {@code
     *     J9Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     *         (J9Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if:
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
     *  if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     *      return Long.compare(a[aFromIndex + i], b[bFromIndex + i]);
     *  return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be compared
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be compared
     * @param b
     *            the second array to compare
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be compared
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be compared
     * @return the value {@code 0} if, over the specified ranges, the first and
     *         second array are equal and contain the same elements in the same
     *         order; a value less than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically less than the second array;
     *         and a value greater than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically greater than the second
     *         array
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int compare(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            long va = a[aFromIndex++];
            long vb = b[bFromIndex++];
            if (va != vb)
                return Long.compare(va, vb);
        }

        return aLength - bLength;
    }

    // Compare double

    /**
     * Compares two {@code double} arrays lexicographically.
     *
     * <p>
     * If the two arrays share a common prefix then the lexicographic comparison
     * is the result of comparing two elements, as if by
     * {@link Double#compare(double, double)}, at an index within the respective
     * arrays that is the prefix length. Otherwise, one array is a proper prefix
     * of the other and, lexicographic comparison is the result of comparing the
     * two array lengths. (See {@link #mismatch(double[], double[])} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * A {@code null} array reference is considered lexicographically less than
     * a non-{@code null} array reference. Two {@code null} array references are
     * considered equal.
     *
     * <p>
     * The comparison is consistent with
     * {@link java.util.Arrays#equals(double[], double[]) Arrays.equals}, more
     * specifically the following holds for arrays {@code a} and {@code b}:
     * 
     * <pre>
     * {@code
     *     java.util.Arrays.equals(a, b) == (J9Arrays.compare(a, b) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if (for non-{@code null} array references):
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, b);
     *  if (i >= 0 && i < Math.min(a.length, b.length))
     *      return Double.compare(a[i], b[i]);
     *  return a.length - b.length;
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param b
     *            the second array to compare
     * @return the value {@code 0} if the first and second array are equal and
     *         contain the same elements in the same order; a value less than
     *         {@code 0} if the first array is lexicographically less than the
     *         second array; and a value greater than {@code 0} if the first
     *         array is lexicographically greater than the second array
     * @since 9
     */
    public static int compare(double[] a, double[] b) {
        if (a == b)
            return 0;
        if (a == null || b == null)
            return a == null ? -1 : 1;

        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            double va = a[i], vb = b[i];
            if (Double.doubleToRawLongBits(va) != Double.doubleToRawLongBits(vb)) {
                int c = Double.compare(va, vb);
                if (c != 0)
                    return c;
            }
        }

        return a.length - b.length;
    }

    /**
     * Compares two {@code double} arrays lexicographically over the specified
     * ranges.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the lexicographic comparison is the result of comparing two elements, as
     * if by {@link Double#compare(double, double)}, at a relative index within
     * the respective arrays that is the length of the prefix. Otherwise, one
     * array is a proper prefix of the other and, lexicographic comparison is
     * the result of comparing the two range lengths. (See
     * {@link #mismatch(double[], int, int, double[], int, int)} for the
     * definition of a common and proper prefix.)
     *
     * <p>
     * The comparison is consistent with
     * {@link #equals(double[], int, int, double[], int, int) equals}, more
     * specifically the following holds for arrays {@code a} and {@code b} with
     * specified ranges [{@code aFromIndex}, {@code atoIndex}) and
     * [{@code bFromIndex}, {@code btoIndex}) respectively:
     * 
     * <pre>
     * {@code
     *     J9Arrays.equals(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) ==
     *         (J9Arrays.compare(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex) == 0)
     * }
     * </pre>
     *
     * <p>
     * <b>API Note:</b><br>
     * <p>
     * This method behaves as if:
     * 
     * <pre>
     * {
     *  &#64;code
     *  int i = J9Arrays.mismatch(a, aFromIndex, aToIndex, b, bFromIndex, bToIndex);
     *  if (i >= 0 && i < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     *      return Double.compare(a[aFromIndex + i], b[bFromIndex + i]);
     *  return (aToIndex - aFromIndex) - (bToIndex - bFromIndex);
     * }
     * </pre>
     *
     * @param a
     *            the first array to compare
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be compared
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be compared
     * @param b
     *            the second array to compare
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be compared
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be compared
     * @return the value {@code 0} if, over the specified ranges, the first and
     *         second array are equal and contain the same elements in the same
     *         order; a value less than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically less than the second array;
     *         and a value greater than {@code 0} if, over the specified ranges,
     *         the first array is lexicographically greater than the second
     *         array
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int compare(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            double va = a[aFromIndex++], vb = b[bFromIndex++];
            if (Double.doubleToRawLongBits(va) != Double.doubleToRawLongBits(vb)) {
                int c = Double.compare(va, vb);
                if (c != 0)
                    return c;
            }
        }

        return aLength - bLength;
    }

    // Mismatch int

    /**
     * Finds and returns the index of the first mismatch between two {@code int}
     * arrays, otherwise return -1 if no mismatch is found. The index will be in
     * the range of 0 (inclusive) up to the length (inclusive) of the smaller
     * array.
     *
     * <p>
     * If the two arrays share a common prefix then the returned index is the
     * length of the common prefix and it follows that there is a mismatch
     * between the two elements at that index within the respective arrays. If
     * one array is a proper prefix of the other then the returned index is the
     * length of the smaller array and it follows that the index is only valid
     * for the larger array. Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a common
     * prefix of length {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(a.length, b.length) &&
     *     J9Arrays.equals(a, 0, pl, b, 0, pl) &&
     *     a[pl] != b[pl]
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a proper
     * prefix if the following expression is true:
     * 
     * <pre>
     * {@code
     *     a.length != b.length &&
     *     J9Arrays.equals(a, 0, Math.min(a.length, b.length),
     *                     b, 0, Math.min(a.length, b.length))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param b
     *            the second array to be tested for a mismatch
     * @return the index of the first mismatch between the two arrays, otherwise
     *         {@code -1}.
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(int[] a, int[] b) {
        int length = Math.min(a.length, b.length); // Check null array refs
        if (a == b)
            return -1;

        for (int i = 0; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }

        return a.length != b.length ? length : -1;
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * {@code int} arrays over the specified ranges, otherwise return -1 if no
     * mismatch is found. The index will be in the range of 0 (inclusive) up to
     * the length (inclusive) of the smaller range.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the returned relative index is the length of the common prefix and it
     * follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays. If one array is a proper
     * prefix of the other, over the specified ranges, then the returned
     * relative index is the length of the smaller range and it follows that the
     * relative index is only valid for the array with the larger range.
     * Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a common prefix of length
     * {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl) &&
     *     a[aFromIndex + pl] != b[bFromIndex + pl]
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a proper prefix if the following
     * expression is true:
     * 
     * <pre>
     * {@code
     *     (aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for a mismatch
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return the relative index of the first mismatch between the two arrays
     *         over the specified ranges, otherwise {@code -1}.
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(int[] a, int aFromIndex, int aToIndex, int[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            if (a[aFromIndex++] != b[bFromIndex++])
                return i;
        }

        return aLength != bLength ? length : -1;
    }

    // Mismatch long

    /**
     * Finds and returns the index of the first mismatch between two
     * {@code long} arrays, otherwise return -1 if no mismatch is found. The
     * index will be in the range of 0 (inclusive) up to the length (inclusive)
     * of the smaller array.
     *
     * <p>
     * If the two arrays share a common prefix then the returned index is the
     * length of the common prefix and it follows that there is a mismatch
     * between the two elements at that index within the respective arrays. If
     * one array is a proper prefix of the other then the returned index is the
     * length of the smaller array and it follows that the index is only valid
     * for the larger array. Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a common
     * prefix of length {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(a.length, b.length) &&
     *     J9Arrays.equals(a, 0, pl, b, 0, pl) &&
     *     a[pl] != b[pl]
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a proper
     * prefix if the following expression is true:
     * 
     * <pre>
     * {@code
     *     a.length != b.length &&
     *     J9Arrays.equals(a, 0, Math.min(a.length, b.length),
     *                     b, 0, Math.min(a.length, b.length))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param b
     *            the second array to be tested for a mismatch
     * @return the index of the first mismatch between the two arrays, otherwise
     *         {@code -1}.
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(long[] a, long[] b) {
        int length = Math.min(a.length, b.length); // Check null array refs
        if (a == b)
            return -1;

        for (int i = 0; i < length; i++) {
            if (a[i] != b[i])
                return i;
        }

        return a.length != b.length ? length : -1;
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * {@code long} arrays over the specified ranges, otherwise return -1 if no
     * mismatch is found. The index will be in the range of 0 (inclusive) up to
     * the length (inclusive) of the smaller range.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the returned relative index is the length of the common prefix and it
     * follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays. If one array is a proper
     * prefix of the other, over the specified ranges, then the returned
     * relative index is the length of the smaller range and it follows that the
     * relative index is only valid for the array with the larger range.
     * Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a common prefix of length
     * {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl) &&
     *     a[aFromIndex + pl] != b[bFromIndex + pl]
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a proper prefix if the following
     * expression is true:
     * 
     * <pre>
     * {@code
     *     (aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for a mismatch
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return the relative index of the first mismatch between the two arrays
     *         over the specified ranges, otherwise {@code -1}.
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(long[] a, int aFromIndex, int aToIndex, long[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            if (a[aFromIndex++] != b[bFromIndex++])
                return i;
        }

        return aLength != bLength ? length : -1;
    }

    // Mismatch double

    /**
     * Finds and returns the index of the first mismatch between two
     * {@code double} arrays, otherwise return -1 if no mismatch is found. The
     * index will be in the range of 0 (inclusive) up to the length (inclusive)
     * of the smaller array.
     *
     * <p>
     * If the two arrays share a common prefix then the returned index is the
     * length of the common prefix and it follows that there is a mismatch
     * between the two elements at that index within the respective arrays. If
     * one array is a proper prefix of the other then the returned index is the
     * length of the smaller array and it follows that the index is only valid
     * for the larger array. Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a common
     * prefix of length {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(a.length, b.length) &&
     *     J9Arrays.equals(a, 0, pl, b, 0, pl) &&
     *     Double.compare(a[pl], b[pl]) != 0
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b}, share a proper
     * prefix if the following expression is true:
     * 
     * <pre>
     * {@code
     *     a.length != b.length &&
     *     J9Arrays.equals(a, 0, Math.min(a.length, b.length),
     *                     b, 0, Math.min(a.length, b.length))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param b
     *            the second array to be tested for a mismatch
     * @return the index of the first mismatch between the two arrays, otherwise
     *         {@code -1}.
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(double[] a, double[] b) {
        int length = Math.min(a.length, b.length); // Check null array refs
        if (a == b)
            return -1;

        for (int i = 0; i < length; i++) {
            double va = a[i], vb = b[i];
            if (Double.doubleToRawLongBits(va) != Double.doubleToRawLongBits(vb))
                if (!Double.isNaN(va) || !Double.isNaN(vb))
                    return i;
        }

        return a.length != b.length ? length : -1;
    }

    /**
     * Finds and returns the relative index of the first mismatch between two
     * {@code double} arrays over the specified ranges, otherwise return -1 if
     * no mismatch is found. The index will be in the range of 0 (inclusive) up
     * to the length (inclusive) of the smaller range.
     *
     * <p>
     * If the two arrays, over the specified ranges, share a common prefix then
     * the returned relative index is the length of the common prefix and it
     * follows that there is a mismatch between the two elements at that
     * relative index within the respective arrays. If one array is a proper
     * prefix of the other, over the specified ranges, then the returned
     * relative index is the length of the smaller range and it follows that the
     * relative index is only valid for the array with the larger range.
     * Otherwise, there is no mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a common prefix of length
     * {@code pl} if the following expression is true:
     * 
     * <pre>
     * {@code
     *     pl >= 0 &&
     *     pl < Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, aFromIndex, aFromIndex + pl, b, bFromIndex, bFromIndex + pl) &&
     *     Double.compare(a[aFromIndex + pl], b[bFromIndex + pl]) != 0
     * }
     * </pre>
     * 
     * Note that a common prefix length of {@code 0} indicates that the first
     * elements from each array mismatch.
     *
     * <p>
     * Two non-{@code null} arrays, {@code a} and {@code b} with specified
     * ranges [{@code aFromIndex}, {@code atoIndex}) and [{@code bFromIndex},
     * {@code btoIndex}) respectively, share a proper prefix if the following
     * expression is true:
     * 
     * <pre>
     * {@code
     *     (aToIndex - aFromIndex) != (bToIndex - bFromIndex) &&
     *     J9Arrays.equals(a, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex),
     *                     b, 0, Math.min(aToIndex - aFromIndex, bToIndex - bFromIndex))
     * }
     * </pre>
     *
     * @param a
     *            the first array to be tested for a mismatch
     * @param aFromIndex
     *            the index (inclusive) of the first element in the first array
     *            to be tested
     * @param aToIndex
     *            the index (exclusive) of the last element in the first array
     *            to be tested
     * @param b
     *            the second array to be tested for a mismatch
     * @param bFromIndex
     *            the index (inclusive) of the first element in the second array
     *            to be tested
     * @param bToIndex
     *            the index (exclusive) of the last element in the second array
     *            to be tested
     * @return the relative index of the first mismatch between the two arrays
     *         over the specified ranges, otherwise {@code -1}.
     * @throws IllegalArgumentException
     *             if {@code aFromIndex > aToIndex} or if
     *             {@code bFromIndex > bToIndex}
     * @throws ArrayIndexOutOfBoundsException
     *             if {@code aFromIndex < 0 or aToIndex > a.length} or if
     *             {@code bFromIndex < 0 or bToIndex > b.length}
     * @throws NullPointerException
     *             if either array is {@code null}
     * @since 9
     */
    public static int mismatch(double[] a, int aFromIndex, int aToIndex, double[] b, int bFromIndex, int bToIndex) {
        rangeCheck(a.length, aFromIndex, aToIndex);
        rangeCheck(b.length, bFromIndex, bToIndex);

        int aLength = aToIndex - aFromIndex;
        int bLength = bToIndex - bFromIndex;
        int length = Math.min(aLength, bLength);
        for (int i = 0; i < length; i++) {
            double va = a[aFromIndex++], vb = b[bFromIndex++];
            if (Double.doubleToRawLongBits(va) != Double.doubleToRawLongBits(vb))
                if (!Double.isNaN(va) || !Double.isNaN(vb))
                    return i;
        }

        return aLength != bLength ? length : -1;
    }

    /**
     * Returns an array containing all of the elements in the passed collection,
     * using the provided {@code generator} function to allocate the returned
     * array.
     *
     * <p>
     * If the passed collection makes any guarantees as to what order its
     * elements are returned by its iterator, this method must return the
     * elements in the same order.
     *
     * <p>
     * <b>API Note:</b><br>
     * This method acts as a bridge between array-based and collection-based
     * APIs. It allows creation of an array of a particular runtime type. Use
     * {@link Collection#toArray()} to create an array whose runtime type is
     * {@code Object[]}, or use {@link Collection#toArray(Object[])
     * Collection.toArray(T[])} to reuse an existing array.
     *
     * <p>
     * Suppose {@code x} is a collection known to contain only strings. The
     * following code can be used to dump the collection into a newly allocated
     * array of {@code String}:
     *
     * <pre>
     * String[] y = J9Arrays.toArray(x, String[]::new);
     * </pre>
     *
     * <p>
     * <b>Implementation Requirements:</b><br>
     * The default implementation calls the generator function with zero and
     * then passes the resulting array to {@link Collection#toArray(Object[])
     * Collection.toArray(T[])}.
     *
     * @param <T>
     *            the component type of the array to contain the collection
     * @param col
     *            the collection to work on
     * @param generator
     *            a function which produces a new array of the desired type and
     *            the provided length
     * @return an array containing all of the elements in the passed collection
     * @throws ArrayStoreException
     *             if the runtime type of any element in the passed collection
     *             is not assignable to the {@linkplain Class#getComponentType
     *             runtime component type} of the generated array
     * @throws NullPointerException
     *             if the collection or the generator function is null
     * @since 11
     */
    public static <T> T[] toArray(Collection<T> col, IntFunction<T[]> generator) {
        return col.toArray(generator.apply(0));
    }

    /**
     * Checks that {@code fromIndex} and {@code toIndex} are in the range and
     * throws an exception if they aren't.
     */
    private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (toIndex > arrayLength) {
            throw new ArrayIndexOutOfBoundsException(toIndex);
        }
    }

    private J9Arrays() {
    }
}

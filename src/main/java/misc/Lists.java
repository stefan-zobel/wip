package misc;

import java.util.Collection;
import java.util.List;

/**
 * A place for the <a href="http://openjdk.java.net/jeps/269">JEP 269</a>
 * {@code "Unmodifiable List Static Factory Methods"} in the {@link List}
 * interface that were introduced in Java 9.
 *
 * <h2><a id="unmodifiable">Unmodifiable Lists</a></h2>
 * <p>
 * The {@link Lists#of(Object...) Lists.of} and {@link Lists#copyOf
 * Lists.copyOf} static factory methods provide a convenient way to create
 * unmodifiable lists. The {@code List} instances created by these methods have
 * the following characteristics:
 *
 * <ul>
 * <li>They are
 * <a href="./package-summary.html#unmodifiable"><i>unmodifiable</i></a>.
 * Elements cannot be added, removed, or replaced. Calling any mutator method on
 * the List will always cause {@code UnsupportedOperationException} to be
 * thrown. However, if the contained elements are themselves mutable, this may
 * cause the List's contents to appear to change.
 * <li>They disallow {@code null} elements. Attempts to create them with
 * {@code null} elements result in {@code NullPointerException}.
 * <li>They are serializable if all elements are serializable.
 * <li>The order of elements in the list is the same as the order of the
 * provided arguments, or of the elements in the provided array.
 * <li>They are
 * <a href="../lang/package-summary.html#Value-based-Classes">value-based</a>.
 * Callers should make no assumptions about the identity of the returned
 * instances. Factories are free to create new instances or reuse existing ones.
 * Therefore, identity-sensitive operations on these instances (reference
 * equality ( {@code ==}), identity hash code, and synchronization) are
 * unreliable and should be avoided.
 * </ul>
 */
public final class Lists {
    /**
     * Returns an unmodifiable list containing zero elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @return an empty {@code List}
     *
     * @since 9
     */
    @SuppressWarnings("unchecked")
    public static <E> List<E> of() {
        return (List<E>) ImmutableCollections.ListN.EMPTY_LIST;
    }

    /**
     * Returns an unmodifiable list containing one element.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the single element
     * @return a {@code List} containing the specified element
     * @throws NullPointerException
     *             if the element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1) {
        return new ImmutableCollections.List12<E>(e1);
    }

    /**
     * Returns an unmodifiable list containing two elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2) {
        return new ImmutableCollections.List12<E>(e1, e2);
    }

    /**
     * Returns an unmodifiable list containing three elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3);
    }

    /**
     * Returns an unmodifiable list containing four elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4);
    }

    /**
     * Returns an unmodifiable list containing five elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5);
    }

    /**
     * Returns an unmodifiable list containing six elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @param e6
     *            the sixth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5, e6);
    }

    /**
     * Returns an unmodifiable list containing seven elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @param e6
     *            the sixth element
     * @param e7
     *            the seventh element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5, e6, e7);
    }

    /**
     * Returns an unmodifiable list containing eight elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @param e6
     *            the sixth element
     * @param e7
     *            the seventh element
     * @param e8
     *            the eighth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5, e6, e7, e8);
    }

    /**
     * Returns an unmodifiable list containing nine elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @param e6
     *            the sixth element
     * @param e7
     *            the seventh element
     * @param e8
     *            the eighth element
     * @param e9
     *            the ninth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5, e6, e7, e8, e9);
    }

    /**
     * Returns an unmodifiable list containing ten elements.
     *
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param e1
     *            the first element
     * @param e2
     *            the second element
     * @param e3
     *            the third element
     * @param e4
     *            the fourth element
     * @param e5
     *            the fifth element
     * @param e6
     *            the sixth element
     * @param e7
     *            the seventh element
     * @param e8
     *            the eighth element
     * @param e9
     *            the ninth element
     * @param e10
     *            the tenth element
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null}
     *
     * @since 9
     */
    public static <E> List<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
        return ImmutableCollections.ListN.fromTrustedArray(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
    }

    /**
     * Returns an unmodifiable list containing an arbitrary number of elements.
     * See <a href="#unmodifiable">Unmodifiable Lists</a> for details.
     *
     * <p>
     * <b>API Note:</b><br>
     * This method also accepts a single array as an argument. The element type
     * of the resulting list will be the component type of the array, and the
     * size of the list will be equal to the length of the array. To create a
     * list with a single element that is an array, do the following:
     *
     * <pre>
     * {@code
     *     String[] array = ... ;
     *     List<String[]> list = Lists.<String[]>of(array);
     * }
     * </pre>
     *
     * This will cause the {@link Lists#of(Object) Lists.of(E)} method to be
     * invoked instead.
     *
     * @param <E>
     *            the {@code List}'s element type
     * @param elements
     *            the elements to be contained in the list
     * @return a {@code List} containing the specified elements
     * @throws NullPointerException
     *             if an element is {@code null} or if the array is {@code null}
     *
     * @since 9
     */
    @SafeVarargs
    public static <E> List<E> of(E... elements) {
        switch (elements.length) { // implicit null check of elements
        case 0:
            @SuppressWarnings("unchecked")
            List<E> list = (List<E>) ImmutableCollections.ListN.EMPTY_LIST;
            return list;
        case 1:
            return new ImmutableCollections.List12<E>(elements[0]);
        case 2:
            return new ImmutableCollections.List12<E>(elements[0], elements[1]);
        default:
            return ImmutableCollections.ListN.fromArray(elements);
        }
    }

    /**
     * Returns an <a href="#unmodifiable">unmodifiable List</a> containing the
     * elements of the given Collection, in its iteration order. The given
     * Collection must not be null, and it must not contain any null elements.
     * If the given Collection is subsequently modified, the returned List will
     * not reflect such modifications.
     *
     * <p>
     * <b>Implementation Note:</b> If the given Collection is an
     * <a href="#unmodifiable">unmodifiable List</a>, calling copyOf will
     * generally not create a copy.
     * 
     * @param <E>
     *            the {@code List}'s element type
     * @param coll
     *            the collection from which elements are drawn, must be non-null
     * @return a {@code List} containing the elements of the given
     *         {@code Collection}
     * @throws NullPointerException
     *             if coll is null, or if it contains any nulls
     * @since 10
     */
    public static <E> List<E> copyOf(Collection<? extends E> coll) {
        return ImmutableCollections.listCopy(coll);
    }

    private Lists() {
    }
}

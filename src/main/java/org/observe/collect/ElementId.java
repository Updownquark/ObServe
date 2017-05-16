package org.observe.collect;

import java.util.Comparator;
import java.util.Objects;

/**
 * Although not every ObservableCollection must be indexed, all ObservableCollections must have some notion of order. All change events and
 * spliterator elements from ObservableCollections provide an ElementId that not only uniquely identifies the element in the collection, but
 * allows the element's order relative to other elements to be determined.
 *
 * The equivalence and ordering of ElementIds may not change with its contents or with any other property of the collection. The ElementId
 * must remain valid until the element is removed from the collection, including while the remove event is firing.
 *
 * A collection's iteration must follow this ordering scheme as well, i.e. the ID of each element from the
 * {@link ObservableCollection#spliterator()} method must be successively greater than the previous element
 *
 * @see ObservableCollectionElement#getElementId()
 * @see ObservableCollectionEvent#getElementId()
 */
public interface ElementId extends Comparable<ElementId> {
	/**
	 * Creates an ElementId from a comparable value
	 *
	 * @param value The comparable value to back the element ID
	 * @return An ElementId backed by the comparable value
	 */
	static <T> ElementId of(Comparable<T> value) {
		class ComparableElementId implements ElementId {
			private final Comparable<T> theValue = value;

			@Override
			public int compareTo(ElementId o) {
				return theValue.compareTo((T) ((ComparableElementId) o).theValue);
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theValue);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ComparableElementId && Objects.equals(theValue, ((ComparableElementId) obj).theValue);
			}

			@Override
			public String toString() {
				return String.valueOf(theValue);
			}
		}
		return new ComparableElementId();
	}

	/**
	 * Creates an ElementId from a value and a comparator
	 *
	 * @param value The value to back the element ID
	 * @param compare The comparator to compare element IDs
	 * @return An ElementId backed by the value and compared by the comparator
	 */
	static <T> ElementId of(T value, Comparator<? super T> compare) {
		class ComparatorElementId implements ElementId {
			private final T theValue = value;

			@Override
			public int compareTo(ElementId o) {
				return compare.compare(theValue, ((ComparatorElementId) o).theValue);
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theValue);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ComparatorElementId && Objects.equals(theValue, ((ComparatorElementId) obj).theValue);
			}

			@Override
			public String toString() {
				return String.valueOf(theValue);
			}
		}
		return new ComparatorElementId();
	}
}

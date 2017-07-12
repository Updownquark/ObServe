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
	/** @return An element ID that behaves like this one, but orders in reverse */
	default ElementId reverse() {
		class ReversedElementId implements ElementId {
			private final ElementId theWrapped;

			ReversedElementId(ElementId wrap) {
				theWrapped = wrap;
			}

			@Override
			public int compareTo(ElementId o) {
				return -theWrapped.compareTo(((ReversedElementId) o).theWrapped);
			}

			@Override
			public ElementId reverse() {
				return theWrapped;
			}

			@Override
			public int hashCode() {
				return theWrapped.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ReversedElementId && theWrapped.equals(((ReversedElementId) obj).theWrapped);
			}

			@Override
			public String toString() {
				return theWrapped.toString();
			}
		}
		return new ReversedElementId(this);
	}

	/**
	 * Creates an ElementId from a comparable value
	 *
	 * @param value The comparable value to back the element ID
	 * @return An ElementId backed by the comparable value
	 */
	static <T extends Comparable<T>> ElementId of(T value) {
		return new ComparableElementId<>(value);
	}

	/**
	 * Creates an ElementId from a value and a comparator
	 *
	 * @param value The value to back the element ID
	 * @param compare The comparator to compare element IDs
	 * @return An ElementId backed by the value and compared by the comparator
	 */
	static <T> ElementId of(T value, Comparator<? super T> compare) {
		return new ComparatorElementId(value, compare);
	}

	class ComparableElementId<T extends Comparable<T>> implements ElementId {
		private final T theValue;

		public ComparableElementId(T value) {
			theValue = value;
		}

		public T getValue() {
			return theValue;
		}

		@Override
		public int compareTo(ElementId o) {
			return theValue.compareTo(((ComparableElementId<T>) o).theValue);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theValue);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ComparableElementId && Objects.equals(theValue, ((ComparableElementId<T>) obj).theValue);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	class ComparatorElementId<T> implements ElementId {
		private final T theValue;
		private final Comparator<? super T> theCompare;

		public ComparatorElementId(T value, Comparator<? super T> compare) {
			theValue = value;
			theCompare = compare;
		}

		@Override
		public int compareTo(ElementId o) {
			return theCompare.compare(theValue, ((ComparatorElementId<T>) o).theValue);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theValue);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ComparatorElementId && Objects.equals(theValue, ((ComparatorElementId<T>) obj).theValue);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}
}

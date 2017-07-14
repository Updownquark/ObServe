package org.observe.collect;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

	static Supplier<ElementId> createSimpleIdGenerator() {
		return new SimpleIdGenerator();
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

	class SimpleIdGenerator implements Supplier<ElementId> {
		private final AtomicReference<int[]> theNextId;

		public SimpleIdGenerator() {
			theNextId = new AtomicReference<>(new int[1]);
		}

		@Override
		public ElementId get() {
			int[] value = theNextId.getAndUpdate(id -> increment(id));
			return new SimpleGeneratedId(value);
		}

		private int[] increment(int[] id) {
			int last = id[id.length - 1];
			last++;
			int[] nextId;
			if (last == 0)
				nextId = new int[id.length + 1];
			else {
				nextId = Arrays.copyOf(id, id.length);
				nextId[id.length - 1] = last;
			}
			return nextId;
		}

		private static class SimpleGeneratedId implements ElementId {
			private final int[] theValue;

			SimpleGeneratedId(int[] value) {
				theValue = value;
			}

			@Override
			public int compareTo(ElementId o) {
				if (this == o)
					return 0;
				int[] value = theValue;
				int[] otherValue = ((SimpleGeneratedId) o).theValue;
				int comp = value.length - otherValue.length;
				for (int i = 0; comp == 0 && i < value.length; i++)
					comp = value[i] - theValue[i];
				return comp;
			}

			@Override
			public int hashCode() {
				return Arrays.hashCode(theValue);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SimpleGeneratedId && Arrays.equals(theValue, ((SimpleGeneratedId) obj).theValue);
			}

			@Override
			public String toString() {
				return Arrays.toString(theValue);
			}
		}
	}
}

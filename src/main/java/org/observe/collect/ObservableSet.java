package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

import org.observe.Equivalence;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableSetImpl.ConstantObservableSet;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterSet;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed.
 *
 * See <a href="https://github.com/Updownquark/ObServe/wiki/ObservableCollection-API#observableset">the wiki</a> for more detail.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, BetterSet<E> {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableSet<?>> TYPE = TypeTokens.get().keyFor(ObservableSet.class).wildCard();

	@Override
	ObservableSet<E> alias(String alias);

	@Override
	boolean isEmpty();

	@Override
	default Iterator<E> iterator() {
		return ObservableCollection.super.iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		return BetterSet.super.spliterator();
	}

	@Override
	default ObservableSet<E> reverse() {
		return new ObservableSetImpl.ReversedSet<>(this);
	}

	@Override
	default Object[] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return ObservableCollection.super.toArray(a);
	}

	@Override
	default boolean contains(Object o) {
		return ObservableCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ObservableCollection.super.containsAll(c);
	}

	@Override
	default boolean remove(Object o) {
		return ObservableCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ObservableCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ObservableCollection.super.retainAll(c);
	}

	@Override
	default ObservableSet<E> with(E... values) {
		ObservableCollection.super.with(values);
		return this;
	}

	@Override
	default ObservableSet<E> withAll(Collection<? extends E> values) {
		ObservableCollection.super.withAll(values);
		return this;
	}

	@Override
	default <T> DistinctDataFlow<E, E, E> flow() {
		return new ObservableSetImpl.DistinctBaseFlow<>(this);
	}

	/**
	 * @param value The value to test
	 * @return A settable observable boolean whose value is whether this set contains the given value, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}. The
	 *         {@link SettableValue#set(Object, Object)}, when called with the opposite value of the result, will remove the element if it
	 *         is present in the set or add it if it is missing.
	 */
	default SettableValue<Boolean> observeContainsElement(ObservableValue<? extends E> value) {
		ObservableValue<ObservableElement<E>> element = value.map(v -> observeElement(v, true));
		ObservableValue<Boolean> found = ObservableValue.flatten(element.map(el -> el.map(__ -> el.getElementId() != null)));
		ObservableValue<String> enabled = ObservableValue.flatten(element.map(el -> el.map(__ -> {
			if (el.getElementId() != null) {
				MutableCollectionElement<E> mutableEl = mutableElement(el.getElementId());
				String msg = mutableEl.canRemove();
				if (msg == null)
					msg = mutableEl.isAcceptable(mutableEl.get());
				return msg;
			} else
				return canAdd(value.get());
		})));
		return SettableValue.settable(found, this, LambdaUtils.printableConsumer(v -> {
			if (v) {
				if (found.get()) {
					MutableCollectionElement<E> el = mutableElement(element.get().getElementId());
					el.set(el.get()); // Update
				} else
					add(value.get());
			} else if (found.get())
				mutableElement(element.get().getElementId()).remove();
			else { // No way to generate an update in this case because there's no element to update
			}
		}, "modifyContainment", null))//
			.filterAccept(v -> {
				if (v) {
					if (found.get()) {
						MutableCollectionElement<E> el = mutableElement(element.get().getElementId());
						return el.isAcceptable(el.get()); // Update
					} else
						return canAdd(value.get());
				} else if (found.get())
					return mutableElement(element.get().getElementId()).canRemove();
				else {
					return StdMsg.UNSUPPORTED_OPERATION; // No way to generate an update in this case because there's no element to update
				}
			})//
			.disableWith(enabled);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(E... values) {
		return of(Arrays.asList(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(Collection<? extends E> values) {
		return of(Equivalence.DEFAULT, values);
	}

	/**
	 * @param <E> The type for the set
	 * @param equivalence The equivalence set to distinguish the values
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(Equivalence<? super E> equivalence, Collection<? extends E> values) {
		return new ConstantObservableSet<>(equivalence, ObservableCollection.<E> createDefaultBacking().withAll(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @return A new observable set with the given type
	 */
	static <E> ObservableSet<E> create() {
		return create(Equivalence.DEFAULT);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param equivalence The equivalence set to distinguish the set's values
	 * @return A new observable set with the given type and equivalence
	 */
	static <E> ObservableSet<E> create(Equivalence<? super E> equivalence) {
		return ObservableCollection.<E> create().flow().withEquivalence(equivalence).distinct().collect();
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @return The builder for the set
	 */
	static <E> ObservableCollectionBuilder.DistinctBuilder<E, ?> build() {
		return ObservableCollection.<E> build().distinct();
	}

	/**
	 * Turns an observable value containing an observable set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A set representing the contents of the value, or a zero-length set when null
	 */
	public static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
		return flattenValue(collectionObservable, Equivalence.DEFAULT);
	}

	/**
	 * Turns an observable value containing an observable set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @param equivalence The equivalence for the set
	 * @return A set representing the contents of the value, or a zero-length set when null
	 */
	static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable,
		Equivalence<Object> equivalence) {
		return new ObservableSetImpl.FlattenedValueSet<>(collectionObservable, equivalence);
	}

	/**
	 * Creates a singleton set from a settable value. The set will always have a single element. If the value is null, the element's value
	 * be null, but the set will not be empty. Add and remove operations are disabled. The element can be set through the collection.
	 *
	 * @param <E> The type of the value and of the set
	 * @param value The value to represent as a set
	 * @return The singleton set
	 */
	static <E> ObservableSet<E> singleton(SettableValue<E> value) {
		return new SingletonObservableSet<>(value);
	}
}

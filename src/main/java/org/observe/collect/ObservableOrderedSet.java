package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.qommons.Equalizer;
import org.qommons.Equalizer.EqualizerNode;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeToken;

/**
 * An observable set whose elements are ordered
 *
 * @param <E> The type of elements in the set
 */
public interface ObservableOrderedSet<E> extends ObservableSet<E>, ObservableOrderedCollection<E> {
	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableOrderedSet<E> filter(Predicate<? super E> filter) {
		return (ObservableOrderedSet<E>) ObservableOrderedCollection.super.filter(filter);
	}

	@Override
	default ObservableOrderedSet<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		if (staticFilter)
			return filterStatic(filter);
		else
			return filterDynamic(filter);
	}

	@Override
	default ObservableOrderedSet<E> filterDynamic(Predicate<? super E> filter) {
		return d().debug(new DynamicFilteredOrderedSet<>(this, getType(), filter)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default ObservableOrderedSet<E> filterStatic(Predicate<? super E> filter) {
		return d().debug(new StaticFilteredOrderedSet<>(this, getType(), filter)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default <T> ObservableOrderedSet<T> filter(Class<T> type) {
		Predicate<E> filter = value -> type.isInstance(value);
		return d().debug(new StaticFilteredOrderedSet<>(this, TypeToken.of(type), filter)).from("filter", this).using("filter", filter)
				.tag("filterType", type).get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedSet<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingOrderedSet<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableOrderedSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return d().debug(new ElementRefreshingOrderedSet<>(this, refresh)).from("refreshEach", this).using("on", refresh).get();
	}

	@Override
	default ObservableOrderedSet<E> immutable() {
		return d().debug(new ImmutableOrderedSet<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableOrderedSet<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableOrderedSet<E>) ObservableOrderedCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableOrderedSet<E> noRemove() {
		return (ObservableOrderedSet<E>) ObservableOrderedCollection.super.noRemove();
	}

	@Override
	default ObservableOrderedSet<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableOrderedSet<E>) ObservableOrderedCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableOrderedSet<E> noAdd() {
		return (ObservableOrderedSet<E>) ObservableOrderedCollection.super.noAdd();
	}

	@Override
	default ObservableOrderedSet<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredOrderedSet<>(this, removeFilter, addFilter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	@Override
	default ObservableOrderedSet<E> cached() {
		return d().debug(new SafeCachedOrderedSet<>(this)).from("cached", this).get();
	}

	/**
	 * @param until The observable to end the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's elements will be
	 *         removed and set subscriptions unsubscribed
	 */
	@Override
	default ObservableOrderedSet<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilOrderedSet<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * @param until The observable to unsubscribe the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's subscriptions
	 *         will be removed. Unlike {@link #takeUntil(Observable)} however, the returned set's elements will not be removed when the
	 *         observable fires.
	 */
	@Override
	default ObservableOrderedSet<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilOrderedSet<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableOrderedSet<E> flattenValue(ObservableValue<? extends ObservableOrderedSet<E>> collectionObservable) {
		return d().debug(new FlattenedValueOrderedSet<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @param equalizer The equalizer to govern uniqueness of the set
	 * @param alwaysUseFirst Whether to always expose the first equivalent element to the set. True makes the collection more predictable
	 *        and reconciles differences between elements received by subscription and those retrieved on-demand. False may be more
	 *        performant.
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> ObservableOrderedSet<T> unique(ObservableOrderedCollection<T> coll, Equalizer equalizer, boolean alwaysUseFirst) {
		return d().debug(new CollectionWrappingOrderedSet<>(coll, equalizer, alwaysUseFirst)).from("unique", coll).get();
	}

	/**
	 * Implements {@link ObservableOrderedSet#filter(Predicate)} and {@link ObservableOrderedSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class StaticFilteredOrderedSet<E, T> extends StaticFilteredOrderedCollection<E, T> implements ObservableOrderedSet<T> {
		protected StaticFilteredOrderedSet(ObservableOrderedSet<E> wrap, TypeToken<T> type, Predicate<? super E> filter) {
			super(wrap, type, value -> {
				boolean pass = filter.test(value);
				return new FilterMapResult<>(pass ? (T) value : null, pass);
			} , value -> (E) value);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class DynamicFilteredOrderedSet<E, T> extends DynamicFilteredOrderedCollection<E, T> implements ObservableOrderedSet<T> {
		protected DynamicFilteredOrderedSet(ObservableOrderedSet<E> wrap, TypeToken<T> type, Predicate<? super E> filter) {
			super(wrap, type, value -> {
				boolean pass = filter.test(value);
				return new FilterMapResult<>(pass ? (T) value : null, pass);
			} , value -> (E) value);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	class RefreshingOrderedSet<E> extends RefreshingOrderedCollection<E> implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		protected RefreshingOrderedSet(ObservableOrderedSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	class ElementRefreshingOrderedSet<E> extends ElementRefreshingOrderedCollection<E>
	implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		protected ElementRefreshingOrderedSet(ObservableOrderedSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableOrderedSet<E> extends ImmutableOrderedCollection<E> implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		protected ImmutableOrderedSet(ObservableOrderedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableOrderedSet<E> immutable() {
			return this;
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredOrderedSet<E> extends ModFilteredOrderedCollection<E> implements ObservableOrderedSet<E> {
		public ModFilteredOrderedSet(ObservableOrderedSet<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#cached()}
	 *
	 * @param <E> The type of elements in the set
	 */
	class SafeCachedOrderedSet<E> extends SafeCachedOrderedCollection<E> implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		protected SafeCachedOrderedSet(ObservableOrderedSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public ObservableOrderedSet<E> cached() {
			return this;
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Backs {@link ObservableOrderedSet#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class TakenUntilOrderedSet<E> extends TakenUntilOrderedCollection<E> implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		public TakenUntilOrderedSet(ObservableOrderedSet<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableOrderedSet<E> getWrapped() {
			return (ObservableOrderedSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class FlattenedValueOrderedSet<E> extends FlattenedOrderedValueCollection<E> implements PartialSetImpl<E>, ObservableOrderedSet<E> {
		public FlattenedValueOrderedSet(ObservableValue<? extends ObservableOrderedSet<? extends E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableOrderedSet<? extends E>> getWrapped() {
			return (ObservableValue<? extends ObservableOrderedSet<? extends E>>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			ObservableOrderedSet<? extends E> set = getWrapped().get();
			return set == null ? Objects::equals : set.getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableOrderedSet#unique(ObservableOrderedCollection, Equalizer, boolean)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingOrderedSet<E> extends CollectionWrappingSet<E> implements ObservableOrderedSet<E> {
		private final boolean isAlwaysUsingFirst;

		public CollectionWrappingOrderedSet(ObservableOrderedCollection<E> collection, Equalizer equalizer, boolean alwaysUseFirst) {
			super(collection, equalizer);
			isAlwaysUsingFirst = alwaysUseFirst;
		}

		@Override
		protected ObservableOrderedCollection<E> getWrapped() {
			return (ObservableOrderedCollection<E>) super.getWrapped();
		}

		protected class UniqueOrderedElementTracking extends UniqueElementTracking {
			DefaultTreeSet<UniqueOrderedElement<E>> orderedElements = new DefaultTreeSet<>(
					(el1, el2) -> el1.getInternalIndex() - el2.getInternalIndex());
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<E>> onElement) {
			return onElement(element -> onElement.accept((ObservableOrderedElement<E>) element));
		}


		@Override
		protected UniqueElement<E> addUniqueElement(UniqueElementTracking tracking, EqualizerNode<E> node) {
			UniqueOrderedElement<E> unique = new UniqueOrderedElement<>(getType(), isAlwaysUsingFirst,
					((UniqueOrderedElementTracking) tracking).orderedElements);
			tracking.elements.put(node, unique);
			return unique;
		}
	}

	/**
	 * Implements elements for {@link ObservableOrderedSet#unique(ObservableOrderedCollection, Equalizer, boolean)}
	 *
	 * @param <E> The type of value in the element
	 */
	class UniqueOrderedElement<E> extends UniqueElement<E> implements ObservableOrderedElement<E> {
		private final DefaultTreeSet<UniqueOrderedElement<E>> orderedElements;
		private DefaultNode<UniqueOrderedElement<E>> node;

		public UniqueOrderedElement(TypeToken<E> type, boolean alwaysUseFirst, DefaultTreeSet<UniqueOrderedElement<E>> orderedEls) {
			super(type, alwaysUseFirst);
			orderedElements = orderedEls;
		}

		@Override
		public int getIndex() {
			return node.getIndex();
		}

		int getInternalIndex() {
			return ((ObservableOrderedElement<E>) getCurrentElement()).getIndex();
		}

		@Override
		protected Collection<ObservableElement<E>> createElements() {
			return new TreeSet<>(
					(el1, el2) -> ((ObservableOrderedElement<?>) el1).getIndex() - ((ObservableOrderedElement<?>) el2).getIndex());
		}

		@Override
		protected void setCurrentElement(ObservableElement<E> element, Object cause) {
			if (node != null)
				node.delete();
			super.setCurrentElement(element, cause);
			node = orderedElements.addGetNode(this);
		}
	}
}

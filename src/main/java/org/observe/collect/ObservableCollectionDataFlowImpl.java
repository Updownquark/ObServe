package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.XformOptions;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableMultiMapImpl;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.FlowOptions.MapOptions;
import org.observe.collect.FlowOptions.SimpleUniqueOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.ModFilterBuilder;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableCollectionImpl.ActiveDerivedCollection;
import org.observe.collect.ObservableCollectionImpl.PassiveDerivedCollection;
import org.observe.util.WeakListening;
import org.qommons.BiTuple;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Contains implementations of {@link CollectionDataFlow} and its dependencies */
public class ObservableCollectionDataFlowImpl {
	private ObservableCollectionDataFlowImpl() {}

	/**
	 * Used in mapping/filtering collection data
	 *
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	public static class FilterMapResult<E, T> {
		public E source;
		public T result;
		private boolean isError;
		private String rejectReason;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}

		public boolean isAccepted() {
			return rejectReason == null;
		}

		public boolean isError() {
			return isError;
		}

		public String getRejectReason() {
			return rejectReason;
		}

		public void clearRejection() {
			rejectReason = null;
			isError = false;
		}

		public <X extends Throwable> String throwIfError(Function<String, X> type) throws X {
			if (rejectReason == null)
				return null;
			if (isError)
				throw type.apply(rejectReason);
			return rejectReason;
		}

		public FilterMapResult<E, T> reject(String reason, boolean error) {
			if (error && reason == null)
				throw new IllegalArgumentException("Need a reason for the error");
			result = null;
			rejectReason = reason;
			isError = error;
			return this;
		}

		public FilterMapResult<E, T> maybeReject(String reason, boolean error) {
			if (reason != null)
				reject(reason, error);
			return this;
		}

		public <X> FilterMapResult<X, T> map(Function<? super E, ? extends X> map) {
			FilterMapResult<X, T> mapped = (FilterMapResult<X, T>) this;
			mapped.source = map.apply(source);
			return mapped;
		}
	}

	public static interface CollectionOperation<E, I, T> extends Transactable {
		TypeToken<T> getTargetType();

		Equivalence<? super T> equivalence();
	}

	public static interface PassiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		default boolean isReversed() {
			return false;
		}

		ObservableValue<? extends Function<? super E, ? extends T>> map();

		String canReverse();

		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd);

		default FilterMapResult<T, E> reverse(T dest, boolean forAdd) {
			return reverse(new FilterMapResult<>(dest), forAdd);
		}

		boolean isRemoveFiltered();

		MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map);

		BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map);

		boolean isManyToOne();

		void setValue(Collection<MutableCollectionElement<T>> elements, T value);
	}

	public static <E, T> TypeToken<Function<? super E, T>> functionType(TypeToken<E> srcType, TypeToken<T> destType) {
		return new TypeToken<Function<? super E, T>>() {}.where(new TypeParameter<E>() {}, srcType.wrap()).where(new TypeParameter<T>() {},
			destType.wrap());
	}

	static class MapWithParent<E, I, T> implements Function<E, T> {
		private final Function<? super E, ? extends I> theParentMap;
		private final Function<? super I, ? extends T> theMap;

		MapWithParent(Function<? super E, ? extends I> parentMap, Function<? super I, ? extends T> map) {
			theParentMap = parentMap;
			theMap = map;
		}

		@Override
		public T apply(E source) {
			I intermediate = theParentMap.apply(source);
			T dest = theMap.apply(intermediate);
			return dest;
		}

		Function<? super E, ? extends I> getParentMap() {
			return theParentMap;
		}

		public Function<? super I, ? extends T> getChildMap() {
			return theMap;
		}
	}

	public static interface ActiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		Transaction lock(boolean write, boolean structural, Object cause);

		@Override
		default Transaction lock(boolean write, Object cause) {
			return lock(write, write, cause);
		}

		boolean isContentControlled();

		Comparable<DerivedCollectionElement<T>> getElementFinder(T value);

		String canAdd(T toAdd);

		DerivedCollectionElement<T> addElement(T value, boolean first);

		/**
		 * Removes all elements in this manager, if possible
		 *
		 * @return Whether this method removed all elements. If false, the derived collection may need to remove elements itself.
		 */
		boolean clear();

		void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException;

		void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening);
	}

	public static interface ElementAccepter<E> {
		void accept(DerivedCollectionElement<E> element, Object cause);
	}

	public static interface DerivedCollectionElement<E> extends Comparable<DerivedCollectionElement<E>> {
		void setListener(CollectionElementListener<E> listener);

		E get();

		String isEnabled();

		String isAcceptable(E value);

		void set(E value) throws UnsupportedOperationException, IllegalArgumentException;

		String canRemove();

		void remove() throws UnsupportedOperationException;

		String canAdd(E value, boolean before);

		DerivedCollectionElement<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException;

		default DerivedCollectionElement<E> reverse() {
			DerivedCollectionElement<E> outer = this;
			return new DerivedCollectionElement<E>() {
				@Override
				public int compareTo(DerivedCollectionElement<E> o) {
					return -outer.compareTo(o.reverse());
				}

				@Override
				public void setListener(CollectionElementListener<E> listener) {
					outer.setListener(listener);
				}

				@Override
				public E get() {
					return outer.get();
				}

				@Override
				public String isEnabled() {
					return outer.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					return outer.isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					outer.set(value);
				}

				@Override
				public String canRemove() {
					return outer.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					outer.remove();
				}

				@Override
				public String canAdd(E value, boolean before) {
					return outer.canAdd(value, !before);
				}

				@Override
				public DerivedCollectionElement<E> add(E value, boolean before)
					throws UnsupportedOperationException, IllegalArgumentException {
					DerivedCollectionElement<E> parentEl = outer.add(value, !before);
					return parentEl == null ? null : parentEl.reverse();
				}

				@Override
				public DerivedCollectionElement<E> reverse() {
					return outer;
				}
			};
		}
	}

	public static interface CollectionElementListener<E> {
		void update(E oldValue, E newValue, Object cause);

		void removed(E value, Object cause);
	}

	static <T> void update(CollectionElementListener<T> listener, T oldValue, T newValue, Object cause) {
		if (listener != null)
			listener.update(oldValue, newValue, cause);
	}

	static <T> void removed(CollectionElementListener<T> listener, T value, Object cause) {
		if (listener != null)
			listener.removed(value, cause);
	}

	public static class ModFilterer<T> {
		private final String theImmutableMessage;
		private final boolean areUpdatesAllowed;
		private final String theAddMessage;
		private final String theRemoveMessage;
		private final Function<? super T, String> theAddFilter;
		private final Function<? super T, String> theRemoveFilter;

		public ModFilterer(ModFilterBuilder<T> options) {
			theImmutableMessage = options.getImmutableMsg();
			this.areUpdatesAllowed = options.areUpdatesAllowed();
			theAddMessage = options.getAddMsg();
			theRemoveMessage = options.getRemoveMsg();
			theAddFilter = options.getAddMsgFn();
			theRemoveFilter = options.getRemoveMsgFn();
		}

		public String getImmutableMessage() {
			return theImmutableMessage;
		}

		public boolean areUpdatesAllowed() {
			return areUpdatesAllowed;
		}

		public String getAddMessage() {
			return theAddMessage;
		}

		public String getRemoveMessage() {
			return theRemoveMessage;
		}

		public Function<? super T, String> getAddFilter() {
			return theAddFilter;
		}

		public Function<? super T, String> getRemoveFilter() {
			return theRemoveFilter;
		}

		public String isEnabled() {
			if (areUpdatesAllowed)
				return null;
			return theImmutableMessage;
		}

		public String isAcceptable(T value, Supplier<T> oldValue) {
			String msg = null;
			if (isAddFiltered() || isRemoveFiltered() || (theImmutableMessage != null && areUpdatesAllowed)) {
				T old = oldValue.get();
				if (old == value) {
					// An update. These are treated differently. These can only be prevented explicitly.
				} else {
					// Non-updates are treated
					if (theRemoveFilter != null)
						msg = theRemoveFilter.apply(old);
					if (msg == null)
						msg = theRemoveMessage;
					if (msg == null && theAddFilter != null)
						msg = theAddFilter.apply(value);
					if (msg == null)
						msg = theAddMessage;
					if (msg == null)
						msg = theImmutableMessage;
				}
			} else {
				// Not add- or remove-filtered, and don't care about updates, so no need to get the old value. Possibly immutable.
				msg = theImmutableMessage;
			}
			return msg;
		}

		public void assertSet(T value, Supplier<T> oldValue) {
			String msg = null;
			if (isAddFiltered() || isRemoveFiltered() || (theImmutableMessage != null && areUpdatesAllowed)) {
				T old = oldValue.get();
				if (old == value) {
					// An update. These are treated differently. These can only be prevented explicitly.
				} else {
					// Non-updates are treated
					if (theRemoveFilter != null)
						msg = theRemoveFilter.apply(old);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					msg = theRemoveMessage;
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					if (msg == null && theAddFilter != null)
						msg = theAddFilter.apply(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					msg = theAddMessage;
					if (msg == null)
						msg = theImmutableMessage;
					if (msg != null)
						throw new UnsupportedOperationException(msg);
				}
			} else {
				// Not add- or remove-filtered, and don't care about updates, so no need to get the old value. Possibly immutable.
				msg = theImmutableMessage;
				if (msg != null)
					throw new UnsupportedOperationException(msg);
			}
		}

		public boolean isAddFiltered() {
			return theAddFilter != null || theAddMessage != null || theImmutableMessage != null;
		}

		public boolean isRemoveFiltered() {
			return theRemoveFilter != null || theRemoveMessage != null || theImmutableMessage != null;
		}

		public String canRemove(Supplier<T> oldValue) {
			String msg = null;
			if (theRemoveFilter != null)
				msg = theRemoveFilter.apply(oldValue.get());
			if (msg == null)
				msg = theRemoveMessage;
			if (msg == null)
				msg = theImmutableMessage;
			return msg;
		}

		public void assertRemove(Supplier<T> oldValue) {
			String msg = null;
			if (theRemoveFilter != null)
				msg = theRemoveFilter.apply(oldValue.get());
			if (msg == null)
				msg = theRemoveMessage;
			if (msg == null)
				msg = theImmutableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
		}

		public String canAdd() {
			if (theAddMessage != null)
				return theAddMessage;
			if (theImmutableMessage != null)
				return theImmutableMessage;
			return null;
		}

		public String canAdd(T value) {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			return msg;
		}

		public void assertAdd(T value) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = null;
			if (theAddFilter != null)
				msg = theAddFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			if (msg == null && theAddMessage != null)
				msg = theAddMessage;
			if (msg == null && theImmutableMessage != null)
				msg = theImmutableMessage;
			if (msg != null)
				throw new UnsupportedOperationException(msg);
		}

		public boolean isEmpty() {
			return theImmutableMessage == null && theAddMessage == null && theRemoveMessage == null && theAddFilter == null
				&& theRemoveFilter == null;
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			if (theAddFilter != null)
				s.append("addFilter:").append(theAddFilter).append(' ');
			if (theAddMessage != null)
				s.append("noAdd:").append(theAddMessage).append(' ');
			if (theRemoveFilter != null)
				s.append("removeFilter:").append(theRemoveFilter).append(' ');
			if (theRemoveMessage != null)
				s.append("noRemove:").append(theRemoveMessage).append(' ');
			if (theImmutableMessage != null)
				s.append("immutable:").append(theImmutableMessage).append('(').append(areUpdatesAllowed ? "" : "not ").append("updatable)")
				.append(' ');
			if (s.length() > 0)
				s.deleteCharAt(s.length() - 1);
			else
				s.append("not filtered");
			return s.toString();
		}
	}

	private static class GroupedEntry<K, T> implements Map.Entry<K, T> {
		private final T theValue;
		private final Function<? super T, ? extends K> theKeyMap;

		GroupedEntry(T value, Function<? super T, ? extends K> keyMap) {
			theValue = value;
			theKeyMap = keyMap;
		}

		@Override
		public K getKey() {
			return theKeyMap.apply(theValue);
		}

		@Override
		public T getValue() {
			return theValue;
		}

		@Override
		public T setValue(T value) {
			throw new UnsupportedOperationException();
		}
	}

	public static abstract class AbstractDataFlow<E, I, T> implements CollectionDataFlow<E, I, T> {
		private final ObservableCollection<E> theSource;
		private final CollectionDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Equivalence<? super T> theEquivalence;

		protected AbstractDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Equivalence<? super T> equivalence) {
			theSource = source;
			theParent = parent;
			theTargetType = targetType;
			theEquivalence = equivalence;
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public CollectionDataFlow<E, T, T> reverse() {
			return new ReverseOp<>(theSource, this);
		}

		@Override
		public CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter) {
			return new FilterOp<>(theSource, this, filter);
		}

		@Override
		public <X> CollectionDataFlow<E, T, T> whereContained(CollectionDataFlow<?, ?, X> other, boolean include) {
			return new IntersectionFlow<>(theSource, this, other, !include);
		}

		@Override
		public CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence) {
			return new EquivalenceSwitchOp<>(theSource, this, equivalence);
		}

		@Override
		public CollectionDataFlow<E, T, T> refresh(Observable<?> refresh) {
			return new RefreshOp<>(theSource, this, refresh);
		}

		@Override
		public CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh) {
			return new ElementRefreshOp<>(theSource, this, refresh);
		}

		@Override
		public <X> CollectionDataFlow<E, T, X> map(TypeToken<X> target, Function<? super T, ? extends X> map,
			Consumer<MapOptions<T, X>> options) {
			MapOptions<T, X> mapOptions = new MapOptions<>();
			options.accept(mapOptions);
			return new MapOp<>(theSource, this, target, map, new MapDef<>(mapOptions));
		}

		@Override
		public <X> CollectionDataFlow<E, T, X> combine(TypeToken<X> targetType,
			Function<CombinationPrecursor<T, X>, CombinedFlowDef<T, X>> combination) {
			CombinedFlowDef<T, X> def = combination.apply(new CombinationPrecursor<>());
			return new CombinedCollectionOp<>(theSource, this, targetType, def);
		}

		@Override
		public <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends CollectionDataFlow<?, ?, ? extends X>> map) {
			return new FlattenedOp<>(theSource, this, target, map);
		}

		@Override
		public CollectionDataFlow<E, T, T> filterMod(Consumer<ModFilterBuilder<T>> options) {
			ModFilterBuilder<T> filter = new ModFilterBuilder<>();
			options.accept(filter);
			return new ModFilteredOp<>(theSource, this, new ModFilterer<>(filter));
		}

		@Override
		public CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare) {
			return new SortedDataFlow<>(theSource, this, compare);
		}

		@Override
		public UniqueDataFlow<E, T, T> distinct(Consumer<UniqueOptions> options) {
			SimpleUniqueOptions uo = new SimpleUniqueOptions(equivalence() instanceof Equivalence.ComparatorEquivalence);
			options.accept(uo);
			return new ObservableSetImpl.UniqueOp<>(theSource, this, uo.isUseFirst(), uo.isPreservingSourceOrder());
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> distinctSorted(Comparator<? super T> compare, boolean alwaysUseFirst) {
			return new ObservableSortedSetImpl.UniqueSortedOp<>(theSource, this, compare, alwaysUseFirst);
		}

		@Override
		public <K> MultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			Equivalence<? super K> keyEquivalence) {
			TypeToken<Map.Entry<K, T>> entryType = ObservableMap.buildEntryType(keyType, getTargetType());
			return new ObservableMultiMapImpl.DefaultMultiMapFlow<>(map(entryType, v -> new GroupedEntry<>(v, keyMap)), keyEquivalence,
				equivalence(), this instanceof UniqueDataFlow);
		}

		@Override
		public <K> SortedMultiMapFlow<K, T> groupBy(TypeToken<K> keyType, Function<? super T, ? extends K> keyMap,
			Comparator<? super K> keyCompare) {
			TypeToken<Map.Entry<K, T>> entryType = ObservableMap.buildEntryType(keyType, getTargetType());
			return new ObservableMultiMapImpl.DefaultSortedMultiMapFlow<>(map(entryType, v -> new GroupedEntry<>(v, keyMap)),
				Equivalence.of((Class<K>) keyType.getRawType(), keyCompare, true), equivalence(), this instanceof UniqueDataFlow);
		}

		@Override
		public ObservableCollection<T> collectPassive() {
			if (!supportsPassive())
				throw new UnsupportedOperationException("This flow does not support passive collection");
			return new PassiveDerivedCollection<>(getSource(), managePassive());
		}

		@Override
		public ObservableCollection<T> collectActive(Observable<?> until) {
			return new ActiveDerivedCollection<>(manageActive(), until);
		}
	}

	private static class SortedDataFlow<E, T> extends AbstractDataFlow<E, T, T> {
		private final Comparator<? super T> theCompare;

		SortedDataFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Comparator<? super T> compare) {
			super(source, parent, parent.getTargetType(), Equivalence.of((Class<T>) parent.getTargetType().getRawType(), compare, true));
			theCompare = compare;
		}

		protected Comparator<? super T> getCompare() {
			return theCompare;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new SortedManager<>(getParent().manageActive(), theCompare);
		}
	}

	/**
	 * Implements {@link CollectionDataFlow}
	 *
	 * @param <E> The type of the source collection
	 */
	public static class BaseCollectionDataFlow<E> extends AbstractDataFlow<E, E, E> {
		protected BaseCollectionDataFlow(ObservableCollection<E> source) {
			super(source, null, source.getType(), source.equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, E> managePassive() {
			return new BaseCollectionPassThrough<>(getSource());
		}

		@Override
		public ActiveCollectionManager<E, ?, E> manageActive() {
			return new BaseCollectionManager<>(getSource());
		}

		@Override
		public ObservableCollection<E> collectPassive() {
			return getSource();
		}
	}

	private static class ReverseOp<E, T> extends AbstractDataFlow<E, T, T> {
		ReverseOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveReversedManager<E, T>(getParent().managePassive());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveReversedManager<>(getParent().manageActive());
		}
	}

	private static class FilterOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, String> theFilter;

		FilterOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Function<? super T, String> filter) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theFilter = filter;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FilteredCollectionManager<>(getParent().manageActive(), theFilter);
		}
	}

	private static class IntersectionFlow<E, T, X> extends AbstractDataFlow<E, T, T> {
		private final CollectionDataFlow<?, ?, X> theFilter;
		private final boolean isExclude;

		IntersectionFlow(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter,
			boolean exclude) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theFilter = filter;
			isExclude = exclude;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new IntersectionManager<>(getParent().manageActive(), theFilter, isExclude);
		}
	}

	private static class EquivalenceSwitchOp<E, T> extends AbstractDataFlow<E, T, T> {
		EquivalenceSwitchOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Equivalence<? super T> equivalence) {
			super(source, parent, parent.getTargetType(), equivalence);
		}

		@Override
		public boolean supportsPassive() {
			return true;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveEquivalenceSwitchedManager<>(getParent().managePassive(), equivalence());
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveEquivalenceSwitchedManager<>(getParent().manageActive(), equivalence());
		}
	}

	/**
	 * Implements {@link CollectionDataFlow#map(TypeToken, Function, Consumer)}
	 *
	 * @param <E> The type of the source collection
	 * @param <I> The target type of this flow's parent flow
	 * @param <T> The type of values produced by this flow
	 */
	protected static class MapOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final MapDef<I, T> theOptions;

		protected MapOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> target,
			Function<? super I, ? extends T> map, MapDef<I, T> options) {
			super(source, parent, target, mapEquivalence(parent.getTargetType(), parent.equivalence(), target, map, options));
			theMap = map;
			theOptions = options;
		}

		@Override
		public boolean supportsPassive() {
			if (theOptions.isCached())
				return false;
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveMappedCollectionManager<>(getParent().managePassive(), getTargetType(), theMap, equivalence(), theOptions);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveMappedCollectionManager<>(getParent().manageActive(), getTargetType(), theMap, equivalence(), theOptions);
		}
	}

	private static <I, T> Equivalence<? super T> mapEquivalence(TypeToken<I> srcType, Equivalence<? super I> equivalence,
		TypeToken<T> targetType, Function<? super I, ? extends T> map, MapDef<I, T> options) {
		if (options.getEquivalence() != null)
			return options.getEquivalence();
		else if (srcType.equals(targetType))
			return (Equivalence<? super T>) equivalence;
		else
			return Equivalence.DEFAULT;
	}

	/**
	 * Defines a combination of a single source collection with one or more observable values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of elements in the resulting collection
	 */
	public static class CombinedCollectionOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final CombinedFlowDef<I, T> theDef;

		protected CombinedCollectionOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> target,
			CombinedFlowDef<I, T> def) {
			super(source, parent, target,
				parent.getTargetType().equals(target) ? (Equivalence<? super T>) parent.equivalence() : Equivalence.DEFAULT);
			theDef = def;
		}

		@Override
		public boolean supportsPassive() {
			if (theDef.isCached())
				return false;
			return getParent().supportsPassive();
		}

		@Override
		public boolean prefersPassive() {
			return false; // I think it's better just to do active here
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveCombinedCollectionManager<>(getParent().managePassive(), getTargetType(), theDef);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveCombinedCollectionManager<>(getParent().manageActive(), getTargetType(), theDef);
		}
	}

	private static class RefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Observable<?> theRefresh;

		RefreshOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, Observable<?> refresh) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theRefresh = refresh;
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveRefreshingCollectionManager<>(getParent().managePassive(), theRefresh);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveRefreshingCollectionManager<>(getParent().manageActive(), theRefresh);
		}
	}

	private static class ElementRefreshOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final Function<? super T, ? extends Observable<?>> theElementRefresh;

		ElementRefreshOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent,
			Function<? super T, ? extends Observable<?>> elementRefresh) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theElementRefresh = elementRefresh;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ElementRefreshingCollectionManager<>(getParent().manageActive(), theElementRefresh);
		}
	}

	protected static class ModFilteredOp<E, T> extends AbstractDataFlow<E, T, T> {
		private final ModFilterer<T> theOptions;

		protected ModFilteredOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent, ModFilterer<T> options) {
			super(source, parent, parent.getTargetType(), parent.equivalence());
			theOptions = options;
		}

		@Override
		public boolean supportsPassive() {
			return getParent().supportsPassive();
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return new PassiveModFilteredManager<>(getParent().managePassive(), theOptions);
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new ActiveModFilteredManager<>(getParent().manageActive(), theOptions);
		}
	}

	private static class FlattenedOp<E, I, T> extends AbstractDataFlow<E, I, T> {
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		FlattenedOp(ObservableCollection<E> source, CollectionDataFlow<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			super(source, parent, targetType, Equivalence.DEFAULT);
			theMap = map;
		}

		@Override
		public boolean supportsPassive() {
			return false;
		}

		@Override
		public PassiveCollectionManager<E, ?, T> managePassive() {
			return null;
		}

		@Override
		public ActiveCollectionManager<E, ?, T> manageActive() {
			return new FlattenedManager<>(getParent().manageActive(), getTargetType(), theMap);
		}
	}

	private static class BaseCollectionPassThrough<E> implements PassiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;

		BaseCollectionPassThrough(ObservableCollection<E> source) {
			theSource = source;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public TypeToken<E> getTargetType() {
			return theSource.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theSource.equivalence();
		}

		@Override
		public ObservableValue<Function<? super E, E>> map() {
			TypeToken<E> srcType = theSource.getType();
			return ObservableValue.of(functionType(srcType, srcType), v -> v);
		}

		@Override
		public String canReverse() {
			return null;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest, boolean forAdd) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public MutableCollectionElement<E> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends E> map) {
			return mapped;
		}

		@Override
		public BiTuple<E, E> map(E oldSource, E newSource, Function<? super E, ? extends E> map) {
			return new BiTuple<>(oldSource, newSource);
		}

		@Override
		public boolean isManyToOne() {
			return false;
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<E>> elements, E value) {
			theSource.setValue(//
				elements.stream().map(el -> el.getElementId()).collect(Collectors.toList()), value);
		}
	}

	private static class BaseCollectionManager<E> implements ActiveCollectionManager<E, E, E> {
		private final ObservableCollection<E> theSource;
		private final BetterTreeMap<ElementId, CollectionElementListener<E>> theElementListeners;

		BaseCollectionManager(ObservableCollection<E> source) {
			theSource = source;
			theElementListeners = new BetterTreeMap<>(false, ElementId::compareTo);
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public TypeToken<E> getTargetType() {
			return theSource.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theSource.equivalence();
		}

		@Override
		public boolean isContentControlled() {
			return theSource.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<E>> getElementFinder(E value) {
			return null;
		}

		@Override
		public boolean clear() {
			theSource.clear();
			return true;
		}

		@Override
		public String canAdd(E toAdd) {
			return theSource.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<E> addElement(E value, boolean first) {
			CollectionElement<E> srcEl = theSource.addElement(value, first);
			return srcEl == null ? null : new BaseDerivedElement(theSource.mutableElement(srcEl.getElementId()));
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<E>> elements, E newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theSource.setValue(//
				elements.stream().map(el -> ((BaseDerivedElement) el).source.getElementId()).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<E> onElement, WeakListening listening) {
			listening.withConsumer((ObservableCollectionEvent<? extends E> evt) -> {
				switch (evt.getType()) {
				case add:
					BaseDerivedElement el = new BaseDerivedElement(theSource.mutableElement(evt.getElementId()));
					onElement.accept(el, evt);
					break;
				case remove:
					CollectionElementListener<E> listener = theElementListeners.remove(evt.getElementId());
					if (listener != null)
						listener.removed(evt.getOldValue(), evt);
					break;
				case set:
					listener = theElementListeners.get(evt.getElementId());
					if (listener != null)
						listener.update(evt.getOldValue(), evt.getNewValue(), evt);
					break;
				}
			}, action -> theSource.subscribe(action, fromStart).removeAll());
		}

		class BaseDerivedElement implements DerivedCollectionElement<E> {
			private final MutableCollectionElement<E> source;

			BaseDerivedElement(MutableCollectionElement<E> src) {
				source = src;
			}

			@Override
			public int compareTo(DerivedCollectionElement<E> o) {
				return source.getElementId().compareTo(((BaseDerivedElement) o).source.getElementId());
			}

			@Override
			public void setListener(CollectionElementListener<E> listener) {
				theElementListeners.put(source.getElementId(), listener);
			}

			@Override
			public E get() {
				return source.get();
			}

			@Override
			public String isEnabled() {
				return source.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return source.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				source.set(value);
			}

			@Override
			public String canRemove() {
				return source.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				source.remove();
			}

			@Override
			public String canAdd(E value, boolean before) {
				return source.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<E> add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				ElementId newId = source.add(value, before);
				return new BaseDerivedElement(theSource.mutableElement(newId));
			}

			@Override
			public String toString() {
				return source.getElementId().toString();
			}
		}
	}

	private static class PassiveReversedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;

		PassiveReversedManager(PassiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public boolean isReversed() {
			return !theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map).reverse();
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(//
				elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}
	}

	private static class ActiveReversedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;

		ActiveReversedManager(ActiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder == null)
				return null;
			return el -> -parentFinder.compareTo(el.reverse());
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, !first);
			return parentEl == null ? null : parentEl.reverse();
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(!fromStart, (el, cause) -> onElement.accept(el.reverse(), cause), listening);
		}
	}

	protected static class SortedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Comparator<? super T> theCompare;
		// Need to keep track of the values to enforce the set-does-not-reorder policy
		private final BetterTreeList<T> theValues;

		protected SortedManager(ActiveCollectionManager<E, ?, T> parent, Comparator<? super T> compare) {
			theParent = parent;
			theCompare = compare;
			theValues = new BetterTreeList<>(false);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null; // Even if the parent could've found it, the order will be mixed up now
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new SortedElement(parentEl, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((SortedElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> onElement.accept(new SortedElement(parentEl, false), cause), listening);
		}

		private BinaryTreeNode<T> insertIntoValues(T value) {
			BinaryTreeNode<T> node = theValues.getRoot();
			if (node == null)
				return theValues.addElement(value, false);
			else {
				node = node.findClosest(n -> {
					return theCompare.compare(n.get(), value);
				}, true, false);
				return theValues.getElement(theValues.mutableNodeFor(node).add(value, theCompare.compare(node.get(), value) > 0));
			}
		}

		class SortedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private BinaryTreeNode<T> theValueNode;
			private CollectionElementListener<T> theListener;

			SortedElement(DerivedCollectionElement<T> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theValueNode = insertIntoValues(parentEl.get());
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							if (!isInCorrectOrder(newValue)) {
								// The order of this element has changed
								theValues.mutableNodeFor(theValueNode).remove();
								theValueNode = insertIntoValues(newValue);
							}
							ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
						}

						@Override
						public void removed(T value, Object cause) {
							theValues.mutableNodeFor(theValueNode).remove();
							ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
						}
					});
				}
			}

			private boolean isInCorrectOrder(T newValue) {
				BinaryTreeNode<T> left = theValueNode.getClosest(true);
				BinaryTreeNode<T> right = theValueNode.getClosest(false);
				return (left == null || theCompare.compare(left.get(), newValue) <= 0)//
					&& (right == null || theCompare.compare(newValue, right.get()) <= 0);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				int comp = theCompare.compare(get(), o.get());
				if (comp == 0)
					comp = theParentEl.compareTo(((SortedElement) o).theParentEl);
				return comp;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (theCompare.compare(theParentEl.get(), value) != 0 && !isInCorrectOrder(value))
					return StdMsg.ILLEGAL_ELEMENT;
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				// It is not allowed to change the order of an element via set
				// However, if the order has already been changed (e.g. due to changes in the value or the comparator),
				// it is permitted (and required) to use set to notify the collection of the change
				if (theCompare.compare(theParentEl.get(), value) != 0 && !isInCorrectOrder(value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				return theParentEl.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				return new SortedElement(theParentEl.add(value, before), true);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	private static class FilteredCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, String> theFilter;
		private ElementAccepter<T> theElementAccepter;

		FilteredCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, String> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			return false;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return theParent.getElementFinder(value);
		}

		@Override
		public String canAdd(T toAdd) {
			String msg = theFilter.apply(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = theFilter.apply(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			if (parentEl == null)
				return null;
			return new FilteredElement(parentEl, true, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = theFilter.apply(newValue);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theParent.setValues(elements.stream().map(el -> ((FilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theElementAccepter = onElement;
			theParent.begin(fromStart, (parentEl, cause) -> {
				String msg = theFilter.apply(parentEl.get());
				if (msg == null)
					onElement.accept(new FilteredElement(parentEl, false, true), cause);
				else
					new FilteredElement(parentEl, false, false);
			}, listening);
		}

		class FilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private final boolean isSynthetic;
			private boolean included;
			private CollectionElementListener<T> theListener;

			FilteredElement(DerivedCollectionElement<T> parentEl, boolean synthetic, boolean included) {
				theParentEl = parentEl;
				isSynthetic = synthetic;
				this.included = included;
				if (!isSynthetic) {
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							boolean oldIncluded = FilteredElement.this.included;
							boolean newIncluded = theFilter.apply(newValue) == null;
							FilteredElement.this.included = newIncluded;
							if (!oldIncluded && newIncluded) {
								theElementAccepter.accept(FilteredElement.this, cause);
							} else if (oldIncluded && !newIncluded && theListener != null) {
								theListener.removed(oldValue, cause);
								theListener = null;
							} else if (oldIncluded && newIncluded && theListener != null)
								theListener.update(oldValue, newValue, cause);
						}

						@Override
						public void removed(T value, Object cause) {
							if (FilteredElement.this.included && theListener != null) {
								theListener.removed(value, cause);
								theListener = null;
							}
						}
					});
				}
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((FilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.apply(value);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theFilter.apply(value);
				if (msg == null)
					msg = theParentEl.canAdd(value, before);
				return msg;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theFilter.apply(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new FilteredElement(parentEl, true, true);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	private static class IntersectionManager<E, T, X> implements ActiveCollectionManager<E, T, T> {
		private class IntersectionElement {
			private final T value;
			private int rightCount;
			final List<IntersectedCollectionElement> leftElements;

			IntersectionElement(T value) {
				this.value = value;
				leftElements = new ArrayList<>();
			}

			boolean isPresent() {
				return (rightCount > 0) != isExclude;
			}

			void incrementRight(Object cause) {
				rightCount++;
				if (rightCount == 1)
					presentChanged(cause);
			}

			void decrementRight(Object cause) {
				rightCount--;
				if (rightCount == 0) {
					presentChanged(cause);
					if (leftElements.isEmpty())
						theValues.remove(value);
				}
			}

			void addLeft(IntersectedCollectionElement element) {
				leftElements.add(element);
			}

			void removeLeft(IntersectedCollectionElement element) {
				leftElements.remove(element);
				if (leftElements.isEmpty() && rightCount == 0)
					theValues.remove(value);
			}

			private void presentChanged(Object cause) {
				if (isPresent()) {
					for (IntersectedCollectionElement el : leftElements)
						theAccepter.accept(el, cause);
				} else {
					for (IntersectedCollectionElement el : leftElements)
						el.fireRemove(cause);
				}
			}
		}

		class IntersectedCollectionElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private IntersectionElement intersection;
			private CollectionElementListener<T> theListener;

			IntersectedCollectionElement(DerivedCollectionElement<T> parentEl, IntersectionElement intersect, boolean synthetic) {
				theParentEl = parentEl;
				intersection = intersect;
				if (!synthetic)
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							if (theEquivalence.elementEquals(intersection.value, newValue)) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							} else {
								boolean oldPresent = intersection.isPresent();
								intersection.removeLeft(IntersectedCollectionElement.this);
								intersection = theValues.computeIfAbsent(newValue, v -> new IntersectionElement(newValue));
								intersection.addLeft(IntersectedCollectionElement.this);
								boolean newPresent = intersection.isPresent();
								if (oldPresent && newPresent)
									ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
								else if (oldPresent && !newPresent) {
									ObservableCollectionDataFlowImpl.removed(theListener, oldValue, cause);
									theListener = null;
								} else if (!oldPresent && newPresent)
									theAccepter.accept(IntersectedCollectionElement.this, cause);
								else { // Wasn't present before, still isn't present. Nothing to do.
								}
							}
						}

						@Override
						public void removed(T value, Object cause) {
							if (intersection.isPresent())
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							intersection.removeLeft(IntersectedCollectionElement.this);
							intersection = null;
						}
					});
			}

			void fireRemove(Object cause) {
				removed(theListener, theParentEl.get(), cause);
				theListener = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((IntersectedCollectionElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theParentEl.isAcceptable(value);
				if (msg != null)
					return msg;
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theParentEl.canAdd(value, before);
				if (msg != null)
					return msg;
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				IntersectionElement intersect = theValues.get(value);
				boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
				if (filterHas == isExclude)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final CollectionDataFlow<?, ?, X> theFilter;
		private final Equivalence<? super T> theEquivalence; // Make this a field since we'll need it often
		/** Whether a value's presence in the right causes the value in the left to be present (true) or absent (false) in the result */
		private final boolean isExclude;
		private Map<T, IntersectionElement> theValues;
		// The following two fields are needed because the values may mutate
		private Map<ElementId, IntersectionElement> theRightElementValues;

		private ElementAccepter<T> theAccepter;

		IntersectionManager(ActiveCollectionManager<E, ?, T> parent, CollectionDataFlow<?, ?, X> filter, boolean exclude) {
			theParent = parent;
			theFilter = filter;
			theEquivalence = parent.equivalence();
			isExclude = exclude;
			theValues = theEquivalence.createMap();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			return false;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return theParent.getElementFinder(value);
		}

		@Override
		public String canAdd(T toAdd) {
			IntersectionElement intersect = theValues.get(toAdd);
			boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
			if (filterHas == isExclude)
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			IntersectionElement intersect = theValues.get(value);
			boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new IntersectedCollectionElement(parentEl, null, true);
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			IntersectionElement intersect = theValues.get(newValue);
			boolean filterHas = intersect == null ? false : intersect.rightCount > 0;
			if (filterHas == isExclude)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValues(elements.stream().map(el -> ((IntersectedCollectionElement) el).theParentEl).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			ObservableCollection<X> collectedFilter = theFilter.collect();
			listening.withConsumer((ObservableCollectionEvent<? extends X> evt) -> {
				// We're not modifying, but we want to obtain an exclusive lock
				// to ensure that nothing above or below us is firing events at the same time.
				// TODO Maybe replace this with an independent lock on the base flow to avoid lock-writing?
				try (Transaction t = theParent.lock(true, evt)) {
					IntersectionElement element;
					switch (evt.getType()) {
					case add:
						if (!theEquivalence.isElement(evt.getNewValue()))
							return;
						element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
						element.incrementRight(evt);
						theRightElementValues.put(evt.getElementId(), element);
						break;
					case remove:
						element = theRightElementValues.remove(evt.getElementId());
						if (element == null)
							return; // Must not have belonged to the flow's equivalence
						element.decrementRight(evt);
						break;
					case set:
						element = theRightElementValues.get(evt.getElementId());
						if (element != null && theEquivalence.elementEquals(element.value, evt.getNewValue()))
							return; // No change;
						boolean newIsElement = equivalence().isElement(evt.getNewValue());
						if (element != null) {
							theRightElementValues.remove(evt.getElementId());
							element.decrementRight(evt);
						}
						if (newIsElement) {
							element = theValues.computeIfAbsent((T) evt.getNewValue(), v -> new IntersectionElement(v));
							element.incrementRight(evt);
							theRightElementValues.put(evt.getElementId(), element);
						}
						break;
					}
				}
			}, action -> collectedFilter.subscribe(action, fromStart).removeAll());
			theParent.begin(fromStart, (parentEl, cause) -> {
				IntersectionElement element = theValues.computeIfAbsent(parentEl.get(), v -> new IntersectionElement(v));
				IntersectedCollectionElement el = new IntersectedCollectionElement(parentEl, element, false);
				element.addLeft(el);
				if (element.isPresent())
					onElement.accept(el, cause);
			}, listening);
		}
	}

	private static class PassiveEquivalenceSwitchedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		PassiveEquivalenceSwitchedManager(PassiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends T> map) {
			return theParent.map(mapped, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
		}
	}

	private static class ActiveEquivalenceSwitchedManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		ActiveEquivalenceSwitchedManager(ActiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			return theParent.addElement(value, first);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements, newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, onElement, listening);
		}
	}

	private static class PassiveMappedCollectionManager<E, I, T> implements PassiveCollectionManager<E, I, T> {
		private final PassiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends T> theMap;
		private final Equivalence<? super T> theEquivalence;
		private final MapDef<I, T> theOptions;

		PassiveMappedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Equivalence<? super T> equivalence, MapDef<I, T> options) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;
			theEquivalence = equivalence;
			theOptions = options;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public ObservableValue<Function<? super E, T>> map() {
			return theParent.map().map(parentMap -> new MapWithParent<>(parentMap, theMap));
		}

		@Override
		public String canReverse() {
			if (theOptions.getReverse() == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (theOptions.getReverse() == null)
				return dest.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			T value = dest.source;
			FilterMapResult<I, E> intermediate = dest.map(theOptions.getReverse());
			if (intermediate.isAccepted() && !theEquivalence.elementEquals(theMap.apply(intermediate.source), value))
				return dest.reject(StdMsg.ILLEGAL_ELEMENT, true);
			return (FilterMapResult<T, E>) theParent.reverse(intermediate, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
			return new MappedElement(source, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			if (oldSource == newSource) {
				if (!theOptions.isFireIfUnchanged())
					return null;
				if (!theOptions.isReEvalOnUpdate()) {
					T newDest = map.apply(newSource);
					return new BiTuple<>(newDest, newDest);
				}
			}
			MapWithParent<E, I, T> mwp = (MapWithParent<E, I, T>) map;
			BiTuple<I, I> interm = theParent.map(oldSource, newSource, mwp.getParentMap());
			if (interm == null)
				return null;
			if (interm.getValue1() == interm.getValue2()) {
				if (!theOptions.isFireIfUnchanged())
					return null;
				if (!theOptions.isReEvalOnUpdate()) {
					T newDest = mwp.getChildMap().apply(interm.getValue2());
					return new BiTuple<>(newDest, newDest);
				}
			}
			return new BiTuple<>(mwp.getChildMap().apply(interm.getValue1()), mwp.getChildMap().apply(interm.getValue2()));
		}

		@Override
		public boolean isManyToOne() {
			return theOptions.isManyToOne() || theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			Collection<MutableCollectionElement<I>> remaining;
			if (theOptions.getElementReverse() != null) {
				remaining = new ArrayList<>();
				for (MutableCollectionElement<T> el : elements) {
					if (theOptions.getElementReverse().setElement(((MappedElement) el).theParentMap.get(), value, true) == null)
						remaining.add(((MappedElement) el).theParentMap);
				}
			} else
				remaining = elements.stream().map(el -> ((MappedElement) el).theParentMap).collect(Collectors.toList());
			if (remaining.isEmpty())
				return;
			if (theOptions.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			I reversed = theOptions.getReverse().apply(value);
			if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValue(remaining, reversed);
		}

		class MappedElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<I> theParentMap;

			MappedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				theParentMap = theParent.map(source, ((MapWithParent<E, I, I>) map).getParentMap());
			}

			@Override
			public ElementId getElementId() {
				return theParentMap.getElementId();
			}

			@Override
			public T get() {
				return theMap.apply(theParentMap.get());
			}

			@Override
			public String isEnabled() {
				if (theOptions.getElementReverse() != null)
					return null;
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				return theParentMap.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = null;
				if (theOptions.getElementReverse() != null) {
					msg = theOptions.getElementReverse().setElement(theParentMap.get(), value, false);
					if (msg == null)
						return null;
				}
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					return StdMsg.ILLEGAL_ELEMENT;
				String setMsg = theParentMap.isAcceptable(reversed);
				// If the element reverse is set, it should get the final word on the error message if
				if (setMsg == null || msg == null)
					return setMsg;
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (theOptions.getElementReverse() != null) {
					if (theOptions.getElementReverse().setElement(theParentMap.get(), value, true) == null)
						return;
				}
				if (theOptions.getReverse() == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theParentMap.set(reversed);
			}

			@Override
			public String canRemove() {
				return theParentMap.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentMap.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					return StdMsg.ILLEGAL_ELEMENT;
				return theParentMap.canAdd(reversed, before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (theOptions.getReverse() == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				return theParentMap.add(reversed, before);
			}
		}
	}

	public static class ActiveMappedCollectionManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends T> theMap;
		private final Equivalence<? super T> theEquivalence;
		private final MapDef<I, T> theOptions;

		public ActiveMappedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Equivalence<? super T> equivalence, MapDef<I, T> options) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;
			theEquivalence = equivalence;
			theOptions = options;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theOptions.getReverse() == null || theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			if (theOptions.getReverse() == null)
				return null;
			Comparable<DerivedCollectionElement<I>> pef = theParent.getElementFinder(theOptions.getReverse().apply(value));
			if (pef == null)
				return null;
			return el -> pef.compareTo(((MappedElement) el).theParentEl);
		}

		@Override
		public String canAdd(T toAdd) {
			if (theOptions.getReverse() == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			I reversed = theOptions.getReverse().apply(toAdd);
			if (!theEquivalence.elementEquals(theMap.apply(reversed), toAdd))
				return StdMsg.ILLEGAL_ELEMENT;
			return theParent.canAdd(reversed);
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			if (theOptions.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			I reversed = theOptions.getReverse().apply(value);
			if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			DerivedCollectionElement<I> parentEl = theParent.addElement(reversed, first);
			return parentEl == null ? null : new MappedElement(parentEl, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			Collection<DerivedCollectionElement<I>> remaining;
			if (theOptions.getElementReverse() != null) {
				remaining = new ArrayList<>();
				for (DerivedCollectionElement<T> el : elements) {
					if (theOptions.getElementReverse().setElement(((MappedElement) el).theParentEl.get(), newValue, true) == null)
						remaining.add(((MappedElement) el).theParentEl);
				}
			} else
				remaining = elements.stream().map(el -> ((MappedElement) el).theParentEl).collect(Collectors.toList());
			if (remaining.isEmpty())
				return;
			if (theOptions.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			I reversed = theOptions.getReverse().apply(newValue);
			if (!theEquivalence.elementEquals(theMap.apply(reversed), newValue))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theParent.setValues(remaining, reversed);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> {
				onElement.accept(new MappedElement(parentEl, false), cause);
			}, listening);
		}

		public class MappedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private CollectionElementListener<T> theListener;
			private T theValue;
			private final XformOptions.XformCacheHandler<I, T> theCacheHandler;

			MappedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theCacheHandler = theOptions.createCacheHandler(new XformOptions.XformCacheHandlingInterface<I, T>() {
						@Override
						public Function<? super I, ? extends T> map() {
							return theMap;
						}

						@Override
						public Transaction lock() {
							// No need to lock, as modifications only come from one source
							return Transaction.NONE;
						}

						@Override
						public T getDestCache() {
							return theValue;
						}

						@Override
						public void setDestCache(T value) {
							theValue = value;
						}
					});
					// Populate the initial values if these are needed
					if (theOptions.isCached() || !theOptions.isFireIfUnchanged()) {
						I srcVal = parentEl.get();
						theCacheHandler.initialize(srcVal);
						if (theOptions.isCached())
							theValue = theMap.apply(srcVal);
					}
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldSource, I newSource, Object cause) {
							BiTuple<T, T> values = theCacheHandler.handleChange(oldSource, newSource);
							if (values != null)
								ObservableCollectionDataFlowImpl.update(theListener, values.getValue1(), values.getValue2(), cause);
						}

						@Override
						public void removed(I value, Object cause) {
							T val = theOptions.isCached() ? theValue : theMap.apply(value);
							ObservableCollectionDataFlowImpl.removed(theListener, val, cause);
							theListener = null;
							theCacheHandler.initialize(null);
							theValue = null;
						}
					});
				} else
					theCacheHandler = null;
			}

			public DerivedCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((MappedElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theOptions.isCached() ? theValue : theMap.apply(theParentEl.get());
			}

			@Override
			public String isEnabled() {
				if (theOptions.getElementReverse() != null)
					return null;
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = null;
				if (theOptions.getElementReverse() != null) {
					msg = theOptions.getElementReverse().setElement(theParentEl.get(), value, false);
					if (msg == null)
						return null;
				}
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					return StdMsg.ILLEGAL_ELEMENT;
				String setMsg = theParentEl.isAcceptable(reversed);
				// If the element reverse is set, it should get the final word on the error message if
				if (setMsg == null || msg == null)
					return setMsg;
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (theOptions.getElementReverse() != null) {
					if (theOptions.getElementReverse().setElement(theParentEl.get(), value, true) == null)
						return;
				}
				if (theOptions.getReverse() == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theParentEl.set(reversed);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (theOptions.getReverse() == null)
					return StdMsg.UNSUPPORTED_OPERATION;
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					return StdMsg.ILLEGAL_ELEMENT;
				return theParentEl.canAdd(reversed, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (theOptions.getReverse() == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				I reversed = theOptions.getReverse().apply(value);
				if (!theEquivalence.elementEquals(theMap.apply(reversed), value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				DerivedCollectionElement<I> parentEl = theParentEl.add(reversed, before);
				return parentEl == null ? null : new MappedElement(parentEl, true);
			}

			@Override
			public String toString() {
				return String.valueOf(get());
			}
		}
	}

	private static class PassiveCombinedCollectionManager<E, I, T> implements PassiveCollectionManager<E, I, T> {
		private final PassiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final CombinedFlowDef<I, T> theDef;

		PassiveCombinedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, CombinedFlowDef<I, T> def) {
			theParent = parent;
			theTargetType = targetType;
			theDef = def;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (theTargetType.equals(theParent.getTargetType()))
				return (Equivalence<? super T>) theParent.equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction parentLock = theParent.lock(write, cause);
			Transaction valueLock = lockValues();
			return () -> {
				valueLock.close();
				parentLock.close();
			};
		}

		private Transaction lockValues() {
			Transaction[] valueLocks = new Transaction[theDef.getArgs().size()];
			int v = 0;
			for (ObservableValue<?> arg : theDef.getArgs())
				valueLocks[v++] = arg.lock();
			return () -> {
				for (int a = 0; a < valueLocks.length; a++)
					valueLocks[a].close();
			};
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			ObservableValue<? extends Function<? super E, ? extends I>> parentMap = theParent.map();
			return new ObservableValue<Function<? super E, T>>() {
				/** Can't imagine why this would ever be used, but we'll fill it out I guess */
				private TypeToken<Function<? super E, T>> theType;

				@Override
				public TypeToken<Function<? super E, T>> getType() {
					if (theType == null)
						theType = functionType((TypeToken<E>) parentMap.getType().resolveType(Function.class.getTypeParameters()[0]),
							theTargetType);
					return theType;
				}

				@Override
				public Transaction lock() {
					Transaction parentLock = parentMap.lock();
					Transaction valueLock=lockValues();
					return ()->{
						valueLock.close();
						parentLock.close();
					};
				}

				@Override
				public Function<? super E, T> get() {
					Function<? super E, ? extends I> parentMapVal = parentMap.get();
					Map<ObservableValue<?>, ObservableValue<?>> values = new HashMap<>();
					for (ObservableValue<?> v : theDef.getArgs())
						values.put(v, v);

					return new CombinedMap(parentMapVal, null, values);
				}

				@Override
				public Observable<ObservableValueEvent<Function<? super E, T>>> changes() {
					Observable<? extends ObservableValueEvent<? extends Function<? super E, ? extends I>>> parentChanges=parentMap.changes();
					return new Observable<ObservableValueEvent<Function<? super E, T>>>(){
						private CombinedMap theCurrentMap;

						@Override
						public boolean isSafe() {
							return true;
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<Function<? super E, T>>> observer) {
							Subscription parentSub=parentChanges.act(new Consumer<ObservableValueEvent<? extends Function<? super E, ? extends I>>>(){
								@Override
								public void accept(ObservableValueEvent<? extends Function<? super E, ? extends I>> parentEvt){
									if (parentEvt.isInitial()) {
										theCurrentMap = new CombinedMap(parentEvt.getNewValue(), null, new HashMap<>());
										return;
									}
									try(Transaction valueLock=lockValues()){
										CombinedMap oldMap = theCurrentMap;
										theCurrentMap = new CombinedMap(parentEvt.getNewValue(), null, oldMap.theValues);
										observer.onNext(createChangeEvent(oldMap, theCurrentMap, parentEvt));
									}
								}
							});
							Subscription [] argSubs=new Subscription[theDef.getArgs().size()];
							int a=0;
							for(ObservableValue<?> arg : theDef.getArgs()){
								int argIndex=a++;
								argSubs[argIndex]=arg.changes().act(new Consumer<ObservableValueEvent<?>>(){
									@Override
									public void accept(ObservableValueEvent<?> argEvent) {
										if (argEvent.isInitial()) {
											((Map<ObservableValue<?>, SimpleSupplier>) theCurrentMap.theValues).put(arg,
												new SimpleSupplier(argEvent.getNewValue()));
										}
										try (Transaction t = lock()) {
											CombinedMap oldMap = theCurrentMap;
											Map<ObservableValue<?>, SimpleSupplier> newValues = new HashMap<>(
												(Map<ObservableValue<?>, SimpleSupplier>) oldMap.theValues);
											newValues.put(arg, new SimpleSupplier(argEvent.getNewValue()));
											theCurrentMap = new CombinedMap(oldMap.getParentMap(), null, newValues);
											observer.onNext(createChangeEvent(oldMap, theCurrentMap, argEvent));
										}
									}
								});
							}
							observer.onNext(createInitialEvent(theCurrentMap, null));
							return () -> {
								try (Transaction t = lock()) {
									for (int i = 0; i < argSubs.length; i++)
										argSubs[i].unsubscribe();
									parentSub.unsubscribe();
								}
							};
						}
					};
				}
			};
		}

		@Override
		public String canReverse() {
			return theDef.getReverse() == null ? null : StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (theDef.getReverse() == null)
				return dest.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			Map<ObservableValue<?>, Object[]> values = new HashMap<>();
			for (ObservableValue<?> v : theDef.getArgs())
				values.put(v, new Object[] { v.get() });
			FilterMapResult<I, E> interm = (FilterMapResult<I, E>) dest;
			interm.source = theDef.getReverse().apply(new Combination.CombinedValues<T>() {
				@Override
				public T getElement() {
					return dest.source;
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					Object[] holder = values.get(arg);
					if (holder == null)
						throw new IllegalArgumentException("Unrecognized value: " + arg);
					return (X) holder[0];
				}
			});
			return (FilterMapResult<T, E>) theParent.reverse(interm, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new CombinedElement(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			if (oldSource == newSource) {
				if (!theDef.isFireIfUnchanged())
					return null;
				if (!theDef.isReEvalOnUpdate()) {
					T newDest = map.apply(newSource);
					return new BiTuple<>(newDest, newDest);
				}
			}
			CombinedMap combinedMap = (CombinedMap) map;
			BiTuple<I, I> interm = theParent.map(oldSource, newSource, combinedMap.getParentMap());
			if (interm == null)
				return null;
			if (interm.getValue1() == interm.getValue2()) {
				if (!theDef.isFireIfUnchanged())
					return null;
				if (!theDef.isReEvalOnUpdate()) {
					T newDest = combinedMap.map(interm.getValue2());
					return new BiTuple<>(newDest, newDest);
				}
			}
			return new BiTuple<>(combinedMap.map(interm.getValue1()), combinedMap.map(interm.getValue2()));
		}

		@Override
		public boolean isManyToOne() {
			return theDef.isManyToOne() || theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			if (elements.isEmpty())
				return;
			theParent.setValue(elements.stream().map(el -> ((CombinedElement) el).theInterm).collect(Collectors.toList()),
				((CombinedElement) elements.iterator().next()).theCombinedMap.reverse(value));
		}

		class SimpleSupplier implements Supplier<Object> {
			final Object value;

			SimpleSupplier(Object value) {
				this.value = value;
			}

			@Override
			public Object get() {
				return value;
			}
		}

		class CombinedElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<E> theSource;
			private final MutableCollectionElement<I> theInterm;
			private final CombinedMap theCombinedMap;

			CombinedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				theSource = source;
				theCombinedMap = (CombinedMap) map;
				theInterm = theParent.map(theSource, theCombinedMap.getParentMap());
			}

			@Override
			public ElementId getElementId() {
				return theSource.getElementId();
			}

			@Override
			public T get() {
				return theCombinedMap.map(theInterm.get());
			}

			@Override
			public String isEnabled() {
				String msg = canReverse();
				if (msg != null)
					return msg;
				return theInterm.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				String msg = canReverse();
				if (msg != null)
					return msg;
				I intermVal = theCombinedMap.reverse(value);
				return theInterm.isAcceptable(intermVal);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = canReverse();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				I intermVal = theCombinedMap.reverse(value);
				theInterm.set(intermVal);
			}

			@Override
			public String canRemove() {
				return theInterm.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theInterm.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = canReverse();
				if (msg != null)
					return msg;
				I intermVal = theCombinedMap.reverse(value);
				return theInterm.canAdd(intermVal, before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = canReverse();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				I intermVal = theCombinedMap.reverse(value);
				return theInterm.add(intermVal, before);
			}
		}

		class CombinedMap extends MapWithParent<E, I, T> {
			final Map<ObservableValue<?>, ? extends Supplier<?>> theValues;

			CombinedMap(Function<? super E, ? extends I> parentMap, Function<? super I, ? extends T> map,
				Map<ObservableValue<?>, ? extends Supplier<?>> values) {
				super(parentMap, map);
				theValues = values;
			}

			@Override
			public T apply(E source) {
				I interm = getParentMap().apply(source);
				return map(interm);
			}

			T map(I interm) {
				return theDef.getCombination().apply(new Combination.CombinedValues<I>() {
					@Override
					public I getElement() {
						return interm;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				});
			}

			I reverse(T dest) {
				return theDef.getReverse().apply(new Combination.CombinedValues<T>() {
					@Override
					public T getElement() {
						return dest;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				});
			}
		}
	}

	private static class ActiveCombinedCollectionManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final CombinedFlowDef<I, T> theDef;

		private final Map<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> theArgs;

		// Need to keep track of these to update them when the combined values change
		private final List<CombinedElement> theElements;

		ActiveCombinedCollectionManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, CombinedFlowDef<I, T> def) {
			theParent = parent;
			theTargetType = targetType;
			theDef = def;
			theArgs = new HashMap<>();
			for (ObservableValue<?> arg : def.getArgs())
				theArgs.put(arg, theDef.createCacheHandler(new XformOptions.XformCacheHandlingInterface<Object, Void>() {
					@Override
					public Function<? super Object, ? extends Void> map() {
						return v -> null;
					}

					@Override
					public Transaction lock() {
						// Should not be called, though
						return ActiveCombinedCollectionManager.this.lock(true, false, null);
					}

					@Override
					public Void getDestCache() {
						return null;
					}

					@Override
					public void setDestCache(Void value) {}
				}));

			theElements = new ArrayList<>();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (theTargetType.equals(theParent.getTargetType()))
				return (Equivalence<? super T>) theParent.equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction parentLock = theParent.lock(write, structural, cause);
			Transaction argLock = lockArgs();
			return () -> {
				argLock.close();
				parentLock.close();
			};
		}

		private Transaction lockArgs() {
			Transaction[] argLocks = new Transaction[theArgs.size()];
			int a = 0;
			for (ObservableValue<?> arg : theArgs.keySet())
				argLocks[a++] = arg.lock();
			return () -> {
				for (int a2 = 0; a2 < argLocks.length; a2++)
					argLocks[a2].close();
			};
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theDef.getReverse() == null || theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			if (theDef.getReverse() == null)
				return null;
			Comparable<DerivedCollectionElement<I>> pef = theParent.getElementFinder(reverseValue(value));
			if (pef == null)
				return null;
			return el -> pef.compareTo(((CombinedElement) el).theParentEl);
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(reverseValue(toAdd));
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<I> parentEl = theParent.addElement(reverseValue(value), first);
			return parentEl == null ? null : new CombinedElement(parentEl, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((CombinedElement) el).theParentEl).collect(Collectors.toList()),
				reverseValue(newValue));
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			for (Map.Entry<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> arg : theArgs.entrySet()) {
				XformOptions.XformCacheHandler<Object, Void> holder = arg.getValue();
				listening.withConsumer((ObservableValueEvent<?> evt) -> {
					if (evt.isInitial()) {
						holder.initialize(evt.getNewValue());
						return;
					}
					Object oldValue = theDef.isCached() ? holder.getSourceCache() : evt.getOldValue();
					Ternian update = holder.isUpdate(evt.getOldValue(), evt.getNewValue());
					if (update == null)
						return; // No change, no event
					try (Transaction t = lock(true, false, null)) {
						// The old values are not needed if we're caching each element value
						Object[] source = theDef.isCached() ? null : new Object[1];
						Combination.CombinedValues<I> oldValues = theDef.isCached() ? null : getCopy(source, arg.getKey(), oldValue);
						// The order of update here may be different than the order in the derived collection
						// It's a lot of work to keep the elements in order (since the order may change),
						// so we'll just let order of addition be good enough
						for (CombinedElement el : theElements)
							el.updated(src -> {
								source[0] = src;
								return oldValues;
							}, evt, update.value);
					}
				}, action -> arg.getKey().changes().act(action));
			}
			theParent.begin(fromStart, (parentEl, cause) -> {
				try (Transaction t = lockArgs()) {
					CombinedElement el = new CombinedElement(parentEl, false);
					theElements.add(el);
					onElement.accept(el, cause);
				}
			}, listening);
		}

		private <V> V getArgValue(ObservableValue<V> arg) {
			XformOptions.XformCacheHandler<Object, Void> holder = theArgs.get(arg);
			if (holder == null)
				throw new IllegalArgumentException("Unrecognized value: " + arg);
			if (theDef.isCached())
				return (V) holder.getSourceCache();
			else
				return arg.get();
		}

		private T combineValue(I source) {
			return theDef.getCombination().apply(new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return source;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
				}
			});
		}

		private I reverseValue(T dest) {
			return theDef.getReverse().apply(new Combination.CombinedValues<T>() {
				@Override
				public T getElement() {
					return dest;
				}

				@Override
				public <V> V get(ObservableValue<V> arg) {
					return getArgValue(arg);
				}
			});
		}

		private Combination.CombinedValues<I> getCopy(Object[] source, ObservableValue<?> replaceArg, Object value) {
			Map<ObservableValue<?>, Object> theValues = new HashMap<>();
			for (Map.Entry<ObservableValue<?>, XformOptions.XformCacheHandler<Object, Void>> holder : theArgs.entrySet()) {
				if (holder.getKey() == replaceArg)
					theValues.put(holder.getKey(), value);
				else if (theDef.isCached())
					theValues.put(holder.getKey(), holder.getValue().getSourceCache());
				else
					theValues.put(holder.getKey(), holder.getKey().get());
			}
			return new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return (I) source[0];
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return (X) theValues.get(arg);
				}
			};
		}

		private class CombinedElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<I> theParentEl;
			private CollectionElementListener<T> theListener;
			private final XformOptions.XformCacheHandler<I, T> theCacheHandler;
			private T theValue;

			CombinedElement(DerivedCollectionElement<I> parentEl, boolean synthetic) {
				theParentEl = parentEl;
				if (!synthetic) {
					theCacheHandler = theDef.createCacheHandler(new XformOptions.XformCacheHandlingInterface<I, T>() {
						@Override
						public Function<? super I, ? extends T> map() {
							return v -> combineValue(v);
						}

						@Override
						public Transaction lock() {
							return lockArgs();
						}

						@Override
						public T getDestCache() {
							return theValue;
						}

						@Override
						public void setDestCache(T value) {
							theValue = value;
						}
					});
					theParentEl.setListener(new CollectionElementListener<I>() {
						@Override
						public void update(I oldSource, I newSource, Object cause) {
							BiTuple<T, T> values = theCacheHandler.handleChange(oldSource, newSource);
							if (values != null)
								ObservableCollectionDataFlowImpl.update(theListener, values.getValue1(), values.getValue2(), cause);
						}

						@Override
						public void removed(I value, Object cause) {
							try (Transaction t = lockArgs()) {
								T val = theDef.isCached() ? theValue : combineValue(value);
								theElements.remove(this);
								ObservableCollectionDataFlowImpl.removed(theListener, val, cause);
								theListener = null;
								theValue = null;
							}
						}
					});
					if (theDef.isCached()) {
						I parentValue = theParentEl.get();
						theCacheHandler.initialize(parentValue);
						theValue = combineValue(parentValue);
					}
				} else
					theCacheHandler = null;
			}

			void updated(Function<I, Combination.CombinedValues<I>> oldValues, Object cause, boolean isUpdate) {
				I parentValue = theDef.isCached() ? theCacheHandler.getSourceCache() : theParentEl.get();
				BiTuple<T, T> values = theCacheHandler.handleChange(parentValue, parentValue, isUpdate);
				if (values != null)
					ObservableCollectionDataFlowImpl.update(theListener, values.getValue1(), values.getValue2(), cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((CombinedElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theDef.isCached() ? theValue : combineValue(theParentEl.get());
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theParentEl.isAcceptable(reverseValue(value));
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theParentEl.set(reverseValue(value));
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				return theParentEl.canAdd(reverseValue(value), before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				DerivedCollectionElement<I> parentEl = theParentEl.add(reverseValue(value), before);
				return parentEl == null ? null : new CombinedElement(parentEl, true);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	private static class PassiveRefreshingCollectionManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;
		private final ReentrantLock theLock;

		PassiveRefreshingCollectionManager(PassiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theLock = new ReentrantLock();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction parentLock = theParent.lock(write, cause);
			theLock.lock();
			boolean[] unlocked = new boolean[1];
			return () -> {
				if (unlocked[0])
					return;
				unlocked[0] = true;
				theLock.unlock();
				parentLock.close();
			};
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map().refresh(theRefresh);
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
		}
	}

	private static class ActiveRefreshingCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;
		private final BetterCollection<RefreshingElement> theElements;

		ActiveRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theElements = new BetterTreeList<>(false);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder != null)
				return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
				return null;
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new RefreshingElement(parentEl, true);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> {
				onElement.accept(new RefreshingElement(parentEl, false), cause);
			}, listening);
			listening.withConsumer((Object r) -> {
				try (Transaction t = lock(true, false, r)) {
					for (RefreshingElement el : theElements)
						el.refresh(r);
				}
			}, theRefresh::act);
		}

		private class RefreshingElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private final ElementId theElementId;
			private CollectionElementListener<T> theListener;

			RefreshingElement(DerivedCollectionElement<T> parent, boolean synthetic) {
				theParentEl = parent;
				if (!synthetic) {
					theElementId = theElements.addElement(this, false).getElementId();
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
						}

						@Override
						public void removed(T value, Object cause) {
							theElements.mutableElement(theElementId).remove();
							ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
						}
					});
				} else
					theElementId = null;
			}

			void refresh(Object cause) {
				T value = get();
				ObservableCollectionDataFlowImpl.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				return theParentEl.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				DerivedCollectionElement<T> parent = theParentEl.add(value, before);
				return parent == null ? null : new RefreshingElement(parent, true);
			}
		}
	}

	private static class ElementRefreshingCollectionManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private class RefreshHolder {
			private final ElementId theElementId;
			private final Subscription theSub;
			final BetterCollection<RefreshingElement> elements;

			RefreshHolder(Observable<?> refresh) {
				theElementId = theRefreshObservables.putEntry(refresh, this, false).getElementId();
				elements = new BetterTreeList<>(false);
				theSub = theListening.withConsumer(r -> {
					try (Transaction t = lock(true, false, r)) {
						for (RefreshingElement el : elements)
							el.refresh(r);
					}
				}, action -> refresh.safe().act(action));
			}

			void remove(ElementId element) {
				elements.mutableElement(element).remove();
				if (elements.isEmpty()) {
					theSub.unsubscribe();
					theRefreshObservables.mutableEntry(theElementId).remove();
				}
			}
		}

		private final ActiveCollectionManager<E, ?, T> theParent;
		private final Function<? super T, ? extends Observable<?>> theRefresh;
		private final BetterMap<Observable<?>, RefreshHolder> theRefreshObservables;
		private WeakListening theListening;

		ElementRefreshingCollectionManager(ActiveCollectionManager<E, ?, T> parent, Function<? super T, ? extends Observable<?>> refresh) {
			theParent = parent;
			theRefresh = refresh;
			theRefreshObservables = BetterHashMap.build().unsafe().buildMap();
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			Comparable<DerivedCollectionElement<T>> parentFinder = theParent.getElementFinder(value);
			if (parentFinder != null)
				return el -> parentFinder.compareTo(((RefreshingElement) el).theParentEl);
				return null;
		}

		@Override
		public String canAdd(T toAdd) {
			return theParent.canAdd(toAdd);
		}

		@Override
		public boolean clear() {
			return theParent.clear();
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new RefreshingElement(parentEl);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			theParent.setValues(elements.stream().map(el -> ((RefreshingElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theListening = listening;
			theParent.begin(fromStart, (parentEl, cause) -> {
				onElement.accept(new RefreshingElement(parentEl), cause);
			}, listening);
		}

		private class RefreshingElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;
			private Observable<?> theCurrentRefresh;
			private RefreshHolder theCurrentHolder;
			private ElementId theElementId;
			private CollectionElementListener<T> theListener;
			private boolean isInstalled;

			RefreshingElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			/**
			 * Called when the value of the element changes. Handles the case where the element's refresh observable changes as a result.
			 *
			 * @param value The new value for the element
			 */
			void updated(T value) {
				Observable<?> newRefresh = theRefresh.apply(value);
				if (newRefresh == theCurrentRefresh)
					return;

				// Refresh is different, need to remove from old refresh and add to new
				if (theCurrentHolder != null) { // Remove from old refresh if non-null
					theCurrentHolder.remove(theElementId);
					theElementId = null;
					theCurrentHolder = null;
				}
				theCurrentRefresh = newRefresh;
				if (newRefresh != null) {
					RefreshHolder newHolder = theRefreshObservables.get(newRefresh);
					if (newHolder == null)
						newHolder = new RefreshHolder(newRefresh); // Adds itself
					theCurrentHolder = newHolder;
					theElementId = newHolder.elements.addElement(this, false).getElementId();
				}
			}

			private void installOrUninstall() {
				if (!isInstalled && theListener != null) {
					isInstalled = true;
					updated(theParentEl.get()); // Subscribe to the initial refresh value
					theParentEl.setListener(new CollectionElementListener<T>() {
						@Override
						public void update(T oldValue, T newValue, Object cause) {
							ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							updated(newValue);
						}

						@Override
						public void removed(T value, Object cause) {
							if (theCurrentHolder != null) { // Remove from old refresh if non-null
								theCurrentHolder.remove(theElementId);
								theElementId = null;
								theCurrentHolder = null;
							}
							theCurrentRefresh = null;
							ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
						}
					});
				} else if (isInstalled && theListener == null) {
					theParentEl.setListener(null);
					if (theCurrentHolder != null) { // Remove from old refresh if non-null
						theCurrentHolder.remove(theElementId);
						theElementId = null;
						theCurrentHolder = null;
					}
					theCurrentRefresh = null;
				}
			}

			private void refresh(Object cause) {
				T value = get();
				ObservableCollectionDataFlowImpl.update(theListener, value, value, cause);
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((RefreshingElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
				installOrUninstall();
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				return theParentEl.canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new RefreshingElement(parentEl);
			}
		}
	}

	private static class PassiveModFilteredManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final ModFilterer<T> theFilter;

		PassiveModFilteredManager(PassiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			String msg = theFilter.canAdd();
			if (msg != null)
				return msg;
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (forAdd) {
				dest.maybeReject(theFilter.canAdd(dest.source), true);
				if (!dest.isAccepted())
					return dest;
			}
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theFilter.isRemoveFiltered() || theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new ModFilteredElement(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements.stream().map(el -> ((ModFilteredElement) el).theParentMapped).collect(Collectors.toList()), value);
		}

		private class ModFilteredElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<T> theParentMapped;

			ModFilteredElement(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
				theParentMapped = theParent.map(element, map);
			}

			@Override
			public ElementId getElementId() {
				return theParentMapped.getElementId();
			}

			@Override
			public T get() {
				return theParentMapped.get();
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentMapped.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, this::get);
				if (msg == null)
					msg = theParentMapped.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, this::get);
				theParentMapped.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(this::get);
				if (msg == null)
					msg = theParentMapped.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(this::get);
				theParentMapped.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theFilter.canAdd(value);
				if (msg == null)
					msg = theParentMapped.canAdd(value, before);
				return msg;
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertAdd(value);
				return theParentMapped.add(value, before);
			}
		}
	}

	private static class ActiveModFilteredManager<E, T> implements ActiveCollectionManager<E, T, T> {
		private final ActiveCollectionManager<E, ?, T> theParent;

		private final ModFilterer<T> theFilter;

		ActiveModFilteredManager(ActiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theParent.lock(write, structural, cause);
		}

		@Override
		public boolean clear() {
			if (!theFilter.isRemoveFiltered())
				return theParent.clear();
			if (theFilter.theImmutableMessage != null || theFilter.theRemoveMessage != null)
				return true;
			else
				return false;
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return theParent.getElementFinder(value);
		}

		@Override
		public String canAdd(T toAdd) {
			String msg = theFilter.canAdd(toAdd);
			if (msg == null)
				msg = theParent.canAdd(toAdd);
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			theFilter.assertAdd(value);
			DerivedCollectionElement<T> parentEl = theParent.addElement(value, first);
			return parentEl == null ? null : new ModFilteredElement(parentEl);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			for (DerivedCollectionElement<T> el : elements)
				theFilter.assertSet(newValue, el::get);
			theParent.setValues(elements.stream().map(el -> ((ModFilteredElement) el).theParentEl).collect(Collectors.toList()), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theParent.begin(fromStart, (parentEl, cause) -> onElement.accept(new ModFilteredElement(parentEl), cause), listening);
		}

		private class ModFilteredElement implements DerivedCollectionElement<T> {
			private final DerivedCollectionElement<T> theParentEl;

			ModFilteredElement(DerivedCollectionElement<T> parentEl) {
				theParentEl = parentEl;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				return theParentEl.compareTo(((ModFilteredElement) o).theParentEl);
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theParentEl.setListener(listener);
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentEl.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, this::get);
				if (msg == null)
					msg = theParentEl.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, this::get);
				theParentEl.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentEl.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(this::get);
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theFilter.canAdd(value);
				if (msg == null)
					msg = theParentEl.canAdd(value, before);
				return msg;
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertAdd(value);
				DerivedCollectionElement<T> parentEl = theParentEl.add(value, before);
				return parentEl == null ? null : new ModFilteredElement(parentEl);
			}
		}
	}

	private static class FlattenedManager<E, I, T> implements ActiveCollectionManager<E, I, T> {
		private final ActiveCollectionManager<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private final Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> theMap;

		private ElementAccepter<T> theAccepter;
		private WeakListening theListening;
		private final BetterList<FlattenedHolder> theOuterElements;

		public FlattenedManager(ActiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends CollectionDataFlow<?, ?, ? extends T>> map) {
			theParent = parent;
			theTargetType = targetType;
			theMap = map;

			theOuterElements = new BetterTreeList<>(false);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theTargetType;
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			Transaction outerLock = theParent.lock(write, structural, cause);
			List<Transaction> innerLocks = new LinkedList<>();
			for (FlattenedHolder holder : theOuterElements) {
				if (holder.manager != null)
					innerLocks.add(holder.manager.lock(write, structural, cause));
			}
			return () -> {
				for (Transaction innerLock : innerLocks)
					innerLock.close();
				outerLock.close();
			};
		}

		@Override
		public boolean isContentControlled() {
			try (Transaction t = theParent.lock(true, false, null)) {
				boolean anyControlled = false;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					anyControlled |= outerEl.manager.isContentControlled();
				}
				return anyControlled;
			}
		}

		@Override
		public Comparable<DerivedCollectionElement<T>> getElementFinder(T value) {
			return null;
		}

		@Override
		public boolean clear() {
			try (Transaction t = theParent.lock(true, false, null)) {
				boolean allCleared = true;
				for (FlattenedHolder outerEl : theOuterElements) {
					if (outerEl.manager == null)
						continue;
					allCleared &= outerEl.manager.clear();
				}
				return allCleared;
			}
		}

		@Override
		public String canAdd(T toAdd) {
			String msg = null;
			try (Transaction t = theParent.lock(true, false, null)) {
				for (FlattenedHolder holder : theOuterElements) {
					if (holder.manager == null)
						continue;
					if (holder.manager.getTargetType().wrap().getRawType().isInstance(toAdd)
						&& holder.manager.equivalence().isElement(toAdd))
						msg = ((ActiveCollectionManager<?, ?, T>) holder.manager).canAdd(toAdd);
					else
						msg = StdMsg.ILLEGAL_ELEMENT;
					if (msg == null)
						return null;
				}
			}
			if (msg == null)
				msg = StdMsg.UNSUPPORTED_OPERATION;
			return msg;
		}

		@Override
		public DerivedCollectionElement<T> addElement(T value, boolean first) {
			String msg = null;
			boolean tried = false;
			try (Transaction t = theParent.lock(true, false, null)) {
				for (FlattenedHolder holder : (first ? theOuterElements : theOuterElements.reverse())) {
					String msg_i;
					if (holder.manager == null)
						continue;
					if (holder.manager.getTargetType().wrap().getRawType().isInstance(value)
						&& holder.manager.equivalence().isElement(value))
						msg_i = ((ActiveCollectionManager<?, ?, T>) holder.manager).canAdd(value);
					else
						msg_i = StdMsg.ILLEGAL_ELEMENT;
					if (msg == null) {
						tried = true;
						DerivedCollectionElement<T> el = ((ActiveCollectionManager<?, ?, T>) holder.manager).addElement(value, first);
						if (el != null)
							return new FlattenedElement(holder, el, true);
					} else
						msg = msg_i;
				}
			}
			if (tried)
				return null;
			if (msg == null)
				msg = StdMsg.UNSUPPORTED_OPERATION;
			throw new UnsupportedOperationException(msg);
		}

		@Override
		public void setValues(Collection<DerivedCollectionElement<T>> elements, T newValue)
			throws UnsupportedOperationException, IllegalArgumentException {
			// Group by collection
			BetterMap<ActiveCollectionManager<?, ?, T>, List<DerivedCollectionElement<T>>> grouped = BetterHashMap.build().identity()
				.unsafe().buildMap();
			for (DerivedCollectionElement<T> el : elements)
				grouped
				.computeIfAbsent((ActiveCollectionManager<?, ?, T>) ((FlattenedElement) el).theHolder.manager, h -> new ArrayList<>())
				.add((DerivedCollectionElement<T>) ((FlattenedElement) el).theParentEl);

			for (Map.Entry<ActiveCollectionManager<?, ?, T>, List<DerivedCollectionElement<T>>> entry : grouped.entrySet())
				entry.getKey().setValues(entry.getValue(), newValue);
		}

		@Override
		public void begin(boolean fromStart, ElementAccepter<T> onElement, WeakListening listening) {
			theAccepter = onElement;
			theListening = listening;
			theParent.begin(fromStart, (parentEl, cause) -> {
				theOuterElements.add(new FlattenedHolder(parentEl, listening, cause, fromStart));
			}, listening);
		}

		private class FlattenedHolder {
			private final DerivedCollectionElement<I> theParentEl;
			private final BetterCollection<FlattenedElement> theElements;
			private final WeakListening.Builder theChildListening = theListening.child();
			private final boolean isFromStart;
			private CollectionDataFlow<?, ?, ? extends T> theFlow;
			ActiveCollectionManager<?, ?, ? extends T> manager;

			FlattenedHolder(DerivedCollectionElement<I> parentEl, WeakListening listening, Object cause, boolean fromStart) {
				theParentEl = parentEl;
				theElements = new BetterTreeList<>(false);
				isFromStart = fromStart;
				updated(theParentEl.get(), cause);
				theParentEl.setListener(new CollectionElementListener<I>() {
					@Override
					public void update(I oldValue, I newValue, Object innerCause) {
						updated(newValue, innerCause);
					}

					@Override
					public void removed(I value, Object innerCause) {
						clearSubElements(innerCause);
					}
				});
			}

			void updated(I newValue, Object cause) {
				try (Transaction t = theParent.lock(true, false, cause)) {
					CollectionDataFlow<?, ?, ? extends T> newFlow = theMap.apply(newValue);
					if (newFlow == theFlow)
						return;
					clearSubElements(cause);
					theFlow = newFlow;
					manager = newFlow.manageActive();
					manager.begin(isFromStart, (childEl, innerCause) -> {
						FlattenedElement flatEl = new FlattenedElement(this, childEl, false);
						theAccepter.accept(flatEl, innerCause);
					}, theChildListening.getListening());
				}
			}

			void clearSubElements(Object cause) {
				if (manager == null)
					return;
				try (Transaction t = manager.lock(true, true, cause)) {
					theChildListening.unsubscribe(); // unsubscribe here removes all elements
					manager = null;
				}
			}
		}

		private class FlattenedElement implements DerivedCollectionElement<T> {
			private final FlattenedHolder theHolder;
			private final DerivedCollectionElement<? extends T> theParentEl;
			private final ElementId theElementId;
			private CollectionElementListener<T> theListener;

			<X extends T> FlattenedElement(FlattenedHolder holder, DerivedCollectionElement<X> parentEl, boolean synthetic) {
				theHolder = holder;
				theParentEl = parentEl;
				if (!synthetic) {
					theElementId = theHolder.theElements.addElement(this, false).getElementId();
					parentEl.setListener(new CollectionElementListener<X>() {
						@Override
						public void update(X oldValue, X newValue, Object cause) {
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction t = theParent.lock(true, false, null)) {
								ObservableCollectionDataFlowImpl.update(theListener, oldValue, newValue, cause);
							}
						}

						@Override
						public void removed(X value, Object cause) {
							theHolder.theElements.mutableElement(theElementId).remove();
							// Need to make sure that the flattened collection isn't firing at the same time as the child collection
							try (Transaction t = theParent.lock(true, false, null)) {
								ObservableCollectionDataFlowImpl.removed(theListener, value, cause);
							}
						}
					});
				} else
					theElementId = null;
			}

			@Override
			public int compareTo(DerivedCollectionElement<T> o) {
				FlattenedElement flat = (FlattenedElement) o;
				int comp = theHolder.theParentEl.compareTo(flat.theHolder.theParentEl);
				if (comp == 0)
					comp = ((DerivedCollectionElement<T>) theParentEl).compareTo((DerivedCollectionElement<T>) flat.theParentEl);
				return comp;
			}

			@Override
			public void setListener(CollectionElementListener<T> listener) {
				theListener = listener;
			}

			@Override
			public T get() {
				return theParentEl.get();
			}

			@Override
			public String isEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			public String isAcceptable(T value) {
				if (value != null && !theHolder.manager.getTargetType().wrap().getRawType().isInstance(value))
					return StdMsg.BAD_TYPE;
				return ((DerivedCollectionElement<T>) theParentEl).isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value != null && !theHolder.manager.getTargetType().wrap().getRawType().isInstance(value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((DerivedCollectionElement<T>) theParentEl).set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				if (value != null && !theHolder.manager.getTargetType().wrap().getRawType().isInstance(value))
					return StdMsg.BAD_TYPE;
				return ((DerivedCollectionElement<T>) theParentEl).canAdd(value, before);
			}

			@Override
			public DerivedCollectionElement<T> add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				if (value != null && !theHolder.manager.getTargetType().wrap().getRawType().isInstance(value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				DerivedCollectionElement<? extends T> parentEl = ((DerivedCollectionElement<T>) theParentEl).add(value, before);
				return parentEl == null ? null : new FlattenedElement(theHolder, parentEl, true);
			}

			@Override
			public String toString() {
				return theHolder.theParentEl.toString() + "/" + theParentEl.toString();
			}
		}
	}
}

package org.observe.collect;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.Eventable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Transformation;
import org.observe.Transformation.TransformationState;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractTransformedManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.MapWithParent;
import org.observe.collect.ObservableCollectionDataFlowImpl.ModFilterer;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.Stamped;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Contains implementations of {@link PassiveCollectionManager} and its dependencies */
public class ObservableCollectionPassiveManagers {
	private ObservableCollectionPassiveManagers() {}

	/**
	 * A manager for a {@link ObservableCollection.CollectionDataFlow#supportsPassive() passively-}derived collection
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that this manager can power
	 */
	public static interface PassiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T>, Stamped, Eventable {
		/** @return Whether this manager is the result of an odd number of {@link CollectionDataFlow#reverse() reverse} operations */
		boolean isReversed();

		/** @return The observable value of this manager's mapping function that produces values from source values */
		ObservableValue<? extends Function<? super E, ? extends T>> map();

		/**
		 * @return Whether this manager has the ability to convert its values to source values for at least some sub-set of possible values
		 */
		String canReverse();

		/**
		 * @param dest The filter-map structure whose source is the value to convert
		 * @param forAdd Whether this operation should return an error if the value cannot be an element in the collection
		 * @param test True if the value will be discarded after this operation, false if it may be kept as part of the collection
		 * @return The filter-mapped result (typically the same instance as <code>dest</code>)
		 */
		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test);

		/**
		 * A shortcut for reversing a value
		 *
		 * @param dest The value to convert
		 * @param forAdd Whether this operation should return an error if the value cannot be an element in the collection
		 * @param test True if the value will be discarded after this operation, false if it may be kept as part of the collection
		 * @return The filter-mapped result
		 */
		default FilterMapResult<T, E> reverse(T dest, boolean forAdd, boolean test) {
			return reverse(new FilterMapResult<>(dest), forAdd, test);
		}

		/** @return Whether this manager may disallow some remove operations */
		boolean isRemoveFiltered();

		/** @return null if this manager allows derived collections to move elements around, or a reason otherwise */
		default String canMove() {
			return null;
		}

		/**
		 * @param element The source element to map
		 * @param map The mapping function to apply to the element's value
		 * @return The element for the derived collection
		 */
		MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map);

		/**
		 * Maps the old and new source values for an event from the source collection to this manager's value type
		 *
		 * @param oldSource The old value from the source event
		 * @param newSource The new value from the source event
		 * @param map The mapping function to apply to the values
		 * @return The old and new values for the event to propagate from the manager's derived collection
		 */
		BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map);

		/**
		 * @return Whether this manager may produce a single mapped value for many different source values. Affects searching by value in
		 *         the derived collection.
		 */
		boolean isManyToOne();

		/**
		 * @param elements The elements to set the value for en masse
		 * @param value The value to set for the elements
		 */
		void setValue(Collection<MutableCollectionElement<T>> elements, T value);
	}

	/**
	 * Derives a function type from its parameter types
	 *
	 * @param <E> The compiler type of the function's source parameter
	 * @param <T> The compiler type of the function's product parameter
	 * @param srcType The run-time type of the function's source parameter
	 * @param destType The run-time type of the function's product parameter
	 * @return The type of a function with the given parameter types
	 */
	static <E, T> TypeToken<Function<? super E, T>> functionType(TypeToken<E> srcType, TypeToken<T> destType) {
		return new TypeToken<Function<? super E, T>>() {}.where(new TypeParameter<E>() {}, srcType.wrap()).where(new TypeParameter<T>() {},
			destType.wrap());
	}

	/**
	 * Supports passive collection of a base collection flow
	 *
	 * @param <E> The type of the collection and therefore the flow
	 */
	public static class BaseCollectionPassThrough<E> implements PassiveCollectionManager<E, E, E> {
		private static final ConcurrentHashMap<TypeToken<?>, TypeToken<? extends Function<?, ?>>> thePassThroughFunctionTypes = new ConcurrentHashMap<>();

		private final ObservableCollection<E> theSource;
		private final ObservableValue<Function<? super E, E>> theFunctionValue;

		/** @param source The source collection */
		public BaseCollectionPassThrough(ObservableCollection<E> source) {
			theSource = source;

			TypeToken<E> srcType = theSource.getType();
			TypeToken<Function<? super E, E>> functionType = (TypeToken<Function<? super E, E>>) thePassThroughFunctionTypes
				.computeIfAbsent(srcType, st -> functionType(st, st));
			theFunctionValue = ObservableValue.of(functionType, LambdaUtils.identity());
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "passiveManager");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theSource.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theSource.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theSource.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theSource.getCoreId();
		}

		@Override
		public long getStamp() {
			return theSource.getStamp();
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
		public boolean isReversed() {
			return false;
		}

		@Override
		public ObservableValue<Function<? super E, E>> map() {
			return theFunctionValue;
		}

		@Override
		public String canReverse() {
			return null;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest, boolean forAdd, boolean test) {
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

	static class PassiveReversedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;

		PassiveReversedManager(PassiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "reverse");
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			Equivalence<? super T> equiv = theParent.equivalence();
			if (equiv instanceof Equivalence.SortedEquivalence)
				return ((Equivalence.SortedEquivalence<? super T>) equiv).reverse();
			return theParent.equivalence();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theParent.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theParent.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public long getStamp() {
			return theParent.getStamp();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
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
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test) {
			return theParent.reverse(dest, forAdd, test);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map); // Don't reverse here--the passive collection takes care of it
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
				elements, value);
			// elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}
	}

	static class PassiveEquivalenceSwitchedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		PassiveEquivalenceSwitchedManager(PassiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "withEquivalence", theEquivalence);
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
		public ThreadConstraint getThreadConstraint() {
			return theParent.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theParent.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public long getStamp() {
			return theParent.getStamp();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
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
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test) {
			return theParent.reverse(dest, forAdd, test);
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

	static class PassiveTransformedCollectionManager<E, I, T> extends AbstractTransformedManager<E, I, T>
	implements PassiveCollectionManager<E, I, T> {
		PassiveTransformedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Transformation<I, T> transformation, Equivalence<? super T> equivalence) {
			super(parent, targetType, transformation, equivalence);
		}

		@Override
		protected PassiveCollectionManager<E, ?, I> getParent() {
			return (PassiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean isEventing() {
			return getParent().isEventing() || getEngine().isEventing();
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(getParent().getStamp(), getTransformation().createEngine(getParent().equivalence()).getStamp());
		}

		@Override
		public boolean isReversed() {
			return getParent().isReversed();
		}

		@Override
		protected void doParentMultiSet(Collection<AbstractTransformedElement> elements, I newValue) {
			getParent().setValue(elements.stream().map(el -> ((TransformedElement) el).getParentEl()).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return getParent().map().transform(//
				(TypeToken<Function<? super E, ? extends T>>) (TypeToken<?>) TypeTokens.get().of(Function.class), //
				tx -> tx.combineWith(getEngine()).combine((parentMap, state) -> {
					return new TransformedMap(parentMap, state);
				}));
		}

		@Override
		public String canReverse() {
			if (!isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			return getParent().canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test) {
			I reversed;
			if(!forAdd || test){
				org.observe.Transformation.ReverseQueryResult<I> qr=getEngine().reverse(dest.source, forAdd, test);
				if(qr.getError()!=null)
					return dest.reject(qr.getError(), true);
				reversed=qr.getReversed();
			} else
				reversed = getEngine().reverse(dest.source, true, false).getReversed();
			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) dest;
			intermediate.source = reversed;
			intermediate.result=null;
			return (FilterMapResult<T, E>) getParent().reverse(intermediate, forAdd, test);
		}

		@Override
		public boolean isRemoveFiltered() {
			return getParent().isRemoveFiltered();
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			TransformedMap cm = (TransformedMap) map;
			BiTuple<I, I> interm = getParent().map(oldSource, newSource, cm.getParentMap());
			if (interm == null)
				return null;
			return getEngine().createElement(interm::getValue1).sourceChanged(interm.getValue1(), interm.getValue2(), cm.theState);
		}

		@Override
		public boolean isManyToOne() {
			return getTransformation().isManyToOne() || getParent().isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			setElementsValue(elements, value);
		}

		@Override
		public TransformedElement map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
			return new TransformedElement(source, map);
		}

		protected class TransformedElement extends AbstractTransformedElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<I> theParentEl;
			private final TransformedMap theTransformedMap;

			protected TransformedElement(MutableCollectionElement<E> sourceEl, Function<? super E, ? extends T> map) {
				super(() -> ((MapWithParent<E, I, T>) map).getParentMap().apply(sourceEl.get()));
				theTransformedMap = (TransformedMap) map;
				theParentEl = getParent().map(sourceEl, theTransformedMap.getParentMap());
			}

			protected MutableCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public ElementId getElementId() {
				return theParentEl.getElementId();
			}

			@Override
			public T get() {
				return transformElement.getCurrentValue(getEngine().get());
			}

			@Override
			public BetterCollection<T> getCollection() {
				return null;
			}

			@Override
			public String isEnabled() {
				return isEnabledLocal();
			}

			@Override
			public String isAcceptable(T value) {
				return super.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				super.set(value);
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
			protected String isParentEnabled() {
				return theParentEl.isEnabled();
			}

			@Override
			protected String isParentAcceptable(I value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			void setParent(I parentValue) {
				theParentEl.set(parentValue);
			}

			@Override
			public String canAdd(T value, boolean before) {
				Transformation.ReverseQueryResult<I> rq = getEngine().reverse(value, true, true);
				if (rq.getError() != null)
					return rq.getError();
				return theParentEl.canAdd(rq.getReversed(), before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				I reversed = getEngine().reverse(value, true, false).getReversed();
				return theParentEl.add(reversed, before);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}

		class TransformedMap extends MapWithParent<E, I, T> {
			private final Transformation.TransformationState theState;

			TransformedMap(Function<? super E, ? extends I> parentMap, TransformationState state) {
				super(parentMap, null);
				theState = state;
			}

			@Override
			public T mapIntermediate(I interm) {
				return getEngine().map(interm, theState);
			}
		}
	}

	static class PassiveRefreshingCollectionManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;

		PassiveRefreshingCollectionManager(PassiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh.getIdentity());
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
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(theParent, theRefresh);
		}

		@Override
		public boolean isEventing() {
			return theParent.isEventing() || theRefresh.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public long getStamp() {
			return theParent.getStamp();
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
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
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test) {
			return theParent.reverse(dest, forAdd, test);
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

	static class PassiveModFilteredManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final ModFilterer<T> theFilter;

		PassiveModFilteredManager(PassiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "filterMod", theFilter);
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
		public ThreadConstraint getThreadConstraint() {
			return theParent.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theParent.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theParent.getCoreId();
		}

		@Override
		public long getStamp() {
			return theParent.getStamp();
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
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
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd, boolean test) {
			if (forAdd) {
				dest.maybeReject(theFilter.canAdd(dest.source), true);
				if (!dest.isAccepted())
					return dest;
			}
			return theParent.reverse(dest, forAdd, test);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theFilter.isRemoveFiltered() || theFilter.getUnmodifiableMessage() != null || theParent.isRemoveFiltered();
		}

		@Override
		public String canMove() {
			return theFilter.canMove();
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
			public BetterCollection<T> getCollection() {
				return null;
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
				String msg = theFilter.isAcceptable(value, //
					this::get);
				if (msg == null)
					msg = theParentMapped.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, //
					this::get);
				theParentMapped.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentMapped.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(//
					this::get);
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

			@Override
			public String toString() {
				return theParentMapped.toString();
			}
		}
	}
}

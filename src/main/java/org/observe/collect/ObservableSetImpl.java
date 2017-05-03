package org.observe.collect;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionImpl.ElementRefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.FilterMappedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ModFilteredCollection;
import org.observe.collect.ObservableCollectionImpl.RefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.TakenUntilObservableCollection;
import org.observe.collect.ObservableSet.CollectionWrappingSet.UniqueElementTracking;
import org.observe.collect.ObservableSet.ImmutableObservableSet;
import org.observe.collect.ObservableSetImpl.UniqueElement;
import org.qommons.Equalizer;
import org.qommons.Equalizer.EqualizerNode;
import org.qommons.Transaction;
import org.qommons.collect.ElementSpliterator;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public class ObservableSetImpl {
	private ObservableSetImpl() {}

	public static class IntersectedSet<E, X> implements ObservableSet<E> {
		// Note: Several (E) v casts below are technically incorrect, as the values may not be of type E
		// But they are runtime-safe because of the isElement tests
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final CombinedCollectionSessionObservable theSession;
		private final boolean sameEquivalence;

		public IntersectedSet(ObservableSet<E> left, ObservableCollection<X> right) {
			theLeft = left;
			theRight = right;
			theSession = new CombinedCollectionSessionObservable(
				ObservableCollection.constant(new TypeToken<ObservableCollection<?>>() {}, theLeft, theRight));
			sameEquivalence = left.equivalence().equals(right.equivalence());
		}

		@Override
		public TypeToken<E> getType() {
			return theLeft.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction lt = theLeft.lock(write, cause);
			Transaction rt = theRight.lock(write, cause);
			return () -> {
				lt.close();
				rt.close();
			};
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theSession;
		}

		protected Set<? super E> getRightSet() {
			Equivalence<? super E> equiv = theLeft.equivalence();
			Set<? super E> rightSet = equiv.createSet();
			try (Transaction t = theRight.lock(false, null)) {
				for (X v : theRight)
					if (equiv.isElement(v))
						rightSet.add((E) v);
			}
			return rightSet;
		}

		@Override
		public int size() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return 0;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					return (int) theLeft.stream().filter(theRight::contains).count();
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					return (int) theLeft.stream().filter(rightSet::contains).count();
				}
			}
		}

		@Override
		public boolean isEmpty() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return true;
			try (Transaction t = theRight.lock(false, null)) {
				return !theLeft.containsAny(theRight);
			}
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return ElementSpliterator.empty(theLeft.getType());
			// Create the right set for filtering when the method is called
			Set<? super E> rightSet = getRightSet();
			ElementSpliterator<E> leftSplit = theLeft.spliterator();
			return new ElementSpliterator.FilteredSpliterator<>(leftSplit, el -> rightSet.contains(el));
		}

		@Override
		public boolean contains(Object o) {
			if (!theLeft.contains(o))
				return false;
			if (sameEquivalence)
				return theRight.contains(o);
			else {
				try (Transaction t = theRight.lock(false, null)) {
					Equivalence<? super E> equiv = theLeft.equivalence();
					return theRight.stream().anyMatch(v -> equiv.isElement(v) && equiv.elementEquals((E) v, o));
				}
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			if (theRight.isEmpty())
				return false;
			if (!theLeft.containsAll(c))
				return false;
			if (sameEquivalence)
				return theRight.containsAll(c);
			else
				return getRightSet().containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			if (theLeft.isEmpty() || theRight.isEmpty())
				return false;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && theRight.contains(o))
							return true;
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && rightSet.contains(o))
							return true;
				}
			}
			return false;
		}

		@Override
		public String canAdd(E value) {
			String msg = theLeft.canAdd(value);
			if (msg != null)
				return msg;
			if (value != null && !theRight.getType().getRawType().isInstance(value))
				return ObservableCollection.StdMsg.BAD_TYPE;
			msg = theRight.canAdd((X) value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean add(E e) {
			String msg;
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				boolean addedLeft = false;
				if (!theLeft.contains(e)) {
					if (!theLeft.add(e))
						return false;
					addedLeft = true;
				}
				if (!theRight.contains(e)) {
					try {
						if (!theRight.add((X) e)) {
							if (addedLeft)
								theLeft.remove(e);
							return false;
						}
					} catch (RuntimeException ex) {
						if (addedLeft)
							theLeft.remove(e);
						throw ex;
					}
				}
				return true;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			boolean addedAny = false;
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				for (E o : c) {
					boolean addedLeft = false;
					if (!theLeft.contains(o)) {
						if (!theLeft.add(o))
							continue;
						addedLeft = true;
					}
					boolean addedRight = false;
					if (!theRight.contains(o)) {
						addedRight = true;
						if (!theRight.add((X) o)) {
							if (addedLeft)
								theLeft.remove(o);
							continue;
						}
					}
					if (addedLeft || addedRight)
						addedAny = true;
				}
			}
			return addedAny;
		}

		@Override
		public String canRemove(Object value) {
			String msg = theLeft.canRemove(value);
			if (msg != null)
				return msg;
			msg = theRight.canRemove(value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean remove(Object o) {
			return theLeft.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theLeft.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theLeft.retainAll(c);
		}

		@Override
		public void clear() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return;
			Set<? super E> rightSet = getRightSet();
			theLeft.removeAll(rightSet);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			// TODO Auto-generated method stub
		}
	}

	/**
	 * A set that is a result of a filter-map operation applied to another set
	 *
	 * @param <E> The type of values in the source set
	 * @param <T> The type of values in this set
	 */
	public static class FilterMappedSet<E, T> extends FilterMappedObservableCollection<E, T> implements ObservableSet<T> {
		public FilterMappedSet(ObservableSet<E> wrap, EquivalentFilterMapDef<E, ?, T> filterMapDef, boolean dynamic) {
			super(wrap, filterMapDef, dynamic);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		protected EquivalentFilterMapDef<E, ?, T> getDef() {
			return (EquivalentFilterMapDef<E, ?, T>) super.getDef();
		}

		@Override
		public Equalizer equalizer() {
			return (t1, t2) -> {
				if (!getType().getRawType().isInstance(t1) || !getType().getRawType().isInstance(t2))
					return false;
				FilterMapResult<T, E> reversed1 = getDef().reverse(new FilterMapResult<>((T) t1));
				FilterMapResult<T, E> reversed2 = getDef().reverse(new FilterMapResult<>((T) t2));
				if (reversed1.error != null || reversed2.error != null)
					return false;
				return getWrapped().equalizer().equals(reversed1.result, reversed2.result);
			};
		}

		@Override
		public Optional<T> equivalent(Object o) {
			if (!getType().getRawType().isInstance(o))
				return Optional.empty();
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) o));
			if (reversed.error != null)
				return Optional.empty();
			Optional<E> wrappedEquiv = getWrapped().equivalent(reversed.result);
			return wrappedEquiv.flatMap(e -> {
				FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(e));
				return res.error != null ? Optional.empty() : Optional.of(res.result);
			});
		}
	}

	/**
	 * Implements {@link ObservableSet#equivalent(Object)}
	 *
	 * @param <E> The type of the set to find the value in
	 */
	public static class ObservableSetEquivalentFinder<E> implements ObservableValue<E> {
		private final ObservableSet<E> theSet;
	
		private final Object theKey;
	
		protected ObservableSetEquivalentFinder(ObservableSet<E> set, Object key) {
			theSet = set;
			theKey = key;
		}
	
		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}
	
		@Override
		public E get() {
			for(E value : theSet) {
				if(Objects.equals(value, theKey))
					return value;
			}
			return null;
		}
	
		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			boolean [] isMatch = new boolean[1];
			boolean [] initialized = new boolean[1];
			Subscription ret = theSet.onElement(new Consumer<ObservableElement<E>>() {
				private E theCurrentMatch;
	
				@Override
				public void accept(ObservableElement<E> element) {
					element.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							boolean match = Objects.equals(event.getValue(), theKey);
							E old = theCurrentMatch;
							if (match) {
								isMatch[0] = true;
								theCurrentMatch = event.getValue();
								if (!initialized[0])
									Observer.onNextAndFinish(observer, createInitialEvent(event.getValue(), event));
								else if (event.getValue() != theCurrentMatch)
									Observer.onNextAndFinish(observer, createChangeEvent(old, event.getValue(), event));
							} else if (isMatch[0] && Objects.equals(event.getOldValue(), theKey)) {
								isMatch[0] = false;
								theCurrentMatch = null;
								Observer.onNextAndFinish(observer, createChangeEvent(old, null, event));
							}
						}
	
						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							if(isMatch[0] && theCurrentMatch == event.getValue()) {
								theCurrentMatch = null;
								Observer.onNextAndFinish(observer, createChangeEvent(event.getValue(), null, event));
							}
						}
					});
				}
			});
			initialized[0] = true;
			if(!isMatch[0])
				Observer.onNextAndFinish(observer, createInitialEvent(null, null));
			return ret;
		}
	
		@Override
		public boolean isSafe() {
			return theSet.isSafe();
		}
	}

	/**
	 * Implements {@link ObservableSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	public static class RefreshingSet<E> extends RefreshingCollection<E> implements ObservableSet<E> {
		protected RefreshingSet(ObservableSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}
	
		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	
		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	public static class ElementRefreshingSet<E> extends ElementRefreshingCollection<E> implements ObservableSet<E> {
		protected ElementRefreshingSet(ObservableSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}
	
		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
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
	public static class ImmutableObservableSet<E> extends ImmutableObservableCollection<E> implements ObservableSet<E> {
		protected ImmutableObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}
	
		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	
		@Override
		public ImmutableObservableSet<E> immutable() {
			return this;
		}
	
		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ModFilteredSet<E> extends ModFilteredCollection<E> implements ObservableSet<E> {
		public ModFilteredSet(ObservableSet<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}
	
		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	
		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Backs {@link ObservableSet#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class TakenUntilObservableSet<E> extends TakenUntilObservableCollection<E> implements ObservableSet<E> {
		public TakenUntilObservableSet(ObservableSet<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}
	
		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	
		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements ObservableSet<E> {
		public FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
			super(collectionObservable);
		}
	
		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}
	
		@Override
		public Equalizer getEqualizer() {
			ObservableSet<? extends E> wrapped = getWrapped().get();
			return wrapped == null ? Objects::equals : wrapped.getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#unique(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class CollectionWrappingSet<E> implements ObservableSet<E> {
		private final ObservableCollection<E> theCollection;
		private final Equalizer theEqualizer;
	
		public CollectionWrappingSet(ObservableCollection<E> collection) {
			theCollection = collection;
			theEqualizer = equalizer;
		}
	
		protected ObservableCollection<E> getWrapped() {
			return theCollection;
		}
	
		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}
	
		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theCollection.getSession();
		}
	
		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}
	
		@Override
		public boolean isSafe() {
			return theCollection.isSafe();
		}
	
		@Override
		public int size() {
			HashSet<EqualizerNode<E>> set = new HashSet<>();
			for (E value : theCollection)
				set.add(new EqualizerNode<>(theEqualizer, value));
			return set.size();
		}
	
		@Override
		public Iterator<E> iterator() {
			return unique(theCollection.iterator());
		}
	
		@Override
		public boolean canRemove(Object value) {
			return theCollection.canRemove(value);
		}
	
		@Override
		public boolean canAdd(E value) {
			if (!theCollection.canAdd(value))
				return false;
			HashSet<EqualizerNode<E>> set = new HashSet<>();
			for (E v : theCollection)
				set.add(new EqualizerNode<>(theEqualizer, v));
			return !set.contains(new EqualizerNode<>(theEqualizer, value));
		}
	
		protected Iterator<E> unique(Iterator<E> backing) {
			return new Iterator<E>() {
				private final HashSet<EqualizerNode<E>> set = new HashSet<>();
	
				private E nextVal;
	
				@Override
				public boolean hasNext() {
					while (nextVal == null && backing.hasNext()) {
						nextVal = backing.next();
						if (!set.add(new EqualizerNode<>(theEqualizer, nextVal)))
							nextVal = null;
					}
					return nextVal != null;
				}
	
				@Override
				public E next() {
					if (nextVal == null && !hasNext())
						throw new java.util.NoSuchElementException();
					E ret = nextVal;
					nextVal = null;
					return ret;
				}
	
				@Override
				public void remove() {
					backing.remove();
				}
			};
		}
	
		protected class UniqueElementTracking {
			protected Map<EqualizerNode<E>, ObservableSetImpl.UniqueElement<E>> elements = new LinkedHashMap<>();
		}
	
		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onElement(onElement, ObservableCollection::onElement);
		}
	
		protected Subscription onElement(Consumer<? super ObservableElement<E>> onElement,
			BiFunction<ObservableCollection<E>, Consumer<? super ObservableElement<E>>, Subscription> subscriber) {
			final UniqueElementTracking tracking = createElementTracking();
			return subscriber.apply(theCollection, element -> {
				element.subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <EV extends ObservableValueEvent<E>> void onNext(EV event) {
						EqualizerNode<E> newNode = new EqualizerNode<>(theEqualizer, event.getValue());
						ObservableSetImpl.UniqueElement<E> newUnique = tracking.elements.get(newNode);
						if (newUnique == null)
							newUnique = addUniqueElement(tracking, newNode);
						boolean addElement = newUnique.isEmpty();
						boolean reAdd;
						if (event.isInitial()) {
							reAdd = newUnique.addElement(element, event);
						} else {
							EqualizerNode<E> oldNode = new EqualizerNode<>(theEqualizer, event.getOldValue());
							ObservableSetImpl.UniqueElement<E> oldUnique = tracking.elements.get(oldNode);
							if (oldUnique == newUnique) {
								reAdd = newUnique.changed(element);
							} else {
								if (oldUnique != null)
									removeFromOld(oldUnique, oldNode, event);
								reAdd = newUnique.addElement(element, event);
							}
						}
						if (addElement)
							onElement.accept(newUnique);
						else if (reAdd) {
							newUnique.reset(event);
							onElement.accept(newUnique);
						}
					}
	
					@Override
					public <EV extends ObservableValueEvent<E>> void onCompleted(EV event) {
						EqualizerNode<E> node = new EqualizerNode<>(theEqualizer, event.getValue());
						ObservableSetImpl.UniqueElement<E> unique = tracking.elements.get(node);
						if (unique != null)
							removeFromOld(unique, node, event);
					}
	
					void removeFromOld(ObservableSetImpl.UniqueElement<E> unique, EqualizerNode<E> node, Object cause) {
						boolean reAdd = unique.removeElement(element, cause);
						if (unique.isEmpty())
							tracking.elements.remove(node);
						else if (reAdd) {
							unique.reset(cause);
							onElement.accept(unique);
						}
					}
				});
			});
		}
	
		protected UniqueElementTracking createElementTracking() {
			return new UniqueElementTracking();
		}
	
		protected ObservableSetImpl.UniqueElement<E> addUniqueElement(UniqueElementTracking tracking, EqualizerNode<E> node) {
			ObservableSetImpl.UniqueElement<E> unique = new ObservableSetImpl.UniqueElement<>(this, false);
			tracking.elements.put(node, unique);
			return unique;
		}
	
		@Override
		public String toString() {
			return ObservableSet.toString(this);
		}
	}

	/**
	 * Implements elements for {@link ObservableSet#unique(ObservableCollection, Equalizer)}
	 *
	 * @param <E> The type of value in the element
	 */
	public static class UniqueElement<E> implements ObservableElement<E>{
		private final CollectionWrappingSet<E> theSet;
		private final boolean isAlwaysUsingFirst;
		private final Collection<ObservableElement<E>> theElements;
		private final SimpleSettableValue<ObservableElement<E>> theCurrentElement;
	
		UniqueElement(CollectionWrappingSet<E> set, boolean alwaysUseFirst) {
			theSet = set;
			isAlwaysUsingFirst = alwaysUseFirst;
			theElements = createElements();
			theCurrentElement = new SimpleSettableValue<>(
				new TypeToken<ObservableElement<E>>() {}.where(new TypeParameter<E>() {}, theSet.getType()), true);
		}
	
		protected Collection<ObservableElement<E>> createElements() {
			return new ArrayDeque<>();
		}
	
		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}
	
		@Override
		public E get() {
			return theElements.isEmpty() ? null : theElements.iterator().next().get();
		}
	
		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return ObservableElement.flatten(theCurrentElement).subscribe(observer);
		}
	
		@Override
		public boolean isSafe() {
			return theSet.isSafe();
		}
	
		@Override
		public ObservableValue<E> persistent() {
			return theElements.isEmpty() ? ObservableValue.constant(theSet.getType(), null) : theElements.iterator().next().persistent();
		}
	
		protected ObservableElement<E> getCurrentElement() {
			return theCurrentElement.get();
		}
	
		protected boolean addElement(ObservableElement<E> element, Object cause) {
			theElements.add(element);
			boolean newBest;
			if (isAlwaysUsingFirst)
				newBest = theElements.iterator().next() == element;
			else
				newBest = theCurrentElement.get() == null;
			if (newBest)
				return setCurrentElement(element, cause);
			else
				return false;
		}
	
		protected boolean removeElement(ObservableElement<E> element, Object cause) {
			theElements.remove(element);
			if (theCurrentElement.get() == element)
				return setCurrentElement(theElements.isEmpty() ? null : theElements.iterator().next(), cause);
			else
				return false;
		}
	
		protected boolean changed(ObservableElement<E> element) {
			return false;
		}
	
		protected void reset(Object cause) {
			theCurrentElement.set(theElements.iterator().next(), cause);
		}
	
		protected boolean setCurrentElement(ObservableElement<E> element, Object cause) {
			theCurrentElement.set(element, cause);
			return false;
		}
	
		protected boolean isEmpty() {
			return theElements.isEmpty();
		}
	}
}

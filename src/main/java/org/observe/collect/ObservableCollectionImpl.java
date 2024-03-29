package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollectionActiveManagers.ActiveCollectionManager;
import org.observe.collect.ObservableCollectionActiveManagers.CollectionElementListener;
import org.observe.collect.ObservableCollectionActiveManagers.DerivedCollectionElement;
import org.observe.collect.ObservableCollectionActiveManagers.ElementAccepter;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.ObservableUtils;
import org.observe.util.TypeTokens;
import org.observe.util.WeakListening;
import org.qommons.*;
import org.qommons.Causable.CausableKey;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ReentrantNotificationException;
import org.qommons.debug.Debug;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} */
public final class ObservableCollectionImpl {
	private ObservableCollectionImpl() {
	}

	/** Cached TypeToken of {@link String} */
	public static final TypeToken<String> STRING_TYPE = TypeToken.of(String.class);

	/**
	 * @param <E> The type for the set
	 * @param collection The collection to create the value set for (whose {@link ObservableCollection#equivalence() equivalence} will be
	 *        used)
	 * @param equiv The equivalence set to make a set of
	 * @param c The collection whose values to add to the set
	 * @param excluded A boolean flag that will be set to true if any elements in the second are excluded as not belonging to the
	 *        BetterCollection
	 * @return The set
	 */
	public static <E> Set<E> toSet(BetterCollection<E> collection, Equivalence<? super E> equiv, Collection<?> c, boolean[] excluded) {
		try (Transaction t = Transactable.lock(c, false, null)) {
			Set<E> set = equiv.createSet();
			for (Object value : c) {
				if (collection.belongs(value))
					set.add((E) value);
				else if (excluded != null)
					excluded[0] = true;
			}
			return set;
		}
	}

	/**
	 * A default version of {@link ObservableCollection#onChange(Consumer)} for collections whose changes may depend on the elements that
	 * already existed in the collection when the change subscription was made. Such collections must override
	 * {@link ObservableCollection#subscribe(Consumer, boolean)}
	 *
	 * @param coll The collection to subscribe to changes for
	 * @param observer The observer to be notified of changes (but not initial elements)
	 * @return the subscription to unsubscribe to the changes
	 */
	public static <E> Subscription defaultOnChange(ObservableCollection<E> coll,
		Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		boolean[] initialized = new boolean[1];
		CollectionSubscription sub;
		try (Transaction t = coll.lock(false, null)) {
			sub = coll.subscribe(evt -> {
				if (initialized[0])
					observer.accept(evt);
			}, true);
			initialized[0] = true;
		}
		return sub;
	}

	/**
	 * Implements {@link ObservableCollection#only()}
	 *
	 * @param <E> The type of the collection
	 */
	public static class OnlyElement<E> extends AbstractIdentifiable implements SettableElement<E> {
		/** The message rejection message returned for set operations when the collection size is not exactly 1 */
		public static final String COLL_SIZE_NOT_1 = "Collection size is not 1";

		private final ObservableCollection<E> theCollection;

		/** @param collection The collection whose first element to represent */
		public OnlyElement(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theCollection.getIdentity(), "only");
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public long getStamp() {
			return theCollection.getStamp();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theCollection.tryLock(write, cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			class OnlyEnabled extends AbstractIdentifiable implements ObservableValue<String> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(OnlyElement.this.getIdentity(), "enabled");
				}

				@Override
				public TypeToken<String> getType() {
					return TypeTokens.get().STRING;
				}

				@Override
				public long getStamp() {
					return theCollection.getStamp();
				}

				@Override
				public String get() {
					try (Transaction t = theCollection.lock(false, null)) {
						if (theCollection.size() == 1)
							return theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId()).isEnabled();
						else
							return COLL_SIZE_NOT_1;
					}
				}

				@Override
				public Observable<ObservableValueEvent<String>> noInitChanges() {
					class OnlyEnabledChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
						@Override
						protected Object createIdentity() {
							return Identifiable.wrap(OnlyEnabled.this.getIdentity(), "noInitChanges");
						}

						@Override
						public ThreadConstraint getThreadConstraint() {
							return theCollection.getThreadConstraint();
						}

						@Override
						public boolean isEventing() {
							return theCollection.isEventing();
						}

						@Override
						public boolean isSafe() {
							return theCollection.isLockSupported();
						}

						@Override
						public Transaction lock() {
							return theCollection.lock(false, null);
						}

						@Override
						public Transaction tryLock() {
							return theCollection.tryLock(false, null);
						}

						@Override
						public CoreId getCoreId() {
							return theCollection.getCoreId();
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
							try (Transaction t = lock()) {
								return theCollection.onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
									private String theOldMessage = get();

									@Override
									public void accept(ObservableCollectionEvent<? extends E> event) {
										int size = theCollection.size();
										if (size > 2)
											return;
										switch (event.getType()) {
										case add:
											fireNewValue(size, event);
											break;
										case remove:
											if (size != 2)
												fireNewValue(size, event);
											break;
										case set:
											if (size == 1)
												fireNewValue(size, event);
											break;
										}
									}

									private void fireNewValue(int size, Object cause) {
										String message;
										if (size == 1)
											message = theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId())
											.isEnabled();
										else
											message = COLL_SIZE_NOT_1;
										if (Objects.equals(theOldMessage, message))
											return;
										ObservableValueEvent<String> event = createChangeEvent(theOldMessage, message, cause);
										theOldMessage = message;
										try (Transaction evtT = event.use()) {
											observer.onNext(event);
										}
									}
								});
							}
						}
					}
					return new OnlyEnabledChanges();
				}
			}
			return new OnlyEnabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.mutableElement(theCollection.getTerminalElement(true).getElementId()).isAcceptable(value);
				else
					return COLL_SIZE_NOT_1;
			}
		}

		@Override
		public ElementId getElementId() {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.getTerminalElement(true).getElementId();
				else
					return null;
			}
		}

		@Override
		public E get() {
			try (Transaction t = theCollection.lock(false, null)) {
				if (theCollection.size() == 1)
					return theCollection.getFirst();
				else
					return null;
			}
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theCollection.lock(true, cause)) {
				if (theCollection.size() == 1) {
					CollectionElement<E> firstEl = theCollection.getTerminalElement(true);
					E oldValue = firstEl.get();
					theCollection.mutableElement(firstEl.getElementId()).set(value);
					return oldValue;
				} else
					throw new UnsupportedOperationException(COLL_SIZE_NOT_1);
			}
		}

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			class OnlyElementChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<E>> {
				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(OnlyElement.this.getIdentity(), "elementChanges");
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theCollection.getThreadConstraint();
				}

				@Override
				public boolean isEventing() {
					return theCollection.isEventing();
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}

				@Override
				public CoreId getCoreId() {
					return theCollection.getCoreId();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableElementEvent<E>> observer) {
					class OnlySubscriber implements Consumer<ObservableCollectionEvent<? extends E>> {
						private ElementId theOldElement;
						private E theOldValue;

						{
							ObservableElementEvent<E> event;
							if (theCollection.size() == 1) {
								theOldElement = theCollection.getTerminalElement(true).getElementId();
								theOldValue = theCollection.getElement(theOldElement).get();
								event = createInitialEvent(theOldElement, theOldValue, null);
							} else
								event = createInitialEvent(null, null, null);
							try (Transaction evtT = event.use()) {
								observer.onNext(event);
							}
						}

						@Override
						public void accept(ObservableCollectionEvent<? extends E> event) {
							if (theCollection.size() == 1 || theOldElement != null)
								fireNewValue(event);
						}

						private void fireNewValue(Object cause) {
							ElementId newElement;
							E newValue;
							if (theCollection.size() == 1) {
								newElement = theCollection.getTerminalElement(true).getElementId();
								newValue = theCollection.getFirst();
							} else {
								newElement = null;
								newValue = null;
							}
							ObservableElementEvent<E> event = createChangeEvent(theOldElement, theOldValue, newElement, newValue, cause);
							theOldElement = newElement;
							theOldValue = newValue;
							try (Transaction evtT = event.use()) {
								observer.onNext(event);
							}
						}
					}
					try (Transaction t = lock()) {
						return theCollection.onChange(new OnlySubscriber());
					}
				}
			}
			return new OnlyElementChanges();
		}
	}

	/**
	 * An {@link ObservableElement} whose state reflects the value of an element within a collection whose value matches some condition
	 *
	 * @param <E> The type of the element
	 */
	public static abstract class AbstractObservableElementFinder<E> extends AbstractIdentifiable implements ObservableElement<E> {
		private final ObservableCollection<E> theCollection;
		private final Comparator<CollectionElement<? extends E>> theElementCompare;
		private final Supplier<? extends E> theDefault;
		private final Observable<?> theRefresh;
		private final LongSupplier theRefreshStamp;
		private Object theChangesIdentity;

		private long theLastMatchStamp;
		private ElementId theLastMatch;

		/**
		 * @param collection The collection to find elements in
		 * @param elementCompare A comparator to determine whether to prefer one {@link #test(Object) matching} element over another. When
		 *        <code>elementCompare{@link Comparable#compareTo(Object) compareTo}(el1, el2)<0</code>, el1 will replace el2.
		 * @param def The default value to use when no element matches this finder's condition
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 * @param refreshStamp The stamp representing the refresh state
		 */
		public AbstractObservableElementFinder(ObservableCollection<E> collection,
			Comparator<CollectionElement<? extends E>> elementCompare, Supplier<? extends E> def, Observable<?> refresh,
			LongSupplier refreshStamp) {
			theCollection = collection;
			theElementCompare = elementCompare;
			theLastMatchStamp = -1;
			if (def != null)
				theDefault = def;
			else {
				theDefault = () -> null;
			}
			theRefresh = refresh;
			theRefreshStamp = refreshStamp;
		}

		/** @return The collection that this finder searches elements in */
		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		/** @return The ID of the last known element matching this search */
		protected ElementId getLastMatch() {
			return theLastMatch;
		}

		/**
		 * @param value The value of the last known element matching this search
		 * @return Whether to use the element from {@link #get()} or {@link #getElementId()}
		 */
		protected abstract boolean useCachedMatch(E value);

		/** @return The default value supplier for this finder (used when no element in the collection matches the search) */
		protected Supplier<? extends E> getDefault() {
			return theDefault;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public long getStamp() {
			long stamp = theCollection.getStamp();
			if (theRefreshStamp != null)
				stamp += theRefreshStamp.getAsLong();
			return stamp;
		}

		@Override
		public ElementId getElementId() {
			try (Transaction t = getCollection().lock(false, null)) {
				long stamp = getStamp();
				if (stamp == theLastMatchStamp
					|| (theLastMatch != null && theLastMatch.isPresent() && useCachedMatch(getCollection().getElement(theLastMatch).get())))
					return theLastMatch;
				ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
				find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
				theLastMatchStamp = stamp;
				if (element.get() != null)
					return theLastMatch = element.get().getElementId();
				else {
					theLastMatch = null;
					return null;
				}
			}
		}

		@Override
		public E get() {
			try (Transaction t = getCollection().lock(false, null)) {
				long stamp = getStamp();
				if (stamp == theLastMatchStamp
					|| (theLastMatch != null && theLastMatch.isPresent() && useCachedMatch(getCollection().getElement(theLastMatch).get())))
					return theLastMatch == null ? theDefault.get() : getCollection().getElement(theLastMatch).get();
				ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
				find(el -> element.accept(new SimpleElement(el.getElementId(), el.get())));
				theLastMatchStamp = stamp;
				if (element.get() != null) {
					theLastMatch = element.get().getElementId();
					return element.get().get();
				} else {
					theLastMatch = null;
					return theDefault.get();
				}
			}
		}

		/**
		 * Finds the value ad-hoc (stateless) from the concrete class
		 *
		 * @param onElement The consumer to give the element to when found
		 * @return Whether an element matching this finder's condition was found
		 */
		protected abstract boolean find(Consumer<? super CollectionElement<? extends E>> onElement);

		/**
		 * @param value The value to test
		 * @return Whether the value matches this finder's condition
		 */
		protected abstract boolean test(E value);

		@Override
		public Observable<ObservableElementEvent<E>> elementChanges() {
			class ElementChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<E>> {
				@Override
				public boolean isEventing() {
					return theCollection.isEventing() || theRefresh.isEventing();
				}

				@Override
				protected Object createIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(AbstractObservableElementFinder.this.getIdentity(), "elementChanges");
					return theChangesIdentity;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableElementEvent<E>> observer) {
					try (Transaction t = Lockable.lockAll(theRefresh, Lockable.lockable(theCollection, false, null))) {
						class FinderListener implements Consumer<ObservableCollectionEvent<? extends E>> {
							private SimpleElement theCurrentElement;
							private final Causable.CausableKey theCollectionCauseKey;
							private final Causable.CausableKey theRefreshCauseKey;
							private boolean isChanging;
							private boolean isRefreshNeeded;

							{
								theCollectionCauseKey = Causable.key((cause, data) -> {
									synchronized (this) {
										boolean refresh = isRefreshNeeded;
										isRefreshNeeded = false;
										isChanging = false;
										if (refresh || data.containsKey("re-search")) {
											// Means we need to find the new value in the collection
											doRefresh(cause);
										} else
											setCurrentElement((SimpleElement) data.get("replacement"), cause);
									}
								});
								theRefreshCauseKey = Causable.key((cause, data) -> {
									synchronized (this) {
										if (isChanging)
											return; // We'll do it when the collection finishes changing
										if (!isRefreshNeeded)
											return; // Refresh has already been done
										isRefreshNeeded = false;
										doRefresh(cause);
									}
								});
							}

							@Override
							public void accept(ObservableCollectionEvent<? extends E> evt) {
								Map<Object, Object> causeData = theCollectionCauseKey.getData();
								if (isRefreshNeeded || causeData.containsKey("re-search")) {
									theLastMatch = null;
									return; // We've lost track of the current best and will need to find it again later
								}
								SimpleElement current = (SimpleElement) causeData.getOrDefault("replacement", theCurrentElement);
								boolean mayReplace;
								boolean sameElement;
								boolean better;
								try {
									if (current == null) {
										sameElement = false;
										mayReplace = better = true;
									} else if (current.getElementId().equals(evt.getElementId())) {
										sameElement = true;
										mayReplace = true;
										better = evt.getType() == CollectionChangeType.set//
											? theElementCompare.compare(new SimpleElement(evt.getElementId(), evt.getNewValue()),
												current) <= 0
												: false;
									} else {
										sameElement = false;
										mayReplace = better = theElementCompare
											.compare(new SimpleElement(evt.getElementId(), evt.getNewValue()), current) < 0;
									}
								} catch (RuntimeException e) {
									if (!ListenerList.isSwallowingExceptions())
										throw e;
									e.printStackTrace();
									better = sameElement = mayReplace = false;
								}
								if (!mayReplace)
									return; // Even if the new element's value matches, it wouldn't replace the current value
								boolean matches;
								try {
									matches = test(evt.isFinal() ? evt.getOldValue() : evt.getNewValue());
								} catch (RuntimeException e) {
									e.printStackTrace();
									matches = false;
								}
								if (!matches && !sameElement)
									return;// If the new value doesn't match and it's not the current element, we don't care

								// At this point we know that we will have to do something
								// If causeData!=null, then it's unmodifiable, so we need to grab the modifiable form using the cause
								boolean refresh;
								if (!isChanging) {
									synchronized (this) {
										isChanging = true;
										refresh = isRefreshNeeded;
									}
								} else
									refresh = false;
								causeData = evt.getRootCausable().onFinish(theCollectionCauseKey);
								if (refresh) {
									theLastMatch = null;
									return;
								}
								if (!matches) {
									// The current element's value no longer matches
									// We need to search for the new value if we don't already know of a better match.
									causeData.remove("replacement");
									causeData.put("re-search", true);
									theLastMatch = null;
								} else {
									if (evt.isFinal()) {
										// The current element has been removed
										// We need to search for the new value if we don't already know of a better match.
										// The signal for this is a null replacement
										causeData.remove("replacement");
										causeData.put("re-search", true);
										theLastMatch = null;
									} else {
										// Either:
										// * There is no current element and the new element matches
										// ** --use it unless we already know of a better match
										// * The new value is better than the current element
										// If we already know of a replacement element even better-positioned than the new element,
										// ignore the new one
										if (current == null || better) {
											causeData.put("replacement", new SimpleElement(evt.getElementId(), evt.getNewValue()));
											causeData.remove("re-search");
											theLastMatchStamp = getStamp();
											theLastMatch = evt.getElementId();
										} else if (sameElement) {
											// The current best element is removed or replaced with an inferior value. Need to re-search.
											causeData.remove("replacement");
											causeData.put("re-search", true);
											theLastMatch = null;
										}
									}
								}
							}

							synchronized void refresh(Object cause) {
								if (isRefreshNeeded) {
									// We already know, but the last match may have been found manually in the mean time
									theLastMatch = null;
									return;
								} else if (isChanging) {
									// If the collection is also changing, just do the refresh after all the other changes
									theLastMatch = null;
									isRefreshNeeded = true;
								} else if (cause instanceof Causable) {
									isRefreshNeeded = true;
									theLastMatch = null;
									((Causable) cause).getRootCausable().onFinish(theRefreshCauseKey);
								} else {
									doRefresh(cause);
								}
							}

							void doRefresh(Object cause) {
								boolean found;
								try {
									found = find(//
										el -> setCurrentElement(new SimpleElement(el.getElementId(), el.get()), cause));
								} catch (RuntimeException e) {
									if (!ListenerList.isSwallowingExceptions())
										throw e;
									e.printStackTrace();
									found = false;
								}
								if (!found)
									setCurrentElement(null, cause);
							}

							void setCurrentElement(SimpleElement element, Object cause) {
								SimpleElement oldElement = theCurrentElement;
								ElementId oldId = oldElement == null ? null : oldElement.getElementId();
								ElementId newId = element == null ? null : element.getElementId();
								E oldVal;
								try {
									oldVal = oldElement == null ? theDefault.get() : oldElement.get();
								} catch (RuntimeException e) {
									e.printStackTrace();
									oldVal = null;
								}
								E newVal;
								try {
									newVal = element == null ? theDefault.get() : element.get();
								} catch (RuntimeException e) {
									e.printStackTrace();
									newVal = null;
								}
								theLastMatchStamp = getStamp();
								theLastMatch = newId;
								if (Objects.equals(oldId, newId) && oldVal == newVal)
									return;
								theCurrentElement = element;
								theLastMatch = element == null ? null : element.getElementId();
								ObservableElementEvent<E> evt = createChangeEvent(oldId, oldVal, newId, newVal, cause);
								try (Transaction evtT = evt.use()) {
									observer.onNext(evt);
								}
							}
						}
						FinderListener listener = new FinderListener();
						if (!find(el -> {
							listener.theCurrentElement = new SimpleElement(el.getElementId(), el.get());
						}))
							listener.theCurrentElement = null;
						Subscription collSub = theCollection.onChange(listener);
						@SuppressWarnings("resource")
						Subscription refreshSub = theRefresh != null ? theRefresh.act(r -> listener.refresh(r)) : Subscription.NONE;
						ElementId initId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
						E initVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
						ObservableElementEvent<E> evt = createInitialEvent(initId, initVal, null);
						try (Transaction evtT = evt.use()) {
							observer.onNext(evt);
						}
						return new Subscription() {
							private boolean isDone;

							@Override
							public void unsubscribe() {
								if (isDone)
									return;
								isDone = true;
								refreshSub.unsubscribe();
								collSub.unsubscribe();
								ElementId endId = listener.theCurrentElement == null ? null : listener.theCurrentElement.getElementId();
								E endVal = listener.theCurrentElement == null ? theDefault.get() : listener.theCurrentElement.get();
								ObservableElementEvent<E> evt2 = createChangeEvent(endId, endVal, endId, endVal, null);
								try (Transaction evtT = evt2.use()) {
									observer.onCompleted(evt2);
								}
							}
						};
					}
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theCollection.getThreadConstraint();
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}

				@Override
				public CoreId getCoreId() {
					return theCollection.getCoreId();
				}
			}
			return new ElementChanges();
		}

		private class SimpleElement implements CollectionElement<E> {
			private final ElementId theId;
			private final E theValue;

			public SimpleElement(ElementId id, E value) {
				theId = id;
				theValue = value;
			}

			@Override
			public ElementId getElementId() {
				return theId;
			}

			@Override
			public E get() {
				return theValue;
			}

			@Override
			public String toString() {
				return String.valueOf(theValue);
			}
		}
	}

	/**
	 * Implements {@link ObservableCollection#observeFind(Predicate)}
	 *
	 * @param <E> The type of the value
	 */
	public static class ObservableCollectionFinder<E> extends AbstractObservableElementFinder<E> implements SettableElement<E> {
		private final Predicate<? super E> theTest;
		private final Ternian isFirst;

		/**
		 * @param collection The collection to find elements in
		 * @param test The test to find elements that pass
		 * @param def The default value to use when no passing element is found in the collection
		 * @param first Whether to get the first value in the collection that passes, the last value, or any passing value
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 * @param refreshStamp The stamp representing the refresh state
		 */
		protected ObservableCollectionFinder(ObservableCollection<E> collection, Predicate<? super E> test, Supplier<? extends E> def,
			Ternian first, Observable<?> refresh, LongSupplier refreshStamp) {
			super(collection, (el1, el2) -> {
				if (first == Ternian.NONE)
					return 0;
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first.value)
					compare = -compare;
				return compare;
			}, def, refresh, refreshStamp);
			theTest = test;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			try {
				return theTest.test(value);
			} catch (RuntimeException e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "find", theTest, describe(isFirst));
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> element = getCollection().find(theTest, isFirst.withDefault(true));
			if (element == null)
				return false;
			else {
				onElement.accept(element);
				return true;
			}
		}

		@Override
		protected boolean test(E value) {
			return theTest.test(value);
		}

		@Override
		public boolean isLockSupported() {
			return getCollection().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getCollection().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getCollection().tryLock(write, cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			class Enabled extends AbstractIdentifiable implements ObservableValue<String> {
				@Override
				public long getStamp() {
					return getCollection().getStamp();
				}

				@Override
				public TypeToken<String> getType() {
					return TypeTokens.get().STRING;
				}

				@Override
				public String get() {
					String msg = null;
					try (Transaction t = getCollection().lock(false, null)) {
						ElementId lastMatch = getLastMatch();
						if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
							lastMatch = getElementId();
						}
						if (lastMatch != null) {
							msg = getCollection().mutableElement(lastMatch).isEnabled();
							if (msg == null)
								return null;
						}
						if (getDefault() != null)
							msg = getCollection().canAdd(getDefault().get(), //
								isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
					}
					if (msg == null)
						msg = StdMsg.UNSUPPORTED_OPERATION;
					return msg;
				}

				@Override
				public Observable<ObservableValueEvent<String>> noInitChanges() {
					class NoInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
						@Override
						protected Object createIdentity() {
							return Identifiable.wrap(Enabled.this.getIdentity(), "noInitChanges");
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
							String[] oldValue = new String[] { get() };
							return getCollection().onChange(collEvt -> {
								if (theTest.test(collEvt.getNewValue())//
									|| collEvt.getType() == CollectionChangeType.set && theTest.test(collEvt.getOldValue())) {
									String newValue = get();
									if (!Objects.equals(oldValue[0], newValue)) {
										ObservableValueEvent<String> evt = createChangeEvent(oldValue[0], newValue, collEvt);
										oldValue[0] = newValue;
										try (Transaction evtT = evt.use()) {
											observer.onNext(evt);
										}
									}
								}
							});
						}

						@Override
						public ThreadConstraint getThreadConstraint() {
							return getCollection().getThreadConstraint();
						}

						@Override
						public boolean isEventing() {
							return getCollection().isEventing();
						}

						@Override
						public boolean isSafe() {
							return getCollection().isLockSupported();
						}

						@Override
						public Transaction lock() {
							return getCollection().lock(false, null);
						}

						@Override
						public Transaction tryLock() {
							return getCollection().tryLock(false, null);
						}

						@Override
						public CoreId getCoreId() {
							return getCollection().getCoreId();
						}
					}
					return new NoInitChanges();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(ObservableCollectionFinder.this.getIdentity(), "enabled");
				}
			}
			return new Enabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			if (!theTest.test(value))
				return StdMsg.ILLEGAL_ELEMENT;
			String msg = null;
			try (Transaction t = getCollection().lock(false, null)) {
				ElementId lastMatch = getLastMatch();
				if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
					lastMatch = getElementId();
				}
				if (lastMatch != null) {
					msg = getCollection().mutableElement(lastMatch).isAcceptable(value);
					if (msg == null)
						return null;
				}
				msg = getCollection().canAdd(value, //
					isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
				if (msg == null)
					return null;
			}
			return msg;
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (!theTest.test(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			String msg = null;
			try (Transaction t = getCollection().lock(true, null)) {
				ElementId lastMatch = getLastMatch();
				if (lastMatch == null || !lastMatch.isPresent() || !theTest.test(getCollection().getElement(lastMatch).get())) {
					lastMatch = getElementId();
				}
				if (lastMatch != null) {
					msg = getCollection().mutableElement(lastMatch).isAcceptable(value);
					if (msg == null) {
						E oldValue = getCollection().getElement(lastMatch).get();
						getCollection().mutableElement(lastMatch).set(value);
						return oldValue;
					}
				}
				msg = getCollection().canAdd(value, //
					isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null);
				if (msg == null) {
					E oldValue = get();
					getCollection().addElement(value, //
						isFirst == Ternian.FALSE ? lastMatch : null, isFirst == Ternian.TRUE ? lastMatch : null, isFirst == Ternian.TRUE);
					return oldValue;
				}
			}
			if (msg == null || msg.equals(StdMsg.UNSUPPORTED_OPERATION))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * Finds an element in a collection with a value equivalent to a given value
	 *
	 * @param <E> The type of the element
	 */
	public static class ObservableEquivalentFinder<E> extends AbstractObservableElementFinder<E> {
		private final E theValue;
		private final boolean isFirst;

		/**
		 * @param collection The collection to find the element within
		 * @param value The value to find
		 * @param first Whether to search for the first, last, or any equivalent element
		 */
		public ObservableEquivalentFinder(ObservableCollection<E> collection, E value, boolean first) {
			super(collection, (el1, el2) -> {
				int compare = el1.getElementId().compareTo(el2.getElementId());
				if (!first)
					compare = -compare;
				return compare;
			}, () -> null, null, null);
			theValue = value;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return getCollection().equivalence().elementEquals(value, theValue);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "equivalent", theValue, isFirst ? "first" : "last");
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			CollectionElement<E> element = getCollection().getElement(theValue, isFirst);
			if (element == null)
				return false;
			else {
				onElement.accept(element);
				return true;
			}
		}

		@Override
		protected boolean test(E value) {
			return getCollection().equivalence().elementEquals(theValue, value);
		}
	}

	static String describe(Ternian first) {
		String str = null;
		switch (first) {
		case FALSE:
			str = "last";
			break;
		case NONE:
			str = "any";
			break;
		case TRUE:
			str = "first";
			break;
		}
		return str;
	}

	/**
	 * Searches all the elements in a collection for the best by some measure
	 *
	 * @param <E> The type of the element
	 */
	public static class BestCollectionElement<E> extends AbstractObservableElementFinder<E> {
		private final Comparator<? super E> theCompare;
		private final Ternian isFirst;

		/**
		 * @param collection The collection to search within
		 * @param compare The comparator for element values
		 * @param def The default value for an empty collection
		 * @param first Whether to use the first, last, or any element in a tie
		 * @param refresh The observable which, when fired, will cause this value to re-check its elements
		 * @param refreshStamp The stamp representing the refresh state
		 */
		public BestCollectionElement(ObservableCollection<E> collection, Comparator<? super E> compare, Supplier<? extends E> def,
			Ternian first, Observable<?> refresh, LongSupplier refreshStamp) {
			super(collection, (el1, el2) -> {
				int comp = compare.compare(el1.get(), el2.get());
				if (comp == 0 && first.value != null) {
					comp = el1.getElementId().compareTo(el2.getElementId());
					if (!first.value)
						comp = -comp;
				}
				return comp;
			}, def, refresh, refreshStamp);
			theCompare = compare;
			isFirst = first;
		}

		@Override
		protected boolean useCachedMatch(E value) {
			return false; // Can't be sure that the cached match is the best one
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getCollection().getIdentity(), "best", theCompare, describe(isFirst));
		}

		@Override
		protected boolean find(Consumer<? super CollectionElement<? extends E>> onElement) {
			boolean first = isFirst.withDefault(true);
			CollectionElement<E> best = null;
			CollectionElement<E> el = getCollection().getTerminalElement(first);
			while (el != null) {
				if (best == null || theCompare.compare(el.get(), best.get()) < 0)
					best = el;
				el = getCollection().getAdjacentElement(el.getElementId(), first);
			}
			if (best != null) {
				onElement.accept(best);
				return true;
			} else
				return false;
		}

		@Override
		protected boolean test(E value) {
			return true;
		}
	}

	/**
	 * A value that is a combination of a collection's values
	 *
	 * @param <E> The type of values in the collection
	 * @param <X> The type of the intermediate result used for calculation
	 * @param <T> The type of the produced value
	 */
	public static abstract class ReducedValue<E, X, T> extends AbstractIdentifiable implements ObservableValue<T> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<T> theDerivedType;
		private Object theChangesIdentity;

		/**
		 * @param collection The collection to reduce
		 * @param derivedType The type of the produced value
		 */
		public ReducedValue(ObservableCollection<E> collection, TypeToken<T> derivedType) {
			theCollection = collection;
			theDerivedType = derivedType;
		}

		/** @return The reduced collection */
		protected ObservableCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public TypeToken<T> getType() {
			return theDerivedType;
		}

		@Override
		public long getStamp() {
			return theCollection.getStamp();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return new Observable<ObservableValueEvent<T>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(ReducedValue.this.getIdentity(), "noInitChanges");
					return theChangesIdentity;
				}

				@Override
				public boolean isEventing() {
					return theCollection.isEventing();
				}

				@Override
				public boolean isSafe() {
					return theCollection.isLockSupported();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					ValueHolder<X> x = new ValueHolder<>();
					ValueHolder<T> value = new ValueHolder<>();
					boolean[] init = new boolean[1];
					Causable.CausableKey key = Causable.key((root, values) -> {
						T oldV = value.get();
						T v;
						try {
							v = getValue((X) values.get("x"));
						} catch (RuntimeException e) {
							v = null;
							e.printStackTrace();
						}
						value.accept(v);
						if (init[0])
							fireChangeEvent(oldV, v, root, observer::onNext);
					});
					Subscription sub;
					try (Transaction t = theCollection.lock(false, null)) {
						try {
							x.accept(init());
						} catch (RuntimeException e) {
							x.accept(null);
							e.printStackTrace();
						}
						try {
							value.accept(getValue(x.get()));
						} catch (RuntimeException e) {
							value.accept(null);
							e.printStackTrace();
						}
						sub = theCollection.onChange(evt -> {
							X newX;
							try {
								newX = update(x.get(), evt);
							} catch (RuntimeException e) {
								newX = null;
								e.printStackTrace();
							}
							x.accept(newX);

							Map<Object, Object> values = evt.getRootCausable().onFinish(key);
							values.put("x", newX);
						});
						init[0] = true;
					}
					return sub;
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return theCollection.getThreadConstraint();
				}

				@Override
				public Transaction lock() {
					return theCollection.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theCollection.tryLock(false, null);
				}

				@Override
				public CoreId getCoreId() {
					return theCollection.getCoreId();
				}
			};
		}

		@Override
		public T get() {
			return getValue(getCurrent());
		}

		/** @return The initial computation value */
		protected abstract X init();

		/** @return The computation value for the collection's current state */
		protected X getCurrent() {
			try (Transaction t = theCollection.lock(false, null)) {
				X value = init();
				CollectionElement<E> el = getCollection().getTerminalElement(true);
				int index = 0;
				while (el != null) {
					value = update(value, new ObservableCollectionEvent<>(el.getElementId(), index++, //
						CollectionChangeType.add, null, el.get()));
					el = getCollection().getAdjacentElement(el.getElementId(), true);
				}
				return value;
			}
		}

		/**
		 * Performs a reduction of a computation value with a collection element
		 *
		 * @param oldValue The value of the computation before the change
		 * @param change The collection element change to reduce into the computation
		 * @return The new value of the reduction
		 */
		protected abstract X update(X oldValue, ObservableCollectionEvent<? extends E> change);

		/**
		 * @param updated The computation value
		 * @return The value for the result
		 */
		protected abstract T getValue(X updated);
	}

	/**
	 * A structure to keeps track of the number of occurrences of a particular value in two collections
	 *
	 * @param <E> The type of the value to track
	 */
	public static class ValueCount<E> {
		E value;
		int left;
		int right;

		/** @param val The value to track */
		public ValueCount(E val) {
			value = val;
		}

		/** @return The value being tracked */
		public E getValue() {
			return value;
		}

		/** @return The number of occurrences of this value in the left collection */
		public int getLeftCount() {
			return left;
		}

		/** @return The number of occurrences of this value in the right collection */
		public int getRightCount() {
			return right;
		}

		/** @return Whether this value occurs at all in either collection */
		public boolean isEmpty() {
			return left == 0 && right == 0;
		}

		boolean modify(boolean add, boolean lft) {
			boolean modified;
			if (add) {
				if (lft) {
					modified = left == 0;
					left++;
				} else {
					modified = right == 0;
					right++;
				}
			} else {
				if (lft) {
					modified = left == 1;
					left--;
				} else {
					modified = right == 1;
					right--;
				}
			}
			return modified;
		}

		@Override
		public String toString() {
			return value + " (" + left + "/" + right + ")";
		}
	}

	/**
	 * Used by {@link IntersectionValue}
	 *
	 * @param <E> The type of values in the left collection
	 * @param <X> The type of values in the right collection
	 */
	public static abstract class ValueCounts<E, X> {
		final Equivalence<? super E> leftEquiv;
		final Map<E, ValueCount<E>> leftCounts;
		final Map<E, ValueCount<E>> rightCounts;
		final ReentrantLock theLock;
		private int leftCount;
		private int commonCount;
		private int rightCount;

		ValueCounts(Equivalence<? super E> leftEquiv) {
			this.leftEquiv = leftEquiv;
			leftCounts = leftEquiv.createMap();
			rightCounts = leftEquiv.createMap();
			theLock = new ReentrantLock();
		}

		/** @return The number of values in the left collection that do not exist in the right collection */
		public int getLeftCount() {
			return leftCount;
		}

		/** @return The number of values in the right collection that do not exist in the left collection */
		public int getRightCount() {
			return rightCount;
		}

		/** @return The number of values in the right collection that also exist in the left collection */
		public int getCommonCount() {
			return commonCount;
		}

		/**
		 * @param left The left collection
		 * @param right The right collection
		 * @param until The observable to terminate this structure's record-keeping activity
		 * @param weak Whether to listen to the collections weakly or strongly
		 * @param initAction The action to perform on this structure after the initial values of the collections are accounted for
		 * @return The subscription to use to cease record-keeping
		 */
		public Subscription init(ObservableCollection<E> left, ObservableCollection<X> right, Observable<?> until, boolean weak,
			Consumer<ValueCounts<E, X>> initAction) {
			theLock.lock();
			try (Transaction lt = left.lock(false, null); Transaction rt = right.lock(false, null)) {
				for (E e : left)
					modify(e, true, true, null);
				for (X x : right) {
					if (leftEquiv.isElement(x))
						modify((E) x, true, false, null);
				}

				Consumer<ObservableCollectionEvent<? extends E>> leftListener = evt -> onEvent(evt, true);
				Consumer<ObservableCollectionEvent<? extends X>> rightListener = evt -> onEvent(evt, false);
				Subscription sub;
				if (weak) {
					WeakListening.Builder builder = WeakListening.build();
					if (until != null)
						builder.withUntil(r -> until.act(v -> r.run()));
					WeakListening listening = builder.getListening();
					listening.withConsumer(leftListener, left::onChange);
					listening.withConsumer(rightListener, right::onChange);
					sub = builder::unsubscribe;
				} else {
					Subscription leftSub = left.onChange(leftListener);
					Subscription rightSub = right.onChange(rightListener);
					sub = Subscription.forAll(leftSub, rightSub);
				}
				initAction.accept(this);
				return sub;
			} finally {
				theLock.unlock();
			}
		}

		private void onEvent(ObservableCollectionEvent<?> evt, boolean onLeft) {
			theLock.lock();
			try {
				switch (evt.getType()) {
				case add:
					if (onLeft || leftEquiv.isElement(evt.getNewValue()))
						modify((E) evt.getNewValue(), true, onLeft, evt);
					break;
				case remove:
					if (onLeft || leftEquiv.isElement(evt.getOldValue()))
						modify((E) evt.getOldValue(), false, onLeft, evt);
					break;
				case set:
					boolean oldApplies = onLeft || leftEquiv.isElement(evt.getOldValue());
					boolean newApplies = onLeft || leftEquiv.isElement(evt.getNewValue());
					if ((oldApplies != newApplies) || (oldApplies && !leftEquiv.elementEquals((E) evt.getOldValue(), evt.getNewValue()))) {
						if (oldApplies)
							modify((E) evt.getOldValue(), false, onLeft, evt);
						if (newApplies)
							modify((E) evt.getNewValue(), true, onLeft, evt);
					} else if (oldApplies)
						update(evt.getOldValue(), evt.getNewValue(), onLeft, evt);
				}
			} finally {
				theLock.unlock();
			}
		}

		private void modify(E value, boolean add, boolean onLeft, Causable cause) {
			ValueCount<E> count = leftCounts.get(value);
			if (count == null && rightCounts != null)
				count = rightCounts.get(value);
			if (count == null) {
				if (add)
					count = new ValueCount<>(value);
				else
					throw new IllegalStateException("Value not found: " + value + " on " + (onLeft ? "left" : "right"));
			}
			boolean containmentChange = count.modify(add, onLeft);
			if (containmentChange) {
				if (onLeft) {
					if (add) {
						leftCount++;
						leftCounts.put(value, count);
						if (count.right > 0)
							commonCount++;
					} else {
						leftCount--;
						leftCounts.remove(value);
						if (count.right > 0)
							commonCount--;
					}
				} else {
					if (add) {
						rightCount++;
						rightCounts.put(value, count);
						if (count.left > 0)
							commonCount++;
					} else {
						rightCount--;
						rightCounts.remove(value);
						if (count.left > 0)
							commonCount--;
					}
				}
			}
			Object oldValue = add ? null : value;
			if (cause != null)
				changed(count, oldValue, add ? CollectionChangeType.add : CollectionChangeType.remove, onLeft, containmentChange, cause);
		}

		private void update(Object oldValue, Object newValue, boolean onLeft, Causable cause) {
			ValueCount<?> count = leftCounts.get(oldValue);
			if (count == null && rightCounts != null)
				count = rightCounts.get(oldValue);
			if (count == null) {
				if (onLeft || rightCounts != null)
					throw new IllegalStateException("Value not found: " + oldValue + " on " + (onLeft ? "left" : "right"));
				else
					return; // Not of concern
			}
			if (onLeft && oldValue != newValue)
				((ValueCount<Object>) count).value = newValue;
			if (cause != null)
				changed(count, oldValue, CollectionChangeType.set, onLeft, false, cause);
		}

		/**
		 * @param count The counts for value in the left and right collections
		 * @param oldValue The old value of the change
		 * @param type The type of the change that occurred
		 * @param onLeft Whether the change occurred in the left or in the right collection
		 * @param containmentChange Whether the change resulted in either the left or right collection's containment of the value to change
		 *        to true or false
		 * @param cause The cause of the change
		 */
		protected abstract void changed(ValueCount<?> count, Object oldValue, CollectionChangeType type, boolean onLeft,
			boolean containmentChange, Causable cause);
	}

	/**
	 * An observable value that reflects some quality of the intersection between two collections
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public abstract static class IntersectionValue<E, X> extends AbstractIdentifiable implements ObservableValue<Boolean> {
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final Predicate<ValueCounts<E, X>> theSatisfiedCheck;
		private Object theChangesIdentity;

		/**
		 * @param left The left collection
		 * @param right The right collection
		 * @param satisfied The test to determine this value after any changes
		 */
		public IntersectionValue(ObservableCollection<E> left, ObservableCollection<X> right, Predicate<ValueCounts<E, X>> satisfied) {
			theLeft = left;
			theRight = right;
			theSatisfiedCheck = satisfied;
		}

		/** @return The left collection */
		protected ObservableCollection<E> getLeft() {
			return theLeft;
		}

		/** @return The right collection */
		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		@Override
		public TypeToken<Boolean> getType() {
			return TypeToken.of(Boolean.TYPE);
		}

		@Override
		public long getStamp() {
			return theLeft.getStamp() ^ Long.rotateRight(theRight.getStamp(), 32);
		}

		@Override
		public Observable<ObservableValueEvent<Boolean>> changes() {
			return new Observable<ObservableValueEvent<Boolean>>() {
				@Override
				public Object getIdentity() {
					if (theChangesIdentity == null)
						theChangesIdentity = Identifiable.wrap(IntersectionValue.this.getIdentity(), "changes");
					return theChangesIdentity;
				}

				@Override
				public boolean isEventing() {
					return theLeft.isEventing() || theRight.isEventing();
				}

				@Override
				public boolean isSafe() {
					return theLeft.isLockSupported() && theRight.isLockSupported();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
					boolean[] initialized = new boolean[1];
					boolean[] satisfied = new boolean[1];
					ValueCounts<E, X> counts = new ValueCounts<E, X>(theLeft.equivalence()) {
						private final CausableKey theKey = Causable.key((c, data) -> {
							boolean wasSatisfied = satisfied[0];
							satisfied[0] = theSatisfiedCheck.test(this);
							if (!initialized[0] && wasSatisfied != satisfied[0])
								fireChangeEvent(wasSatisfied, satisfied[0], c, observer::onNext);
						});

						@Override
						protected void changed(ValueCount<?> count, Object oldValue, CollectionChangeType type, boolean onLeft,
							boolean containmentChange, Causable cause) {
							cause.getRootCausable().onFinish(theKey);
						}
					};
					return counts.init(theLeft, theRight, null, false, c -> {
						satisfied[0] = theSatisfiedCheck.test(counts);
						ObservableValueEvent<Boolean> evt = createInitialEvent(satisfied[0], null);
						try (Transaction t = evt.use()) {
							observer.onNext(evt);
						}
					});
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return ThreadConstrained.getThreadConstraint(theLeft, theRight);
				}

				@Override
				public Transaction lock() {
					return Lockable.lockAll(Lockable.lockable(theLeft), Lockable.lockable(theRight));
				}

				@Override
				public Transaction tryLock() {
					return Lockable.tryLockAll(Lockable.lockable(theLeft), Lockable.lockable(theRight));
				}

				@Override
				public CoreId getCoreId() {
					return Lockable.getCoreId(Lockable.lockable(theLeft), Lockable.lockable(theRight));
				}
			};
		}

		@Override
		public Observable<ObservableValueEvent<Boolean>> noInitChanges() {
			return changes().noInit();
		}
	}

	/**
	 * A value that reflects whether a collection contains a given value
	 *
	 * @param <E> The type of the collection
	 * @param <X> The type of the value to find
	 */
	public static class ContainsValue<E, X> extends IntersectionValue<E, X> {
		private final ObservableValue<X> theValue;

		/**
		 * @param collection The collection
		 * @param value The value to find
		 */
		public ContainsValue(ObservableCollection<E> collection, ObservableValue<X> value) {
			super(collection, toCollection(value), counts -> counts.getCommonCount() > 0);
			theValue = value;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "contains", theValue.getIdentity());
		}

		private static <T> ObservableCollection<T> toCollection(ObservableValue<T> value) {
			ObservableValue<ObservableCollection<T>> cv = value.map(v -> ObservableCollection.of(value.getType(), v));
			return ObservableCollection.flattenValue(cv);
		}

		@Override
		public Boolean get() {
			return getLeft().contains(theValue.get());
		}
	}

	/**
	 * A value that reflects whether one collection contains any elements of another
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public static class ContainsAllValue<E, X> extends IntersectionValue<E, X> {
		/**
		 * @param left The left collection
		 * @param right The right collection
		 */
		public ContainsAllValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, counts -> counts.getRightCount() == counts.getCommonCount());
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "containsAll", getRight().getIdentity());
		}

		@Override
		public Boolean get() {
			return getLeft().containsAll(getRight());
		}
	}

	/**
	 * A value that reflects whether one collection contains all elements of another
	 *
	 * @param <E> The type of the left collection
	 * @param <X> The type of the right collection
	 */
	public static class ContainsAnyValue<E, X> extends IntersectionValue<E, X> {
		/**
		 * @param left The left collection
		 * @param right The right collection
		 */
		public ContainsAnyValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, counts -> counts.getCommonCount() > 0);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(getLeft().getIdentity(), "containsAny", getRight().getIdentity());
		}

		@Override
		public Boolean get() {
			return getLeft().containsAny(getRight());
		}
	}

	/**
	 * An {@link ObservableCollection} whose elements are reversed from its parent
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ReversedObservableCollection<E> extends BetterList.ReversedList<E> implements ObservableCollection<E> {
		/** @param wrapped The collection whose elements to reverse */
		public ReversedObservableCollection(ObservableCollection<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected ObservableCollection<E> getWrapped() {
			return (ObservableCollection<E>) super.getWrapped();
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return getWrapped().equivalence();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return getWrapped().getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return getWrapped().isEventing();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().onChange(new ReversedSubscriber(observer, size()));
			}
		}

		@Override
		public ObservableCollection<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return ObservableCollection.super.reverse();
		}

		@Override
		public E[] toArray() {
			return ObservableCollection.super.toArray();
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			getWrapped().setValue(//
				elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return getWrapped().subscribe(new ReversedSubscriber(observer, 0), !forward);
		}

		class ReversedSubscriber implements Consumer<ObservableCollectionEvent<? extends E>> {
			private final Consumer<? super ObservableCollectionEvent<? extends E>> theObserver;
			private int theSize;

			ReversedSubscriber(Consumer<? super ObservableCollectionEvent<? extends E>> observer, int size) {
				theObserver = observer;
				theSize = size;
			}

			@Override
			public void accept(ObservableCollectionEvent<? extends E> evt) {
				if (evt.getType() == CollectionChangeType.add)
					theSize++;
				int index = theSize - evt.getIndex() - 1;
				if (evt.getType() == CollectionChangeType.remove)
					theSize--;
				ObservableCollectionEvent<E> reversed = new ObservableCollectionEvent<>(evt.getElementId().reverse(), index, evt.getType(),
					evt.getOldValue(), evt.getNewValue(), evt, evt.getMovement());
				try (Transaction t = reversed.use()) {
					theObserver.accept(reversed);
				}
			}
		}
	}

	/**
	 * An ObservableCollection derived from one or more source ObservableCollections
	 *
	 * @param <T> The type of elements in the collection
	 */
	public static interface DerivedCollection<T> extends ObservableCollection<T> {
	}

	/**
	 * A derived collection, {@link ObservableCollection.CollectionDataFlow#collect() collected} from a
	 * {@link ObservableCollection.CollectionDataFlow}. A passive collection maintains no information about its sources (either the source
	 * collection or any external sources from its flow), but relies on the state of the collection. Each listener to the collection
	 * maintains its own state which is released when the listener is {@link Subscription#unsubscribe() unsubscribed}. This stateless
	 * behavior cannot accommodate some categories of operations, but may be much more efficient for those it does support in many
	 * situations.
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of values in the collection
	 */
	public static class PassiveDerivedCollection<E, T> extends AbstractIdentifiable implements DerivedCollection<T> {
		private final ObservableCollection<E> theSource;
		private final PassiveCollectionManager<E, ?, T> theFlow;
		private final Equivalence<? super T> theEquivalence;
		private final boolean isReversed;
		volatile boolean isFiring;

		/**
		 * @param source The source collection
		 * @param flow The passive manager to produce this collection's elements
		 */
		public PassiveDerivedCollection(ObservableCollection<E> source, PassiveCollectionManager<E, ?, T> flow) {
			theSource = source;
			theFlow = flow;
			theEquivalence = theFlow.equivalence();
			isReversed = theFlow.isReversed();
		}

		/** @return The source collection */
		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		/** @return The passive manager that produces this collection's elements */
		protected PassiveCollectionManager<E, ?, T> getFlow() {
			return theFlow;
		}

		/** @return Whether this collection's element order is the reverse of its source */
		protected boolean isReversed() {
			return isReversed;
		}

		@Override
		protected Object createIdentity() {
			return theFlow.getIdentity();
		}

		@Override
		public boolean isContentControlled() {
			return theFlow.isContentControlled();
		}

		@Override
		public TypeToken<T> getType() {
			return theFlow.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theFlow.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theFlow.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theFlow.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction t = theFlow.lock(write, cause);
			if (write && isFiring)
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			return t;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			Transaction t = theFlow.tryLock(write, cause);
			if (write && t != null && isFiring)
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			return t;
		}

		@Override
		public CoreId getCoreId() {
			return theFlow.getCoreId();
		}

		@Override
		public long getStamp() {
			return theFlow.getStamp();
		}

		@Override
		public int size() {
			return theSource.size();
		}

		@Override
		public boolean isEmpty() {
			return theSource.isEmpty();
		}

		/**
		 * @param source The element ID from the source collection
		 * @return The ID of the corresponding element in this collection
		 */
		protected ElementId mapId(ElementId source) {
			if (source == null)
				return null;
			return isReversed ? source.reverse() : source;
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			FilterMapResult<T, E> reversed = theFlow.reverse(value, true, true);
			if (!reversed.isAccepted())
				return reversed.getRejectReason();
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
			}
			return theSource.canAdd(reversed.result, after, before);
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				// Lock so the reversed value is consistent until it is added
				FilterMapResult<T, E> reversed = theFlow.reverse(value, true, false);
				if (reversed.throwIfError(IllegalArgumentException::new) != null)
					return null;
				if (isReversed) {
					ElementId temp = mapId(after);
					after = mapId(before);
					before = temp;
					first = !first;
				}
				CollectionElement<E> srcEl = theSource.addElement(reversed.result, after, before, first);
				return srcEl == null ? null : elementFor(srcEl, null);
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			String msg = theFlow.canMove();
			if (msg != null) {
				if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
					return null;
				return msg;
			}
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
			}
			return theSource.canMove(mapId(valueEl), after, before);
		}

		@Override
		public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			String msg = theFlow.canMove();
			if (msg != null && (after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return getElement(valueEl);
			if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
				throw new UnsupportedOperationException(msg);
			else if (msg != null)
				throw new IllegalArgumentException(msg);
			if (isReversed) {
				ElementId temp = mapId(after);
				after = mapId(before);
				before = temp;
				first = !first;
			}
			try (Transaction t = lock(true, null)) {
				return elementFor(theSource.move(mapId(valueEl), after, before, first, afterRemove), null);
			}
		}

		@Override
		public void clear() {
			if (!theFlow.isRemoveFiltered())
				theSource.clear();
			else {
				boolean reverse = isReversed;
				try (Transaction t = lock(true, null)) {
					ElementId lastStatic = null;
					Function<? super E, ? extends T> map = theFlow.map().get();
					CollectionElement<E> el = theSource.getTerminalElement(reverse);
					while (el != null) {
						MutableCollectionElement<E> mutable = theSource.mutableElement(el.getElementId());
						if (theFlow.map(mutable, map).canRemove() == null)
							mutable.remove();
						else
							lastStatic = el.getElementId();
						el = lastStatic == null ? theSource.getTerminalElement(reverse) : theSource.getAdjacentElement(lastStatic, reverse);
					}
				}
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			try (Transaction t = lock(true, null)) {
				Function<? super E, ? extends T> map = theFlow.map().get();
				theFlow.setValue(//
					elements.stream().map(el -> theFlow.map(theSource.mutableElement(mapId(el)), map)).collect(Collectors.toList()), value);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			if (isReversed)
				return theSource.getElementsAfter(id.reverse());
			else
				return theSource.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			if (isReversed)
				return theSource.getElementsBefore(id.reverse());
			else
				return theSource.getElementsAfter(id);
		}

		@Override
		public CollectionElement<T> getElement(int index) {
			if (isReversed)
				index = getSource().size() - index - 1;
			return elementFor(theSource.getElement(index), null);
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			if (!belongs(value))
				return null;
			try (Transaction t = lock(false, null)) {
				Function<? super E, ? extends T> map = theFlow.map().get();
				boolean forward = first ^ isReversed;
				if (!theFlow.isManyToOne()) {
					// If the flow is one-to-one, we can use any search optimizations the source collection may be capable of
					FilterMapResult<T, E> reversed = theFlow.reverse(value, false, true);
					if (!reversed.isError() && equivalence().elementEquals(map.apply(reversed.result), value)) {
						CollectionElement<E> srcEl = theSource.getElement(reversed.result, forward);
						return srcEl == null ? null : elementFor(srcEl, null);
					}
				}
				CollectionElement<E> el = theSource.getTerminalElement(forward);
				while (el != null) {
					if (equivalence().elementEquals(map.apply(el.get()), value))
						return elementFor(el, map);
					el = theSource.getAdjacentElement(el.getElementId(), forward);
				}
				return null;
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor(theSource.getElement(mapId(id)), null);
		}

		@Override
		public CollectionElement<T> getTerminalElement(boolean first) {
			if (isReversed)
				first = !first;
			CollectionElement<E> t = theSource.getTerminalElement(first);
			return t == null ? null : elementFor(t, null);
		}

		@Override
		public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
			if (isReversed) {
				elementId = elementId.reverse();
				next = !next;
			}
			CollectionElement<E> adj = theSource.getAdjacentElement(elementId, next);
			return adj == null ? null : elementFor(adj, null);
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			return mutableElementFor(theSource.mutableElement(mapId(id)), null);
		}

		@Override
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection)
			throws NoSuchElementException {
			if (isReversed)
				sourceEl = sourceEl.reverse();
			if (sourceCollection == this)
				sourceCollection = theSource;
			return QommonsUtils.map2(theSource.getElementsBySource(sourceEl, sourceCollection), el -> elementFor(el, null));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (isReversed)
				localElement = localElement.reverse();
			if (sourceCollection == this)
				return QommonsUtils.map2(theSource.getSourceElements(localElement, theSource), this::mapId);
			return theSource.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (isReversed)
				equivalentEl = equivalentEl.reverse();
			ElementId found = theSource.getEquivalentElement(equivalentEl);
			if (isReversed)
				found = ElementId.reverse(found);
			return found;
		}

		/**
		 * @param el The source element
		 * @param map The mapping function for the element's values, or null to just get the current map from the flow
		 * @return The corresponding element for this collection
		 */
		protected CollectionElement<T> elementFor(CollectionElement<? extends E> el, Function<? super E, ? extends T> map) {
			Function<? super E, ? extends T> fMap = map == null ? theFlow.map().get() : map;
			return new CollectionElement<T>() {
				@Override
				public T get() {
					return fMap.apply(el.get());
				}

				@Override
				public ElementId getElementId() {
					return mapId(el.getElementId());
				}

				@Override
				public int hashCode() {
					return getElementId().hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof CollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
				}

				@Override
				public String toString() {
					StringBuilder str = new StringBuilder("[");
					if (getElementId().isPresent())
						str.append(getElementsBefore(getElementId()));
					else
						str.append("removed");
					return str.append("]: ").append(get()).toString();
				}
			};
		}

		/**
		 * @param el The source mutable element
		 * @param map The mapping function for the element's values, or null to just get the current map from the flow
		 * @return The corresponding mutable element for this collection
		 */
		protected MutableCollectionElement<T> mutableElementFor(MutableCollectionElement<E> el, Function<? super E, ? extends T> map) {
			Function<? super E, ? extends T> fMap = map == null ? theFlow.map().get() : map;
			MutableCollectionElement<T> flowEl = theFlow.map(el, fMap);
			class PassiveMutableElement implements MutableCollectionElement<T> {
				@Override
				public BetterCollection<T> getCollection() {
					return PassiveDerivedCollection.this;
				}

				@Override
				public ElementId getElementId() {
					return mapId(el.getElementId());
				}

				@Override
				public T get() {
					return flowEl.get();
				}

				@Override
				public String isEnabled() {
					return flowEl.isEnabled();
				}

				@Override
				public String isAcceptable(T value) {
					return flowEl.isAcceptable(value);
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					try (Transaction t = lock(true, null)) {
						flowEl.set(value);
					}
				}

				@Override
				public String canRemove() {
					return flowEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					try (Transaction t = lock(true, null)) {
						flowEl.remove();
					}
				}

				@Override
				public int hashCode() {
					return getElementId().hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof MutableCollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
				}

				@Override
				public String toString() {
					return flowEl.toString();
				}
			}
			return new PassiveMutableElement();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			Subscription sourceSub, mapSub;
			try (Transaction outerFlowLock = theFlow.lock(false, null)) {
				Function<? super E, ? extends T>[] currentMap = new Function[1];
				mapSub = theFlow.map().changes().act(evt -> {
					if (evt.isInitial()) {
						currentMap[0] = evt.getNewValue();
						return;
					}
					try (Transaction sourceLock = theSource.lock(false, evt)) {
						if (isFiring)
							throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
						isFiring = true;
						try {
							currentMap[0] = evt.getNewValue();
							CollectionElement<E> el = theSource.getTerminalElement(!isReversed);
							int index = 0;
							while (el != null) {
								E sourceVal = el.get();
								T oldVal;
								try {
									oldVal = evt.getOldValue().apply(sourceVal);
								} catch (RuntimeException e) {
									oldVal = null;
									e.printStackTrace();
								}
								T newVal;
								try {
									newVal = currentMap[0].apply(sourceVal);
								} catch (RuntimeException e) {
									newVal = null;
									e.printStackTrace();
								}
								ObservableCollectionEvent<? extends T> evt2 = new ObservableCollectionEvent<>(mapId(el.getElementId()),
									index++, CollectionChangeType.set, oldVal, newVal, evt);
								try (Transaction evtT = evt2.use()) {
									observer.accept(evt2);
								}
								el = theSource.getAdjacentElement(el.getElementId(), !isReversed);
							}
						} finally {
							isFiring = false;
						}
					}
				});
				try (Transaction sourceT = theSource.lock(false, null)) {
					sourceSub = getSource().onChange(new Consumer<ObservableCollectionEvent<? extends E>>() {
						private int theSize = isReversed ? size() : -1;

						@Override
						public void accept(ObservableCollectionEvent<? extends E> evt) {
							try (Transaction t = theFlow.lock(false, evt)) {
								if (isFiring)
									throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
								isFiring = true;
								try {
									T oldValue, newValue;
									switch (evt.getType()) {
									case add:
										try {
											newValue = currentMap[0].apply(evt.getNewValue());
										} catch (RuntimeException e) {
											newValue = null;
											e.printStackTrace();
										}
										oldValue = null;
										break;
									case remove:
										try {
											oldValue = currentMap[0].apply(evt.getOldValue());
										} catch (RuntimeException e) {
											oldValue = null;
											e.printStackTrace();
										}
										newValue = oldValue;
										break;
									case set:
										BiTuple<T, T> values;
										try {
											values = theFlow.map(evt.getOldValue(), evt.getNewValue(), currentMap[0]);
										} catch (RuntimeException e) {
											e.printStackTrace();
											values = null;
										}
										if (values == null)
											return;
										oldValue = values.getValue1();
										newValue = values.getValue2();
										break;
									default:
										throw new IllegalStateException("Unrecognized collection change type: " + evt.getType());
									}
									int index;
									if (!isReversed)
										index = evt.getIndex();
									else {
										if (evt.getType() == CollectionChangeType.add)
											theSize++;
										index = theSize - evt.getIndex() - 1;
										if (evt.getType() == CollectionChangeType.remove)
											theSize--;
									}
									ObservableCollectionEvent<? extends T> evt2 = new ObservableCollectionEvent<>(mapId(evt.getElementId()),
										index, evt.getType(), oldValue, newValue, evt, evt.getMovement());
									try (Transaction evtT = evt2.use()) {
										observer.accept(evt2);
									}
								} finally {
									isFiring = false;
								}
							}
						}
					});
				}
			}
			return Subscription.forAll(sourceSub, mapSub);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	/**
	 * A derived collection, {@link ObservableCollection.CollectionDataFlow#collect() collected} from a
	 * {@link ObservableCollection.CollectionDataFlow}. An active collection maintains a sorted collection of derived elements, enabling
	 * complex and order-affecting operations like {@link ObservableCollection.CollectionDataFlow#sorted(Comparator) sort} and
	 * {@link ObservableCollection.CollectionDataFlow#distinct() distinct}.
	 *
	 * @param <T> The type of values in the collection
	 */
	public static class ActiveDerivedCollection<T> extends AbstractIdentifiable implements DerivedCollection<T> {
		/**
		 * Stores strong references to actively-derived collections on which listeners are installed, preventing the garbage-collection of
		 * these collections since the listeners only contain weak references to their data sources.
		 */
		private static final Set<IdentityKey<ActiveDerivedCollection<?>>> STRONG_REFS = new ConcurrentHashSet<>();

		/**
		 * Holds a {@link ObservableCollectionActiveManagers.DerivedCollectionElement}s for an {@link ActiveDerivedCollection}
		 *
		 * @param <T> The type of the collection
		 */
		protected static class DerivedElementHolder<T> implements ElementId {
			/** The element from the flow */
			protected final ObservableCollectionActiveManagers.DerivedCollectionElement<T> element;
			BinaryTreeNode<DerivedElementHolder<T>> treeNode;
			DerivedElementHolder<T> successor;

			/** @param element The element from the flow */
			protected DerivedElementHolder(ObservableCollectionActiveManagers.DerivedCollectionElement<T> element) {
				this.element = element;
			}

			@Override
			public boolean isPresent() {
				return treeNode.getElementId().isPresent();
			}

			@Override
			public int compareTo(ElementId o) {
				return treeNode.getElementId().compareTo(((DerivedElementHolder<T>) o).treeNode.getElementId());
			}

			DerivedElementHolder<T> check() {
				if (treeNode == null)
					throw new IllegalStateException("This node is not currently present in the collection");
				return this;
			}

			/** @return The current value of this element */
			public T get() {
				return element.get();
			}

			@Override
			public int hashCode() {
				return treeNode.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (!(obj instanceof DerivedElementHolder))
					return false;
				return treeNode.equals(((DerivedElementHolder<T>) obj).treeNode);
			}

			@Override
			public String toString() {
				return new StringBuilder().append('[').append(treeNode.getNodesBefore()).append("]: ").append(element.get()).toString();
			}
		}

		private final ActiveCollectionManager<?, ?, T> theFlow;
		private final BetterTreeSet<DerivedElementHolder<T>> theDerivedElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends T>>> theListeners;
		private final AtomicInteger theListenerCount;
		private final Equivalence<? super T> theEquivalence;
		private final AtomicLong theStamp;
		private final WeakListening.Builder theWeakListening;

		/**
		 * @param flow The active data manager to power this collection
		 * @param until The observable to cease maintenance
		 */
		public ActiveDerivedCollection(ActiveCollectionManager<?, ?, T> flow, Observable<?> until) {
			theFlow = flow;
			theDerivedElements = BetterTreeSet.<DerivedElementHolder<T>> buildTreeSet((e1, e2) -> e1.element.compareTo(e2.element)).build();
			theListeners = ListenerList.build().reentrancyError(ObservableCollection.REENTRANT_EVENT_ERROR).build();
			theListenerCount = new AtomicInteger();
			theEquivalence = flow.equivalence();
			theStamp = new AtomicLong();

			// Begin listening
			ElementAccepter<T> onElement = (el, causes) -> {
				theStamp.incrementAndGet();
				DerivedElementHolder<T>[] holder = new DerivedElementHolder[] { createHolder(el) };
				holder[0].treeNode = theDerivedElements.addElement(holder[0], false);
				if (holder[0].treeNode == null)
					throw new IllegalStateException("Element already exists: " + holder[0]);
				CollectionElementMove initMove = null;
				for (Object cause : causes) {
					if (cause instanceof ObservableCollectionEvent && ((ObservableCollectionEvent<?>) cause).getMovement() != null)
						initMove = ((ObservableCollectionEvent<?>) cause).getMovement();
				}
				if (initMove != null)
					causes = ArrayUtils.add(causes, initMove);
				fireListeners(new ObservableCollectionEvent<>(holder[0], holder[0].treeNode.getNodesBefore(), CollectionChangeType.add,
					null, el.get(), causes));
				el.setListener(new CollectionElementListener<T>() {
					@Override
					public void update(T oldValue, T newValue, boolean internalOnly, Object... elCauses) {
						if (internalOnly)
							return;
						while (holder[0].successor != null)
							holder[0] = holder[0].successor;
						theStamp.incrementAndGet();
						BinaryTreeNode<DerivedElementHolder<T>> left = holder[0].treeNode.getClosest(true);
						BinaryTreeNode<DerivedElementHolder<T>> right = holder[0].treeNode.getClosest(false);
						if ((left != null && left.get().element.compareTo(holder[0].element) > 0)
							|| (right != null && right.get().element.compareTo(holder[0].element) < 0)) {
							// Element is out-of-order. This may indicate that only this element has changed,
							// or it could indicate that the ordering scheme of the elements has changed.
							// We need to do a repair operation to be safe.
							theDerivedElements.repair(holder[0].treeNode.getElementId(),
								new BetterTreeSet.RepairListener<DerivedElementHolder<T>, CollectionElementMove>() {
								@Override
								public CollectionElementMove removed(CollectionElement<DerivedElementHolder<T>> element) {
									int index = theDerivedElements.getElementsBefore(element.getElementId());
									T value = element.get() == holder[0] ? oldValue : element.get().get();
									// We know this is a move because elements are always distinct
									CollectionElementMove move = new CollectionElementMove();
									fireListeners(new ObservableCollectionEvent<>(element.get(), index, CollectionChangeType.remove,
										value, value, ArrayUtils.add(elCauses, 0, move)));
									return move;
								}

								@Override
								public void disposed(DerivedElementHolder<T> value, CollectionElementMove data) {
									throw new IllegalStateException("This should never happen in a repair");
								}

								@Override
								public void transferred(CollectionElement<DerivedElementHolder<T>> element,
									CollectionElementMove data) {
									// Don't re-use elements, as this violates the BetterCollection API wrt element removal
									// See ElementId#isPresent()
									DerivedElementHolder<T> newHolder = createHolder(element.get().element);
									newHolder.treeNode = (BinaryTreeNode<DerivedElementHolder<T>>) element;
									// Instruct the element's listener how to find the new holder
									element.get().successor = newHolder;
									theDerivedElements.mutableElement(element.getElementId()).set(newHolder);
									int index = theDerivedElements.getElementsBefore(element.getElementId());
									T value = element.get().get();
									data.moved();
									fireListeners(new ObservableCollectionEvent<>(newHolder, index, CollectionChangeType.add, null,
										value, ArrayUtils.add(elCauses, 0, data)));
								}
							});
							if (holder[0].successor != null) {
								while (holder[0].successor != null)
									holder[0] = holder[0].successor;
							} else // Since we weren't actually moved in the repair, we need to fire the listener
								fireListeners(new ObservableCollectionEvent<>(holder[0], holder[0].treeNode.getNodesBefore(),
									CollectionChangeType.set, oldValue, newValue, elCauses));
						} else {
							fireListeners(new ObservableCollectionEvent<>(holder[0], holder[0].treeNode.getNodesBefore(),
								CollectionChangeType.set, oldValue, newValue, elCauses));
						}
					}

					@Override
					public void removed(T value, Object... elCauses) {
						while (holder[0].successor != null)
							holder[0] = holder[0].successor;
						theStamp.incrementAndGet();
						int index = holder[0].treeNode.getNodesBefore();
						if (holder[0].treeNode.getElementId().isPresent()) // May have been removed already
							theDerivedElements.mutableElement(holder[0].treeNode.getElementId()).remove();
						CollectionElementMove terminalMove = null;
						for (Object elCause : elCauses) {
							if (elCause instanceof ObservableCollectionEvent
								&& ((ObservableCollectionEvent<?>) elCause).getMovement() != null)
								terminalMove = ((ObservableCollectionEvent<?>) elCause).getMovement();
						}
						if (terminalMove != null)
							elCauses = ArrayUtils.add(elCauses, terminalMove);
						fireListeners(
							new ObservableCollectionEvent<>(holder[0], index, CollectionChangeType.remove, value, value, elCauses));
					}
				});
			};
			// Must maintain a strong reference to the event listening so it is not GC'd while the collection is still alive
			theWeakListening = WeakListening.build().withUntil(r -> until.act(v -> r.run()));
			theFlow.begin(true, onElement, theWeakListening.getListening());
		}

		/**
		 * @param el The flow element
		 * @return The holder for the element
		 */
		protected DerivedElementHolder<T> createHolder(ObservableCollectionActiveManagers.DerivedCollectionElement<T> el) {
			return new DerivedElementHolder<>(el);
		}

		/** @return This collection's data manager */
		protected ActiveCollectionManager<?, ?, T> getFlow() {
			return theFlow;
		}

		/** @return This collection's element holders */
		protected BetterTreeSet<DerivedElementHolder<T>> getPresentElements() {
			return theDerivedElements;
		}

		void fireListeners(ObservableCollectionEvent<T> event) {
			try (Transaction t = event.use()) {
				theListeners.forEach(//
					listener -> listener.accept(event));
			}
		}

		@Override
		protected Object createIdentity() {
			return theFlow.getIdentity();
		}

		@Override
		public boolean isContentControlled() {
			return theFlow.isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return ((DerivedElementHolder<T>) id).treeNode.getNodesBefore();
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return ((DerivedElementHolder<T>) id).treeNode.getNodesAfter();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			return onChange(observer, true);
		}

		/**
		 * Allows adding a listener to this collection without creating a persistent strong reference to keep it alive
		 *
		 * @param observer The listener for changes to this collection
		 * @param withStrongRef Whether to install a strong reference to this collection to keep it alive while the listener is active, in
		 *        case no strong reference to the actual collection (or the subscription returned from this method) is kept
		 * @return A subscription to uninstall the listener
		 */
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer, boolean withStrongRef) {
			Runnable remove = theListeners.add(observer, true);
			// Add a strong reference to this collection while we have listeners.
			// Otherwise, this collection could be GC'd and listeners (which may not reference this collection) would just be left hanging
			if (withStrongRef && theListenerCount.getAndIncrement() == 0)
				STRONG_REFS.add(new IdentityKey<>(this));
			return () -> {
				remove.run();
				if (withStrongRef && theListenerCount.decrementAndGet() == 0)
					STRONG_REFS.remove(new IdentityKey<>(this));
			};
		}

		@Override
		public TypeToken<T> getType() {
			return theFlow.getTargetType();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theFlow.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theListeners.isFiring()//
				|| theFlow.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theFlow.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write && theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			return theFlow.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (write && theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			return theFlow.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return theFlow.getCoreId();
		}

		@Override
		public long getStamp() {
			return theStamp.get();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public int size() {
			return theDerivedElements.size();
		}

		@Override
		public boolean isEmpty() {
			return theDerivedElements.isEmpty();
		}

		@Override
		public T get(int index) {
			try (Transaction t = lock(false, null)) {
				return theDerivedElements.get(index).get();
			}
		}

		@Override
		public CollectionElement<T> getElement(int index) {
			return elementFor(theDerivedElements.get(index));
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			try (Transaction t = lock(false, null)) {
				Comparable<ObservableCollectionActiveManagers.DerivedCollectionElement<T>> finder = getFlow().getElementFinder(value);
				if (finder != null) {
					BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(holder -> finder.compareTo(holder.element), //
						BetterSortedList.SortedSearchFilter.of(first, false));
					if (found == null || !equivalence().elementEquals(found.get().element.get(), value))
						return null;
					while (found.getClosest(first) != null
						&& equivalence().elementEquals(found.getClosest(first).get().element.get(), value))
						found = found.getClosest(first);
					return elementFor(found.get());
				}
				for (DerivedElementHolder<T> el : (first ? theDerivedElements : theDerivedElements.reverse()))
					if (equivalence().elementEquals(el.get(), value))
						return elementFor(el);
				return null;
			}
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			return elementFor((DerivedElementHolder<T>) id);
		}

		@Override
		public CollectionElement<T> getTerminalElement(boolean first) {
			DerivedElementHolder<T> holder = first ? theDerivedElements.peekFirst() : theDerivedElements.peekLast();
			return holder == null ? null : getElement(holder);
		}

		@Override
		public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
			DerivedElementHolder<T> holder = (DerivedElementHolder<T>) elementId;
			BinaryTreeNode<DerivedElementHolder<T>> adjacentNode = holder.treeNode.getClosest(!next);
			return adjacentNode == null ? null : getElement(adjacentNode.get());
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			if (id == null)
				throw new NullPointerException();
			DerivedElementHolder<T> el = (DerivedElementHolder<T>) id;
			class DerivedMutableCollectionElement implements MutableCollectionElement<T> {
				@Override
				public BetterCollection<T> getCollection() {
					return ActiveDerivedCollection.this;
				}

				@Override
				public ElementId getElementId() {
					return el;
				}

				@Override
				public T get() {
					return el.element.get();
				}

				@Override
				public String isEnabled() {
					return el.element.isEnabled();
				}

				@Override
				public String isAcceptable(T value) {
					return el.element.isAcceptable(value);
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					if (theListeners.isFiring())
						throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
					el.element.set(value);
				}

				@Override
				public String canRemove() {
					return el.element.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					if (theListeners.isFiring())
						throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
					el.element.remove();
				}

				@Override
				public String toString() {
					return el.element.toString();
				}
			}
			return new DerivedMutableCollectionElement();
		}

		@Override
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection)
			throws NoSuchElementException {
			try (Transaction t = lock(false, null)) {
				if (sourceCollection == this)
					return BetterList.of(getElement(sourceEl));

				return BetterList.of(theFlow.getElementsBySource(sourceEl, sourceCollection).stream().map(el -> {
					DerivedElementHolder<T> found = theDerivedElements.searchValue(de -> el.compareTo(de.element),
						BetterSortedList.SortedSearchFilter.OnlyMatch);
					if (found == null) {
						// This may happen if a listener for the source collection calls this method
						// before the element has been added to this collection
						return null;
					}
					return elementFor(found);
				}).filter(el -> el != null));
			}
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(localElement).getElementId()); // Verify that it's actually our element
			try (Transaction t = lock(false, null)) {
				return theFlow.getSourceElements(((DerivedElementHolder<T>) localElement).element, sourceCollection);
			}
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof DerivedElementHolder))
				return null;
			DerivedElementHolder<?> holder = (DerivedElementHolder<?>) equivalentEl;
			ElementId local = theDerivedElements.getEquivalentElement(holder.treeNode.getElementId());
			if (local == null)
				return equivalentEl;
			DerivedCollectionElement<T> found = theFlow.getEquivalentElement(holder.element);
			return found == null ? null : idFromSynthetic(found);
		}

		/**
		 * @param el The element holder
		 * @return A collection element for the given element in this collection
		 */
		protected CollectionElement<T> elementFor(DerivedElementHolder<T> el) {
			el.check();
			return new ActiveDerivedElement<>(el);
		}

		static class ActiveDerivedElement<T> implements CollectionElement<T> {
			private final DerivedElementHolder<T> element;

			ActiveDerivedElement(DerivedElementHolder<T> element) {
				this.element = element;
			}

			@Override
			public T get() {
				return element.get();
			}

			@Override
			public ElementId getElementId() {
				return element;
			}

			@Override
			public int hashCode() {
				return element.treeNode.hashCode();
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof ActiveDerivedElement && element.treeNode.equals(((ActiveDerivedElement<?>) o).element.treeNode);
			}

			@Override
			public String toString() {
				return element.element.toString();
			}
		};

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			return theFlow.canAdd(value, //
				strip(after), strip(before));
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> derived = theFlow.addElement(value, //
				strip(after), strip(before), first);
			return derived == null ? null : elementFor(idFromSynthetic(derived));
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theFlow.canMove(//
				strip(valueEl), strip(after), strip(before));
		}

		@Override
		public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			ObservableCollectionActiveManagers.DerivedCollectionElement<T> derived = theFlow.move(//
				strip(valueEl), strip(after), strip(before), first, afterRemove);
			return elementFor(idFromSynthetic(derived));
		}

		private DerivedElementHolder<T> idFromSynthetic(ObservableCollectionActiveManagers.DerivedCollectionElement<T> added) {
			BinaryTreeNode<DerivedElementHolder<T>> found = theDerivedElements.search(//
				holder -> added.compareTo(holder.element), BetterSortedList.SortedSearchFilter.OnlyMatch);
			return found.get();
		}

		private ObservableCollectionActiveManagers.DerivedCollectionElement<T> strip(ElementId id) {
			return id == null ? null : ((DerivedElementHolder<T>) id).element;
		}

		@Override
		public void clear() {
			if (isEmpty())
				return;
			if (theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			Causable cause = Causable.simpleCause();
			try (Transaction cst = cause.use(); Transaction t = lock(true, cause)) {
				if (!theFlow.clear()) {
					new ArrayList<>(theDerivedElements).forEach(el -> {
						if (el.element.canRemove() == null)
							el.element.remove();
					});
				}
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			if (theListeners.isFiring())
				throw new ReentrantNotificationException(REENTRANT_EVENT_ERROR);
			theFlow.setValues(//
				elements.stream().map(el -> ((DerivedElementHolder<T>) el).element).collect(Collectors.toList()), value);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		@Override
		protected void finalize() throws Throwable {
			// TODO Move this functionality to java.lang.ref.Cleanable, BUT ONLY when JDK 8 is no longer supported
			super.finalize();
			theWeakListening.unsubscribe();
		}
	}

	/**
	 * An {@link ObservableCollection} whose contents are fixed
	 *
	 * @param <E> The type of values in the collection
	 */
	public static class ConstantCollection<E> extends BetterList.ConstantList<E> implements ObservableCollection<E> {
		private final TypeToken<E> theType;

		ConstantCollection(TypeToken<E> type, BetterList<? extends E> values) {
			super(values);
			theType = type;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return () -> {
			};
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> extends AbstractIdentifiable implements ObservableCollection<E> {
		private final ObservableValue<? extends ObservableCollection<? extends E>> theCollectionObservable;
		private final TypeToken<E> theType;
		private final Equivalence<? super E> theEquivalence;

		/**
		 * @param collectionObservable The value to present as a static collection
		 * @param equivalence The equivalence for the collection
		 */
		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<? extends E>> collectionObservable,
			Equivalence<? super E> equivalence) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			theEquivalence = equivalence;
		}

		/** @return The value that backs this collection */
		protected ObservableValue<? extends ObservableCollection<? extends E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theCollectionObservable.getIdentity(), "flatten");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (theCollectionObservable.getThreadConstraint() == ThreadConstraint.NONE)
				return ThreadConstrained.getThreadConstraint(theCollectionObservable.get());
			else
				return ThreadConstraint.ANY; // Can't know
		}

		@Override
		public boolean isEventing() {
			if (theCollectionObservable.isEventing())
				return true;
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll != null && coll.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theCollectionObservable.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write)
				return Transactable.writeLockWithOwner(theCollectionObservable, theCollectionObservable::get, cause);
			else
				return Lockable.lockAll(theCollectionObservable, () -> Arrays.asList(Lockable.lockable(theCollectionObservable.get())),
					LambdaUtils.identity());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (write)
				return Transactable.tryWriteLockWithOwner(theCollectionObservable, theCollectionObservable::get, cause);
			else
				return Lockable.tryLockAll(theCollectionObservable, () -> Arrays.asList(Lockable.lockable(theCollectionObservable.get())),
					LambdaUtils.identity());
		}

		@Override
		public CoreId getCoreId() {
			return Lockable.getCoreId(theCollectionObservable, () -> Lockable.lockable(theCollectionObservable.get(), false, null));
		}

		@Override
		public long getStamp() {
			long stamp = theCollectionObservable.getStamp();
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll != null) {
				long collStamp = coll.getStamp();
				stamp = Stamped.compositeStamp(stamp, collStamp);
			}
			return stamp;
		}

		@Override
		public boolean isContentControlled() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null || coll.isContentControlled();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}

		@Override
		public int getElementsBefore(ElementId id) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll.getElementsBefore(strip(coll, id));
		}

		@Override
		public int getElementsAfter(ElementId id) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll.getElementsAfter(strip(coll, id));
		}

		@Override
		public int size() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public boolean isEmpty() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? true : coll.isEmpty();
		}

		@Override
		public E get(int index) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return coll.get(index);
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return getElement(current, ((ObservableCollection<E>) current).getElement(index));
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return getElement(current, ((ObservableCollection<E>) current).getElement(value, first));
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.getElement(strip(current, id)));
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			return getElement(current, current.getTerminalElement(first));
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.getAdjacentElement(strip(current, elementId), next));
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			ObservableCollection<? extends E> current = getWrapped().get();
			return getElement(current, current.mutableElement(strip(current, id)));
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return BetterList.empty();
			return BetterList.of(current.getElementsBySource(sourceEl, sourceCollection).stream().map(el -> getElement(current, el)));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return BetterList.empty();
			if (sourceCollection == this)
				return BetterList.of(current.getSourceElements(strip(current, localElement), current).stream()
					.map(el -> new FlattenedElementId(current, el)));
			else
				return BetterList.of(current.getSourceElements(strip(current, localElement), sourceCollection).stream()
					.map(el -> new FlattenedElementId(current, el)));
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			ObservableCollection<? extends E> current = getWrapped().get();
			if (current == null)
				return null;
			if (equivalentEl instanceof FlattenedValueCollection.FlattenedElementId) {
				if (((FlattenedElementId) equivalentEl).theCollection == current)
					return new FlattenedElementId(current, ((FlattenedElementId) equivalentEl).theSourceEl);
				else
					equivalentEl = ((FlattenedElementId) equivalentEl).theSourceEl;
			}
			ElementId found = current.getEquivalentElement(equivalentEl);
			return found == null ? null : new FlattenedElementId(current, found);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			ObservableCollection<? extends E> current = theCollectionObservable.get();
			if (current == null)
				return MutableCollectionElement.StdMsg.UNSUPPORTED_OPERATION;
			else if (value != null && !TypeTokens.get().isInstance(current.getType(), value))
				return MutableCollectionElement.StdMsg.BAD_TYPE;
			return ((ObservableCollection<E>) current).canAdd(//
				value, strip(current, after), strip(current, before));
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				if (value != null && !TypeTokens.get().isInstance(coll.getType(), value))
					throw new IllegalArgumentException(MutableCollectionElement.StdMsg.BAD_TYPE);
				return getElement(coll, ((ObservableCollection<E>) coll).addElement(//
					value, strip(coll, after), strip(coll, before), first));
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return coll.canMove(//
				strip(coll, valueEl), strip(coll, after), strip(coll, before));
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null)
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				return getElement(coll, coll.move(//
					strip(coll, valueEl), strip(coll, after), strip(coll, before), first, afterRemove));
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll != null)
					coll.clear();
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			try (Transaction t = lock(true, null)) {
				ObservableCollection<? extends E> coll = theCollectionObservable.get();
				if (coll == null) {
					if (!elements.isEmpty())
						throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
					return;
				}
				List<ElementId> sourceEls = elements.stream().map(el -> strip(coll, el)).collect(Collectors.toList());
				((ObservableCollection<E>) coll).setValue(sourceEls, value);
			}
		}

		@Override
		public Observable<? extends CollectionChangeEvent<E>> changes() {
			class Changes extends AbstractIdentifiable implements Observable<CollectionChangeEvent<E>> {
				@Override
				public Subscription subscribe(Observer<? super CollectionChangeEvent<E>> observer) {
					class ChangeObserver implements Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>> {
						private ObservableCollection<? extends E> collection;
						private Subscription collectionSub;
						private final boolean[] flushOnUnsubscribe = new boolean[] { true };

						@Override
						public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V collEvt) {
							// If the collections have the same identity, then the content can't have changed
							boolean clearAndAdd;
							if (collEvt.isInitial())
								clearAndAdd = false;
							else if (collection == null || collEvt.getNewValue() == null)
								clearAndAdd = true;
							else
								clearAndAdd = !collection.getIdentity().equals(collEvt.getNewValue().getIdentity());
							if (collection != null) {
								if (clearAndAdd) {
									try (Transaction t = collection.lock(false, null)) {
										collectionSub.unsubscribe();
										collectionSub = null;
										if (clearAndAdd) {
											List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(collection.size());
											int index = 0;
											for (E v : collection)
												elements.add(new CollectionChangeEvent.ElementChange<>(v, v, index++, null));
											CollectionChangeEvent<E> clearEvt = new CollectionChangeEvent<>(CollectionChangeType.remove, //
												elements, collEvt);
											debug(s -> s.append("clear: ").append(clearEvt));
											try (Transaction evtT = clearEvt.use()) {
												observer.onNext(clearEvt);
											}
										}
									}
								} else {
									collectionSub.unsubscribe();
									collectionSub = null;
								}
								collection = null;
							}
							collection = collEvt.getNewValue();
							if (collection != null) {
								CollectionChangesObservable<? extends E> changes;
								if (clearAndAdd) {
									try (Transaction t = collection.lock(false, null)) {
										List<CollectionChangeEvent.ElementChange<E>> elements = new ArrayList<>(collection.size());
										int index = 0;
										for (E v : collection)
											elements.add(new CollectionChangeEvent.ElementChange<>(v, null, index++, null));
										CollectionChangeEvent<E> populateEvt = new CollectionChangeEvent<>(CollectionChangeType.add, //
											elements, collEvt);
										debug(s -> s.append("populate: ").append(populateEvt));
										try (Transaction evtT = populateEvt.use()) {
											observer.onNext(populateEvt);
										}
										changes = new CollectionChangesObservable<>(collection);
										collectionSub = changes.subscribe((Observer<CollectionChangeEvent<? extends E>>) observer,
											flushOnUnsubscribe);
									}
								} else {
									changes = new CollectionChangesObservable<>(collection);
									collectionSub = changes.subscribe((Observer<CollectionChangeEvent<? extends E>>) observer,
										flushOnUnsubscribe);
								}
								if (Debug.d().debug(Changes.this).isActive())
									Debug.d().debug(changes, true).merge(Debug.d().debug(Changes.this));
							}
						}

						@Override
						public void onCompleted(Causable cause) {
							unsubscribe();
						}

						void unsubscribe() {
							if (collectionSub != null) {
								collectionSub.unsubscribe();
								collectionSub = null;
							}
							collection = null;
						}
					}
					ChangeObserver chgObserver = new ChangeObserver();
					Subscription obsSub = theCollectionObservable.changes().subscribe(chgObserver);
					return () -> {
						chgObserver.flushOnUnsubscribe[0] = false;
						obsSub.unsubscribe();
						chgObserver.unsubscribe();
					};
				}

				void debug(Consumer<StringBuilder> debugMsg) {
					Debug.DebugData data = Debug.d().debug(this);
					if (!Boolean.TRUE.equals(data.getField("debug")))
						return;
					StringBuilder s = new StringBuilder();
					String debugName = data.getField("name", String.class);
					if (debugName != null)
						s.append(debugName).append(": ");
					debugMsg.accept(s);
					System.out.println(s.toString());
				}

				@Override
				public ThreadConstraint getThreadConstraint() {
					return FlattenedValueCollection.this.getThreadConstraint();
				}

				@Override
				public boolean isEventing() {
					return FlattenedValueCollection.this.isEventing();
				}

				@Override
				public boolean isSafe() {
					return theCollectionObservable.noInitChanges().isSafe();
				}

				@Override
				public Transaction lock() {
					return FlattenedValueCollection.this.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return FlattenedValueCollection.this.tryLock(false, null);
				}

				@Override
				public CoreId getCoreId() {
					return FlattenedValueCollection.this.getCoreId();
				}

				@Override
				protected Object createIdentity() {
					return Identifiable.wrap(FlattenedValueCollection.this.getIdentity(), "changes");
				}
			}
			return new Changes();
		}

		@Override
		public Observable<Causable> simpleChanges() {
			return ObservableValue
				.flattenObservableValue(theCollectionObservable.map(coll -> coll != null ? coll.simpleChanges() : Observable.empty()));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return subscribe(observer, false, true);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			return subscribe(observer, true, forward);
		}

		private CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean populate,
			boolean forward) {
			class ElementMappingChangeObserver implements Consumer<ObservableCollectionEvent<? extends E>> {
				private final ObservableCollection<? extends E> theCollection;
				private final Consumer<? super ObservableCollectionEvent<? extends E>> theWrapped;

				ElementMappingChangeObserver(ObservableCollection<? extends E> collection,
					Consumer<? super ObservableCollectionEvent<? extends E>> wrapped) {
					theCollection = collection;
					theWrapped = wrapped;
				}

				@Override
				public void accept(ObservableCollectionEvent<? extends E> event) {
					ObservableCollectionEvent<E> mapped = new ObservableCollectionEvent<>(//
						new FlattenedElementId(theCollection, event.getElementId()), event.getIndex(), event.getType(), event.getOldValue(),
						event.getNewValue(), event, event.getMovement());
					theWrapped.accept(mapped);
				}
			}
			class ChangesSubscription implements Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>> {
				ObservableCollection<? extends E> collection;
				Subscription collectionSub;

				@Override
				public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V collEvt) {
					// The only way we can avoid de-populating and populating the values into the listener is if the old and new collections
					// share identity *and* element IDs, since those are part of the events and the contract of the subscribe method
					boolean clearAndAdd;
					if (!populate && collEvt.isInitial())
						clearAndAdd = false;
					else if (collection == collEvt.getNewValue())
						clearAndAdd = false;
					else if (collection == null || collEvt.getNewValue() == null//
						|| !collection.getIdentity().equals(collEvt.getNewValue().getIdentity()))
						clearAndAdd = true;
					else if (collection.size() != collEvt.getNewValue().size())
						clearAndAdd = true;
					else if (collection.isEmpty()//
						|| collection.getTerminalElement(true).getElementId()
						.equals(collEvt.getNewValue().getTerminalElement(true).getElementId()))
						clearAndAdd = false;
					else
						clearAndAdd = true;
					if (collection != null) {
						if (clearAndAdd) {
							// The collection in the value is not changing--we just don't want it to while we're working
							try (Transaction t = collection.lock(false, null)) {
								if (collectionSub != null) // Subscription may have failed. Don't cause more trouble.
									collectionSub.unsubscribe();
								collectionSub = null;
								// De-populate in opposite direction
								ObservableUtils.depopulateValues(collection, new ElementMappingChangeObserver(collection, observer),
									!forward, collEvt);
							}
						} else { // Don't need to lock
							collectionSub.unsubscribe();
							collectionSub = null;
						}
					}
					collection = collEvt.getNewValue();
					if (collection != null) {
						// The collection in the value is not changing--we just don't want it to while we're working
						try (Transaction t = collection.lock(false, null)) {
							if (clearAndAdd)
								ObservableUtils.populateValues(collection, new ElementMappingChangeObserver(collection, observer), forward,
									collEvt);
							collectionSub = collection.onChange(new ElementMappingChangeObserver(collection, observer));
						}
					}
				}

				@Override
				public void onCompleted(Causable cause) {
					unsubscribe(true);
				}

				void unsubscribe(boolean removeAll) {
					if (collection != null) {
						if (removeAll) {
							// The collection in the value is not changing--we just don't want it to while we're working
							try (Transaction t = collection.lock(false, null)) {
								collectionSub.unsubscribe();
								collectionSub = null;
								// De-populate in opposite direction
								ObservableUtils.depopulateValues(collection, new ElementMappingChangeObserver(collection, observer),
									!forward, null);
							}
						} else { // Don't need to lock
							collectionSub.unsubscribe();
							collectionSub = null;
						}
						collection = null;
					}
				}
			}
			ChangesSubscription chgSub = new ChangesSubscription();
			Subscription obsSub = theCollectionObservable.changes().subscribe(chgSub);
			return removeAll -> {
				obsSub.unsubscribe();
				chgSub.unsubscribe(removeAll);
			};
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		ElementId strip(ObservableCollection<? extends E> coll, ElementId id) {
			if (id == null)
				return null;
			FlattenedElementId flatId = (FlattenedElementId) id;
			if (!flatId.check(coll))
				throw new NoSuchElementException(StdMsg.ELEMENT_REMOVED);
			return flatId.theSourceEl;
		}

		CollectionElement<E> getElement(ObservableCollection<? extends E> collection, CollectionElement<? extends E> element) {
			if (element == null)
				return null;
			return new FlattenedValueElement(collection, element);
		}

		MutableCollectionElement<E> getElement(ObservableCollection<? extends E> collection,
			MutableCollectionElement<? extends E> element) {
			return new MutableFlattenedValueElement(collection, element);
		}

		class FlattenedElementId implements ElementId {
			private final ObservableCollection<? extends E> theCollection;
			private final ElementId theSourceEl;

			FlattenedElementId(ObservableCollection<? extends E> collection, ElementId sourceEl) {
				theCollection = collection;
				theSourceEl = sourceEl;
			}

			boolean check(ObservableCollection<? extends E> collection) {
				if (collection == theCollection)
					return true;
				else if (collection != null && theCollection.getIdentity().equals(collection.getIdentity()))
					return true;
				else
					return false;
			}

			boolean check() {
				return check(getWrapped().get());
			}

			@Override
			public int compareTo(ElementId o) {
				FlattenedElementId other = (FlattenedElementId) o;
				if (!check(other.theCollection))
					throw new IllegalStateException("Cannot compare these elements--one or both have been removed");
				return theSourceEl.compareTo(other.theSourceEl);
			}

			@Override
			public boolean isPresent() {
				// Fudging this just a little, because the contract says that this method should never return true after it is removed
				// This could toggle between true and false, if the value of the wrapped observable goes from A to B to A.
				// The value of this method is only critical during transition events and when using an element from deep storage,
				// and we should be fine in those situations.
				return check() && theSourceEl.isPresent();
			}

			@Override
			public int hashCode() {
				return theSourceEl.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof FlattenedValueCollection.FlattenedElementId))
					return false;
				FlattenedElementId other = (FlattenedElementId) obj;
				return check(other.theCollection) && theSourceEl.equals(other.theSourceEl);
			}

			@Override
			public String toString() {
				String str = theSourceEl.toString();
				if (theSourceEl.isPresent() && !check())
					str = "(removed) " + str;
				return str;
			}
		}

		class FlattenedValueElement implements CollectionElement<E> {
			private final FlattenedElementId theId;
			private final CollectionElement<? extends E> theElement;

			FlattenedValueElement(ObservableCollection<? extends E> collection, CollectionElement<? extends E> element) {
				theElement = element;
				theId = new FlattenedElementId(collection, element.getElementId());
			}

			protected CollectionElement<? extends E> getElement() {
				return theElement;
			}

			@Override
			public FlattenedElementId getElementId() {
				return theId;
			}

			@Override
			public E get() {
				return theElement.get();
			}

			@Override
			public int hashCode() {
				return theElement.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof FlattenedValueCollection.FlattenedValueElement && theId.equals(((FlattenedValueElement) obj).theId);
			}

			@Override
			public String toString() {
				String str = theElement.toString();
				if (theElement.getElementId().isPresent() && !theId.check())
					str = "(removed) " + str;
				return str;
			}
		}

		class MutableFlattenedValueElement extends FlattenedValueElement implements MutableCollectionElement<E> {
			MutableFlattenedValueElement(ObservableCollection<? extends E> collection, MutableCollectionElement<? extends E> element) {
				super(collection, element);
			}

			@Override
			protected MutableCollectionElement<? extends E> getElement() {
				return (MutableCollectionElement<? extends E>) super.getElement();
			}

			@Override
			public BetterCollection<E> getCollection() {
				return FlattenedValueCollection.this;
			}

			@Override
			public String isEnabled() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getElement().isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				else if (!getWrapped().get().belongs(value))
					return StdMsg.BAD_TYPE;
				return ((MutableCollectionElement<E>) getElement()).isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				else if (!getWrapped().get().belongs(value))
					throw new IllegalArgumentException(StdMsg.BAD_TYPE);
				((MutableCollectionElement<E>) getElement()).set(value);
			}

			@Override
			public String canRemove() {
				if (!getElementId().check())
					return StdMsg.UNSUPPORTED_OPERATION;
				return getElement().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (!getElementId().check())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				getElement().remove();
			}
		}
	}

	public static class SimpleCollectionBackedObservable<T> implements ObservableCollection<T> {
		private final ObservableCollection<T> theCollection;
		private final List<ElementId> theBackingElements;
		private final SettableValue<? extends Collection<T>> theCollectionValue;
		private Subscription theValueSubscription;
		private final AtomicInteger theSubscriptionCount;
		private long theStampCopy;
		private boolean isModifying;

		protected SimpleCollectionBackedObservable(ObservableCollection<T> collection,
			SettableValue<? extends Collection<T>> collectionValue) {
			theCollection = collection;
			theBackingElements = new ArrayList<>();
			theCollectionValue = collectionValue;
			theSubscriptionCount = new AtomicInteger();
			theStampCopy = -1;
			sync(null);
		}

		@Override
		public CollectionElement<T> getElement(int index) throws IndexOutOfBoundsException {
			return theCollection.getElement(index);
		}

		@Override
		public boolean isContentControlled() {
			return theCollection.isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			update();
			return theCollection.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			update();
			return theCollection.getElementsAfter(id);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction cvT = theCollectionValue.lock(write, cause);
			Collection<T> cv = theCollectionValue.get();
			Transaction cT = Transactable.lock(cv, write, cause);
			update();
			Transaction collT = theCollection.lock(write, cause);
			return Transaction.and(cvT, cT, collT);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			Transaction cvT = theCollectionValue.tryLock(write, cause);
			if (cvT == null)
				return null;
			Collection<T> cv = theCollectionValue.get();
			Transaction cT = Transactable.tryLock(cv, write, cause);
			if (cT == null) {
				cvT.close();
				return null;
			}
			update();
			Transaction collT = theCollection.tryLock(write, cause);
			if (collT == null) {
				cT.close();
				cvT.close();
				return null;
			}
			return Transaction.and(cvT, cT, collT);
		}

		@Override
		public CoreId getCoreId() {
			return theCollectionValue.getCoreId();
		}

		@Override
		public int size() {
			update();
			return theCollection.size();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theCollectionValue.getThreadConstraint();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theCollectionValue.getIdentity(), "observeCollection");
		}

		@Override
		public boolean isEmpty() {
			update();
			return theCollection.isEmpty();
		}

		@Override
		public boolean isEventing() {
			return theCollection.isEventing() || theCollectionValue.isEventing();
		}

		@Override
		public CollectionElement<T> getElement(T value, boolean first) {
			update();
			return theCollection.getElement(value, first);
		}

		@Override
		public CollectionElement<T> getElement(ElementId id) {
			update();
			return theCollection.getElement(id);
		}

		@Override
		public CollectionElement<T> getTerminalElement(boolean first) {
			update();
			return theCollection.getTerminalElement(first);
		}

		@Override
		public CollectionElement<T> getAdjacentElement(ElementId elementId, boolean next) {
			update();
			return theCollection.getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			update();
			return new MutableElement(theCollection.mutableElement(id));
		}

		@Override
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			Collection<T> c = theCollectionValue.get();
			if (c instanceof BetterCollection) {
				try (Transaction t = lock(false, null)) {
					BetterCollection<T> list = (BetterCollection<T>) c;
					return BetterList.of2(list.getElementsBySource(sourceEl, sourceCollection).stream(),
						el -> getElement(getFrontedElement(el.getElementId())));
				}
			} else
				return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			Collection<T> c = theCollectionValue.get();
			if (c instanceof BetterList && !theCollection.isContentControlled()) {
				try (Transaction t = lock(false, null)) {
					BetterCollection<T> list = (BetterCollection<T>) c;
					return BetterList.of2(list.getSourceElements(localElement, sourceCollection).stream(), el -> getFrontedElement(el));
				}
			} else
				return BetterList.empty();
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			ElementId result = theCollection.getEquivalentElement(equivalentEl);
			if (result != null)
				return result;
			Collection<T> c = theCollectionValue.get();
			if (c instanceof BetterList && !theCollection.isContentControlled()) {
				try (Transaction t = lock(false, null)) {
					BetterList<T> list = (BetterList<T>) c;
					result = list.getEquivalentElement(equivalentEl);
					if (result != null)
						return getElement(list.getElementsBefore(result)).getElementId();
				}
			}
			return null;
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				String error = theCollection.canAdd(value, after, before);
				if (error != null)
					throw new IllegalArgumentException(error);
				Collection<T> coll = theCollectionValue.get();
				if (coll instanceof BetterCollection) {
					return ((BetterCollection<T>) coll).canAdd(value, getBackingElement(after), getBackingElement(before));
				} else if (coll instanceof List)
					return null;
				else
					return StdMsg.UNSUPPORTED_OPERATION;
			}
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				String error = theCollection.canAdd(value, after, before);
				if (error != null)
					throw new IllegalArgumentException(error);
				CollectionElement<T> result;
				Collection<T> coll = theCollectionValue.get();
				if (coll instanceof BetterCollection) {
					CollectionElement<T> backing = ((BetterCollection<T>) coll).addElement(value, getBackingElement(after),
						getBackingElement(before), first);
					if (backing == null)
						return null;
					result = addBackingElement(backing);
				} else if (coll instanceof List) {
					int index;
					if (first) {
						if (after != null)
							index = theCollection.getElementsBefore(after) + 1;
						else
							index = 0;
					} else if (before != null)
						index = theCollection.getElementsBefore(before);
					else
						index = theCollection.size();
					((List<T>) coll).add(index, value);
					result = theCollection.addElement(index, value);
				} else if (after == null && before == null) {
					// In theory we could try to add here, but it would be difficult to figure out where the element was added
					throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
				} else
					throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);

				if (result != null) {
					isModifying = true;
					try {
						if (((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
							((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
						}
					} finally {
						isModifying = false;
					}
				}
				return result;
			}
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				String error = theCollection.canMove(valueEl, after, before);
				if (error != null)
					throw new IllegalArgumentException(error);
				Collection<T> coll = theCollectionValue.get();
				if (coll instanceof BetterCollection) {
					return ((BetterCollection<T>) coll).canMove(valueEl, getBackingElement(after), getBackingElement(before));
				} else if (coll instanceof List)
					return null;
				else
					return StdMsg.UNSUPPORTED_OPERATION;
			}
		}

		@Override
		public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				String error = theCollection.canMove(valueEl, after, before);
				if (error != null)
					throw new IllegalArgumentException(error);
				CollectionElement<T> result;
				Collection<T> coll = theCollectionValue.get();
				if (coll instanceof BetterCollection) {
					ElementId backingValueEl = getBackingElement(valueEl);
					CollectionElement<T> backing = ((BetterCollection<T>) coll).move(backingValueEl, getBackingElement(after),
						getBackingElement(before), first, () -> removeBackingElement(backingValueEl));
					if (backingValueEl.isPresent())
						return getElement(valueEl); // No actual move
					// We can't use movement in the backing collection, because it might not put it exactly where the source did
					theCollection.mutableElement(valueEl).remove();
					if (afterRemove != null)
						afterRemove.run();
					result = addBackingElement(backing);
				} else if (coll instanceof List) {
					int sourceIndex = theCollection.getElementsBefore(valueEl);
					result = theCollection.move(valueEl, after, before, first, afterRemove);
					if (valueEl.isPresent())
						return result; // No actual move
					T value = ((List<T>) coll).remove(sourceIndex);
					((List<T>) coll).add(theCollection.getElementsBefore(result.getElementId()), value);
				} else
					throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);

				if (result != null) {
					isModifying = true;
					try {
						if (((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
							((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
						}
					} finally {
						isModifying = false;
					}
				}
				return result;
			}
		}

		@Override
		public long getStamp() {
			update();
			return theStampCopy;
		}

		@Override
		public TypeToken<T> getType() {
			return theCollection.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theCollectionValue.isLockSupported();
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				Collection<T> coll = theCollectionValue.get();
				if (coll.isEmpty())
					return;
				int preSize = coll.size();
				coll.clear();
				isModifying = true;
				try {
					if (coll.size() != preSize && ((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
						((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
					}
				} finally {
					isModifying = false;
				}
			}
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theCollection.equivalence();
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			if (elements.isEmpty())
				return;
			try (Transaction t = lock(true, null)) {
				Collection<T> coll = theCollectionValue.get();
				if (coll instanceof BetterCollection) {
					BetterCollection<T> bc = (BetterCollection<T>) coll;
					for (ElementId el : elements) {
						String error = bc.mutableElement(el).isAcceptable(value);
						if (StdMsg.UNSUPPORTED_OPERATION.equals(error))
							throw new UnsupportedOperationException(error);
						else if (error != null)
							throw new IllegalArgumentException(error);
					}
					boolean success = false;
					try {
						for (ElementId el : elements)
							bc.mutableElement(el).set(value);
						success = true;
						theCollection.setValue(elements, value);
					} finally {
						if (!success)
							update();
					}
					isModifying = true;
					try {
						if (((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
							((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
						}
					} finally {
						isModifying = false;
					}
				}
			}
		}

		protected ElementId getBackingElement(ElementId localElement) {
			if (localElement == null || !localElement.isPresent())
				return null;
			return theBackingElements.get(theCollection.getElementsBefore(localElement));
		}

		protected ElementId getFrontedElement(ElementId backingElement) {
			int index = Collections.binarySearch(theBackingElements, backingElement);
			return theCollection.getElement(index).getElementId();
		}

		private CollectionElement<T> addBackingElement(CollectionElement<T> backingElement) {
			int index = Collections.binarySearch(theBackingElements, backingElement.getElementId());
			index = -index - 1;
			return theCollection.addElement(index, backingElement.get());
		}

		private void removeBackingElement(ElementId backingElement) {
			int index = Collections.binarySearch(theBackingElements, backingElement);
			theBackingElements.remove(index);
		}

		protected void update() {
			long stamp = getBackingStamp();
			if (stamp != theStampCopy) {
				theStampCopy = stamp;
				sync(null);
			}
		}

		private long getBackingStamp() {
			long stamp = theCollectionValue.getStamp();
			Collection<T> coll = theCollectionValue.get();
			if (coll instanceof Stamped)
				return Stamped.compositeStamp(stamp, ((Stamped) coll).getStamp());
			else
				return stamp;
		}

		private void sync(Object cause) {
			try (Transaction t = theCollectionValue.lock(); //
				Causable.CausableInUse syncCause = Causable.cause(cause)) {
				Collection<T> cv = theCollectionValue.get();
				try (Transaction t2 = Transactable.lock(cv, false, syncCause)) {
					List<T> list = cv instanceof List ? (List<T>) cv : QommonsUtils.unmodifiableCopy(cv);
					CollectionUtils.SimpleAdjustment<T, T, RuntimeException> syncAction = CollectionUtils.synchronize(theCollection, list)//
						.simple(LambdaUtils.identity());
					if (theCollection.isContentControlled())
						syncAction.addLast();
					else
						syncAction.rightOrder();
					syncAction.adjust();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			if (0 == theSubscriptionCount.getAndIncrement()) {
				theValueSubscription = theCollectionValue.noInitChanges().act(evt -> {
					if (!isModifying)
						sync(evt);
				});
			}
			Subscription sub = theCollection.onChange(observer);
			boolean[] unsubscribed = new boolean[1];
			return () -> {
				if (unsubscribed[0])
					return;
				unsubscribed[0] = true;
				sub.unsubscribe();
				if (0 == theSubscriptionCount.decrementAndGet()) {
					theValueSubscription.unsubscribe();
					theValueSubscription = null;
				}
			};
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		class MutableElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<T> theWrappedEl;

			MutableElement(MutableCollectionElement<T> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public T get() {
				return theWrappedEl.get();
			}

			@Override
			public BetterCollection<T> getCollection() {
				return SimpleCollectionBackedObservable.this;
			}

			@Override
			public String isEnabled() {
				String msg = theWrappedEl.isEnabled();
				if (msg != null)
					return msg;
				try (Transaction t = lock(false, null)) {
					if (!theWrappedEl.getElementId().isPresent())
						return StdMsg.ELEMENT_REMOVED;
					Collection<T> coll = theCollectionValue.get();
					if (coll instanceof BetterCollection)
						return ((BetterCollection<T>) coll).mutableElement(getBackingElement(getElementId())).isEnabled();
					else if (coll instanceof List)
						return null;
					else
						return StdMsg.UNSUPPORTED_OPERATION;
				}
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theWrappedEl.isAcceptable(value);
				if (msg != null)
					return msg;
				try (Transaction t = lock(false, null)) {
					if (!theWrappedEl.getElementId().isPresent())
						return StdMsg.ELEMENT_REMOVED;
					Collection<T> coll = theCollectionValue.get();
					if (coll instanceof BetterCollection)
						return ((BetterCollection<T>) coll).mutableElement(getBackingElement(getElementId())).isAcceptable(value);
					else if (coll instanceof List)
						return null;
					else
						return StdMsg.UNSUPPORTED_OPERATION;
				}
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = theWrappedEl.isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				try (Transaction t = lock(true, null)) {
					if (!theWrappedEl.getElementId().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					Collection<T> coll = theCollectionValue.get();
					if (coll instanceof BetterCollection) {
						((BetterCollection<T>) coll).mutableElement(getBackingElement(getElementId())).set(value);
						theWrappedEl.set(value);
					} else if (coll instanceof List) {
						int index = theCollection.getElementsBefore(getElementId());
						((List<T>) coll).set(index, value);
						theWrappedEl.set(value);
					} else
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					isModifying = true;
					try {
						if (((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
							((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
						}
					} finally {
						isModifying = false;
					}
				}
			}

			@Override
			public String canRemove() {
				try (Transaction t = lock(false, null)) {
					if (!theWrappedEl.getElementId().isPresent())
						return StdMsg.ELEMENT_REMOVED;
					Collection<T> coll = theCollectionValue.get();
					if (coll instanceof BetterCollection)
						return ((BetterCollection<T>) coll).mutableElement(getBackingElement(getElementId())).canRemove();
					else if (coll instanceof List)
						return null;
					else
						return StdMsg.UNSUPPORTED_OPERATION;
				}
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					if (!theWrappedEl.getElementId().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					Collection<T> coll = theCollectionValue.get();
					if (coll instanceof BetterCollection) {
						((BetterCollection<T>) coll).mutableElement(getBackingElement(getElementId())).remove();
						theWrappedEl.remove();
					} else if (coll instanceof List) {
						int index = theCollection.getElementsBefore(getElementId());
						((List<T>) coll).remove(index);
						theWrappedEl.remove();
					} else
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					isModifying = true;
					try {
						if (((SettableValue<Collection<T>>) theCollectionValue).isAcceptable(coll) == null) {
							((SettableValue<Collection<T>>) theCollectionValue).set(coll, null);
						}
					} finally {
						isModifying = false;
					}
				}
			}

			@Override
			public String toString() {
				return theWrappedEl.toString();
			}
		}
	}

	/**
	 * Default {@link DataControlledCollection} implementation
	 *
	 * @param <E> The type of the collection values
	 * @param <V> The type of the source data
	 */
	public static class DataControlledCollectionImpl<E, V> extends ObservableCollectionWrapper<E>
	implements DataControlledCollection<E, V> {
		private final ObservableCollection<E> theBacking;
		private long theMaxRefreshFrequency;
		private final Supplier<? extends List<? extends V>> theBackingData;
		private final ObservableCollectionBuilder.DataControlAutoRefresher theAutoRefresh;
		private boolean isRefreshOnAccess;
		private final BiPredicate<? super E, ? super V> theEqualsTester;
		private final CollectionUtils.CollectionSynchronizerE<E, ? super V, ?> theSynchronizer;
		private final CollectionUtils.AdjustmentOrder theAdjustmentOrder;
		private final SettableValue<Boolean> isRefreshing;

		private volatile long theLastRefresh;
		private final AtomicInteger theListeningCount;
		private volatile Runnable theAutoRefreshTerminate;

		/**
		 * @param backing The collection to control all the observable functionality
		 * @param backingData Supplies backing data for refresh operations
		 * @param autoRefresh The asynchronous auto refresher for this collection
		 * @param refreshOnAccess Whether this collection should refresh synchronously each time it is accessed
		 * @param equals The equals tester to preserve elements between refreshes
		 * @param synchronizer The synchronizer to perform the refresh operation
		 * @param adjustmentOrder The adjustment order for the synchronization
		 */
		public DataControlledCollectionImpl(ObservableCollection<E> backing, Supplier<? extends List<? extends V>> backingData,
			ObservableCollectionBuilder.DataControlAutoRefresher autoRefresh, boolean refreshOnAccess,
			BiPredicate<? super E, ? super V> equals, CollectionUtils.CollectionSynchronizerE<E, ? super V, ?> synchronizer,
			CollectionUtils.AdjustmentOrder adjustmentOrder) {
			theBacking = backing;
			theBackingData = backingData;
			theAutoRefresh = autoRefresh;
			isRefreshOnAccess = refreshOnAccess;
			theEqualsTester = equals;
			theSynchronizer = synchronizer;
			theAdjustmentOrder = adjustmentOrder;
			theListeningCount = theAutoRefresh == null ? null : new AtomicInteger();
			isRefreshing = SettableValue.build(boolean.class).withLocking(backing).withValue(false).build();

			init(backing// TODO Maybe one day add capability for callers to affect the backing data
				.flow().unmodifiable(false).collectPassive());
			try {
				refresh();
			} catch (RuntimeException e) {
				System.err.println("Could not perform initial refresh");
				e.printStackTrace();
			}
		}

		@Override
		public long getMaxRefreshFrequency() {
			return theMaxRefreshFrequency;
		}

		@Override
		public DataControlledCollectionImpl<E, V> setMaxRefreshFrequency(long frequency) {
			theMaxRefreshFrequency = frequency;
			return this;
		}

		@Override
		public boolean refresh() throws CheckedExceptionWrapper {
			long now;
			if (theMaxRefreshFrequency > 0) {
				now = System.currentTimeMillis();
				if (now - theLastRefresh < theMaxRefreshFrequency)
					return false;
			} else
				now = 0;
			Transaction lock = getWrapped().tryLock(true, null);
			if (lock == null)
				return false;
			theLastRefresh = now;
			try {
				doRefresh();
			} finally {
				lock.close();
			}
			return true;
		}

		private void doRefresh() {
			// if (getIdentity().toString().contains(".tar"))
			// BreakpointHere.breakpoint();
			isRefreshing.set(true, null);
			try {
				// System.out.println("Refreshing " + getIdentity());
				List<? extends V> backing = theBackingData.get();
				if (backing == null) {
					theBacking.clear();
					return;
				}
				try (Transaction t2 = backing instanceof Transactable ? ((Transactable) backing).lock(false, null) : Transaction.NONE) {
					CollectionUtils.synchronize(theBacking, backing, theEqualsTester)//
					.adjust(theSynchronizer, theAdjustmentOrder);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new CheckedExceptionWrapper(e);
				}
			} finally {
				// System.out.println("Done refreshing " + getIdentity());
				isRefreshing.set(false, null);
			}
		}

		@Override
		public ObservableValue<Boolean> isRefreshing() {
			return isRefreshing.unsettable();
		}

		@Override
		public int size() {
			if (isRefreshOnAccess)
				refresh();
			return getWrapped().size();
		}

		@Override
		public boolean isEmpty() {
			if (isRefreshOnAccess)
				refresh();
			return getWrapped().isEmpty();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (!isRefreshOnAccess)
				return getWrapped().lock(write, cause);
			else if (write) {
				Transaction lock = super.lock(true, cause);
				doRefresh();
				return lock;
			} else {
				refresh();
				return super.lock(false, cause);
			}
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (!isRefreshOnAccess)
				return getWrapped().tryLock(write, cause);
			else if (write) {
				Transaction lock = super.tryLock(true, cause);
				if (lock == null)
					return null;
				doRefresh();
				return lock;
			} else {
				refresh();
				return super.tryLock(false, cause);
			}
		}

		@Override
		public long getStamp() {
			if (isRefreshOnAccess)
				refresh();
			return getWrapped().getStamp();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			if (isRefreshOnAccess)
				refresh();
			if (theListeningCount == null)
				return super.onChange(observer);
			// If unsubscription and subscription happen in rapid succession,
			// it may be necessary to ensure the operations don't trample each other
			// This synchronization only occurs on the first subscription and the last unsubscription
			if (theListeningCount.getAndIncrement() == 0) {
				synchronized (this) {
					theAutoRefreshTerminate = theAutoRefresh.add(this);
				}
			}
			Subscription sub = super.onChange(observer);
			return () -> {
				sub.unsubscribe();
				if (theListeningCount.decrementAndGet() == 0) {
					synchronized (this) {
						Runnable terminate = theAutoRefreshTerminate;
						theAutoRefreshTerminate = null;
						terminate.run();
					}
				}
			};
		}
	}
}

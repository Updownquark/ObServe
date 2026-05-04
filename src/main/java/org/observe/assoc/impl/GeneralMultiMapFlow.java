package org.observe.assoc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.ModControlledObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.Identifiable;
import org.qommons.IterableUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.Stamped;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.ListenerList;
import org.qommons.collect.ModControlledCollection;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OrderedMultiEntry;
import org.qommons.fn.FunctionUtils;

/**
 * A general implementation for {@link ObservableMultiMap#flow()}
 *
 * @param <KS> The key type of the source multi-map
 * @param <KT> The key type of the derived multi-map
 * @param <VS> The value type of the source multi-map
 * @param <VT> The value type of the derived multi-map
 */
public class GeneralMultiMapFlow<KS, KT, VS, VT> implements MultiMapFlow<KT, VT> {
	/**
	 * @param <K> The key type of the source multi-map
	 * @param <V> The value type of the source multi-map
	 * @param source The source multi-map
	 * @return The multi-map flow for the map
	 */
	public static <K, V> GeneralMultiMapFlow<K, K, V, V> init(ObservableMultiMap<K, V> source) {
		if (source instanceof ObservableSortedMultiMap)
			return new GeneralSortedMultiMapFlow<>(source, FunctionUtils.identity(), FunctionUtils.identity(),
				PassiveFlowSupport.SUPPORT[0]);
		else
			return new GeneralMultiMapFlow<>(source, FunctionUtils.identity(), FunctionUtils.identity(), PassiveFlowSupport.SUPPORT[0]);
	}

	/**
	 * @param <K> The key type of the source sorted multi-map
	 * @param <V> The value type of the source sorted multi-map
	 * @param source The source sorted multi-map
	 * @return The sorted multi-map flow for the sorted map
	 */
	public static <K, V> GeneralSortedMultiMapFlow<K, K, V, V> init(ObservableSortedMultiMap<K, V> source) {
		return new GeneralSortedMultiMapFlow<>(source, FunctionUtils.identity(), FunctionUtils.identity(), PassiveFlowSupport.SUPPORT[0]);
	}

	private static final ObservableSortedSet<?> SORTED_EMPTY = ObservableSortedSet.of((v1, v2) -> 0);

	private final ObservableMultiMap<KS, VS> theSource;
	private final Function<? super DistinctDataFlow<KS, ?, KS>, ? extends DistinctDataFlow<?, ?, KT>> theKeyFlow;
	private final Function<? super CollectionDataFlow<VS, ?, VS>, ? extends CollectionDataFlow<?, ?, VT>> theValueFlow;
	private final PassiveFlowSupport thePassiveSupport;

	/**
	 * @param source The source multi-map
	 * @param keyFlow The function producing key flows from the source key set
	 * @param valueFlow The function producing value flows from source value collections
	 * @param passiveSupport The passive support of the flow
	 */
	protected GeneralMultiMapFlow(ObservableMultiMap<KS, VS> source,
		Function<? super DistinctDataFlow<KS, ?, KS>, ? extends DistinctDataFlow<?, ?, KT>> keyFlow,
			Function<? super CollectionDataFlow<VS, ?, VS>, ? extends CollectionDataFlow<?, ?, VT>> valueFlow,
				PassiveFlowSupport passiveSupport) {
		theSource = source;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
		this.thePassiveSupport = passiveSupport;
	}

	/** @return The source multi-map that this flow is for */
	protected ObservableMultiMap<KS, VS> getSource() {
		return theSource;
	}

	/** @return The function producing key flows from the source key set */
	protected Function<? super DistinctDataFlow<KS, KS, KS>, ? extends DistinctDataFlow<?, ?, KT>> getKeyFlow() {
		return theKeyFlow;
	}

	/** @return The function producing value flows from source value collections */
	protected Function<? super CollectionDataFlow<VS, ?, VS>, ? extends CollectionDataFlow<?, ?, VT>> getValueFlow() {
		return theValueFlow;
	}

	/** @return The passive support of the flow */
	protected PassiveFlowSupport getPassiveSupport() {
		return thePassiveSupport;
	}

	@Override
	public <K2> MultiMapFlow<K2, VT> withKeys(Function<DistinctDataFlow<?, ?, KT>, DistinctDataFlow<?, ?, K2>> keyMap) {
		return new GeneralMultiMapFlow<>(theSource, theKeyFlow.andThen(keyMap), theValueFlow, thePassiveSupport.and(keyMap));
	}

	@Override
	public <K2> SortedMultiMapFlow<K2, VT> withSortedKeys(Function<DistinctDataFlow<?, ?, KT>, DistinctSortedDataFlow<?, ?, K2>> keyMap) {
		return new GeneralSortedMultiMapFlow<>(theSource, theKeyFlow.andThen(keyMap), theValueFlow, thePassiveSupport.and(keyMap));
	}

	@Override
	public <V2> MultiMapFlow<KT, V2> withValues(Function<CollectionDataFlow<?, ?, VT>, CollectionDataFlow<?, ?, V2>> valueMap) {
		return new GeneralMultiMapFlow<>(theSource, theKeyFlow, theValueFlow.andThen(valueMap), thePassiveSupport.and(valueMap));
	}

	@Override
	public MultiMapFlow<KT, VT> reverse() {
		return new GeneralMultiMapFlow<>(theSource.reverse(), theKeyFlow, theValueFlow, thePassiveSupport);
	}

	@Override
	public boolean supportsPassive() {
		return thePassiveSupport.supportsPassive;
	}

	@Override
	public boolean prefersPassive() {
		return thePassiveSupport.prefersPassive;
	}

	@Override
	public ObservableMultiMap<KT, VT> gatherPassive() {
		if (!supportsPassive())
			throw new IllegalStateException("This multi-map flow does not support passive gathering");
		return new GeneralPassiveDerivedMultiMap<>(this, new AddKeyHolder.Default<>());
	}

	@Override
	public ObservableMultiMap<KT, VT> gatherActive(Observable<?> until) {
		return new GeneralActiveDerivedMultiMap<>(this, until);
	}

	@Override
	public int hashCode() {
		return Objects.hash(theSource.getIdentity(), theKeyFlow, theValueFlow);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof GeneralMultiMapFlow))
			return false;
		GeneralMultiMapFlow<?, ?, ?, ?> other = (GeneralMultiMapFlow<?, ?, ?, ?>) obj;
		return theSource.getIdentity().equals(other.theSource.getIdentity())//
			&& theKeyFlow.equals(other.theKeyFlow)//
			&& theValueFlow.equals(other.theValueFlow);
	}

	@Override
	public String toString() {
		if (theKeyFlow.equals(FunctionUtils.identity()) && theValueFlow.equals(FunctionUtils.identity()))
			return theSource.getIdentity().toString();
		StringBuilder str = new StringBuilder().append(theSource.getIdentity());
		if (!theKeyFlow.equals(FunctionUtils.identity()))
			str.append(".withKeys(").append(theKeyFlow).append(')');
		if (!theValueFlow.equals(FunctionUtils.identity()))
			str.append(".withValues(").append(theValueFlow).append(')');
		return str.toString();
	}

	private static class PassiveFlowSupport {
		static final PassiveFlowSupport DEFAULT_PASSIVE = new PassiveFlowSupport(true, true);
		static final PassiveFlowSupport DEFAULT_ACTIVE = new PassiveFlowSupport(true, false);
		static final PassiveFlowSupport NO_PASSIVE = new PassiveFlowSupport(false, false);

		private static final PassiveFlowSupport[] SUPPORT = { DEFAULT_PASSIVE, DEFAULT_ACTIVE, NO_PASSIVE };

		final boolean supportsPassive;
		final boolean prefersPassive;

		private PassiveFlowSupport(boolean supportsPassive, boolean defaultPassive) {
			this.supportsPassive = supportsPassive;
			this.prefersPassive = defaultPassive;
		}

		<V> PassiveFlowSupport and(Function<? super DistinctSortedDataFlow<?, ?, V>, ? extends CollectionDataFlow<?, ?, ?>> flow) {
			if (!supportsPassive)
				return this;
			CollectionDataFlow<?, ?, ?> newFlow = flow.apply(((ObservableSortedSet<V>) SORTED_EMPTY).flow());
			if (!newFlow.supportsPassive())
				return NO_PASSIVE;
			if (!prefersPassive || !newFlow.prefersPassive())
				return DEFAULT_ACTIVE;
			else
				return DEFAULT_PASSIVE;
		}
	}

	/**
	 * Sorted extension of {@link GeneralMultiMapFlow}
	 *
	 * @param <KS> The key type of the source multi-map
	 * @param <KT> The key type of the derived multi-map
	 * @param <VS> The value type of the source multi-map
	 * @param <VT> The value type of the derived multi-map
	 */
	public static class GeneralSortedMultiMapFlow<KS, KT, VS, VT> extends GeneralMultiMapFlow<KS, KT, VS, VT>
	implements SortedMultiMapFlow<KT, VT> {
		/**
		 * @param source The source multi-map
		 * @param keyFlow The function producing key flows from the source key set
		 * @param valueFlow The function producing value flows from source value collections
		 * @param passiveSupport The passive support of the flow
		 */
		protected GeneralSortedMultiMapFlow(ObservableMultiMap<KS, VS> source,
			Function<? super DistinctSortedDataFlow<KS, ?, KS>, ? extends DistinctSortedDataFlow<?, ?, KT>> keyFlow,
				Function<? super CollectionDataFlow<VS, ?, VS>, ? extends CollectionDataFlow<?, ?, VT>> valueFlow,
					PassiveFlowSupport passiveSupport) {
			super(source, (Function<? super DistinctDataFlow<KS, ?, KS>, ? extends DistinctDataFlow<?, ?, KT>>) keyFlow, valueFlow,
				passiveSupport);
		}

		@Override
		protected ObservableSortedMultiMap<KS, VS> getSource() {
			return (ObservableSortedMultiMap<KS, VS>) super.getSource();
		}

		@Override
		protected Function<? super DistinctDataFlow<KS, ?, KS>, ? extends DistinctSortedDataFlow<?, ?, KT>> getKeyFlow() {
			return (Function<? super DistinctDataFlow<KS, ?, KS>, ? extends DistinctSortedDataFlow<?, ?, KT>>) super.getKeyFlow();
		}

		@Override
		public <V2> SortedMultiMapFlow<KT, V2> withValues(Function<CollectionDataFlow<?, ?, VT>, CollectionDataFlow<?, ?, V2>> valueMap) {
			return new GeneralSortedMultiMapFlow<>(getSource(), getKeyFlow(), getValueFlow().andThen(valueMap),
				getPassiveSupport().and(valueMap));
		}

		@Override
		public SortedMultiMapFlow<KT, VT> reverse() {
			return new GeneralSortedMultiMapFlow<>(getSource().reverse(), getKeyFlow(), getValueFlow(), getPassiveSupport());
		}

		@Override
		public ObservableSortedMultiMap<KT, VT> gatherPassive() {
			if (!supportsPassive())
				throw new IllegalStateException("This multi-map flow does not support passive gathering");
			return new GeneralPassiveDerivedSortedMultiMap<>(this, new AddKeyHolder.Default<>());
		}

		@Override
		public ObservableSortedMultiMap<KT, VT> gatherActive(Observable<?> until) {
			return new GeneralActiveDerivedSortedMultiMap<>(this, until);
		}
	}

	/**
	 * Default passive multi-map produced by a {@link GeneralMultiMapFlow}
	 *
	 * @param <KS> The key type of the source multi-map
	 * @param <KT> The key type of the derived multi-map
	 * @param <VS> The value type of the source multi-map
	 * @param <VT> The value type of the derived multi-map
	 */
	public static class GeneralPassiveDerivedMultiMap<KS, KT, VS, VT> extends AbstractPassiveMultiMap<KS, VS, KT, VT> {
		private final GeneralMultiMapFlow<KS, KT, VS, VT> theMapFlow;

		/**
		 * @param mapFlow The flow that produce this map
		 * @param addKey The add key for producing value collections
		 */
		public GeneralPassiveDerivedMultiMap(GeneralMultiMapFlow<KS, KT, VS, VT> mapFlow, AddKeyHolder<KT> addKey) {
			super(mapFlow.getSource(), (DistinctDataFlow<KS, ?, KT>) mapFlow.getKeyFlow().apply(mapFlow.getSource().keySet().flow()),
				(Function<CollectionDataFlow<VS, ?, VS>, CollectionDataFlow<VS, ?, VT>>) mapFlow.getValueFlow(), addKey);
			theMapFlow = mapFlow;
		}

		@Override
		public MultiMapFlow<KT, VT> flow() {
			return theMapFlow;
		}
	}

	/**
	 * Default passive sorted multi-map produced by a {@link GeneralMultiMapFlow}
	 *
	 * @param <KS> The key type of the source multi-map
	 * @param <KT> The key type of the derived multi-map
	 * @param <VS> The value type of the source multi-map
	 * @param <VT> The value type of the derived multi-map
	 */
	public static class GeneralPassiveDerivedSortedMultiMap<KS, KT, VS, VT> extends GeneralPassiveDerivedMultiMap<KS, KT, VS, VT>
	implements ObservableSortedMultiMap<KT, VT> {
		/**
		 * @param mapFlow The flow that produce this map
		 * @param addKey The add key for producing value collections
		 */
		public GeneralPassiveDerivedSortedMultiMap(GeneralSortedMultiMapFlow<KS, KT, VS, VT> mapFlow, AddKeyHolder<KT> addKey) {
			super(mapFlow, addKey);
		}

		@Override
		public ObservableSortedMultiMap<KT, VT> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSortedSet<KT> keySet() {
			return (ObservableSortedSet<KT>) super.keySet();
		}

		@Override
		public SortedMultiMapFlow<KT, VT> flow() {
			return (SortedMultiMapFlow<KT, VT>) super.flow();
		}
	}

	/**
	 * Default active multi-map produced by a {@link GeneralMultiMapFlow}
	 *
	 * @param <KS> The key type of the source multi-map
	 * @param <KT> The key type of the derived multi-map
	 * @param <VS> The value type of the source multi-map
	 * @param <VT> The value type of the derived multi-map
	 */
	public static class GeneralActiveDerivedMultiMap<KS, KT, VS, VT> extends Identifiable.AbstractIdentifiable
	implements ObservableMultiMap<KT, VT> {
		class ActiveEntry implements OrderedMultiEntry<KT, VT> {
			private final ListElement<KT> theKey;
			private final ObservableCollection<VT> theValues;
			private final SimpleObservable<Void> theEntryUntil;

			ActiveEntry(ListElement<KT> key, ObservableCollection<VS> sourceValues) {
				theKey = key;
				theEntryUntil = new SimpleObservable<>();
				theValues = theFlow.getValueFlow().apply(sourceValues.flow()).collectActive(theEntryUntil);
			}

			@Override
			public ElementId getElementId() {
				return theKey.getElementId();
			}

			@Override
			public KT getKey() {
				return theKey.get();
			}

			@Override
			public ObservableCollection<VT> getValues() {
				return theValues;
			}

			@Override
			public int getElementsBefore() {
				return theKey.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theKey.getElementsAfter();
			}

			@Override
			public OrderedMultiEntry<KT, VT> getAdjacent(boolean next) {
				int index = getElementsBefore();
				if (next) {
					if (theKey.getElementId().isPresent())
						index++;
					if (index >= theEntries.size())
						return null;
				} else {
					index--;
					if (index < 0)
						return null;
				}
				return theEntries.get(index);
			}

			Observable<?> getUntil() {
				return theEntryUntil;
			}

			void destroy() {
				theEntryUntil.onNext(null);
			}
		}

		private final GeneralMultiMapFlow<KS, KT, VS, VT> theFlow;
		private final List<ActiveEntry> theEntries;
		private final AtomicInteger theValueSize;
		private final AtomicInteger isEventing;
		private final ObservableSet<KT> theKeySet;
		private boolean isTestingAdd;
		private final AddKeyHolder.Default<KS> theAddKey;
		private final List<VS> theAddValues;
		private final ObservableCollection<VT> theValueTester;
		private final ListenerList<Consumer<? super ObservableMultiMapEvent<? extends KT, ? extends VT>>> theListeners;

		/**
		 * @param flow The flow that produce this map
		 * @param until The observable to destroy this multi-map
		 */
		public GeneralActiveDerivedMultiMap(GeneralMultiMapFlow<KS, KT, VS, VT> flow, Observable<?> until) {
			theFlow = flow;
			initIdentity(theFlow);
			theAddKey = new AddKeyHolder.Default<>();
			theKeySet = theFlow.getKeyFlow().apply(ModControlledObservableCollection.controlSet(theFlow.getSource().keySet(), //
				new ModControlledCollection.CollectionModificationControl<KS>() {
				@Override
				public String canAdd(KS value, ElementId after, ElementId before) {
					if (isTestingAdd) {
						theAddKey.accept(value);
						return StdMsg.UNSUPPORTED_OPERATION;
					}
					return null;
				}

				@Override
				public String canRemove(CollectionElement<KS> element) {
					return null;
				}

				@Override
				public String isModifiable(CollectionElement<KS> element) {
					return null;
				}

				@Override
				public String isAcceptable(CollectionElement<KS> element, KS newValue) {
					return null;
				}

				@Override
				public String canMove(CollectionElement<KS> element, ElementId after, ElementId before) {
					return null;
				}
			}, null).flow()).collectActive(until);
			theEntries = new ArrayList<>();
			theListeners = ListenerList.build().build();
			theValueSize = new AtomicInteger();
			isEventing = new AtomicInteger();
			Subscription keySub = theKeySet.subscribe(evt -> {
				isEventing.getAndIncrement();
				try {
					ActiveEntry entry;
					switch (evt.getType()) {
					case add:
						ElementId sourceKey = theKeySet.getSourceElements(evt.getElementId(), theFlow.getSource().keySet()).getFirst();
						entry = new ActiveEntry(theKeySet.getElement(evt.getElementId()), theFlow.getSource().watchById(sourceKey));
						theEntries.add(evt.getIndex(), entry);
						Subscription entrySub = entry.getValues().subscribe(valueEvt -> {
							isEventing.getAndIncrement();
							try {
								switch (valueEvt.getType()) {
								case add:
									theValueSize.getAndIncrement();
									break;
								case remove:
									theValueSize.getAndDecrement();
									break;
								default:
									break;
								}
								ObservableMultiMapEvent<KT, VT> mapEvt = new ObservableMultiMapEvent.Default<>(//
									entry.getElementId(), valueEvt.getElementId(), entry.getElementsBefore(), valueEvt.getIndex(), //
									valueEvt.getType(), entry.getKey(), entry.getKey(), valueEvt.getOldValue(), valueEvt.getNewValue(),
									valueEvt);
								try (Transaction evtT = mapEvt.use()) {
									theListeners.forEach(//
										l -> l.accept(mapEvt));
								}
							} finally {
								isEventing.getAndDecrement();
							}
						}, true);
						entry.getUntil().take(1).act0(entrySub::unsubscribe);
						break;
					case remove:
						entry = theEntries.remove(evt.getIndex());
						if (!theListeners.isEmpty() && !entry.getValues().isEmpty()) {
							try (Transaction t = entry.getValues().lock(false, null)) {
								int valueIdx = entry.getValues().size() - 1;
								for (CollectionElement<VT> valueEl : entry.getValues().elements().reverse()) {
									ObservableMultiMapEvent<KT, VT> mapEvt = new ObservableMultiMapEvent.Default<>(//
										entry.getElementId(), valueEl.getElementId(), evt.getIndex(), valueIdx, evt.getType(), //
										entry.getKey(), entry.getKey(), valueEl.get(), valueEl.get(), evt);
									try (Transaction evtT = mapEvt.use()) {
										theListeners.forEach(//
											l -> l.accept(mapEvt));
									}
									valueIdx--;
								}
							}
						}
						entry.destroy();
						break;
					case set:
						entry = theEntries.get(evt.getIndex());
						ObservableMultiMapEvent<KT, VT> mapEvt = new ObservableMultiMapEvent.Default<>(//
							entry.getElementId(), null, evt.getIndex(), -1, evt.getType(), //
							evt.getOldValue(), evt.getNewValue(), null, null, evt);
						try (Transaction evtT = mapEvt.use()) {
							theListeners.forEach(//
								l -> l.accept(mapEvt));
						}
						break;
					}
				} finally {
					isEventing.getAndDecrement();
				}
			}, true);
			if (until != null) {
				until.act(__ -> {
					keySub.unsubscribe();
					for (ActiveEntry entry : theEntries)
						entry.destroy();
				});
			}
			theAddValues = new ArrayList<>();
			theValueTester = theFlow.getValueFlow().apply(ModControlledObservableCollection.controlSet(//
				ObservableSortedSet.<VS> create((o1, o2) -> 0), //
				new ModControlledCollection.CollectionModificationControl<VS>() {
					@Override
					public String canAdd(VS value, ElementId after, ElementId before) {
						theAddValues.add(value);
						return null;
					}

					@Override
					public String canRemove(CollectionElement<VS> element) {
						return null;
					}

					@Override
					public String isModifiable(CollectionElement<VS> element) {
						return null;
					}

					@Override
					public String isAcceptable(CollectionElement<VS> element, VS newValue) {
						return null;
					}

					@Override
					public String canMove(CollectionElement<VS> element, ElementId after, ElementId before) {
						return null;
					}
				}, null).flow()).collectActive(until);
		}

		@Override
		protected Object createIdentity() {
			return theFlow;
		}

		@Override
		public ObservableMultiMap<KT, VT> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return isEventing.get() > 0;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return IterableUtils.concat(theKeySet.getCurrentCauses(),
				theEntries.stream().flatMap(e -> e.getValues().getCurrentCauses().stream()).collect(Collectors.toSet()));
		}

		@Override
		public boolean isLockSupported() {
			if (!theKeySet.isLockSupported())
				return false;
			for (ActiveEntry entry : theEntries) {
				if (!entry.getValues().isLockSupported())
					return false;
			}
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theKeySet, write, cause), () -> theEntries,
				e -> Lockable.lockable(e.getValues(), write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theKeySet, write, cause), () -> theEntries,
				e -> Lockable.lockable(e.getValues(), write, cause));
		}

		@Override
		public CoreId getCoreId() {
			CoreId core = theKeySet.getCoreId();
			for (ActiveEntry entry : theEntries)
				core = core.and(entry.getValues().getCoreId());
			return core;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			ThreadConstraint thread = theKeySet.getThreadConstraint();
			for (ActiveEntry entry : theEntries)
				thread = ThreadConstraint.union(thread, entry.getValues().getThreadConstraint());
			return thread;
		}

		@Override
		public long getStamp() {
			return Stamped.compositeOf2Stamps(theKeySet.getStamp(), Stamped.compositeStamp(theEntries, e -> e.getValues().getStamp()));
		}

		@Override
		public int valueSize() {
			return theValueSize.get();
		}

		@Override
		public boolean clear() {
			try (Transaction t = theKeySet.lock(true, null)) {
				int preVS = valueSize();
				theKeySet.clear();
				for (ActiveEntry entry : theEntries)
					entry.getValues().clear();
				return valueSize() < preVS;
			}
		}

		@Override
		public ObservableSet<KT> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableMultiEntry<KT, VT> watchById(ElementId keyId) {
			return watch(theKeySet.getElement(keyId).get());
		}

		@Override
		public ObservableMultiEntry<KT, VT> watch(KT key) {
			return new ObservableEntry(key, theKeySet.observeElement(key, true));
		}

		@Override
		public OrderedMultiEntry<KT, VT> getEntryById(ElementId keyId) {
			return theEntries.get(theKeySet.getElement(keyId).getElementsBefore());
		}

		@Override
		public OrderedMultiEntry<KT, VT> getOrPutEntry(KT key, Function<? super KT, ? extends Iterable<? extends VT>> value,
			ElementId afterKey, ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			long stamp = theKeySet.getStamp();
			OrderedMultiEntry<KT, VT> entry = getEntry(key);
			if (entry != null)
				return entry;
			try (Transaction t = theKeySet.lock(true, null)) {
				if (stamp != theKeySet.getStamp()) { // It might have been added while we were waiting for the lock
					entry = getEntry(key);
					if (entry != null)
						return entry;
				}
				isTestingAdd = true;
				theKeySet.canAdd(key);
				isTestingAdd = false;
				if (!theAddKey.isPresent())
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				KS sourceKey = theAddKey.getAndClear();
				ElementId sourceAfter = afterKey == null ? null
					: theKeySet.getSourceElements(afterKey, theFlow.getSource().keySet()).peekFirst();
				ElementId sourceBefore = beforeKey == null ? null
					: theKeySet.getSourceElements(beforeKey, theFlow.getSource().keySet()).peekFirst();
				for (VT v : value.apply(key))
					theValueTester.canAdd(v);
				OrderedMultiEntry<KS, VS> sourceEntry = theFlow.getSource().getOrPutEntry(sourceKey, __ -> theAddValues, sourceAfter,
					sourceBefore, first, preAdd, postAdd);
				if (sourceEntry == null)
					return null;
				ListElement<KT> keyEl = (ListElement<KT>) theKeySet
					.getElementsBySource(sourceEntry.getElementId(), theFlow.getSource().keySet()).peekFirst();
				return keyEl == null ? null : theEntries.get(keyEl.getElementsBefore());
			} finally {
				theAddValues.clear();
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends KT, ? extends VT>> action) {
			return theListeners.add(action, true);
		}

		@Override
		public MultiMapFlow<KT, VT> flow() {
			return theFlow;
		}

		class ObservableEntry extends ObservableCollectionWrapper<VT> implements ObservableMultiEntry<KT, VT> {
			private final KT theKey;
			private final ObservableElement<KT> theElement;

			ObservableEntry(KT key, ObservableElement<KT> element) {
				theKey = key;
				theElement = element;
				init(ObservableCollection.flattenValue(theElement.map(__ -> {
					ElementId keyId = theElement.getElementId();
					if (keyId == null)
						return null;
					return theEntries.get(theKeySet.getElement(keyId).getElementsBefore()).getValues();
				})));
			}

			@Override
			public KT getKey() {
				return theKey;
			}

			@Override
			public ElementId getKeyId() {
				return theElement.getElementId();
			}

			@Override
			public String toString() {
				return theKey + "=" + super.toString();
			}
		}
	}

	/**
	 * Default active sorted multi-map produced by a {@link GeneralMultiMapFlow}
	 *
	 * @param <KS> The key type of the source multi-map
	 * @param <KT> The key type of the derived multi-map
	 * @param <VS> The value type of the source multi-map
	 * @param <VT> The value type of the derived multi-map
	 */
	public static class GeneralActiveDerivedSortedMultiMap<KS, KT, VS, VT> extends GeneralActiveDerivedMultiMap<KS, KT, VS, VT>
	implements ObservableSortedMultiMap<KT, VT> {
		/**
		 * @param flow The flow that produce this map
		 * @param until The observable to destroy this multi-map
		 */
		public GeneralActiveDerivedSortedMultiMap(GeneralSortedMultiMapFlow<KS, KT, VS, VT> flow, Observable<?> until) {
			super(flow, until);
		}

		@Override
		public ObservableSortedMultiMap<KT, VT> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSortedSet<KT> keySet() {
			return (ObservableSortedSet<KT>) super.keySet();
		}

		@Override
		public SortedMultiMapFlow<KT, VT> flow() {
			return (SortedMultiMapFlow<KT, VT>) super.flow();
		}
	}
}

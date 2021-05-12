package org.observe.supertest.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionTester;
import org.observe.supertest.AbstractChainLink;
import org.observe.supertest.ChainLinkGenerator.CollectionLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.BaseCollectionLink;
import org.observe.supertest.collect.CollectionLinkElement;
import org.observe.supertest.collect.CollectionSourcedLink;
import org.observe.supertest.collect.ExpectedCollectionOperation;
import org.observe.supertest.collect.ObservableCollectionLink;
import org.observe.supertest.collect.ObservableCollectionTestDef;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.Transactable;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * Base class for {@link ObservableMultiMap} testing
 *
 * @param <S> The type of the source link, if any
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public abstract class ObservableMultiMapLink<S, K, V> extends AbstractChainLink<S, V> {
	/** Generates collection links containing all the values associated with a random key in an {@link ObservableMultiMapLink} */
	public static final CollectionLinkGenerator VALUE_GENERATE = new CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableMultiMapLink) || (targetType != null && sourceLink.getType() != targetType))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return deriveValues(path, (ObservableMultiMapLink<?, ?, X>) sourceLink, helper);
		}

		private <T, K, V> ObservableCollectionLink<T, V> deriveValues(String path, ObservableMultiMapLink<?, K, V> mapLink,
			TestHelper helper) {
			K key = mapLink.getKeySupplier().apply(helper);
			return (ObservableCollectionLink<T, V>) mapLink.getValues(path, key);
		}
	};

	private final ObservableMultiMapTestDef<K, V> theDef;
	private final ObservableMultiMap<K, V> theMap;
	private final ObservableMultiMap<K, V> theMultiStepMap;
	private final KeySetWrappingLink theKeySet;
	private final ObservableCollectionLink<?, K> theKeyTracker;
	private final Function<? super K, ObservableCollectionLink<?, V>> theValuesProducer;

	private Function<TestHelper, K> theKeySupplier;
	private Function<TestHelper, V> theValueSupplier;

	private final BetterMap<K, EntryLink> theEntryTracker;
	private final BetterMap<K, List<V>> theMultiStepSync;
	private final ObservableCollectionTester<MultiEntryHandle<K, V>> theEntryTester;

	public ObservableMultiMapLink(String path, ObservableChainLink<?, S> sourceLink, ObservableMultiMapTestDef<K, V> def,
		Function<ObservableMultiMapLink<S, K, V>, ObservableCollectionLink<?, K>> keySource,
		Function<? super K, ObservableCollectionLink<?, V>> valuesProducer, boolean useFirstKey, TestHelper helper) {
		super(path, sourceLink);
		theDef = def;

		boolean passive = def.oneStepFlow.supportsPassive() && helper.getBoolean();
		if (passive)
			theMap = def.oneStepFlow.gatherPassive();
		else
			theMap = def.oneStepFlow.gatherActive(Observable.empty);
		if (passive && def.multiStepFlow.supportsPassive())
			theMultiStepMap = def.multiStepFlow.gatherPassive();
		else
			theMultiStepMap = def.multiStepFlow.gatherActive(Observable.empty);

		theKeySupplier = (Function<TestHelper, K>) ObservableChainTester.SUPPLIERS.get(def.keyType);
		theValueSupplier = (Function<TestHelper, V>) ObservableChainTester.SUPPLIERS.get(def.valueType);

		theKeyTracker = new BaseCollectionLink<>(path + ".keyTracker", new ObservableCollectionTestDef<>(//
			def.keyType, theMap.keySet().flow(), theMap.keySet().flow(), def.orderImportant, def.checkOldValues),
			theMap.keySet(), helper);
		theValuesProducer = valuesProducer;
		theKeySet = new KeySetWrappingLink(keySource.apply(this));

		theEntryTracker = theKeySet.getCollection().equivalence().createMap();

		((List<ObservableCollectionLink<?, ?>>) getDerivedLinks()).add(theKeySet);

		theMultiStepSync = theMap.keySet().equivalence().createMap();
		theEntryTester = new ObservableCollectionTester<>(path + ".entries", theMap.entrySet());
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);

		// TODO Populate random initial values

		theKeySet.initialize(helper);
		theMultiStepMap.subscribe(evt -> {
			ElementId after, before;
			if (evt.getKeyIndex() == theMultiStepSync.keySet().size()) {
				after = CollectionElement.getElementId(theMultiStepSync.keySet().getTerminalElement(false));
				before = after == null ? null : CollectionElement.getElementId(theMultiStepSync.keySet().getAdjacentElement(after, true));
			} else {
				before = ((BetterList<K>) theMultiStepSync.keySet()).getElement(evt.getKeyIndex()).getElementId();
				after = CollectionElement.getElementId(theMultiStepSync.keySet().getAdjacentElement(before, false));
			}
			MapEntryHandle<K, List<V>> entry = theMultiStepSync.getOrPutEntry(evt.getKey(), k -> new ArrayList<>(), after, before, false,
				null);
			switch (evt.getType()) {
			case add:
				entry.getValue().add(evt.getIndex(), evt.getNewValue());
				break;
			case remove:
				V oldValue = entry.getValue().remove(evt.getIndex());
				if (getMapDef().checkOldValues && !Objects.equals(oldValue, evt.getOldValue()))
					Assert.assertEquals("Old values do not match", oldValue, evt.getOldValue());
				if (entry.getValue().isEmpty())
					theMultiStepSync.mutableEntry(entry.getElementId()).remove();
				break;
			case set:
				oldValue = entry.getValue().set(evt.getIndex(), evt.getNewValue());
				if (getMapDef().checkOldValues && !Objects.equals(oldValue, evt.getOldValue()))
					Assert.assertEquals("Old values do not match", oldValue, evt.getOldValue());
				break;
			}
		}, true, true);
	}

	@Override
	public List<? extends MultiMapSourcedLink<K, V, ?>> getDerivedLinks() {
		return (List<? extends MultiMapSourcedLink<K, V, ?>>) super.getDerivedLinks();
	}

	@Override
	protected Transactable getLocking() {
		return theMap;
	}

	@Override
	public TestValueType getType() {
		return theDef.valueType;
	}

	/** @return The multi-map test definition for this link */
	public ObservableMultiMapTestDef<K, V> getMapDef() {
		return theDef;
	}

	/** @return The multi-map this link is testing */
	public ObservableMultiMap<K, V> getMultiMap() {
		return theMap;
	}

	/** @return Random key producer for this link */
	public Function<TestHelper, K> getKeySupplier() {
		return theKeySupplier;
	}

	/** @return Random value producer for this link */
	public Function<TestHelper, V> getValueSupplier() {
		return theValueSupplier;
	}

	@Override
	public double getModificationAffinity() {
		double aff = 0;
		if (theKeySet != null && theValueSupplier != null)
			aff += 7;
		return aff;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		if (theKeySet != null && theValueSupplier != null) {
			action.or(5, () -> { // General single put
				K key = theKeySupplier.apply(helper);
				V value = theValueSupplier.apply(helper);
				ObservableCollectionLink<?, V> values;
				EntryLink currentEntry = theEntryTracker.get(key);
				boolean tempValues = currentEntry == null;
				if (currentEntry != null)
					values = currentEntry.valueTracker;
				else
					values = theValuesProducer.apply(key);
				try {
					if (helper.isReproducing())
						System.out.print("For key " + key + ": ");
					MultiEntryValueHandle<K, V>[] newEntry = new MultiEntryValueHandle[1];
					values.tryAdd(value, helper, first -> {
						newEntry[0] = theMap.putEntry(key, value, first);
						return values.getCollection().getElement(values.getCollection().getEquivalentElement(newEntry[0].getElementId()));
					});
					if (newEntry[0] != null) {
						if (theMap.getEntryById(newEntry[0].getKeyId()).getValues().size() == 1)
							theKeySet.expectAddToBase(newEntry[0].getKeyId(), key);
						expectAddValue(key, newEntry[0].getElementId(), value);
					}
				} finally {
					if (tempValues)
						values.dispose();
				}
			});
			action.or(2, () -> { // Remove by key/value
				K key = theKeySupplier.apply(helper);
				V value = theValueSupplier.apply(helper);
				ObservableCollectionLink<?, V> values;
				EntryLink currentEntry = theEntryTracker.get(key);
				boolean tempValues = currentEntry == null;
				if (currentEntry != null)
					values = currentEntry.valueTracker;
				else
					values = theValuesProducer.apply(key);
				try {
					System.out.print("For key " + key + ": ");
					values.tryRemove(value, helper, //
						() -> theMap.remove(key, value));
				} finally {
					if (tempValues)
						values.dispose();
				}
			});
		}
	}

	void expectAddValue(K key, ElementId newValueId, V value){
		theEntryTracker.get(key).expectAddToBase(newValueId, value);
		for(ObservableChainLink<V, ?> derived : getDerivedLinks()){
			if(derived instanceof ValuesWrappingLink)
		}
	}

	public ObservableCollectionLink<?, V> getValues(String path, K key) {
		ObservableCollectionLink<?, V> source = theValuesProducer.apply(key);
		return new ValuesWrappingLink(path, source, key);
	}

	@Override
	public void validate(boolean transactionEnd) throws AssertionError {
		theKeySet.validate(transactionEnd);
		theKeyTracker.validate(transactionEnd);

		theEntryTester.check();
		Assert.assertEquals(theMap.keySet().size(), theEntryTester.getSyncedCopy().size());
		List<K> syncedEntryKeys = theEntryTester.getSyncedCopy().stream().map(e -> e.getKey())
			.collect(Collectors.toCollection(() -> new ArrayList<>(theEntryTester.getSyncedCopy().size())));
		Assert.assertThat(syncedEntryKeys, QommonsTestUtils.collectionsEqual(theMap.keySet(), getMapDef().orderImportant));

		// Validate multi-step map
		Assert.assertThat(theMultiStepMap.keySet(), QommonsTestUtils.collectionsEqual(theMap.keySet(), getMapDef().orderImportant));
		Assert.assertThat(theMultiStepSync.keySet(), QommonsTestUtils.collectionsEqual(theMap.keySet(), getMapDef().orderImportant));
		for (MultiMap.MultiEntry<K, V> entry : theMap.entrySet()) {
			Assert.assertThat(theMultiStepMap.get(entry.getKey()), //
				QommonsTestUtils.collectionsEqual(entry.getValues(), getMapDef().orderImportant));
			Assert.assertThat(theMultiStepSync.get(entry.getKey()), //
				QommonsTestUtils.collectionsEqual(entry.getValues(), getMapDef().orderImportant));
		}
		MapEntryHandle<K, EntryLink> entry = theEntryTracker.getTerminalEntry(true);
		while (entry != null) {
			if (entry.getValue().valueTracker.getCollection().isEmpty()) {
				entry.getValue().valueTracker.dispose();
				theEntryTracker.mutableEntry(entry.getElementId()).remove();
			}

			entry = theEntryTracker.getAdjacentEntry(entry.getElementId(), true);
		}
	}

	@Override
	public String printValue() {
		return theMap.toString();
	}

	EntryLink getOrCreateEntry(K key, CollectionLinkElement<?, K> keySetElement) {
		MapEntryHandle<K, EntryLink> entry = theEntryTracker.getOrPutEntry(key, k -> new EntryLink(k, keySetElement), null, null, false,
			null);
		entry.getValue().setId(entry.getElementId());
		return entry.getValue();
	}

	class EntryLink {
		ElementId theId;
		final CollectionLinkElement<?, K> keySetElement;
		final CollectionLinkElement<?, K> keyTrackElement;
		final ValuesWrappingLink valueTracker;

		private EntryLink(K key, CollectionLinkElement<?, K> keySetElement) {
			keySetElement.withCustomData(this);
			this.keySetElement = keySetElement;
			keySetElement.expectAdded(key);
			CollectionLinkElement<?, K> tracked = null;
			for (CollectionLinkElement<?, K> keyEl : theKeyTracker.getElements()) {
				if (keyEl.isPresent() && getMultiMap().keySet().equivalence().elementEquals(key, keyEl.getCollectionValue())) {
					tracked = keyEl.expectAdded(keySetElement.getValue());
					break;
				}
			}
			if (tracked == null)
				throw new AssertionError(getPath() + ": " + toString() + ": Key " + key + " not added to key set");
			keyTrackElement = tracked;
			keyTrackElement.expectAdded(key);
			valueTracker = createValuesTracker(this);
		}

		void setId(ElementId id) {
			theId = id;
		}

		K getKey() {
			return keySetElement.getValue();
		}

		void removed() {
			keySetElement.expectRemoval();
			keyTrackElement.expectRemoval();
		}

		void updated(K newKey) {
			keySetElement.expectSet(newKey);
			keyTrackElement.expectSet(newKey);
			// No effect on keys
		}
	}

	class KeySetWrappingLink extends ObservableCollectionLink<K, K> implements CollectionSourcedLink<K, K> {
		public KeySetWrappingLink(ObservableCollectionLink<?, K> sourceLink) {
			super(ObservableMultiMapLink.this.getPath() + "[keys]", sourceLink, //
				new ObservableCollectionTestDef<>(getMapDef().keyType, sourceLink.getCollection().flow(), sourceLink.getDef().multiStepFlow,
					getMapDef().orderImportant, getMapDef().checkOldValues),
				null);
			sourceLink.getDerivedLinks().add(this);
			sourceLink.initialize(null);
			theKeyTracker.initialize(null);
			initialize(null);
		}

		@Override
		public ObservableCollectionLink<?, K> getSourceLink() {
			return (ObservableCollectionLink<?, K>) super.getSourceLink();
		}

		@Override
		public K getUpdateValue(CollectionLinkElement<K, K> element, K value) {
			return ((ObservableCollectionLink<Object, K>) getSourceLink())
				.getUpdateValue((CollectionLinkElement<Object, K>) element.getFirstSource(), value);
		}

		@Override
		public boolean isAcceptable(K value) {
			return getSourceLink().isAcceptable(value);
		}

		EntryLink entry(CollectionLinkElement<?, K> element) {
			return element.getCustomData();
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, K> sourceOp) {
			CollectionLinkElement<K, K> derivedEl = (CollectionLinkElement<K, K>) sourceOp.getElement()
				.getDerivedElements(getSiblingIndex()).getFirst();
			switch (sourceOp.getType()) {
			case move:
				throw new IllegalStateException();
			case add:
				getOrCreateEntry(sourceOp.getValue(), derivedEl);
				break;
			case remove:
				entry(derivedEl).removed();
				break;
			case set:
				entry(derivedEl).updated(sourceOp.getValue());
				break;
			}
		}

		@Override
		public CollectionLinkElement<K, K> expectAdd(K value, CollectionLinkElement<?, K> after, CollectionLinkElement<?, K> before,
			boolean first, OperationRejection rejection, boolean execute) {
			// Can't add a key with no values
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
			// CollectionLinkElement<?, K> sourceAdded = getSourceLink().expectAdd(value, //
			// after == null ? null : (CollectionLinkElement<?, K>) after.getFirstSource(), //
			// before == null ? null : (CollectionLinkElement<?, K>) before.getFirstSource(), //
			// first, rejection, execute);
			// return sourceAdded == null ? null : (CollectionLinkElement<K, K>)
			// sourceAdded.getDerivedElements(getSiblingIndex()).getFirst();
		}

		@Override
		public CollectionLinkElement<K, K> expectMove(CollectionLinkElement<?, K> source, CollectionLinkElement<?, K> after,
			CollectionLinkElement<?, K> before, boolean first, OperationRejection rejection, boolean execute) {
			CollectionLinkElement<?, K> sourceMoved = getSourceLink().expectMove((CollectionLinkElement<?, K>) source.getFirstSource(), //
				after == null ? null : (CollectionLinkElement<?, K>) after.getFirstSource(), //
					before == null ? null : (CollectionLinkElement<?, K>) before.getFirstSource(), //
						first, rejection, execute);
			if (sourceMoved == null)
				return null;
			else if (source.getFirstSource() == sourceMoved)
				return (CollectionLinkElement<K, K>) source; // No actual movement
			CollectionLinkElement<K, K> derived = (CollectionLinkElement<K, K>) sourceMoved.getDerivedElements(getSiblingIndex())
				.getFirst();
			return derived;
		}

		@Override
		public void expect(ExpectedCollectionOperation<?, K> derivedOp, OperationRejection rejection, boolean execute) {
			getSourceLink().expect(//
				new ExpectedCollectionOperation<>((CollectionLinkElement<?, K>) derivedOp.getElement().getFirstSource(), //
					derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()),
				rejection, execute);
		}

		@Override
		protected void validate(CollectionLinkElement<K, K> element, boolean transactionEnd) {
			entry(element).valueTracker.validate(transactionEnd);
		}

		@Override
		public String toString() {
			return "keySet()";
		}
	}

	ValuesWrappingLink createValuesTracker(EntryLink entry) {
		K key = entry.getKey();
		ObservableCollectionLink<?, V> source = theValuesProducer.apply(key);
		String path = ObservableMultiMapLink.this.getPath() + ".values(" + key + ")";
		return new ValuesWrappingLink(path, source, key);
	}

	class ValuesWrappingLink extends ObservableCollectionLink<V, V> {
		private final K theKey;
		private final ObservableCollectionLink<V, V> theBaseValues;

		public ValuesWrappingLink(String path, ObservableCollectionLink<?, V> sourceLink, K key) {
			super(path, sourceLink, //
				new ObservableCollectionTestDef<>(getMapDef().valueType, sourceLink.getCollection().flow(),
					sourceLink.getDef().multiStepFlow, getMapDef().orderImportant, getMapDef().checkOldValues),
				null);
			theKey = key;
			sourceLink.getDerivedLinks().add(this);
			ObservableCollection<V> values = theMap.get(key);
			theBaseValues = new BaseCollectionLink<>(ObservableMultiMapLink.this.getPath() + ".valueTracker(" + key + ")",
				new ObservableCollectionTestDef<>(getMapDef().valueType, values.flow(), values.flow(), getMapDef().orderImportant,
					getMapDef().checkOldValues),
				values, null);
			getSourceLink().initialize(null);
			initialize(null);
			theBaseValues.initialize(null);
		}

		@Override
		public void expectFromSource(ExpectedCollectionOperation<?, V> sourceOp) {
			CollectionLinkElement<V, V> derivedEl = (CollectionLinkElement<V, V>) sourceOp.getElement()
				.getDerivedElements(getSiblingIndex()).getFirst();
			switch (sourceOp.getType()) {
			case move:
				throw new IllegalStateException();
			case add:
				ElementId baseEquivalent = theBaseValues.getCollection().getEquivalentElement(sourceOp.getElement().getCollectionAddress());
				CollectionLinkElement<V, V> baseEl = theBaseValues.getElement(baseEquivalent);
				derivedEl.expectAdded(sourceOp.getValue()).withCustomData(baseEl.expectAdded(sourceOp.getValue()));
				break;
			case remove:
				derivedEl.expectRemoval();
				((CollectionLinkElement<V, V>) derivedEl.getCustomData()).expectRemoval();
				break;
			case set:
				derivedEl.expectSet(sourceOp.getValue());
				((CollectionLinkElement<V, V>) derivedEl.getCustomData()).expectSet(sourceOp.getValue());
				break;
			}
		}

		@Override
		public boolean isAcceptable(V value) {
			return getSourceLink().isAcceptable(value);
		}

		@Override
		public V getUpdateValue(CollectionLinkElement<V, V> element, V value) {
			return ((ObservableCollectionLink<Object, V>) getSourceLink())
				.getUpdateValue((CollectionLinkElement<Object, V>) element.getFirstSource(), value);
		}

		@Override
		public CollectionLinkElement<V, V> expectAdd(V value, CollectionLinkElement<?, V> after, CollectionLinkElement<?, V> before,
			boolean first, OperationRejection rejection, boolean execute) {
			CollectionLinkElement<?, V> sourceAdded = getSourceLink().expectAdd(value, //
				after == null ? null : (CollectionLinkElement<?, V>) after.getFirstSource(), //
					before == null ? null : (CollectionLinkElement<?, V>) before.getFirstSource(), //
						first, rejection, execute);
			if (sourceAdded == null)
				return null;
			return (CollectionLinkElement<V, V>) sourceAdded.getDerivedElements(getSiblingIndex()).getFirst();
		}

		@Override
		public CollectionLinkElement<V, V> expectMove(CollectionLinkElement<?, V> source, CollectionLinkElement<?, V> after,
			CollectionLinkElement<?, V> before, boolean first, OperationRejection rejection, boolean execute) {
			CollectionLinkElement<?, V> sourceAdded = getSourceLink().expectMove((CollectionLinkElement<V, V>) source.getFirstSource(), //
				after == null ? null : (CollectionLinkElement<?, V>) after.getFirstSource(), //
					before == null ? null : (CollectionLinkElement<?, V>) before.getFirstSource(), //
						first, rejection, execute);
			if (sourceAdded == null)
				return null;
			return (CollectionLinkElement<V, V>) sourceAdded.getDerivedElements(getSiblingIndex()).getFirst();
		}

		@Override
		public void expect(ExpectedCollectionOperation<?, V> derivedOp, OperationRejection rejection, boolean execute) {
			getSourceLink().expect(new ExpectedCollectionOperation<>((CollectionLinkElement<?, V>) derivedOp.getElement().getFirstSource(),
				derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection, execute);
		}

		@Override
		protected void validate(CollectionLinkElement<V, V> element, boolean transactionEnd) {}

		@Override
		public void validate(boolean transactionEnd) throws AssertionError {
			super.validate(transactionEnd);
			theBaseValues.validate(transactionEnd);
		}

		@Override
		public void dispose() {
			super.dispose();
			theBaseValues.dispose();
		}

		@Override
		public String toString() {
			return "values(" + theKey + ")";
		}
	}
}

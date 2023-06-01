package org.observe.supertest.collect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.util.TypeTokens;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection#flattenValue(org.observe.ObservableValue)}
 *
 * @param <T> The type of the collection
 */
public class FlattenedValueBaseCollectionLink<T> extends BaseCollectionLink<T> {
	/** Generates a root {@link FlattenedValueBaseCollectionLink} */
	public static final ChainLinkGenerator GENERATE_FLATTENED = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (sourceLink != null)
				return 0;
			return 0.1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			int collectionCount = helper.getInt(2, 4);
			List<ObservableCollection<X>> collections = new ArrayList<>(collectionCount);
			TestValueType type = targetType != null ? targetType : nextType(helper);
			TypeToken<X> collectionType = (TypeToken<X>) type.getType();
			for (int i = 0; i < collectionCount; i++)
				collections.add(ObservableCollection.build(collectionType).build());
			SettableValue<ObservableCollection<X>> collectionValue = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<X>> parameterized(collectionType))
				.build();
			int collectionIdx = getRandomCollectionIndex(collections.size(), helper);
			ObservableCollection<X> initCollection = collectionIdx == collections.size() ? null : collections.get(collectionIdx);
			collectionValue.set(initCollection, null);

			// Populate the collections (except the initial collection--that is populated by the collection link)
			Function<TestHelper, X> supplier = (Function<TestHelper, X>) ObservableChainTester.SUPPLIERS.get(type);
			if (supplier != null) {
				for (ObservableCollection<X> coll : collections) {
					if (coll == initCollection)
						continue;
					if (helper.getBoolean(.25)) {
						int size = helper.getInt(0, 10);
						for (int i = 0; i < size; i++)
							coll.add(supplier.apply(helper));
					}
				}
			}

			ObservableCollection<X> flatCollection = ObservableCollection.flattenValue(collectionValue);
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, flatCollection.flow(), flatCollection.flow(), true,
				true);
			return (ObservableCollectionLink<T, X>) new FlattenedValueBaseCollectionLink<>(path, def, helper, collections, collectionValue);
		}
	};

	private final List<ObservableCollection<T>> theCollections;
	private final SettableValue<ObservableCollection<T>> theCollectionValue;

	/**
	 * @param path The path for the link (generally "root")
	 * @param def The collection definition for the link
	 * @param helper The randomness to use to initialize the link
	 * @param collections The collections to toggle between
	 * @param collectionValue The value containing the active collection
	 */
	public FlattenedValueBaseCollectionLink(String path, ObservableCollectionTestDef<T> def, TestHelper helper,
		List<ObservableCollection<T>> collections, SettableValue<ObservableCollection<T>> collectionValue) {
		super(path, def, helper);
		theCollections = collections;
		theCollectionValue = collectionValue;
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(2, () -> {
			int collectionIdx = getRandomCollectionIndex(theCollections.size(), helper);
			ObservableCollection<T> oldCollection = theCollectionValue.get();
			ObservableCollection<T> newCollection = collectionIdx == theCollections.size() ? null : theCollections.get(collectionIdx);
			if (helper.isReproducing())
				System.out.println("Replacing " + (oldCollection == null ? "" : ("" + oldCollection.size())) + oldCollection//
					+ " with " + (newCollection == null ? "" : ("" + newCollection.size())) + newCollection);
			theCollectionValue.set(newCollection, null);
			expectCollectionChange(oldCollection, newCollection);
		});
	}

	private void expectCollectionChange(ObservableCollection<T> oldCollection, ObservableCollection<T> newCollection) {
		if (oldCollection == newCollection)
			return;
		Iterator<CollectionLinkElement<T, T>> elements = getElements().iterator();
		if (oldCollection != null) {
			for (@SuppressWarnings("unused")
			T value : oldCollection) {
				CollectionLinkElement<T, T> element = elements.next();
				element.expectRemoval();
			}
		}
		if (newCollection != null) {
			for (T value : newCollection) {
				CollectionLinkElement<T, T> element = elements.next();
				element.expectAdded(value);
			}
		}
	}

	@Override
	public boolean isAcceptable(T value) {
		return theCollectionValue.get() != null;
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		if (theCollectionValue.get() == null) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection, execute);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (theCollectionValue.get() == null) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return;
		}
		super.expect(derivedOp, rejection, execute);
	}

	@Override
	public String toString() {
		return "flatBase(" + getType() + ")";
	}

	static int getRandomCollectionIndex(int collectionCount, TestHelper helper) {
		double index = helper.getDouble(0, collectionCount + .1);
		return (int) index;
	}
}

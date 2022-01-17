package org.observe.supertest.map;

import java.util.Comparator;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.BaseCollectionLink;
import org.observe.supertest.collect.DistinctCollectionLink;
import org.observe.supertest.collect.ObservableCollectionLink;
import org.observe.supertest.collect.ObservableCollectionTestDef;
import org.observe.supertest.collect.SortedBaseCollectionLink;
import org.observe.supertest.collect.SortedCollectionLink;
import org.qommons.TestHelper;

import com.google.common.reflect.TypeToken;

/**
 * Base multi-map link
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class BaseMultiMapLink<K, V> extends ObservableMultiMapLink<V, K, V> {
	/** Generates {@link BaseMultiMapLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (sourceLink != null)
				return 0;
			return 0.1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			return (ObservableChainLink<T, X>) deriveMap(path, targetType, helper);
		}

		private <K, V> ObservableMultiMapLink<V, K, V> deriveMap(String path, TestValueType targetType, TestHelper helper) {
			TestValueType keyType = BaseCollectionLink.nextType(helper);
			TestValueType valueType = targetType != null ? targetType : BaseCollectionLink.nextType(helper);
			Comparator<? super K> keySorting = helper.getBoolean() ? SortedCollectionLink.compare(keyType, helper) : null;
			ObservableMultiMap.Builder<K, V, ?> builder = ObservableMultiMap.build((TypeToken<K>) keyType.getType(),
				(TypeToken<V>) valueType.getType());
			if (keySorting != null)
				builder = builder.sortedBy(keySorting);
			ObservableMultiMap<K, V> map = builder.build(Observable.empty());
			ObservableMultiMapTestDef<K, V> def = new ObservableMultiMapTestDef<>(keyType, valueType, map.flow(), map.flow(),
				keySorting != null, true);
			Function<ObservableMultiMapLink<V, K, V>, ObservableCollectionLink<?, K>> keySource = link -> {
				ObservableCollection.CollectionDataFlow<?, ?, K> keyFlow = map.keySet().flow();
				ObservableCollectionTestDef<K> keyDef = new ObservableCollectionTestDef<>(keyType, keyFlow, keyFlow, keySorting != null,
					true);
				if (keySorting != null)
					return new SortedBaseCollectionLink<>(path + ".keys", keyDef, keySorting, true, null);
				else {
					BaseCollectionLink<K> base = new BaseCollectionLink<>(path + ".keyBase", keyDef, null);
					DistinctCollectionLink<K> distinct = new DistinctCollectionLink<>(path + ".keys", base, keyDef, null, false, null);
					base.getDerivedLinks().add(distinct);
					return distinct;
				}
			};
			Function<K, ObservableCollectionLink<?, V>> valuesProducer = key -> {
				ObservableCollection.CollectionDataFlow<?, ?, V> valueFlow = map.get(key).flow();
				ObservableCollectionTestDef<V> valueDef = new ObservableCollectionTestDef<>(valueType, valueFlow, valueFlow, true, true);
				return new BaseCollectionLink<>(path + ".valueBase(" + key + ")", valueDef, null);
			};
			return new BaseMultiMapLink<>(path, def, keySource, valuesProducer, false, helper);
		}
	};

	private int theModificationSet;
	private int theModification;
	private int theOverallModification;

	public BaseMultiMapLink(String path, ObservableMultiMapTestDef<K, V> def,
		Function<ObservableMultiMapLink<V, K, V>, ObservableCollectionLink<?, K>> keySource,
		Function<? super K, ObservableCollectionLink<?, V>> valuesProducer, boolean useFirstKey, TestHelper helper) {
		super(path, null, def, keySource, valuesProducer, useFirstKey, helper);
	}

	@Override
	public int getModSet() {
		return theModificationSet;
	}

	@Override
	public int getModification() {
		return theModification;
	}

	@Override
	public int getOverallModification() {
		return theOverallModification;
	}

	@Override
	public void setModification(int modSet, int modification, int overall) {
		theModificationSet = modSet;
		theModification = modification;
		theOverallModification = overall;
	}

	@Override
	public String toString() {
		return "base" + (getMultiMap() instanceof ObservableSortedMultiMap ? "Sorted" : "") + "MultiMap("//
			+ getMapDef().keyType + ", " + getMapDef().valueType + ")";
	}
}

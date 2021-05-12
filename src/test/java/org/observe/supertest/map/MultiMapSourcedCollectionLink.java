package org.observe.supertest.map;

import org.observe.collect.ObservableCollection;
import org.observe.supertest.collect.ObservableCollectionLink;
import org.observe.supertest.collect.ObservableCollectionTestDef;
import org.qommons.TestHelper;

public abstract class MultiMapSourcedCollectionLink<K, V, T> extends ObservableCollectionLink<V, T>
	implements MultiMapSourcedLink<K, V, T> {
	public MultiMapSourcedCollectionLink(String path, ObservableCollectionLink<?, V> sourceLink, ObservableCollectionTestDef<T> def,
		ObservableCollection<T> oneStepCollection, ObservableCollection<T> multiStepCollection, TestHelper helper) {
		super(path, sourceLink, def, oneStepCollection, multiStepCollection, helper);
	}

	public MultiMapSourcedCollectionLink(String path, ObservableCollectionLink<?, V> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	@Override
	public ObservableMultiMapLink<?, K, V> getSourceLink() {
		return (ObservableMultiMapLink<?, K, V>) super.getSourceLink();
	}
}

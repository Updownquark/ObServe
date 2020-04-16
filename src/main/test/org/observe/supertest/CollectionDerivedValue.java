package org.observe.supertest;

public abstract class CollectionDerivedValue<S, T> extends ObservableValueLink<S, T> implements CollectionSourcedLink<S, T> {
	public CollectionDerivedValue(String path, ObservableCollectionLink<?, S> sourceLink, TestValueType type) {
		super(path, sourceLink, type);
	}

	@Override
	public ObservableCollectionLink<?, S> getSourceLink() {
		return (ObservableCollectionLink<?, S>) super.getSourceLink();
	}
}

package org.observe.supertest.dev2;

public abstract class CollectionDerivedValue<S, T> extends ObservableValueLink<S, T> implements CollectionSourcedLink<S, T> {
	public CollectionDerivedValue(ObservableCollectionLink<?, S> sourceLink, TestValueType type) {
		super(sourceLink, type);
	}

	@Override
	public ObservableCollectionLink<?, S> getSourceLink() {
		return (ObservableCollectionLink<?, S>) super.getSourceLink();
	}
}

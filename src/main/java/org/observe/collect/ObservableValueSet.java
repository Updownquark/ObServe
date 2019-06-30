package org.observe.collect;

public interface ObservableValueSet<E> {
	ObservableCollection<E> getValues();

	ValueCreator<E> create();
}

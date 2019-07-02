package org.observe.collect;

public interface ObservableValueSet<E> {
	ObservableCollection<? extends E> getValues();

	ValueCreator<E> create();
}

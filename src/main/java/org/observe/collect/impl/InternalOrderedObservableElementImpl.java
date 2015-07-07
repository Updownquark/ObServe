package org.observe.collect.impl;

import prisms.lang.Type;

class InternalOrderedObservableElementImpl<T> extends InternalObservableElementImpl<T> {
	private int theCachedIndex;
	private int theModTracker;
	private boolean isRemoved;

	InternalOrderedObservableElementImpl(Type type, T value) {
		super(type, value);
	}

	int getCachedIndex(int modCount) {
		if(isRemoved || theModTracker == modCount)
			return theCachedIndex;
		else
			return -1;
	}

	void cacheIndex(int index, int modCount) {
		theCachedIndex = index;
		theModTracker = modCount;
	}

	void setRemovedIndex(int index) {
		theCachedIndex = index;
		isRemoved = true;
	}
}

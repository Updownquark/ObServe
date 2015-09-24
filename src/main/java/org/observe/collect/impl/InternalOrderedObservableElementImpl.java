package org.observe.collect.impl;

import com.google.common.reflect.TypeToken;

class InternalOrderedObservableElementImpl<T> extends InternalObservableElementImpl<T> {
	private int theCachedIndex;
	// The mod count on the collections starts at 0, so we don't want to make it look like the cached index is correct from the start
	private int theModTracker = -1;
	private boolean isRemoved;

	InternalOrderedObservableElementImpl(TypeToken<T> type, T value) {
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

package org.observe;

import java.util.function.Consumer;

public interface ObservableCache<T> {
	long getCurrentStamp(boolean hold);

	long update(Consumer<? super T> update, Runnable reset);

	void release(long stamp);

	boolean canUpdate(long stamp);

	int getLimit();

	void close();
}

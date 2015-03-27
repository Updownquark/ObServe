package org.observe.collect;

public interface Transaction extends AutoCloseable {
	@Override
	void close();
}

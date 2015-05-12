package org.observe.util;

/** Represents a set of operations after which the {@link #close()} method must be called */
@FunctionalInterface
public interface Transaction extends AutoCloseable {
	@Override
	void close();
}

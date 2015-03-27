package org.observe;

/**
 * A function that operates on 3 arguments
 * 
 * @param <T> The first argument type
 * @param <U> The second argument type
 * @param <V> The third argument type
 * @param <R> The return type of the function
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @return The product of the function call
	 */
	R apply(T arg1, U arg2, V arg3);
}

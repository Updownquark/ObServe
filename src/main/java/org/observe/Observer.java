package org.observe;

import java.util.function.Supplier;

import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;

/**
 * Listens to an observable
 *
 * @param <T> The super type of observable that this observer may listen to
 */
public interface Observer<T> {
	/** @param value The latest value on the observable */
	void onNext(T value);

	/**
	 * Signals that the observable has no more values
	 *
	 * @param cause A supplier for the cause of the completion. Most observers will not require this value, so callers have the option to
	 *        provide it lazily.
	 */
	void onCompleted(Supplier<Causable> cause);

	/** @return A {@link CompletedCause} that supplies a fresh, root-level {@link Causable} */
	static CompletedCause completion() {
		return new UnlinkedCompletedCause();
	}

	/**
	 * @param cause The cause supplier for the completion
	 * @return A {@link CompletedCause} that supplies a Causable whose {@link Causable#getCauses() cause} is the value supplied by the
	 *         argument
	 */
	static CompletedCause completion(Supplier<?> cause) {
		return new GeneralCompletedCause(cause);
	}

	/**
	 * An {@link Observer} whose {@link Observer#onCompleted(Supplier) onCompleted} method is defaulted to do nothing
	 *
	 * @param <T> The type of value the observer expects
	 */
	@FunctionalInterface
	interface SimpleObserver<T> extends Observer<T> {
		@Override
		default void onCompleted(Supplier<Causable> cause) {
		}
	}

	/**
	 * @param <T> The type of value the observer expects
	 * @param observer The observer implementation
	 * @param toString The {@link Object#toString()} implementation for the lambda
	 * @param identifier The identifier for the lambda
	 * @return The printable observer
	 */
	static <T> SimpleObserver<T> printableObserver(SimpleObserver<T> observer, String toString, Object identifier) {
		return new PrintableObserver<>(observer, toString, identifier);
	}

	/**
	 * @param <T> The type of value the observer expects
	 * @param observer The observer implementation
	 * @param toString The {@link Object#toString()} implementation for the lambda
	 * @param identifier The identifier for the lambda
	 * @return The printable observer
	 */
	static <T> SimpleObserver<T> printableObserver(SimpleObserver<T> observer, Supplier<String> toString, Object identifier) {
		return new PrintableObserver<>(observer, toString, identifier);
	}

	/**
	 * A cause supplier for {@link Observer#onCompleted(Supplier)} that creates a cause dynamically. The {@link #close()} method finishes
	 * the cause if it was requested.
	 */
	public interface CompletedCause extends Supplier<Causable>, Transaction {
	}

	/** A {@link CompletedCause} that creates a fresh, root-level {@link Causable} */
	class UnlinkedCompletedCause implements CompletedCause {
		private Causable.CausableInUse theCausable;

		@Override
		public Causable get() {
			if (theCausable == null)
				theCausable = Causable.cause();
			return theCausable;
		}

		@Override
		public void close() {
			if (theCausable != null) {
				theCausable.close();
				theCausable = null;
			}
		}
	}

	/**
	 * A {@link CompletedCause} that supplies a Causable whose {@link Causable#getCauses() cause} is the value supplied by the constructor
	 * argument
	 */
	class GeneralCompletedCause implements CompletedCause {
		private final Supplier<?> theSupplier;
		private Causable theCausable;
		private Transaction theFinish;

		public GeneralCompletedCause(Supplier<?> supplier) {
			theSupplier = supplier;
		}

		@Override
		public Causable get() {
			if (theCausable == null) {
				if (theSupplier == null) {
					theCausable = Causable.cause();
					theFinish = (Transaction) theCausable;
				} else {
					Object supplied = theSupplier.get();
					if (supplied == null) {
						theCausable = Causable.cause();
						theFinish = (Transaction) theCausable;
					} else if (supplied instanceof Causable) {
						theCausable = (Causable) supplied;
						theFinish = theCausable.use();
					} else {
						theCausable = Causable.cause(supplied);
						theFinish = (Transaction) theCausable;
					}
				}
			}
			return theCausable;
		}

		@Override
		public void close() {
			if (theFinish != null) {
				theFinish.close();
				theFinish = null;
			}
		}
	}

	/**
	 * Implementation for the {@link Observer#printableObserver(SimpleObserver, Supplier, Object)} methods
	 *
	 * @param <T> The type of value the observer expects
	 */
	class PrintableObserver<T> extends LambdaUtils.PrintableLambda<SimpleObserver<T>> implements SimpleObserver<T> {
		public PrintableObserver(SimpleObserver<T> lambda, String print, Object identifier) {
			super(lambda, print, identifier);
		}

		public PrintableObserver(SimpleObserver<T> lambda, Supplier<String> print, Object identifier) {
			super(lambda, print);
		}

		@Override
		public void onNext(T value) {
			getLambda().onNext(value);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}
	}
}

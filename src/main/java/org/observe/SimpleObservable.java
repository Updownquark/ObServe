package org.observe;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * A simple observable that can be controlled directly
 *
 * @param <T> The type of values from this observable
 */
public class SimpleObservable<T> extends LightWeightObservable<T> {
	/** @return A builder for a {@link SimpleObservable} */
	public static Builder build() {
		return new Builder();
	}

	/**
	 * @param <T> The type of observable to create
	 * @param build Configuration for the observable
	 * @return The new observable
	 */
	public static <T> SimpleObservable<T> create(Consumer<Builder> build) {
		Builder builder = build();
		if (build != null)
			build.accept(builder);
		return builder.build();
	}

	/** Builds {@link SimpleObservable}s */
	public static class Builder extends AbstractEventableBuilder<SimpleObservable<?>, Builder> {
		private boolean isInternalState;
		private Object theIdentity;

		Builder() {
			super("observable");
		}

		/**
		 * @param identity The identity for the observable
		 * @return This builder
		 */
		public Builder withIdentity(Object identity) {
			theIdentity = identity;
			return this;
		}

		/** @return The observable */
		public <T> SimpleObservable<T> build() {
			return build(null);
		}

		/**
		 * @param onSubscribe The consumer for each new subscriber to the observable
		 * @return The observable
		 */
		public <T> SimpleObservable<T> build(Consumer<? super Observer<? super T>> onSubscribe) {
			return new SimpleObservable<>(onSubscribe, theIdentity, getDescription(), isInternalState, buildData());
		}
	}

	private final Consumer<? super Observer<? super T>> theOnSubscribe;
	private final Object theIdentity;
	private final Transactable theLock;

	/** Creates a simple observable */
	public SimpleObservable() {
		this(false, false);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param safe Whether this observable is externally thread-safed
	 */
	public SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, boolean internalState, boolean safe) {
		this(onSubscribe, null, null, internalState, safe ? new ReentrantReadWriteLock() : null, null);
	}

	/**
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param safe Whether this observable is externally thread-safed
	 */
	protected SimpleObservable(boolean internalState, boolean safe) {
		this(null, internalState, safe);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param description A description of this observable's purpose
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param lock The lock for this observable
	 * @param listening Listening options for this observable
	 */
	SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, Object identity, String description, boolean internalState,
		ReentrantReadWriteLock lock, ListenerList.Builder listening) {
		this(onSubscribe, identity, description, internalState, o -> Transactable.transactable(lock, o, ThreadConstraint.ANY), listening);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param identity The identity for this observable
	 * @param description A description of this observable's purpose
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param lock The lock for this observable
	 * @param listening Listening options for this observable
	 */
	protected SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, Object identity, String description,
		boolean internalState, Function<Object, Transactable> lock, ListenerList.Builder listening) {
		super((listening == null ? ListenerList.build() : listening).skipAddByDefault(internalState).build());
		theIdentity = identity != null ? identity : Identifiable.baseId(description != null ? description : "observable", this);
		theOnSubscribe = onSubscribe;
		theLock = lock == null ? null : lock.apply(this);
	}

	/**
	 * @param onSubscribe The function to notify when a subscription is added to this observable
	 * @param identity The identity for this observable
	 * @param description A description of this observable's purpose
	 * @param internalState Whether this observable is firing changes for some valued state
	 * @param eventableData The eventable data from the builder
	 */
	protected SimpleObservable(Consumer<? super Observer<? super T>> onSubscribe, Object identity, String description,
		boolean internalState, AbstractEventableBuilder.EventableData<? super SimpleObservable<T>> eventableData) {
		super(obs -> eventableData.getListening((SimpleObservable<T>) obs).skipAddByDefault(internalState).build());
		theIdentity = identity != null ? identity : Identifiable.baseId(description != null ? description : "observable", this);
		theOnSubscribe = onSubscribe;
		theLock = eventableData.getLock(this);
	}

	/** @return This observable's lock */
	protected Transactable getLock() {
		return theLock;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLock == null ? ThreadConstraint.ANY : theLock.getThreadConstraint();
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
	}

	@Override
	public Subscription subscribe(Observer<? super T> observer) {
		Subscription sub = super.subscribe(observer);
		if (theOnSubscribe != null && isAlive())
			theOnSubscribe.accept(observer);
		return sub;
	}

	@Override
	public void onNext(T value) {
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, value)) {
			super.onNext(value);
		}
	}

	@Override
	public void onCompleted(Supplier<Causable> cause) {
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, cause)) {
			super.onCompleted(cause);
		}
	}

	@Override
	public boolean isSafe() {
		return theLock != null;
	}

	/**
	 * Locks this observable exclusively, allowing {@link #onNext(Object)} or {@link #onCompleted(Supplier)} to be called on the current
	 * thread while the lock is held.
	 *
	 * @return The transaction to close to release the lock
	 */
	public Transaction lockWrite() {
		return theLock == null ? Transaction.NONE : theLock.lock(true, null);
	}

	@Override
	public Transaction lock() {
		return theLock == null ? Transaction.NONE : theLock.lock(false, null);
	}

	@Override
	public Transaction tryLock() {
		return theLock == null ? Transaction.NONE : theLock.tryLock(false, null);
	}

	@Override
	public CoreId getCoreId() {
		return theLock == null ? CoreId.EMPTY : theLock.getCoreId();
	}
}

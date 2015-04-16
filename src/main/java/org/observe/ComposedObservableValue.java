package org.observe;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.util.ListenerSet;

import prisms.lang.Type;

/**
 * An observable that depends on the values of other observables
 *
 * @param <T> The type of the composed observable
 */
public class ComposedObservableValue<T> implements ObservableValue<T> {
	private final List<ObservableValue<?>> theComposed;
	private final Function<Object [], T> theFunction;

	private final ListenerSet<Observer<? super ObservableValueEvent<T>>> theObservers;
	private final Type theType;
	private final boolean combineNulls;

	/**
	 * @param function The function that operates on the argument observables to produce this observable's value
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @param composed The argument observables whose values are passed to the function
	 */
	public ComposedObservableValue(Function<Object [], T> function, boolean combineNull, ObservableValue<?>... composed) {
		this(null, function, combineNull, composed);
	}

	/**
	 * @param type The type for this value
	 * @param function The function that operates on the argument observables to produce this observable's value
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *            result will be null.
	 * @param composed The argument observables whose values are passed to the function
	 */
	public ComposedObservableValue(Type type, Function<Object [], T> function, boolean combineNull, ObservableValue<?>... composed) {
		theFunction = function;
		combineNulls = combineNull;
		theType = type != null ? type : getReturnType(function);
		theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
		theObservers = new ListenerSet<>();
		final Runnable [] composedSubs = new Runnable[theComposed.size()];
		final Object [] values = new Object[theComposed.size()];
		final Object [] oldValue = new Object[1];
		boolean [] completed = new boolean[1];
		theObservers.setUsedListener(new Consumer<Boolean>() {
			@Override
			public void accept(Boolean used) {
				if(used) {
					/*Don't need to initialize this explicitly, because these will be populated when the components are subscribed to
					 * for(int i = 0; i < args.length; i++)
					 * 	args[i] = theComposed.get(i).get(); */
					boolean [] initialized = new boolean[1];
					for(int i = 0; i < values.length; i++) {
						int index = i;
						composedSubs[i] = theComposed.get(i).observe(new Observer<ObservableValueEvent<?>>() {
							@Override
							public <V extends ObservableValueEvent<?>> void onNext(V event) {
								values[index] = event.getValue();
								if(!initialized[0])
									return;
								T newValue = combine(values);
								ObservableValueEvent<T> toFire = new ObservableValueEvent<>(ComposedObservableValue.this, (T) oldValue[0],
									newValue, event);
								oldValue[0] = newValue;
								fireNext(toFire);
							}

							@Override
							public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
								values[index] = event.getValue();
								completed[0] = true;
								if(!initialized[0])
									return;
								T newValue = combine(values);
								ObservableValueEvent<T> toFire = new ObservableValueEvent<>(ComposedObservableValue.this, (T) oldValue[0],
									newValue, event);
								oldValue[0] = newValue;
								fireCompleted(toFire);
							}

							@Override
							public void onError(Throwable e) {
								fireError(e);
							}

							private void fireNext(ObservableValueEvent<T> next) {
								theObservers.forEach(listener -> listener.onNext(next));
							}

							private void fireCompleted(ObservableValueEvent<T> next) {
								theObservers.forEach(listener -> listener.onCompleted(next));
							}

							private void fireError(Throwable error) {
								theObservers.forEach(listener -> listener.onError(error));
							}
						});
					}
					oldValue[0] = combine(values);
					initialized[0] = true;
				} else {
					for(int i = 0; i < theComposed.size(); i++) {
						composedSubs[i].run();
						composedSubs[i] = null;
						values[i] = null;
						oldValue[0] = null;
						completed[0] = false;
					}
				}
			}
		});
		theObservers.setOnSubscribe(observer -> {
			if(completed[0])
				observer.onCompleted(new ObservableValueEvent<>(this, null, (T) oldValue[0], null));
			else
				observer.onNext(new ObservableValueEvent<>(this, null, (T) oldValue[0], null));
		});
	}

	@Override
	public Type getType() {
		return theType;
	}

	/** @return The observable values that compose this value */
	public ObservableValue<?> [] getComposed() {
		return theComposed.toArray(new ObservableValue[theComposed.size()]);
	}

	/** @return The function used to map this observable's composed values into its return value */
	public Function<Object [], T> getFunction() {
		return theFunction;
	}

	/**
	 * @return Whether the combination function will be applied if the arguments are null. If false and any arguments are null, the result
	 *         will be null.
	 */
	public boolean isNullCombined() {
		return combineNulls;
	}

	@Override
	public T get() {
		Object [] args = new Object[theComposed.size()];
		for(int i = 0; i < args.length; i++)
			args[i] = theComposed.get(i).get();
		return combine(args);
	}

	private T combine(Object [] args) {
		if(!combineNulls) {
			for(Object arg : args)
				if(arg == null)
					return null;
		}
		return theFunction.apply(args.clone());
	}

	@Override
	public Runnable observe(Observer<? super ObservableValueEvent<T>> observer) {
		theObservers.add(observer);
		return () -> theObservers.remove(observer);
	}

	@Override
	public String toString() {
		return theComposed.toString();
	}

	/**
	 * @param function The function
	 * @return The return type of the function
	 */
	public static Type getReturnType(Function<?, ?> function) {
		return getReturnType(function, "apply", Object.class);
	}

	/**
	 * @param function The function
	 * @return The return type of the function
	 */
	public static Type getReturnType(BiFunction<?, ?, ?> function) {
		return getReturnType(function, "apply", Object.class, Object.class);
	}

	/**
	 * @param function The function
	 * @return The return type of the function
	 */
	public static Type getReturnType(java.util.function.Supplier<?> function) {
		return getReturnType(function, "get");
	}

	private static Type getReturnType(Object function, String methodName, Class<?>... types) {
		try {
			return new Type(function.getClass().getMethod(methodName, types).getGenericReturnType());
		} catch(NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException("No apply method on a function?", e);
		}
	}
}

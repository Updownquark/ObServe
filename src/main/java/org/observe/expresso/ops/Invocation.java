package org.observe.expresso.ops;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public abstract class Invocation implements ObservableExpression {
	private final List<String> theTypeArguments;
	private final List<ObservableExpression> theArguments;

	public Invocation(List<String> typeArguments, List<ObservableExpression> arguments) {
		theTypeArguments = typeArguments;
		theArguments = arguments;
	}

	public List<String> getTypeArguments() {
		return theTypeArguments;
	}

	public List<ObservableExpression> getArguments() {
		return theArguments;
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		TypeToken<?> targetType;
		if (type.getModelType() == ModelTypes.Action || type.getModelType() == ModelTypes.Value)
			targetType = type.getType(0);
		else
			targetType = TypeTokens.get().keyFor(type.getModelType().modelType).parameterized(type.getTypeList());
		return evaluateInternal2(type, env, new ArgOption(env), targetType);
	}

	protected class ArgOption implements Args {
		final ExpressoEnv theEnv;
		public final List<ValueContainer<SettableValue<?>, SettableValue<?>>>[] args;
		private final ValueContainer<SettableValue<?>, SettableValue<?>>[] resolved;

		ArgOption(ExpressoEnv env) {
			theEnv = env;
			args = new List[theArguments.size()];
			resolved = new ValueContainer[theArguments.size()];
			for (int a = 0; a < theArguments.size(); a++)
				args[a] = new ArrayList<>(2);
		}

		@Override
		public int size() {
			return args.length;
		}

		@Override
		public boolean matchesType(int arg, TypeToken<?> paramType) throws QonfigInterpretationException {
			ValueContainer<SettableValue<?>, SettableValue<?>> c;
			for (int i = 0; i < args[arg].size(); i++) {
				c = args[arg].get(i);
				if (TypeTokens.get().isAssignable(paramType, c.getType().getType(i))) {
					// Move to the beginning
					args[arg].remove(i);
					args[arg].add(0, c);
					return true;
				}
			}
			// Not found, try to evaluate it
			c = (ValueContainer<SettableValue<?>, SettableValue<?>>) (ValueContainer<?, ?>) theArguments.get(arg)
				.evaluate(ModelTypes.Value.forType(paramType), theEnv);
			args[arg].add(0, c);
			return true;
		}

		@Override
		public TypeToken<?> resolve(int arg) throws QonfigInterpretationException {
			if (resolved[arg] == null)
				resolved[arg] = theArguments.get(arg).evaluate(ModelTypes.Value.any(), theEnv);
			return resolved[arg].getType().getType(0);
		}
	}

	protected abstract <M, MV extends M> ValueContainer<M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, ExpressoEnv env,
		ArgOption args, TypeToken<?> targetType) throws QonfigInterpretationException;

	public static String printSignature(Executable method) {
		StringBuilder str = new StringBuilder(method.getDeclaringClass().getName()).append('.').append(method.getName()).append('(');
		for (int i = 0; i < method.getParameterCount(); i++) {
			if (i > 0)
				str.append(", ");
			str.append(method.getParameterTypes()[i].getName());
		}
		return str.append(')').toString();
	}

	public static boolean checkArgCount(int parameters, int args, boolean varArgs) {
		if (varArgs) {
			return args >= parameters - 1;
		} else
			return args == parameters;
	}

	public static <M extends Executable, T> Invocation.MethodResult<M, ? extends T> findMethod(M[] methods, String methodName,
		TypeToken<?> contextType, boolean arg0Context, List<? extends Args> argOptions, TypeToken<T> targetType, ExpressoEnv env,
		ExecutableImpl<M> impl) throws QonfigInterpretationException {
		Class<T> rawTarget = targetType == null ? (Class<T>) Void.class : TypeTokens.get().wrap(TypeTokens.getRawType(targetType));
		boolean voidTarget = rawTarget == Void.class;
		for (M m : methods) {
			if (methodName != null && !m.getName().equals(methodName))
				continue;
			boolean isStatic = impl.isStatic(m);
			if (!isStatic && !arg0Context && contextType == null)
				continue;
			TypeToken<?>[] paramTypes = null;
			List<QonfigInterpretationException> errors = null;
			for (int o = 0; o < argOptions.size(); o++) {
				try {
					Args option = argOptions.get(o);
					int methodArgCount = (!isStatic && arg0Context) ? option.size() - 1 : option.size();
					if (methodArgCount < 0 || !Invocation.checkArgCount(m.getParameterTypes().length, methodArgCount, m.isVarArgs()))
						continue;
					boolean ok = true;
					if (isStatic) {
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++)
								paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
						}
						// All arguments are parameters
						for (int a = 0; ok && a < option.size(); a++) {
							int p = a;
							TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
							if (!option.matchesType(a, paramType))
								ok = false;
						}
					} else {
						if (arg0Context) {
							// Use the first argument as context
							contextType = option.resolve(0);
							if (paramTypes == null) {
								paramTypes = new TypeToken[m.getParameterTypes().length];
								for (int p = 0; p < paramTypes.length; p++) {
									paramTypes[p] = contextType.resolveType(m.getGenericParameterTypes()[p]);
								}
							}
							ok = true;
							if (!option.matchesType(0, contextType))
								continue;
							for (int a = 1; ok && a < option.size(); a++) {
								int p = a - 1;
								TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
								if (!option.matchesType(a, paramType))
									ok = false;
							}
						} else {
							// Ignore context (supplied by caller), all arguments are parameters
							if (paramTypes == null) {
								paramTypes = new TypeToken[m.getParameterTypes().length];
								for (int p = 0; p < paramTypes.length; p++) {
									if (!(contextType.getType() instanceof Class))
										paramTypes[p] = contextType.resolveType(m.getGenericParameterTypes()[p]);
									else
										paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
								}
							}
							for (int a = 0; ok && a < option.size(); a++) {
								int p = a;
								TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
								if (!option.matchesType(a, paramType))
									ok = false;
							}
						}
					}
					if (ok) {
						TypeToken<?> returnType;
						if (!isStatic)
							returnType = contextType.resolveType(impl.getReturnType(m));
						else
							returnType = TypeTokens.get().of(impl.getReturnType(m));

						if (!voidTarget && !TypeTokens.get().isAssignable(targetType, returnType))
							throw new QonfigInterpretationException("Return type " + returnType + " of method "
								+ Invocation.printSignature(m) + " cannot be assigned to type " + targetType);
						return new Invocation.MethodResult<>(m, o, false, (TypeToken<T>) returnType);
					}
				} catch (QonfigInterpretationException e) {
					if (errors == null)
						errors = new ArrayList<>(3);
					errors.add(e);
				}
			}
			if (errors != null) {
				QonfigInterpretationException ex = errors.get(0);
				for (int i = 1; i < errors.size(); i++)
					ex.addSuppressed(errors.get(i));
				throw ex;
			}
		}
		return null;
	}

	public static abstract class InvocationContainer<X extends Executable, M, T, MV extends M> implements ValueContainer<M, MV> {
		private final Invocation.MethodResult<X, T> theMethod;
		private final ValueContainer<SettableValue<?>, SettableValue<?>> theContext;
		private final List<ValueContainer<SettableValue<?>, SettableValue<?>>> theArguments;
		private final ModelInstanceType<M, MV> theType;
		private final Invocation.ExecutableImpl<X> theImpl;
		private boolean isUpdatingContext;
		private T theCachedValue;

		public InvocationContainer(Invocation.MethodResult<X, T> method, ValueContainer<SettableValue<?>, SettableValue<?>> context,
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> arguments, ModelInstanceType<M, MV> type,
			Invocation.ExecutableImpl<X> impl) {
			theMethod = method;
			theArguments = arguments;
			theType = type;
			if (impl.isStatic(theMethod.method)) {
				if (context != null)
					System.out.println("Info: " + method + " should be called statically");
				theContext = null;
			} else {
				if (context == null)
					throw new IllegalStateException(method + " cannot be called without context");
				theContext = context;
			}
			theImpl = impl;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}

		@Override
		public MV get(ModelSetInstance models) {
			SettableValue<?> ctxV = theContext == null ? null : theContext.get(models);
			SettableValue<?>[] argVs = new SettableValue[theArguments.size()];
			Observable<?>[] changeSources = new Observable[ctxV == null ? argVs.length : argVs.length + 1];
			if (ctxV != null)
				changeSources[changeSources.length - 1] = ctxV.noInitChanges();
			for (int i = 0; i < argVs.length; i++) {
				argVs[i] = theArguments.get(i).get(models);
				if (argVs[i] == null)
					throw new IllegalStateException("Caller provided a model set without variable " + theArguments.get(i).toString());
				changeSources[i] = argVs[i].noInitChanges();
			}
			return createModelValue(ctxV, argVs, //
				Observable.or(changeSources));
		}

		protected abstract MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes);

		protected <C> T invoke(SettableValue<C> ctxV, SettableValue<?>[] argVs, boolean asAction)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
			if (isUpdatingContext)
				return theCachedValue;
			Object ctx = ctxV == null ? null : ctxV.get();
			if (ctx == null && ctxV != null) {
				// Although this is better in theory, all the conditionals needed to work around this are obnoxious
				// throw new NullPointerException(ctxV + " is null, cannot call " + theMethod);
				return null;
			}
			Object[] args = new Object[argVs.length];
			for (int a = 0; a < args.length; a++)
				args[a] = argVs[a].get();
			T returnValue = theMethod.invoke(ctx, args, theImpl);
			if (asAction && !isUpdatingContext && ctxV != null && theImpl.updateContext() && ctxV.isAcceptable(ctxV.get()) == null) {
				isUpdatingContext = true;
				theCachedValue = returnValue;
				try {
					ctxV.set(ctxV.get(), null);
				} catch (RuntimeException e) {
					System.err.println("Could not update context after method invocation " + this);
					e.printStackTrace();
				} finally {
					isUpdatingContext = false;
					theCachedValue = null;
				}
			}
			return returnValue;
		}

		protected <X> SettableValue<X> syntheticResultValue(TypeToken<X> type, SettableValue<?> ctxV, SettableValue<?>[] argVs,
			Observable<?> changes) {
			return SettableValue.asSettable(//
				ObservableValue.of(type, () -> {
					try {
						return (X) invoke(ctxV, argVs, false);
					} catch (Throwable e) {
						e.printStackTrace();
						return null;
					}
				}, () -> {
					long stamp = ctxV == null ? 0 : ctxV.getStamp();
					for (SettableValue<?> argV : argVs) {
						if (argV != null)
							stamp = Long.rotateLeft(stamp, 13) ^ argV.getStamp();
					}
					return stamp;
				}, changes, () -> this).cached(), //
				__ -> theImpl + "s are not reversible");
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theContext != null)
				str.append(theContext).append('.');
			str.append(theMethod.method.getName()).append('(');
			for (int i = 0; i < theArguments.size(); i++) {
				if (i > 0)
					str.append(", ");
				str.append(theArguments.get(i));
			}
			return str.append(')').toString();
		}
	}

	public static class InvocationActionContainer<X extends Executable, T>
	extends Invocation.InvocationContainer<X, ObservableAction<?>, T, ObservableAction<T>> {
		public InvocationActionContainer(Invocation.MethodResult<X, T> method, ValueContainer<SettableValue<?>, SettableValue<?>> context,
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> arguments, Invocation.ExecutableImpl<X> impl) {
			super(method, context, arguments, ModelTypes.Action.forType(method.returnType), impl);
		}

		@Override
		public ModelInstanceType.SingleTyped<ObservableAction<?>, T, ObservableAction<T>> getType() {
			return (SingleTyped<ObservableAction<?>, T, ObservableAction<T>>) super.getType();
		}

		@Override
		protected ObservableAction<T> createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
			return ObservableAction.of(getType().getValueType(), __ -> {
				try {
					return invoke(ctxV, argVs, true);
				} catch (Throwable e) {
					e.printStackTrace();
					return null;
				}
			});
		}
	}

	public static class InvocationValueContainer<X extends Executable, T>
	extends Invocation.InvocationContainer<X, SettableValue<?>, T, SettableValue<T>> {
		public InvocationValueContainer(Invocation.MethodResult<X, T> method, ValueContainer<SettableValue<?>, SettableValue<?>> context,
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> arguments, Invocation.ExecutableImpl<X> impl) {
			super(method, context, arguments, ModelTypes.Value.forType(method.returnType), impl);
		}

		@Override
		public ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> getType() {
			return (SingleTyped<SettableValue<?>, T, SettableValue<T>>) super.getType();
		}

		@Override
		protected SettableValue<T> createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
			return syntheticResultValue(getType().getValueType(), ctxV, argVs, changes);
		}
	}

	public static class InvocationThingContainer<X extends Executable, M, MV extends M>
	extends Invocation.InvocationContainer<X, M, MV, MV> {
		public InvocationThingContainer(Invocation.MethodResult<X, MV> method, ValueContainer<SettableValue<?>, SettableValue<?>> context,
			List<ValueContainer<SettableValue<?>, SettableValue<?>>> arguments, ModelInstanceType<M, MV> type,
			Invocation.ExecutableImpl<X> impl) {
			super(method, context, arguments, type, impl);
		}

		@Override
		protected MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
			if (getType().getModelType() == ModelTypes.Collection) {
				ObservableValue<ObservableCollection<?>> value = syntheticResultValue(//
					TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<?>> parameterized(getType().getType(0)), ctxV,
					argVs, changes);
				return (MV) ObservableCollection.flattenValue(value);
			} else if (getType().getModelType() == ModelTypes.Set) {
				ObservableValue<ObservableSet<?>> value = syntheticResultValue(
					TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<?>> parameterized(getType().getType(0)), ctxV, argVs,
					changes);
				return (MV) ObservableSet.flattenValue(value);
			} else {
				try {
					return invoke(ctxV, argVs, false);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
					e.printStackTrace();
					return (MV) ObservableCollection.of(getType().getType(0));
				}
			}
		}
	}

	interface ExecutableImpl<M extends Executable> {
		boolean isStatic(M method);

		Type getReturnType(M method);

		Object execute(M method, Object context, Object[] args)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException;

		boolean updateContext();

		static ExecutableImpl<Method> METHOD = new ExecutableImpl<Method>() {
			@Override
			public boolean isStatic(Method method) {
				return Modifier.isStatic(method.getModifiers());
			}

			@Override
			public Type getReturnType(Method method) {
				return method.getGenericReturnType();
			}

			@Override
			public Object execute(Method method, Object context, Object[] args)
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
				return method.invoke(context, args);
			}

			@Override
			public boolean updateContext() {
				return true;
			}

			@Override
			public String toString() {
				return "method";
			}
		};

		static ExecutableImpl<Constructor<?>> CONSTRUCTOR = new ExecutableImpl<Constructor<?>>() {
			@Override
			public boolean isStatic(Constructor<?> method) {
				return true;
			}

			@Override
			public Type getReturnType(Constructor<?> method) {
				return method.getDeclaringClass();
			}

			@Override
			public Object execute(Constructor<?> method, Object context, Object[] args)
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
				Object[] args2 = context == null ? args : ArrayUtils.add(args, 0, context);
				return method.newInstance(args2);
			}

			@Override
			public boolean updateContext() {
				return false;
			}

			@Override
			public String toString() {
				return "constructor";
			}
		};
	}

	public static class MethodResult<M extends Executable, R> {
		public final M method;
		public final int argListOption;
		private final boolean isArg0Context;
		public final TypeToken<R> returnType;

		public MethodResult(M method, int argListOption, boolean arg0Context, TypeToken<R> returnType) {
			this.method = method;
			this.argListOption = argListOption;
			isArg0Context = arg0Context;
			this.returnType = returnType;
		}

		public R invoke(Object context, Object[] args, ExecutableImpl<M> impl)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
			Object[] parameters;
			if (isArg0Context || method.isVarArgs()) {
				parameters = new Object[method.getParameterCount()];
				if (isArg0Context) {
					context = args[0];
					if (method.isVarArgs()) {
						System.arraycopy(args, 1, parameters, 0, parameters.length - 1);
						int lastArgLen = args.length - parameters.length;
						Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1], lastArgLen);
						System.arraycopy(args, parameters.length, lastArg, 0, lastArgLen);
						parameters[parameters.length - 1] = lastArg;
					} else
						System.arraycopy(args, 1, parameters, 0, parameters.length);
				} else { // var args
					System.arraycopy(args, 0, parameters, 0, parameters.length - 1);
					int lastArgLen = args.length - parameters.length + 1;
					Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1], lastArgLen);
					System.arraycopy(args, parameters.length - 1, lastArg, 0, lastArgLen);
					parameters[parameters.length - 1] = lastArg;
				}
			} else
				parameters = args;
			Object retVal = impl.execute(method, context, parameters);
			return (R) retVal;
		}

		@Override
		public String toString() {
			return Invocation.printSignature(method);
		}
	}
}
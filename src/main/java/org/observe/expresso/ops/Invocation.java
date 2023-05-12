package org.observe.expresso.ops;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.ErrorReporting;

import com.google.common.reflect.TypeToken;

/** An expression representing the invocation of a {@link MethodInvocation method} or {@link ConstructorInvocation constructor} */
public abstract class Invocation implements ObservableExpression {
	private final List<BufferedType> theTypeArguments;
	private final List<ObservableExpression> theArguments;

	/**
	 * @param typeArguments The type arguments to the invocation
	 * @param arguments The arguments to use to invoke the invokable
	 */
	public Invocation(List<BufferedType> typeArguments, List<ObservableExpression> arguments) {
		theTypeArguments = typeArguments;
		theArguments = arguments;
	}

	/** @return The type arguments to the invocation */
	public List<BufferedType> getTypeArguments() {
		return theTypeArguments;
	}

	/** @return The arguments to use to invoke the invokable */
	public List<ObservableExpression> getArguments() {
		return theArguments;
	}

	/** @return The offset in this expression of the method name */
	protected abstract int getMethodNameOffset();

	/** @return The offset in this expression of the initial parameter argument expression */
	protected abstract int getInitialArgOffset();

	@Override
	public ModelType<?> getModelType(ExpressoEnv env) {
		return ModelTypes.Value; // Gotta pick one
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() == ModelTypes.Action) {
			try (Transaction t = asAction()) {
				InvokableResult<?, SettableValue<?>, ? extends SettableValue<?>> result = evaluateInternal2(
					ModelTypes.Value.forType(type.getType(0)), env, new ArgOption(env, expressionOffset + getInitialArgOffset()),
					expressionOffset);
				return (ModelValueSynth<M, MV>) createActionContainer((InvokableResult<?, SettableValue<?>, SettableValue<Object>>) result,
					env.at(getMethodNameOffset()));
			}
		} else {
			InvokableResult<?, M, MV> result = evaluateInternal2(type, env, new ArgOption(env, expressionOffset + getInitialArgOffset()),
				expressionOffset);
			return createValueContainer(result, env.at(getMethodNameOffset()));
		}
	}

	/**
	 * Represents an argument option supplied to
	 * {@link Invocation#findMethod(Executable[], String, TypeToken, boolean, List, ModelInstanceType, ExpressoEnv, ExecutableImpl, ObservableExpression, int)}
	 */
	public interface Args {
		/** @return The number of arguments in the option */
		int size();

		/**
		 * @param arg The index of the argument to check
		 * @param paramType The type of the input parameter
		 * @return Whether the given parameter can be matched to the given argument
		 * @throws ExpressoEvaluationException If an error occurs making the determination
		 * @throws ExpressoInterpretationException If the argument could not be interpreted
		 * @throws TypeConversionException If the argument could not be interpreted as the given type
		 */
		boolean matchesType(int arg, TypeToken<?> paramType)
			throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException;

		/**
		 * @param arg The argument index
		 * @return The argument type at the given index
		 * @throws ExpressoEvaluationException If an error occurs making the determination
		 * @throws ExpressoInterpretationException If the argument could not be interpreted
		 * @throws TypeConversionException If the argument could not be interpreted as a value
		 */
		TypeToken<?> resolve(int arg) throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException;
	}

	/** An {@link Args} option representing a set of arguments to an invokable */
	protected class ArgOption implements Args {
		final ExpressoEnv theEnv;
		/** The arguments */
		public final List<ModelValueSynth<SettableValue<?>, SettableValue<?>>>[] args;
		private final ModelValueSynth<SettableValue<?>, SettableValue<?>>[] resolved;
		private final int theExpressionOffset;

		ArgOption(ExpressoEnv env, int argOffset) {
			theEnv = env;
			args = new List[theArguments.size()];
			resolved = new ModelValueSynth[theArguments.size()];
			for (int a = 0; a < theArguments.size(); a++)
				args[a] = new ArrayList<>(2);
			theExpressionOffset = argOffset;
		}

		@Override
		public int size() {
			return args.length;
		}

		@Override
		public boolean matchesType(int arg, TypeToken<?> paramType)
			throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException {
			ModelValueSynth<SettableValue<?>, SettableValue<?>> c;
			for (int i = 0; i < args[arg].size(); i++) {
				c = args[arg].get(i);
				TypeToken<?> argType = c.getType().getType(0);
				if (TypeTokens.get().isAssignable(paramType, argType)) {
					// Move to the beginning
					args[arg].remove(i);
					args[arg].add(0, c);
					return true;
				}
			}
			// Not found, try to evaluate it
			int argOffset = 0;
			for (int i = 0; i < arg; i++) {
				if (i > 0)
					argOffset++;
				argOffset += theArguments.get(i).getExpressionLength();
			}
			c = (ModelValueSynth<SettableValue<?>, SettableValue<?>>) (ModelValueSynth<?, ?>) theArguments.get(arg)
				.evaluate(ModelTypes.Value.forType(paramType), theEnv.at(argOffset), theExpressionOffset + argOffset);
			args[arg].add(0, c);
			return true;
		}

		@Override
		public TypeToken<?> resolve(int arg) throws ExpressoEvaluationException, ExpressoInterpretationException, TypeConversionException {
			if (resolved[arg] == null) {
				if (args[arg].isEmpty()) {
					int argOffset = 0;
					for (int i = 0; i < arg; i++) {
						if (i > 0)
							argOffset++;
						argOffset += theArguments.get(i).getExpressionLength();
					}
					resolved[arg] = theArguments.get(arg).evaluate(ModelTypes.Value.any(), theEnv.at(argOffset),
						theExpressionOffset + argOffset);
				} else
					resolved[arg] = args[arg].get(0);
			}
			try {
				return resolved[arg].getType().getType(0);
			} catch (ExpressoInterpretationException e) {
				throw new ExpressoEvaluationException(e.getErrorOffset(), e.getErrorLength(), e.getMessage(), e);
			}
		}

		@Override
		public String toString() {
			return theArguments.toString();
		}
	}

	/**
	 * A structure containing everything needed to invoke an invokable
	 *
	 * @param <X> The type of invokable
	 * @param <M> The model type of the result
	 * @param <MV> The type of the result
	 */
	protected static class InvokableResult<X extends Executable, M, MV extends M> {
		/** The method to invoke */
		public final MethodResult<X, MV> method;
		/** The context for the method invocation */
		public final ModelValueSynth<SettableValue<?>, SettableValue<?>> context;
		/** The arguments for the method invocation */
		public final List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> arguments;
		/** The interface to actually invoke the invokable */
		public final ExecutableImpl<X> impl;

		/**
		 * @param method The method to invoke
		 * @param context The context for the method invocation
		 * @param arguments The arguments for the method invocation
		 * @param impl The interface to actually invoke the invokable
		 */
		public InvokableResult(MethodResult<X, MV> method, ModelValueSynth<SettableValue<?>, SettableValue<?>> context,
			List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> arguments, ExecutableImpl<X> impl) {
			this.method = method;
			this.context = context;
			this.arguments = arguments;
			this.impl = impl;
		}
	}

	/**
	 * @param <M> The model type
	 * @param <MV> The model instance type
	 * @param type The model instance type of the value container to create
	 * @param env The expresso environment to use to evaluate this invocation
	 * @param args The argument option to use to invoke
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @return The result definition
	 * @throws ExpressoEvaluationException If an error occurs evaluating the invokable
	 * @throws ExpressoInterpretationException If an expression on which this expression depends could not be interpreted
	 */
	protected abstract <M, MV extends M> InvokableResult<?, M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, ExpressoEnv env,
		ArgOption args, int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException;

	private <X extends Executable, T> InvocationActionContainer<X, T> createActionContainer(
		InvokableResult<X, SettableValue<?>, SettableValue<T>> result, ErrorReporting reporting) {
		return new InvocationActionContainer<>(result.method, result.context, result.arguments, result.impl, reporting);
	}

	private <X extends Executable, M, MV extends M> ModelValueSynth<M, MV> createValueContainer(InvokableResult<X, M, MV> result,
		ErrorReporting reporting) {
		return new InvocationThingContainer<>(result.method, result.context, result.arguments, result.impl, reporting);
	}

	/**
	 * @param method The invokable to print
	 * @return The signature of the invokable
	 */
	public static String printSignature(Executable method) {
		StringBuilder str = new StringBuilder(method.getDeclaringClass().getName()).append('.').append(method.getName()).append('(');
		for (int i = 0; i < method.getParameterCount(); i++) {
			if (i > 0)
				str.append(", ");
			str.append(method.getParameterTypes()[i].getName());
		}
		return str.append(')').toString();
	}

	/**
	 * @param parameters The number of parameters in the invokable signature
	 * @param args The number of arguments to the method
	 * @param varArgs Whether the method is a var-args method
	 * @return Whether the given argument count matches the parameter count
	 */
	public static boolean checkArgCount(int parameters, int args, boolean varArgs) {
		if (varArgs) {
			return args >= parameters - 1;
		} else
			return args == parameters;
	}

	/**
	 * Finds the method matching an invocation
	 *
	 * @param <X> The type of the invokable to find
	 * @param <M> The model type of the target result
	 * @param <MV> The type of the target result
	 * @param methods The invokables to search through
	 * @param methodName The name of the invokable to find
	 * @param contextType The type that the invocation's context was evaluated to
	 * @param arg0Context Whether the first argument of the method should be its context
	 * @param argOptions The list of parameter options available for invocation
	 * @param targetType The result type of the invocation
	 * @param env The expresso environment to use for the invocation
	 * @param impl The executable implementation corresponding to the invokable type
	 * @param invocation The expression that this is being called from, just for inclusion in an error message
	 * @param expressionOffset The offset of this expression in the evaluated root
	 * @return The result containing the invokable matching the given options, or null if no such invokable was found in the list
	 * @throws ExpressoEvaluationException If a suitable invokable is found, but there is an error with its invocation
	 * @throws ExpressoInterpretationException If a method argument could not be interpreted
	 */
	public static <X extends Executable, M, MV extends M> MethodResult<X, MV> findMethod(X[] methods, String methodName,
		TypeToken<?> contextType, boolean arg0Context, List<? extends Args> argOptions, ModelInstanceType<M, MV> targetType,
		ExpressoEnv env, ExecutableImpl<X> impl, ObservableExpression invocation, int expressionOffset)
			throws ExpressoEvaluationException, ExpressoInterpretationException {
		Map<String, Exception> methodErrors = null;
		MethodResult<X, MV> bestResult = null;
		for (X m : methods) {
			if (methodName != null && !m.getName().equals(methodName))
				continue;
			boolean isStatic = impl.isStatic(m);
			if (!isStatic && !arg0Context && contextType == null)
				continue;
			int specificity = -1;
			TypeToken<?>[] paramTypes = null;
			if (bestResult != null) {
				specificity = 0;
				paramTypes = new TypeToken[m.getParameterTypes().length];
				for (int p = 0; p < paramTypes.length; p++) {
					paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
					specificity += TypeTokens.get().getTypeSpecificity(paramTypes[p].getType());
				}
				if (specificity < bestResult.specificity)
					continue; // Current result is better than this even if it matches
			}
			List<Exception> errors = null;
			for (int o = 0; o < argOptions.size(); o++) {
				try {
					TypeTokens.TypeVariableAccumulation tva;
					Args option = argOptions.get(o);
					int methodArgCount = (!isStatic && arg0Context) ? option.size() - 1 : option.size();
					if (methodArgCount < 0 || !Invocation.checkArgCount(m.getParameterTypes().length, methodArgCount, m.isVarArgs()))
						continue;
					TypeToken<?> tvaResolver;
					int methodArgStart;
					if (isStatic) {
						// No context, all arguments are parameters
						tvaResolver = null;
						methodArgStart = 0;
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++)
								paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
						}
					} else if (arg0Context) {
						// Use the first argument as context
						if (!option.matchesType(0, contextType))
							continue;
						tvaResolver = option.resolve(0);
						methodArgStart = 1;
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++)
								paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
						}
					} else {
						// Ignore context (supplied by caller), all arguments are parameters
						tvaResolver = contextType;
						methodArgStart = 0;
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++) {
								if (!(contextType.getType() instanceof Class))
									paramTypes[p] = contextType.resolveType(TypeTokens.get().wrap(m.getGenericParameterTypes()[p]));
								else
									paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
							}
						}
					}
					boolean ok = true;
					boolean varArgs = false;
					tva = TypeTokens.get().accumulate(impl.getMethodTypes(m), tvaResolver);
					for (int a = 0; ok && a < option.size() - methodArgStart; a++) {
						int ma = a - methodArgStart;
						int p = ma < paramTypes.length ? ma : paramTypes.length - 1;
						TypeToken<?> paramType = paramTypes[p];
						if (p == paramTypes.length - 1 && m.isVarArgs()) {
							// Test var-args invocation first
							Exception ex = null;
							try {
								TypeToken<?> ptComp = paramType.getComponentType();
								varArgs = option.matchesType(a, ptComp);
								if (varArgs && !tva.accumulate(ptComp, option.resolve(a)))
									ok = false;
							} catch (ExpressoEvaluationException | ExpressoInterpretationException | TypeConversionException e) {
								ex = e;
							}
							if (!varArgs && option.size() == paramTypes.length) { // Check for non-var-args invocation
								if (option.matchesType(a, paramType)) {
									if (!tva.accumulate(paramType, option.resolve(a)))
										ok = false;
								} else {
									ok = false;
									if (ex != null)
										_throw(ex, invocation, expressionOffset);
								}
							}
						} else if (!option.matchesType(a, paramType))
							ok = false;
						else if (!tva.accumulate(paramType, option.resolve(a)))
							ok = false;
					}
					if (ok) {
						TypeToken<?> returnType = tva.resolve(impl.getReturnType(m));

						ModelInstanceConverter<?, ?> converter = ModelTypes.Value.forType(returnType).convert(targetType);
						if (converter == null)
							throw new ExpressoEvaluationException(expressionOffset, invocation.getExpressionLength(), "Return type "
								+ returnType + " of method " + Invocation.printSignature(m) + " cannot be assigned to type " + targetType);
						if (specificity < 0) {
							specificity = 0;
							for (TypeToken<?> pt : paramTypes)
								specificity += TypeTokens.get().getTypeSpecificity(pt.getType());
						}
						bestResult = new Invocation.MethodResult<>(m, o, false, specificity,
							(ModelInstanceConverter<SettableValue<Object>, MV>) converter);
					}
				} catch (ExpressoEvaluationException | ExpressoInterpretationException | TypeConversionException e) {
					if (errors == null)
						errors = new ArrayList<>(3);
					errors.add(e);
				}
			}
			if (bestResult == null && errors != null) {
				if (methodErrors == null)
					methodErrors = new LinkedHashMap<>();
				if (errors.size() == 1)
					methodErrors.put(m.toString(), errors.get(0));
				else {
					StringBuilder msg = new StringBuilder().append('\t');
					for (int i = 0; i < errors.size(); i++) {
						if (i > 0)
							msg.append("\n\t\t");
						msg.append(errors.get(i).getMessage());
					}

					ExpressoEvaluationException ex = new ExpressoEvaluationException(expressionOffset, invocation.getExpressionLength(),
						msg.toString(), errors.get(0));
					for (int i = 1; i < errors.size(); i++)
						ex.addSuppressed(errors.get(i));
					methodErrors.put(m.toString(), ex);
				}
			}
		}
		if (bestResult == null && methodErrors != null) {
			if (methodErrors.size() == 1) {
				Exception ex = methodErrors.values().iterator().next();
				if (ex instanceof ExpressoInterpretationException)
					throw (ExpressoInterpretationException) ex;
			}
			String exMsg = methodErrors.values().iterator().next().getMessage();
			boolean sameMsg = true;
			for (Exception ex : methodErrors.values())
				sameMsg &= exMsg.equals(ex.getMessage());
			if (sameMsg)
				_throw(methodErrors.values().iterator().next(), invocation, expressionOffset);
			StringBuilder msg = new StringBuilder("Could not find a match for ").append(invocation).append(':');
			for (Map.Entry<String, Exception> err : methodErrors.entrySet()) {
				msg.append("\n\t").append(err.getKey()).append(": ").append(err.getValue().getMessage());
			}
			Exception firstEx = methodErrors.values().iterator().next();
			ExpressoEvaluationException ex;
			if (firstEx instanceof ExpressoEvaluationException)
				ex = new ExpressoEvaluationException(//
					((ExpressoEvaluationException) firstEx).getErrorOffset(), ((ExpressoEvaluationException) firstEx).getErrorLength(),
					msg.toString(), firstEx);
			else
				ex = new ExpressoEvaluationException(expressionOffset, invocation.getExpressionLength(), msg.toString(), firstEx);
			boolean first = true;
			for (Exception err : methodErrors.values()) {
				if (first)
					first = false;
				else
					ex.addSuppressed(err);
			}
			throw ex;
		}
		return bestResult;
	}

	static void _throw(Exception ex, ObservableExpression invocation, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (ex instanceof ExpressoEvaluationException)
			throw (ExpressoEvaluationException) ex;
		else if (ex instanceof ExpressoInterpretationException)
			throw (ExpressoInterpretationException) ex;
		else
			throw new ExpressoEvaluationException(expressionOffset, invocation.getExpressionLength(), ex.getMessage(), ex);
	}

	private static ThreadLocal<Boolean> AS_ACTION = new ThreadLocal<>();

	/**
	 * During the transaction, values evaluated from invocations will be uncached, such that each request of the value will cause a fresh
	 * invocation of the invokable.
	 *
	 * @return The transaction to close
	 */
	public static Transaction asAction() {
		AS_ACTION.set(true);
		return AS_ACTION::remove;
	}

	static abstract class InvocationContainer<X extends Executable, R, M, MV extends M> implements ModelValueSynth<M, MV> {
		private final Invocation.MethodResult<X, R> theMethod;
		private final ModelValueSynth<SettableValue<?>, SettableValue<?>> theContext;
		private final List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> theArguments;
		private final ModelInstanceType<M, MV> theType;
		private final Invocation.ExecutableImpl<X> theImpl;
		private final boolean isCaching;
		private final ErrorReporting theReporting;
		private boolean isUpdatingContext;
		private Object theCachedValue;

		InvocationContainer(Invocation.MethodResult<X, R> method, ModelValueSynth<SettableValue<?>, SettableValue<?>> context,
			List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> arguments, ModelInstanceType<M, MV> type,
			Invocation.ExecutableImpl<X> impl, ErrorReporting reporting) {
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
			isCaching = !Boolean.TRUE.equals(AS_ACTION.get());
			theReporting = reporting;
		}

		@Override
		public ModelType<M> getModelType() {
			return theType.getModelType();
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}

		protected Invocation.MethodResult<X, R> getMethod() {
			return theMethod;
		}

		@Override
		public MV get(ModelSetInstance models) throws ModelInstantiationException {
			SettableValue<?> ctxV = theContext == null ? null : theContext.get(models);
			SettableValue<?>[] argVs = new SettableValue[theArguments.size()];
			Observable<?>[] changeSources = new Observable[theContext == null ? argVs.length : argVs.length + 1];
			for (int i = 0; i < argVs.length; i++) {
				argVs[i] = theArguments.get(i).get(models);
				if (argVs[i] == null)
					throw new IllegalStateException("Caller provided a model set without variable " + theArguments.get(i).toString());
				changeSources[i] = argVs[i].noInitChanges();
			}
			if (ctxV != null)
				changeSources[changeSources.length - 1] = ctxV.noInitChanges();
			return createModelValue(ctxV, argVs, //
				Observable.or(changeSources));
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			SettableValue<?> sourceCtx = theContext == null ? null : theContext.get(sourceModels);
			SettableValue<?> newCtx = theContext == null ? null : theContext.get(newModels);
			SettableValue<?>[] argVs = new SettableValue[theArguments.size()];
			Observable<?>[] changeSources = new Observable[theContext == null ? argVs.length : argVs.length + 1];
			boolean different = sourceCtx != newCtx;
			for (int i = 0; i < argVs.length; i++) {
				SettableValue<?> sourceArg = theArguments.get(i).get(sourceModels);
				SettableValue<?> newArg = theArguments.get(i).get(newModels);
				different |= sourceArg != newArg;
				argVs[i] = newArg;
			}

			if (!different)
				return value;
			if (theContext != null)
				changeSources[changeSources.length - 1] = newCtx.noInitChanges();
			return createModelValue(newCtx, argVs, //
				Observable.or(changeSources));
		}

		@Override
		public BetterList<ModelValueSynth<?, ?>> getCores() throws ExpressoInterpretationException {
			return BetterList.of(//
				Stream.concat(Stream.of(theContext), theArguments.stream()), //
				vc -> vc == null ? Stream.empty() : vc.getCores().stream());
		}

		protected abstract MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes)
			throws ModelInstantiationException;

		protected <C> Object invoke(SettableValue<C> ctxV, SettableValue<?>[] argVs, boolean asAction)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
			if (isUpdatingContext)
				return theCachedValue;
			Object ctx = ctxV == null ? null : ctxV.get();
			if (ctx == null && ctxV != null) {
				theReporting.error(ctxV + " is null, cannot call " + theMethod);
				// Although throwing an exception is better in theory, all the conditionals needed to work around this are obnoxious
				// throw new NullPointerException(ctxV + " is null, cannot call " + theMethod);
				return null;
			}
			Object[] args = new Object[argVs.length];
			for (int a = 0; a < args.length; a++)
				args[a] = argVs[a].get();
			Object returnValue = theMethod.invoke(ctx, args, theImpl);
			if (asAction && !isUpdatingContext && ctxV != null && theImpl.updateContext() && ctxV.isAcceptable(ctxV.get()) == null) {
				isUpdatingContext = true;
				theCachedValue = returnValue;
				try {
					ctxV.set(ctxV.get(), null);
				} catch (RuntimeException e) {
					theReporting.error("Could not update context after method invocation " + this);
					e.printStackTrace();
				} finally {
					isUpdatingContext = false;
					theCachedValue = null;
				}
			}
			return returnValue;
		}

		protected <X2> SettableValue<X2> syntheticResultValue(TypeToken<X2> type, SettableValue<?> ctxV, SettableValue<?>[] argVs,
			Observable<?> changes) {
			ObservableValue.SyntheticObservable<X2> backing = ObservableValue.of(type, () -> {
				try {
					return (X2) invoke(ctxV, argVs, false);
				} catch (Throwable e) {
					theReporting.error(null, e);
					return null;
				}
			}, () -> {
				long stamp = ctxV == null ? 0 : ctxV.getStamp();
				for (SettableValue<?> argV : argVs) {
					if (argV != null)
						stamp = Long.rotateLeft(stamp, 13) ^ argV.getStamp();
				}
				return stamp;
			}, changes, () -> this);
			if (isCaching) {
				return SettableValue.asSettable(backing.cached(), //
					__ -> theImpl + "s are not reversible");
			} else {
				long[] stamp = new long[1];
				return SettableValue.asSettable(ObservableValue.of(type, //
					() -> {
						stamp[0]++;
						return backing.get();
					}, () -> {
						return backing.getStamp() ^ stamp[0];
					}, changes, () -> this), //
					__ -> theImpl + "s are not reversible");
			}
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

	static class InvocationActionContainer<X extends Executable, T>
	extends Invocation.InvocationContainer<X, SettableValue<T>, ObservableAction<?>, ObservableAction<T>> {
		InvocationActionContainer(Invocation.MethodResult<X, SettableValue<T>> method,
			ModelValueSynth<SettableValue<?>, SettableValue<?>> context, List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> arguments,
			Invocation.ExecutableImpl<X> impl, ErrorReporting reporting) {
			super(method, context, arguments, ModelTypes.Action.forType((TypeToken<T>) method.converter.getType().getType(0)), impl,
				reporting);
		}

		@Override
		public ModelInstanceType.SingleTyped<ObservableAction<?>, T, ObservableAction<T>> getType() {
			return (SingleTyped<ObservableAction<?>, T, ObservableAction<T>>) super.getType();
		}

		@Override
		protected ObservableAction<T> createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
			return ObservableAction.of(getType().getValueType(), __ -> {
				try {
					Object retValue = invoke(ctxV, argVs, true);
					return getMethod().converter.convert(SettableValue.of(Object.class, retValue, "")).get();
				} catch (InvocationTargetException e) {
					if (e.getTargetException() instanceof RuntimeException)
						throw (RuntimeException) e.getTargetException();
					else if (e.getTargetException() instanceof Error)
						throw (Error) e.getTargetException();
					else
						throw new CheckedExceptionWrapper(e.getTargetException());
				} catch (Throwable e) {
					e.printStackTrace();
					return null;
				}
			});
		}
	}

	static class InvocationThingContainer<X extends Executable, M, MV extends M> extends Invocation.InvocationContainer<X, MV, M, MV> {
		InvocationThingContainer(Invocation.MethodResult<X, MV> method, ModelValueSynth<SettableValue<?>, SettableValue<?>> context,
			List<ModelValueSynth<SettableValue<?>, SettableValue<?>>> arguments, Invocation.ExecutableImpl<X> impl,
			ErrorReporting reporting) {
			super(method, context, arguments, (ModelInstanceType<M, MV>) method.converter.getType(), impl, reporting);
		}

		@Override
		protected MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes)
			throws ModelInstantiationException {
			SettableValue<Object> value = syntheticResultValue(//
				TypeTokens.get().OBJECT, ctxV, argVs, changes);
			return getMethod().converter.convert(value);
		}
	}

	interface ExecutableImpl<M extends Executable> {
		boolean isStatic(M method);

		Type getReturnType(M method);

		TypeVariable<?>[] getMethodTypes(M method);

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
			public TypeVariable<?>[] getMethodTypes(Method method) {
				return method.getTypeParameters();
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
				if (method.getDeclaringClass().getTypeParameters().length == 0)
					return method.getDeclaringClass();
				else
					return TypeTokens.get().keyFor(method.getDeclaringClass()).parameterized(method.getDeclaringClass().getTypeParameters())
						.getType();
			}

			@Override
			public TypeVariable<?>[] getMethodTypes(Constructor<?> method) {
				return ArrayUtils.concat(TypeVariable.class, method.getDeclaringClass().getTypeParameters(), method.getTypeParameters());
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

	/**
	 * Represents a suitable invokable for an invocation with some additional information needed to invoke it
	 *
	 * @param <M> The type of the invokable
	 * @param <MV> The model instance type of the result
	 */
	public static class MethodResult<M extends Executable, MV> {
		/** The invokable to invoke */
		public final M method;
		/**
		 * The index of the option passed to
		 * {@link Invocation#findMethod(Executable[], String, TypeToken, boolean, List, ModelInstanceType, ExpressoEnv, ExecutableImpl, ObservableExpression, int)}
		 * whose arguments match this invocation
		 */
		public final int argListOption;
		private final boolean isArg0Context;
		/** The {@link TypeTokens#getTypeSpecificity(Type) specificity} of the method's parameters */
		public final int specificity;
		/** The convert to convert a {@link SettableValue} containing the invokable's return value to the target type */
		public final ModelInstanceConverter<SettableValue<Object>, MV> converter;

		MethodResult(M method, int argListOption, boolean arg0Context, int specificity,
			ModelInstanceConverter<SettableValue<Object>, MV> converter) {
			this.method = method;
			this.argListOption = argListOption;
			isArg0Context = arg0Context;
			this.specificity = specificity;
			this.converter = converter;
		}

		/**
		 * @param context The context on which to invoke the invokable
		 * @param args The arguments to pass to the invokable
		 * @param impl The invokable implementation to use to invoke the invokable
		 * @return The result of the invokable
		 * @throws IllegalAccessException If the invokable is inaccessible
		 * @throws IllegalArgumentException If the context or any of the arguments are of an inappropriate types
		 * @throws InvocationTargetException If the invokable itself throws an exception
		 * @throws InstantiationException If the constructor cannot create an instance of its type
		 */
		public Object invoke(Object context, Object[] args, ExecutableImpl<M> impl)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
			Object[] parameters;
			if (isArg0Context || method.isVarArgs()) {
				parameters = new Object[method.getParameterCount()];
				if (isArg0Context) {
					context = args[0];
					if (method.isVarArgs()) {
						System.arraycopy(args, 1, parameters, 0, parameters.length - 1);
						int lastArgLen = args.length - parameters.length;
						Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1].getComponentType(),
							lastArgLen);
						System.arraycopy(args, parameters.length, lastArg, 0, lastArgLen);
						parameters[parameters.length - 1] = lastArg;
					} else
						System.arraycopy(args, 1, parameters, 0, parameters.length);
				} else { // var args
					System.arraycopy(args, 0, parameters, 0, parameters.length - 1);
					int lastArgLen = args.length - parameters.length + 1;
					Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1].getComponentType(), lastArgLen);
					System.arraycopy(args, parameters.length - 1, lastArg, 0, lastArgLen);
					parameters[parameters.length - 1] = lastArg;
				}
			} else
				parameters = args;
			return impl.execute(method, context, parameters);
		}

		@Override
		public String toString() {
			return Invocation.printSignature(method);
		}
	}
}
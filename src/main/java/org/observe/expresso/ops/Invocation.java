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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
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
	protected Invocation(List<BufferedType> typeArguments, List<ObservableExpression> arguments) {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Value; // Could also be an action, but we gotta pick one
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() == ModelTypes.Action) {
			try (Transaction t = asAction()) {
				InvokableResult<?, SettableValue<?>, ? extends SettableValue<?>> result = evaluateInternal2(
					ModelTypes.Value.any(), env, new ArgOption(env, expressionOffset + getInitialArgOffset()),
					expressionOffset, exHandler);
				if (result == null)
					return null;
				return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					(InterpretedValueSynth<M, MV>) createActionContainer(
						(InvokableResult<?, SettableValue<?>, SettableValue<Object>>) result, env.reporting().at(getMethodNameOffset()),
						env.isTesting()),
					result.method.method, result.getAllChildren());
			}
		} else {
			InvokableResult<?, M, MV> result = evaluateInternal2(type, env, new ArgOption(env, expressionOffset + getInitialArgOffset()),
				expressionOffset, exHandler);
			if (result == null)
				return null;
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				createValueContainer(result, env.reporting().at(getMethodNameOffset()), env.isTesting()), result.method.method,
				result.getAllChildren());
		}
	}

	/**
	 * Represents an argument option supplied to
	 * {@link Invocation#findMethod(Executable[], String, TypeToken, boolean, List, ModelInstanceType, InterpretedExpressoEnv, ExecutableImpl, ObservableExpression, int)}
	 */
	public interface Args {
		/** @return The number of arguments in the option */
		int size();

		/**
		 * @param arg The index of the argument to check
		 * @param paramType The type of the input parameter
		 * @return Null if the given parameter can be matched to the given argument, or the type conversion error if it can't
		 */
		ExpressoInterpretationException matchesType(int arg, TypeToken<?> paramType) throws ExpressoInterpretationException;

		/**
		 * @param arg The argument index
		 * @return The argument type at the given index
		 */
		<EX extends Throwable> TypeToken<?> resolve(int arg, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX;

		int getArgOffset(int arg);
	}

	/** An {@link Args} option representing a set of arguments to an invokable */
	protected class ArgOption implements Args {
		final InterpretedExpressoEnv theEnv;
		/** The arguments */
		public final List<EvaluatedExpression<SettableValue<?>, SettableValue<?>>>[] args;
		private final EvaluatedExpression<SettableValue<?>, SettableValue<?>>[] resolved;
		private final int theExpressionOffset;

		ArgOption(InterpretedExpressoEnv env, int argOffset) {
			theEnv = env;
			args = new List[theArguments.size()];
			resolved = new EvaluatedExpression[theArguments.size()];
			for (int a = 0; a < theArguments.size(); a++)
				args[a] = new ArrayList<>(2);
			theExpressionOffset = argOffset;
		}

		@Override
		public int size() {
			return args.length;
		}

		@Override
		public ExpressoInterpretationException matchesType(int arg, TypeToken<?> paramType) throws ExpressoInterpretationException {
			EvaluatedExpression<SettableValue<?>, SettableValue<?>> c;
			for (int i = 0; i < args[arg].size(); i++) {
				c = args[arg].get(i);
				TypeToken<?> argType = c.getType().getType(0);
				if (TypeTokens.get().isAssignable(paramType, argType)) {
					// Move to the beginning
					args[arg].remove(i);
					args[arg].add(0, c);
					return null;
				}
			}
			// Not found, try to evaluate it
			int argOffset = 0;
			for (int i = 0; i < arg; i++)
				argOffset += theArguments.get(i).getExpressionLength() + 1;
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
				.holder2();
			c = (EvaluatedExpression<SettableValue<?>, SettableValue<?>>) (EvaluatedExpression<?, ?>) theArguments.get(arg).evaluate(//
				ModelTypes.Value.forType(paramType), theEnv.at(argOffset), theExpressionOffset + argOffset, tce);
			if (tce.get1() != null)
				return tce.get1();
			else if (tce.get2() != null)
				return new ExpressoInterpretationException(tce.get2().getMessage(), theEnv.reporting().at(argOffset).getPosition(), 0);
			else {
				args[arg].add(0, c);
				return null;
			}
		}

		@Override
		public <EX extends Throwable> TypeToken<?> resolve(int arg, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws EX, ExpressoInterpretationException {
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
				.stack(ExceptionHandler.holder());
			if (resolved[arg] == null) {
				if (args[arg].isEmpty()) {
					int argOffset = 0;
					for (int i = 0; i < arg; i++) {
						if (i > 0)
							argOffset++;
						argOffset += theArguments.get(i).getExpressionLength();
					}
					resolved[arg] = theArguments.get(arg).evaluate(ModelTypes.Value.any(), theEnv.at(argOffset),
						theExpressionOffset + argOffset, doubleX);
					if (doubleX.get2() != null) {
						exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
							theEnv.reporting().at(argOffset).getPosition(), 0));
						return null;
					} else if (resolved[arg] == null)
						return null;
				} else
					resolved[arg] = args[arg].get(0);
			}
			return resolved[arg].getType().getType(0);
		}

		@Override
		public int getArgOffset(int arg) {
			int argOffset = 0;
			for (int i = 0; i < arg; i++)
				argOffset += theArguments.get(i).getExpressionLength() + 1;
			return argOffset;
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
		public final EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> context;
		/** Whether this result's context is trivial and not to be used by the result */
		public final boolean trivialContext;
		/** The arguments for the method invocation */
		public final List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> arguments;
		/** The interface to actually invoke the invokable */
		public final ExecutableImpl<X> impl;

		/**
		 * @param method The method to invoke
		 * @param context The context for the method invocation
		 * @param contextTrivial Whether the result's context is trivial and not to be used by the result
		 * @param arguments The arguments for the method invocation
		 * @param impl The interface to actually invoke the invokable
		 */
		public InvokableResult(MethodResult<X, MV> method, EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> context,
			boolean contextTrivial, List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> arguments,
			ExecutableImpl<X> impl) {
			this.method = method;
			this.context = context;
			this.trivialContext = contextTrivial;
			this.arguments = arguments;
			this.impl = impl;
		}

		/** @return All evaluated expressions that are inputs in this result */
		public List<EvaluatedExpression<?, ?>> getAllChildren() {
			if (context != null) {
				if (arguments.isEmpty())
					return Collections.singletonList(context);
				List<EvaluatedExpression<?, ?>> children = new ArrayList<>(1 + arguments.size());
				children.add(context);
				children.addAll(arguments);
				return Collections.unmodifiableList(children);
			} else if (arguments.isEmpty())
				return Collections.emptyList();
			else
				return QommonsUtils.unmodifiableCopy(arguments);
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
	 */
	protected abstract <M, MV extends M, EX extends Throwable> InvokableResult<?, M, MV> evaluateInternal2(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, ArgOption args, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX;

	private <X extends Executable, T> InvocationActionContainer<X, T> createActionContainer(
		InvokableResult<X, SettableValue<?>, SettableValue<T>> result, ErrorReporting reporting, boolean testing) {
		return new InvocationActionContainer<>(result.method, result.trivialContext ? null : result.context, result.arguments, result.impl,
			reporting, testing);
	}

	private <X extends Executable, M, MV extends M> InterpretedValueSynth<M, MV> createValueContainer(InvokableResult<X, M, MV> result,
		ErrorReporting reporting, boolean testing) {
		return new InvocationThingContainer<>(result.method, result.trivialContext ? null : result.context, result.arguments, result.impl,
			reporting, testing);
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
	 */
	public static <X extends Executable, M, MV extends M, EX extends Throwable> MethodResult<X, MV> findMethod(X[] methods,
		String methodName, TypeToken<?> contextType, boolean arg0Context, List<? extends Args> argOptions,
		ModelInstanceType<M, MV> targetType, InterpretedExpressoEnv env, ExecutableImpl<X> impl, ObservableExpression invocation,
		int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		Map<X, ExpressoInterpretationException> methodErrors = null;
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
			for (int o = 0; o < argOptions.size(); o++) {
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
					ExpressoInterpretationException ex = option.matchesType(0, contextType);
					if (ex != null) {
						if (methodErrors == null)
							methodErrors = new LinkedHashMap<>();
						methodErrors.put(m, ex);
						continue;
					}
					tvaResolver = option.resolve(0, exHandler);
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
					if (ma < 0)
						continue;
					int p = ma < paramTypes.length ? ma : paramTypes.length - 1;
					TypeToken<?> paramType = paramTypes[p];
					if (p == paramTypes.length - 1 && m.isVarArgs()) {
						// Test var-args invocation first
						TypeToken<?> ptComp = paramType.getComponentType();
						varArgs = option.matchesType(a, ptComp) == null;
						if (varArgs && !tva.accumulate(ptComp, option.resolve(a, exHandler))) {
							if (methodErrors == null)
								methodErrors = new LinkedHashMap<>();
							methodErrors.put(m,
								new ExpressoInterpretationException(
									option.resolve(a, exHandler).getType() + " is not valid for var arg parameter "
										+ (paramTypes.length - 1) + ". Expected type " + tva.resolve(ptComp.getType()),
										env.reporting().at(expressionOffset + option.getArgOffset(a)).getPosition(), 0));
							ok = false;
							break;
						}
						if (!varArgs && option.size() == paramTypes.length) { // Check for non-var-args invocation
							ExpressoInterpretationException ex = option.matchesType(a, paramType);
							if (ex == null) {
								if (!tva.accumulate(paramType, option.resolve(a, exHandler))) {
									if (methodErrors == null)
										methodErrors = new LinkedHashMap<>();
									methodErrors.put(m,
										new ExpressoInterpretationException(
											option.resolve(a, exHandler).getType() + " is not valid for parameter " + a + ". Expected type "
												+ tva.resolve(paramType.getType()),
												env.reporting().at(expressionOffset + option.getArgOffset(a)).getPosition(), 0));
									ok = false;
								}
							} else {
								if (methodErrors == null)
									methodErrors = new LinkedHashMap<>();
								methodErrors.put(m, ex);
								ok = false;
								break;
							}
						}
					} else {
						ExpressoInterpretationException ex = option.matchesType(a, paramType);
						if (ex != null) {
							if (methodErrors == null)
								methodErrors = new LinkedHashMap<>();
							methodErrors.put(m, ex);
							ok = false;
						} else if (!tva.accumulate(paramType, option.resolve(a, exHandler))) {
							if (methodErrors == null)
								methodErrors = new LinkedHashMap<>();
							methodErrors.put(m,
								new ExpressoInterpretationException(
									option.resolve(a, exHandler).getType() + " is not valid for parameter " + a + ". Expected type "
										+ tva.resolve(paramType.getType()),
										env.reporting().at(expressionOffset + option.getArgOffset(a)).getPosition(), 0));
							ok = false;
						}
					}

				}
				if (ok) {
					TypeToken<?> returnType = tva.resolve(impl.getReturnType(m));

					ModelInstanceConverter<?, ?> converter = ModelTypes.Value.forType(returnType).convert(targetType, env);
					if (converter == null) {
						if (methodErrors == null)
							methodErrors = new LinkedHashMap<>();
						methodErrors.put(m,
							new ExpressoInterpretationException("Return type " + returnType + " of method " + Invocation.printSignature(m)
							+ " cannot be assigned to type " + targetType, env.reporting().getPosition(), 0));
					} else {
						if (specificity < 0) {
							specificity = 0;
							for (TypeToken<?> pt : paramTypes)
								specificity += TypeTokens.get().getTypeSpecificity(pt.getType());
						}
						bestResult = new Invocation.MethodResult<>(m, o, false, specificity,
							(ModelInstanceConverter<SettableValue<Object>, MV>) converter);
					}
				}
			}
		}
		if (bestResult == null && methodErrors != null) {
			if (methodErrors.size() == 1) {
				ExpressoInterpretationException ex = methodErrors.values().iterator().next();
				exHandler.handle1(ex);
				return null;
			}
			String exMsg = methodErrors.values().iterator().next().getMessage();
			boolean sameMsg = true;
			for (ExpressoInterpretationException ex : methodErrors.values())
				sameMsg &= exMsg.equals(ex.getMessage());
			if (sameMsg) {
				exHandler.handle1(methodErrors.values().iterator().next());
				return null;
			}
			StringBuilder msg = new StringBuilder("Could not find a match for ").append(invocation).append(':');
			for (Map.Entry<X, ExpressoInterpretationException> err : methodErrors.entrySet())
				msg.append("\n\t").append(err.getKey()).append(": ").append(err.getValue().getMessage());
			ExpressoInterpretationException tce = new ExpressoInterpretationException(msg.toString(), env.reporting().getPosition(), 0);
			for (ExpressoInterpretationException ex : methodErrors.values())
				tce.addSuppressed(ex);
			exHandler.handle1(tce);
		}
		return bestResult;
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

	static abstract class InvocationContainer<X extends Executable, R, M, MV extends M> implements InterpretedValueSynth<M, MV> {
		private final Invocation.MethodResult<X, R> theMethod;
		private final EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> theContext;
		private final List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> theArguments;
		private final ModelInstanceType<M, MV> theType;
		protected final Invocation.ExecutableImpl<X> theImpl;
		protected final boolean isCaching;
		protected final ErrorReporting theReporting;
		protected final boolean isTesting;

		InvocationContainer(Invocation.MethodResult<X, R> method, EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> context,
			List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> arguments, ModelInstanceType<M, MV> type,
			Invocation.ExecutableImpl<X> impl, ErrorReporting reporting, boolean testing) {
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
			isTesting = testing;
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

		public ErrorReporting getReporting() {
			return theReporting;
		}

		public Invocation.ExecutableImpl<X> getImpl() {
			return theImpl;
		}

		public boolean isCaching() {
			return isCaching;
		}

		public boolean isTesting() {
			return isTesting;
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			if (theContext != null) {
				if (theArguments.isEmpty())
					return Collections.singletonList(theContext);
				ArrayList<InterpretedValueSynth<?, ?>> components = new ArrayList<>(theArguments.size() + 1);
				components.add(theContext);
				components.addAll(theArguments);
				return Collections.unmodifiableList(components);
			} else
				return theArguments;
		}

		protected ModelValueInstantiator<? extends SettableValue<?>> contextInstantiator() {
			return theContext == null ? null : theContext.instantiate();
		}

		protected List<ModelValueInstantiator<? extends SettableValue<?>>> argumentInstantiators() {
			ModelValueInstantiator<? extends SettableValue<?>>[] args = new ModelValueInstantiator[theArguments.size()];
			for (int i = 0; i < args.length; i++)
				args[i] = theArguments.get(i).instantiate();
			return Arrays.asList(args);
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

	static abstract class InvocationInstantiator<X extends Executable, R, M, MV extends M> implements ModelValueInstantiator<MV> {
		private final Invocation.MethodResult<X, R> theMethod;
		private final ModelValueInstantiator<? extends SettableValue<?>> theContext;
		private final List<ModelValueInstantiator<? extends SettableValue<?>>> theArguments;
		protected final Invocation.ExecutableImpl<X> theImpl;
		protected final boolean isCaching;
		protected final ErrorReporting theReporting;
		protected final boolean isTesting;

		InvocationInstantiator(MethodResult<X, R> method, ModelValueInstantiator<? extends SettableValue<?>> context,
			List<ModelValueInstantiator<? extends SettableValue<?>>> arguments, ExecutableImpl<X> impl, boolean caching,
			ErrorReporting reporting, boolean testing) {
			super();
			theMethod = method;
			theContext = context;
			theArguments = arguments;
			theImpl = impl;
			isCaching = caching;
			theReporting = reporting;
			isTesting = testing;
		}

		protected Invocation.MethodResult<X, R> getMethod() {
			return theMethod;
		}

		public ErrorReporting getReporting() {
			return theReporting;
		}

		@Override
		public void instantiate() {
			if (theContext != null)
				theContext.instantiate();
			for (ModelValueInstantiator<?> arg : theArguments)
				arg.instantiate();
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

		protected abstract MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes)
			throws ModelInstantiationException;

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

	static class InvocationInstance<X extends Executable, R> {
		private final Invocation.MethodResult<X, R> theMethod;
		private boolean isUpdatingContext;
		private final boolean isCaching;
		private Object theCachedValue;
		protected final ErrorReporting theReporting;
		private final Invocation.ExecutableImpl<X> theImpl;
		private final SettableValue<Object> theContext;
		private final SettableValue<?>[] theArguments;
		protected final boolean isTesting;
		private final Object theDefaultValue;

		protected InvocationInstance(MethodResult<X, R> method, boolean caching, ErrorReporting reporting, ExecutableImpl<X> impl,
			SettableValue<Object> context, SettableValue<?>[] arguments, boolean testing, Object defaultValue) {
			theMethod = method;
			isCaching = caching;
			theReporting = reporting;
			theImpl = impl;
			theContext = context;
			theArguments = arguments;
			isTesting = testing;
			theDefaultValue = defaultValue;
		}

		protected Object invoke(boolean asAction)
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
			if (isUpdatingContext)
				return theCachedValue;
			Object ctx = theContext == null ? null : theContext.get();
			if (ctx == null && theContext != null) {
				String msg = theContext + " is null, cannot call " + theMethod;
				if (isTesting)
					throw new NullPointerException(msg);
				theReporting.error(msg);
				// Although throwing an exception is better in theory, all the conditionals needed to work around this are obnoxious
				// throw new NullPointerException(ctxV + " is null, cannot call " + theMethod);
				return theDefaultValue;
			}
			Object[] args = new Object[theArguments.length];
			for (int a = 0; a < args.length; a++)
				args[a] = theArguments[a].get();
			Object returnValue = theMethod.invoke(ctx, args, theImpl);
			if (asAction && !isUpdatingContext && theContext != null && theImpl.updateContext()
				&& theContext.isAcceptable(theContext.get()) == null) {
				isUpdatingContext = true;
				theCachedValue = returnValue;
				try {
					theContext.set(theContext.get(), null);
				} catch (RuntimeException e) {
					String msg = "Could not update context after method invocation " + this;
					if (isTesting)
						throw new IllegalStateException(msg, e);
					theReporting.error(msg, e);
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
					return (X2) invoke(false);
				} catch (Throwable e) {
					theReporting.error(null, e);
					return (X2) theDefaultValue;
				}
			}, () -> {
				if (ctxV != null)
					return Stamped.compositeStamp(Stream.concat(Stream.of(ctxV), Arrays.stream(argVs)).mapToLong(Stamped::getStamp),
						argVs.length + 1);
				else
					return Stamped.compositeStamp(Arrays.asList(argVs));
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
					}, () -> Stamped.compositeStamp(backing.getStamp(), stamp[0]), //
					changes, () -> this), //
					__ -> theImpl + "s are not reversible");
			}
		}

		public Invocation.MethodResult<X, R> getMethod() {
			return theMethod;
		}

		public ErrorReporting getReporting() {
			return theReporting;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theContext != null)
				str.append(theContext).append('.');
			str.append(theMethod.method.getName()).append('(');
			StringUtils.print(str, ", ", Arrays.asList(theArguments), StringBuilder::append);
			str.append(')');
			return str.toString();
		}
	}

	static class InvocationActionContainer<X extends Executable, T>
	extends Invocation.InvocationContainer<X, SettableValue<T>, ObservableAction, ObservableAction> {
		InvocationActionContainer(Invocation.MethodResult<X, SettableValue<T>> method,
			EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> context,
				List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> arguments, Invocation.ExecutableImpl<X> impl,
				ErrorReporting reporting, boolean testing) {
			super(method, context, arguments, ModelTypes.Action.instance(), impl, reporting, testing);
		}

		@Override
		public ModelInstanceType.UnTyped<ObservableAction> getType() {
			return (ModelInstanceType.UnTyped<ObservableAction>) super.getType();
		}

		@Override
		public ModelValueInstantiator<ObservableAction> instantiate() {
			return new ActionInstantiator<>(getMethod(), contextInstantiator(), argumentInstantiators(), getImpl(), isCaching(),
				getReporting(), isTesting(), (TypeToken<T>) getMethod().converter.getType().getType(0));
		}

		static class ActionInstantiator<X extends Executable, T>
		extends InvocationInstantiator<X, SettableValue<T>, ObservableAction, ObservableAction> {
			private final TypeToken<T> theType;

			ActionInstantiator(MethodResult<X, SettableValue<T>> method, ModelValueInstantiator<? extends SettableValue<?>> context,
				List<ModelValueInstantiator<? extends SettableValue<?>>> arguments, ExecutableImpl<X> impl, boolean caching,
				ErrorReporting reporting, boolean testing, TypeToken<T> type) {
				super(method, context, arguments, impl, caching, reporting, testing);
				theType = type;
			}

			@Override
			protected ObservableAction createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
				return new InvocationAction<>(getMethod(), isCaching, theReporting, theImpl, (SettableValue<Object>) ctxV, argVs, isTesting,
					theType);
			}
		}

		static class InvocationAction<X extends Executable, T> extends InvocationInstance<X, SettableValue<T>>
		implements ObservableAction {
			protected InvocationAction(MethodResult<X, SettableValue<T>> method, boolean caching, ErrorReporting reporting,
				ExecutableImpl<X> impl, SettableValue<Object> context, SettableValue<?>[] arguments, boolean testing, TypeToken<T> type) {
				super(method, caching, reporting, impl, context, arguments, testing, null);
			}

			@Override
			public void act(Object cause) throws IllegalStateException {
				try {
					invoke(true);
				} catch (InstantiationException | IllegalAccessException e) {
					if (isTesting)
						throw new IllegalStateException(e);
					getReporting().error(e.getMessage(), e);
				} catch (InvocationTargetException e) {
					if (isTesting) {
						if (e.getTargetException() instanceof RuntimeException)
							throw (RuntimeException) e.getTargetException();
						else if (e.getTargetException() instanceof Error)
							throw (Error) e.getTargetException();
						else
							throw new IllegalStateException(e.getTargetException());
					}
					getReporting().error(e.getTargetException().getMessage(), e.getTargetException());
				} catch (RuntimeException e) {
					if (isTesting)
						throw e;
					getReporting().error(e.getMessage(), e);
				} catch (Error e) {
					if (isTesting)
						throw e;
					getReporting().error(e.getMessage(), e);
				}
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_ENABLED;
			}
		}
	}

	static class InvocationThingContainer<X extends Executable, M, MV extends M> extends Invocation.InvocationContainer<X, MV, M, MV> {
		InvocationThingContainer(Invocation.MethodResult<X, MV> method,
			EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> context,
				List<EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>>> arguments, Invocation.ExecutableImpl<X> impl,
				ErrorReporting reporting, boolean testing) {
			super(method, context, arguments, (ModelInstanceType<M, MV>) method.converter.getType(), impl, reporting, testing);
		}

		@Override
		public ModelValueInstantiator<MV> instantiate() {
			return new ThingInstantiator<>(getMethod(), contextInstantiator(), argumentInstantiators(), getImpl(), isCaching(),
				getReporting(), isTesting());
		}

		static class ThingInstantiator<X extends Executable, M, MV extends M> extends InvocationInstantiator<X, MV, M, MV> {
			ThingInstantiator(MethodResult<X, MV> method, ModelValueInstantiator<? extends SettableValue<?>> context,
				List<ModelValueInstantiator<? extends SettableValue<?>>> arguments, ExecutableImpl<X> impl, boolean caching,
				ErrorReporting reporting, boolean testing) {
				super(method, context, arguments, impl, caching, reporting, testing);
			}

			@Override
			protected MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes)
				throws ModelInstantiationException {
				Object defaultValue;
				if (getMethod().method instanceof Method)
					defaultValue = TypeTokens.get().getDefaultValue(((Method) getMethod().method).getReturnType());
				else
					defaultValue = null;
				SettableValue<Object> value = new InvocationInstance<>(getMethod(), isCaching, theReporting, theImpl,
					(SettableValue<Object>) ctxV, argVs, isTesting, defaultValue)
					.syntheticResultValue(TypeTokens.get().OBJECT, ctxV, argVs, changes);
				return getMethod().converter.convert(value);
			}
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
		 * {@link Invocation#findMethod(Executable[], String, TypeToken, boolean, List, ModelInstanceType, InterpretedExpressoEnv, ExecutableImpl, ObservableExpression, int)}
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
					for (int srcI = parameters.length - 1, destI = 0; destI < lastArgLen; srcI++, destI++)
						Array.set(lastArg, destI, args[srcI]);
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
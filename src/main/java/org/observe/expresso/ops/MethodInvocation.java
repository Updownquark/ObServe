package org.observe.expresso.ops;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** An expression representing the invocation of a method */
public class MethodInvocation extends Invocation {
	private final ObservableExpression theContext;
	private final String theMethodName;

	/**
	 * @param context The expression representing the object on which to invoke the non-static method or the type on which to invoke the
	 *        static method
	 * @param methodName The name of the method
	 * @param typeArgs The type arguments for the method invocation
	 * @param arguments The arguments to the method
	 */
	public MethodInvocation(ObservableExpression context, String methodName, List<String> typeArgs,
		List<ObservableExpression> arguments) {
		super(typeArgs, arguments);
		theContext = context;
		theMethodName = methodName;
	}

	/**
	 * @return The expression representing the object on which to invoke the non-static method or the type on which to invoke the static
	 *         method
	 */
	public ObservableExpression getContext() {
		return theContext;
	}

	/** @return The name of the method */
	public String getMethodName() {
		return theMethodName;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		List<ObservableExpression> children = new ArrayList<>(getArguments().size() + (theContext == null ? 0 : 1));
		if (theContext != null)
			children.add(theContext);
		children.addAll(getArguments());
		return Collections.unmodifiableList(children);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression ctx = theContext == null ? null : theContext.replaceAll(replace);
		List<ObservableExpression> args = getArguments();
		List<ObservableExpression> newArgs = new ArrayList<>(args.size());
		boolean different = ctx != theContext;
		for (ObservableExpression arg : args) {
			ObservableExpression newArg = arg.replaceAll(replace);
			newArgs.add(newArg);
			different |= newArg != arg;
		}
		if (different)
			return new MethodInvocation(ctx, theMethodName, getTypeArguments(), Collections.unmodifiableList(newArgs));
		return this;
	}

	@Override
	protected <M, MV extends M> InvokableResult<?, M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, ExpressoEnv env, ArgOption args)
		throws QonfigInterpretationException {
		if (theContext != null) {
			if (theContext instanceof NameExpression) {
				Class<?> clazz = env.getClassView().getType(theContext.toString());
				if (clazz != null) {
					Invocation.MethodResult<Method, MV> result = Invocation.findMethod(clazz.getMethods(), theMethodName,
						TypeTokens.get().of(clazz), true, Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this);
					if (result != null) {
						ValueContainer<SettableValue<?>, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
						for (int a = 0; a < realArgs.length; a++)
							realArgs[a] = args.args[a].get(0);
						return new InvokableResult<>(result, null, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
					}
					throw new QonfigInterpretationException("No such method " + printSignature() + " in class " + clazz.getName());
				}
			}
			ValueContainer<SettableValue<?>, SettableValue<?>> ctx = theContext.evaluate(ModelTypes.Value.any(), env);
			Invocation.MethodResult<Method, MV> result = Invocation.findMethod(
				TypeTokens.getRawType(ctx.getType().getType(0)).getMethods(), theMethodName, ctx.getType().getType(0), false,
				Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this);
			if (result != null) {
				ValueContainer<SettableValue<?>, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				return new InvokableResult<>(result, ctx, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
			}
			throw new QonfigInterpretationException(
				"No such method " + printSignature() + " on " + theContext + "(" + ctx.getType().getType(0) + ")");
		} else {
			List<Method> methods = env.getClassView().getImportedStaticMethods(theMethodName);
			Invocation.MethodResult<Method, MV> result = Invocation.findMethod(methods.toArray(new Method[methods.size()]), theMethodName,
				null, true, Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this);
			if (result != null) {
				ValueContainer<SettableValue<?>, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				return new InvokableResult<>(result, null, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
			}
			throw new QonfigInterpretationException("No such imported method " + printSignature());
		}
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not implemented.  Are you sure you didn't mean to use the resolution operator (::)?");
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (theContext != null)
			str.append(theContext).append('.');
		str.append(printSignature());
		return str.toString();
	}

	/** @return A string representing the method signature */
	public String printSignature() {
		StringBuilder str = new StringBuilder(theMethodName).append('(');
		boolean first = true;
		for (ObservableExpression arg : getArguments()) {
			if (first)
				first = false;
			else
				str.append(", ");
			str.append(arg);
		}
		return str.append(')').toString();
	}
}
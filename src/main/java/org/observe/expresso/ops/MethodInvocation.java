package org.observe.expresso.ops;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

import com.google.common.reflect.TypeToken;

/** An expression representing the invocation of a method */
public class MethodInvocation extends Invocation {
	private final ObservableExpression theContext;
	private final BufferedName theMethodName;

	/**
	 * @param context The expression representing the object on which to invoke the non-static method or the type on which to invoke the
	 *        static method
	 * @param methodName The name of the method
	 * @param typeArgs The type arguments for the method invocation
	 * @param arguments The arguments to the method
	 */
	public MethodInvocation(ObservableExpression context, BufferedName methodName, List<BufferedType> typeArgs,
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
	public BufferedName getMethodName() {
		return theMethodName;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		if (childIndex >= getComponents().size())
			throw new IndexOutOfBoundsException(childIndex + " of " + getComponents().size());
		int argIndex;
		int offset = 0;
		if (theContext != null) {
			if (childIndex == 0)
				return 0;
			argIndex = childIndex - 1;
			offset = theContext.getExpressionLength() + 1;
		} else
			argIndex = childIndex;
		offset += theMethodName.length() + 1;
		if (argIndex > 0)
			offset += argIndex;
		for (int a = 0; a < argIndex; a++)
			offset += getArguments().get(a).getExpressionLength();
		return offset;
	}

	@Override
	public int getExpressionLength() {
		int length = theMethodName.length() + 2;
		if (theContext != null)
			length += theContext.getExpressionLength() + 1;
		if (getArguments().size() > 1)
			length += getArguments().size() - 1;
		for (int a = 0; a < getArguments().size(); a++)
			length += getArguments().get(a).getExpressionLength();
		return length;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
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
	protected int getInitialArgOffset() {
		int offset = theMethodName.length() + 1;
		if (theContext != null)
			offset += theContext.getExpressionLength() + 1;
		return offset;
	}

	@Override
	protected int getMethodNameOffset() {
		if (theContext != null)
			return theContext.getExpressionLength() + 1;
		return 0;
	}

	@Override
	protected <M, MV extends M, EX extends Throwable> InvokableResult<?, M, MV> evaluateInternal2(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, ArgOption args, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		if (theContext != null) {
			if (theContext instanceof NameExpression) {
				Class<?> clazz = env.getClassView().getType(((NameExpression) theContext).getName());
				if (clazz != null) {
					Invocation.MethodResult<Method, MV> result = Invocation.findMethod(clazz.getMethods(), theMethodName.getName(),
						TypeTokens.get().of(clazz), true, Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this,
						expressionOffset, exHandler);
					if (result != null) {
						EvaluatedExpression<SettableValue<?>, SettableValue<?>>[] realArgs = new EvaluatedExpression[getArguments().size()];
						for (int a = 0; a < realArgs.length; a++)
							realArgs[a] = args.args[a].get(0);
						EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> ctx = ObservableExpression.evEx(expressionOffset,
							getExpressionLength(), InterpretedValueSynth.literalValue(TypeTokens.get().VOID, null, theContext.toString()),
							clazz);
						return new InvokableResult<>(result, ctx, true, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
					}
					exHandler
					.handle1(new ExpressoInterpretationException("No such method " + printSignature() + " in class " + clazz.getName(),
						env.reporting().getPosition(), getExpressionLength()));
					return null;
				}
			}
			EvaluatedExpression<SettableValue<?>, SettableValue<?>> ctx;
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
				.stack(ExceptionHandler.holder());
			ctx = theContext.evaluate(ModelTypes.Value.any(), env, expressionOffset, doubleX);
			if (doubleX.get2() != null) {
				exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
					theContext.getExpressionLength(), doubleX.get2()));
				return null;
			} else if (ctx == null)
				return null;
			TypeToken<?> ctxType = ctx.getType().getType(0);
			Class<?> rawCtxType = TypeTokens.getRawType(ctxType);
			Method[] methods = rawCtxType.getMethods();
			if (rawCtxType.isInterface())
				methods = ArrayUtils.addAll(methods, Object.class.getMethods());
			Invocation.MethodResult<Method, MV> result = Invocation.findMethod(methods, theMethodName.getName(), ctxType, false,
				Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this, expressionOffset, exHandler);
			if (result != null) {
				EvaluatedExpression<SettableValue<?>, SettableValue<?>>[] realArgs = new EvaluatedExpression[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				return new InvokableResult<>(result, ctx, false, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
			} else if (exHandler.hasException())
				return null;
			else {
				exHandler.handle1(
					new ExpressoInterpretationException("No such method " + printSignature() + " on " + theContext + "(" + ctxType + ")",
						env.reporting().getPosition(), getExpressionLength()));
				return null;
			}
		} else {
			List<Method> methods = env.getClassView().getImportedStaticMethods(theMethodName.getName());
			Invocation.MethodResult<Method, MV> result = Invocation.findMethod(methods.toArray(new Method[methods.size()]),
				theMethodName.getName(), null, true, Arrays.asList(args), type, env, Invocation.ExecutableImpl.METHOD, this,
				expressionOffset, exHandler);
			if (result != null) {
				EvaluatedExpression<SettableValue<?>, SettableValue<?>>[] realArgs = new EvaluatedExpression[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				return new InvokableResult<>(result, null, false, Arrays.asList(realArgs), Invocation.ExecutableImpl.METHOD);
			} else if (exHandler.hasException())
				return null;
			else {
				exHandler.handle1(new ExpressoInterpretationException("No such imported method " + printSignature(),
					env.reporting().getPosition(), getExpressionLength()));
				return null;
			}
		}
	}

	@Override
	public String toString() {
		return printSignature();
	}

	/** @return A string representing the method signature */
	public String printSignature() {
		StringBuilder str = new StringBuilder();
		if (theContext != null)
			str.append(theContext).append('.');
		str.append(theMethodName).append('(');
		boolean first = true;
		for (ObservableExpression arg : getArguments()) {
			if (first)
				first = false;
			else
				str.append(',');
			str.append(arg);
		}
		return str.append(')').toString();
	}
}
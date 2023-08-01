package org.observe.expresso.ops;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;

/** An expression representing the invocation of a constructor to create a new instance of a type */
public class ConstructorInvocation extends Invocation {
	private final BufferedType theType;

	/**
	 * @param type The string representing the type for which to create an instance
	 * @param typeArguments The strings representing the type arguments to the constructor
	 * @param args The arguments to pass to the constructor
	 */
	public ConstructorInvocation(BufferedType type, List<BufferedType> typeArguments, List<ObservableExpression> args) {
		super(typeArguments, args);
		theType = type;
	}

	/** @return The string representing the type for which to create an instance */
	public BufferedType getType() {
		return theType;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		if (childIndex >= getComponents().size())
			throw new IndexOutOfBoundsException(childIndex + " of " + getComponents().size());
		int offset = 4 + theType.length();
		if (childIndex > 0)
			offset += childIndex - 1;
		for (int i = 0; i < childIndex; i++)
			offset += getComponents().get(childIndex).getExpressionLength();
		return offset;
	}

	@Override
	public int getExpressionLength() {
		int length = 5 + theType.length();
		if (!getComponents().isEmpty())
			length += getComponents().size() - 1;
		for (ObservableExpression arg : getComponents())
			length = arg.getExpressionLength();
		return length;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return getArguments();
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		List<? extends ObservableExpression> children = getComponents();
		List<ObservableExpression> newChildren = new ArrayList<>(children.size());
		boolean different = false;
		for (ObservableExpression child : children) {
			ObservableExpression newChild = child.replaceAll(replace);
			newChildren.add(newChild);
			different |= newChild != child;
		}
		if (different)
			return new ConstructorInvocation(getType(), getTypeArguments(), Collections.unmodifiableList(newChildren));
		return this;
	}

	@Override
	protected int getInitialArgOffset() {
		return theType.length() + 4;
	}

	@Override
	protected int getMethodNameOffset() {
		return 4;
	}

	@Override
	protected <M, MV extends M> InvokableResult<?, M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env, ArgOption args,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		Class<?> constructorType = env.getClassView().getType(theType.getName());
		if (constructorType == null)
			throw new ExpressoEvaluationException(expressionOffset + 4, theType.length(), "No such type found: " + theType);
		Invocation.MethodResult<Constructor<?>, MV> result = Invocation.findMethod(constructorType.getConstructors(), null, null, true,
			Arrays.asList(args), type, env, Invocation.ExecutableImpl.CONSTRUCTOR, this, expressionOffset);
		if (result != null) {
			EvaluatedExpression<SettableValue<?>, SettableValue<?>>[] realArgs = new EvaluatedExpression[getArguments().size()];
			for (int a = 0; a < realArgs.length; a++)
				realArgs[a] = args.args[a].get(0);
			return new InvokableResult<>(result, null, false, Arrays.asList(realArgs), Invocation.ExecutableImpl.CONSTRUCTOR);
		}
		throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), "No such constructor " + printSignature());
	}

	@Override
	public String toString() {
		return printSignature();
	}

	/** @return A string representing this constructor invocation */
	public String printSignature() {
		StringBuilder str = new StringBuilder("new").append(theType).append('(');
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
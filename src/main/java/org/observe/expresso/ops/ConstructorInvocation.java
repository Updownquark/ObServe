package org.observe.expresso.ops;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** An expression representing the invocation of a constructor to create a new instance of a type */
public class ConstructorInvocation extends Invocation {
	private final String theType;

	/**
	 * @param type The string representing the type for which to create an instance
	 * @param typeArguments The strings representing the type arguments to the constructor
	 * @param args The arguments to pass to the constructor
	 */
	public ConstructorInvocation(String type, List<String> typeArguments, List<ObservableExpression> args) {
		super(typeArguments, args);
		theType = type;
	}

	/** @return The string representing the type for which to create an instance */
	public String getType() {
		return theType;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return getArguments();
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not implemented");
	}

	@Override
	protected <M, MV extends M> ValueContainer<M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, ExpressoEnv env, ArgOption args,
		TypeToken<?> targetType) throws QonfigInterpretationException {
		Class<?> constructorType = env.getClassView().getType(theType);
		if (constructorType == null)
			throw new QonfigInterpretationException("No such type found: " + theType);
		Invocation.MethodResult<Constructor<?>, ?> result = Invocation.findMethod(constructorType.getConstructors(), null, null, true,
			Arrays.asList(args), targetType, env, Invocation.ExecutableImpl.CONSTRUCTOR);
		if (result != null) {
			ValueContainer<SettableValue<?>, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
			for (int a = 0; a < realArgs.length; a++)
				realArgs[a] = args.args[a].get(0);
			if (type.getModelType() == ModelTypes.Value)
				return (ValueContainer<M, MV>) new InvocationValueContainer<>(result, null, Arrays.asList(realArgs),
					Invocation.ExecutableImpl.CONSTRUCTOR);
			else if (type.getModelType() == ModelTypes.Action)
				return (ValueContainer<M, MV>) new InvocationActionContainer<>(result, null, Arrays.asList(realArgs),
					Invocation.ExecutableImpl.CONSTRUCTOR);
			else {
				TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
				for (int i = 0; i < paramTypes.length; i++)
					paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
				return new InvocationThingContainer<>((Invocation.MethodResult<Constructor<?>, MV>) result, null, Arrays.asList(realArgs),
					(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes), Invocation.ExecutableImpl.CONSTRUCTOR);
			}
		}
		throw new QonfigInterpretationException("No such constructor " + printSignature());
	}

	@Override
	public String toString() {
		return printSignature();
	}

	/** @return A string representing this constructor invocation */
	public String printSignature() {
		StringBuilder str = new StringBuilder(theType).append('(');
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
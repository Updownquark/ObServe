package org.observe.expresso.ops;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ClassView;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.Invocation.ArgOption;
import org.observe.expresso.ops.Invocation.ExecutableImpl;
import org.observe.expresso.ops.Invocation.InvocationActionContainer;
import org.observe.expresso.ops.Invocation.InvocationThingContainer;
import org.observe.expresso.ops.Invocation.InvocationValueContainer;
import org.observe.expresso.ops.Invocation.MethodResult;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class MethodInvocation extends Invocation {
	private final ObservableExpression theContext;
	private final String theMethodName;

	public MethodInvocation(ObservableExpression context, String methodName, List<String> typeArgs,
		List<ObservableExpression> arguments) {
		super(typeArgs, arguments);
		theContext = context;
		theMethodName = methodName;
	}

	public ObservableExpression getContext() {
		return theContext;
	}

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
	protected <M, MV extends M> ValueContainer<M, MV> evaluateInternal2(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView, ArgOption args, TypeToken<?> targetType) throws QonfigInterpretationException {
		if (theContext != null) {
			if (theContext instanceof NameExpression) {
				Class<?> clazz = classView.getType(theContext.toString());
				if (clazz != null) {
					Invocation.MethodResult<Method, ?> result = Invocation.findMethod(clazz.getMethods(), theMethodName,
						TypeTokens.get().of(clazz), true, Arrays.asList(args), targetType, models, classView, Invocation.ExecutableImpl.METHOD);
					if (result != null) {
						ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
						for (int a = 0; a < realArgs.length; a++)
							realArgs[a] = args.args[a].get(0);
						if (type.getModelType() == ModelTypes.Value)
							return (ValueContainer<M, MV>) new InvocationValueContainer<>(result, null, Arrays.asList(realArgs),
								Invocation.ExecutableImpl.METHOD);
						else if (type.getModelType() == ModelTypes.Action)
							return (ValueContainer<M, MV>) new InvocationActionContainer<>(result, null, Arrays.asList(realArgs),
								Invocation.ExecutableImpl.METHOD);
						else {
							TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
							for (int i = 0; i < paramTypes.length; i++)
								paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
							return new InvocationThingContainer<>((Invocation.MethodResult<Method, MV>) result, null, Arrays.asList(realArgs),
								(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes), Invocation.ExecutableImpl.METHOD);
						}
					}
					throw new QonfigInterpretationException("No such method " + printSignature() + " in class " + clazz.getName());
				}
			}
			ValueContainer<SettableValue, SettableValue<?>> ctx = theContext.evaluate(ModelTypes.Value.any(), models, classView);
			Invocation.MethodResult<Method, ?> result = Invocation.findMethod(
				TypeTokens.getRawType(ctx.getType().getType(0)).getMethods(), theMethodName, ctx.getType().getType(0), false,
				Arrays.asList(args), targetType, models, classView, Invocation.ExecutableImpl.METHOD);
			if (result != null) {
				ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				if (type.getModelType() == ModelTypes.Value)
					return (ValueContainer<M, MV>) new InvocationValueContainer<>(result, ctx, Arrays.asList(realArgs),
						Invocation.ExecutableImpl.METHOD);
				else if (type.getModelType() == ModelTypes.Action)
					return (ValueContainer<M, MV>) new InvocationActionContainer<>(result, ctx, Arrays.asList(realArgs),
						Invocation.ExecutableImpl.METHOD);
				else {
					TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
					for (int i = 0; i < paramTypes.length; i++)
						paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
					return new InvocationThingContainer<>((Invocation.MethodResult<Method, MV>) result, ctx, Arrays.asList(realArgs),
						(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes), Invocation.ExecutableImpl.METHOD);
				}
			}
			throw new QonfigInterpretationException("No such method " + printSignature() + " in type " + ctx.getType().getType(0));
		} else {
			List<Method> methods = classView.getImportedStaticMethods(theMethodName);
			Invocation.MethodResult<Method, ?> result = Invocation.findMethod(methods.toArray(new Method[methods.size()]),
				theMethodName, null, true, Arrays.asList(args), targetType, models, classView, Invocation.ExecutableImpl.METHOD);
			if (result != null) {
				ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[getArguments().size()];
				for (int a = 0; a < realArgs.length; a++)
					realArgs[a] = args.args[a].get(0);
				if (type.getModelType() == ModelTypes.Value)
					return (ValueContainer<M, MV>) new InvocationValueContainer<>(result, null, Arrays.asList(realArgs),
						Invocation.ExecutableImpl.METHOD);
				else if (type.getModelType() == ModelTypes.Action)
					return (ValueContainer<M, MV>) new InvocationActionContainer<>(result, null, Arrays.asList(realArgs),
						Invocation.ExecutableImpl.METHOD);
				else {
					TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
					for (int i = 0; i < paramTypes.length; i++)
						paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
					return new InvocationThingContainer<>((Invocation.MethodResult<Method, MV>) result, null, Arrays.asList(realArgs),
						(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes), Invocation.ExecutableImpl.METHOD);
				}
			}
			throw new QonfigInterpretationException("No such imported method " + printSignature());
		}
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
		ClassView classView) throws QonfigInterpretationException {
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
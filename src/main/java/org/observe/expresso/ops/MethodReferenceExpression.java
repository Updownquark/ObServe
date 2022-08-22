package org.observe.expresso.ops;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.TriFunction;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class MethodReferenceExpression implements ObservableExpression {
	private final ObservableExpression theContext;
	private final String theMethodName;
	private final List<String> theTypeArgs;

	public MethodReferenceExpression(ObservableExpression context, String methodName, List<String> typeArgs) {
		theContext = context;
		theMethodName = methodName;
		theTypeArgs = QommonsUtils.unmodifiableCopy(typeArgs);
	}

	public ObservableExpression getContext() {
		return theContext;
	}

	public String getMethodName() {
		return theMethodName;
	}

	public List<String> getTypeArgs() {
		return theTypeArgs;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theContext);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not implemented");
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		return new MethodFinder<P1, P2, P3, T>(targetType) {
			@Override
			public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
				boolean voidTarget = TypeTokens.get().unwrap(TypeTokens.getRawType(targetType)) == void.class;
				if (theContext instanceof NameExpression) {
					Class<?> type = env.getClassView().getType(theContext.toString());
					if (type != null) {
						Invocation.MethodResult<Method, ? extends T> result = Invocation.findMethod(type.getMethods(), theMethodName,
							TypeTokens.get().of(type), true, theOptions, targetType, env, Invocation.ExecutableImpl.METHOD);
						if (result != null) {
							setResultType(result.returnType);
							MethodOption option = theOptions.get(result.argListOption);
							boolean isStatic = (result.method.getModifiers() & Modifier.STATIC) != 0;
							return msi -> (p1, p2, p3) -> {
								Object ctx;
								Object[] args = new Object[option.size()];
								option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
								if (isStatic) {
									ctx = null;
								} else {
									ctx = p1;
									args = ArrayUtils.remove(args, 0);
								}
								try {
									T val = result.invoke(ctx, args, Invocation.ExecutableImpl.METHOD);
									return voidTarget ? null : val;
								} catch (InvocationTargetException e) {
									throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result,
										e.getTargetException());
								} catch (IllegalAccessException | IllegalArgumentException | InstantiationException e) {
									throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result, e);
								} catch (NullPointerException e) {
									NullPointerException npe = new NullPointerException(MethodReferenceExpression.this.toString()//
										+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
									npe.setStackTrace(e.getStackTrace());
									throw npe;
								}
							};
						}
						throw new QonfigInterpretationException(
							"No such method matching: " + MethodReferenceExpression.this + " on class " + type.getName());
					}
				}
				ValueContainer<SettableValue<?>, SettableValue<?>> ctx = theContext.evaluate(ModelTypes.Value.any(), env);
				Invocation.MethodResult<Method, ? extends T> result = Invocation.findMethod(//
					TypeTokens.getRawType(ctx.getType().getType(0)).getMethods(), theMethodName, ctx.getType().getType(0), true,
					theOptions, targetType, env, Invocation.ExecutableImpl.METHOD);
				if (result != null) {
					setResultType(result.returnType);
					MethodOption option = theOptions.get(result.argListOption);
					return msi -> (p1, p2, p3) -> {
						Object ctxV = ctx.apply(msi).get();
						Object[] args = new Object[option.size()];
						option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
						try {
							T val = result.invoke(ctxV, args, Invocation.ExecutableImpl.METHOD);
							return voidTarget ? null : val;
						} catch (InvocationTargetException e) {
							throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result,
								e.getTargetException());
						} catch (IllegalAccessException | IllegalArgumentException | InstantiationException e) {
							throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result, e);
						} catch (NullPointerException e) {
							NullPointerException npe = new NullPointerException(MethodReferenceExpression.this.toString()//
								+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
							npe.setStackTrace(e.getStackTrace());
							throw npe;
						}
					};
				}
				throw new QonfigInterpretationException(
					"No such method matching: " + MethodReferenceExpression.this + " for type " + ctx.getType().getType(0));
			}
		};
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theContext.toString()).append("::");
		if (!theTypeArgs.isEmpty()) {
			str.append('<');
			for (int i = 0; i < theTypeArgs.size(); i++) {
				if (i > 0)
					str.append(", ");
				str.append(theTypeArgs.get(i));
			}
			str.append('>');
		}
		str.append(theMethodName);
		return str.toString();
	}
}
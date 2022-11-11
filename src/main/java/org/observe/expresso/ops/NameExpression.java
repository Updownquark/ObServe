package org.observe.expresso.ops;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** An expression of the form 'name1.name2.name3' */
public class NameExpression implements ObservableExpression {
	private final ObservableExpression theContext;
	private final BetterList<String> theNames;

	/**
	 * @param ctx The expression representing the object the model in which to get the value
	 * @param names The subsequent names in the expression
	 */
	public NameExpression(ObservableExpression ctx, BetterList<String> names) {
		theContext = ctx;
		theNames = names;
	}

	/** @return The expression representing the object the model in which to get the value */
	public ObservableExpression getContext() {
		return theContext;
	}

	/** @return The subsequent names in the expression */
	public BetterList<String> getNames() {
		return theNames;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return theContext == null ? Collections.emptyList() : QommonsUtils.unmodifiableCopy(theContext);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression ctx = theContext == null ? null : theContext.replaceAll(replace);
		if (ctx != theContext)
			return new NameExpression(ctx, theNames);
		return this;
	}

	/* Order of operations:
	 * Model value
	 * Statically-imported variable
	 *
	 */

	@Override
	public <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		ValueContainer<?, ?> mv = null;
		if (theContext != null)
			mv = theContext.evaluate(ModelTypes.Value.any(), env);
		if (mv == null)
			mv = env.getModels().getComponentIfExists(theNames.getFirst());
		if (mv != null)
			return evaluateModel(//
				mv, 1, new StringBuilder(theNames.get(0)), type, env.getModels());
		// Allow unqualified enum value references
		if (theNames.size() == 1 && type.getModelType() == ModelTypes.Value) {
			Class<?> paramType = TypeTokens.getRawType(type.getType(0));
			if (paramType != null && paramType.isEnum()) {
				for (Enum<?> value : ((Class<? extends Enum<?>>) paramType).getEnumConstants()) {
					if (value.name().equals(theNames.getFirst()))
						return (ValueContainer<M, MV>) ValueContainer.literal(TypeTokens.get().of((Class<Object>) paramType), value,
							value.name());
				}
			}
		}
		Field field = env.getClassView().getImportedStaticField(theNames.getFirst());
		if (field != null)
			return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
		StringBuilder typeName = new StringBuilder().append(theNames.get(0));
		Class<?> clazz = env.getClassView().getType(typeName.toString());
		int i;
		for (i = 1; i < theNames.size() - 1; i++) {
			typeName.append(theNames.get(i));
			clazz = env.getClassView().getType(typeName.toString());
		}
		if (clazz == null)
			throw new QonfigInterpretationException("'" + theNames.get(0) + "' cannot be resolved to a variable ");
		try {
			field = clazz.getField(theNames.get(i));
		} catch (NoSuchFieldException e) {
			throw new QonfigInterpretationException("'" + theNames.get(i) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new QonfigInterpretationException(clazz.getName() + "." + theNames.get(i) + " cannot be accessed", e);
		}
		return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
	}

	private <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateModel(ValueContainer<?, ?> mv, int nameIndex,
		StringBuilder path, ModelInstanceType<M, MV> type, ObservableModelSet models) throws QonfigInterpretationException {
		if (nameIndex == theNames.size())
			return models.getValue(toString(), type);
		if (mv.getType().getModelType() == ModelTypes.Model) {
			path.append('.').append(theNames.get(nameIndex));
			String pathStr = path.toString();
			ValueContainer<?, ?> nextMV = models.getComponentIfExists(pathStr);
			if (nextMV != null)
				return evaluateModel(nextMV, nameIndex + 1, path, type, models);
			models.getComponentIfExists(pathStr);// DEBUGGING
			throw new QonfigInterpretationException("'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value");
		} else if (mv.getType().getModelType() == ModelTypes.Value) {
			Field field;
			try {
				field = TypeTokens.getRawType(mv.getType().getType(0)).getField(theNames.get(nameIndex));
			} catch (NoSuchFieldException e) {
				throw new QonfigInterpretationException(getPath(nameIndex) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new QonfigInterpretationException(getPath(nameIndex) + " cannot be accessed", e);
			}
			return evaluateField(field, mv.getType().getType(0).resolveType(field.getGenericType()), //
				(ValueContainer<SettableValue<?>, ? extends SettableValue<?>>) mv, nameIndex, type);
		} else
			throw new QonfigInterpretationException(
				"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mv.getType());
	}

	private <M, MV extends M, F> ValueContainer<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type)
			throws QonfigInterpretationException {
		if (!field.isAccessible()) {
			try {
				field.setAccessible(true);
			} catch (SecurityException e) {
				throw new QonfigInterpretationException("Could not access field " + getPath(nameIndex), e);
			}
		}
		if (nameIndex == theNames.size() - 1) {
			if (type.getModelType() == ModelTypes.Value) {
				return (ValueContainer<M, MV>) getFieldValue(field, fieldType, context, type.getType(0));
			} else
				throw new IllegalStateException("Only Value types supported by fields currently"); // TODO
		}
		Field newField;
		try {
			newField = TypeTokens.getRawType(fieldType).getField(theNames.get(nameIndex));
		} catch (NoSuchFieldException e) {
			throw new QonfigInterpretationException(getPath(nameIndex) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new QonfigInterpretationException(getPath(nameIndex) + " cannot be accessed", e);
		}
		return evaluateField(newField, fieldType.resolveType(newField.getGenericType()), //
			getFieldValue(field, fieldType, context, null), nameIndex + 1, type);
	}

	String getPath(int upToIndex) {
		StringBuilder str = new StringBuilder();
		for (int i = 0; i <= upToIndex; i++) {
			if (i > 0)
				str.append('.');
			str.append(theNames.get(i));
		}
		return str.toString();
	}

	@Override
	public String toString() {
		return StringUtils.print(null, ".", theNames, StringBuilder::append).toString();
	}

	private <F, M> ValueContainer<SettableValue<?>, SettableValue<M>> getFieldValue(Field field, TypeToken<F> fieldType,
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> context, TypeToken<M> targetType)
			throws QonfigInterpretationException {
		ModelInstanceType<SettableValue<?>, SettableValue<F>> fieldModelType = ModelTypes.Value.forType(fieldType);
		ModelInstanceType<SettableValue<?>, SettableValue<M>> targetModelType = ModelTypes.Value.forType(targetType);
		ValueContainer<SettableValue<?>, SettableValue<F>> fieldValue = ValueContainer.of(fieldModelType,
			msi -> new FieldValue<>(context == null ? null : context.get(msi), field, fieldType));
		return fieldModelType.as(fieldValue, targetModelType);
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		boolean voidTarget = TypeTokens.get().unwrap(TypeTokens.getRawType(targetType)) == void.class;
		return new MethodFinder<P1, P2, P3, T>(targetType) {
			@Override
			public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
				ValueContainer<?, ?> mv = env.getModels().getComponentIfExists(toString());
				if (mv != null) {
					for (MethodOption option : theOptions) {
						switch (option.size()) {
						case 0:
							if (mv.getType().getModelType() == ModelTypes.Value) {
								if (targetType.isAssignableFrom(mv.getType().getType(0))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> ((SettableValue<T>) mv.get(msi)).get();
								} else if (TypeTokens.get().isAssignable(targetType, mv.getType().getType(0))) {
									ValueContainer<?, SettableValue<T>> mv2 = env.getModels().getValue(toString(),
										ModelTypes.Value.forType(targetType));
									// TODO resultType
									return msi -> (p1, p2, p3) -> mv2.get(msi).get();
								} else if (Supplier.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))
									&& TypeTokens.get().isAssignable(targetType,
										mv.getType().getType(0).resolveType(Supplier.class.getTypeParameters()[0]))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> ((SettableValue<Supplier<? extends T>>) mv.get(msi)).get().get();
								} else if (voidTarget
									&& Runnable.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> {
										((SettableValue<? extends Runnable>) mv.get(msi)).get().run();
										return null;
									};
								} else
									continue;
							} else if (targetType.isAssignableFrom(TypeTokens.get().keyFor(mv.getType().getModelType().modelType)
								.parameterized(mv.getType().getType(0)))) {
								// TODO resultType
								return msi -> (p1, p2, p3) -> (T) mv.get(msi);
							} else
								continue;
						case 1:
							if (mv.getType().getModelType() == ModelTypes.Value) {
								if (Function.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))
									&& TypeTokens.get().isAssignable(
										mv.getType().getType(0).resolveType(Function.class.getTypeParameters()[0]), option.resolve(0))//
									&& TypeTokens.get().isAssignable(targetType,
										mv.getType().getType(0).resolveType(Function.class.getTypeParameters()[1]))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> {
										Object[] args = new Object[1];
										option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
										return ((SettableValue<Function<Object, ? extends T>>) mv.get(msi)).get().apply(args[0]);
									};
								} else if (voidTarget
									&& Consumer.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> {
										Object[] args = new Object[1];
										option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
										((SettableValue<? extends Consumer<Object>>) mv.get(msi)).get().accept(args[0]);
										return null;
									};
								} else
									continue;
							} else
								continue;
						case 2:
						default:
							// TODO
						}
					}
				} else if (theNames.size() == 1) {
					Invocation.MethodResult<Method, ? extends T> result = Invocation.findMethod(//
						env.getClassView().getImportedStaticMethods(theNames.getFirst()).toArray(new Method[0]), theNames.getFirst(), null,
						false, theOptions, voidTarget ? null : targetType, env, Invocation.ExecutableImpl.METHOD, NameExpression.this);
					if (result != null) {
						setResultType(result.returnType);
						MethodOption option = theOptions.get(result.argListOption);
						return msi -> (p1, p2, p3) -> {
							Object[] args = new Object[option.size()];
							option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
							try {
								return result.invoke(null, args, Invocation.ExecutableImpl.METHOD);
							} catch (InvocationTargetException e) {
								throw new IllegalStateException(NameExpression.this + ": Could not invoke " + result,
									e.getTargetException());
							} catch (IllegalAccessException | IllegalArgumentException | InstantiationException e) {
								throw new IllegalStateException(NameExpression.this + ": Could not invoke " + result, e);
							} catch (NullPointerException e) {
								NullPointerException npe = new NullPointerException(NameExpression.this.toString()//
									+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
								npe.setStackTrace(e.getStackTrace());
								throw npe;
							}
						};
					}
				} else {
					// TODO evaluate model value for names.length-1, then use that context to find a method
					Class<?> type = env.getClassView().getType(getPath(theNames.size() - 2));
					if (type != null) {
						Invocation.MethodResult<Method, ? extends T> result = Invocation.findMethod(//
							type.getMethods(), theNames.getFirst(), null, false, theOptions, voidTarget ? null : targetType, env,
								Invocation.ExecutableImpl.METHOD, NameExpression.this);
						if (result != null) {
							setResultType(result.returnType);
							MethodOption option = theOptions.get(result.argListOption);
							return msi -> (p1, p2, p3) -> {
								Object[] args = new Object[option.size()];
								option.getArgMaker().makeArgs(p1, p2, p3, args, msi);
								try {
									return result.invoke(null, args, Invocation.ExecutableImpl.METHOD);
								} catch (InvocationTargetException e) {
									throw new IllegalStateException(NameExpression.this + ": Could not invoke " + result,
										e.getTargetException());
								} catch (IllegalAccessException | IllegalArgumentException | InstantiationException e) {
									throw new IllegalStateException(NameExpression.this + ": Could not invoke " + result, e);
								} catch (NullPointerException e) {
									NullPointerException npe = new NullPointerException(NameExpression.this.toString()//
										+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
									npe.setStackTrace(e.getStackTrace());
									throw npe;
								}
							};
						}
					}
				}
				throw new QonfigInterpretationException("Could not parse method from " + NameExpression.this.toString());
			}
		};
	}

	static class FieldValue<M, F> extends Identifiable.AbstractIdentifiable implements SettableValue<F> {
		private final SettableValue<?> theContext;
		private final Field theField;
		private final TypeToken<F> theType;
		private final SimpleObservable<Void> theChanges;
		private final ObservableValue<F> theMappedValue;
		private long theStamp;

		FieldValue(SettableValue<?> context, Field field, TypeToken<F> type) {
			theContext = context;
			theField = field;
			theType = type;
			theChanges = SimpleObservable.build().build();
			if (theContext == null)
				theMappedValue = ObservableValue.of(type, this::getStatic, this::getStamp, theChanges);
			else
				theMappedValue = theContext.map(this::getFromContext);
		}

		@Override
		protected Object createIdentity() {
			if (theContext != null)
				return Identifiable.wrap(theContext.getIdentity(), theField.getName());
			else
				return Identifiable.baseId(theField.getName(), theField);
		}

		@Override
		public TypeToken<F> getType() {
			return theType;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public F get() {
			return theMappedValue.get();
		}

		private F getStatic() {
			try {
				return (F) theField.get(null);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new IllegalStateException("Could not access field " + theField.getName(), e);
			}
		}

		private F getFromContext(Object context) {
			if (context == null)
				return TypeTokens.get().getDefaultValue(theType);
			try {
				return (F) theField.get(context);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new IllegalStateException("Could not access field " + theField.getName(), e);
			}
		}

		@Override
		public Observable<ObservableValueEvent<F>> noInitChanges() {
			return theMappedValue.noInitChanges();
		}

		@Override
		public long getStamp() {
			return theStamp + (theContext == null ? 0 : theContext.getStamp());
		}

		@Override
		public ObservableValue<String> isEnabled() {
			if (Modifier.isFinal(theField.getModifiers()))
				return ObservableValue.of("Final field cannot be assigned");
			else if (theContext != null)
				return theContext.map(String.class, ctx -> ctx == null ? "Cannot assign the field of a null value" : null);
			else
				return SettableValue.ALWAYS_ENABLED;
		}

		@Override
		public <V extends F> String isAcceptable(V value) {
			if (Modifier.isFinal(theField.getModifiers()))
				return "Final field cannot be assigned";
			else if (theContext != null && theContext.get() == null)
				return "Cannot assign the field of a null value";
			else
				return null;
		}

		@Override
		public <V extends F> F set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (Modifier.isFinal(theField.getModifiers()))
				throw new UnsupportedOperationException("Final field cannot be assigned");
			Object ctx = theContext == null ? null : theContext.get();
			if (theContext != null && ctx == null)
				throw new UnsupportedOperationException("Cannot assign the field of a null value");
			F previous;
			try {
				previous = (F) theField.get(ctx);
				theField.set(ctx, value);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Could not access field " + theField.getName(), e);
			}
			if (theContext != null && ((SettableValue<Object>) theContext).isAcceptable(ctx) == null)
				((SettableValue<Object>) theContext).set(ctx, cause);
			else {
				theStamp++;
				theChanges.onNext(null);
			}
			return previous;
		}
	}
}
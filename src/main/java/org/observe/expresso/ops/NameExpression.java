package org.observe.expresso.ops;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

/** An expression of the form 'name1.name2.name3' */
public class NameExpression implements ObservableExpression {
	private final ObservableExpression theContext;
	private final BetterList<String> theNames;
	private final int[] theNameOffsets;

	/**
	 * @param ctx The expression representing the object the model in which to get the value
	 * @param names The subsequent names in the expression
	 * @param nameOffsets The starting position of each name in this expression in the root sequence
	 */
	public NameExpression(ObservableExpression ctx, BetterList<String> names, int[] nameOffsets) {
		theContext = ctx;
		theNames = names;
		theNameOffsets = nameOffsets;
	}

	/** @return The expression representing the object the model in which to get the value */
	public ObservableExpression getContext() {
		return theContext;
	}

	/** @return The subsequent names in the expression */
	public BetterList<String> getNames() {
		return theNames;
	}

	/** @return The starting position of each name in this expression in the root sequence */
	public int[] getNameOffsets() {
		return theNameOffsets.clone();
	}

	@Override
	public int getExpressionOffset() {
		if (theContext == null)
			return theNameOffsets[0];
		else
			return theContext.getExpressionOffset();
	}

	@Override
	public int getExpressionEnd() {
		return theNameOffsets[theNameOffsets.length - 1] + theNames.get(theNames.size() - 1).length();
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
			return new NameExpression(ctx, theNames, theNameOffsets);
		return this;
	}

	/* Order of operations:
	 * Model value
	 * Statically-imported variable
	 *
	 */

	@Override
	public <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		ValueContainer<?, ?> mv = null;
		if (theContext != null) {
			try {
				mv = theContext.evaluate(ModelTypes.Value.any(), env);
			} catch (TypeConversionException e) {
				throw new ExpressoEvaluationException(theContext.getExpressionOffset(), theContext.getExpressionEnd(), e.getMessage(), e);
			}
			return evaluateModel(//
				mv, 0, new StringBuilder(), type, env.getModels());
		} else {
			mv = env.getModels().getComponentIfExists(theNames.getFirst());
			if (mv != null)
				return evaluateModel(//
					mv, 1, new StringBuilder(theNames.get(0)), type, env.getModels());
		}
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
			throw new ExpressoEvaluationException(theNameOffsets[0], theNameOffsets[0] + theNames.get(0).length(), //
				"'" + theNames.get(0) + "' cannot be resolved to a variable ");
		try {
			field = clazz.getField(theNames.get(i));
		} catch (NoSuchFieldException e) {
			throw new ExpressoEvaluationException(theNameOffsets[i], theNameOffsets[i] + theNames.get(0).length(), //
				"'" + theNames.get(i) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new ExpressoEvaluationException(theNameOffsets[i], theNameOffsets[i] + theNames.get(0).length(), //
				clazz.getName() + "." + theNames.get(i) + " cannot be accessed", e);
		}
		return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
	}

	private <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateModel(ValueContainer<?, ?> mv, int nameIndex,
		StringBuilder path, ModelInstanceType<M, MV> type, ObservableModelSet models) throws ExpressoEvaluationException {
		ModelInstanceType<?, ?> mvType;
		try {
			mvType = mv.getType();
		} catch (ExpressoInterpretationException e) {
			throw new ExpressoEvaluationException(getExpressionOffset(),
				theNameOffsets[nameIndex - 1] + theNames.get(nameIndex).length(), e.getMessage(), e);
		}
		if (nameIndex == theNames.size()) {
			if (mvType.getModelType() == ModelTypes.Model)
				throw new ExpressoEvaluationException(getExpressionOffset(), getExpressionEnd(),
					this + " is a model, not a " + type.getModelType());
			return (ValueContainer<M, MV>) mv;
		}
		if (mvType.getModelType() == ModelTypes.Model) {
			path.append('.').append(theNames.get(nameIndex));
			String pathStr = path.toString();
			ValueContainer<?, ?> nextMV = models.getComponentIfExists(pathStr);
			if (nextMV != null)
				return evaluateModel(nextMV, nameIndex + 1, path, type, models);
			models.getComponentIfExists(pathStr);// DEBUGGING
			throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
				"'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value");
		} else if (mvType.getModelType() == ModelTypes.Value) {
			Field field;
			try {
				field = TypeTokens.getRawType(mvType.getType(0)).getField(theNames.get(nameIndex));
			} catch (NoSuchFieldException e) {
				throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
					getPath(nameIndex) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
					getPath(nameIndex) + " cannot be accessed", e);
			}
			return evaluateField(field, mvType.getType(0).resolveType(field.getGenericType()), //
				(ValueContainer<SettableValue<?>, ? extends SettableValue<?>>) mv, nameIndex, type);
		} else
			throw new ExpressoEvaluationException(theNameOffsets[nameIndex + 1],
				theNameOffsets[0] + theNames.get(nameIndex + 1).length(), //
				"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mvType);
	}

	private <M, MV extends M, F> ValueContainer<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type)
			throws ExpressoEvaluationException {
		if (!field.isAccessible()) {
			try {
				field.setAccessible(true);
			} catch (SecurityException e) {
				throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
					"Could not access field " + getPath(nameIndex), e);
			}
		}
		if (nameIndex == theNames.size() - 1) {
			if (type.getModelType() == ModelTypes.Value)
				return (ValueContainer<M, MV>) getFieldValue(field, fieldType, context, type.getType(0), nameIndex);
			else {
				try {
					return getFieldValue(field, fieldType, context, TypeTokens.get().WILDCARD, nameIndex).as(type);
				} catch (ExpressoInterpretationException | TypeConversionException e) {
					throw new ExpressoEvaluationException(getExpressionOffset(), getExpressionEnd(), e.getMessage(), e);
				}
			}
		}
		Field newField;
		try {
			newField = TypeTokens.getRawType(fieldType).getField(theNames.get(nameIndex));
		} catch (NoSuchFieldException e) {
			throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
				getPath(nameIndex) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new ExpressoEvaluationException(theNameOffsets[nameIndex], theNameOffsets[0] + theNames.get(nameIndex).length(), //
				getPath(nameIndex) + " cannot be accessed", e);
		}
		return evaluateField(newField, fieldType.resolveType(newField.getGenericType()), //
			getFieldValue(field, fieldType, context, null, nameIndex), nameIndex + 1, type);
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
		StringBuilder str = new StringBuilder();
		if (theContext != null)
			str.append(theContext).append('.');
		return StringUtils.print(str, ".", theNames, StringBuilder::append).toString();
	}

	private <F, M> ValueContainer<SettableValue<?>, SettableValue<M>> getFieldValue(Field field, TypeToken<F> fieldType,
		ValueContainer<SettableValue<?>, ? extends SettableValue<?>> context, TypeToken<M> targetType, int nameIndex)
			throws ExpressoEvaluationException {
		ModelInstanceType<SettableValue<?>, SettableValue<F>> fieldModelType = ModelTypes.Value.forType(fieldType);
		ModelInstanceType<SettableValue<?>, SettableValue<M>> targetModelType = ModelTypes.Value.forType(targetType);
		ValueContainer<SettableValue<?>, SettableValue<F>> fieldValue = ValueContainer.of(fieldModelType,
			msi -> new FieldValue<>(context == null ? null : context.get(msi), field, fieldType));
		try {
			return fieldValue.as(targetModelType);
		} catch (ExpressoInterpretationException | TypeConversionException e) {
			throw new ExpressoEvaluationException(getExpressionOffset(), theNameOffsets[nameIndex] + theNames.get(nameIndex).length(),
				e.getMessage(), e);
		}
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
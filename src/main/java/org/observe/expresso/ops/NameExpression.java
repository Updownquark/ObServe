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
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

/** An expression of the form 'name1.name2.name3' */
public class NameExpression implements ObservableExpression, Named {
	private final ObservableExpression theContext;
	private final BetterList<BufferedName> theNames;

	/**
	 * @param ctx The expression representing the object the model in which to get the value
	 * @param names The subsequent names in the expression
	 */
	public NameExpression(ObservableExpression ctx, BetterList<BufferedName> names) {
		theContext = ctx;
		theNames = names;
	}

	/** @return The expression representing the object the model in which to get the value */
	public ObservableExpression getContext() {
		return theContext;
	}

	/** @return The subsequent names in the expression */
	public BetterList<BufferedName> getNames() {
		return theNames;
	}

	@Override
	public String getName() {
		StringBuilder str = new StringBuilder();
		if (theContext != null)
			str.append(theContext).append('.');
		return StringUtils.print(str, ".", theNames, (s, name) -> s.append(name.getName())).toString();
	}

	/**
	 * @param name The index of the name to get the offset for
	 * @return The offset in this expression of the given name
	 */
	public int getNameOffset(int name) {
		int offset = 0;
		if (theContext != null)
			offset += theContext.getExpressionLength() + 1;
		if (name > 1)
			offset += name - 1;
		for (int n = 0; n < name - 1; n++)
			offset += theNames.get(n).length();
		return offset;
	}

	@Override
	public int getChildOffset(int childIndex) {
		if (theContext == null)
			throw new IndexOutOfBoundsException(childIndex + " of 0");
		else if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return 0;
	}

	@Override
	public int getExpressionLength() {
		int length = 0;
		if (theContext != null)
			length += theContext.getExpressionLength() + 1;
		if (theNames.size() > 1)
			length += theNames.size() - 1;
		for (BufferedName name : theNames)
			length += name.length();
		return length;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return theContext == null ? Collections.emptyList() : Collections.singletonList(theContext);
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		if (theContext != null)
			return ModelTypes.Value; // Just gotta guess
		ModelComponentNode<?, ?> mv = env.getModels().getComponentIfExists(StringUtils.print(".", theNames, n -> n.getName()).toString());
		if (mv != null)
			return mv.getModelType();
		return ModelTypes.Value; // Guess
	}

	@Override
	public <M, MV extends M> ObservableModelSet.ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		ModelValueSynth<?, ?> mv = null;
		if (theContext != null) {
			try {
				mv = theContext.evaluate(ModelTypes.Value.any(), env, expressionOffset);
			} catch (TypeConversionException e) {
				throw new ExpressoEvaluationException(expressionOffset, theContext.getExpressionLength(), e.getMessage(), e);
			}
			return evaluateModel(//
				mv, 0, new StringBuilder(), type, env.getModels(), expressionOffset);
		} else {
			mv = env.getModels().getComponentIfExists(theNames.getFirst().getName());
			if (mv != null)
				return evaluateModel(//
					mv, 1, new StringBuilder(theNames.get(0).getName()), type, env.getModels(), expressionOffset);
		}
		// Allow unqualified enum value references
		if (theNames.size() == 1 && type.getModelType() == ModelTypes.Value) {
			Class<?> paramType = TypeTokens.getRawType(type.getType(0));
			if (paramType != null && paramType.isEnum()) {
				for (Enum<?> value : ((Class<? extends Enum<?>>) paramType).getEnumConstants()) {
					if (value.name().equals(theNames.getFirst().getName()))
						return (ModelValueSynth<M, MV>) ModelValueSynth.literal(TypeTokens.get().of((Class<Object>) paramType), value,
							value.name());
				}
			}
		}
		Field field = env.getClassView().getImportedStaticField(theNames.getFirst().getName());
		if (field != null)
			return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type, expressionOffset);
		StringBuilder typeName = new StringBuilder().append(theNames.get(0).getName());
		Class<?> clazz = env.getClassView().getType(typeName.toString());
		int i;
		for (i = 1; i < theNames.size() - 1; i++) {
			typeName.append(theNames.get(i).getName());
			clazz = env.getClassView().getType(typeName.toString());
		}
		if (clazz == null)
			throw new ExpressoEvaluationException(getNameOffset(0), getNameOffset(0) + theNames.get(0).length(), //
				"'" + theNames.get(0) + "' cannot be resolved to a variable ");
		try {
			field = clazz.getField(theNames.get(i).getName());
		} catch (NoSuchFieldException e) {
			throw new ExpressoEvaluationException(getNameOffset(i), getNameOffset(i) + theNames.get(0).length(), //
				"'" + theNames.get(i) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new ExpressoEvaluationException(getNameOffset(i), getNameOffset(i) + theNames.get(0).length(), //
				clazz.getName() + "." + theNames.get(i) + " cannot be accessed", e);
		}
		return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type, expressionOffset);
	}

	private <M, MV extends M> ObservableModelSet.ModelValueSynth<M, MV> evaluateModel(ModelValueSynth<?, ?> mv, int nameIndex,
		StringBuilder path, ModelInstanceType<M, MV> type, ObservableModelSet models, int expressionOffset)
			throws ExpressoEvaluationException {
		ModelInstanceType<?, ?> mvType;
		try {
			mvType = mv.getType();
		} catch (ExpressoInterpretationException e) {
			throw new ExpressoEvaluationException(expressionOffset, getNameOffset(nameIndex - 1) + theNames.get(nameIndex).length(),
				e.getMessage(), e);
		}
		if (nameIndex == theNames.size()) {
			if (mvType.getModelType() == ModelTypes.Model)
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					this + " is a model, not a " + type.getModelType());
			return (ModelValueSynth<M, MV>) mv;
		}
		if (mvType.getModelType() == ModelTypes.Model) {
			path.append('.').append(theNames.get(nameIndex).getName());
			String pathStr = path.toString();
			ModelValueSynth<?, ?> nextMV = models.getComponentIfExists(pathStr);
			if (nextMV != null)
				return evaluateModel(nextMV, nameIndex + 1, path, type, models, expressionOffset);
			models.getComponentIfExists(pathStr);// DEBUGGING
			throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
				"'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value");
		} else if (mvType.getModelType() == ModelTypes.Value) {
			Field field;
			try {
				field = TypeTokens.getRawType(mvType.getType(0)).getField(theNames.get(nameIndex).getName());
			} catch (NoSuchFieldException e) {
				throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
					getPath(nameIndex) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
					getPath(nameIndex) + " cannot be accessed", e);
			}
			return evaluateField(field, mvType.getType(0).resolveType(field.getGenericType()), //
				(ModelValueSynth<SettableValue<?>, ? extends SettableValue<?>>) mv, nameIndex, type, expressionOffset);
		} else
			throw new ExpressoEvaluationException(getNameOffset(nameIndex + 1), getNameOffset(0) + theNames.get(nameIndex + 1).length(), //
				"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mvType);
	}

	private <M, MV extends M, F> ModelValueSynth<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
		ModelValueSynth<SettableValue<?>, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type,
			int expressionOffset) throws ExpressoEvaluationException {
		if (!field.isAccessible()) {
			try {
				field.setAccessible(true);
			} catch (SecurityException e) {
				throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
					"Could not access field " + getPath(nameIndex), e);
			}
		}
		if (nameIndex == theNames.size() - 1) {
			if (type.getModelType() == ModelTypes.Value)
				return (ModelValueSynth<M, MV>) getFieldValue(field, fieldType, context, type.getType(0), nameIndex, expressionOffset);
			else {
				try {
					return getFieldValue(field, fieldType, context, TypeTokens.get().WILDCARD, nameIndex, expressionOffset).as(type);
				} catch (ExpressoInterpretationException | TypeConversionException e) {
					throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), e.getMessage(), e);
				}
			}
		}
		Field newField;
		try {
			newField = TypeTokens.getRawType(fieldType).getField(theNames.get(nameIndex).getName());
		} catch (NoSuchFieldException e) {
			throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
				getPath(nameIndex) + "' cannot be resolved or is not a field");
		} catch (SecurityException e) {
			throw new ExpressoEvaluationException(getNameOffset(nameIndex), getNameOffset(0) + theNames.get(nameIndex).length(), //
				getPath(nameIndex) + " cannot be accessed", e);
		}
		return evaluateField(newField, fieldType.resolveType(newField.getGenericType()), //
			getFieldValue(field, fieldType, context, null, nameIndex, expressionOffset), nameIndex + 1, type, expressionOffset);
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

	private <F, M> ModelValueSynth<SettableValue<?>, SettableValue<M>> getFieldValue(Field field, TypeToken<F> fieldType,
		ModelValueSynth<SettableValue<?>, ? extends SettableValue<?>> context, TypeToken<M> targetType, int nameIndex, int expressionOffset)
			throws ExpressoEvaluationException {
		ModelInstanceType<SettableValue<?>, SettableValue<F>> fieldModelType = ModelTypes.Value.forType(fieldType);
		ModelInstanceType<SettableValue<?>, SettableValue<M>> targetModelType = ModelTypes.Value.forType(targetType);
		ModelValueSynth<SettableValue<?>, SettableValue<F>> fieldValue = ModelValueSynth.of(fieldModelType,
			msi -> new FieldValue<>(context == null ? null : context.get(msi), field, fieldType));
		try {
			return fieldValue.as(targetModelType);
		} catch (ExpressoInterpretationException | TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, getNameOffset(nameIndex) + theNames.get(nameIndex).length(),
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
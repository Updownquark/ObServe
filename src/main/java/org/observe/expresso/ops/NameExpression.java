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
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretableModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;

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

	@Override
	public int getDivisionCount() {
		return theNames.size();
	}

	@Override
	public int getDivisionOffset(int division) {
		int offset = 0;
		if (theContext != null)
			offset += theContext.getExpressionLength() + 1;
		if (division > 0)
			offset += division;
		for (int n = 0; n < division; n++)
			offset += theNames.get(n).length();
		return offset;
	}

	@Override
	public int getDivisionLength(int division) {
		return theNames.get(division).length();
	}

	@Override
	public int getComponentOffset(int childIndex) {
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
	public List<? extends ObservableExpression> getComponents() {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) throws ExpressoCompilationException {
		if (theContext != null)
			return ModelTypes.Value; // Just gotta guess
		ModelComponentNode<?> mv = env.getModels().getComponentIfExists(StringUtils.print(".", theNames, n -> n.getName()).toString());
		if (mv != null)
			return mv.getModelType(env);
		return ModelTypes.Value; // Guess
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		EvaluatedExpression<?, ?>[] divisions = new EvaluatedExpression[theNames.size()];
		InterpretedValueSynth<?, ?> mv = null;
		if (theContext != null) {
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
				.stack(ExceptionHandler.holder());
			mv = theContext.evaluate(ModelTypes.Value.any(), env, expressionOffset, doubleX);
			if (doubleX.get2() != null) {
				exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
					theContext.getExpressionLength(), doubleX.get2()));
				return null;
			} else if (mv == null)
				return null;
			return evaluateModel(//
				mv, 0, new StringBuilder(), type, env.getModels(), expressionOffset + theContext.getExpressionLength() + 1,
				env.reporting().at(theContext.getExpressionLength() + 1), divisions, (EvaluatedExpression<?, ?>) mv, env, exHandler);
		} else {
			InterpretableModelComponentNode<?> interpretable = env.getModels().getComponentIfExists(theNames.getFirst().getName());
			if (interpretable != null)
				return evaluateModel(//
					interpretable.interpreted(), 1, new StringBuilder(theNames.get(0).getName()), type, env.getModels(), expressionOffset,
					env.reporting(), divisions, null, env, exHandler);
		}
		// Allow unqualified enum value references
		if (theNames.size() == 1 && type.getModelType() == ModelTypes.Value) {
			Class<?> paramType = TypeTokens.getRawType(type.getType(0));
			if (paramType != null && paramType.isEnum()) {
				for (Enum<?> value : ((Class<? extends Enum<?>>) paramType).getEnumConstants()) {
					if (value.name().equals(theNames.getFirst().getName()))
						return (EvaluatedExpression<M, MV>) ObservableExpression.evEx(expressionOffset, getExpressionLength(),
							InterpretedValueSynth.literalValue(TypeTokens.get().of((Class<Object>) paramType), value, value.name()), value);
				}
			}
		}
		EvaluatedExpression<M, MV> fieldValue;
		Field field = env.getClassView().getImportedStaticField(theNames.getFirst().getName());
		if (field != null) {
			fieldValue = evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 0, type, expressionOffset, env.reporting(),
				divisions, env, exHandler);
		} else {
			StringBuilder typeName = new StringBuilder().append(theNames.get(0).getName());
			Class<?> clazz = env.getClassView().getType(typeName.toString());
			int i;
			for (i = 1; i < theNames.size() - 1; i++) {
				typeName.append('.').append(theNames.get(i).getName());
				clazz = env.getClassView().getType(typeName.toString());
			}
			if (clazz == null) {
				exHandler.handle1(new ExpressoInterpretationException("'" + theNames.get(0) + "' cannot be resolved to a variable",
					env.reporting().getPosition(), theNames.get(0).length()));
				return null;
			}
			try {
				field = clazz.getField(theNames.get(i).getName());
			} catch (NoSuchFieldException e) {
				exHandler.handle1(new ExpressoInterpretationException("'" + theNames.get(0) + "' cannot be resolved or is not a field",
					env.reporting().at(getDivisionOffset(i)).getPosition(), theNames.get(0).length(), e));
				return null;
			} catch (SecurityException e) {
				exHandler.handle1(new ExpressoInterpretationException(clazz.getName() + "." + theNames.get(i) + " cannot be accessed",
					env.reporting().at(getDivisionOffset(i)).getPosition(), theNames.get(0).length(), e));
				return null;
			}
			fieldValue = evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, i, type, expressionOffset, env.reporting(),
				divisions, env, exHandler);
			EvaluatedExpression<?, ?> classValue = ObservableExpression.evEx(expressionOffset, getExpressionLength(), InterpretedValueSynth
				.literalValue(TypeTokens.get().keyFor(Class.class).<Class<?>> parameterized(clazz), clazz, typeName.toString()), clazz);
			for (int d = 0; d < i; d++)
				divisions[d] = classValue;
		}
		return ObservableExpression.evEx2(expressionOffset, getExpressionLength(), fieldValue, null, Collections.emptyList(),
			QommonsUtils.unmodifiableCopy(divisions));
	}

	private <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateModel(InterpretedValueSynth<?, ?> mv, int nameIndex,
		StringBuilder path, ModelInstanceType<M, MV> type, InterpretedModelSet models, int expressionOffset, ErrorReporting reporting,
		EvaluatedExpression<?, ?>[] divisions, EvaluatedExpression<?, ?> context, InterpretedExpressoEnv env,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		ModelType<?> mvType = mv.getModelType();
		if (nameIndex == theNames.size()) {
			if (mvType == ModelTypes.Model)
				throw new ExpressoInterpretationException(this + " is a model, not a " + type.getModelType(), reporting.getPosition(),
					theNames.getLast().length());
			InterpretedValueSynth<M, MV> imv = (InterpretedValueSynth<M, MV>) mv;
			if (nameIndex > 0)
				divisions[nameIndex - 1] = ObservableExpression.evEx(expressionOffset, getExpressionLength(), imv, mv);
			Object descriptor = imv instanceof ObservableModelSet.InterpretedModelComponentNode
				? ((ObservableModelSet.InterpretedModelComponentNode<?, ?>) imv).getValueIdentity() : null;
			return ObservableExpression.evEx2(expressionOffset, getExpressionLength(), imv, descriptor,
				context == null ? Collections.emptyList() : Collections.singletonList(context), QommonsUtils.unmodifiableCopy(divisions));
		} else if (mvType == ModelTypes.Model) {
			String modelStr = path.toString();
			ObservableModelSet model = models.getSubModelIfExists(modelStr);
			if (nameIndex > 0)
				divisions[nameIndex - 1] = ObservableExpression.evEx(expressionOffset, getExpressionLength(),
					InterpretedValueSynth.literalValue(TypeTokens.get().of(ObservableModelSet.class), model, modelStr), mv);
			path.append('.').append(theNames.get(nameIndex).getName());
			String pathStr = path.toString();
			InterpretableModelComponentNode<?> nextMV = models.getComponentIfExists(pathStr);
			if (nextMV != null) {
				return evaluateModel(//
					nextMV.interpreted(), nameIndex + 1, path, type, models, expressionOffset,
					reporting.at(theNames.get(nameIndex).length()), divisions, context, env, exHandler);
			} else
				throw new ExpressoInterpretationException("'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value",
					reporting.getPosition(), theNames.get(nameIndex).length());
		} else if (mvType == ModelTypes.Value) {
			InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> imv = (InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>>) mv;
			ModelInstanceType<SettableValue<?>, ? extends SettableValue<?>> instType = imv.getType();
			if (nameIndex > 0)
				divisions[nameIndex - 1] = ObservableExpression.evEx(expressionOffset, getExpressionLength(), imv, mv);
			Class<?> ctxType = TypeTokens.getRawType(instType.getType(0));
			Field field;
			try {
				field = ctxType.getField(theNames.get(nameIndex).getName());
			} catch (NoSuchFieldException e) {
				exHandler.handle1(new ExpressoInterpretationException(
					"'" + getPath(nameIndex) + "' cannot be resolved or is not a field of " + ctxType.getName(), reporting.getPosition(),
					theNames.get(nameIndex).length()));
				return null;
			} catch (SecurityException e) {
				exHandler.handle1(new ExpressoInterpretationException(getPath(nameIndex) + " cannot be accessed", reporting.getPosition(),
					theNames.get(nameIndex).length(), e));
				return null;
			}
			return evaluateField(field, instType.getType(0).resolveType(field.getGenericType()), //
				imv, nameIndex, type, expressionOffset, reporting.at(theNames.get(nameIndex).length() + 1), divisions, env, exHandler);
		} else
			throw new ExpressoInterpretationException(
				"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mvType, reporting.getPosition(),
				theNames.get(nameIndex + 1).length());
	}

	private <M, MV extends M, F, EX extends Throwable> EvaluatedExpression<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
		InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type,
			int expressionOffset, ErrorReporting reporting, EvaluatedExpression<?, ?>[] divisions, InterpretedExpressoEnv env,
			ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
		if (!field.isAccessible()) {
			try {
				field.setAccessible(true);
			} catch (SecurityException e) {
				exHandler.handle1(new ExpressoInterpretationException("Could not access field " + getPath(nameIndex),
					reporting.getPosition(), theNames.get(nameIndex).length(), e));
				return null;
			}
		}
		EvaluatedExpression<SettableValue<?>, ? extends SettableValue<?>> fieldValue = getFieldValue(field, fieldType, context, nameIndex,
			expressionOffset, reporting, env);
		divisions[nameIndex] = fieldValue;
		EvaluatedExpression<M, MV> value;
		if (nameIndex == theNames.size() - 1) {
			if (type.getModelType() == ModelTypes.Value)
				value = (EvaluatedExpression<M, MV>) fieldValue;
			else {
				ExceptionHandler.Single<TypeConversionException, NeverThrown> tce = ExceptionHandler.holder();
				value = ObservableExpression.evEx2(expressionOffset, getExpressionLength(),
					fieldValue.as(type, env, tce), fieldValue.getDescriptor(),
					fieldValue.getComponents(), fieldValue.getDivisions());
				if (tce.hasException()) {
					exHandler.handle1(new ExpressoInterpretationException(tce.get1().getMessage(), reporting.getPosition(),
						theNames.get(nameIndex).length(), tce.get1()));
					return null;
				}
			}
		} else {
			Field newField;
			try {
				newField = TypeTokens.getRawType(fieldType).getField(theNames.get(nameIndex).getName());
			} catch (NoSuchFieldException e) {
				exHandler.handle1(new ExpressoInterpretationException(getPath(nameIndex) + "' cannot be resolved or is not a field",
					reporting.getPosition(), theNames.get(nameIndex).length(), e));
				return null;
			} catch (SecurityException e) {
				exHandler.handle1(new ExpressoInterpretationException(getPath(nameIndex) + " cannot be accessed", reporting.getPosition(),
					theNames.get(nameIndex).length(), e));
				return null;
			}
			value = evaluateField(newField, fieldType.resolveType(newField.getGenericType()), //
				fieldValue, nameIndex + 1, type, expressionOffset, reporting.at(theNames.get(nameIndex).length() + 1), divisions, env,
				exHandler);
		}
		return value;
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

	private <F, M> EvaluatedExpression<SettableValue<?>, SettableValue<M>> getFieldValue(Field field, TypeToken<F> fieldType,
		InterpretedValueSynth<SettableValue<?>, ?> context, int nameIndex, int expressionOffset, ErrorReporting reporting,
		InterpretedExpressoEnv env) {
		ModelInstanceType<SettableValue<?>, SettableValue<F>> fieldModelType = ModelTypes.Value.forType(fieldType);
		InterpretedValueSynth<SettableValue<?>, SettableValue<F>> fieldValue = InterpretedValueSynth.of(fieldModelType,
			() -> new FieldInstantiator<>(//
				context == null ? null : ((InterpretedValueSynth<SettableValue<?>, SettableValue<Object>>) context).instantiate(), field,
					fieldType, reporting));
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			(InterpretedValueSynth<SettableValue<?>, SettableValue<M>>) (InterpretedValueSynth<?, ?>) fieldValue, null);
	}

	static class FieldInstantiator<C, F> implements ModelValueInstantiator<SettableValue<F>> {
		private final ModelValueInstantiator<SettableValue<C>> theContext;
		private final Field theField;
		private final TypeToken<F> theType;
		private final ErrorReporting theReporting;

		FieldInstantiator(ModelValueInstantiator<SettableValue<C>> context, Field field, TypeToken<F> type, ErrorReporting reporting) {
			theContext = context;
			theField = field;
			theType = type;
			theReporting = reporting;
		}

		@Override
		public void instantiate() {
			if (theContext != null)
				theContext.instantiate();
		}

		@Override
		public SettableValue<F> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<C> ctx = theContext == null ? null : theContext.get(models);
			return new FieldValue<>(ctx, theField, theType, theReporting);
		}

		@Override
		public SettableValue<F> forModelCopy(SettableValue<F> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			if (theContext == null)
				return value;
			SettableValue<C> oldCtx = theContext.get(sourceModels);
			SettableValue<C> newCtx = theContext.forModelCopy(oldCtx, sourceModels, newModels);
			if (oldCtx == newCtx)
				return value;
			return new FieldValue<>(newCtx, theField, theType, theReporting);
		}

		@Override
		public String toString() {
			if (theContext != null)
				return theContext + "." + theField.getName();
			else
				return theField.getDeclaringClass().getName() + "." + theField.getName();
		}
	}

	static class FieldValue<F> extends Identifiable.AbstractIdentifiable implements SettableValue<F> {
		private final SettableValue<?> theContext;
		private final Field theField;
		private final boolean isFinal;
		private final TypeToken<F> theType;
		private final SimpleObservable<Void> theChanges;
		private final ObservableValue<F> theMappedValue;
		private final ErrorReporting theReporting;
		private long theStamp;

		FieldValue(SettableValue<?> context, Field field, TypeToken<F> type, ErrorReporting reporting) {
			theContext = context;
			theField = field;
			isFinal = Modifier.isFinal(theField.getModifiers());
			theType = type;
			theChanges = SimpleObservable.build().build();
			if (theContext == null)
				theMappedValue = ObservableValue.of(type, LambdaUtils.printableSupplier(this::getStatic, theField::getName, null),
					this::getStamp, theChanges);
			else
				theMappedValue = theContext.transform(theType, tx -> tx.cache(isFinal).map(//
					LambdaUtils.printableFn(this::getFromContext, theField.getName(), null)));
			theReporting = reporting;
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
				theReporting.error("Could not access field " + theField.getName(), e);
				return null;
			}
		}

		private F getFromContext(Object context) {
			if (context == null)
				return TypeTokens.get().getDefaultValue(theType);
			try {
				return (F) theField.get(context);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				theReporting.error("Could not access field " + theField.getName(), e);
				return null;
			}
		}

		@Override
		public Observable<ObservableValueEvent<F>> noInitChanges() {
			return theMappedValue.noInitChanges();
		}

		@Override
		public long getStamp() {
			if (!isFinal)
				theStamp++; // Could have changed, gotta make sure we can always see the current value
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
			if (theContext != null && ctx == null) {
				theReporting.error("Cannot assign the field of a null value");
				return value;
			}
			F previous;
			try {
				previous = (F) theField.get(ctx);
				theField.set(ctx, value);
			} catch (IllegalAccessException e) {
				theReporting.error("Could not access field " + theField.getName(), e);
				return value;
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
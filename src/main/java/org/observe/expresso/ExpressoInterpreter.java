package org.observe.expresso;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.StatusReportAccumulator;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

public abstract class ExpressoInterpreter<QIS extends ExpressoInterpreter.ExpressoSession<?>> extends QonfigInterpreter<QIS> {
	public static abstract class ExpressoSession<QIS extends ExpressoSession<QIS>>
	extends QonfigInterpreter.QonfigInterpretingSession<QIS> {
		private ExpressoEnv theEnv;

		protected ExpressoSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			super(parent, element, type, childIndex);
			theEnv = ((ExpressoSession<?>) parent).theEnv;
		}

		protected ExpressoSession(ExpressoInterpreter<QIS> interpreter, QonfigElement root, ExpressoEnv env)
			throws QonfigInterpretationException {
			super(interpreter, root);
			theEnv = env;
		}

		@Override
		public ExpressoInterpreter<QIS> getInterpreter() {
			return (ExpressoInterpreter<QIS>) super.getInterpreter();
		}

		public ExpressoEnv getExpressoEnv() {
			return theEnv;
		}

		public QIS setModels(ObservableModelSet models, ClassView classView) {
			theEnv = theEnv.with(models, classView);
			return (QIS) this;
		}

		public <M, MV extends M> ValueContainer<M, MV> getAttribute(String attrName, ModelInstanceType<M, MV> type,
			Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
			ObservableExpression expression = getAttribute(attrName, ObservableExpression.class);
			if (expression == null) {
				return new ValueContainer<M, MV>() {
					final Function<ModelSetInstance, MV> def = defaultValue.get();

					@Override
					public ModelInstanceType<M, MV> getType() {
						return type;
					}

					@Override
					public MV get(ModelSetInstance models) {
						return def.apply(models);
					}
				};
			}
			return expression.evaluate(type, theEnv);
		}

		public <M, MV extends M> ValueContainer<M, MV> getValue(ModelInstanceType<M, MV> type,
			Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
			Object value = getElement().getValue();
			if (value == null) {
				if (defaultValue == null)
					return null;
				return new ValueContainer<M, MV>() {
					final Function<ModelSetInstance, MV> def = defaultValue.get();

					@Override
					public ModelInstanceType<M, MV> getType() {
						return type;
					}

					@Override
					public MV get(ModelSetInstance models) {
						return def.apply(models);
					}
				};
			} else if (!(value instanceof ObservableExpression))
				throw new QonfigInterpretationException(
					"Value of " + getElement() + " is a " + value.getClass().getName() + ", not an expression");
			return ((ObservableExpression) value).evaluate(type, theEnv);
		}

		public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(TypeToken<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getValue(ModelTypes.Value.forType(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(Class<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getValueAsValue(TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName,
			TypeToken<T> type, Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
				throws QonfigInterpretationException {
			return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, Class<T> type,
			Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
			return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
			Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
			return getValue(ModelTypes.Collection.forType(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(Class<T> type,
			Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
			return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
		}
	}

	private final ExpressoEnv theExpressoEnv;

	protected ExpressoInterpreter(Class<?> callingClass,
		Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
		Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv env) {
		super(callingClass, creators, modifiers);
		theExpressoEnv = env;
	}

	public ExpressoEnv getExpressoEnv() {
		return theExpressoEnv;
	}

	/**
	 * Builds an interpreter
	 *
	 * @param callingClass The class invoking this interpretation--may be needed to access resources on the classpath
	 * @param toolkits The toolkits that the interpreter will be able to interpret documents of
	 * @return A builder
	 */
	public static Builder<?, ?> build(Class<?> callingClass, QonfigToolkit... toolkits) {
		return new DefaultBuilder(callingClass, null, toolkits);
	}

	public static abstract class Builder<QIS extends ExpressoSession<?>, B extends Builder<QIS, B>>
	extends QonfigInterpreter.Builder<QIS, B> {
		private ExpressoEnv theExpressoEnv;

		protected Builder(Class<?> callingClass, ExpressoEnv expressoEnv, QonfigToolkit... toolkits) {
			super(callingClass, toolkits);
			theExpressoEnv = expressoEnv;
		}

		protected Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv expressoEnv) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers);
			theExpressoEnv = expressoEnv;
		}

		public ExpressoEnv getExpressoEnv() {
			return theExpressoEnv;
		}

		protected ExpressoEnv getOrCreateExpressoEnv() {
			if (theExpressoEnv == null)
				theExpressoEnv = createExpressoEnv();
			return theExpressoEnv;
		}

		protected ExpressoEnv createExpressoEnv() {
			return new ExpressoEnv(null, null, //
				UnaryOperatorSet.build().withStandardJavaOps().build(), //
				BinaryOperatorSet.STANDARD_JAVA)//
				.withDefaultNonStructuredParsing();
		}

		public B withExpresoEnv(ExpressoEnv expressoEnv) {
			theExpressoEnv = expressoEnv;
			return (B) this;
		}

		@Override
		protected B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers) {
			return builderFor(callingClass, toolkits, toolkit, status, creators, modifiers, theExpressoEnv);
		}

		protected abstract B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv env);

		@Override
		public abstract ExpressoInterpreter<QIS> create();

		@Override
		public ExpressoInterpreter<QIS> build() {
			return (ExpressoInterpreter<QIS>) super.build();
		}
	}

	public static class ExpressoSessionDefault extends ExpressoSession<ExpressoSessionDefault> {
		ExpressoSessionDefault(ExpressoSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			super(parent, element, type, childIndex);
		}

		ExpressoSessionDefault(ExpressoInterpreter<ExpressoSessionDefault> interpreter, QonfigElement root, ExpressoEnv expressoEnv)
			throws QonfigInterpretationException {
			super(interpreter, root, expressoEnv);
		}
	}

	public static class Default extends ExpressoInterpreter<ExpressoSessionDefault> {
		Default(Class<?> callingClass,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<ExpressoSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoEnv expressoEnv) {
			super(callingClass, creators, modifiers, expressoEnv);
		}

		@Override
		public ExpressoSessionDefault interpret(QonfigElement element) throws QonfigInterpretationException {
			return new ExpressoSessionDefault(this, element, getExpressoEnv());
		}

		@Override
		protected ExpressoSessionDefault interpret(ExpressoSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type,
			int childIndex) throws QonfigInterpretationException {
			return new ExpressoSessionDefault(parent, element, type, childIndex);
		}
	}

	public static class DefaultBuilder extends Builder<ExpressoSessionDefault, DefaultBuilder> {
		DefaultBuilder(Class<?> callingClass, ExpressoEnv expressoEnv, QonfigToolkit... toolkits) {
			super(callingClass, expressoEnv, toolkits);
		}

		DefaultBuilder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<ExpressoSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoEnv expressoEnv) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv);
		}

		@Override
		protected DefaultBuilder builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<ExpressoSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoEnv expressoEnv) {
			return new DefaultBuilder(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv);
		}

		@Override
		public Default create() {
			return new Default(getCallingClass(), getCreators(), getModifiers(), getOrCreateExpressoEnv());
		}
	}
}

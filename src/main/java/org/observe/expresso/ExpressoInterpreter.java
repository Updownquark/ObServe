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
import org.observe.util.TypeTokens;
import org.qommons.StatusReportAccumulator;
import org.qommons.SubClassMap2;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

public abstract class ExpressoInterpreter<QIS extends ExpressoInterpreter.ExpressoSession<QIS>> extends QonfigInterpreter<QIS> {
	public static abstract class ExpressoSession<QIS extends ExpressoSession<QIS>>
	extends QonfigInterpreter.QonfigInterpretingSession<QIS> {
		private ObservableModelSet theModels;
		private ClassView theClassView;

		protected ExpressoSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex) {
			super(parent, element, type, childIndex);
			theModels = ((ExpressoSession<?>) parent).theModels;
			theClassView = ((ExpressoSession<?>) parent).theClassView;
		}

		protected ExpressoSession(QonfigInterpreter<QIS> interpreter, QonfigElement root) {
			super(interpreter, root);
		}

		@Override
		public ExpressoInterpreter<QIS> getInterpreter() {
			return (ExpressoInterpreter<QIS>) super.getInterpreter();
		}

		public ObservableModelSet getModels() {
			return theModels;
		}

		public QIS setModels(ObservableModelSet models) {
			theModels = models;
			return (QIS) this;
		}

		public ClassView getClassView() {
			return theClassView;
		}

		public void setClassView(ClassView classView) {
			theClassView = classView;
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
			return expression.evaluate(type, theModels, theClassView);
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
			}
			else if (!(value instanceof ObservableExpression))
				throw new QonfigInterpretationException(
					"Value of " + getElement() + " is a " + value.getClass().getName() + ", not an expression");
			return ((ObservableExpression) value).evaluate(type, theModels, theClassView);
		}

		public <T> ValueContainer<SettableValue, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue, SettableValue<T>> getValueAsValue(TypeToken<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getValue(ModelTypes.Value.forType(type), defaultValue);
		}

		public <T> ValueContainer<SettableValue, SettableValue<T>> getValueAsValue(Class<T> type,
			Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
			return getValueAsValue(TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection, ObservableCollection<T>> getAttributeAsCollection(String attrName,
			TypeToken<T> type, Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
				throws QonfigInterpretationException {
			return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection, ObservableCollection<T>> getAttributeAsCollection(String attrName,
			Class<T> type, Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue)
				throws QonfigInterpretationException {
			return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
			Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
			return getValue(ModelTypes.Collection.forType(type), defaultValue);
		}

		public <T> ValueContainer<ObservableCollection, ObservableCollection<T>> getValueAsCollection(Class<T> type,
			Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
			return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
		}
	}

	private final ExpressoParser theExpressionParser;

	protected ExpressoInterpreter(Class<?> callingClass, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
		Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoParser expressionParser) {
		super(callingClass, creators, modifiers);
		theExpressionParser = expressionParser;
	}

	public ExpressoParser getExpressionParser() {
		return theExpressionParser;
	}

	/**
	 * Builds an interpreter
	 *
	 * @param callingClass The class invoking this interpretation--may be needed to access resources on the classpath
	 * @param toolkits The toolkits that the interpreter will be able to interpret documents of
	 * @return A builder
	 */
	public static DefaultBuilder build(Class<?> callingClass, QonfigToolkit... toolkits) {
		return new DefaultBuilder(callingClass, null, toolkits);
	}

	public static abstract class Builder<QIS extends ExpressoSession<QIS>, B extends Builder<QIS, B>>
	extends QonfigInterpreter.Builder<QIS, B> {
		private ExpressoParser theExpressionParser;

		protected Builder(Class<?> callingClass, ExpressoParser expressionParser, QonfigToolkit... toolkits) {
			super(callingClass, toolkits);
			theExpressionParser = expressionParser;
		}

		protected Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoParser expressionParser) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers);
			theExpressionParser = expressionParser;
		}

		public ExpressoParser getExpressionParser() {
			return theExpressionParser;
		}

		public B withExpressionParser(ExpressoParser expressionParser) {
			theExpressionParser = expressionParser;
			return (B) this;
		}

		@Override
		protected B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers) {
			return builderFor(callingClass, toolkits, toolkit, status, creators, modifiers, theExpressionParser);
		}

		protected abstract B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, QonfigCreatorHolder<QIS, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoParser expressionParser);

		@Override
		public abstract ExpressoInterpreter<QIS> create();

		@Override
		public ExpressoInterpreter<QIS> build() {
			return (ExpressoInterpreter<QIS>) super.build();
		}
	}

	public static class ExpressoSessionDefault extends ExpressoSession<ExpressoSessionDefault> {
		ExpressoSessionDefault(ExpressoSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex) {
			super(parent, element, type, childIndex);
		}

		ExpressoSessionDefault(QonfigInterpreter<ExpressoSessionDefault> interpreter, QonfigElement root) {
			super(interpreter, root);
		}
	}

	public static class Default extends ExpressoInterpreter<ExpressoSessionDefault> {
		protected Default(Class<?> callingClass, Map<QonfigElementOrAddOn, QonfigCreatorHolder<ExpressoSessionDefault, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoParser expressionParser) {
			super(callingClass, creators, modifiers, expressionParser);
		}

		@Override
		public ExpressoSessionDefault interpret(QonfigElement element) {
			return new ExpressoSessionDefault(this, element);
		}

		@Override
		protected ExpressoSessionDefault interpret(ExpressoSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type,
			int childIndex) {
			return new ExpressoSessionDefault(parent, element, type, childIndex);
		}
	}

	public static class DefaultBuilder extends Builder<ExpressoSessionDefault, DefaultBuilder> {
		DefaultBuilder(Class<?> callingClass, ExpressoParser expressionParser, QonfigToolkit... toolkits) {
			super(callingClass, expressionParser, toolkits);
		}

		DefaultBuilder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, QonfigCreatorHolder<ExpressoSessionDefault, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoParser expressionParser) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers, expressionParser);
		}

		@Override
		protected DefaultBuilder builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, QonfigCreatorHolder<ExpressoSessionDefault, ?>> creators,
			Map<QonfigElementOrAddOn, SubClassMap2<Object, QonfigModifierHolder<ExpressoSessionDefault, ?>>> modifiers,
			ExpressoParser expressionParser) {
			return new DefaultBuilder(callingClass, toolkits, toolkit, status, creators, modifiers, expressionParser);
		}

		@Override
		public Default create() {
			return new Default(getCallingClass(), getCreators(), getModifiers(),
				getExpressionParser() == null ? new DefaultExpressoParser() : getExpressionParser());
		}
	}
}

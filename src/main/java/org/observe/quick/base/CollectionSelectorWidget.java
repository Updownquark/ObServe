package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public abstract class CollectionSelectorWidget<T> extends QuickValueWidget.Abstract<T> {
	public static final String COLLECTION_SELECTOR = "collection-selector-widget";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = COLLECTION_SELECTOR,
		interpretation = Interpreted.class,
		instance = QuickComboBox.class)
	public static abstract class Def<W extends CollectionSelectorWidget<?>> extends QuickValueWidget.Def.Abstract<W> {
		private CompiledExpression theValues;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = "collection-selector-widget", value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theValues = session.getAttributeExpression("values");
		}

		@Override
		public abstract Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<T, W extends CollectionSelectorWidget<T>> extends QuickValueWidget.Interpreted.Abstract<T, W> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;

		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theValues = getDefinition().getValues() == null ? null
				: getDefinition().getValues().interpret(ModelTypes.Collection.forType(getValueType()), env);
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private SettableValue<ObservableCollection<T>> theValues;

	protected CollectionSelectorWidget(Object id) {
		super(id);
	}

	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		TypeToken<T> valueType = (TypeToken<T>) myInterpreted.getValue().getType().getType(0);
		if (theValues == null || !getValues().getType().equals(valueType)) {
			theValues = SettableValue
				.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<T>> parameterized(valueType)).build();
		}
		theValuesInstantiator = myInterpreted.getValues() == null ? null : myInterpreted.getValues().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theValuesInstantiator != null)
			theValuesInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theValues.set(theValuesInstantiator == null ? null : theValuesInstantiator.get(myModels), null);
	}

	@Override
	protected CollectionSelectorWidget<T> clone() {
		CollectionSelectorWidget<T> copy = (CollectionSelectorWidget<T>) super.clone();

		copy.theValues = SettableValue.build(theValues.getType()).build();

		return copy;
	}
}

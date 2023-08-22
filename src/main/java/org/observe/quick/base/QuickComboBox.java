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
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickComboBox<T> extends QuickValueWidget.Abstract<T> {
	public static final String COMBO_BOX = "combo";
	private static final SingleTypeTraceability<QuickComboBox<?>, Interpreted<?>, Def> COMBO_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, COMBO_BOX, Def.class, Interpreted.class,
			QuickComboBox.class);
	private static final SingleTypeTraceability<QuickComboBox<?>, Interpreted<?>, Def> COLLECTION_SELECTOR_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, "collection-selector-widget", Def.class,
			Interpreted.class, QuickComboBox.class);

	public static class Def extends QuickValueWidget.Def.Abstract<QuickComboBox<?>> {
		private CompiledExpression theValues;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter(asType = "collection-selector-widget", value = "values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(COMBO_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			withTraceability(COLLECTION_SELECTOR_TRACEABILITY.validate(session.asElement("collection-selector-widget").getFocusType(),
				session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theValues = session.getAttributeExpression("values");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickValueWidget.Interpreted.Abstract<T, QuickComboBox<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectedValue;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectedValue() {
			return theSelectedValue;
		}

		@Override
		public TypeToken<QuickComboBox<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickComboBox.class).<QuickComboBox<T>> parameterized(getValueType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theValues = getDefinition().getValues() == null ? null
				: getDefinition().getValues().interpret(ModelTypes.Collection.forType(getValueType()), env);
		}

		@Override
		public QuickComboBox<T> create() {
			return new QuickComboBox<>(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private SettableValue<ObservableCollection<T>> theValues;

	public QuickComboBox(Object id) {
		super(id);
	}

	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
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
	protected QuickComboBox<T> clone() {
		QuickComboBox<T> copy = (QuickComboBox<T>) super.clone();

		copy.theValues = SettableValue.build(theValues.getType()).build();

		return copy;
	}
}

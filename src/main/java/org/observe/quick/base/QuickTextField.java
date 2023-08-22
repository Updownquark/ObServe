package org.observe.quick.base;

import org.observe.SettableValue;
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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextField<T> extends QuickEditableTextWidget.Abstract<T> {
	public static final String TEXT_FIELD = "text-field";
	private static final SingleTypeTraceability<QuickTextField<?>, Interpreted<?>, Def> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_FIELD, Def.class, Interpreted.class,
			QuickTextField.class);

	public static class Def extends QuickEditableTextWidget.Def.Abstract<QuickTextField<?>> {
		private Integer theColumns;
		private CompiledExpression theEmptyText;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		@QonfigAttributeGetter("columns")
		public Integer getColumns() {
			return theColumns;
		}

		@QonfigAttributeGetter("empty-text")
		public CompiledExpression getEmptyText() {
			return theEmptyText;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			String columnsStr = session.getAttributeText("columns");
			theColumns = columnsStr == null ? null : Integer.parseInt(columnsStr);
			theEmptyText = session.getAttributeExpression("empty-text");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextField<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theEmptyText;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickTextField.Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickTextField<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickTextField.class).parameterized(getValueType());
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getEmptyText() {
			return theEmptyText;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theEmptyText = getDefinition().getEmptyText() == null ? null
				: getDefinition().getEmptyText().interpret(ModelTypes.Value.forType(String.class), getExpressoEnv());
		}

		@Override
		public QuickTextField<T> create() {
			return new QuickTextField<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theEmptyTextInstantiator;
	private Integer theColumns;
	private SettableValue<SettableValue<String>> theEmptyText;

	public QuickTextField(Object id) {
		super(id);
		theEmptyText = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class))
			.build();
	}

	public Integer getColumns() {
		return theColumns;
	}

	public SettableValue<String> getEmptyText() {
		return SettableValue.flatten(theEmptyText);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickTextField.Interpreted<T> myInterpreted = (QuickTextField.Interpreted<T>) interpreted;
		theColumns = myInterpreted.getDefinition().getColumns();
		theEmptyTextInstantiator = myInterpreted.getEmptyText() == null ? null : myInterpreted.getEmptyText().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theEmptyTextInstantiator != null)
			theEmptyTextInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theEmptyText.set(theEmptyTextInstantiator == null ? null : theEmptyTextInstantiator.get(myModels), null);
	}

	@Override
	public QuickTextField<T> copy(ExElement parent) {
		QuickTextField<T> copy = (QuickTextField<T>) super.copy(parent);

		copy.theEmptyText = SettableValue.build(theEmptyText.getType()).build();

		return copy;
	}
}

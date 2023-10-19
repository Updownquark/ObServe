package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickTextArea<T> extends QuickEditableTextWidget.Abstract<T> {
	public static final String TEXT_AREA = "text-area";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TEXT_AREA,
		interpretation = Interpreted.class,
		instance = QuickTextArea.class)
	public static class Def extends QuickEditableTextWidget.Def.Abstract<QuickTextArea<?>> {
		private CompiledExpression theRows;
		private StyledDocument.Def<?> theDocument;
		private ModelComponentId theMousePositionVariable;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		@QonfigChildGetter("document")
		public StyledDocument.Def<?> getDocument() {
			return theDocument;
		}

		public ModelComponentId getMousePositionVariable() {
			return theMousePositionVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theRows = getAttributeExpression("rows", session);
			theDocument = ExElement.useOrReplace(StyledDocument.Def.class, theDocument, session, "document");

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theMousePositionVariable = elModels.getElementValueModelId("mousePosition");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextArea<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRows;
		private StyledDocument.Interpreted<T, ?> theDocument;
		private boolean isDocumentStale;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickTextArea<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickTextArea.class).parameterized(getValueType());
		}

		@Override
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			getOrInitValue();
			if (theDocument != null)
				return theDocument.getValueType();
			else
				return super.getValueType();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRows() {
			return theRows;
		}

		public StyledDocument.Interpreted<T, ?> getDocument() {
			return theDocument;
		}

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException {
			super.getOrInitValue();
			if (isDocumentStale) {
				isDocumentStale = false;
				if (getDefinition().getDocument() == null) {
					if (theDocument != null)
						theDocument.destroy();
					theDocument = null;
				} else if (theDocument == null || theDocument.getIdentity() != getDefinition().getDocument().getIdentity()) {
					if (theDocument != null)
						theDocument.destroy();
					theDocument = (StyledDocument.Interpreted<T, ?>) getDefinition().getDocument().interpret(this);
				}
				if (theDocument != null)
					theDocument.updateDocument(getExpressoEnv());
			}
			return getValue();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			isDocumentStale = true;
			super.doUpdate(env);
			theRows = getDefinition().getRows() == null ? null
				: getDefinition().getRows().interpret(ModelTypes.Value.forType(Integer.class), getExpressoEnv());
		}

		@Override
		protected void checkValidModel() throws ExpressoInterpretationException {
			if (theDocument != null) {
				if (getValue() != null && getDefinition().getValue().getExpression() != ObservableExpression.EMPTY)
					throw new ExpressoInterpretationException("Both document and value are specified, but only one is allowed",
						getDefinition().getValue().getFilePosition(0), getDefinition().getValue().getExpression().getExpressionLength());
				if (getFormat() != null)
					throw new ExpressoInterpretationException("Format is not needed when document is specified",
						getDefinition().getFormat().getFilePosition(0), getDefinition().getFormat().getExpression().getExpressionLength());
			} else
				super.checkValidModel();
		}

		@Override
		public QuickTextArea<T> create() {
			return new QuickTextArea<>(getIdentity());
		}
	}

	public interface QuickTextAreaContext {
		SettableValue<Integer> getMousePosition();

		public class Default implements QuickTextAreaContext {
			private final SettableValue<Integer> theMousePosition;

			public Default(SettableValue<Integer> mousePosition) {
				theMousePosition = mousePosition;
			}

			public Default() {
				this(SettableValue.build(int.class).withDescription("mousePosition").withValue(0).build());
			}

			@Override
			public SettableValue<Integer> getMousePosition() {
				return theMousePosition;
			}
		}
	}

	private ModelComponentId theMousePositionVariable;
	private StyledDocument<T> theDocument;
	private ModelValueInstantiator<SettableValue<Integer>> theRowsInstantiator;
	private SettableValue<SettableValue<Integer>> theRows;
	private SettableValue<SettableValue<Integer>> theMousePosition;

	public QuickTextArea(Object id) {
		super(id);
		theRows = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class))
			.build();
		theMousePosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
	}

	public StyledDocument<T> getDocument() {
		return theDocument;
	}

	public SettableValue<Integer> getRows() {
		return SettableValue.flatten(theRows, () -> 0);
	}

	public SettableValue<Integer> getMousePosition() {
		return SettableValue.flatten(theMousePosition, () -> 0);
	}

	public void setTextAreaContext(QuickTextAreaContext ctx) {
		theMousePosition.set(ctx.getMousePosition(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickTextArea.Interpreted<T> myInterpreted = (QuickTextArea.Interpreted<T>) interpreted;
		theMousePositionVariable = myInterpreted.getDefinition().getMousePositionVariable();
		theRowsInstantiator = myInterpreted.getRows() == null ? null : myInterpreted.getRows().instantiate();
		theDocument = myInterpreted.getDocument() == null ? null : myInterpreted.getDocument().create();
		if (theDocument != null)
			theDocument.update(myInterpreted.getDocument(), this);
	}

	@Override
	public void instantiated() {
		super.instantiated();

		if (theRowsInstantiator != null)
			theRowsInstantiator.instantiate();

		if (theDocument != null)
			theDocument.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theRows.set(theRowsInstantiator == null ? null : theRowsInstantiator.get(myModels), null);
		if (theDocument != null)
			theDocument.instantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theMousePositionVariable, myModels, getMousePosition());
	}

	@Override
	public QuickTextArea<T> copy(ExElement parent) {
		QuickTextArea<T> copy = (QuickTextArea<T>) super.copy(parent);

		copy.theRows = SettableValue.build(theRows.getType()).build();
		copy.theMousePosition = SettableValue.build(theMousePosition.getType()).build();

		if (theDocument != null)
			copy.theDocument = theDocument.copy(copy);

		return copy;
	}
}

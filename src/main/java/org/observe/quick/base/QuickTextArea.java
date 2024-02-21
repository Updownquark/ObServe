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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * Like a {@link QuickTextField}, but with multiple lines of text
 *
 * @param <T> The type of the value represented
 */
public class QuickTextArea<T> extends QuickEditableTextWidget.Abstract<T> {
	/** The XML name of this element */
	public static final String TEXT_AREA = "text-area";

	/** {@link QuickTextArea} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TEXT_AREA,
		interpretation = Interpreted.class,
		instance = QuickTextArea.class)
	public static class Def extends QuickEditableTextWidget.Def.Abstract<QuickTextArea<?>> {
		private CompiledExpression theRows;
		private boolean isHtml;
		private StyledDocument.Def<?> theDocument;
		private ModelComponentId theMousePositionVariable;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return true;
		}

		/** @return The number of rows of text to display at a time */
		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		/** @return Whether to render the formatted value as HTML */
		@QonfigAttributeGetter("html")
		public boolean isHtml() {
			return isHtml;
		}

		/** @return The styled document for the text area */
		@QonfigChildGetter("document")
		public StyledDocument.Def<?> getDocument() {
			return theDocument;
		}

		/** @return The model ID of the variable containing the position of the mouse over the text, offset from 0 */
		public ModelComponentId getMousePositionVariable() {
			return theMousePositionVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theRows = getAttributeExpression("rows", session);
			isHtml = session.getAttribute("html", boolean.class);
			theDocument = syncChild(StyledDocument.Def.class, theDocument, session, "document");

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theMousePositionVariable = elModels.getElementValueModelId("mousePosition");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickTextArea} interpretation
	 *
	 * @param <T> The type of the value represented
	 */
	public static class Interpreted<T> extends QuickEditableTextWidget.Interpreted.Abstract<T, QuickTextArea<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRows;
		private StyledDocument.Interpreted<T, ?> theDocument;
		private boolean isDocumentStale;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			getOrInitValue();
			if (theDocument != null)
				return theDocument.getValueType();
			else
				return super.getValueType();
		}

		/** @return The number of rows of text to display at a time */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRows() {
			return theRows;
		}

		/** @return The styled document for the text area */
		public StyledDocument.Interpreted<T, ?> getDocument() {
			return theDocument;
		}

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException {
			super.getOrInitValue();
			if (isDocumentStale) {
				isDocumentStale = false;
				theDocument = syncChild(getDefinition().getDocument(), theDocument,
					def -> (StyledDocument.Interpreted<T, ?>) def.interpret(this), (d, dEnv) -> d.updateDocument(dEnv));
			}
			return getValue();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			isDocumentStale = true;
			super.doUpdate(env);
			theRows = interpret(getDefinition().getRows(), ModelTypes.Value.forType(Integer.class));
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

	/** Model context for a {@link QuickTextArea} */
	public interface QuickTextAreaContext {
		/** @return The mouse position in the document, in characters from the start of the text */
		SettableValue<Integer> getMousePosition();

		/** Default {@link QuickTextAreaContext} implementation */
		public class Default implements QuickTextAreaContext {
			private final SettableValue<Integer> theMousePosition;

			/** @param mousePosition The mouse position in the document, in characters from the start of the text */
			public Default(SettableValue<Integer> mousePosition) {
				theMousePosition = mousePosition;
			}

			/** Creates the context */
			public Default() {
				this(SettableValue.<Integer> build().withDescription("mousePosition").withValue(0).build());
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
	private boolean isHtml;
	private SettableValue<SettableValue<Integer>> theMousePosition;

	/** @param id The element ID for this widget */
	protected QuickTextArea(Object id) {
		super(id);
		theRows = SettableValue.<SettableValue<Integer>> build().build();
		theMousePosition = SettableValue.<SettableValue<Integer>> build().build();
	}

	/** @return The styled document for the text area */
	public StyledDocument<T> getDocument() {
		return theDocument;
	}

	/** @return The number of rows of text to display at a time */
	public SettableValue<Integer> getRows() {
		return SettableValue.flatten(theRows, () -> 0);
	}

	/** @return Whether to render the formatted value as HTML */
	public boolean isHtml() {
		return isHtml;
	}

	/** @return The position of the mouse over the text, offset from 0 */
	public SettableValue<Integer> getMousePosition() {
		return SettableValue.flatten(theMousePosition, () -> 0);
	}

	/** @param ctx The model context for this text area */
	public void setTextAreaContext(QuickTextAreaContext ctx) {
		theMousePosition.set(ctx.getMousePosition(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickTextArea.Interpreted<T> myInterpreted = (QuickTextArea.Interpreted<T>) interpreted;
		theMousePositionVariable = myInterpreted.getDefinition().getMousePositionVariable();
		theRowsInstantiator = myInterpreted.getRows() == null ? null : myInterpreted.getRows().instantiate();
		isHtml = myInterpreted.getDefinition().isHtml();
		theDocument = myInterpreted.getDocument() == null ? null : myInterpreted.getDocument().create();
		if (theDocument != null)
			theDocument.update(myInterpreted.getDocument(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
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

		copy.theRows = SettableValue.<SettableValue<Integer>> build().build();
		copy.theMousePosition = SettableValue.<SettableValue<Integer>> build().build();

		if (theDocument != null)
			copy.theDocument = theDocument.copy(copy);

		return copy;
	}
}

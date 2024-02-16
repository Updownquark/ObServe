package org.observe.quick.ext;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
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
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A container with a single content widget. A header is displayed above the content, and when the header is clicked, the content will be
 * hidden or made visible.
 */
public class QuickCollapsePane extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String COLLAPSE_PANE = "collapse-pane";

	/** {@link QuickCollapsePane} definition */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = COLLAPSE_PANE,
		interpretation = Interpreted.class,
		instance = QuickCollapsePane.class)
	public static class Def extends QuickContainer.Def.Abstract<QuickCollapsePane, QuickWidget> {
		private CompiledExpression isCollapsed;
		private boolean isAnimated;
		private QuickWidget.Def<?> theHeader;
		private ModelComponentId theCollapsedVariable;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the content is currently hidden */
		@QonfigAttributeGetter("collapsed")
		public CompiledExpression isCollapsed() {
			return isCollapsed;
		}

		/** @return Whether to animate the collapse/expand actions, or just make them happen instantaneously */
		@QonfigAttributeGetter("animated")
		public boolean isAnimated() {
			return isAnimated;
		}

		/** @return The header to represent the content and allow the user to toggle its collapsed state */
		@QonfigChildGetter("header")
		public QuickWidget.Def<?> getHeader() {
			return theHeader;
		}

		/** @return The model ID of the variable containing the collapsed state of the widget */
		public ModelComponentId getCollapsedVariable() {
			return theCollapsedVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			isCollapsed = getAttributeExpression("collapsed", session);
			isAnimated = session.getAttribute("animated", boolean.class);
			theHeader = syncChild(QuickWidget.Def.class, theHeader, session, "header");

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theCollapsedVariable = elModels.getElementValueModelId("collapsed");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickCollapsePane} interpretation */
	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickCollapsePane, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isCollapsed;
		private QuickWidget.Interpreted<?> theHeader;

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

		/** @return Whether the content is currently hidden */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isCollapsed() {
			return isCollapsed;
		}

		/** @return The header to represent the content and allow the user to toggle its collapsed state */
		public QuickWidget.Interpreted<?> getHeader() {
			return theHeader;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			isCollapsed = interpret(getDefinition().isCollapsed(), ModelTypes.Value.BOOLEAN);
			theHeader = syncChild(getDefinition().getHeader(), theHeader, def -> def.interpret(this), (h, hEnv) -> h.updateElement(hEnv));
		}

		@Override
		public QuickCollapsePane create() {
			return new QuickCollapsePane(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Boolean>> isCollapsedInstantiator;
	private SettableValue<SettableValue<Boolean>> isCollapsed;
	private ModelComponentId theCollapsedVariable;
	private boolean isAnimated;
	private QuickWidget theHeader;

	/** @param id The element ID for this widget */
	protected QuickCollapsePane(Object id) {
		super(id);
		isCollapsed = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
	}

	/** @return Whether the content is currently hidden */
	public SettableValue<Boolean> isCollapsed() {
		return SettableValue.flatten(isCollapsed);
	}

	/** @return The header to represent the content and allow the user to toggle its collapsed state */
	public QuickWidget getHeader() {
		return theHeader;
	}

	/** @return Whether to animate the collapse/expand actions, or just make them happen instantaneously */
	public boolean isAnimated() {
		return isAnimated;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		isCollapsedInstantiator = myInterpreted.isCollapsed() == null ? null : myInterpreted.isCollapsed().instantiate();
		theCollapsedVariable = myInterpreted.getDefinition().getCollapsedVariable();
		isAnimated = myInterpreted.getDefinition().isAnimated();

		if (theHeader != null
			&& (myInterpreted.getHeader() == null || theHeader.getIdentity() != myInterpreted.getHeader().getIdentity())) {
			theHeader.destroy();
			theHeader = null;
		}
		if (theHeader == null && myInterpreted.getHeader() != null)
			theHeader = myInterpreted.getHeader().create();
		if (theHeader != null)
			theHeader.update(myInterpreted.getHeader(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (isCollapsedInstantiator != null)
			isCollapsedInstantiator.instantiate();

		if (theHeader != null)
			theHeader.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		isCollapsed.set(isCollapsedInstantiator == null ? SettableValue.build(boolean.class).withValue(true).build()
			: isCollapsedInstantiator.get(myModels), null);
		ExFlexibleElementModelAddOn.satisfyElementValue(theCollapsedVariable, myModels, isCollapsed());

		if (theHeader != null)
			theHeader.instantiate(myModels);
	}

	@Override
	public QuickCollapsePane copy(ExElement parent) {
		QuickCollapsePane copy = (QuickCollapsePane) super.copy(parent);

		copy.isCollapsed = SettableValue.build(isCollapsed.getType()).build();
		if (theHeader != null)
			copy.theHeader = theHeader.copy(copy);

		return copy;
	}
}

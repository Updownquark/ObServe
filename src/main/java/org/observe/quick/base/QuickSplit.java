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
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A container with exactly two content widgets, laying them out side-by-side or one above another, separated by a bar that the user may
 * drag to change the amount of size allocated to each
 */
public class QuickSplit extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String SPLIT = "split";

	/**
	 * {@link QuickSplit} definition
	 *
	 * @param <S> The sub-type of split pane to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPLIT,
		interpretation = Interpreted.class,
		instance = QuickSplit.class)
	public static class Def<S extends QuickSplit> extends QuickContainer.Def.Abstract<S, QuickWidget> {
		private boolean isVertical;
		private CompiledExpression theSplitPosition;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the split pane arranges its content on top of each other or side-by-side */
		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		/** @return The position of the split between the components, a {@link QuickSize} */
		@QonfigAttributeGetter("split-position")
		public CompiledExpression getSplitPosition() {
			return theSplitPosition;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			switch (session.getAttributeText("orientation")) {
			case "horizontal":
				isVertical = false;
				break;
			case "vertical":
				isVertical = true;
				break;
			default:
				isVertical = true;
				session.reporting().at(session.attributes().get("orientation").getLocatedContent())
				.error("Unrecognized orientation: '" + session.getAttributeText("orientation"));
			}
			theSplitPosition = getAttributeExpression("split-position", session);
			if (getContents().size() != 2)
				session.reporting().error("Expected exactly 2 children, not " + getContents().size());
		}

		@Override
		public Interpreted<? extends S> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickSplit} interpretation
	 *
	 * @param <S> The sub-type of split pane to create
	 */
	public static class Interpreted<S extends QuickSplit> extends QuickContainer.Interpreted.Abstract<S, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theSplitPosition;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super S> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super S> getDefinition() {
			return (Def<? super S>) super.getDefinition();
		}

		/** @return The position of the split between the components */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getSplitPosition() {
			return theSplitPosition;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theSplitPosition = interpret(getDefinition().getSplitPosition(), ModelTypes.Value.forType(QuickSize.class));
		}

		@Override
		public S create() {
			return (S) new QuickSplit(getIdentity());
		}
	}

	private boolean isVertical;
	private ModelValueInstantiator<SettableValue<QuickSize>> theSplitPositionInstantiator;
	private SettableValue<SettableValue<QuickSize>> theSplitPosition;

	/** @param id The element ID for this widget */
	protected QuickSplit(Object id) {
		super(id);
		theSplitPosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
	}

	/** @return Whether the split pane arranges its content on top of each other or side-by-side */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return The position of the split between the components */
	public SettableValue<QuickSize> getSplitPosition() {
		return SettableValue.flatten(theSplitPosition);
	}

	/** @return Whether a {@link #getSplitPosition() split-position} is configured for this splitpane */
	public boolean isSplitPositionSet() {
		return theSplitPosition.get() != null;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickSplit.Interpreted<?> myInterpreted = (QuickSplit.Interpreted<?>) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		theSplitPositionInstantiator = myInterpreted.getSplitPosition() == null ? null : myInterpreted.getSplitPosition().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theSplitPositionInstantiator != null)
			theSplitPositionInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theSplitPosition.set(
			theSplitPositionInstantiator == null ? SettableValue.build(QuickSize.class).build()
				: theSplitPositionInstantiator.get(myModels),
				null);
	}

	@Override
	public QuickSplit copy(ExElement parent) {
		QuickSplit copy = (QuickSplit) super.copy(parent);

		theSplitPosition = SettableValue.build(theSplitPosition.getType()).build();

		return copy;
	}
}

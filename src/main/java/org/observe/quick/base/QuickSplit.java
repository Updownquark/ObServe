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

import com.google.common.reflect.TypeToken;

public class QuickSplit extends QuickContainer.Abstract<QuickWidget> {
	public static final String SPLIT = "split";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPLIT,
		interpretation = Interpreted.class,
		instance = QuickSplit.class)
	public static class Def<S extends QuickSplit> extends QuickContainer.Def.Abstract<S, QuickWidget> {
		private boolean isVertical;
		private CompiledExpression theSplitPosition;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

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

	public static class Interpreted<S extends QuickSplit> extends QuickContainer.Interpreted.Abstract<S, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theSplitPosition;

		public Interpreted(Def<? super S> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super S> getDefinition() {
			return (Def<? super S>) super.getDefinition();
		}

		@Override
		public TypeToken<S> getWidgetType() {
			return (TypeToken<S>) TypeTokens.get().of(QuickSplit.class);
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getSplitPosition() {
			return theSplitPosition;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theSplitPosition = getDefinition().getSplitPosition() == null ? null
				: getDefinition().getSplitPosition().interpret(ModelTypes.Value.forType(QuickSize.class), env);
		}

		@Override
		public S create() {
			return (S) new QuickSplit(getIdentity());
		}
	}

	private boolean isVertical;
	private ModelValueInstantiator<SettableValue<QuickSize>> theSplitPositionInstantiator;
	private SettableValue<SettableValue<QuickSize>> theSplitPosition;

	public QuickSplit(Object id) {
		super(id);
		theSplitPosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
	}

	public boolean isVertical() {
		return isVertical;
	}

	public SettableValue<QuickSize> getSplitPosition() {
		return SettableValue.flatten(theSplitPosition);
	}

	public boolean isSplitPositionSet() {
		return theSplitPosition.get() != null;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickSplit.Interpreted<?> myInterpreted = (QuickSplit.Interpreted<?>) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		theSplitPositionInstantiator = myInterpreted.getSplitPosition() == null ? null : myInterpreted.getSplitPosition().instantiate();
	}

	@Override
	public void instantiated() {
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

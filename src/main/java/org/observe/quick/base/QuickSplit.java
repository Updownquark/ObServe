package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
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
	private static final SingleTypeTraceability<QuickSplit, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, SPLIT, Def.class, Interpreted.class,
			QuickSplit.class);

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

		public CompiledExpression getSplitPosition() {
			return theSplitPosition;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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
				session.reporting().at(session.getAttributeValuePosition("orientation"))
				.error("Unrecognized orientation: '" + session.getAttributeText("orientation"));
			}
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
		public S create(ExElement parent) {
			return (S) new QuickSplit(this, parent);
		}
	}

	private boolean isVertical;
	private final SettableValue<SettableValue<QuickSize>> theSplitPosition;

	public QuickSplit(Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
		theSplitPosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
	}

	public boolean isVertical() {
		return isVertical;
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickSplit.Interpreted<?> myInterpreted = (QuickSplit.Interpreted<?>) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		if (myInterpreted.getSplitPosition() != null)
			theSplitPosition.set(myInterpreted.getSplitPosition().get(myModels), null);
		else {
			SettableValue<QuickSize> splitPos = SettableValue.build(QuickSize.class).build();
			if (theSplitPosition.get() != null)
				splitPos.set(theSplitPosition.get().get(), null);
			theSplitPosition.set(splitPos, null);
		}
	}
}

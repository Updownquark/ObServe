package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickSplit extends QuickContainer2.Abstract<QuickWidget> {
	public static class Def<S extends QuickSplit> extends QuickContainer2.Def.Abstract<S, QuickWidget> {
		private boolean isVertical;
		private CompiledExpression theSplitPosition;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		public boolean isVertical() {
			return isVertical;
		}

		public CompiledExpression getSplitPosition() {
			return theSplitPosition;
		}

		@Override
		public Def<S> update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
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
			return this;
		}

		@Override
		public Interpreted<? extends S> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<S extends QuickSplit> extends QuickContainer2.Interpreted.Abstract<S, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theSplitPosition;

		public Interpreted(Def<? super S> definition, QuickElement.Interpreted<?> parent) {
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
		public Interpreted<S> update(QuickWidget.QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theSplitPosition = getDefinition().getSplitPosition() == null ? null
				: getDefinition().getSplitPosition().evaluate(ModelTypes.Value.forType(QuickSize.class)).interpret();
			return this;
		}

		@Override
		public S create(QuickElement parent) {
			return (S) new QuickSplit(this, parent);
		}
	}

	private final SettableValue<SettableValue<QuickSize>> theSplitPosition;

	public QuickSplit(Interpreted<?> interpreted, QuickElement parent) {
		super(interpreted, parent);
		theSplitPosition = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<QuickSize>> parameterized(QuickSize.class)).build();
	}

	@Override
	public Interpreted<?> getInterpreted() {
		return (Interpreted<?>) super.getInterpreted();
	}

	@Override
	public QuickSplit update(ModelSetInstance models) throws ModelInstantiationException {
		super.update(models);
		if (getInterpreted().getSplitPosition() != null)
			theSplitPosition.set(getInterpreted().getSplitPosition().get(getModels()), null);
		else {
			SettableValue<QuickSize> splitPos = SettableValue.build(QuickSize.class).build();
			if (theSplitPosition.get() != null)
				splitPos.set(theSplitPosition.get().get(), null);
			theSplitPosition.set(splitPos, null);
		}
		return this;
	}
}

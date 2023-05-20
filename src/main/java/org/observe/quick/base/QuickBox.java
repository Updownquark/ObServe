package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickBox extends QuickContainer2.Abstract<QuickWidget> {
	public static class Def<W extends QuickBox> extends QuickContainer2.Def.Abstract<W, QuickWidget> {
		private CompiledExpression theOpacity;

		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		public CompiledExpression getOpacity() {
			return theOpacity;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			if (getAddOn(QuickLayout.Def.class) == null) {
				String layout = session.getAttributeText("layout");
				throw new QonfigInterpretationException("No Quick interpretationfor layout " + layout,
					session.getAttributeValuePosition("layout", 0), layout.length());
			}
			theOpacity = session.getAttributeExpression("opacity");
		}

		@Override
		public Interpreted<W> interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickBox> extends QuickContainer2.Interpreted.Abstract<W, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theOpacity;

		public Interpreted(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		@Override
		public TypeToken<W> getWidgetType() {
			return (TypeToken<W>) TypeTokens.get().of(QuickBox.class);
		}

		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getOpacity() {
			return theOpacity;
		}

		@Override
		public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(cache);
			theOpacity = getDefinition().getOpacity() == null ? null
				: getDefinition().getOpacity().evaluate(ModelTypes.Value.DOUBLE).interpret();
		}

		@Override
		public W create(QuickElement parent) {
			return (W) new QuickBox(this, parent);
		}
	}

	private SettableValue<Double> theOpacity;

	public QuickBox(Interpreted<?> interpreted, QuickElement parent) {
		super(interpreted, parent);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	@Override
	public ModelSetInstance update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		ModelSetInstance myModels = super.update(interpreted, models);
		QuickBox.Interpreted<?> myInterpreted = (QuickBox.Interpreted<?>) interpreted;
		theOpacity = myInterpreted.getOpacity() == null ? null : myInterpreted.getOpacity().get(myModels);
		return myModels;
	}

	public SettableValue<Double> getOpacity() {
		return theOpacity;
	}
}

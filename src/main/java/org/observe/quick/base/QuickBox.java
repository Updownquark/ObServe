package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickBox extends QuickContainer2.Abstract<QuickWidget> {
	public static class Def<W extends QuickBox> extends QuickContainer2.Def.Abstract<W, QuickWidget> {
		private QuickLayout.Def theLayout;
		private CompiledExpression theOpacity;

		public Def(AbstractQIS<?> session) throws QonfigInterpretationException {
			super(session);
		}

		public QuickLayout.Def getLayout() {
			return theLayout;
		}

		public CompiledExpression getOpacity() {
			return theOpacity;
		}

		@Override
		public Def<W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
			super.update(session);
			theLayout = session.asElement(session.getAttribute("layout", QonfigAddOn.class)).interpret(QuickLayout.Def.class);
			theOpacity = getExpressoSession().getAttributeExpression("opacity");
			return this;
		}

		@Override
		public Interpreted<W> interpret(QuickContainer2.Interpreted<?, ?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickBox> extends QuickContainer2.Interpreted.Abstract<W, QuickWidget> {
		private QuickLayout.Interpreted theLayout;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theOpacity;

		public Interpreted(Def<? super W> definition, QuickContainer2.Interpreted<?, ?> parent) {
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

		public QuickLayout.Interpreted getLayout() {
			return theLayout;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getOpacity() {
			return theOpacity;
		}

		@Override
		public Interpreted<W> update(InterpretedModelSet models, QuickInterpretationCache cache) throws ExpressoInterpretationException {
			super.update(models, cache);
			theLayout = getDefinition().getLayout().interpret(models);
			theOpacity = getDefinition().getOpacity() == null ? null
				: getDefinition().getOpacity().evaluate(ModelTypes.Value.DOUBLE).interpret();
			return this;
		}

		@Override
		public W create(QuickContainer2<?> parent) {
			return (W) new QuickBox(this, parent);
		}
	}

	private QuickLayout theLayout;
	private SettableValue<Double> theOpacity;

	public QuickBox(Interpreted<?> interpreted, QuickContainer2<?> parent) {
		super(interpreted, parent);
	}

	@Override
	public Interpreted<?> getInterpreted() {
		return (Interpreted<?>) super.getInterpreted();
	}

	@Override
	public QuickBox update(ModelSetInstance models, QuickInstantiationCache cache) throws ModelInstantiationException {
		super.update(models, cache);
		theLayout = getInterpreted().getLayout().create(getModels());
		theOpacity = getInterpreted().getOpacity() == null ? null : getInterpreted().getOpacity().get(getModels());
		return this;
	}

	public QuickLayout getLayout() {
		return theLayout;
	}

	public SettableValue<Double> getOpacity() {
		return theOpacity;
	}
}

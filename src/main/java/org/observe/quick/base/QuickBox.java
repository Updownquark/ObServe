package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickStyledElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickBox extends QuickContainer.Abstract<QuickWidget> {
	public static final String BOX = "box";

	public static ExElement.AttributeValueGetter.AddOn<QuickBox, QuickLayout, QuickLayout.Interpreted<?>, QuickLayout.Def<?>> LAYOUT = ExElement.AttributeValueGetter
		.addOn(QuickLayout.Def.class, QuickLayout.Interpreted.class, QuickLayout.class,
			"The layout that the box will use to arrange its contents");
	public static ExElement.AttributeValueGetter.Expression<QuickBox, Interpreted<? extends QuickBox>, Def<? extends QuickBox>, SettableValue<?>, SettableValue<Double>> OPACITY = ExElement.AttributeValueGetter
		.ofX(Def::getOpacity, Interpreted::getOpacity, QuickBox::getOpacity,
			"The opacity of the box, between 0 (completely transparent) and 1 (completely opaque)");

	public static class Def<W extends QuickBox> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		private CompiledExpression theOpacity;

		public Def(ExElement.Def<?> parent, QonfigElement element) {
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
			ExElement.checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, BOX);
			forAttribute(session.getAttributeDef(null, null, "layout"), LAYOUT);
			forAttribute(session.getAttributeDef(null, null, "opacity"), OPACITY);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
			if (getAddOn(QuickLayout.Def.class) == null) {
				String layout = session.getAttributeText("layout");
				throw new QonfigInterpretationException("No Quick interpretation for layout " + layout,
					session.getAttributeValuePosition("layout", 0), layout.length());
			}
			theOpacity = session.getAttributeExpression("opacity");
		}

		@Override
		public Interpreted<W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickBox> extends QuickContainer.Interpreted.Abstract<W, QuickWidget> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theOpacity;

		public Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
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
		public W create(ExElement parent) {
			return (W) new QuickBox(this, parent);
		}
	}

	private SettableValue<Double> theOpacity;

	public QuickBox(Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		QuickBox.Interpreted<?> myInterpreted = (QuickBox.Interpreted<?>) interpreted;
		theOpacity = myInterpreted.getOpacity() == null ? null : myInterpreted.getOpacity().get(myModels);
	}

	public SettableValue<Double> getOpacity() {
		return theOpacity;
	}
}

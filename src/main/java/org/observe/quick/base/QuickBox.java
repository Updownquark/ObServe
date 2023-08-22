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

public class QuickBox extends QuickContainer.Abstract<QuickWidget> {
	public static final String BOX = "box";
	private static final SingleTypeTraceability<QuickBox, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, BOX, Def.class, Interpreted.class,
			QuickBox.class);

	public static class Def<W extends QuickBox> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		private CompiledExpression theOpacity;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		@QonfigAttributeGetter("opacity")
		public CompiledExpression getOpacity() {
			return theOpacity;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
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
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			theOpacity = getDefinition().getOpacity() == null ? null : getDefinition().getOpacity().interpret(ModelTypes.Value.DOUBLE, env);
		}

		@Override
		public W create() {
			return (W) new QuickBox(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Double>> theOpacityInstantiator;
	private SettableValue<Double> theOpacity;

	public QuickBox(Object id) {
		super(id);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickBox.Interpreted<?> myInterpreted = (QuickBox.Interpreted<?>) interpreted;
		theOpacityInstantiator = myInterpreted.getOpacity() == null ? null : myInterpreted.getOpacity().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theOpacityInstantiator != null)
			theOpacityInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theOpacity = theOpacityInstantiator == null ? null : theOpacityInstantiator.get(myModels);
	}

	public SettableValue<Double> getOpacity() {
		return theOpacity;
	}
}

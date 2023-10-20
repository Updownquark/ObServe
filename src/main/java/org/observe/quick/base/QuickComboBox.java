package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickComboBox<T> extends CollectionSelectorWidget<T> {
	public static final String COMBO_BOX = "combo";

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = COMBO_BOX,
			interpretation = Interpreted.class,
			instance = QuickComboBox.class),
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = "rendering",
		interpretation = Interpreted.class,
		instance = QuickComboBox.class)//
	})
	public static class Def extends CollectionSelectorWidget.Def<QuickComboBox<?>> {
		private QuickWidget.Def<?> theRenderer;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigChildGetter(asType = "rendering", value = "renderer")
		public QuickWidget.Def<?> getRenderer() {
			return theRenderer;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExpressoQIS renderer = session.forChildren("renderer").peekFirst();
			if (renderer == null)
				renderer = session.metadata().get("default-renderer").get().peekFirst();
			theRenderer = ExElement.useOrReplace(QuickWidget.Def.class, theRenderer, renderer, null);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickComboBox<T>> {
		private QuickWidget.Interpreted<?> theRenderer;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public QuickWidget.Interpreted<?> getRenderer() {
			return theRenderer;
		}

		@Override
		public TypeToken<QuickComboBox<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickComboBox.class).<QuickComboBox<T>> parameterized(getValueType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);
			if (theRenderer != null
				&& (getDefinition().getRenderer() == null || theRenderer.getIdentity() != getDefinition().getRenderer().getIdentity())) {
				theRenderer.destroy();
				theRenderer = null;
			}
			if (theRenderer == null && getDefinition().getRenderer() != null)
				theRenderer = getDefinition().getRenderer().interpret(this);
			if (theRenderer != null)
				theRenderer.updateElement(env);
		}

		@Override
		public QuickComboBox<T> create() {
			return new QuickComboBox<>(getIdentity());
		}
	}

	private QuickWidget theRenderer;

	public QuickComboBox(Object id) {
		super(id);
	}

	public QuickWidget getRenderer() {
		return theRenderer;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		if (theRenderer != null
			&& (myInterpreted.getRenderer() == null || theRenderer.getIdentity() != myInterpreted.getRenderer().getIdentity())) {
			theRenderer.destroy();
			theRenderer = null;
		}
		if (theRenderer == null && myInterpreted.getRenderer() != null)
			theRenderer = myInterpreted.getRenderer().create();
		if (theRenderer != null)
			theRenderer.update(myInterpreted.getRenderer(), this);
	}

	@Override
	public void instantiated() {
		super.instantiated();
		if (theRenderer != null)
			theRenderer.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		if (theRenderer != null)
			theRenderer.instantiate(myModels);
	}

	@Override
	public QuickComboBox<T> copy(ExElement parent) {
		QuickComboBox<T> copy = (QuickComboBox<T>) super.copy(parent);

		if (theRenderer != null)
			copy.theRenderer = theRenderer.copy(copy);

		return copy;
	}
}

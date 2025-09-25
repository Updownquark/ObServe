package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Allows access to the size of a widget */
public class QuickSizeListener extends QuickEventListener.Abstract {
	/** The XML name of this listener */
	public static final String SIZE_LISTENER = "on-size-change";

	/** {@link QuickSizeListener} definition */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = SIZE_LISTENER,
		interpretation = Interpreted.class,
		instance = QuickSizeListener.class)
	public static class Def extends QuickEventListener.Def.Abstract<QuickSizeListener> {
		private ModelComponentId theWidth;
		private ModelComponentId theHeight;

		/**
		 * @param parent The parent element of this listener
		 * @param type The Qonfig type of this listener
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The model value ID of the width of the widget */
		public ModelComponentId getWidth() {
			return theWidth;
		}

		/** @return The model value ID of the height of the widget */
		public ModelComponentId getHeight() {
			return theHeight;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theWidth = elModels.getElementValueModelId("width");
			theHeight = elModels.getElementValueModelId("height");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickSizeListener} interpretation */
	public static class Interpreted extends QuickEventListener.Interpreted.Abstract<QuickSizeListener> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickSizeListener create() {
			return new QuickSizeListener(getIdentity());
		}
	}

	private ModelComponentId theWidthId;
	private ModelComponentId theHeightId;
	private SettableValue<Integer> theWidth;
	private SettableValue<Integer> theHeight;

	QuickSizeListener(Object id) {
		super(id);
		theWidth = SettableValue.create(0);
		theHeight = SettableValue.create(0);
	}

	/** @return The observable width of the widget */
	public SettableValue<Integer> getWidth() {
		return theWidth;
	}

	/** @return The observable height of the widget */
	public SettableValue<Integer> getHeight() {
		return theHeight;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theWidthId = myInterpreted.getDefinition().getWidth();
		theHeightId = myInterpreted.getDefinition().getHeight();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theWidthId, myModels, theWidth);
		ExFlexibleElementModelAddOn.satisfyElementValue(theHeightId, myModels, theHeight);
		return myModels;
	}

	@Override
	public QuickSizeListener copy(ExElement parent) {
		QuickSizeListener copy = (QuickSizeListener) super.copy(parent);

		copy.theWidth = SettableValue.create(0);
		copy.theHeight = SettableValue.create(0);

		return copy;
	}
}

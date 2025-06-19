package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickCanvas extends QuickWidget.Abstract {
	public static final String CANVAS = "canvas";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = CANVAS,
		interpretation = Interpreted.class,
		instance = QuickCanvas.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickCanvas> {
		private CompiledExpression thePublishWidth;
		private CompiledExpression thePublishHeight;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("publish-width")
		public CompiledExpression getPublishWidth() {
			return thePublishWidth;
		}

		@QonfigAttributeGetter("publish-height")
		public CompiledExpression getPublishHeight() {
			return thePublishHeight;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			thePublishWidth = getAttributeExpression("publish-width", session);
			thePublishHeight = getAttributeExpression("publish-height", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickCanvas> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> thePublishWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> thePublishHeight;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getPublishWidth() {
			return thePublishWidth;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getPublishHeight() {
			return thePublishHeight;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			thePublishWidth = interpret(getDefinition().getPublishWidth(), ModelTypes.Value.INT);
			thePublishHeight = interpret(getDefinition().getPublishHeight(), ModelTypes.Value.INT);
		}

		@Override
		public QuickCanvas create() {
			return new QuickCanvas(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> thePublishWidthInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> thePublishHeightInstantiator;

	private SettableValue<SettableValue<Integer>> thePublishWidth;
	private SettableValue<SettableValue<Integer>> thePublishHeight;

	QuickCanvas(Object id) {
		super(id);
		thePublishWidth = SettableValue.create();
		thePublishHeight = SettableValue.create();
	}

	public SettableValue<Integer> getPublishWidth() {
		return SettableValue.flatten(thePublishWidth);
	}

	public SettableValue<Integer> getPublishHeight() {
		return SettableValue.flatten(thePublishHeight);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		thePublishWidthInstantiator = myInterpreted.getPublishWidth() == null ? null : myInterpreted.getPublishWidth().instantiate();
		thePublishHeightInstantiator = myInterpreted.getPublishHeight() == null ? null : myInterpreted.getPublishHeight().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (thePublishWidthInstantiator != null)
			thePublishWidthInstantiator.instantiate();
		if (thePublishHeightInstantiator != null)
			thePublishHeightInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		thePublishWidth.set(thePublishWidthInstantiator == null ? null : thePublishWidthInstantiator.get(myModels));
		thePublishHeight.set(thePublishHeightInstantiator == null ? null : thePublishHeightInstantiator.get(myModels));
		return myModels;
	}

	@Override
	public QuickCanvas copy(ExElement parent) {
		QuickCanvas copy = (QuickCanvas) super.copy(parent);
		copy.thePublishWidth = SettableValue.create();
		copy.thePublishHeight = SettableValue.create();
		return copy;
	}
}

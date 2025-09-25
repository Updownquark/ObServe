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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class Scale extends ExElement.Abstract implements TransformOp {
	public static final String SCALE = "scale";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SCALE,
		interpretation = Interpreted.class,
		instance = Scale.class)
	public static class Def extends ExElement.Def.Abstract<Scale> implements TransformOp.Def<Scale> {
		private CompiledExpression theX;
		private CompiledExpression theY;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("x")
		public CompiledExpression getX() {
			return theX;
		}

		@QonfigAttributeGetter("y")
		public CompiledExpression getY() {
			return theY;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theX = getAttributeExpression("x", session);
			theY = getAttributeExpression("y", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<Scale> implements TransformOp.Interpreted<Scale> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theY;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getX() {
			return theX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getY() {
			return theY;
		}

		@Override
		public void updateOperation() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theX = interpret(getDefinition().getX(), ModelTypes.Value.forType(float.class));
			theY = interpret(getDefinition().getY(), ModelTypes.Value.forType(float.class));
		}

		@Override
		public Scale create() {
			return new Scale(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Float>> theXInstantiator;
	private ModelValueInstantiator<SettableValue<Float>> theYInstantiator;

	private SettableValue<SettableValue<Float>> theX;
	private SettableValue<SettableValue<Float>> theY;

	Scale(Object id) {
		super(id);

		theX = SettableValue.create();
		theY = SettableValue.create();
	}

	public SettableValue<Float> getX() {
		return SettableValue.flatten(theX);
	}

	public SettableValue<Float> getY() {
		return SettableValue.flatten(theY);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theXInstantiator = ExElement.instantiate(myInterpreted.getX());
		theYInstantiator = ExElement.instantiate(myInterpreted.getY());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theXInstantiator.instantiate();
		theYInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theX.set(ExElement.get(theXInstantiator, myModels));
		theY.set(ExElement.get(theYInstantiator, myModels));

		return myModels;
	}

	@Override
	public Scale copy(ExElement parent) {
		Scale copy = (Scale) super.copy(parent);

		copy.theX = SettableValue.create();
		copy.theY = SettableValue.create();

		return copy;
	}
}

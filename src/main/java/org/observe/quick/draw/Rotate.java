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

public class Rotate extends ExElement.Abstract implements TransformOp {
	public static final String ROTATE = "rotate";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = ROTATE,
		interpretation = Interpreted.class,
		instance = Rotate.class)
	public static class Def extends ExElement.Def.Abstract<Rotate> implements TransformOp.Def<Rotate> {
		private CompiledExpression theAnchorX;
		private CompiledExpression theAnchorY;
		private CompiledExpression theRadians;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("anchor-x")
		public CompiledExpression getAnchorX() {
			return theAnchorX;
		}

		@QonfigAttributeGetter("anchor-y")
		public CompiledExpression getAnchorY() {
			return theAnchorY;
		}

		@QonfigAttributeGetter("radians")
		public CompiledExpression getRadians() {
			return theRadians;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theAnchorX = getAttributeExpression("anchor-x", session);
			theAnchorY = getAttributeExpression("anchor-y", session);
			theRadians = getAttributeExpression("radians", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<Rotate> implements TransformOp.Interpreted<Rotate> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theAnchorX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theAnchorY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theRadians;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getAnchorX() {
			return theAnchorX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getAnchorY() {
			return theAnchorY;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getRadians() {
			return theRadians;
		}

		@Override
		public void updateOperation() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theAnchorX = interpret(getDefinition().getAnchorX(), ModelTypes.Value.INT);
			theAnchorY = interpret(getDefinition().getAnchorY(), ModelTypes.Value.INT);
			theRadians = interpret(getDefinition().getRadians(), ModelTypes.Value.DOUBLE);
		}

		@Override
		public Rotate create() {
			return new Rotate(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> theAnchorXInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theAnchorYInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theRadiansInstantiator;

	private SettableValue<SettableValue<Integer>> theAnchorX;
	private SettableValue<SettableValue<Integer>> theAnchorY;
	private SettableValue<SettableValue<Double>> theRadians;

	Rotate(Object id) {
		super(id);

		theAnchorX = SettableValue.create();
		theAnchorY = SettableValue.create();
		theRadians = SettableValue.create();
	}

	public SettableValue<Integer> getAnchorX() {
		return SettableValue.flatten(theAnchorX);
	}

	public SettableValue<Integer> getAnchorY() {
		return SettableValue.flatten(theAnchorY);
	}

	public SettableValue<Double> getRadians() {
		return SettableValue.flatten(theRadians);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theAnchorXInstantiator = ExElement.instantiate(myInterpreted.getAnchorX());
		theAnchorYInstantiator = ExElement.instantiate(myInterpreted.getAnchorY());
		theRadiansInstantiator = ExElement.instantiate(myInterpreted.getRadians());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theAnchorXInstantiator.instantiate();
		theAnchorYInstantiator.instantiate();
		theRadiansInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theAnchorX.set(ExElement.get(theAnchorXInstantiator, myModels));
		theAnchorY.set(ExElement.get(theAnchorYInstantiator, myModels));
		theRadians.set(ExElement.get(theRadiansInstantiator, myModels));

		return myModels;
	}

	@Override
	public Rotate copy(ExElement parent) {
		Rotate copy = (Rotate) super.copy(parent);

		copy.theAnchorX = SettableValue.create();
		copy.theAnchorY = SettableValue.create();
		copy.theRadians = SettableValue.create();

		return copy;
	}
}

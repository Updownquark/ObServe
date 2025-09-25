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

public class Translate extends ExElement.Abstract implements TransformOp {
	public static final String TRANSLATE = "translate";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = TRANSLATE,
		interpretation = Interpreted.class,
		instance = Translate.class)
	public static class Def extends ExElement.Def.Abstract<Translate> implements TransformOp.Def<Translate> {
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

	public static class Interpreted extends ExElement.Interpreted.Abstract<Translate> implements TransformOp.Interpreted<Translate> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theY;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		@Override
		public void updateOperation() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theX = interpret(getDefinition().getX(), ModelTypes.Value.INT);
			theY = interpret(getDefinition().getY(), ModelTypes.Value.INT);
		}

		@Override
		public Translate create() {
			return new Translate(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> theXInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theYInstantiator;

	private SettableValue<SettableValue<Integer>> theX;
	private SettableValue<SettableValue<Integer>> theY;

	Translate(Object id) {
		super(id);

		theX = SettableValue.create();
		theY = SettableValue.create();
	}

	public SettableValue<Integer> getX() {
		return SettableValue.flatten(theX);
	}

	public SettableValue<Integer> getY() {
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
	public Translate copy(ExElement parent) {
		Translate copy = (Translate) super.copy(parent);

		copy.theX = SettableValue.create();
		copy.theY = SettableValue.create();

		return copy;
	}
}

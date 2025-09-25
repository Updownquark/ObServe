package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickShapeView extends ExElement.Abstract implements QuickShapePublisher {
	public static final String SHAPE_VIEW = "shape-view";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE_VIEW,
		interpretation = Interpreted.class,
		instance = QuickShapeView.class)
	public static class Def extends ExElement.Def.Abstract<QuickShapeView> implements QuickShapePublisher.Def<QuickShapeView> {
		private final List<TransformOp.Def<?>> theTransformations;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theTransformations = new ArrayList<>();
		}

		@QonfigChildGetter("transform")
		public List<TransformOp.Def<?>> getTransformations() {
			return Collections.unmodifiableList(theTransformations);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			syncChildren(TransformOp.Def.class, theTransformations, session.forChildren("transform"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickShapeView>
	implements QuickShapePublisher.Interpreted<QuickShapeView> {
		private final List<TransformOp.Interpreted<?>> theTransformations;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theTransformations = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public List<TransformOp.Interpreted<?>> getTransformations() {
			return Collections.unmodifiableList(theTransformations);
		}

		@Override
		public void updateElement() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			syncChildren(getDefinition().getTransformations(), theTransformations, def -> def.interpret(this),
				TransformOp.Interpreted::updateOperation);
		}

		@Override
		public QuickShapeView create() {
			return new QuickShapeView(getIdentity());
		}
	}

	private List<TransformOp> theTransformations;

	QuickShapeView(Object id) {
		super(id);
		theTransformations = new ArrayList<>();
	}

	public List<TransformOp> getTransformations() {
		return Collections.unmodifiableList(theTransformations);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		syncChildren(myInterpreted.getTransformations(), theTransformations, TransformOp.Interpreted::create, TransformOp::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (TransformOp tx : theTransformations)
			tx.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		for (TransformOp tx : theTransformations)
			tx.instantiate(myModels);

		return myModels;
	}

	@Override
	public QuickShapeView copy(ExElement parent) {
		QuickShapeView copy = (QuickShapeView) super.copy(parent);

		copy.theTransformations = new ArrayList<>();

		return copy;
	}

}

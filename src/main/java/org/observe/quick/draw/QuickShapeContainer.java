package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickShapeContainer extends ExAddOn.Abstract<ExElement> {
	public static final String SHAPE_CONTAINER = "shape-container";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE_CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickShapeContainer.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, QuickShapeContainer> {
		private final List<QuickShapePublisher.Def<?>> theShapes;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			theShapes = new ArrayList<>();
		}

		@QonfigChildGetter("shape")
		public List<QuickShapePublisher.Def<?>> getShapes() {
			return Collections.unmodifiableList(theShapes);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.singleton((Class<? extends ExAddOn.Def<?, ?>>) ExModelAugmentation.Def.class);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			element.syncChildren(QuickShapePublisher.Def.class, theShapes, session.forChildren("shape"));
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, QuickShapeContainer> {
		private final List<QuickShapePublisher.Interpreted<?>> theShapes;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
			theShapes = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<QuickShapeContainer> getInstanceType() {
			return QuickShapeContainer.class;
		}

		public List<QuickShapePublisher.Interpreted<?>> getShapes() {
			return Collections.unmodifiableList(theShapes);
		}

		@Override
		public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.update(element);
			element.<QuickShapePublisher.Def<?>, QuickShapePublisher.Interpreted<?>> syncChildren(getDefinition().getShapes(), theShapes,
				def -> def.interpret(element), QuickShapePublisher.Interpreted::updateElement);
		}

		@Override
		public QuickShapeContainer create(ExElement element) {
			return new QuickShapeContainer(element);
		}
	}

	private List<QuickShapePublisher> theShapes;

	QuickShapeContainer(ExElement element) {
		super(element);
		theShapes = new ArrayList<>();
	}

	public List<QuickShapePublisher> getShapes() {
		return Collections.unmodifiableList(theShapes);
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<ExElement, ?>> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		element.syncChildren(myInterpreted.getShapes(), theShapes, QuickShapePublisher.Interpreted::create, QuickShapePublisher::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		for (QuickShapePublisher shape : theShapes)
			shape.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		for (QuickShapePublisher shape : theShapes)
			shape.instantiate(models);

		return models;
	}

	@Override
	public QuickShapeContainer copy(ExElement element) {
		QuickShapeContainer copy = (QuickShapeContainer) super.copy(element);

		copy.theShapes = new ArrayList<>();
		for (QuickShapePublisher shape : theShapes)
			copy.theShapes.add(shape.copy(element));

		return copy;
	}
}

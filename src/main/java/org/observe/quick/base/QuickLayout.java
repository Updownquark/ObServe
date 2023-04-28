package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.qommons.config.QonfigAddOn;

public interface QuickLayout extends QuickAddOn<QuickBox> {
	public abstract class Def<L extends QuickLayout> extends QuickAddOn.Def.Abstract<QuickBox, QuickLayout> {
		public Def(QonfigAddOn type, QuickElement.Def<? extends QuickBox> element) {
			super(type, element);
		}

		@Override
		public abstract Interpreted<L> interpret(QuickElement.Interpreted<? extends QuickBox> element);
	}

	public abstract class Interpreted<L extends QuickLayout> extends QuickAddOn.Interpreted.Abstract<QuickBox, L> {

		public Interpreted(Def<L> definition, QuickElement.Interpreted<? extends QuickBox> element) {
			super(definition, element);
		}

		@Override
		public Def<L> getDefinition() {
			return (Def<L>) super.getDefinition();
		}

		@Override
		public abstract Interpreted<L> update(InterpretedModelSet models) throws ExpressoInterpretationException;
	}

	@Override
	Interpreted<?> getInterpreted();

	@Override
	QuickLayout update(ModelSetInstance models) throws ModelInstantiationException;

	public abstract class Abstract implements QuickLayout {
		private final QuickLayout.Interpreted<?> theInterpreted;
		private final QuickBox theElement;

		public Abstract(Interpreted<?> interpreted, QuickBox element) {
			theInterpreted = interpreted;
			theElement = element;
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return theInterpreted;
		}

		@Override
		public QuickBox getElement() {
			return theElement;
		}
	}
}

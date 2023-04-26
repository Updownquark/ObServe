package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickAddOn<E extends QuickElement> {
	public interface Def<E extends QuickElement, AO extends QuickAddOn<E>> {
		QonfigAddOn getType();

		QuickElement.Def<?> getElement();

		void init(QonfigAddOn addOn, QuickElement.Def<?> element);

		Def<E, AO> update(ExpressoQIS session) throws QonfigInterpretationException;

		Interpreted<? extends E, ? extends AO> interpret(QuickElement.Interpreted<?> element);

		public abstract class Abstract<E extends QuickElement, AO extends QuickAddOn<E>> implements Def<E, AO> {
			private QonfigAddOn theType;
			private QuickElement.Def<?> theElement;

			@Override
			public QonfigAddOn getType() {
				return theType;
			}

			@Override
			public QuickElement.Def<?> getElement() {
				return theElement;
			}

			@Override
			public void init(QonfigAddOn addOn, QuickElement.Def<?> element) {
				theType = addOn;
				theElement = element;
			}
		}
	}

	public interface Interpreted<E extends QuickElement, AO extends QuickAddOn<E>> {
		Def<? super E, ? super AO> getDefinition();

		QuickElement.Interpreted<?> getElement();

		Interpreted<E, AO> update(InterpretedModelSet models) throws ExpressoInterpretationException;

		AO create(E element);

		public abstract class Abstract<E extends QuickElement, AO extends QuickAddOn<E>> implements Interpreted<E, AO> {
			private final Def<? super E, ? super AO> theDefinition;
			private final QuickElement.Interpreted<?> theElement;

			public Abstract(Def<? super E, ? super AO> definition, QuickElement.Interpreted<?> element) {
				theDefinition = definition;
				theElement = element;
			}

			@Override
			public Def<? super E, ? super AO> getDefinition() {
				return theDefinition;
			}

			@Override
			public QuickElement.Interpreted<?> getElement() {
				return theElement;
			}
		}
	}

	Interpreted<? super E, ?> getInterpreted();

	E getElement();

	QuickAddOn<E> update(ModelSetInstance models) throws ModelInstantiationException;

	public abstract class Abstract<E extends QuickElement> implements QuickAddOn<E> {
		private final Interpreted<? super E, ?> theInterpreted;
		private final E theElement;

		public Abstract(Interpreted<? super E, ?> interpreted, E element) {
			theInterpreted = interpreted;
			theElement = element;
		}

		@Override
		public Interpreted<? super E, ?> getInterpreted() {
			return theInterpreted;
		}

		@Override
		public E getElement() {
			return theElement;
		}
	}
}

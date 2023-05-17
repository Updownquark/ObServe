package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * The Quick interpretation of a {@link QonfigAddOn}. Hangs on to a {@link QuickElement} and provides specialized functionality to it.
 *
 * @param <E> The super type of element that this type of add-on may be added onto
 */
public interface QuickAddOn<E extends QuickElement> {
	/**
	 * The definition of a {@link QuickAddOn}
	 *
	 * @param <E> The super type of element that this type of add-on may be added onto
	 * @param <AO> The type of add-on that this definition is for
	 */
	public interface Def<E extends QuickElement, AO extends QuickAddOn<? super E>> {
		/** @return The add-on that the Qonfig toolkit uses to represent this type */
		QonfigAddOn getType();

		/** @return The element definition that this add-on is added onto */
		QuickElement.Def<? extends E> getElement();

		/**
		 * Called from the {@link #getElement() element}'s {@link QuickElement.Def#update(org.qommons.config.AbstractQIS) update},
		 * initializes or updates this add-on definition
		 *
		 * @param session The session to support this add-on
		 * @return This add-on definition
		 * @throws QonfigInterpretationException
		 */
		Def<E, AO> update(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * @param element The element interpretation
		 * @return An interpretation of this element definition for the given element interpretation
		 */
		Interpreted<? extends E, ? extends AO> interpret(QuickElement.Interpreted<? extends E> element);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <E> The super type of element that this type of add-on may be added onto
		 * @param <AO> The type of add-on that this definition is for
		 */
		public abstract class Abstract<E extends QuickElement, AO extends QuickAddOn<? super E>> implements Def<E, AO> {
			private final QonfigAddOn theType;
			private final QuickElement.Def<? extends E> theElement;
			private ExpressoQIS theExpressoSession;

			/**
			 * @param type The add-on that the Qonfig toolkit uses to represent this type
			 * @param element The element definition that this add-on is added onto
			 */
			protected Abstract(QonfigAddOn type, QuickElement.Def<? extends E> element) {
				theType = type;
				theElement = element;
			}

			@Override
			public QonfigAddOn getType() {
				return theType;
			}

			@Override
			public QuickElement.Def<? extends E> getElement() {
				return theElement;
			}

			/** @return The session supporting this add-on */
			public ExpressoQIS getExpressoSession() {
				return theExpressoSession;
			}

			@Override
			public Def<E, AO> update(ExpressoQIS session) throws QonfigInterpretationException {
				theExpressoSession = session;
				return this;
			}
		}
	}

	/**
	 *
	 * @param <E> The super type of element that this type of add-on may be added onto
	 * @param <AO> The type of add-on that this interpretation is for
	 */
	public interface Interpreted<E extends QuickElement, AO extends QuickAddOn<? super E>> {
		/** @return The definition that produce this interpretation */
		Def<? super E, ? super AO> getDefinition();

		/** @return The element interpretation that this add-on is added onto */
		QuickElement.Interpreted<?> getElement();

		/**
		 * Called from the {@link #getElement() element}'s update, initializes or updates this add-on interpretation
		 *
		 * @param models The models to support this add-on
		 * @param session The session to support this add-on
		 * @return This add-on interpretation
		 * @throws ExpressoInterpretationException If any models in this add on could not be interpreted
		 */
		Interpreted<E, AO> update(InterpretedModelSet models) throws ExpressoInterpretationException;

		/**
		 * @param element The QuickElement
		 * @return An instance of this add-on for the given element
		 */
		AO create(E element);

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <E> The super type of element that this type of add-on may be added onto
		 * @param <AO> The type of add-on that this interpretation is for
		 */
		public abstract class Abstract<E extends QuickElement, AO extends QuickAddOn<? super E>> implements Interpreted<E, AO> {
			private final Def<? super E, ? super AO> theDefinition;
			private final QuickElement.Interpreted<?> theElement;

			/**
			 * @param definition The definition producing this interpretation
			 * @param element The element interpretation that this add-on is added onto
			 */
			protected Abstract(Def<? super E, ? super AO> definition, QuickElement.Interpreted<?> element) {
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

	/** @return The interpretation that produced this add-on */
	Interpreted<? super E, ?> getInterpreted();

	/** @return The element that this add-on is added onto */
	E getElement();

	/**
	 * Called by the {@link #getElement() element's} update, initializes or updates this add-on
	 *
	 * @param models The models to support this add-on
	 * @return This add-on
	 * @throws ModelInstantiationException If any models in this add-on could not be instantiated
	 */
	QuickAddOn<E> update(ModelSetInstance models) throws ModelInstantiationException;

	/**
	 * An abstract {@link QuickAddOn} implementation
	 *
	 * @param <E> The type of element that this add-on is added onto
	 */
	public abstract class Abstract<E extends QuickElement> implements QuickAddOn<E> {
		private final Interpreted<? super E, ?> theInterpreted;
		private final E theElement;

		/**
		 * @param interpreted The interpretation producing this add-on
		 * @param element The element that this add-on is added onto
		 */
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

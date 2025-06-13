package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExTriFunction;

/**
 * The interpretation of a {@link QonfigAddOn}. Hangs on to an {@link ExElement} and provides specialized functionality to it.
 *
 * @param <E> The super type of element that this type of add-on may be added onto
 */
public interface ExAddOn<E extends ExElement> {
	/**
	 * The definition of a {@link ExAddOn}
	 *
	 * @param <E> The super type of element that this type of add-on may be added onto
	 * @param <AO> The type of add-on that this definition is for
	 */
	public interface Def<E extends ExElement, AO extends ExAddOn<? super E>> {
		/** @return The add-on that the Qonfig toolkit uses to represent this type */
		QonfigAddOn getType();

		/** @return The classes of add-ons whose work this add-on depends on before it can work */
		default Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.emptySet();
		}

		/** @return The element definition that this add-on is added onto */
		ExElement.Def<? extends E> getElement();

		/**
		 * Called from the {@link #getElement() element}'s {@link ExElement.Def#update(ExpressoQIS) update} before
		 * {@link #update(ExpressoQIS, org.observe.expresso.qonfig.ExElement.Def)} has been called on any add-ons
		 *
		 * @param session The session to support this add-on
		 * @param element The element that this add-on is for
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		default void preUpdate(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
		}

		/**
		 * Called from the {@link #getElement() element}'s {@link ExElement.Def#update(ExpressoQIS) update}, initializes or updates this
		 * add-on definition
		 *
		 * @param session The session to support this add-on
		 * @param element The element that this add-on is for
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException;

		/**
		 * Called from the {@link #getElement() element}'s {@link ExElement.Def#update(ExpressoQIS) update} afer
		 * {@link #update(ExpressoQIS, org.observe.expresso.qonfig.ExElement.Def)} has been called on all add-ons
		 *
		 * @param session The session to support this add-on
		 * @param element The element that this add-on is for
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		default void postUpdate(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
		}

		/**
		 * @param element The element interpretation
		 * @return An interpretation of this element definition for the given element interpretation
		 */
		<E2 extends E> Interpreted<? super E2, ? extends AO> interpret(ExElement.Interpreted<E2> element);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <E> The super type of element that this type of add-on may be added onto
		 * @param <AO> The type of add-on that this definition is for
		 */
		public abstract class Abstract<E extends ExElement, AO extends ExAddOn<? super E>> implements Def<E, AO> {
			private final QonfigAddOn theType;
			private final ExElement.Def<? extends E> theElement;

			/**
			 * @param type The add-on that the Qonfig toolkit uses to represent this type
			 * @param element The element definition that this add-on is added onto
			 */
			protected Abstract(QonfigAddOn type, ExElement.Def<? extends E> element) {
				theType = type;
				theElement = element;
			}

			@Override
			public QonfigAddOn getType() {
				return theType;
			}

			@Override
			public ExElement.Def<? extends E> getElement() {
				return theElement;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
			}

			@Override
			public String toString() {
				return theElement + ":<" + theType + ">";
			}
		}
	}

	/**
	 *
	 * @param <E> The super type of element that this type of add-on may be added onto
	 * @param <AO> The type of add-on that this interpretation is for
	 */
	public interface Interpreted<E extends ExElement, AO extends ExAddOn<? super E>> {
		/** @return The definition that produce this interpretation */
		Def<? super E, ? super AO> getDefinition();

		/** @return The element interpretation that this add-on is added onto */
		ExElement.Interpreted<? extends E> getElement();

		/**
		 * Called from the {@link #getElement() element}'s update before {@link #update(org.observe.expresso.qonfig.ExElement.Interpreted)}
		 * has been called on any add-ons
		 *
		 * @param element The element that this add-on is for
		 * @throws ExpressoInterpretationException If an error occurs updating this add-on
		 */
		default void preUpdate(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
		}

		/**
		 * Called from the {@link #getElement() element}'s update, initializes or updates this add-on interpretation
		 *
		 * @param element The element that this add-on is for
		 * @throws ExpressoInterpretationException If any models in this add on could not be interpreted
		 */
		void update(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException;

		/**
		 * Called from the {@link #getElement() element}'s update after {@link #update(org.observe.expresso.qonfig.ExElement.Interpreted)}
		 * has been called on all add-ons
		 *
		 * @param element The element that this add-on is for
		 * @throws ExpressoInterpretationException If an error occurs updating this add-on
		 */
		default void postUpdate(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
		}

		/** @return The type of add-on instance that this interpretation creates */
		public Class<AO> getInstanceType();

		/**
		 * @param element The QonfigDefinedElement
		 * @return An instance of this add-on for the given element
		 */
		AO create(ExElement element);

		/** Destroys this add-on interpretation, releasing resources */
		void destroy();

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <E> The super type of element that this type of add-on may be added onto
		 * @param <AO> The type of add-on that this interpretation is for
		 */
		public abstract class Abstract<E extends ExElement, AO extends ExAddOn<? super E>> implements Interpreted<E, AO> {
			private final Def<? super E, ? super AO> theDefinition;
			private final ExElement.Interpreted<? extends E> theElement;

			/**
			 * @param definition The definition producing this interpretation
			 * @param element The element interpretation that this add-on is added onto
			 */
			protected Abstract(Def<? super E, ? super AO> definition, ExElement.Interpreted<? extends E> element) {
				theDefinition = definition;
				theElement = element;
			}

			@Override
			public Def<? super E, ? super AO> getDefinition() {
				return theDefinition;
			}

			@Override
			public ExElement.Interpreted<? extends E> getElement() {
				return theElement;
			}

			@Override
			public void update(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
			}

			@Override
			public void destroy() {
			}

			@Override
			public String toString() {
				return theDefinition.toString();
			}
		}
	}

	/** @return The type of interpretation that created this add-on */
	Class<? extends Interpreted<E, ?>> getInterpretationType();

	/** @return The element that this add-on is added onto */
	ExElement getElement();

	/**
	 * Called from the {@link #getElement() element}'s {@link ExElement#update(org.observe.expresso.qonfig.ExElement.Interpreted, ExElement)
	 * update} before {@link #update(Interpreted, ExElement)} has been called on any add-ons
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param element The element that this add-on is for
	 */
	default void preUpdate(ExAddOn.Interpreted<? super E, ?> interpreted, ExElement element) {
	}

	/**
	 * Called by the {@link #getElement() element's} update, initializes or updates this add-on
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param element The element that this add-on is for
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	void update(ExAddOn.Interpreted<? super E, ?> interpreted, ExElement element) throws ModelInstantiationException;

	/**
	 * Called from the {@link #getElement() element}'s
	 * {@link ExElement#update(org.observe.expresso.qonfig.ExElement.Interpreted, ExElement)} after {@link #update(Interpreted, ExElement)}
	 * has been called on all add-ons
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param element The element that this add-on is for
	 */
	default void postUpdate(ExAddOn.Interpreted<? super E, ?> interpreted, ExElement element) {
	}

	/**
	 * Called from the {@link #getElement() element}'s {@link ExElement#instantiated() instantiated} before {@link #instantiated()} has been
	 * called on any add-ons
	 *
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	default void preInstantiated() throws ModelInstantiationException {
	}

	/**
	 * Called from the {@link #getElement() element}'s {@link ExElement#instantiated() instantiated}
	 *
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	void instantiated() throws ModelInstantiationException;

	/**
	 * Called from the {@link #getElement() element}'s {@link ExElement#instantiate(ModelSetInstance) instantiate} before
	 * {@link #instantiate(ModelSetInstance)} has been called on any add-ons
	 */
	default void preInstantiate() {
	}

	/**
	 * Called by the {@link #getElement() element's} instantiate, initializes model instantiators in this add-on
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param models The models to support this add-on
	 * @return The possible augmented models
	 * @throws ModelInstantiationException If any models in this add-on could not be instantiated
	 */
	ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException;

	/**
	 * Called from the {@link #getElement() element}'s {@link ExElement#instantiate(ModelSetInstance) instantiate} after
	 * {@link #instantiate(ModelSetInstance)} has been called on all add-ons
	 *
	 * @param models The models to support this add-on
	 * @throws ModelInstantiationException If any models in this add-on could not be instantiated
	 */
	default void postInstantiate(ModelSetInstance models) throws ModelInstantiationException {
	}

	/**
	 * @param element The element to create a copy for
	 * @return A copy of this add-on for the given element
	 */
	ExAddOn<E> copy(ExElement element);

	/** Destroys this add-on, destroying its model values and releasing its resources */
	default void destroy() {
	}

	/**
	 * An abstract {@link ExAddOn} implementation
	 *
	 * @param <E> The type of element that this add-on is added onto
	 */
	public abstract class Abstract<E extends ExElement> implements ExAddOn<E>, Cloneable {
		private ExElement theElement;

		/** @param element The element that this add-on is added onto */
		protected Abstract(ExElement element) {
			theElement = element;
		}

		@Override
		public ExElement getElement() {
			return theElement;
		}

		@Override
		public void update(ExAddOn.Interpreted<? super E, ?> interpreted, ExElement element) throws ModelInstantiationException {
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
		}

		@Override
		public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			return models;
		}

		@Override
		public Abstract<E> copy(ExElement element) {
			Abstract<E> copy = clone();
			copy.theElement = element;
			return copy;
		}

		@Override
		protected Abstract<E> clone() {
			try {
				return (Abstract<E>) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException("Clone not supported?", e);
			}
		}

		@Override
		public String toString() {
			return theElement + ":<" + getClass().getSimpleName() + ">";
		}
	}

	/**
	 * An add-on that can never be instantiated, intended for use as a type parameter for {@link ExAddOn.Def definition} and
	 * {@link ExAddOn.Interpreted interpretation} implementations to signify that they do not actually produce an instance.
	 *
	 * @param <E> The element type that the definition/interpretation apply to
	 */
	public static class Void<E extends ExElement> extends ExAddOn.Abstract<E> {
		private Void() {
			super(null);
			throw new IllegalStateException("Impossible");
		}

		@Override
		public Class<? extends Interpreted<E, ?>> getInterpretationType() {
			return null;
		}
	}

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExAddOn}
	 *
	 * @param <AO> The type of the add-on
	 * @param creator Function to create the add-on from the owner and type
	 * @return The Qonfig interpretation creator
	 */
	static <AO extends Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> creator(
		ExBiFunction<QonfigAddOn, ExElement.Def<?>, AO, QonfigInterpretationException> creator) {
		return session -> creator.apply((QonfigAddOn) session.getFocusType(), session.as(ExpressoQIS.class).getElementRepresentation());
	}

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExAddOn}
	 *
	 * @param <AO> The type of the add-on
	 * @param creator Function to create the add-on from the owner and type
	 * @return The Qonfig interpretation creator
	 */
	static <AO extends Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> creator(
		ExTriFunction<QonfigAddOn, ExElement.Def<?>, ? super ExpressoQIS, AO, QonfigInterpretationException> creator) {
		return session -> {
			ExpressoQIS exSession = session.as(ExpressoQIS.class);
			return creator.apply((QonfigAddOn) session.getFocusType(), exSession.getElementRepresentation(), exSession);
		};
	}

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExAddOn}
	 *
	 * @param <D> The element type required by the add-on type
	 * @param <AO> The type of the add-on
	 * @param defType The element type required by the add-on type
	 * @param creator Function to create the add-on from the owner and type
	 * @return The Qonfig interpretation creator
	 */
	static <E extends ExElement, D extends ExElement.Def<? extends E>, AO extends Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> creator(
		Class<D> defType, ExBiFunction<QonfigAddOn, ExElement.Def<? extends E>, AO, QonfigInterpretationException> creator) {
		return session -> {
			ExElement.Def<?> def = session.as(ExpressoQIS.class).getElementRepresentation();
			if (def != null) {
				// Check the type, but don't use the returned element def, as it may have different model visibility
				def.as(defType, session.reporting().getPosition());
			}
			return creator.apply((QonfigAddOn) session.getFocusType(), (ExElement.Def<? extends E>) def);
		};
	}
}

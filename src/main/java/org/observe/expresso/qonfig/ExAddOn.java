package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

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

		default Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.emptySet();
		}

		/** @return The element definition that this add-on is added onto */
		ExElement.Def<?> getElement();

		default void preUpdate(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
		}

		/**
		 * Called from the {@link #getElement() element}'s {@link ExElement.Def#update(ExpressoQIS) update}, initializes or updates this
		 * add-on definition
		 *
		 * @param session The session to support this add-on
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException;

		default void postUpdate(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
		}

		/**
		 * @param element The element interpretation
		 * @return An interpretation of this element definition for the given element interpretation
		 */
		Interpreted<? extends E, ? extends AO> interpret(ExElement.Interpreted<?> element);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <E> The super type of element that this type of add-on may be added onto
		 * @param <AO> The type of add-on that this definition is for
		 */
		public abstract class Abstract<E extends ExElement, AO extends ExAddOn<? super E>> implements Def<E, AO> {
			private final QonfigAddOn theType;
			private final ExElement.Def<?> theElement;

			/**
			 * @param type The add-on that the Qonfig toolkit uses to represent this type
			 * @param element The element definition that this add-on is added onto
			 */
			protected Abstract(QonfigAddOn type, ExElement.Def<?> element) {
				theType = type;
				theElement = element;
			}

			@Override
			public QonfigAddOn getType() {
				return theType;
			}

			@Override
			public ExElement.Def<?> getElement() {
				return theElement;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
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
		ExElement.Interpreted<?> getElement();

		default void preUpdate(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
		}

		/**
		 * Called from the {@link #getElement() element}'s update, initializes or updates this add-on interpretation
		 *
		 * @param models The models to support this add-on
		 * @param session The session to support this add-on
		 * @throws ExpressoInterpretationException If any models in this add on could not be interpreted
		 */
		void update(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException;

		default void postUpdate(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
		}

		public Class<AO> getInstanceType();

		/**
		 * @param element The QonfigDefinedElement
		 * @return An instance of this add-on for the given element
		 */
		AO create(E element);

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
			private final ExElement.Interpreted<?> theElement;

			/**
			 * @param definition The definition producing this interpretation
			 * @param element The element interpretation that this add-on is added onto
			 */
			protected Abstract(Def<? super E, ? super AO> definition, ExElement.Interpreted<?> element) {
				theDefinition = definition;
				theElement = element;
			}

			@Override
			public Def<? super E, ? super AO> getDefinition() {
				return theDefinition;
			}

			@Override
			public ExElement.Interpreted<?> getElement() {
				return theElement;
			}

			@Override
			public void update(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
			}

			@Override
			public void destroy() {
			}
		}
	}

	Class<? extends Interpreted<?, ?>> getInterpretationType();

	/** @return The element that this add-on is added onto */
	ExElement getElement();

	default void preUpdate(ExAddOn.Interpreted<? extends E, ?> interpreted, E element) {
	}

	/**
	 * Called by the {@link #getElement() element's} update, initializes or updates this add-on
	 *
	 * @param interpreted The interpretation producing this add-on
	 */
	void update(ExAddOn.Interpreted<? extends E, ?> interpreted, E element);

	default void postUpdate(ExAddOn.Interpreted<? extends E, ?> interpreted, E element) {
	}

	default void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {}

	default void preInstantiated() {}

	void instantiated();

	default void preInstantiate() {
	}

	/**
	 * Called by the {@link #getElement() element's} instantiate, initializes model instantiators in this add-on
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param models The models to support this add-on
	 * @throws ModelInstantiationException If any models in this add-on could not be instantiated
	 */
	void instantiate(ModelSetInstance models) throws ModelInstantiationException;

	default void postInstantiate(ModelSetInstance models) throws ModelInstantiationException {
	}

	ExAddOn<E> copy(E element);

	default void destroy() {}

	/**
	 * An abstract {@link ExAddOn} implementation
	 *
	 * @param <E> The type of element that this add-on is added onto
	 */
	public abstract class Abstract<E extends ExElement> implements ExAddOn<E>, Cloneable {
		private E theElement;

		/** @param element The element that this add-on is added onto */
		protected Abstract(E element) {
			theElement = element;
		}

		@Override
		public E getElement() {
			return theElement;
		}

		@Override
		public void update(ExAddOn.Interpreted<? extends E, ?> interpreted, E element) {
		}

		@Override
		public void instantiated() {
		}

		@Override
		public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		}

		@Override
		public Abstract<E> copy(E element) {
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
		public Class<? extends Interpreted<?, ? super E>> getInterpretationType() {
			return null;
		}
	}

	static <AO extends Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> creator(
		BiFunction<QonfigAddOn, ExElement.Def<?>, AO> creator) {
		return session -> creator.apply((QonfigAddOn) session.getFocusType(), session.as(ExpressoQIS.class).getElementRepresentation());
	}

	static <D extends ExElement.Def<?>, AO extends Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> creator(Class<D> defType,
		BiFunction<QonfigAddOn, D, AO> creator) {
		return session -> {
			ExElement.Def<?> def = session.as(ExpressoQIS.class).getElementRepresentation();
			if (def != null && !defType.isInstance(def))
				throw new QonfigInterpretationException("This implementation requires an element definition of type " + defType.getName()
				+ ", not " + (def == null ? "null" : def.getClass().getName()), session.reporting().getPosition(), 0);
			return creator.apply((QonfigAddOn) session.getFocusType(), (D) def);
		};
	}
}

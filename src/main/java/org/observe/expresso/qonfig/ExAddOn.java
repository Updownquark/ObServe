package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * The interpretation of a {@link QonfigAddOn}. Hangs on to an {@link ExElement} and provides specialized functionality to it.
 *
 * @param <E> The super type of element that this type of add-on may be added onto
 */
public interface ExAddOn<E extends ExElement> {
	// public abstract class AddOnAttributeGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ?
	// extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	// implements ExElement.AttributeValueGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
	// private final Class<D> theDefType;
	// private final Class<I> theInterpType;
	//
	// protected AddOnAttributeGetter(Class<? super D> defType, Class<? super I> interpType) {
	// theDefType = (Class<D>) (Class<?>) defType;
	// theInterpType = (Class<I>) (Class<?>) interpType;
	// }
	//
	// public abstract Object getFromDef(D def);
	//
	// public abstract Object getFromInterpreted(I interpreted);
	//
	// @Override
	// public Object getFromDef(ExElement.Def<? extends E> def) {
	// return def.getAddOnValue(theDefType, this::getFromDef);
	// }
	//
	// @Override
	// public Object getFromInterpreted(ExElement.Interpreted<? extends E> interp) {
	// return interp.getAddOnValue(theInterpType, this::getFromInterpreted);
	// }
	//
	// public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends
	// ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
	// Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpretedType,
	// Function<? super I, ?> interpretedGetter) {
	// return new Default<>(defType, defGetter, interpretedType, interpretedGetter);
	// }
	//
	// public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D
	// extends ExAddOn.Def<? super E, ? extends AO>>
	// extends AddOnAttributeGetter<E, AO, I, D> {
	// private final Function<? super D, ?> theDefGetter;
	// private final Function<? super I, ?> theInterpretedGetter;
	//
	// public Default(Class<? super D> defType, Function<? super D, ?> defGetter, Class<? super I> interpType,
	// Function<? super I, ?> interpretedGetter) {
	// super(defType, interpType);
	// theDefGetter = defGetter;
	// theInterpretedGetter = interpretedGetter;
	// }
	//
	// @Override
	// public Object getFromDef(D def) {
	// return theDefGetter.apply(def);
	// }
	//
	// @Override
	// public Object getFromInterpreted(I interpreted) {
	// return theInterpretedGetter.apply(interpreted);
	// }
	// }
	// }
	//
	// public abstract class AddOnChildGetter<E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ? extends
	// AO>, D extends ExAddOn.Def<? super E, ? extends AO>> //
	// implements ExElement.ChildElementGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
	// private final Class<D> theDefType;
	// private final Class<I> theInterpType;
	// private final Class<AO> theAddOnType;
	//
	// protected AddOnChildGetter(Class<? super D> defType, Class<? super I> interpType, Class<? super AO> addOnType) {
	// theDefType = (Class<D>) (Class<?>) defType;
	// theInterpType = (Class<I>) (Class<?>) interpType;
	// theAddOnType = (Class<AO>) (Class<?>) addOnType;
	// }
	//
	// public abstract List<? extends ExElement.Def<?>> getFromDef(D def);
	//
	// public abstract List<? extends ExElement.Interpreted<?>> getFromInterpreted(I interpreted);
	//
	// public abstract List<? extends ExElement> getFromAddOn(AO addOn);
	//
	// @Override
	// public List<? extends ExElement.Def<?>> getChildrenFromDef(ExElement.Def<? extends E> def) {
	// return def.getAddOnValue(theDefType, this::getFromDef);
	// }
	//
	// @Override
	// public List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(ExElement.Interpreted<? extends E> interp) {
	// return interp.getAddOnValue(theInterpType, this::getFromInterpreted);
	// }
	//
	// @Override
	// public List<? extends ExElement> getChildrenFromElement(E element) {
	// return element.getAddOnValue(theAddOnType, this::getFromAddOn);
	// }
	//
	// public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends
	// ExAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
	// Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
	// Class<? super I> interpretedType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
	// Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
	// return new Default<>(defType, defGetter, interpretedType, interpretedGetter, addOnType, addOnGetter);
	// }
	//
	// public static class Default<E extends ExElement, AO extends ExAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D
	// extends ExAddOn.Def<? super E, ? extends AO>>
	// extends AddOnChildGetter<E, AO, I, D> {
	// private final Function<? super D, ? extends List<? extends ExElement.Def<?>>> theDefGetter;
	// private final Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> theInterpretedGetter;
	// private final Function<? super AO, ? extends List<? extends ExElement>> theAddOnGetter;
	//
	// public Default(Class<? super D> defType, Function<? super D, ? extends List<? extends ExElement.Def<?>>> defGetter,
	// Class<? super I> interpType, Function<? super I, ? extends List<? extends ExElement.Interpreted<?>>> interpretedGetter,
	// Class<? super AO> addOnType, Function<? super AO, ? extends List<? extends ExElement>> addOnGetter) {
	// super(defType, interpType, addOnType);
	// theDefGetter = defGetter;
	// theInterpretedGetter = interpretedGetter;
	// theAddOnGetter = addOnGetter;
	// }
	//
	// @Override
	// public List<? extends org.observe.expresso.qonfig.ExElement.Def<?>> getFromDef(D def) {
	// return theDefGetter.apply(def);
	// }
	//
	// @Override
	// public List<? extends org.observe.expresso.qonfig.ExElement.Interpreted<?>> getFromInterpreted(I interpreted) {
	// return theInterpretedGetter.apply(interpreted);
	// }
	//
	// @Override
	// public List<? extends ExElement> getFromAddOn(AO addOn) {
	// return theAddOnGetter.apply(addOn);
	// }
	// }
	// }
	//
	/**
	 * The definition of a {@link ExAddOn}
	 *
	 * @param <E> The super type of element that this type of add-on may be added onto
	 * @param <AO> The type of add-on that this definition is for
	 */
	public interface Def<E extends ExElement, AO extends ExAddOn<? super E>> {
		/** @return The add-on that the Qonfig toolkit uses to represent this type */
		QonfigAddOn getType();

		/** @return The element definition that this add-on is added onto */
		ExElement.Def<? extends E> getElement();

		/**
		 * Called from the {@link #getElement() element}'s {@link ExElement.Def#update(ExpressoQIS) update}, initializes or updates this
		 * add-on definition
		 *
		 * @param session The session to support this add-on
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException;

		/**
		 * @param element The element interpretation
		 * @return An interpretation of this element definition for the given element interpretation
		 */
		Interpreted<? extends E, ? extends AO> interpret(ExElement.Interpreted<? extends E> element);

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
		 * Called from the {@link #getElement() element}'s update, initializes or updates this add-on interpretation
		 *
		 * @param models The models to support this add-on
		 * @param session The session to support this add-on
		 * @throws ExpressoInterpretationException If any models in this add on could not be interpreted
		 */
		void update(InterpretedModelSet models) throws ExpressoInterpretationException;

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
			public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			}

			@Override
			public void destroy() {
			}
		}
	}

	/** @return The element that this add-on is added onto */
	E getElement();

	/**
	 * Called by the {@link #getElement() element's} update, initializes or updates this add-on
	 *
	 * @param interpreted The interpretation producing this add-on
	 * @param models The models to support this add-on
	 * @throws ModelInstantiationException If any models in this add-on could not be instantiated
	 */
	void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException;

	/**
	 * An abstract {@link ExAddOn} implementation
	 *
	 * @param <E> The type of element that this add-on is added onto
	 */
	public abstract class Abstract<E extends ExElement> implements ExAddOn<E> {
		private final E theElement;

		/**
		 * @param interpreted The interpretation producing this add-on
		 * @param element The element that this add-on is added onto
		 */
		protected Abstract(Interpreted<? super E, ?> interpreted, E element) {
			theElement = element;
		}

		@Override
		public E getElement() {
			return theElement;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		}
	}
}

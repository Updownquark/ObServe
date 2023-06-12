package org.observe.quick;

import java.util.function.Function;

import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * The Quick interpretation of a {@link QonfigAddOn}. Hangs on to a {@link QonfigDefinedElement} and provides specialized functionality to it.
 *
 * @param <E> The super type of element that this type of add-on may be added onto
 */
public interface QuickAddOn<E extends QuickElement> {
	public abstract class AddOnAttributeGetter<E extends QuickElement, AO extends QuickAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends QuickAddOn.Def<? super E, ? extends AO>> //
	implements QuickElement.AttributeValueGetter<E, QuickElement.Interpreted<? extends E>, QuickElement.Def<? extends E>> {
		private final Class<D> theDefType;
		private final Class<I> theInterpType;
		private final Class<AO> theAddOnType;

		protected AddOnAttributeGetter(Class<D> defType, Class<I> interpType, Class<AO> addOnType) {
			theDefType = defType;
			theInterpType = interpType;
			theAddOnType = addOnType;
		}

		public abstract Object getFromDef(D def);

		public abstract Object getFromInterpreted(I interpreted);

		public abstract Object getFromAddOn(AO addOn);

		@Override
		public Object getFromDef(QuickElement.Def<? extends E> def) {
			return def.getAddOnValue(theDefType, this::getFromDef);
		}

		@Override
		public Object getFromInterpreted(QuickElement.Interpreted<? extends E> interp) {
			return interp.getAddOnValue(theInterpType, this::getFromInterpreted);
		}

		@Override
		public Object getFromElement(QuickElement element) {
			return element.getAddOnValue(theAddOnType, this::getFromAddOn);
		}

		public static <E extends QuickElement, AO extends QuickAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends QuickAddOn.Def<? super E, ? extends AO>> Default<E, AO, I, D> of(
			Class<D> defType, Function<? super D, ?> defGetter, Class<I> interpretedType, Function<? super I, ?> interpretedGetter,
			Class<AO> addOnType, Function<? super AO, ?> addOnGetter) {
			return new Default<>(defType, defGetter, interpretedType, interpretedGetter, addOnType, addOnGetter);
		}

		public static <E extends QuickElement, AO extends QuickAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends QuickAddOn.Def<? super E, ? extends AO>, M, MV extends M> Expression<E, AO, I, D, M, MV> ofX(
			Class<D> defType, Function<? super D, ? extends CompiledExpression> defGetter, Class<I> interpretedType,
			Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> interpretedGetter, Class<AO> addOnType,
				Function<? super AO, ? extends MV> addOnGetter) {
			return new Expression<>(defType, defGetter, interpretedType, interpretedGetter, addOnType, addOnGetter);
		}

		public static class Default<E extends QuickElement, AO extends QuickAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends QuickAddOn.Def<? super E, ? extends AO>>
		extends AddOnAttributeGetter<E, AO, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;
			private final Function<? super AO, ?> theAddOnGetter;

			public Default(Class<D> defType, Function<? super D, ?> defGetter, Class<I> interpType,
				Function<? super I, ?> interpretedGetter, Class<AO> addOnType, Function<? super AO, ?> addOnGetter) {
				super(defType, interpType, addOnType);
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theAddOnGetter = addOnGetter;
			}

			@Override
			public Object getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public Object getFromInterpreted(I interpreted) {
				return theInterpretedGetter.apply(interpreted);
			}

			@Override
			public Object getFromAddOn(AO addOn) {
				return theAddOnGetter.apply(addOn);
			}
		}

		public static class Expression<E extends QuickElement, AO extends QuickAddOn<? super E>, I extends Interpreted<? super E, ? extends AO>, D extends QuickAddOn.Def<? super E, ? extends AO>, M, MV extends M>
		extends AddOnAttributeGetter<E, AO, I, D> {
			private final Function<? super D, ? extends CompiledExpression> theDefGetter;
			private final Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> theInterpretedGetter;
			private final Function<? super AO, ? extends MV> theAddOnGetter;

			public Expression(Class<D> defType, Function<? super D, ? extends CompiledExpression> defGetter, Class<I> interpType,
				Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> interpretedGetter, Class<AO> addOnType,
					Function<? super AO, ? extends MV> addOnGetter) {
				super(defType, interpType, addOnType);
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theAddOnGetter = addOnGetter;
			}

			@Override
			public Object getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public Object getFromInterpreted(I interpreted) {
				return theInterpretedGetter.apply(interpreted);
			}

			@Override
			public Object getFromAddOn(AO addOn) {
				return theAddOnGetter.apply(addOn);
			}
		}
	}

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
		 * Called from the {@link #getElement() element}'s {@link QonfigDefinedElement.Def#update(ExpressoQIS) update}, initializes or updates this
		 * add-on definition
		 *
		 * @param session The session to support this add-on
		 * @throws QonfigInterpretationException If an error occurs updating this add-on
		 */
		void update(ExpressoQIS session, QuickElement.Def<? extends E> element) throws QonfigInterpretationException;

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

			@Override
			public void update(ExpressoQIS session, QuickElement.Def<? extends E> element) throws QonfigInterpretationException {
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
		QuickElement.Interpreted<? extends E> getElement();

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
		public abstract class Abstract<E extends QuickElement, AO extends QuickAddOn<? super E>> implements Interpreted<E, AO> {
			private final Def<? super E, ? super AO> theDefinition;
			private final QuickElement.Interpreted<? extends E> theElement;

			/**
			 * @param definition The definition producing this interpretation
			 * @param element The element interpretation that this add-on is added onto
			 */
			protected Abstract(Def<? super E, ? super AO> definition, QuickElement.Interpreted<? extends E> element) {
				theDefinition = definition;
				theElement = element;
			}

			@Override
			public Def<? super E, ? super AO> getDefinition() {
				return theDefinition;
			}

			@Override
			public QuickElement.Interpreted<? extends E> getElement() {
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
	void update(QuickAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException;

	/**
	 * An abstract {@link QuickAddOn} implementation
	 *
	 * @param <E> The type of element that this add-on is added onto
	 */
	public abstract class Abstract<E extends QuickElement> implements QuickAddOn<E> {
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
		public void update(QuickAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		}
	}
}

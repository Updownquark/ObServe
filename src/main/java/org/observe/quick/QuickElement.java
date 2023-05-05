package org.observe.quick;

import java.util.Collection;
import java.util.function.Function;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.StyleQIS;
import org.qommons.ClassMap;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;

/** The base type of values interpreted from Quick-typed {@link QonfigElement}s */
public interface QuickElement {
	/** The property in the Qonfig interpretation session where the definition interpreted from the session is stored. */
	public static final String SESSION_QUICK_ELEMENT = "quick.element.def";

	/**
	 * The definition of an element, interpreted via {@link QonfigInterpreterCore Qonfig interpretation} from {@link QonfigElement}s
	 *
	 * @param <E> The type of element that this definition is for
	 */
	public interface Def<E extends QuickElement> {
		/** @return The definition interpreted from the parent element */
		Def<?> getParentElement();

		/** @return The QonfigElement that this definition was interpreted from */
		QonfigElement getElement();

		/** @return The Expresso session supporting this element definition */
		ExpressoQIS getExpressoSession();

		/** @return The style session supporting this element definition */
		StyleQIS getStyleSession();

		/** @return This element's models */
		ObservableModelSet.Built getModels();

		/**
		 * @param <AO> The type of the add-on to get
		 * @param addOn The type of the add-on to get
		 * @return The add-on in this element definition of the given type
		 */
		<AO extends QuickAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn);

		/** @return All add-ons on this element definition */
		Collection<QuickAddOn.Def<? super E, ?>> getAddOns();

		/**
		 * @param <AO> The type of the add on
		 * @param <T> The type of the value
		 * @param addOn The type of the addon
		 * @param fn Produces the value from the add on if it exists
		 * @return The value from the given add on in this element definition, or null if no such add-on is present
		 */
		default <AO extends QuickAddOn.Def<? super E, ?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		/**
		 * Updates this element definition. Must be called at least once after interpretation produces this object.
		 *
		 * @param session The session supporting this element definition
		 * @return This element definition
		 * @throws QonfigInterpretationException If an error occurs interpreting some of this element's fields or content
		 */
		Def<E> update(AbstractQIS<?> session) throws QonfigInterpretationException;

		/**
		 * An abstract implementation of {@link Def}
		 *
		 * @param <E> The type of the element that this definition is for
		 */
		public abstract class Abstract<E extends QuickElement> implements Def<E> {
			private final QuickElement.Def<?> theParent;
			private QonfigElement theElement;
			private ExpressoQIS theExpressoSession;
			private StyleQIS theStyleSession;
			private final ClassMap<QuickAddOn.Def<? super E, ?>> theAddOns;
			private ObservableModelSet.Built theModels;

			/**
			 * @param parent The definition interpreted from the parent element
			 * @param element The element that this definition is being interpreted from
			 */
			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				theParent = parent;
				theElement = element;
				theAddOns = new ClassMap<>();
			}

			@Override
			public QuickElement.Def<?> getParentElement() {
				return theParent;
			}

			@Override
			public QonfigElement getElement() {
				return theElement;
			}

			@Override
			public ExpressoQIS getExpressoSession() {
				return theExpressoSession;
			}

			@Override
			public StyleQIS getStyleSession() {
				return theStyleSession;
			}

			@Override
			public <AO extends QuickAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<QuickAddOn.Def<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			@Override
			public ObservableModelSet.Built getModels() {
				return theModels;
			}

			@Override
			public Abstract<E> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				session.put(SESSION_QUICK_ELEMENT, this);
				theElement = session.getElement();
				theExpressoSession = session.as(ExpressoQIS.class);
				theStyleSession = session.as(StyleQIS.class);
				ObservableModelSet models = theExpressoSession.getExpressoEnv().getModels();
				theModels = models instanceof ObservableModelSet.Builder ? ((ObservableModelSet.Builder) models).build()
					: (ObservableModelSet.Built) models;

				if (theAddOns.isEmpty()) {
					// Add-ons can't change, because if they do, the Quick definition should be re-interpreted from the session
					for (QonfigAddOn addOn : session.getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
						addAddOn(session.asElementOnly(addOn));

					for (QuickAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.update(theExpressoSession.asElement(addOn.getType()));
				}
				return this;
			}

			private void addAddOn(AbstractQIS<?> session) throws QonfigInterpretationException {
				if (session.supportsInterpretation(QuickAddOn.Def.class)) {
					QuickAddOn.Def<? super E, ?> addOn = session.interpret(QuickAddOn.Def.class);
					theAddOns.put(addOn.getClass(), addOn);
				} else {
					for (QonfigAddOn inh : session.getFocusType().getInheritance())
						addAddOn(session.asElementOnly(inh));
				}
			}

			@Override
			public String toString() {
				return theElement.toString();
			}
		}
	}

	/**
	 * Produced from a {@link Def}. This object may contain more definite information that is present in its {@link #getDefinition()
	 * definition}, especially information about types and links between model values.
	 *
	 * @param <E> The type of element that this interpretation is for
	 */
	public interface Interpreted<E extends QuickElement> {
		/** @return The definition that produced this interpretation */
		Def<? super E> getDefinition();

		/** @return The interpretation from the parent element */
		Interpreted<?> getParentElement();

		/** @return This element's models */
		InterpretedModelSet getModels();

		/**
		 * @param <AO> The type of the add-on to get
		 * @param addOn The type of the add-on to get
		 * @return The add-on in this element definition of the given type
		 */
		<AO extends QuickAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn);

		/** @return All add-ons on this element definition */
		Collection<QuickAddOn.Interpreted<? super E, ?>> getAddOns();

		/**
		 * @param <AO> The type of the add on
		 * @param <T> The type of the value
		 * @param addOn The type of the addon
		 * @param fn Produces the value from the add on if it exists
		 * @return The value from the given add on in this element definition, or null if no such add-on is present
		 */
		default <AO extends QuickAddOn.Interpreted<? super E, ?>, T> T getAddOnValue(Class<AO> addOn,
			Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		/**
		 * An abstract implementation of {@link Interpreted}
		 *
		 * @param <E> The type of element that this interpretation is for
		 */
		public abstract class Abstract<E extends QuickElement> implements Interpreted<E> {
			private final Def<? super E> theDefinition;
			private final Interpreted<?> theParent;
			private final ClassMap<QuickAddOn.Interpreted<? super E, ?>> theAddOns;
			private InterpretedModelSet theModels;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			public Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = definition;
				theParent = parent;
				theAddOns = new ClassMap<>();
				for (QuickAddOn.Def<? super E, ?> addOn : definition.getAddOns()) {
					QuickAddOn.Interpreted<? super E, ?> interp = (QuickAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					theAddOns.put(interp.getClass(), interp);
				}
			}

			@Override
			public Def<? super E> getDefinition() {
				return theDefinition;
			}

			@Override
			public Interpreted<?> getParentElement() {
				return theParent;
			}

			@Override
			public InterpretedModelSet getModels() {
				return theModels;
			}

			@Override
			public <AO extends QuickAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<QuickAddOn.Interpreted<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			/**
			 * Updates this element interpretation. Must be called at least once after the {@link #getDefinition() definition} produces this
			 * object.
			 *
			 * @return This interpretation
			 * @throws ExpressoInterpretationException If any model values in this element or any of its content fail to be interpreted
			 */
			protected Abstract<E> update() throws ExpressoInterpretationException {
				// Do I need this?
				theModels = getDefinition().getModels().interpret();
				// theDefinition.getExpressoSession().setExpressoEnv(theDefinition.getExpressoSession().getExpressoEnv().with(models,
				// null));
				theDefinition.getExpressoSession().interpretLocalModel();
				for (QuickAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.update(theModels);
				return this;
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}
	}

	/** @return The interpretation that produced this element */
	Interpreted<?> getInterpreted();

	/** @return The parent element */
	QuickElement getParentElement();

	/** @return This element's model instance */
	ModelSetInstance getModels();

	/**
	 * @param <AO> The type of the add-on to get
	 * @param addOn The type of the add-on to get
	 * @return The add-on in this element definition of the given type
	 */
	<AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn);

	/** @return All add-ons on this element definition */
	Collection<QuickAddOn<?>> getAddOns();

	/**
	 * @param <AO> The type of the add on
	 * @param <T> The type of the value
	 * @param addOn The type of the addon
	 * @param fn Produces the value from the add on if it exists
	 * @return The value from the given add on in this element definition, or null if no such add-on is present
	 */
	default <AO extends QuickAddOn<?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
		AO ao = getAddOn(addOn);
		return ao == null ? null : fn.apply(ao);
	}

	/** Abstract {@link QuickElement} implementation */
	public abstract class Abstract implements QuickElement {
		private final Interpreted<?> theInterpreted;
		private final QuickElement theParent;
		private final ClassMap<QuickAddOn<?>> theAddOns;
		private ModelSetInstance theModels;

		/**
		 * @param interpreted The interpretation producing this element
		 * @param parent The parent element
		 */
		public Abstract(Interpreted<?> interpreted, QuickElement parent) {
			theInterpreted = interpreted;
			theParent = parent;
			theAddOns = new ClassMap<>();
			for (QuickAddOn.Interpreted<?, ?> addOn : theInterpreted.getAddOns()) {
				QuickAddOn<?> inst = ((QuickAddOn.Interpreted<QuickElement, ?>) addOn).create(this);
				theAddOns.put(inst.getClass(), inst);
			}
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return theInterpreted;
		}

		@Override
		public QuickElement getParentElement() {
			return theParent;
		}

		@Override
		public ModelSetInstance getModels() {
			return theModels;
		}

		@Override
		public <AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn) {
			return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
		}

		@Override
		public Collection<QuickAddOn<?>> getAddOns() {
			return theAddOns.getAllValues();
		}

		/**
		 * Updates this element. Must be called at least once after being produced by its {@link #getInterpreted() interpretation}.
		 *
		 * @param models The model instance for this element
		 * @return This element
		 * @throws ModelInstantiationException If an error occurs instantiating any model values needed by this element or its content
		 */
		protected Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			theModels = theInterpreted.getDefinition().getExpressoSession().wrapLocal(models);
			for (QuickAddOn<?> addOn : theAddOns.getAllValues())
				addOn.update(getModels());
			return this;
		}

		@Override
		public String toString() {
			return getInterpreted().toString();
		}
	}

	/**
	 * @param element1 The first element to compare
	 * @param element2 The second element to compare
	 * @return Whether the type information of both elements are the same
	 */
	public static boolean typesEqual(QonfigElement element1, QonfigElement element2) {
		return element1.getType() == element2.getType() && element1.getInheritance().equals(element2.getInheritance());
	}

	/**
	 * @param <D> The type of the element definition
	 * @param type The type of the element definition
	 * @param def The definition currently in use
	 * @param session The parent session to interpret the new definition from, if needed
	 * @param childName The name of the child role fulfilled by the element to parse the definition from
	 * @return The given definition if it is up-to-date, or the newly interpreted one
	 * @throws QonfigInterpretationException If the definition could not be interpreted
	 * @throws IllegalArgumentException If no such child role exists
	 */
	public static <D extends QuickElement.Def<?>> D useOrReplace(Class<? extends D> type, D def,
		AbstractQIS<?> session, String childName) throws QonfigInterpretationException, IllegalArgumentException {
		QonfigElement element = session.getChildren(childName).peekFirst();
		if (element == null)
			return null;
		else if (def != null && typesEqual(def.getElement(), element))
			return def;
		AbstractQIS<?> childSession = session.forChildren(childName).getFirst();
		def = childSession.interpret(type);
		def.update(childSession);
		return def;
	}
}

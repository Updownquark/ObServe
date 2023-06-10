package org.observe.quick;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

/** The base type of values interpreted from Quick-typed {@link QonfigElement}s */
public interface QuickElement extends Identifiable {
	/** The property in the Qonfig interpretation session where the definition interpreted from the session is stored. */
	public static final String SESSION_QUICK_ELEMENT = "quick.element.def";

	public static class Identity {
		private final String theElementType;
		private final LocatedFilePosition thePosition;

		public Identity(String elementType, LocatedFilePosition position) {
			theElementType = elementType;
			thePosition = position;
		}

		public String getElementType() {
			return theElementType;
		}

		public LocatedFilePosition getPosition() {
			return thePosition;
		}

		@Override
		public String toString() {
			return new StringBuilder().append('<').append(theElementType).append(thePosition.toShortString()).toString();
		}
	}

	/**
	 * The definition of an element, interpreted via {@link QonfigInterpreterCore Qonfig interpretation} from {@link QonfigElement}s
	 *
	 * @param <E> The type of element that this definition is for
	 */
	public interface Def<E extends QuickElement> extends Identifiable {
		@Override
		Identity getIdentity();

		/** @return The definition interpreted from the parent element */
		Def<?> getParentElement();

		/** @return The QonfigElement that this definition was interpreted from */
		QonfigElement getElement();

		ErrorReporting reporting();

		ExpressoEnv getExpressoEnv();

		/** @return This element's models */
		ObservableModelSet.Built getModels();

		Class<?> getCallingClass();

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
		 * @param addOn The type of the add-on
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
		 * @throws QonfigInterpretationException If an error occurs interpreting some of this element's fields or content
		 */
		void update(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * An abstract implementation of {@link Def}
		 *
		 * @param <E> The type of the element that this definition is for
		 */
		public abstract class Abstract<E extends QuickElement> implements Def<E> {
			private final Identity theId;
			private final QuickElement.Def<?> theParent;
			private QonfigElement theElement;
			private final ClassMap<QuickAddOn.Def<? super E, ?>> theAddOns;
			private ExpressoEnv theExpressoEnv;
			private ObservableModelSet.Built theModels;
			private ErrorReporting theReporting;
			private Class<?> theCallingClass;

			/**
			 * @param parent The definition interpreted from the parent element
			 * @param element The element that this definition is being interpreted from
			 */
			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				theId = new Identity(element.getType().getName(), element.getPositionInFile());
				theParent = parent;
				theElement = element;
				theAddOns = new ClassMap<>();
			}

			@Override
			public Identity getIdentity() {
				return theId;
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
			public ErrorReporting reporting() {
				return theReporting;
			}

			@Override
			public Class<?> getCallingClass() {
				return theCallingClass;
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
			public ExpressoEnv getExpressoEnv() {
				return theExpressoEnv;
			}

			@Override
			public ObservableModelSet.Built getModels() {
				return theModels;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				theReporting = session.reporting();
				theCallingClass = session.getWrapped().getInterpreter().getCallingClass();
				session.put(SESSION_QUICK_ELEMENT, this);
				theElement = session.getElement();
				theExpressoEnv = session.getExpressoEnv();
				ObservableModelSet models = theExpressoEnv.getModels();
				theModels = models instanceof ObservableModelSet.Builder ? ((ObservableModelSet.Builder) models).build()
					: (ObservableModelSet.Built) models;

				if (theAddOns.isEmpty()) {
					// Add-ons can't change, because if they do, the Quick definition should be re-interpreted from the session
					for (QonfigAddOn addOn : session.getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance))
						addAddOn(session.asElementOnly(addOn));

					for (QuickAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.update(session.asElement(addOn.getType()));
				}
			}

			public void checkElement(QonfigElementOrAddOn element, String tkName, Version tkVersion, String elementName) {
				if (!element.getDeclarer().getName().equals(tkName) || element.getDeclarer().getMajorVersion() != tkVersion.major
					|| element.getDeclarer().getMinorVersion() != tkVersion.minor || !element.getName().equals(elementName))
					throw new IllegalStateException(
						"This class is designed against " + tkName + " v" + tkVersion.major + "." + tkVersion.minor + " " + elementName
						+ ", not " + element.getDeclarer().getName() + " v" + element.getDeclarer().getMajorVersion() + "."
						+ element.getDeclarer().getMinorVersion() + " " + element.getName());
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

		ObservableValue<Boolean> isDestroyed();

		default Observable<ObservableValueEvent<Boolean>> destroyed() {
			return isDestroyed().changes().filter(evt -> Boolean.TRUE.equals(evt.getNewValue())).take(1);
		}

		void destroy();

		/**
		 * An abstract implementation of {@link Interpreted}
		 *
		 * @param <E> The type of element that this interpretation is for
		 */
		public abstract class Abstract<E extends QuickElement> implements Interpreted<E> {
			private final Def<? super E> theDefinition;
			private final Interpreted<?> theParent;
			private final ClassMap<QuickAddOn.Interpreted<? super E, ?>> theAddOns;
			private final SettableValue<Boolean> isDestroyed;
			private InterpretedModelSet theModels;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			protected Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = definition;
				theParent = parent;
				theAddOns = new ClassMap<>();
				for (QuickAddOn.Def<? super E, ?> addOn : definition.getAddOns()) {
					QuickAddOn.Interpreted<? super E, ?> interp = (QuickAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					theAddOns.put(interp.getClass(), interp);
				}
				isDestroyed = SettableValue.build(boolean.class).withValue(false).build();
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

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return isDestroyed.unsettable();
			}

			@Override
			public void destroy() {
				for (QuickAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.destroy();
				theAddOns.clear();
				if (!isDestroyed.get().booleanValue())
					isDestroyed.set(true, null);
			}

			/**
			 * Updates this element interpretation. Must be called at least once after the {@link #getDefinition() definition} produces this
			 * object.
			 *
			 * @throws ExpressoInterpretationException If any model values in this element or any of its content fail to be interpreted
			 */
			protected void update() throws ExpressoInterpretationException {
				// Do I need this?
				theModels = getDefinition().getModels().interpret();
				// theDefinition.getExpressoSession().setExpressoEnv(theDefinition.getExpressoSession().getExpressoEnv().with(models,
				// null));
				theDefinition.getExpressoEnv().interpretLocalModel();
				for (QuickAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.update(theModels);
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}
	}

	@Override
	Identity getIdentity();

	/** @return The parent element */
	QuickElement getParentElement();

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
	 * @param addOn The type of the add-on
	 * @param fn Produces the value from the add on if it exists
	 * @return The value from the given add on in this element definition, or null if no such add-on is present
	 */
	default <AO extends QuickAddOn<?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
		AO ao = getAddOn(addOn);
		return ao == null ? null : fn.apply(ao);
	}

	ErrorReporting reporting();

	/**
	 * Updates this element. Must be called at least once after being produced by its interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param models The model instance for this element
	 * @throws ModelInstantiationException If an error occurs instantiating any model values needed by this element or its content
	 */
	ModelSetInstance update(Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException;

	/** Abstract {@link QuickElement} implementation */
	public abstract class Abstract implements QuickElement {
		private final Identity theId;
		private final QuickElement theParent;
		private final ClassMap<QuickAddOn<?>> theAddOns;
		private final ClassMap<Class<? extends QuickAddOn.Interpreted<?, ?>>> theAddOnInterpretations;
		private final ErrorReporting theReporting;

		/**
		 * @param interpreted The interpretation producing this element
		 * @param parent The parent element
		 */
		protected Abstract(Interpreted<?> interpreted, QuickElement parent) {
			theId = interpreted.getDefinition().getIdentity();
			theParent = parent;
			theAddOns = new ClassMap<>();
			theAddOnInterpretations = new ClassMap<>();
			for (QuickAddOn.Interpreted<?, ?> addOn : interpreted.getAddOns()) {
				QuickAddOn<?> inst = ((QuickAddOn.Interpreted<QuickElement, ?>) addOn).create(this);
				theAddOns.put(inst.getClass(), inst);
				theAddOnInterpretations.put(inst.getClass(), (Class<? extends QuickAddOn.Interpreted<?, ?>>) addOn.getClass());
			}
			theReporting = interpreted.getDefinition().reporting();
		}

		@Override
		public Identity getIdentity() {
			return theId;
		}

		@Override
		public QuickElement getParentElement() {
			return theParent;
		}

		@Override
		public <AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn) {
			return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
		}

		@Override
		public Collection<QuickAddOn<?>> getAddOns() {
			return theAddOns.getAllValues();
		}

		@Override
		public ErrorReporting reporting() {
			return theReporting;
		}

		@Override
		public ModelSetInstance update(Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = createElementModel(interpreted, models);
			updateModel(interpreted, myModels);
			return myModels;
		}

		protected ModelSetInstance createElementModel(Interpreted<?> interpreted, ModelSetInstance parentModels)
			throws ModelInstantiationException {
			return interpreted.getDefinition().getExpressoEnv().wrapLocal(parentModels);
		}

		protected void updateModel(Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			for (QuickAddOn<?> addOn : theAddOns.getAllValues()) {
				Class<? extends QuickAddOn.Interpreted<QuickElement, ?>> addOnInterpType;
				addOnInterpType = (Class<? extends QuickAddOn.Interpreted<QuickElement, ?>>) theAddOnInterpretations.get(addOn.getClass(),
					ClassMap.TypeMatch.EXACT);
				QuickAddOn.Interpreted<?, ?> interpretedAddOn = interpreted.getAddOn(addOnInterpType);
				((QuickAddOn<QuickElement>) addOn).update(interpretedAddOn, myModels);
			}
		}

		@Override
		public String toString() {
			return theId.toString();
		}

		protected <M, MV extends M> void satisfyContextValue(String valueName, ModelInstanceType<M, MV> type, MV value,
			ModelSetInstance models) throws ModelInstantiationException {
			QuickElement.satisfyContextValue(valueName, type, value, models, this);
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

	public static <M, MV extends M> void satisfyContextValue(String valueName, ModelInstanceType<M, MV> type, MV value,
		ModelSetInstance models, QuickElement element) throws ModelInstantiationException {
		if (value != null) {
			try {
				DynamicModelValue.satisfyDynamicValue(valueName, type, models, value);
			} catch (ModelException e) {
				throw new ModelInstantiationException("No " + valueName + " value?",
					element.reporting().getFileLocation().getPosition(0), 0, e);
			} catch (TypeConversionException e) {
				throw new IllegalStateException(valueName + " is not a " + type + "?", e);
			}
		}
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
		ExpressoQIS session, String childName) throws QonfigInterpretationException, IllegalArgumentException {
		QonfigElement element = session.getChildren(childName).peekFirst();
		if (element == null)
			return null;
		else if (def != null && typesEqual(def.getElement(), element))
			return def;
		ExpressoQIS childSession = session.forChildren(childName).getFirst();
		def = childSession.interpret(type);
		def.update(childSession);
		return def;
	}

	public static <T extends QuickElement.Def<?>> void syncDefs(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions)
		throws QonfigInterpretationException {
		CollectionUtils.synchronize((List<T>) defs, sessions, //
			(widget, child) -> QuickElement.typesEqual(widget.getElement(), child.getElement()))//
		.simpleE(child -> child.interpret(defType))//
		.rightOrder()//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue()))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue()))//
		.adjust();
	}
}

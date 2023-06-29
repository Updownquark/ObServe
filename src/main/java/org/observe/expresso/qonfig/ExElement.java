package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;

/** A base type for values interpreted from {@link QonfigElement}s */
public interface ExElement extends Identifiable {
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
			return new StringBuilder().append('<').append(theElementType).append('>').append(thePosition.toShortString()).toString();
		}
	}

	public interface AttributeValueGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		Object getFromDef(D def);

		Object getFromInterpreted(I interp);

		Object getFromElement(E element);

		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> Default<E, I, D> of(
			Function<? super D, ?> defGetter, Function<? super I, ?> interpretedGetter, Function<? super E, ?> elementGetter) {
			return new Default<>(defGetter, interpretedGetter, elementGetter);
		}

		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>, M, MV extends M> Expression<E, I, D, M, MV> ofX(
			Function<? super D, ? extends CompiledExpression> defGetter,
			Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> interpretedGetter,
				Function<? super E, ? extends MV> elementGetter) {
			return new Expression<>(defGetter, interpretedGetter, elementGetter);
		}

		public static <E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>> AddOn<E, AO, I, D> addOn(
			Class<D> defType, Class<I> interpretedType, Class<AO> addOnType) {
			return new AddOn<>(defType, interpretedType, addOnType);
		}

		public static class Default<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>>
		implements AttributeValueGetter<E, I, D> {
			private final Function<? super D, ?> theDefGetter;
			private final Function<? super I, ?> theInterpretedGetter;
			private final Function<? super E, ?> theElementGetter;

			public Default(Function<? super D, ?> defGetter, Function<? super I, ?> interpretedGetter,
				Function<? super E, ?> elementGetter) {
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theElementGetter = elementGetter;
			}

			@Override
			public Object getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public Object getFromInterpreted(I interp) {
				return theInterpretedGetter == null ? null : theInterpretedGetter.apply(interp);
			}

			@Override
			public Object getFromElement(E element) {
				return theElementGetter == null ? null : theElementGetter.apply(element);
			}
		}

		public static class Expression<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>, M, MV extends M>
		implements AttributeValueGetter<E, I, D> {
			private final Function<? super D, ? extends LocatedExpression> theDefGetter;
			private final Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> theInterpretedGetter;
			private final Function<? super E, ? extends MV> theElementGetter;

			public Expression(Function<? super D, ? extends LocatedExpression> defGetter,
				Function<? super I, ? extends InterpretedValueSynth<M, ? extends MV>> interpretedGetter,
					Function<? super E, ? extends MV> elementGetter) {
				theDefGetter = defGetter;
				theInterpretedGetter = interpretedGetter;
				theElementGetter = elementGetter;
			}

			@Override
			public LocatedExpression getFromDef(D def) {
				return theDefGetter.apply(def);
			}

			@Override
			public InterpretedValueSynth<M, ? extends MV> getFromInterpreted(I interp) {
				return theInterpretedGetter == null ? null : theInterpretedGetter.apply(interp);
			}

			@Override
			public MV getFromElement(E element) {
				return theElementGetter == null ? null : theElementGetter.apply(element);
			}
		}

		public static class AddOn<E extends ExElement, AO extends ExAddOn<? super E>, I extends ExAddOn.Interpreted<? super E, ? extends AO>, D extends ExAddOn.Def<? super E, ? extends AO>>
		implements AttributeValueGetter<E, ExElement.Interpreted<? extends E>, ExElement.Def<? extends E>> {
			private final Class<D> theDefType;
			private final Class<I> theInterpretedType;
			private final Class<AO> theAddOnType;

			public AddOn(Class<D> defType, Class<I> interpretedType, Class<AO> addOnType) {
				theDefType = defType;
				theInterpretedType = interpretedType;
				theAddOnType = addOnType;
			}

			@Override
			public D getFromDef(Def<? extends E> def) {
				return def.getAddOn(theDefType);
			}

			@Override
			public I getFromInterpreted(Interpreted<? extends E> interp) {
				return interp.getAddOn(theInterpretedType);
			}

			@Override
			public AO getFromElement(E element) {
				return element.getAddOn(theAddOnType);
			}
		}
	}

	public interface ChildElementGetter<E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> {
		List<? extends ExElement.Def<?>> getChildrenFromDef(D def);

		List<? extends ExElement.Interpreted<?>> getChildrenFromInterpreted(I interp);

		List<? extends ExElement> getChildrenFromElement(E element);

		public static <E extends ExElement, I extends ExElement.Interpreted<? extends E>, D extends ExElement.Def<? extends E>> ChildElementGetter<E, I, D> of(
			Function<D, List<? extends ExElement.Def<?>>> defGetter, Function<I, List<? extends ExElement.Interpreted<?>>> interpGetter,
			Function<E, List<? extends ExElement>> elGetter) {
			return new ChildElementGetter<E, I, D>() {
				@Override
				public List<? extends Def<?>> getChildrenFromDef(D def) {
					return defGetter.apply(def);
				}

				@Override
				public List<? extends Interpreted<?>> getChildrenFromInterpreted(I interp) {
					return interpGetter == null ? Collections.emptyList() : interpGetter.apply(interp);
				}

				@Override
				public List<? extends ExElement> getChildrenFromElement(E element) {
					return elGetter == null ? Collections.emptyList() : elGetter.apply(element);
				}
			};
		}
	}

	/**
	 * The definition of an element, interpreted via {@link QonfigInterpreterCore Qonfig interpretation} from {@link QonfigElement}s
	 *
	 * @param <E> The type of element that this definition is for
	 */
	public interface Def<E extends ExElement> extends Identifiable {
		/** @return The definition interpreted from the parent element */
		Def<?> getParentElement();

		/** @return The QonfigElement that this definition was interpreted from */
		QonfigElement getElement();

		ErrorReporting reporting();

		ExpressoEnv getExpressoEnv();

		/** @return This element's models */
		ObservableModelSet.Built getModels();

		Class<?> getCallingClass();

		Object getAttribute(QonfigAttributeDef attr);

		Object getElementValue();

		List<? extends Def<?>> getChildren(QonfigChildDef role);

		default List<Def<?>> getAllChildren() {
			List<Def<?>> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends Def<?>> roleChildren = getChildren(child);
				if (!roleChildren.isEmpty()) {
					if (children == null)
						children = new ArrayList<>();
					children.addAll(roleChildren);
				}
			}
			for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				for (QonfigChildDef.Declared child : inh.getDeclaredChildren().values()) {
					if (fulfilled == null)
						fulfilled = new HashSet<>();
					if (!fulfilled.add(child))
						continue;
					List<? extends Def<?>> roleChildren = getChildren(child);
					if (!roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children, (c1, c2) -> Integer.compare(c1.reporting().getFileLocation().getPosition(0).getPosition(),
					c2.reporting().getFileLocation().getPosition(0).getPosition()));
			return children == null ? Collections.emptyList() : children;
		}

		Object getAttribute(Interpreted<? extends E> interpreted, QonfigAttributeDef attr);

		Object getElementValue(Interpreted<? extends E> interpreted);

		List<? extends Interpreted<?>> getChildren(Interpreted<? extends E> interpreted, QonfigChildDef role);

		default List<Interpreted<?>> getAllChildren(Interpreted<? extends E> interpreted) {
			List<Interpreted<?>> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends Interpreted<?>> roleChildren = getChildren(interpreted, child);
				if (!roleChildren.isEmpty()) {
					if (children == null)
						children = new ArrayList<>();
					children.addAll(roleChildren);
				}
			}
			for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				for (QonfigChildDef.Declared child : inh.getDeclaredChildren().values()) {
					if (fulfilled == null)
						fulfilled = new HashSet<>();
					if (!fulfilled.add(child))
						continue;
					List<? extends Interpreted<?>> roleChildren = getChildren(interpreted, child);
					if (!roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children,
					(c1, c2) -> Integer.compare(c1.getDefinition().reporting().getFileLocation().getPosition(0).getPosition(),
						c2.getDefinition().reporting().getFileLocation().getPosition(0).getPosition()));
			return children == null ? Collections.emptyList() : children;
		}

		Object getAttribute(E element, QonfigAttributeDef attr);

		Object getElementValue(E element);

		List<? extends ExElement> getChildren(E element, QonfigChildDef role);

		default List<ExElement> getAllChildren(E element) {
			List<ExElement> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends ExElement> roleChildren = getChildren(element, child);
				if (!roleChildren.isEmpty()) {
					if (children == null)
						children = new ArrayList<>();
					children.addAll(roleChildren);
				}
			}
			for (QonfigAddOn inh : getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				for (QonfigChildDef.Declared child : inh.getDeclaredChildren().values()) {
					if (fulfilled == null)
						fulfilled = new HashSet<>();
					if (!fulfilled.add(child))
						continue;
					List<? extends ExElement> roleChildren = getChildren(element, child);
					if (!roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children, (c1, c2) -> Integer.compare(c1.reporting().getFileLocation().getPosition(0).getPosition(),
					c2.reporting().getFileLocation().getPosition(0).getPosition()));
			return children == null ? Collections.emptyList() : children;
		}

		/**
		 * @param <AO> The type of the add-on to get
		 * @param addOn The type of the add-on to get
		 * @return The add-on in this element definition of the given type
		 */
		<AO extends ExAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn);

		/** @return All add-ons on this element definition */
		Collection<ExAddOn.Def<? super E, ?>> getAddOns();

		/**
		 * @param <AO> The type of the add on
		 * @param <T> The type of the value
		 * @param addOn The type of the add-on
		 * @param fn Produces the value from the add-on if it exists
		 * @return The value from the given add on in this element definition, or null if no such add-on is present
		 */
		default <AO extends ExAddOn.Def<? super E, ?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		void forAttribute(QonfigAttributeDef attr, AttributeValueGetter<? super E, ?, ?> getter);

		void forValue(AttributeValueGetter<? super E, ?, ?> getter);

		void forChild(QonfigChildDef child, ChildElementGetter<? super E, ?, ?> getter);

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
		public abstract class Abstract<E extends ExElement> implements Def<E> {
			private final Identity theId;
			private final ExElement.Def<?> theParent;
			private QonfigElement theElement;
			private final Map<QonfigAttributeDef.Declared, AttributeValueGetter<? super E, ?, ?>> theAttributes;
			private AttributeValueGetter<? super E, ?, ?> theValue;
			private final Map<QonfigChildDef.Declared, ChildElementGetter<? super E, ?, ?>> theChildren;
			private final ClassMap<ExAddOn.Def<? super E, ?>> theAddOns;
			private ExpressoEnv theExpressoEnv;
			private ObservableModelSet.Built theModels;
			private ErrorReporting theReporting;
			private Class<?> theCallingClass;

			/**
			 * @param parent The definition interpreted from the parent element
			 * @param element The element that this definition is being interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
				theId = element == null ? null : new Identity(element.getType().getName(), element.getPositionInFile());
				theParent = parent;
				theElement = element;
				theAttributes = new HashMap<>();
				theChildren = new HashMap<>();
				theAddOns = new ClassMap<>();
			}

			@Override
			public Identity getIdentity() {
				return theId;
			}

			@Override
			public ExElement.Def<?> getParentElement() {
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
			public <AO extends ExAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<ExAddOn.Def<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			@Override
			public ExpressoEnv getExpressoEnv() {
				return theExpressoEnv;
			}

			@Override
			public ObservableModelSet.Built getModels() {
				if (theModels == null && theExpressoEnv != null) {
					ObservableModelSet models = theExpressoEnv.getModels();
					theModels = models instanceof ObservableModelSet.Builder ? ((ObservableModelSet.Builder) models).build()
						: (ObservableModelSet.Built) models;
				}
				return theModels;
			}

			@Override
			public Object getAttribute(QonfigAttributeDef attr) {
				AttributeValueGetter<? super E, ?, ?> getter = theAttributes.get(attr.getDeclared());
				if (getter == null)
					return null;
				return ((AttributeValueGetter<? super E, ?, Def<E>>) getter).getFromDef(this);
			}

			@Override
			public Object getElementValue() {
				return theValue == null ? null : ((AttributeValueGetter<? super E, ?, Def<E>>) theValue).getFromDef(this);
			}

			@Override
			public List<? extends Def<?>> getChildren(QonfigChildDef role) {
				ChildElementGetter<? super E, ?, ?> getter = theChildren.get(role.getDeclared());
				if (getter == null)
					return Collections.emptyList();
				return ((ChildElementGetter<? super E, ?, Def<E>>) getter).getChildrenFromDef(this);
			}

			@Override
			public Object getAttribute(Interpreted<? extends E> interpreted, QonfigAttributeDef attr) {
				AttributeValueGetter<? super E, ?, ?> getter = theAttributes.get(attr.getDeclared());
				if (getter == null)
					return null;
				return ((AttributeValueGetter<? super E, Interpreted<? extends E>, ?>) getter).getFromInterpreted(interpreted);
			}

			@Override
			public Object getElementValue(Interpreted<? extends E> interpreted) {
				return theValue == null ? null
					: ((AttributeValueGetter<? super E, Interpreted<? extends E>, ?>) theValue).getFromInterpreted(interpreted);
			}

			@Override
			public List<? extends Interpreted<?>> getChildren(Interpreted<? extends E> interpreted, QonfigChildDef role) {
				ChildElementGetter<? super E, ?, ?> getter = theChildren.get(role.getDeclared());
				if (getter == null)
					return Collections.emptyList();
				return ((ChildElementGetter<? super E, Interpreted<? extends E>, ?>) getter).getChildrenFromInterpreted(interpreted);
			}

			@Override
			public Object getAttribute(E element, QonfigAttributeDef attr) {
				AttributeValueGetter<? super E, ?, ?> getter = theAttributes.get(attr.getDeclared());
				if (getter == null)
					return null;
				return ((AttributeValueGetter<? super E, ?, ?>) getter).getFromElement(element);
			}

			@Override
			public Object getElementValue(E element) {
				return theValue == null ? null : ((AttributeValueGetter<? super E, ?, ?>) theValue).getFromElement(element);
			}

			@Override
			public List<? extends ExElement> getChildren(E element, QonfigChildDef role) {
				ChildElementGetter<? super E, ?, ?> getter = theChildren.get(role.getDeclared());
				if (getter == null)
					return Collections.emptyList();
				return ((ChildElementGetter<? super E, ?, ?>) getter).getChildrenFromElement(element);
			}

			@Override
			public void forAttribute(QonfigAttributeDef attr, AttributeValueGetter<? super E, ?, ?> getter) {
				theAttributes.putIfAbsent(attr.getDeclared(), getter);
			}

			@Override
			public void forValue(AttributeValueGetter<? super E, ?, ?> getter) {
				theValue = getter;
			}

			@Override
			public void forChild(QonfigChildDef child, ChildElementGetter<? super E, ?, ?> getter) {
				theChildren.putIfAbsent(child.getDeclared(), getter);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				theReporting = session.reporting();
				theCallingClass = session.getWrapped().getInterpreter().getCallingClass();
				session.setElementRepresentation(this);
				theElement = session.getElement();
				theExpressoEnv = session.getExpressoEnv();
				theModels = null; // Build this on demand, as it may still need to be built

				if (theAddOns.isEmpty()) {
					// Add-ons can't change, because if they do, the element definition should be re-interpreted from the session
					Set<QonfigElementOrAddOn> addOnsTested = new HashSet<>();
					for (QonfigAddOn addOn : session.getElement().getInheritance().values())
						addAddOn(session, addOn, addOnsTested);
					addAddOns(session, session.getElement().getType(), addOnsTested);

					for (ExAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.update(session.asElement(addOn.getType()), this);

					// Ensure implementation added all attribute/child getters
					if (theValue == null && theElement.getType().getValue() != null)
						theReporting.warn(getClass() + ": Value getter not declared for " + theElement.getType().getValue().getOwner());
					for (QonfigAttributeDef.Declared attr : session.getElement().getType().getAllAttributes().keySet()) {
						if (!theAttributes.containsKey(attr))
							theReporting.warn(getClass() + ": Attribute getter not declared for " + attr);
					}
					for (QonfigChildDef.Declared child : session.getElement().getType().getAllChildren().keySet()) {
						if (!theChildren.containsKey(child))
							theReporting.warn(getClass() + ": Child getter not declared for " + child);
					}
					for (QonfigAddOn addOn : session.getElement().getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
						for (QonfigAttributeDef.Declared attr : addOn.getDeclaredAttributes().values()) {
							if (!theAttributes.containsKey(attr))
								theReporting.warn(getClass() + ": Attribute getter not declared for " + attr);
						}
						for (QonfigChildDef.Declared child : addOn.getDeclaredChildren().values()) {
							if (!theChildren.containsKey(child))
								theReporting.warn(getClass() + ": Child getter not declared for " + child);
						}
					}
				}
			}

			private void addAddOns(AbstractQIS<?> session, QonfigElementDef element, Set<QonfigElementOrAddOn> tested)
				throws QonfigInterpretationException {
				if (!tested.add(element))
					return;
				for (QonfigAddOn addOn : element.getFullInheritance().values())
					addAddOn(session, addOn, tested);
				if (element.getSuperElement() != null)
					addAddOns(session, element.getSuperElement(), tested);
			}

			private void addAddOn(AbstractQIS<?> session, QonfigAddOn addOn, Set<QonfigElementOrAddOn> tested)
				throws QonfigInterpretationException {
				if (!tested.add(addOn))
					return;
				session = session.asElementOnly(addOn);
				Class<?> addOnType = session.getInterpretationSupport(ExAddOn.Def.class);
				if (addOnType != null && theAddOns.get(addOnType, ClassMap.TypeMatch.SUB_TYPE) == null) {
					ExAddOn.Def<? super E, ?> exAddOn = session.interpret(ExAddOn.Def.class);
					theAddOns.put(exAddOn.getClass(), exAddOn);
				}
				if (addOn.getSuperElement() != null)
					addAddOns(session, addOn.getSuperElement(), tested);
				for (QonfigAddOn inh : session.getFocusType().getInheritance())
					addAddOn(session, inh, tested);
			}

			@Override
			public String toString() {
				return theElement == null ? super.toString() : theElement.toString();
			}
		}
	}

	public static void checkElement(QonfigElementOrAddOn element, String tkName, Version tkVersion, String elementName) {
		if (!element.getDeclarer().getName().equals(tkName) || element.getDeclarer().getMajorVersion() != tkVersion.major
			|| element.getDeclarer().getMinorVersion() != tkVersion.minor || !element.getName().equals(elementName))
			throw new IllegalStateException("This class is designed against " + tkName + " v" + tkVersion.major + "." + tkVersion.minor
				+ " " + elementName + ", not " + element.getDeclarer().getName() + " v" + element.getDeclarer().getMajorVersion() + "."
				+ element.getDeclarer().getMinorVersion() + " " + element.getName());
	}

	/**
	 * Produced from a {@link Def}. This object may contain more definite information that is present in its {@link #getDefinition()
	 * definition}, especially information about types and links between model values.
	 *
	 * @param <E> The type of element that this interpretation is for
	 */
	public interface Interpreted<E extends ExElement> {
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
		<AO extends ExAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn);

		/** @return All add-ons on this element definition */
		Collection<ExAddOn.Interpreted<? super E, ?>> getAddOns();

		/**
		 * @param <AO> The type of the add on
		 * @param <T> The type of the value
		 * @param addOn The type of the add-on
		 * @param fn Produces the value from the add on if it exists
		 * @return The value from the given add on in this element definition, or null if no such add-on is present
		 */
		default <AO extends ExAddOn.Interpreted<? super E, ?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		boolean isModelInstancePersistent();

		Interpreted<E> persistModelInstances(boolean persist);

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
		public abstract class Abstract<E extends ExElement> implements Interpreted<E> {
			private final Def<? super E> theDefinition;
			private final Interpreted<?> theParent;
			private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theAddOns;
			private final SettableValue<Boolean> isDestroyed;
			private InterpretedModelSet theModels;
			private Boolean isModelInstancePersistent;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			protected Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = definition;
				theParent = parent;
				theAddOns = new ClassMap<>();
				for (ExAddOn.Def<? super E, ?> addOn : definition.getAddOns()) {
					ExAddOn.Interpreted<? super E, ?> interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
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
			public <AO extends ExAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<ExAddOn.Interpreted<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			@Override
			public boolean isModelInstancePersistent() {
				if (isModelInstancePersistent != null)
					return isModelInstancePersistent.booleanValue();
				else if (theParent != null)
					return theParent.isModelInstancePersistent();
				else
					return false;
			}

			@Override
			public Interpreted<E> persistModelInstances(boolean persist) {
				isModelInstancePersistent = persist;
				return this;
			}

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return isDestroyed.unsettable();
			}

			@Override
			public void destroy() {
				for (ExAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
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
				for (ExAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.update(theModels);
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}
	}

	/** @return The parent element */
	ExElement getParentElement();

	/**
	 * @param <AO> The type of the add-on to get
	 * @param addOn The type of the add-on to get
	 * @return The add-on in this element definition of the given type
	 */
	<AO extends ExAddOn<?>> AO getAddOn(Class<AO> addOn);

	/** @return All add-ons on this element definition */
	Collection<ExAddOn<?>> getAddOns();

	/**
	 * @param <AO> The type of the add on
	 * @param <T> The type of the value
	 * @param addOn The type of the add-on
	 * @param fn Produces the value from the add-on if it exists
	 * @return The value from the given add on in this element definition, or null if no such add-on is present
	 */
	default <AO extends ExAddOn<?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
		AO ao = getAddOn(addOn);
		return ao == null ? null : fn.apply(ao);
	}

	/**
	 * <p>
	 * Retrieves the model instance by which this element is populated with expression values.
	 * </p>
	 * <p>
	 * This method is generally only applicable while it is {@link #update(Interpreted, ModelSetInstance) updating}. The model structures
	 * are typically discarded outside this phase to free up memory.
	 * </p>
	 * <p>
	 * The exception to this is when the {@link Interpreted#isModelInstancePersistent()} flag is set on the interpreter passed to the
	 * {@link #update(Interpreted, ModelSetInstance) update} method. In this case the models are persisted in the element and available any
	 * time
	 * </p>
	 *
	 * @return The model instance used by this element to build its expression values
	 * @throws IllegalStateException If this element is not {@link #update(Interpreted, ModelSetInstance) updating} and its interpretation's
	 *         {@link Interpreted#isModelInstancePersistent()} flag was not set
	 */
	ModelSetInstance getUpdatingModels() throws IllegalStateException;

	ErrorReporting reporting();

	/**
	 * Updates this element. Must be called at least once after being produced by its interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param models The model instance for this element
	 * @throws ModelInstantiationException If an error occurs instantiating any model values needed by this element or its content
	 */
	ModelSetInstance update(Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException;

	/** Abstract {@link ExElement} implementation */
	public abstract class Abstract implements ExElement {
		private final Object theId;
		private final ExElement theParent;
		private final ClassMap<ExAddOn<?>> theAddOns;
		private final ClassMap<Class<? extends ExAddOn.Interpreted<?, ?>>> theAddOnInterpretations;
		private final ErrorReporting theReporting;
		private ModelSetInstance theUpdatingModels;

		/**
		 * @param interpreted The interpretation producing this element
		 * @param parent The parent element
		 */
		protected Abstract(Interpreted<?> interpreted, ExElement parent) {
			theId = interpreted.getDefinition().getIdentity();
			theParent = parent;
			theAddOns = new ClassMap<>();
			theAddOnInterpretations = new ClassMap<>();
			for (ExAddOn.Interpreted<?, ?> addOn : interpreted.getAddOns()) {
				ExAddOn<?> inst = ((ExAddOn.Interpreted<ExElement, ?>) addOn).create(this);
				theAddOns.put(inst.getClass(), inst);
				theAddOnInterpretations.put(inst.getClass(), (Class<? extends ExAddOn.Interpreted<?, ?>>) addOn.getClass());
			}
			theReporting = interpreted.getDefinition().reporting();
		}

		@Override
		public Object getIdentity() {
			return theId;
		}

		@Override
		public ExElement getParentElement() {
			return theParent;
		}

		@Override
		public <AO extends ExAddOn<?>> AO getAddOn(Class<AO> addOn) {
			return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
		}

		@Override
		public Collection<ExAddOn<?>> getAddOns() {
			return theAddOns.getAllValues();
		}

		@Override
		public ModelSetInstance getUpdatingModels() {
			return theUpdatingModels;
		}

		@Override
		public ErrorReporting reporting() {
			return theReporting;
		}

		@Override
		public ModelSetInstance update(Interpreted<?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = createElementModel(interpreted, models);
			try {
				updateModel(interpreted, myModels);
			} finally {
				if (!interpreted.isModelInstancePersistent())
					theUpdatingModels = null;
			}
			return myModels;
		}

		protected ModelSetInstance createElementModel(Interpreted<?> interpreted, ModelSetInstance parentModels)
			throws ModelInstantiationException {
			theUpdatingModels = interpreted.getDefinition().getExpressoEnv().wrapLocal(parentModels);
			return theUpdatingModels;
		}

		protected void updateModel(Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			for (ExAddOn<?> addOn : theAddOns.getAllValues()) {
				Class<? extends ExAddOn.Interpreted<ExElement, ?>> addOnInterpType;
				addOnInterpType = (Class<? extends ExAddOn.Interpreted<ExElement, ?>>) theAddOnInterpretations.get(addOn.getClass(),
					ClassMap.TypeMatch.EXACT);
				ExAddOn.Interpreted<?, ?> interpretedAddOn = interpreted.getAddOn(addOnInterpType);
				((ExAddOn<ExElement>) addOn).update(interpretedAddOn, myModels);
			}
		}

		@Override
		public String toString() {
			return theId.toString();
		}

		protected <M, MV extends M> void satisfyContextValue(String valueName, ModelInstanceType<M, MV> type, MV value,
			ModelSetInstance models) throws ModelInstantiationException {
			ExElement.satisfyContextValue(valueName, type, value, models, this);
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
		ModelSetInstance models, ExElement element) throws ModelInstantiationException {
		if (value != null) {
			try {
				DynamicModelValue.satisfyDynamicValue(valueName, type, models, value);
			} catch (ModelException e) {
				throw new ModelInstantiationException("No " + valueName + " value?", element.reporting().getFileLocation().getPosition(0),
					0, e);
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
	public static <D extends ExElement.Def<?>> D useOrReplace(Class<? extends D> type, D def, ExpressoQIS session, String childName)
		throws QonfigInterpretationException, IllegalArgumentException {
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

	public static <T extends ExElement.Def<?>> void syncDefs(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions)
		throws QonfigInterpretationException {
		CollectionUtils.synchronize((List<T>) defs, sessions, //
			(widget, child) -> ExElement.typesEqual(widget.getElement(), child.getElement()))//
		.simpleE(child -> child.interpret(defType))//
		.rightOrder()//
		.onRightX(element -> element.getLeftValue().update(element.getRightValue()))//
		.onCommonX(element -> element.getLeftValue().update(element.getRightValue()))//
		.adjust();
	}
}

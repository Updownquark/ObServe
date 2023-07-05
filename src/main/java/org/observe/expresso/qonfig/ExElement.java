package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.TypeConversionException;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.*;
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

		Object getAttribute(QonfigAttributeDef attr);

		Object getElementValue();

		List<? extends Def<?>> getDefChildren(QonfigChildDef child);

		default List<Def<?>> getAllDefChildren() {
			List<Def<?>> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends Def<?>> roleChildren = getDefChildren(child);
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
					List<? extends Def<?>> roleChildren = getDefChildren(child);
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

		List<? extends Interpreted<?>> getInterpretedChildren(Interpreted<? extends E> interpreted, QonfigChildDef child);

		default List<Interpreted<?>> getAllInterpretedChildren(Interpreted<? extends E> interpreted) {
			List<Interpreted<?>> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends Interpreted<?>> roleChildren = getInterpretedChildren(interpreted, child);
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
					List<? extends Interpreted<?>> roleChildren = getInterpretedChildren(interpreted, child);
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

		List<? extends ExElement> getElementChildren(E element, QonfigChildDef child);

		default List<ExElement> getAllElementChildren(E element) {
			List<ExElement> children = null;
			Set<QonfigChildDef.Declared> fulfilled = null;
			for (QonfigChildDef.Declared child : getElement().getType().getAllChildren().keySet()) {
				if (fulfilled == null)
					fulfilled = new HashSet<>();
				if (!fulfilled.add(child))
					continue;
				List<? extends ExElement> roleChildren = getElementChildren(element, child);
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
					List<? extends ExElement> roleChildren = getElementChildren(element, child);
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

		ExElement.Def<?> withTraceability(ElementTypeTraceability<? super E, ?, ?> traceability);

		/**
		 * Updates this element definition. Must be called at least once after interpretation produces this object.
		 *
		 * @param session The session supporting this element definition
		 * @throws QonfigInterpretationException If an error occurs interpreting some of this element's fields or content
		 */
		void update(ExpressoQIS session) throws QonfigInterpretationException;

		public static class QonfigElementKey {
			public String toolkitName;
			public int toolkitMajorVersion;
			public int toolkitMinorVersion;
			public String typeName;

			public QonfigElementKey(String toolkitName, int toolkitMajorVersion, int toolkitMinorVersion, String typeName) {
				this.toolkitName = toolkitName;
				this.toolkitMinorVersion = toolkitMinorVersion;
				this.toolkitMajorVersion = toolkitMajorVersion;
				this.typeName = typeName;
			}

			public QonfigElementKey(ElementTypeTraceability<?, ?, ?> traceability) {
				toolkitName = traceability.getToolkitName();
				toolkitMajorVersion = traceability.getToolkitVersion().majorVersion;
				toolkitMinorVersion = traceability.getToolkitVersion().minorVersion;
				typeName = traceability.getTypeName();
			}

			public QonfigElementKey(QonfigElementOrAddOn type) {
				toolkitName = type.getDeclarer().getName();
				toolkitMajorVersion = type.getDeclarer().getMajorVersion();
				toolkitMinorVersion = type.getDeclarer().getMinorVersion();
				typeName = type.getName();
			}

			@Override
			public int hashCode() {
				return Objects.hash(toolkitName, toolkitMajorVersion, toolkitMinorVersion, typeName);
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == this)
					return true;
				else if (!(obj instanceof QonfigElementKey))
					return false;
				QonfigElementKey other = (QonfigElementKey) obj;
				return toolkitName.equals(other.toolkitName) //
					&& toolkitMajorVersion == other.toolkitMajorVersion && toolkitMinorVersion == other.toolkitMinorVersion//
					&& typeName.equals(other.typeName);
			}

			@Override
			public String toString() {
				return toolkitName + " v" + toolkitMajorVersion + "." + toolkitMinorVersion + "." + typeName;
			}
		}

		/**
		 * An abstract implementation of {@link Def}
		 *
		 * @param <E> The type of the element that this definition is for
		 */
		public abstract class Abstract<E extends ExElement> implements Def<E> {
			private final Identity theId;
			private final ExElement.Def<?> theParent;
			private QonfigElement theElement;
			private final ClassMap<ExAddOn.Def<? super E, ?>> theAddOns;
			private final Map<QonfigElementKey, ElementTypeTraceability<? super E, ?, ?>> theTraceability;
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
				theAddOns = new ClassMap<>();
				theTraceability = new HashMap<>();
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
				QonfigElementOrAddOn owner = attr.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getDefAttribute(this, attr.getName());
			}

			@Override
			public Object getElementValue() {
				QonfigValueDef def = theElement.getType().getValue();
				if (def == null)
					return null;
				QonfigElementOrAddOn owner = def.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getDefElementValue(this);
			}

			@Override
			public List<? extends Def<?>> getDefChildren(QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getDefChildren(this, role.getName());
			}

			@Override
			public Object getAttribute(Interpreted<? extends E> interpreted, QonfigAttributeDef attr) {
				QonfigElementOrAddOn owner = attr.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getInterpretedAttribute(interpreted, attr.getName());
			}

			@Override
			public Object getElementValue(Interpreted<? extends E> interpreted) {
				QonfigValueDef def = theElement.getType().getValue();
				if (def == null)
					return null;
				QonfigElementOrAddOn owner = def.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getInterpretedElementValue(interpreted);
			}

			@Override
			public List<? extends Interpreted<?>> getInterpretedChildren(Interpreted<? extends E> interpreted, QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getInterpretedChildren(interpreted, role.getName());
			}

			@Override
			public List<? extends ExElement> getElementChildren(E element, QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new QonfigElementKey(declarer.getName(), declarer.getMajorVersion(), declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getElementChildren(element, role.getName());
			}

			@Override
			public ExElement.Def<?> withTraceability(ElementTypeTraceability<? super E, ?, ?> traceability) {
				if (theTraceability.putIfAbsent(new QonfigElementKey(traceability), traceability) != null)
					throw new IllegalArgumentException("Traceability has already been configured for " + traceability);
				return this;
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

					// Ensure implementation added all traceability
					checkTraceability(theElement.getType());
					for (QonfigAddOn inh : theElement.getInheritance().values())
						checkTraceability(inh);
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

			private void checkTraceability(QonfigElementOrAddOn type) {
				QonfigElementKey key = new QonfigElementKey(type);
				if (!theTraceability.containsKey(key)) {
					// Only warn about types that need any traceability
					if (!type.getDeclaredAttributes().isEmpty() //
						|| (type.getValue() != null && type.getValue().getOwner() == type)//
						|| !type.getDeclaredChildren().isEmpty())
						theReporting.warn(getClass() + ": Traceability not configured for " + key);
				} else {
					if (type.getSuperElement() != null)
						checkTraceability(type.getSuperElement());
					for (QonfigAddOn inh : type.getInheritance())
						checkTraceability(inh);
				}
			}

			@Override
			public String toString() {
				return theElement == null ? super.toString() : theElement.toString();
			}
		}
	}

	/**
	 * Produced from a {@link Def}. This object may contain more definite information that is present in its {@link #getDefinition()
	 * definition}, especially information about types and links between model values.
	 *
	 * @param <E> The type of element that this interpretation is for
	 */
	public interface Interpreted<E extends ExElement> extends Identifiable {
		/** @return The definition that produced this interpretation */
		Def<? super E> getDefinition();

		@Override
		default Object getIdentity() {
			return getDefinition().getIdentity();
		}

		/** @return The interpretation of the parent element */
		Interpreted<?> getParentElement();

		default ErrorReporting reporting() {
			return getDefinition().reporting();
		}

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
			public ErrorReporting reporting() {
				return theDefinition.reporting();
			}

			@Override
			public Object getIdentity() {
				return theDefinition.getIdentity();
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

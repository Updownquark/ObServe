package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ListenerList;
import org.qommons.config.*;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExConsumer;
import org.qommons.io.ErrorReporting;

/** A base type for values interpreted from {@link QonfigElement}s */
public interface ExElement extends Identifiable {
	public static class ElementIdentity {
		private String theStringRep;

		public void setStringRepresentation(String stringRep) {
			theStringRep = stringRep;
		}

		@Override
		public String toString() {
			return theStringRep;
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

		QonfigElementOrAddOn getQonfigType();

		/** @return The QonfigElement that this definition was interpreted from */
		QonfigElement getElement();

		ErrorReporting reporting();

		CompiledExpressoEnv getExpressoEnv();

		void setExpressoEnv(CompiledExpressoEnv env);

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
				if (roleChildren != null && !roleChildren.isEmpty()) {
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
					if (roleChildren != null && !roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children,
					(c1, c2) -> Integer.compare(c1.reporting().getPosition().getPosition(), c2.reporting().getPosition().getPosition()));
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
				if (roleChildren != null && !roleChildren.isEmpty()) {
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
					if (roleChildren != null && !roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children,
					(c1, c2) -> Integer.compare(c1.reporting().getPosition().getPosition(), c2.reporting().getPosition().getPosition()));
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
				if (roleChildren != null && !roleChildren.isEmpty()) {
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
					if (roleChildren != null && !roleChildren.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.addAll(roleChildren);
					}
				}
			}
			if (children != null)
				Collections.sort(children,
					(c1, c2) -> Integer.compare(c1.reporting().getPosition().getPosition(), c2.reporting().getPosition().getPosition()));
			return children == null ? Collections.emptyList() : children;
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
		public abstract class Abstract<E extends ExElement> implements Def<E> {
			private final ElementIdentity theId;
			private ExElement.Def<?> theParent;
			private final QonfigElementOrAddOn theQonfigType;
			private QonfigElement theElement;
			private final ClassMap<ExAddOn.Def<? super E, ?>> theAddOns;
			private Map<ElementTypeTraceability.QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> theTraceability;
			private CompiledExpressoEnv theExpressoEnv;
			private ErrorReporting theReporting;

			/**
			 * @param parent The definition interpreted from the parent element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				theId = new ElementIdentity();
				theId.setStringRepresentation(qonfigType.getName());
				theParent = parent;
				theQonfigType = qonfigType;
				theAddOns = new ClassMap<>();
			}

			@Override
			public Object getIdentity() {
				return theId;
			}

			@Override
			public ExElement.Def<?> getParentElement() {
				return theParent;
			}

			protected void setParentElement(ExElement.Def<?> parent) {
				if (theParent != null) {
					if (parent != theParent)
						throw new IllegalArgumentException("Parent has already been set");
					return;
				}
				theParent = parent;
			}

			@Override
			public QonfigElementOrAddOn getQonfigType() {
				return theQonfigType;
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
			public <AO extends ExAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<ExAddOn.Def<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			@Override
			public CompiledExpressoEnv getExpressoEnv() {
				return theExpressoEnv;
			}

			@Override
			public Object getAttribute(QonfigAttributeDef attr) {
				QonfigElementOrAddOn owner = attr.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getDefAttribute(this, attr);
			}

			@Override
			public Object getElementValue() {
				QonfigValueDef def = theElement.getType().getValue();
				if (def == null)
					return null;
				QonfigElementOrAddOn owner = def.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getDefElementValue(this);
			}

			@Override
			public List<? extends Def<?>> getDefChildren(QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getDefChildren(this, role);
			}

			@Override
			public Object getAttribute(Interpreted<? extends E> interpreted, QonfigAttributeDef attr) {
				QonfigElementOrAddOn owner = attr.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getInterpretedAttribute(interpreted, attr);
			}

			@Override
			public Object getElementValue(Interpreted<? extends E> interpreted) {
				QonfigValueDef def = theElement.getType().getValue();
				if (def == null)
					return null;
				QonfigElementOrAddOn owner = def.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return null;
				return traceability.getInterpretedElementValue(interpreted);
			}

			@Override
			public List<? extends Interpreted<?>> getInterpretedChildren(Interpreted<? extends E> interpreted, QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getInterpretedChildren(interpreted, role);
			}

			@Override
			public List<? extends ExElement> getElementChildren(E element, QonfigChildDef role) {
				QonfigElementOrAddOn owner = role.getDeclared().getOwner();
				QonfigToolkit declarer = owner.getDeclarer();
				ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>> traceability = (ElementTypeTraceability<E, Interpreted<? extends E>, Def<? extends E>>) theTraceability
					.get(new ElementTypeTraceability.QonfigElementKey(declarer.getName(), declarer.getMajorVersion(),
						declarer.getMinorVersion(), owner.getName()));
				if (traceability == null)
					return Collections.emptyList();
				return traceability.getElementChildren(element, role);
			}

			@Override
			public final void update(ExpressoQIS session) throws QonfigInterpretationException {
				if (session.getFocusType() != theQonfigType)
					session = session.asElement(theQonfigType);
				theId.setStringRepresentation(theQonfigType.getName() + "@" + session.getElement().getPositionInFile().toShortString());

				theElement = session.getElement();
				theReporting = session.reporting();
				boolean firstTime = theTraceability == null;
				if (firstTime) {
					theTraceability = new LinkedHashMap<>(ElementTypeTraceability.SingleTypeTraceability.traceabilityFor(getClass(),
						theElement.getDocument().getDocToolkit(), theReporting));
				}
				session.setElementRepresentation(this);
				if (theParent != null) { // Check that the parent configured is actually the parent element
					if (theElement.getDocument() instanceof QonfigMetadata) {
						if (!theParent.getElement().isInstance(((QonfigMetadata) theElement.getDocument()).getElement()))
							throw new IllegalArgumentException(theParent + " is not the parent of " + this);
					} else if (theParent.getElement() != theElement.getParent())
						throw new IllegalArgumentException(theParent + " is not the parent of " + this);
				}
				setExpressoEnv(session.getExpressoEnv());

				if (firstTime) {
					// Add-ons can't change, because if they do, the element definition should be re-interpreted from the session
					Set<QonfigElementOrAddOn> addOnsTested = new HashSet<>();
					for (QonfigAddOn addOn : session.getElement().getInheritance().values())
						addAddOn(session, addOn, addOnsTested);
					addAddOns(session, session.getElement().getType(), addOnsTested);
				}

				try {
					for (ExAddOn.Def<?, ?> addOn : theAddOns.getAllValues())
						addOn.preUpdate(session.asElement(addOn.getType()));

					doUpdate(session);

					for (ExAddOn.Def<?, ?> addOn : theAddOns.getAllValues())
						addOn.postUpdate(session.asElement(addOn.getType()));
				} catch (RuntimeException | Error e) {
					reporting().error(e.getMessage(), e);
				}

				if (firstTime) {
					// Ensure implementation added all traceability
					checkTraceability(theElement.getType());
					for (QonfigAddOn inh : theElement.getInheritance().values())
						checkTraceability(inh);
				}
			}

			@Override
			public void setExpressoEnv(CompiledExpressoEnv env) {
				theExpressoEnv = env;
			}

			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				for (ExAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
					addOn.update(session.asElement(addOn.getType()), this);
			}

			private void addAddOns(AbstractQIS<?> session, QonfigElementDef element, Set<QonfigElementOrAddOn> tested)
				throws QonfigInterpretationException {
				if (!tested.add(element))
					return;
				for (QonfigAddOn addOn : element.getInheritance())
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
					SingleTypeTraceability.join(theTraceability,
						SingleTypeTraceability.traceabilityFor(exAddOn.getClass(), theElement.getDocument().getDocToolkit(), theReporting));
					theAddOns.put(exAddOn.getClass(), exAddOn);
				}
				if (addOn.getSuperElement() != null)
					addAddOns(session, addOn.getSuperElement(), tested);
				for (QonfigAddOn inh : addOn.getInheritance())
					addAddOn(session, inh, tested);
			}

			private void checkTraceability(QonfigElementOrAddOn type) {
				ElementTypeTraceability.QonfigElementKey key = new ElementTypeTraceability.QonfigElementKey(type);
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
				return theId.toString();
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

		InterpretedExpressoEnv getExpressoEnv();

		void setExpressoEnv(InterpretedExpressoEnv env);

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

		Runnable onInstantiation(ExConsumer<? super E, ModelInstantiationException> task);

		ObservableValue<Boolean> isDestroyed();

		default Observable<ObservableValueEvent<Boolean>> destroyed() {
			return isDestroyed().changes().filterP(evt -> Boolean.TRUE.equals(evt.getNewValue())).take(1);
		}

		void destroy();

		/**
		 * An abstract implementation of {@link Interpreted}
		 *
		 * @param <E> The type of element that this interpretation is for
		 */
		public abstract class Abstract<E extends ExElement> implements Interpreted<E> {
			private final Def<? super E> theDefinition;
			private Interpreted<?> theParent;
			private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theAddOns;
			private final SettableValue<Boolean> isDestroyed;
			private InterpretedExpressoEnv theExpressoEnv;
			private Boolean isModelInstancePersistent;
			private boolean isInterpreting;
			private ListenerList<ExConsumer<? super E, ModelInstantiationException>> theOnInstantiations;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			protected Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = definition;
				if (parent != null)
					setParentElement(parent);
				theAddOns = new ClassMap<>();
				for (ExAddOn.Def<? super E, ?> addOn : definition.getAddOns()) {
					ExAddOn.Interpreted<? super E, ?> interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					if (interp != null) // It is allowed for add-on definitions not to produce interpretations
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

			protected Abstract<E> setParentElement(Interpreted<?> parent) {
				if ((parent == null ? null : parent.getDefinition()) != theDefinition.getParentElement())
					throw new IllegalArgumentException(parent + " is not the parent of " + this);
				theParent = parent;
				return this;
			}

			@Override
			public InterpretedModelSet getModels() {
				return theExpressoEnv.getModels();
			}

			@Override
			public InterpretedExpressoEnv getExpressoEnv() {
				return theExpressoEnv;
			}

			@Override
			public void setExpressoEnv(InterpretedExpressoEnv env) {
				theExpressoEnv = env;
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
			public Runnable onInstantiation(ExConsumer<? super E, ModelInstantiationException> task) {
				if (theOnInstantiations == null)
					theOnInstantiations = ListenerList.build().build();
				return theOnInstantiations.add(task, false);
			}

			protected void instantiated(E element) {
				if (theOnInstantiations != null)
					theOnInstantiations.forEach(l -> {
						try {
							l.accept(element);
						} catch (ModelInstantiationException e) {
							e.printStackTrace();
						}
					});
			}

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return isDestroyed.unsettable();
			}

			@Override
			public void destroy() {
				for (ExElement.Interpreted<?> child : getDefinition().getAllInterpretedChildren(this))
					child.destroy();
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
			protected final void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (isInterpreting)
					return;
				isInterpreting = true;
				try {
					for (ExAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
						addOn.preUpdate();
					theExpressoEnv = env.forChild(theDefinition.getExpressoEnv());
					doUpdate(theExpressoEnv);
					// If our models are the same as the parent, then they're already interpreted or interpreting
					// Can't always rely on having our parent correct, but we can tell from the definition
					boolean hasUniqueModels = getDefinition().getParentElement() == null
						|| getDefinition().getExpressoEnv().getModels() != getDefinition().getParentElement().getExpressoEnv().getModels();
					if (hasUniqueModels)
						theExpressoEnv.getModels().interpret(theExpressoEnv); // Interpret any remaining values
					for (ExAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
						addOn.postUpdate();
					postUpdate();
				} catch (RuntimeException | Error e) {
					reporting().error(e.getMessage(), e);
				} finally {
					isInterpreting = false;
				}
			}

			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				theExpressoEnv = expressoEnv;
				for (ExAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.update(theExpressoEnv);
			}

			protected void postUpdate() throws ExpressoInterpretationException {
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}
	}

	String getTypeName();

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

	/** @return The instantiator for this element's models */
	ModelInstantiator getModels();

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
	 * Instantiates model value instantiators in this element this element. Must be called at least once after being produced by its
	 * interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param models The model instance for this element
	 */
	void update(Interpreted<?> interpreted, ExElement parent);

	void instantiated();

	/**
	 * Instantiates all model values in this element this element. Must be called at least once after being produced by its interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param models The model instance for this element
	 * @return The models applicable to this element
	 * @throws ModelInstantiationException If an error occurs instantiating any model values needed by this element or its content
	 */
	ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException;

	ExElement copy(ExElement parent);

	ObservableValue<Boolean> isDestroyed();

	void destroy();

	default Observable<ObservableValueEvent<Boolean>> onDestroy() {
		return isDestroyed().noInitChanges().filter(evt -> evt.getNewValue()).take(1);
	}

	/** Abstract {@link ExElement} implementation */
	public abstract class Abstract implements ExElement, Cloneable {
		private final Object theId;
		private ExElement theParent;
		private ModelInstantiator theLocalModel;
		private boolean isModelPersistent;
		private ClassMap<ExAddOn<?>> theAddOns;
		private ErrorReporting theReporting;
		private String theTypeName;
		private SettableValue<Boolean> isDestroyed;
		private ModelSetInstance theUpdatingModels;

		/** @param id The identification for this element */
		protected Abstract(Object id) {
			if (id == null)
				throw new NullPointerException();
			theId = id;
			theAddOns = new ClassMap<>();
			isDestroyed = SettableValue.build(boolean.class).withValue(false).build();
		}

		@Override
		public String getTypeName() {
			return theTypeName;
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
		public ModelInstantiator getModels() {
			return theLocalModel;
		}

		@Override
		public ModelSetInstance getUpdatingModels() {
			return theUpdatingModels;
		}

		@Override
		public ErrorReporting reporting() {
			return theReporting;
		}

		protected void persistModels() {
			isModelPersistent = true;
		}

		@Override
		public final void update(Interpreted<?> interpreted, ExElement parent) {
			if (theId != interpreted.getIdentity())
				throw new IllegalArgumentException("Wrong interpretation: " + interpreted + " for " + this);
			if (interpreted instanceof Interpreted.Abstract)
				((Interpreted.Abstract<ExElement>) interpreted).instantiated(this);
			theReporting = interpreted.reporting();
			theParent = parent;
			theTypeName = interpreted.getDefinition().getElement().getType().getName();

			// Create add-ons
			CollectionUtils
			.synchronize(new ArrayList<>(theAddOns.getAllValues()), new ArrayList<>(interpreted.getAddOns()),
				(inst, interp) -> inst.getInterpretationType() == interp.getClass())//
			.adjust(new CollectionUtils.CollectionSynchronizer<ExAddOn<?>, ExAddOn.Interpreted<?, ?>>() {
				@Override
				public boolean getOrder(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					return true;
				}

				@Override
				public ElementSyncAction leftOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					theAddOns.compute(element.getLeftValue().getClass(), __ -> null);
					return element.remove();
				}

				@Override
				public ElementSyncAction rightOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					ExAddOn<?> instance = ((ExAddOn.Interpreted<ExElement, ?>) element.getRightValue()).create(ExElement.Abstract.this);
					if (instance != null) {
						theAddOns.put(instance.getClass(), instance);
						return element.useValue(instance);
					} else
						return element.preserve();
				}

				@Override
				public ElementSyncAction common(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					return element.preserve();
				}
			}, CollectionUtils.AdjustmentOrder.RightOrder);

			if (interpreted.getParentElement() == null || interpreted.getExpressoEnv().getModels().getIdentity() != interpreted
				.getParentElement().getExpressoEnv().getModels().getIdentity())
				theLocalModel = interpreted.getExpressoEnv().getModels().instantiate();
			else
				theLocalModel = null;
			isModelPersistent = interpreted.isModelInstancePersistent();
			try {
				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					addOn.preUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()));

				doUpdate(interpreted);

				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					addOn.postUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()));
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
		}

		protected void doUpdate(Interpreted<?> interpreted) {
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				addOn.update(interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()));
		}

		@Override
		public void instantiated() {
			if (theLocalModel != null)
				theLocalModel.instantiate();
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				addOn.instantiated();
		}

		@Override
		public final ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = null;
			try {
				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					addOn.preInstantiate();

				myModels = createElementModel(models);
				try {
					doInstantiate(myModels);

					for (ExAddOn<?> addOn : theAddOns.getAllValues())
						addOn.postInstantiate(myModels);
				} finally {
					if (!isModelPersistent)
						theUpdatingModels = null;
				}
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
			return myModels;
		}

		protected ModelSetInstance createElementModel(ModelSetInstance parentModels) throws ModelInstantiationException {
			if (theLocalModel != null)
				theUpdatingModels = theLocalModel.wrap(parentModels);
			else
				theUpdatingModels = parentModels;
			return theUpdatingModels;
		}

		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theUpdatingModels = myModels;
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				addOn.instantiate(myModels);
		}

		@Override
		public Abstract copy(ExElement parent) {
			Abstract copy = clone();
			copy.theParent = parent;
			copy.theAddOns = new ClassMap<>();
			copy.isDestroyed = SettableValue.build(boolean.class).withValue(false).build();
			for (ExAddOn<?> addOn : theAddOns.getAllValues()) {
				ExAddOn<?> addOnCopy = ((ExAddOn<ExElement>) addOn).copy(parent);
				copy.theAddOns.put(addOnCopy.getClass(), addOnCopy);
			}
			return copy;
		}

		@Override
		protected Abstract clone() {
			try {
				return (Abstract) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException("Not cloneable?", e);
			}
		}

		@Override
		public ObservableValue<Boolean> isDestroyed() {
			return isDestroyed.unsettable();
		}

		@Override
		public void destroy() {
			isDestroyed.set(true, null);
		}

		@Override
		public String toString() {
			return theId.toString();
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
	public static <D extends ExElement.Def<?>> D useOrReplace(Class<? extends D> type, D def, ExpressoQIS session, String childName)
		throws QonfigInterpretationException, IllegalArgumentException {
		ExpressoQIS childSession = childName == null ? session : session.forChildren(childName).peekFirst();
		if (childSession == null)
			return null;
		else if (def != null && typesEqual(def.getElement(), childSession.getElement()))
			return def;
		def = childSession.interpret(type, (d, s) -> d.update(s));
		return def;
	}

	public static <T extends ExElement.Def<?>> void syncDefs(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions)
		throws QonfigInterpretationException {
		syncDefs(defType, defs, sessions, (d, s) -> d.update(s));
	}

	public static <T extends ExElement.Def<?>> void syncDefs(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions,
		ExBiConsumer<? super T, ExpressoQIS, QonfigInterpretationException> update) throws QonfigInterpretationException {
		CollectionUtils.SimpleAdjustment<T, ExpressoQIS, QonfigInterpretationException> adjustment = CollectionUtils
			.synchronize((List<T>) defs, sessions, //
				(widget, child) -> ExElement.typesEqual(widget.getElement(), child.getElement()))//
			.simpleE(child -> child.interpret(defType, update))//
			.rightOrder();
		if (update != null)
			adjustment.onCommonX(element -> update.accept(element.getLeftValue(), element.getRightValue()));
		adjustment.adjust();
	}

	/**
	 * An element that can never be instantiated, intended for use as a type parameter for {@link ExElement.Def definition} and
	 * {@link ExElement.Interpreted interpretation} implementations to signify that they do not actually produce an element
	 */
	public static class Void extends ExElement.Abstract {
		private Void() {
			super(null);
			throw new IllegalStateException("Impossible");
		}
	}

	static <P extends Def<?>, T> QonfigInterpreterCore.QonfigValueCreator<T> creator(Class<P> parentType,
		BiFunction<P, QonfigElementOrAddOn, T> creator) {
		return session -> {
			Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (parent != null && !parentType.isInstance(parent))
				throw new QonfigInterpretationException("This implementation requires a parent of type " + parentType.getName() + ", not "
					+ (parent == null ? "null" : parent.getClass().getName()), session.reporting().getPosition(), 0);
			return creator.apply((P) parent, session.getFocusType());
		};
	}

	static <T> QonfigInterpreterCore.QonfigValueCreator<T> creator(BiFunction<Def<?>, QonfigElementOrAddOn, T> creator) {
		return session -> creator.apply(session.as(ExpressoQIS.class).getElementRepresentation(), session.getFocusType());
	}
}

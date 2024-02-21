package org.observe.expresso.qonfig;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Def.Abstract.JoinedCollection;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.StringUtils;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ListenerList;
import org.qommons.config.*;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExceptionHandler;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** A base type for values interpreted from {@link QonfigElement}s */
public interface ExElement extends Identifiable {
	/** Default element identity class */
	public static class ElementIdentity {
		private String theStringRep;

		/** @param stringRep The new toString() for this identity */
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

		/** @return The Qonfig type of this element */
		QonfigElementOrAddOn getQonfigType();

		/** @return The QonfigElement that this definition was interpreted from */
		QonfigElement getElement();

		/** @return Error reporting for this element */
		ErrorReporting reporting();

		/** @return The expresso environment for this element */
		CompiledExpressoEnv getExpressoEnv();

		/** @param env The expresso environment for this element */
		void setExpressoEnv(CompiledExpressoEnv env);

		/**
		 * @param <AO> The type of the add-on to get
		 * @param addOn The type of the add-on to get
		 * @return The add-on in this element definition of the given type
		 */
		<AO extends ExAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn);

		/**
		 * @param <AO> The type of the add-ons to get
		 * @param addOn The type of the add-ons to get
		 * @return All add-ons on this element with the given type
		 */
		<AO extends ExAddOn.Def<? super E, ?>> Collection<AO> getAddOns(Class<AO> addOn);

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

		/**
		 * @param attr The attribute to get
		 * @return The value of the given attribute in this element
		 */
		Object getAttribute(QonfigAttributeDef attr);

		/** @return The element value of this element */
		Object getElementValue();

		/**
		 * @param child The child definition to get the children for
		 * @return The child elements in this element for the given role
		 */
		List<? extends Def<?>> getDefChildren(QonfigChildDef child);

		/** @return All element children of this element */
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

		/**
		 * @param interpreted The interpreted element to get the attribute value for
		 * @param attr The attribute to get the value for
		 * @return The value of the given attribute in the given interpreted element
		 */
		Object getAttribute(Interpreted<? extends E> interpreted, QonfigAttributeDef attr);

		/**
		 * @param interpreted The interpreted element to get the element value for
		 * @return The element value of the given interpreted element
		 */
		Object getElementValue(Interpreted<? extends E> interpreted);

		/**
		 * @param interpreted The interpreted element to get the children value in
		 * @param child The child role to get the children for
		 * @return The elements for the given child role in the given interpreted element
		 */
		List<? extends Interpreted<?>> getInterpretedChildren(Interpreted<? extends E> interpreted, QonfigChildDef child);

		/**
		 * @param interpreted The interpreted element to get the children value in
		 * @return All child elements in the given interpreted element
		 */
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

		/**
		 * @param element The element instance to get the children value in
		 * @param child The child role to get the children for
		 * @return The elements for the given child role in the given element Instance
		 */
		List<? extends ExElement> getElementChildren(E element, QonfigChildDef child);

		/**
		 * @param element The element instance to get the children in
		 * @return All child elements in the given element instance
		 */
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

		/** @return The promise that was specified to load this element's content */
		QonfigPromise.Def<?> getPromise();

		/**
		 * @param attrName The name of the attribute to get
		 * @param session The session to use to compile the expression
		 * @return The observable expression at the given attribute
		 * @throws QonfigInterpretationException If the attribute expression could not be parsed
		 */
		CompiledExpression getAttributeExpression(String attrName, ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * @param attr The attribute to get
		 * @param session
		 * @return The observable expression at the given attribute
		 * @throws QonfigInterpretationException If the attribute expression could not be parsed
		 */
		CompiledExpression getAttributeExpression(QonfigAttributeDef attr, ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * @param session
		 * @return The observable expression in this element's value
		 * @throws QonfigInterpretationException If the value expression could not be parsed
		 */
		CompiledExpression getValueExpression(ExpressoQIS session) throws QonfigInterpretationException;

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
		default <D extends ExElement.Def<?>> D syncChild(Class<? extends D> type, D def, ExpressoQIS session, String childName)
			throws QonfigInterpretationException, IllegalArgumentException {
			return syncChild(type, def, session, childName, Def::update);
		}

		/**
		 * @param <D> The type of the element definition
		 * @param type The type of the element definition
		 * @param def The definition currently in use
		 * @param session The parent session to interpret the new definition from, if needed
		 * @param childName The name of the child role fulfilled by the element to parse the definition from
		 * @param update The function to update the element with its session
		 * @return The given definition if it is up-to-date, or the newly interpreted one
		 * @throws QonfigInterpretationException If the definition could not be interpreted
		 * @throws IllegalArgumentException If no such child role exists
		 */
		<D extends ExElement.Def<?>> D syncChild(Class<? extends D> type, D def, ExpressoQIS session, String childName,
			ExBiConsumer<? super D, ExpressoQIS, QonfigInterpretationException> update)
				throws QonfigInterpretationException, IllegalArgumentException;

		/**
		 * Synchronizes a list of children with Qonfig-backed sessions, so that the result has exactly one child per Qonfig element
		 *
		 * @param <T> The type of the child definition
		 * @param defType The type of the child definition
		 * @param defs The list of children
		 * @param sessions The sessions for this element's children
		 * @throws QonfigInterpretationException If the children could not be interpreted from Qonfig
		 */
		default <T extends ExElement.Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions)
			throws QonfigInterpretationException {
			syncChildren(defType, defs, sessions, Def::update);
		}

		/**
		 * Synchronizes a list of children with Qonfig-backed sessions, so that the result has exactly one child per Qonfig element
		 *
		 * @param <T> The type of the child definition
		 * @param defType The type of the child definition
		 * @param defs The list of children
		 * @param sessions The sessions for this element's children
		 * @param update The function to update a child with its session
		 * @throws QonfigInterpretationException If the children could not be interpreted from Qonfig
		 */
		<T extends ExElement.Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions,
			ExBiConsumer<? super T, ExpressoQIS, QonfigInterpretationException> update) throws QonfigInterpretationException;

		/**
		 * Updates this element definition. Must be called at least once after interpretation produces this object.
		 *
		 * @param session The session supporting this element definition
		 * @throws QonfigInterpretationException If an error occurs interpreting some of this element's fields or content
		 */
		void update(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * An abstract implementation of {@link Def}. {@link Def} is an interface to allow implementations to implement more than one type
		 * of element, but all implementations should probably extend or be backed by this.
		 *
		 * @param <E> The type of the element that this definition is for
		 */
		public abstract class Abstract<E extends ExElement> implements Def<E> {
			private final ElementIdentity theId;
			private ExElement.Def<?> theParent;
			private final QonfigElementOrAddOn theQonfigType;
			private QonfigElement theElement;
			private final ClassMap<ExAddOn.Def<? super E, ?>> theAddOns;
			private final Set<ExAddOn.Def<? super E, ?>> theAddOnSequence;
			private Map<ElementTypeTraceability.QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> theTraceability;
			private CompiledExpressoEnv theExpressoEnv;
			private ErrorReporting theReporting;

			private QonfigPromise.Def<?> thePromise;
			private ExtElementView theExternalView;

			/**
			 * @param parent The definition interpreted from the parent element
			 * @param qonfigType The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				theId = new ElementIdentity();
				theId.setStringRepresentation(qonfigType.getName());
				theParent = parent;
				theQonfigType = qonfigType;
				theAddOns = new ClassMap<>();
				theAddOnSequence = new LinkedHashSet<>(); // Order is very important here
			}

			@Override
			public Object getIdentity() {
				return theId;
			}

			@Override
			public ExElement.Def<?> getParentElement() {
				return theParent;
			}

			/**
			 * Sets the parent element in situations where this cannot be known upon creation
			 *
			 * @param parent The parent element for this element
			 */
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
				AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (ao == null && theExternalView != null)
					ao = (AO) theExternalView.theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				return ao;
			}

			@Override
			public <AO extends ExAddOn.Def<? super E, ?>> Collection<AO> getAddOns(Class<AO> addOn) {
				Collection<AO> ao = (Collection<AO>) theAddOns.getAll(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (theExternalView != null)
					ao = new JoinedCollection<>(ao,
						(Collection<AO>) theExternalView.theExtAddOns.getAll(addOn, ClassMap.TypeMatch.SUB_TYPE));
				return ao;
			}

			@Override
			public Collection<ExAddOn.Def<? super E, ?>> getAddOns() {
				return Collections.unmodifiableSet(theAddOnSequence);
			}

			@Override
			public CompiledExpressoEnv getExpressoEnv() {
				return theExpressoEnv;
			}

			@Override
			public QonfigPromise.Def<?> getPromise() {
				return thePromise;
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
			public CompiledExpression getAttributeExpression(String attrName, ExpressoQIS session) throws QonfigInterpretationException {
				QonfigAttributeDef attr = session.attributes().get(attrName).getDefinition();
				return getExpression(attr, session);
			}

			CompiledExpression getExpression(QonfigValueDef type, ExpressoQIS session) throws QonfigInterpretationException {
				if (type == null)
					reporting().error("This element has no value definition");
				else if (!(type.getType() instanceof QonfigValueType.Custom)
					|| !(((QonfigValueType.Custom) type.getType()).getCustomType() instanceof ExpressionValueType))
					reporting().error("Attribute " + type + " is not an expression");

				QonfigValue value;
				if (type instanceof QonfigAttributeDef)
					value = getElement().getAttributes().get(type.getDeclared());
				else
					value = getElement().getValue();
				if (value == null || value.value == null)
					return null;

				Supplier<CompiledExpressoEnv> envSrc;
				if (thePromise == null)
					envSrc = this::getExpressoEnv;
				else if (documentsMatch(thePromise.getElement().getDocument().getLocation(), value.fileLocation))
					envSrc = this::getExpressoEnv;
				else
					envSrc = thePromise::getExternalExpressoEnv;

				ObservableExpression expression;
				try {
					expression = session.getExpressoParser().parse(((QonfigExpression) value.value).text);
				} catch (ExpressoParseException e) {
					LocatedFilePosition position;
					if (value.position instanceof LocatedPositionedContent)
						position = ((LocatedPositionedContent) value.position).getPosition(e.getErrorOffset());
					else
						position = new LocatedFilePosition(getElement().getDocument().getLocation(),
							value.position.getPosition(e.getErrorOffset()));
					throw new QonfigInterpretationException("Could not parse attribute " + type + ": " + e.getMessage(), position,
						e.getErrorLength(), e);
				}

				return new CompiledExpression(expression, getElement(), LocatedPositionedContent.of(value.fileLocation, value.position),
					envSrc);
			}

			@Override
			public CompiledExpression getAttributeExpression(QonfigAttributeDef attr, ExpressoQIS session)
				throws QonfigInterpretationException {
				return getExpression(attr, session);
			}

			@Override
			public CompiledExpression getValueExpression(ExpressoQIS session) throws QonfigInterpretationException {
				return getExpression(session.getValue().getDefinition(), session);
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
			@Override
			public <D extends ExElement.Def<?>> D syncChild(Class<? extends D> type, D def, ExpressoQIS session, String childName,
				ExBiConsumer<? super D, ExpressoQIS, QonfigInterpretationException> update)
					throws QonfigInterpretationException, IllegalArgumentException {
				ExpressoQIS childSession = childName == null ? session : session.forChildren(childName).peekFirst();
				if (childSession == null)
					return null;
				if (thePromise != null && !documentsMatch(childSession.getElement().getDocument().getLocation(),
					thePromise.getElement().getDocument().getLocation()))
					childSession.setExpressoEnv(thePromise.getExternalExpressoEnv());
				if (def == null || !typesEqual(def.getElement(), childSession.getElement()))
					def = childSession.interpret(type, update);
				else
					update.accept(def, childSession.asElement(def.getQonfigType()));
				return def;
			}

			@Override
			public <T extends ExElement.Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions,
				ExBiConsumer<? super T, ExpressoQIS, QonfigInterpretationException> update) throws QonfigInterpretationException {
				if (thePromise != null) {
					for (ExpressoQIS childSession : sessions) {
						if (!documentsMatch(childSession.getElement().getDocument().getLocation(),
							thePromise.getElement().getDocument().getLocation()))
							childSession.setExpressoEnv(thePromise.getExternalExpressoEnv());
					}
				}
				CollectionUtils.SimpleAdjustment<T, ExpressoQIS, QonfigInterpretationException> adjustment = CollectionUtils
					.synchronize((List<T>) defs, sessions, //
						(widget, child) -> ExElement.typesEqual(widget.getElement(), child.getElement()))//
					.simpleX(child -> child.interpret(defType, update))//
					.rightOrder();
				if (update != null) {
					// Right-only element already updated
					adjustment.onCommonX(element -> {
						try {
							update.accept(element.getLeftValue(), element.getRightValue());
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
						}
					});
				}
				adjustment.adjust();
			}

			/**
			 * @return Traceability for this element, which can provide attribute and element values and children for this element and its
			 *         interpretation and instantiations
			 */
			protected Map<ElementTypeTraceability.QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> getTraceability() {
				return theTraceability;
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
					theTraceability = new LinkedHashMap<>(
						ElementTypeTraceability.traceabilityFor(getClass(), theElement.getDocument().getDocToolkit(), theReporting));
				}
				session.setElementRepresentation(this);
				if (theParent != null) { // Check that the parent configured is actually the parent element
					if (theElement.getDocument() instanceof QonfigMetadata) {
						if (!theParent.getElement().isInstance(((QonfigMetadata) theElement.getDocument()).getElement()))
							throw new IllegalArgumentException(theParent + " is not the parent of " + this);
					} else if (theParent.getElement() != theElement.getParent())
						throw new IllegalArgumentException(theParent + " is not the parent of " + this);
				}

				setExpressoEnv(session.getExpressoEnv().at(theReporting.getFileLocation()));

				if (theElement.getPromise() == null)
					thePromise = null;
				else {
					ExpressoQIS promiseSession = session.interpretRoot(theElement.getPromise())//
						.setExpressoEnv(theExpressoEnv);
					if (thePromise == null || !typesEqual(thePromise.getElement(), theElement.getPromise())) {
						thePromise = promiseSession.interpret(QonfigPromise.Def.class);
						theExternalView = new ExtElementView();
					}
					if (thePromise != null) {
						thePromise.update(promiseSession, this);
						setExpressoEnv(thePromise.getExpressoEnv());
						session.setExpressoEnv(theExpressoEnv);
					}
				}

				if (firstTime) {
					// Add-ons can't change, because if they do, the element definition should be re-interpreted from the session
					Set<QonfigElementOrAddOn> addOnsTested = new HashSet<>();
					for (QonfigAddOn addOn : theElement.getInheritance().values()) {
						if (thePromise == null || !thePromise.getElement().isInstance(addOn))
							addAddOn(session, addOn, addOnsTested, theAddOns);
					}
					if (thePromise == null)
						addAddOns(session, theElement.getType(), addOnsTested, theAddOns);
					else {
						for (ExAddOn.Def<?, ?> addOn : thePromise.getAddOns()) {
							ElementTypeTraceability.join(theTraceability, SingleTypeTraceability.traceabilityFor(addOn.getClass(),
								theElement.getDocument().getDocToolkit(), theReporting));
						}
					}
					if (theExternalView != null) {
						theExternalView.update(session.setExpressoEnv(thePromise.getExternalExpressoEnv()));
						session.setExpressoEnv(theExpressoEnv);
					}
					makeAddOnSequence(theAddOns.getAllValues(),
						theExternalView == null ? null : theExternalView.theExtAddOns.getAllValues(),
							ao -> getAddOns((Class<? extends ExAddOn.Def<? super E, ?>>) ao), theAddOnSequence, reporting());
					if (theExternalView != null)
						makeAddOnSequence(theExternalView.theExtAddOns.getAllValues(), theAddOns.getAllValues(),
							ao -> theExternalView.getAddOns((Class<ExAddOn.Def<? super ExElement, ?>>) ao),
							theExternalView.theExtAddOnSequence, theExternalView.reporting());
				}

				try {
					forAddOns(session, (addOn, s) -> addOn.preUpdate(s, this));

					doUpdate(session);

					forAddOns(session, (addOn, s) -> addOn.postUpdate(s, this));

					postUpdate();
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

			private void forAddOns(ExpressoQIS session,
				ExBiConsumer<ExAddOn.Def<? super E, ?>, ExpressoQIS, QonfigInterpretationException> action)
					throws QonfigInterpretationException {
				for (ExAddOn.Def<? super E, ?> addOn : theAddOnSequence) {
					session = session.asElement(addOn.getType());
					if (addOn.getElement() == this)
						session.setExpressoEnv(theExpressoEnv);
					else
						session.setExpressoEnv(thePromise.getExternalExpressoEnv());
					action.accept(addOn, session);
				}
				session.setExpressoEnv(theExpressoEnv);
			}

			@Override
			public void setExpressoEnv(CompiledExpressoEnv env) {
				theExpressoEnv = env;
				if (thePromise != null)
					thePromise.setExpressoEnv(env);
			}

			/**
			 * Performs implementation-specific instantiation/update work. Also updates add-ons.
			 *
			 * @param session The Qonfig-backed session representing this element
			 * @throws QonfigInterpretationException If this element could not be interpreted from Qonfig
			 */
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				forAddOns(session, (addOn, s) -> addOn.update(s, this));
			}

			/**
			 * Called after all add-ons have been updated
			 *
			 * @throws QonfigInterpretationException If any post-update work fails
			 */
			protected void postUpdate() throws QonfigInterpretationException {}

			private void addAddOns(AbstractQIS<?> session, QonfigElementDef element, Set<QonfigElementOrAddOn> tested,
				ClassMap<ExAddOn.Def<? super E, ?>> addOns) throws QonfigInterpretationException {
				if (!tested.add(element))
					return;
				for (QonfigAddOn addOn : element.getInheritance())
					addAddOn(session, addOn, tested, addOns);
				if (element.getSuperElement() != null)
					addAddOns(session, element.getSuperElement(), tested, addOns);
			}

			private void addAddOn(AbstractQIS<?> session, QonfigAddOn addOn, Set<QonfigElementOrAddOn> tested,
				ClassMap<ExAddOn.Def<? super E, ?>> addOns) throws QonfigInterpretationException {
				if (!tested.add(addOn))
					return;
				session = session.asElementOnly(addOn);
				Class<?> addOnType = session.getInterpretationSupport(ExAddOn.Def.class);
				if (addOnType != null && addOns.get(addOnType, ClassMap.TypeMatch.SUB_TYPE) == null) {
					ExAddOn.Def<? super E, ?> exAddOn = session.interpret(addOn, ExAddOn.Def.class);
					ElementTypeTraceability.join(theTraceability,
						SingleTypeTraceability.traceabilityFor(exAddOn.getClass(), theElement.getDocument().getDocToolkit(), theReporting));
					addOns.put(exAddOn.getClass(), exAddOn);
				}
				if (addOn.getSuperElement() != null)
					addAddOns(session, addOn.getSuperElement(), tested, addOns);
				for (QonfigAddOn inh : addOn.getInheritance())
					addAddOn(session, inh, tested, addOns);
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

			private class ExtElementView implements ExElement.Def<ExElement> {
				private final ClassMap<ExAddOn.Def<? super E, ?>> theExtAddOns;
				private final Set<ExAddOn.Def<? super E, ?>> theExtAddOnSequence;
				private final ErrorReporting theExtReporting;

				ExtElementView() {
					theExtAddOns = new ClassMap<>();
					theExtAddOnSequence = new LinkedHashSet<>();
					theExtReporting = Abstract.this.reporting()//
						.at(theElement.getExternalContent().getFilePosition());
				}

				@Override
				public Object getIdentity() {
					return Abstract.this.getIdentity();
				}

				@Override
				public Def<?> getParentElement() {
					return Abstract.this.getParentElement();
				}

				@Override
				public QonfigElementOrAddOn getQonfigType() {
					return Abstract.this.getQonfigType();
				}

				@Override
				public QonfigElement getElement() {
					return Abstract.this.getElement();
				}

				@Override
				public ErrorReporting reporting() {
					return theExtReporting;
				}

				@Override
				public CompiledExpressoEnv getExpressoEnv() {
					return thePromise.getExternalExpressoEnv();
				}

				@Override
				public void setExpressoEnv(CompiledExpressoEnv env) {
					thePromise.setExternalExpressoEnv(env);
				}

				@Override
				public <AO extends ExAddOn.Def<? super ExElement, ?>> AO getAddOn(Class<AO> addOn) {
					AO ao = (AO) theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					if (ao == null)
						ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					return ao;
				}

				@Override
				public <AO extends ExAddOn.Def<? super ExElement, ?>> Collection<AO> getAddOns(Class<AO> addOn) {
					Collection<AO> ao = (Collection<AO>) theExtAddOns.getAll(addOn, ClassMap.TypeMatch.SUB_TYPE);
					ao = new JoinedCollection<>(ao, (Collection<AO>) theAddOns.getAll(addOn, ClassMap.TypeMatch.SUB_TYPE));
					return ao;
				}

				@Override
				public Collection<ExAddOn.Def<? super ExElement, ?>> getAddOns() {
					return (Set<ExAddOn.Def<? super ExElement, ?>>) (Set<?>) Collections.unmodifiableSet(theExtAddOnSequence);
				}

				@Override
				public Object getAttribute(QonfigAttributeDef attr) {
					return null;
				}

				@Override
				public Object getElementValue() {
					return Abstract.this.getElementValue();
				}

				@Override
				public List<? extends Def<?>> getDefChildren(QonfigChildDef child) {
					return Collections.emptyList();
				}

				@Override
				public Object getAttribute(Interpreted<? extends ExElement> interpreted, QonfigAttributeDef attr) {
					return null;
				}

				@Override
				public Object getElementValue(Interpreted<? extends ExElement> interpreted) {
					return null;
				}

				@Override
				public List<? extends Interpreted<?>> getInterpretedChildren(Interpreted<? extends ExElement> interpreted,
					QonfigChildDef child) {
					return Collections.emptyList();
				}

				@Override
				public List<? extends ExElement> getElementChildren(ExElement element, QonfigChildDef child) {
					return Collections.emptyList();
				}

				@Override
				public QonfigPromise.Def<?> getPromise() {
					return Abstract.this.getPromise();
				}

				@Override
				public CompiledExpression getAttributeExpression(String attrName, ExpressoQIS session)
					throws QonfigInterpretationException {
					return Abstract.this.getAttributeExpression(attrName, session);
				}

				@Override
				public CompiledExpression getAttributeExpression(QonfigAttributeDef attr, ExpressoQIS session)
					throws QonfigInterpretationException {
					return Abstract.this.getAttributeExpression(attr, session);
				}

				@Override
				public CompiledExpression getValueExpression(ExpressoQIS session) throws QonfigInterpretationException {
					return Abstract.this.getValueExpression(session);
				}

				@Override
				public <D extends Def<?>> D syncChild(Class<? extends D> type, D def, ExpressoQIS session, String childName,
					ExBiConsumer<? super D, ExpressoQIS, QonfigInterpretationException> update)
						throws QonfigInterpretationException, IllegalArgumentException {
					return Abstract.this.syncChild(type, def, session, childName, update);
				}

				@Override
				public <T extends Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions,
					ExBiConsumer<? super T, ExpressoQIS, QonfigInterpretationException> update) throws QonfigInterpretationException {
					Abstract.this.syncChildren(defType, defs, sessions, update);
				}

				@Override
				public void update(ExpressoQIS session) throws QonfigInterpretationException {
					if (theExtAddOns.isEmpty()) {
						session.setElementRepresentation(this);
						PartialQonfigElement element = theElement.getExternalContent();
						Set<QonfigElementOrAddOn> addOnsTested = new HashSet<>();
						for (QonfigAddOn addOn : element.getInheritance().values())
							addAddOn(session, addOn, addOnsTested, theExtAddOns);
						if (element.getType() instanceof QonfigElementDef)
							addAddOns(session, (QonfigElementDef) element.getType(), addOnsTested, theExtAddOns);
						else
							addAddOn(session, (QonfigAddOn) element.getType(), addOnsTested, theExtAddOns);
						session.setElementRepresentation(Abstract.this);
					}
				}

				@Override
				public String toString() {
					return Abstract.this.toString() + ".extView";
				}
			}

			static class JoinedCollection<E> extends AbstractCollection<E> {
				private final Collection<? extends E> theSource1;
				private final Collection<? extends E> theSource2;

				JoinedCollection(Collection<? extends E> source1, Collection<? extends E> source2) {
					theSource1 = source1;
					theSource2 = source2;
				}

				@Override
				public Iterator<E> iterator() {
					return new JoinedIterator<>(theSource1.iterator(), theSource2.iterator());
				}

				@Override
				public int size() {
					return theSource1.size() + theSource2.size();
				}

				private static class JoinedIterator<E> implements Iterator<E> {
					private final Iterator<? extends E> theSource1;
					private final Iterator<? extends E> theSource2;

					JoinedIterator(Iterator<? extends E> source1, Iterator<? extends E> source2) {
						theSource1 = source1;
						theSource2 = source2;
					}

					@Override
					public boolean hasNext() {
						return theSource1.hasNext() || theSource2.hasNext();
					}

					@Override
					public E next() {
						if (theSource1.hasNext())
							return theSource1.next();
						else
							return theSource2.next();
					}
				}
			}

			static <E extends ExElement> void makeAddOnSequence(Collection<ExAddOn.Def<? super E, ?>> addOns1,
				Collection<ExAddOn.Def<? super E, ?>> addOns2,
				Function<Class<? extends ExAddOn.Def<?, ?>>, Collection<? extends ExAddOn.Def<?, ?>>> getter,
					Set<ExAddOn.Def<? super E, ?>> sequence, ErrorReporting reporting) {
				BetterSet<ExAddOn.Def<? super E, ?>> dependencies = BetterHashSet.build().build();
				for (ExAddOn.Def<? super E, ?> addOn : addOns1) {
					if (sequence.contains(addOn)) {// Already added via dependencies
					} else
						addWithDependencies(addOn, getter, dependencies, sequence, reporting);
				}
				if (addOns2 != null) {
					for (ExAddOn.Def<? super E, ?> addOn : addOns2) {
						if (sequence.contains(addOn)) {// Already added via dependencies
						} else
							addWithDependencies(addOn, getter, dependencies, sequence, reporting);
					}
				}
			}

			private static <E extends ExElement> void addWithDependencies(ExAddOn.Def<? super E, ?> addOn,
				Function<Class<? extends ExAddOn.Def<?, ?>>, Collection<? extends ExAddOn.Def<?, ?>>> getter,
					BetterSet<ExAddOn.Def<? super E, ?>> dependencies,
					Set<ExAddOn.Def<? super E, ?>> sequence, ErrorReporting reporting) {
				dependencies.add(addOn);
				for (Class<? extends ExAddOn.Def<?, ?>> depType : addOn.getDependencies()) {
					for (ExAddOn.Def<?, ?> dep : getter.apply(depType)) {
						if (sequence.contains(dep)) {// Nothing to do
						} else if (dependencies.contains(dep)) {
							reporting.error("An add-on dependency cycle has been detected: "
								+ StringUtils.print("<-", dependencies, ao -> ao.getClass().getName()) + "<-" + depType.getName());
						} else
							addWithDependencies((ExAddOn.Def<? super E, ?>) dep, getter, dependencies, sequence, reporting);
					}
				}
				dependencies.removeLast();
				sequence.add(addOn);
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

		/** @return The promise that was specified to load this element's content */
		QonfigPromise.Interpreted<?> getPromise();

		/** @return Error reporting for this element */
		default ErrorReporting reporting() {
			return getDefinition().reporting();
		}

		/** @return This element's models */
		InterpretedModelSet getModels();

		/** @return The expresso environment for this element */
		InterpretedExpressoEnv getExpressoEnv();

		/** @param env The expresso environment for this element */
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

		/**
		 * @return Whether instances created from this interpretation should preserve their {@link ExElement#getUpdatingModels() models}
		 *         after {@link ExElement#instantiate(ModelSetInstance) instantiation}
		 */
		boolean isModelInstancePersistent();

		/**
		 * @param persist Whether instances created from this interpretation should preserve their {@link ExElement#getUpdatingModels()
		 *        models} after {@link ExElement#instantiate(ModelSetInstance) instantiation}
		 * @return This element
		 */
		Interpreted<E> persistModelInstances(boolean persist);

		/**
		 * @param <M> The model type for the expression
		 * @param <MV> The value type for the expression
		 * @param expression The expression to interpret
		 * @param type The type for the expression
		 * @return The interpreted expression
		 * @throws ExpressoInterpretationException If the expression could not be interpreted
		 */
		default <M, MV extends M> InterpretedValueSynth<M, MV> interpret(LocatedExpression expression, ModelInstanceType<M, MV> type)
			throws ExpressoInterpretationException {
			if (expression == null)
				return null;
			InterpretedExpressoEnv env = getEnvironmentFor(expression);
			return expression.interpret(type, env);
		}

		/**
		 * @param <M> The model type for the expression
		 * @param <MV> The value type for the expression
		 * @param <X1> The exception thrown by the exception handler in response to {@link ExpressoInterpretationException}s
		 * @param <X2> The exception thrown by the exception handler in response to {@link TypeConversionException}s
		 * @param expression The expression to interpret
		 * @param type The type for the expression
		 * @param handler The expression handler for non-fatal {@link ExpressoInterpretationException}s and {@link TypeConversionException}s
		 * @return The interpreted expression
		 * @throws ExpressoInterpretationException If the expression could not be interpreted
		 * @throws X1 If the exception handler throws one in response to an {@link ExpressoInterpretationException}
		 * @throws X2 If the exception handler throws one in response to a {@link TypeConversionException}
		 */
		default <M, MV extends M, X1 extends Throwable, X2 extends Throwable> InterpretedValueSynth<M, MV> interpret(
			LocatedExpression expression, ModelInstanceType<M, MV> type,
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, X1, X2> handler)
				throws ExpressoInterpretationException, X1, X2 {
			if (expression == null)
				return null;
			InterpretedExpressoEnv env = getEnvironmentFor(expression);
			return expression.interpret(type, env, handler);
		}

		/**
		 * @param env The expression to get the environment for
		 * @return The expresso environment to use to interpret the expression
		 */
		InterpretedExpressoEnv getEnvironmentFor(LocatedExpression env);

		/**
		 * Interprets or updates an interpreted child
		 *
		 * @param <D> The type of the definition of the child
		 * @param <I> The type of the interpretation of the child
		 * @param definition The child definition to interpret
		 * @param existing The existing interpreted child
		 * @param interpret The function to produce an interpretation for a child from a definition
		 * @param update The function to update an interpreted child
		 * @return The interpreted child
		 * @throws ExpressoInterpretationException
		 */
		default <D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> I syncChild(D definition, I existing,
			ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
				throws ExpressoInterpretationException {
			return syncChild(definition, existing, (d, env) -> interpret.apply(d), update);
		}

		/**
		 * Interprets or updates an interpreted child
		 *
		 * @param <D> The type of the definition of the child
		 * @param <I> The type of the interpretation of the child
		 * @param definition The child definition to interpret
		 * @param existing The existing interpreted child
		 * @param interpret The function to produce an interpretation for a child from a definition
		 * @param update The function to update an interpreted child
		 * @return The interpreted child
		 * @throws ExpressoInterpretationException
		 */
		<D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> I syncChild(D definition, I existing,
			ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

		/**
		 * Synchronizes a list of child definitions and interpretations, ensuring each child in the definitions list has its interpretation
		 * in the interpreted list, and that any interpretations without a definition are removed and disposed.
		 *
		 * @param <D> The type of the definition of the children
		 * @param <I> The type of the interpretation of the children
		 * @param definitions The child definitions to interpret
		 * @param existing The existing interpreted children
		 * @param interpret The function to produce an interpretation for a child from a definition
		 * @param update The function to update an interpreted child
		 * @throws ExpressoInterpretationException
		 */
		default <D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> void syncChildren(List<? extends D> definitions,
			List<I> existing, ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
				throws ExpressoInterpretationException {
			syncChildren(definitions, existing, (d, env) -> interpret.apply(d), update);
		}

		/**
		 * Synchronizes a list of child definitions and interpretations, ensuring each child in the definitions list has its interpretation
		 * in the interpreted list, and that any interpretations without a definition are removed and disposed.
		 *
		 * @param <D> The type of the definition of the children
		 * @param <I> The type of the interpretation of the children
		 * @param definitions The child definitions to interpret
		 * @param existing The existing interpreted children
		 * @param interpret The function to produce an interpretation for a child from a definition
		 * @param update The function to update an interpreted child
		 * @throws ExpressoInterpretationException
		 */
		<D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> void syncChildren(List<? extends D> definitions, List<I> existing,
			ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

		/**
		 * Installs a callback that will be called when an element on this interpretation is instantiated
		 *
		 * @param task The callback
		 * @return A Runnable that will remove the callback when {@link Runnable#run() run}
		 */
		Runnable onInstantiation(ExConsumer<? super E, ModelInstantiationException> task);

		/** @return Whether this element has been {@link #destroy() destroyed} */
		ObservableValue<Boolean> isDestroyed();

		/** @return An observable that fires if and when this element is {@link #destroy() destroyed} */
		default Observable<ObservableValueEvent<Boolean>> destroyed() {
			return isDestroyed().changes().filterP(evt -> Boolean.TRUE.equals(evt.getNewValue())).take(1);
		}

		/** Destroys this interpreted element, releasing all its resources */
		void destroy();

		/**
		 * An abstract implementation of {@link Interpreted}. {@link Interpreted} is an interface to allow implementations to implement more
		 * than one type of element, but all implementations should probably extend or be backed by this.
		 *
		 * @param <E> The type of element that this interpretation is for
		 */
		public abstract class Abstract<E extends ExElement> implements Interpreted<E> {
			private final Def.Abstract<? super E> theDefinition;
			private Interpreted.Abstract<?> theParent;
			private QonfigPromise.Interpreted<?> thePromise;
			private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theAddOns;
			private final Set<ExAddOn.Interpreted<? super E, ?>> theAddOnSequence;
			private final SettableValue<Boolean> isDestroyed;
			private InterpretedExpressoEnv theExpressoEnv;
			private Boolean isModelInstancePersistent;
			private boolean isInterpreting;
			private ListenerList<ExConsumer<? super E, ModelInstantiationException>> theOnInstantiations;

			private final ExtElementView theExternalView;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			protected Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = (Def.Abstract<? super E>) definition;
				if (parent != null)
					setParentElement(parent);
				theAddOns = new ClassMap<>();
				theAddOnSequence = new LinkedHashSet<>();
				isDestroyed = SettableValue.<Boolean> build().withValue(false).build();

				if (theDefinition.theExternalView != null)
					theExternalView = new ExtElementView();
				else
					theExternalView = null;

				Map<ExAddOn.Def<? super E, ?>, ExAddOn.Interpreted<? super E, ?>> addOns = theExternalView == null ? null : new HashMap<>();
				for (ExAddOn.Def<? super E, ?> addOn : theDefinition.getAddOns()) {
					ExAddOn.Interpreted<? super E, ?> interp;
					if (addOn.getElement() == theDefinition)
						interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					else
						interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(theExternalView);
					if (interp != null) {// It is allowed for add-on definitions not to produce interpretations
						theAddOnSequence.add(interp);
						if (addOn.getElement() == theDefinition)
							theAddOns.put(interp.getClass(), interp);
						else
							theExternalView.theExtAddOns.put(interp.getClass(), interp);
						if (addOns != null)
							addOns.put(addOn, interp);
					}
				}
				if (theExternalView != null) {
					for (ExAddOn.Def<? super E, ?> addOn : theDefinition.theExternalView.getAddOns()) {
						ExAddOn.Interpreted<? super E, ?> interp = addOns.get(addOn);
						if (interp != null)
							theExternalView.theExtAddOnSequence.add(interp);
					}
				}
			}

			@Override
			public QonfigPromise.Interpreted<?> getPromise() {
				return thePromise;
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

			/**
			 * Sets this element's parent in situation where the parent cannot be known when this interpretation is created.
			 *
			 * @param parent The parent for this element
			 * @return This interpreted element
			 */
			protected Abstract<E> setParentElement(Interpreted<?> parent) {
				if ((parent == null ? null : parent.getDefinition()) != theDefinition.getParentElement())
					throw new IllegalArgumentException(parent + " is not the parent of " + this);
				theParent = (Interpreted.Abstract<?>) parent;
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
				AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (ao == null && theExternalView != null)
					ao = (AO) theExternalView.theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				return ao;
			}

			@Override
			public Collection<ExAddOn.Interpreted<? super E, ?>> getAddOns() {
				return Collections.unmodifiableSet(theAddOnSequence);
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

			/**
			 * Called when this interpretation is instantiated for {@link #onInstantiation(ExConsumer)} callbacks
			 *
			 * @param element The element that was instantiated
			 */
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
			public InterpretedExpressoEnv getEnvironmentFor(LocatedExpression expression) {
				if (thePromise == null || documentsMatch(expression.getFilePosition().getFileLocation(),
					theDefinition.getPromise().getElement().getDocument().getLocation()))
					return theExpressoEnv;
				else
					return thePromise.getExternalExpressoEnv();
			}

			@Override
			public <D extends Def<?>, I extends Interpreted<?>> I syncChild(D definition, I existing,
				ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
				ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
					throws ExpressoInterpretationException {
				if (existing != null && (definition == null || existing.getIdentity() != definition.getIdentity())) {
					existing.destroy();
					existing = null;
				}
				if (definition != null) {
					InterpretedExpressoEnv env;
					if (thePromise != null && !documentsMatch(definition.getElement().getDocument().getLocation(),
						thePromise.getDefinition().getElement().getDocument().getLocation()))
						env = thePromise.getExternalExpressoEnv();
					else
						env = theExpressoEnv;
					if (existing == null)
						existing = interpret.apply(definition, env);
					if (existing != null)
						update.accept(existing, env);
				}
				return existing;
			}

			@Override
			public <D extends Def<?>, I extends Interpreted<?>> void syncChildren(List<? extends D> definitions, List<I> existing,
				ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
				ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
					throws ExpressoInterpretationException {
				CollectionUtils.synchronize(existing, definitions, (interp, def) -> interp.getIdentity() == def.getIdentity())//
				.adjust(new CollectionUtils.CollectionSynchronizerX<I, D, ExpressoInterpretationException>() {
					@Override
					public boolean getOrder(ElementSyncInput<I, D> element) throws ExpressoInterpretationException {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(ElementSyncInput<I, D> element) throws ExpressoInterpretationException {
						element.getLeftValue().destroy();
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(ElementSyncInput<I, D> element) throws ExpressoInterpretationException {
						try {
							InterpretedExpressoEnv env = getExpressoEnv(
								element.getRightValue().getElement().getDocument().getLocation());
							I interpreted = interpret.apply(element.getRightValue(), env);
							if (interpreted != null) {
								if (update != null)
									update.accept(interpreted, env);
								return element.useValue(interpreted);
							} else
								return element.remove();
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
							return element.remove();
						}
					}

					@Override
					public ElementSyncAction common(ElementSyncInput<I, D> element) throws ExpressoInterpretationException {
						try {
							update.accept(element.getLeftValue(),
								getExpressoEnv(element.getRightValue().getElement().getDocument().getLocation()));
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
						}
						return element.preserve();
					}

					private InterpretedExpressoEnv getExpressoEnv(String document) {
						InterpretedExpressoEnv env;
						if (thePromise != null
							&& !documentsMatch(document, thePromise.getDefinition().getElement().getDocument().getLocation()))
							env = thePromise.getExternalExpressoEnv();
						else
							env = theExpressoEnv;
						return env;
					}
				}, CollectionUtils.AdjustmentOrder.RightOrder);
			}

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return isDestroyed.unsettable();
			}

			@Override
			public void destroy() {
				for (ExElement.Interpreted<?> child : getDefinition().getAllInterpretedChildren(this))
					child.destroy();
				for (ExAddOn.Interpreted<?, ?> addOn : theAddOnSequence)
					addOn.destroy();
				theAddOns.clear();
				theAddOnSequence.clear();
				if (theExternalView != null) {
					theExternalView.theExtAddOns.clear();
					theExternalView.theExtAddOnSequence.clear();
				}
				if (!isDestroyed.get().booleanValue())
					isDestroyed.set(true, null);
			}

			/**
			 * Updates this element interpretation. Must be called at least once after the {@link #getDefinition() definition} produces this
			 * object.
			 *
			 * @param parentEnv The expresso environment of this element's parent
			 * @throws ExpressoInterpretationException If any model values in this element or any of its content fail to be interpreted
			 */
			protected final void update(InterpretedExpressoEnv parentEnv) throws ExpressoInterpretationException {
				if (isInterpreting)
					return;
				isInterpreting = true;
				try {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOnSequence)
						addOn.preUpdate(this);

					if (thePromise != null && (getDefinition().getPromise() == null
						|| thePromise.getIdentity() != getDefinition().getPromise().getIdentity())) {
						thePromise.destroy();
						thePromise = null;
					}
					if (thePromise == null && getDefinition().getPromise() != null)
						thePromise = getDefinition().getPromise().interpret();

					setExpressoEnv(parentEnv.forChild(theDefinition.getExpressoEnv()));

					doUpdate(theExpressoEnv);
					// If our models are the same as the parent, then they're already interpreted or interpreting
					// Can't always rely on having our parent correct, but we can tell from the definition
					boolean hasUniqueModels = getDefinition().getParentElement() == null
						|| getDefinition().getExpressoEnv().getModels() != getDefinition().getParentElement().getExpressoEnv().getModels();
					if (hasUniqueModels)
						theExpressoEnv.getModels().interpret(theExpressoEnv); // Interpret any remaining values
					for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOnSequence)
						addOn.postUpdate(this);
					postUpdate();
				} catch (RuntimeException | Error e) {
					reporting().error(e.getMessage(), e);
				} finally {
					isInterpreting = false;
				}
			}

			/**
			 * Performs implementation-specific initialization/update work on this element. Also updates add-ons and external content.
			 *
			 * @param expressoEnv The expresso environment to interpret expressions with
			 * @throws ExpressoInterpretationException If this element cannot be interpreted
			 */
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				setExpressoEnv(expressoEnv);
				if (thePromise != null) {
					thePromise.update(theExpressoEnv, this);
					// setExpressoEnv(thePromise.getExpressoEnv());
				}
				for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOnSequence)
					addOn.update(this);
				if (thePromise != null)
					thePromise.getExternalExpressoEnv().getModels().interpret(thePromise.getExternalExpressoEnv());
			}

			/**
			 * Called after all add-ons have been updated for this element
			 *
			 * @throws ExpressoInterpretationException If an exception occurs performing post-update work on this element
			 */
			protected void postUpdate() throws ExpressoInterpretationException {}

			@Override
			public String toString() {
				return getDefinition().toString();
			}

			class ExtElementView implements Interpreted<ExElement> {
				private final Def.Abstract<?>.ExtElementView theExtDefinition;
				private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theExtAddOns;
				private final Set<ExAddOn.Interpreted<? super E, ?>> theExtAddOnSequence;

				ExtElementView() {
					theExtDefinition = ((Def.Abstract<?>) theDefinition).theExternalView;
					theExtAddOns = new ClassMap<>();
					theExtAddOnSequence = new LinkedHashSet<>();
				}

				@Override
				public Def<? super ExElement> getDefinition() {
					return theExtDefinition;
				}

				@Override
				public Interpreted<?> getParentElement() {
					return Abstract.this.getParentElement();
				}

				@Override
				public QonfigPromise.Interpreted<?> getPromise() {
					return Abstract.this.getPromise();
				}

				@Override
				public InterpretedModelSet getModels() {
					return getExpressoEnv() == null ? null : getExpressoEnv().getModels();
				}

				@Override
				public InterpretedExpressoEnv getExpressoEnv() {
					return Abstract.this.getPromise().getExternalExpressoEnv();
				}

				@Override
				public void setExpressoEnv(InterpretedExpressoEnv env) {
					Abstract.this.getPromise().setExternalExpressoEnv(env);
				}

				@Override
				public <AO extends ExAddOn.Interpreted<? super ExElement, ?>> AO getAddOn(Class<AO> addOn) {
					AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					if (ao == null && thePromise != null)
						ao = (AO) theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					return ao;
				}

				@Override
				public Collection<ExAddOn.Interpreted<? super ExElement, ?>> getAddOns() {
					return (Set<ExAddOn.Interpreted<? super ExElement, ?>>) (Set<?>) Collections.unmodifiableSet(theExtAddOnSequence);
				}

				@Override
				public boolean isModelInstancePersistent() {
					return Abstract.this.isModelInstancePersistent();
				}

				@Override
				public Interpreted<ExElement> persistModelInstances(boolean persist) {
					return this;
				}

				@Override
				public InterpretedExpressoEnv getEnvironmentFor(LocatedExpression env) {
					return Abstract.this.getEnvironmentFor(env);
				}

				@Override
				public <D extends Def<?>, I extends Interpreted<?>> I syncChild(D definition, I existing,
					ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
					ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
						throws ExpressoInterpretationException {
					return Abstract.this.syncChild(definition, existing, interpret, update);
				}

				@Override
				public <D extends Def<?>, I extends Interpreted<?>> void syncChildren(List<? extends D> definitions, List<I> existing,
					ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
					ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
						throws ExpressoInterpretationException {
					Abstract.this.syncChildren(definitions, existing, interpret, update);
				}

				@Override
				public Runnable onInstantiation(ExConsumer<? super ExElement, ModelInstantiationException> task) {
					return Abstract.this.onInstantiation(task);
				}

				@Override
				public ObservableValue<Boolean> isDestroyed() {
					return Abstract.this.isDestroyed();
				}

				@Override
				public void destroy() {}

				@Override
				public String toString() {
					return theExtDefinition.toString();
				}
			}
		}
	}

	/** @return The name of this element's Qonfig type */
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
	 * This method is generally only applicable while it is {@link #update(Interpreted, ExElement) updating}. The model structures are
	 * typically discarded outside this phase to free up memory.
	 * </p>
	 * <p>
	 * The exception to this is when the {@link Interpreted#isModelInstancePersistent()} flag is set on the interpreter passed to the
	 * {@link #update(Interpreted, ExElement) update} method. In this case the models are persisted in the element and available any time
	 * </p>
	 *
	 * @return The model instance used by this element to build its expression values
	 * @throws IllegalStateException If this element is not {@link #update(Interpreted, ExElement) updating} and its interpretation's
	 *         {@link Interpreted#isModelInstancePersistent()} flag was not set
	 */
	ModelSetInstance getUpdatingModels() throws IllegalStateException;

	/** @return Error reporting for this element */
	ErrorReporting reporting();

	/**
	 * Instantiates model value instantiators in this element this element. Must be called at least once after being produced by its
	 * interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param parent The parent element for this element
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	void update(Interpreted<?> interpreted, ExElement parent) throws ModelInstantiationException;

	/**
	 * Instantiates this element's models. Must be called once after creation.
	 *
	 * @throws ModelInstantiationException If any model values fail to initialize
	 */
	void instantiated() throws ModelInstantiationException;

	/**
	 * Instantiates all model values in this element this element. Must be called at least once after being produced by its interpretation.
	 *
	 * @param interpreted The interpretation producing this element
	 * @param models The model instance for this element
	 * @return The models applicable to this element
	 * @throws ModelInstantiationException If an error occurs instantiating any model values needed by this element or its content
	 */
	ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException;

	/**
	 * @param parent The parent element for the new copy
	 * @return A copy of this element with the given parent
	 */
	ExElement copy(ExElement parent);

	/** @return Whether this element has been {@link #destroy() destroyed} */
	ObservableValue<Boolean> isDestroyed();

	/** Destroys this element, unsubscribing all its connections and releasing its resources */
	void destroy();

	/** @return An observable that fires once if and when this element is {@link #destroy() destroyed} */
	default Observable<ObservableValueEvent<Boolean>> onDestroy() {
		return isDestroyed().noInitChanges().filter(evt -> evt.getNewValue()).take(1);
	}

	/**
	 * An abstract implementation of {@link ExElement}. {@link ExElement} is an interface to allow implementations to implement more than
	 * one type of element, but all implementations should probably extend or be backed by this.
	 */
	public abstract class Abstract implements ExElement, Cloneable {
		private final Object theId;
		private ExElement theParent;
		private ModelInstantiator theLocalModel;
		private boolean isModelPersistent;
		private ClassMap<ExAddOn<?>> theAddOns;
		private Set<ExAddOn<?>> theAddOnSequence;
		private ErrorReporting theReporting;
		private String theTypeName;
		private SettableValue<Boolean> isDestroyed;
		private ModelSetInstance theUpdatingModels;

		private QonfigPromise thePromise;
		private ExtElementView theExternalView;

		/** @param id The identification for this element */
		protected Abstract(Object id) {
			if (id == null)
				throw new NullPointerException();
			theId = id;
			theAddOns = new ClassMap<>();
			theAddOnSequence = new LinkedHashSet<>();
			isDestroyed = SettableValue.<Boolean> build().withValue(false).build();
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
			AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			if (ao == null && theExternalView != null)
				ao = (AO) theExternalView.theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			return ao;
		}

		@Override
		public Collection<ExAddOn<?>> getAddOns() {
			if (thePromise != null)
				return new Def.Abstract.JoinedCollection<>(theAddOns.getAllValues(), theExternalView.theExtAddOns.getAllValues());
			else
				return theAddOns.getAllValues();
		}

		@Override
		public ModelInstantiator getModels() {
			if (theLocalModel != null)
				return theLocalModel;
			else if (theParent != null)
				return theParent.getModels();
			else
				return null;
		}

		@Override
		public ModelSetInstance getUpdatingModels() {
			return theUpdatingModels;
		}

		@Override
		public ErrorReporting reporting() {
			return theReporting;
		}

		/**
		 * Whether to preserve this element's {@link #getUpdatingModels() models} after {@link #instantiate(ModelSetInstance) instantiation}
		 */
		protected void persistModels() {
			isModelPersistent = true;
		}

		@Override
		public final void update(Interpreted<?> interpreted, ExElement parent) throws ModelInstantiationException {
			if (theId != interpreted.getIdentity())
				throw new IllegalArgumentException("Wrong interpretation: " + interpreted + " for " + this);
			if (interpreted instanceof Interpreted.Abstract)
				((Interpreted.Abstract<ExElement>) interpreted).instantiated(this);
			theReporting = interpreted.reporting();
			if (parent == this)
				throw new IllegalArgumentException("An element cannot be its own parent");
			theParent = parent;
			theTypeName = interpreted.getDefinition().getElement().getType().getName();

			Interpreted.Abstract<?> myInterpreted = (Interpreted.Abstract<?>) interpreted;
			if (myInterpreted.theExternalView != null) {
				if (theExternalView == null)
					theExternalView = new ExtElementView(myInterpreted.theExternalView);
			} else
				theExternalView = null;

			// Create add-ons
			List<ExAddOn<?>> addOns = new ArrayList<>(theAddOnSequence);
			Map<ExAddOn.Interpreted<?, ?>, ExAddOn<?>> addOnsByInterp = theExternalView == null ? null : new HashMap<>();
			theAddOnSequence.clear();
			CollectionUtils
			.synchronize(addOns, new ArrayList<>(interpreted.getAddOns()),
				(inst, interp) -> inst.getInterpretationType() == interp.getClass())//
			.adjust(new CollectionUtils.CollectionSynchronizer<ExAddOn<?>, ExAddOn.Interpreted<?, ?>>() {
				@Override
				public boolean getOrder(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					return true;
				}

				@Override
				public ElementSyncAction leftOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					if (element.getRightValue().getElement() == interpreted)
						theAddOns.compute(element.getLeftValue().getClass(), __ -> null);
					else
						theExternalView.theExtAddOns.compute(element.getLeftValue().getClass(), __ -> null);
					return element.remove();
				}

				@Override
				public ElementSyncAction rightOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					ExAddOn<?> instance;
					if (element.getRightValue().getElement() == interpreted)
						instance = ((ExAddOn.Interpreted<ExElement, ?>) element.getRightValue()).create(ExElement.Abstract.this);
					else
						instance = ((ExAddOn.Interpreted<ExElement, ?>) element.getRightValue()).create(theExternalView);
					if (instance != null) {
						if (element.getRightValue().getElement() == interpreted)
							theAddOns.put(instance.getClass(), instance);
						else
							theExternalView.theExtAddOns.put(instance.getClass(), instance);
						return element.useValue(instance);
					} else
						return element.preserve();
				}

				@Override
				public ElementSyncAction common(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
					return element.preserve();
				}
			}, CollectionUtils.AdjustmentOrder.RightOrder);
			theAddOnSequence.addAll(addOns);
			if (theExternalView != null) {
				for (ExAddOn.Interpreted<?, ?> addOn : myInterpreted.theExternalView.getAddOns()) {
					ExAddOn<?> interp = addOnsByInterp.get(addOn);
					if (interp != null)
						theExternalView.theExtAddOnSequence.add(interp);
				}
			}

			if (thePromise != null
				&& (interpreted.getPromise() == null || thePromise.getIdentity() != interpreted.getPromise().getIdentity())) {
				thePromise.destroy();
				thePromise = null;
			}
			if (thePromise == null && interpreted.getPromise() != null) {
				thePromise = interpreted.getPromise().create(this);
				if (theExternalView == null)
					theExternalView = new ExtElementView(myInterpreted.theExternalView);
			}

			if (interpreted.getParentElement() == null//
				|| interpreted.getExpressoEnv().getModels().getIdentity() != interpreted.getParentElement().getExpressoEnv().getModels()
				.getIdentity())
				theLocalModel = interpreted.getExpressoEnv().getModels().instantiate();
			else
				theLocalModel = null;
			isModelPersistent = interpreted.isModelInstancePersistent();
			try {
				for (ExAddOn<?> addOn : theAddOnSequence)
					((ExAddOn<ExElement>) addOn).preUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);

				doUpdate(interpreted);

				for (ExAddOn<?> addOn : theAddOnSequence)
					((ExAddOn<ExElement>) addOn).postUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
		}

		/**
		 * Performs implementation specific initialization/update operations. Also updates add-ons and external content.
		 *
		 * @param interpreted The interpretation of this element
		 * @throws ModelInstantiationException If any model values fail to initialize
		 */
		protected void doUpdate(Interpreted<?> interpreted) throws ModelInstantiationException {
			for (ExAddOn<?> addOn : theAddOnSequence)
				((ExAddOn<ExElement>) addOn)
				.update(interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
			if (theExternalView != null)
				theExternalView.update(interpreted, theParent);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.preInstantiated();
			if (theLocalModel != null)
				theLocalModel.instantiate();
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.instantiated();
			if (theExternalView != null)
				theExternalView.instantiated();
		}

		@Override
		public final ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = null;
			try {
				for (ExAddOn<?> addOn : theAddOnSequence)
					addOn.preInstantiate();

				myModels = createElementModel(models);
				try {
					if (thePromise != null)
						thePromise.instantiate(models);
					doInstantiate(myModels);

					for (ExAddOn<?> addOn : theAddOnSequence)
						addOn.postInstantiate(theUpdatingModels);
					if (theExternalView != null)
						theExternalView.postInstantiateAddOns(theUpdatingModels);
					myModels = theUpdatingModels;
				} finally {
					if (!isModelPersistent)
						theUpdatingModels = null;
				}
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
			return myModels;
		}

		/**
		 * @param builder The model instance builder to install runtime models into. Runtime models are those that expressions on the
		 *        element should not have access to, but may be needed for expressions that were interpreted in a different environment but
		 *        need to be executed on this element (e.g. style sheets).
		 *
		 * @param elementModels The model instance for this element
		 * @throws ModelInstantiationException If any runtime models could not be installed
		 */
		protected void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels)
			throws ModelInstantiationException {
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.addRuntimeModels(builder, elementModels);
			if (thePromise != null)
				((ExElement.Abstract) thePromise).addRuntimeModels(builder, elementModels);
		}

		/**
		 * @param parentModels The parent models
		 * @return The models for this element
		 * @throws ModelInstantiationException If the element models could not be created
		 */
		protected ModelSetInstance createElementModel(ModelSetInstance parentModels) throws ModelInstantiationException {
			// Construct a model instance containing the parent models, this element's local models,
			// and any models required by the runtime environment
			Observable<?> modelUntil = Observable.or(parentModels.getUntil(), onDestroy());
			ModelSetInstanceBuilder runtimeModels = ObservableModelSet.createMultiModelInstanceBag(modelUntil)//
				.withAll(parentModels);
			ModelSetInstance elementModels;
			if (theLocalModel != null) {
				elementModels = theLocalModel.createInstance(modelUntil)//
					.withAll(parentModels)//
					.build();
				runtimeModels.withAll(elementModels);
			} else
				elementModels = parentModels;
			addRuntimeModels(runtimeModels, elementModels);
			if (runtimeModels.getTopLevelModels().size() == 1)
				return runtimeModels.getInherited(runtimeModels.getTopLevelModels().iterator().next());
			else
				return runtimeModels.build();
		}

		/**
		 * Performs implementation-specific instantiation for the element. Also instantiates add-ons and external content.
		 *
		 * @param myModels The model instance for this element to use for its values
		 * @throws ModelInstantiationException If this element could not be instantiated
		 */
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theUpdatingModels = myModels;
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.instantiate(myModels);
			if (theExternalView != null)
				theExternalView.instantiate(myModels);
		}

		@Override
		public Abstract copy(ExElement parent) {
			Abstract copy = clone();
			copy.theParent = parent;
			copy.theAddOns = new ClassMap<>();
			copy.theAddOnSequence = new LinkedHashSet<>();
			copy.isDestroyed = SettableValue.<Boolean> build().withValue(false).build();

			if (theExternalView != null)
				copy.theExternalView = theExternalView.copy(this);

			Map<ExAddOn<?>, ExAddOn<?>> addOns = new HashMap<>();
			for (ExAddOn<?> addOn : theAddOnSequence) {
				ExAddOn<?> addOnCopy = ((ExAddOn<ExElement>) addOn).copy(copy);
				addOns.put(addOn, addOnCopy);
				copy.theAddOnSequence.add(addOnCopy);
				if (addOn.getElement() == this)
					copy.theAddOns.put(addOnCopy.getClass(), addOnCopy);
				else
					copy.theExternalView.theExtAddOns.put(addOnCopy.getClass(), addOnCopy);
			}
			if (theExternalView != null) {
				for (ExAddOn<?> addOn : theExternalView.theExtAddOnSequence)
					copy.theExternalView.theExtAddOnSequence.add(addOns.get(addOn));
			}
			if (thePromise != null)
				copy.thePromise = thePromise.copy(copy);

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
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.destroy();
			theAddOns.clear();
			theAddOnSequence.clear();
			if (theExternalView != null) {
				theExternalView.theExtAddOns.clear();
				theExternalView.theExtAddOnSequence.clear();
			}
			if (!isDestroyed.get().booleanValue())
				isDestroyed.set(true, null);
		}

		@Override
		public String toString() {
			return theId.toString();
		}

		private class ExtElementView implements ExElement, Cloneable {
			private final ErrorReporting theExtReporting;
			private ClassMap<ExAddOn<?>> theExtAddOns;
			private Set<ExAddOn<?>> theExtAddOnSequence;

			ExtElementView(Interpreted.Abstract<?>.ExtElementView interpreted) {
				theExtReporting = interpreted.reporting();
				theExtAddOns = new ClassMap<>();
				theExtAddOnSequence = new LinkedHashSet<>();
			}

			ExtElementView(Abstract.ExtElementView toCopy) {
				theExtReporting = toCopy.reporting();
				theExtAddOns = new ClassMap<>();
			}

			@Override
			public Object getIdentity() {
				return Abstract.this.getIdentity();
			}

			@Override
			public String getTypeName() {
				return Abstract.this.getTypeName();
			}

			@Override
			public ExElement getParentElement() {
				return Abstract.this.getParentElement();
			}

			@Override
			public <AO extends ExAddOn<?>> AO getAddOn(Class<AO> addOn) {
				AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (ao == null && thePromise != null)
					ao = (AO) theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				return ao;
			}

			@Override
			public Collection<ExAddOn<?>> getAddOns() {
				return new JoinedCollection<>(theExtAddOns.getAllValues(), theAddOns.getAllValues());
			}

			@Override
			public ModelInstantiator getModels() {
				return thePromise.getExtModels();
			}

			@Override
			public ModelSetInstance getUpdatingModels() throws IllegalStateException {
				return Abstract.this.getUpdatingModels();
			}

			@Override
			public ErrorReporting reporting() {
				return theExtReporting;
			}

			@Override
			public void update(Interpreted<?> interpreted, ExElement parent) throws ModelInstantiationException {
				Interpreted.Abstract<?> owner = (Interpreted.Abstract<?>) interpreted;

				thePromise.update(owner.getPromise());
			}

			@Override
			public void instantiated() throws ModelInstantiationException {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					addOn.instantiated();
				thePromise.instantiated();
			}

			@Override
			public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
				return models;
			}

			void postInstantiateAddOns(ModelSetInstance models) throws ModelInstantiationException {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).postInstantiate(models);
			}

			@Override
			public ExtElementView copy(ExElement element) {
				return ((Abstract) element).new ExtElementView(this);
			}

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return Abstract.this.isDestroyed();
			}

			@Override
			public void destroy() {}

			@Override
			public String toString() {
				return Abstract.this.toString() + ".extView";
			}
		}
	}

	/**
	 * @param location1 The location of document 1
	 * @param location2 The location of document 2
	 * @return Whether the 2 locations are for the same document
	 */
	public static boolean documentsMatch(String location1, String location2) {
		// This function handles null and also calls hashCode, which caches, to speed this up for multiple calls
		if (location1 == null)
			return location2 == null;
		else if (location2 == null)
			return false;
		if (location1.hashCode() != location2.hashCode())
			return false;
		return location1.equals(location2);
	}

	/**
	 * @param element1 The first element to compare
	 * @param element2 The second element to compare
	 * @return Whether the type information of both elements are the same
	 */
	public static boolean typesEqual(PartialQonfigElement element1, PartialQonfigElement element2) {
		return element1.getType() == element2.getType() && element1.getInheritance().equals(element2.getInheritance());
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

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExElement}
	 *
	 * @param <P> The parent type required by the element type
	 * @param <T> The type of the element
	 * @param parentType The parent type required by the element type
	 * @param creator Function to create the element from the parent and type
	 * @return The Qonfig interpretation creator
	 */
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

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExElement}
	 *
	 * @param <T> The type of the element
	 * @param creator Function to create the element from the parent and type
	 * @return The Qonfig interpretation creator
	 */
	static <T> QonfigInterpreterCore.QonfigValueCreator<T> creator(BiFunction<Def<?>, QonfigElementOrAddOn, T> creator) {
		return session -> creator.apply(session.as(ExpressoQIS.class).getElementRepresentation(), session.getFocusType());
	}
}

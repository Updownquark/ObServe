package org.observe.expresso.qonfig;

import java.util.*;
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
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.BreakpointHere;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MappedList;
import org.qommons.config.*;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExTriConsumer;
import org.qommons.ex.ExTriFunction;
import org.qommons.ex.ExceptionHandler;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/** A base type for values interpreted from {@link QonfigElement}s */
public interface ExElement extends Identifiable {
	/** Default element identity class */
	public static class ElementIdentity {
		private String theStringRep;

		/**
		 * @param stringRep The new toString() for this identity
		 * @return This identity
		 */
		public ElementIdentity setStringRepresentation(String stringRep) {
			theStringRep = stringRep;
			return this;
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

		/**
		 * @param file The file that this element may belong to
		 * @return The reporting for this element, in the given file if this element has a representation in the file
		 */
		ErrorReporting reporting(String file);

		/** @return The location of the document that declared this element */
		String getDocument();

		/** @return All expresso environments usable by expressions in this element */
		List<CompiledExpressoEnv> getExpressoEnvs();

		/** @return All expresso documents which expressions in this element may be from */
		Set<String> getExpressoDocuments();

		/**
		 * @param document The document to get the environment for
		 * @return The expresso environment for this element for the document
		 */
		CompiledExpressoEnv getExpressoEnv(String document);

		/**
		 * @param document The document to set the environment for
		 * @param env An expresso environment for this element and document
		 */
		void setExpressoEnv(String document, CompiledExpressoEnv env);

		/**
		 * @param <D> The element definition type to cast this element to
		 * @param type The element definition type to cast this element to
		 * @param errorPosition The file position for the error if it must be thrown
		 * @return The representation of this element as the given type
		 * @throws QonfigInterpretationException If this element has no such representation
		 */
		<D extends ExElement.Def<?>> D as(Class<D> type, LocatedFilePosition errorPosition) throws QonfigInterpretationException;

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
				Collections.sort(children, EL_INTERP_COMPARE);
			return children == null ? Collections.emptyList() : children;
		}

		/** Compares interpreted elements by their position */
		static final Comparator<ExElement.Interpreted<?>> EL_INTERP_COMPARE = (el1, el2) -> el1.reporting().getPosition()
			.compareTo(el2.reporting().getPosition());

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
		default CompiledExpression getAttributeExpression(String attrName, ExpressoQIS session) throws QonfigInterpretationException {
			QonfigAttributeDef attr = session.attributes().get(attrName).getDefinition();
			return getAttributeExpression(attr, session);
		}

		/**
		 * @param attr The attribute to get
		 * @param session
		 * @return The observable expression at the given attribute
		 * @throws QonfigInterpretationException If the attribute expression could not be parsed
		 */
		default CompiledExpression getAttributeExpression(QonfigAttributeDef attr, ExpressoQIS session)
			throws QonfigInterpretationException {
			return getExpression(attr, session);
		}

		/**
		 * @param session
		 * @return The observable expression in this element's value
		 * @throws QonfigInterpretationException If the value expression could not be parsed
		 */
		default CompiledExpression getValueExpression(ExpressoQIS session) throws QonfigInterpretationException {
			return getExpression(session.getValue().getDefinition(), session);
		}

		/**
		 * Parses an expression in the context of this element
		 *
		 * @param type The value type of the expression to parse
		 * @param session The expresso session to use for parsing
		 * @return The parsed expression, or null if none was specified in this element for the given value/attribute
		 * @throws QonfigInterpretationException If the expression could not be parsed
		 */
		default CompiledExpression getExpression(QonfigValueDef type, ExpressoQIS session) throws QonfigInterpretationException {
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

			Supplier<CompiledExpressoEnv> envSrc = LambdaUtils.cachingSupplier(() -> getExpressoEnv(value.fileLocation));

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
		public abstract class Abstract<E extends ExElement> extends AbstractIdentifiable implements Def<E> {
			private final ElementIdentity theId;
			private ExElement.Def<?> theParent;
			private final QonfigElementOrAddOn theQonfigType;
			private QonfigElement theElement;
			private final ClassMap<ExAddOn.Def<? super E, ?>> theAddOns;
			private final Set<ExAddOn.Def<? super E, ?>> theAddOnSequence;
			private Map<ElementTypeTraceability.QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> theTraceability;
			private String theDocument;
			private DocumentMap<CompiledExpressoEnv> theExpressoEnvs;
			private ErrorReporting theReporting;

			private QonfigPromise.Def<?> thePromise;

			/**
			 * @param parent The definition interpreted from the parent element
			 * @param qonfigType The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				theId = new ElementIdentity().setStringRepresentation(qonfigType.getName());
				initIdentity(theId);
				theParent = parent;
				theQonfigType = qonfigType;
				theAddOns = new ClassMap<>();
				theAddOnSequence = new LinkedHashSet<>(); // Order is very important here
			}

			@Override
			protected Object createIdentity() {
				throw new IllegalStateException("Should have been initialized");
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
			public ErrorReporting reporting(String file) {
				if (file == null || file.equals(theReporting.getPosition().getFileLocation()))
					return theReporting;
				else if (thePromise != null && file.equals(thePromise.reporting().getPosition().getFileLocation()))
					return thePromise.reporting();
				else
					return theReporting;
			}

			@Override
			public <D extends Def<?>> D as(Class<D> type, LocatedFilePosition errorPosition) throws QonfigInterpretationException {
				if (type.isInstance(this))
					return (D) this;
				throw new QonfigInterpretationException(
					"This implementation requires an element definition of type " + type.getName() + ", not " + getClass().getName(),
					errorPosition == null ? reporting().getPosition() : errorPosition, 0);
			}

			@Override
			public <AO extends ExAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public <AO extends ExAddOn.Def<? super E, ?>> Collection<AO> getAddOns(Class<AO> addOn) {
				return (Collection<AO>) theAddOns.getAll(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<ExAddOn.Def<? super E, ?>> getAddOns() {
				return Collections.unmodifiableSet(theAddOnSequence);
			}

			@Override
			public String getDocument() {
				return theDocument;
			}

			@Override
			public Set<String> getExpressoDocuments() {
				return theExpressoEnvs == null ? Collections.emptySet() : Collections.unmodifiableSet(theExpressoEnvs.keySet());
			}

			@Override
			public List<CompiledExpressoEnv> getExpressoEnvs() {
				return theExpressoEnvs == null ? Collections.emptyList() : theExpressoEnvs.values();
			}

			@Override
			public CompiledExpressoEnv getExpressoEnv(String document) {
				return theExpressoEnvs == null ? null : theExpressoEnvs.get(document);
			}

			@Override
			public void setExpressoEnv(String document, CompiledExpressoEnv env) {
				if (theExpressoEnvs == null)
					throw new IllegalStateException("This element has not been updated yet");
				theExpressoEnvs.put(document, env);
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
				if (def == null || !typesEqual(def.getElement(), childSession.getElement()))
					def = childSession.interpret(type, update);
				else
					update.accept(def, childSession.asElement(def.getQonfigType()));
				return def;
			}

			@Override
			public <T extends ExElement.Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions,
				ExBiConsumer<? super T, ExpressoQIS, QonfigInterpretationException> update) throws QonfigInterpretationException {
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
				theDocument = session.getInterpretingDocument();
				theExpressoEnvs = session.getExpressoEnvs();
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

				if (theElement.getPromise() == null)
					thePromise = null;
				else {
					ExpressoQIS promiseSession = session.interpretRoot(theElement.getPromise())//
						.setExpressoEnvs(theExpressoEnvs);
					if (thePromise == null || !typesEqual(thePromise.getElement(), theElement.getPromise())) {
						thePromise = promiseSession.interpret(QonfigPromise.Def.class);
					}
					if (thePromise != null) {
						thePromise.update(promiseSession, this);
						for (String doc : thePromise.getExpressoDocuments())
							theExpressoEnvs.put(doc, thePromise.getExpressoEnv(doc));
						session.setExpressoEnvs(theExpressoEnvs);
					}
				}

				if (firstTime) {
					// Add-ons can't change, because if they do, the element definition should be re-interpreted from the session
					Set<QonfigElementOrAddOn> addOnsTested = new HashSet<>();
					for (QonfigAddOn addOn : getElementInheritance()) {
						addAddOn(session, addOn, addOnsTested, theAddOns);
					}
					makeAddOnSequence(theAddOns.getAllValues(),
						// theExternalView == null ? null : theExternalView.theExtAddOns.getAllValues(),
						ao -> getAddOns((Class<? extends ExAddOn.Def<? super E, ?>>) ao), theAddOnSequence, reporting());
				}

				try {
					forAddOns(session, (addOn, s) -> addOn.preUpdate(s, this));

					doUpdate(session.setExpressoEnvs(theExpressoEnvs));

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

			/** @return This element's {@link QonfigElement#getInheritance() inheritance} */
			protected Collection<QonfigAddOn> getElementInheritance() {
				return theElement.getInheritance().values();
			}

			private void forAddOns(ExpressoQIS session,
				ExBiConsumer<ExAddOn.Def<? super E, ?>, ExpressoQIS, QonfigInterpretationException> action)
					throws QonfigInterpretationException {
				for (ExAddOn.Def<? super E, ?> addOn : theAddOnSequence) {
					session = session.asElement(addOn.getType());
					action.accept(addOn, session);
				}
				session.setExpressoEnvs(theExpressoEnvs);
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
			protected void postUpdate() throws QonfigInterpretationException {
			}

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
						|| !type.getDeclaredChildren().isEmpty()) {
						if (!(theElement.getType() instanceof QonfigPromiseDef))
							theReporting.warn(getClass() + ": Traceability not configured for " + key);
					}
				} else {
					if (type.getSuperElement() != null)
						checkTraceability(type.getSuperElement());
					for (QonfigAddOn inh : type.getInheritance())
						checkTraceability(inh);
				}
			}

			static class JoinedSet<E> extends AbstractSet<E> {
				private final Collection<? extends E> theSource1;
				private final Collection<? extends E> theSource2;

				JoinedSet(Collection<? extends E> source1, Collection<? extends E> source2) {
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
				// Collection<ExAddOn.Def<? super E, ?>> addOns2,
				Function<Class<? extends ExAddOn.Def<?, ?>>, Collection<? extends ExAddOn.Def<?, ?>>> getter,
					Set<ExAddOn.Def<? super E, ?>> sequence, ErrorReporting reporting) {
				BetterSet<ExAddOn.Def<? super E, ?>> dependencies = BetterHashSet.build().build();
				for (ExAddOn.Def<? super E, ?> addOn : addOns1) {
					if (sequence.contains(addOn)) {// Already added via dependencies
					} else
						addWithDependencies(addOn, getter, dependencies, sequence, reporting);
				}
				// if (addOns2 != null) {
				// for (ExAddOn.Def<? super E, ?> addOn : addOns2) {
				// if (sequence.contains(addOn)) {// Already added via dependencies
				// } else
				// addWithDependencies(addOn, getter, dependencies, sequence, reporting);
				// }
				// }
			}

			private static <E extends ExElement> void addWithDependencies(ExAddOn.Def<? super E, ?> addOn,
				Function<Class<? extends ExAddOn.Def<?, ?>>, Collection<? extends ExAddOn.Def<?, ?>>> getter,
					BetterSet<ExAddOn.Def<? super E, ?>> dependencies, Set<ExAddOn.Def<? super E, ?>> sequence, ErrorReporting reporting) {
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

		/**
		 * Enables expressions in this element to reference the environment of the given element
		 *
		 * @param parent The element to inherit the expresso environment from
		 * @return This element
		 */
		Interpreted<E> addLogicalParent(Interpreted<?> parent);

		/** @return The promise that was specified to load this element's content */
		QonfigPromise.Interpreted<?> getPromise();

		/** @return Error reporting for this element */
		default ErrorReporting reporting() {
			return getDefinition().reporting();
		}

		/**
		 * @param file The file that this element may belong to
		 * @return The reporting for this element, in the given file if this element has a representation in the file
		 */
		default ErrorReporting reporting(String file) {
			return getDefinition().reporting(file);
		}

		/**
		 * @param document The document to get the models for
		 * @return This element's models for the given document
		 */
		default InterpretedModelSet getModels(String document) {
			InterpretedExpressoEnv env = getExpressoEnv(document);
			return env == null ? null : env.getModels();
		}

		/** @return The location of the document that declared this element */
		default String getDocument() {
			return getDefinition().getDocument();
		}

		/** @return The expresso environment for this element and the document that declared it */
		default InterpretedExpressoEnv getDefaultEnv() {
			return getExpressoEnv(getDocument());
		}

		/**
		 * @param document The document to get the environment for
		 * @return The expresso environment for this element and the given document
		 */
		InterpretedExpressoEnv getExpressoEnv(String document);

		/**
		 * @param document The document to set the environment for
		 * @param env The expresso environment for this element and the given document
		 */
		void setExpressoEnv(String document, InterpretedExpressoEnv env);

		/** @return All expresso documents which expressions in this element may be from */
		Set<String> getExpressoDocuments();

		/** @return All expresso documents which declare local models for this element */
		Set<String> getLocalModelDocuments();

		/** @return All expresso environments usable by expressions in this element */
		List<InterpretedExpressoEnv> getExpressoEnvs();

		/** @return Instantiated model sets for all documents visible to this element */
		DocumentMap<ModelInstantiator> instantiateLocalModels();

		/**
		 * @param <I> The element interpretation type to cast this element to
		 * @param type The element interpretation type to cast this element to
		 * @param errorPosition The file position for the error if it must be thrown
		 * @return The representation of this element as the given type
		 * @throws ExpressoInterpretationException If this element has no such representation
		 */
		<I extends ExElement.Interpreted<?>> I as(Class<I> type, LocatedFilePosition errorPosition) throws ExpressoInterpretationException;

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
		 * @param expression The expression to get the environment for
		 * @return The expresso environment to use to interpret the expression
		 */
		default InterpretedExpressoEnv getEnvironmentFor(LocatedExpression expression) {
			if (expression == null)
				return null;
			InterpretedExpressoEnv env = getExpressoEnv(expression.getFilePosition().getFileLocation());
			if (env == null)
				env = getDefaultEnv();
			return env;
		}

		/**
		 * @param type The variable type to interpret
		 * @return The interpreted type
		 * @throws ExpressoInterpretationException If the variable type could not be interpreted for this element
		 */
		default TypeToken<?> interpretType(VariableType type) throws ExpressoInterpretationException {
			if (type == null)
				return null;
			else if (type.getContent() != null)
				return type.getType(getExpressoEnv(type.getContent().getFileLocation()));
			else
				return type.getType(getDefaultEnv());
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
			ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExConsumer<? super I, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

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
		<D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> void syncChildren(List<? extends D> definitions,
			List<I> existing, ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExConsumer<? super I, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

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
		public abstract class Abstract<E extends ExElement> extends AbstractIdentifiable implements Interpreted<E> {
			private final Def.Abstract<? super E> theDefinition;
			private Interpreted.Abstract<?> theParent;
			private QonfigPromise.Interpreted<?> thePromise;
			private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theAddOns;
			private final Set<ExAddOn.Interpreted<? super E, ?>> theAddOnSequence;
			private final SettableValue<Boolean> isDestroyed;
			private DocumentMap<EnvInterpWithInh> theExpressoEnvs;
			private Boolean isModelInstancePersistent;
			private boolean isInterpreting;
			private ListenerList<ExConsumer<? super E, ModelInstantiationException>> theOnInstantiations;

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

				for (ExAddOn.Def<? super E, ?> addOn : theDefinition.getAddOns()) {
					ExAddOn.Interpreted<? super E, ?> interp;
					interp = addOn.interpret(this);
					if (interp != null) {// It is allowed for add-on definitions not to produce interpretations
						theAddOnSequence.add(interp);
						theAddOns.put(interp.getClass(), interp);
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
			protected Object createIdentity() {
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
				if ((parent == null ? null : parent.getDefinition()) != theDefinition.getParentElement()) {
					BreakpointHere.breakpoint();
					throw new IllegalArgumentException(parent + " is not the parent of " + this);
				}
				theParent = (Interpreted.Abstract<?>) parent;
				addLogicalParent(theParent);
				return this;
			}

			@Override
			public Abstract<E> addLogicalParent(Interpreted<?> parent) {
				if (!(parent instanceof Abstract))
					return this;
				if (((Abstract<?>) parent).theExpressoEnvs != null) {
					if (theExpressoEnvs == null)
						theExpressoEnvs = ((Abstract<?>) parent).theExpressoEnvs.extend();
					else {
						for (Map.Entry<String, EnvInterpWithInh> ee : ((Abstract<?>) parent).theExpressoEnvs.entrySet()) {
							theExpressoEnvs.compute(ee.getKey(), (k, myEE) -> {
								if (myEE != null && myEE.owner == this)
									return myEE;
								else
									return ee.getValue();
							});
						}
					}
				}
				return this;
			}

			@Override
			public InterpretedExpressoEnv getExpressoEnv(String document) {
				EnvInterpWithInh ewi = theExpressoEnvs == null ? null : theExpressoEnvs.get(document);
				return ewi == null ? null : ewi.env;
			}

			@Override
			public void setExpressoEnv(String document, InterpretedExpressoEnv env) {
				if (theExpressoEnvs == null)
					theExpressoEnvs = new DocumentMap<>(null);
				EnvInterpWithInh current = theExpressoEnvs.get(document);
				if (current != null && current.env == env)
					return;
				theExpressoEnvs.put(document, new EnvInterpWithInh(env, this));
			}

			@Override
			public Set<String> getLocalModelDocuments() {
				Set<String> docs = new LinkedHashSet<>();
				for (Map.Entry<String, EnvInterpWithInh> env : theExpressoEnvs.entrySet()) {
					if (env.getValue().owner == this)
						docs.add(env.getKey());
				}
				return docs;
			}

			@Override
			public Set<String> getExpressoDocuments() {
				return theExpressoEnvs == null ? Collections.emptySet() : Collections.unmodifiableSet(theExpressoEnvs.keySet());
			}

			@Override
			public List<InterpretedExpressoEnv> getExpressoEnvs() {
				return theExpressoEnvs == null ? Collections.emptyList() : new MappedList<>(theExpressoEnvs.values(), ewi -> ewi.env);
			}

			@Override
			public DocumentMap<ModelInstantiator> instantiateLocalModels() {
				DocumentMap<ModelInstantiator> instantiated = new DocumentMap<>(null);
				if (theExpressoEnvs != null) {
					for (Map.Entry<String, EnvInterpWithInh> env : theExpressoEnvs.entrySet()) {
						if (env.getValue().owner == this)
							instantiated.put(env.getKey(), env.getValue().env.getModels().instantiate());
					}
				}
				return instantiated;
			}

			@Override
			public <I extends Interpreted<?>> I as(Class<I> type, LocatedFilePosition errorPosition)
				throws ExpressoInterpretationException {
				if (type.isInstance(this))
					return (I) this;
				else
					throw new ExpressoInterpretationException("This implementation requires an element interpretation of type "
						+ type.getName() + ", not " + getClass().getName(),
						errorPosition == null ? reporting().getPosition() : errorPosition, 0);
			}

			@Override
			public <AO extends ExAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
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
			public <D extends Def<?>, I extends Interpreted<?>> I syncChild(D definition, I existing,
				ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
				ExConsumer<? super I, ExpressoInterpretationException> update) throws ExpressoInterpretationException {
				if (existing != null && (definition == null || existing.getIdentity() != definition.getIdentity())) {
					existing.destroy();
					existing = null;
				}
				if (definition != null) {
					if (existing == null)
						existing = interpret.apply(definition);
					if (existing != null && update != null)
						update.accept(existing);
				}
				return existing;
			}

			@Override
			public <D extends Def<?>, I extends Interpreted<?>> void syncChildren(List<? extends D> definitions, List<I> existing,
				ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
				ExConsumer<? super I, ExpressoInterpretationException> update)
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
							I interpreted = interpret.apply(element.getRightValue());
							if (interpreted != null) {
								if (update != null)
									update.accept(interpreted);
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
							if(update!=null)
								update.accept(element.getLeftValue());
						} catch (RuntimeException | Error e) {
							element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
						}
						return element.preserve();
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
				if (thePromise != null)
					thePromise.destroy();
				if (!isDestroyed.get().booleanValue())
					isDestroyed.set(true, null);
			}

			/**
			 * Updates this element interpretation. Must be called at least once after the {@link #getDefinition() definition} produces this
			 * object.
			 *
			 * @throws ExpressoInterpretationException If any model values in this element or any of its content fail to be interpreted
			 */
			protected final void update() throws ExpressoInterpretationException {
				if (isInterpreting)
					return;
				isInterpreting = true;
				try {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOnSequence)
						addOn.preUpdate(this);

					if (theExpressoEnvs == null)
						theExpressoEnvs = new DocumentMap<>(null);

					if (thePromise != null && (getDefinition().getPromise() == null
						|| thePromise.getIdentity() != getDefinition().getPromise().getIdentity())) {
						thePromise.destroy();
						thePromise = null;
					}
					if (thePromise == null && getDefinition().getPromise() != null) {
						thePromise = getDefinition().getPromise().interpret();
					}
					if (thePromise != null) {
						thePromise.update(this);
						addLogicalParent(thePromise);
					}

					doUpdate();
					for (Map.Entry<String, EnvInterpWithInh> env : theExpressoEnvs.entrySet()) {
						// If our models are the same as the parent, then they're already interpreted or interpreting
						if (env.getValue().owner == this)
							env.getValue().env.getModels().interpret(env.getValue().env); // Interpret any remaining values
					}
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
			 * @throws ExpressoInterpretationException If this element cannot be interpreted
			 */
			protected void doUpdate() throws ExpressoInterpretationException {
				String doc = getDocument();
				setExpressoEnv(doc, getExpressoEnv(doc).forChild(theDefinition.getExpressoEnv(doc)));

				for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOnSequence)
					addOn.update(this);
			}

			/**
			 * Called after all add-ons have been updated for this element
			 *
			 * @throws ExpressoInterpretationException If an exception occurs performing post-update work on this element
			 */
			protected void postUpdate() throws ExpressoInterpretationException {
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}

			static class EnvInterpWithInh {
				final InterpretedExpressoEnv env;
				final ExElement.Interpreted<?> owner;

				EnvInterpWithInh(InterpretedExpressoEnv env, ExElement.Interpreted<?> owner) {
					this.env = env;
					this.owner = owner;
				}

				@Override
				public String toString() {
					return env.toString();
				}
			}
		}
	}

	/** @return The name of this element's Qonfig type */
	String getTypeName();

	/** @return The parent element */
	ExElement getParentElement();

	/** @return The location of the document that declared this element */
	String getDocument();

	/**
	 * @param <E> The element instance type to cast this element to
	 * @param type The element instance type to cast this element to
	 * @param errorPosition The file position for the error if it must be thrown
	 * @return The representation of this element as the given type
	 * @throws ModelInstantiationException If this element has no such representation
	 */
	<E extends ExElement> E as(Class<E> type, LocatedFilePosition errorPosition) throws ModelInstantiationException;

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
	 * @param document The document to get the instantiated models for
	 * @return The instantiator for this element's models and the given document
	 */
	ModelInstantiator getModels(String document);

	/**
	 * Enables expressions in this element to reference the environment of the given element
	 *
	 * @param parent The element to inherit the expresso environment from
	 */
	void addLogicalParent(ExElement parent);

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
	 * @param file The file that this element may belong to
	 * @return The reporting for this element, in the given file if this element has a representation in the file
	 */
	ErrorReporting reporting(String file);

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
	 * Instantiates or updates an instantiated child
	 *
	 * @param <I> The type of the interpretation of the child
	 * @param <E> The type of the instantiation of the child
	 * @param interpretation The child interpretation to interpret
	 * @param existing The existing instantiated child
	 * @param create The function to produce an instantiation for a child from an interpretation
	 * @param update The function to update an instantiated child
	 * @return The instantiated child
	 * @throws ModelInstantiationException If an error occurs instantiating or updating the child
	 */
	default <I extends ExElement.Interpreted<?>, E extends ExElement> E syncChild(I interpretation, E existing,
		ExFunction<? super I, ? extends E, ModelInstantiationException> create,
		ExTriConsumer<? super E, ? super I, ExElement, ModelInstantiationException> update) throws ModelInstantiationException {
		if (existing != null && (interpretation == null || existing.getIdentity() != interpretation.getIdentity())) {
			existing.destroy();
			existing = null;
		}
		if (interpretation == null)
			return null;
		try {
			if (existing == null)
				existing = create.apply(interpretation);
			update.accept(existing, interpretation, this);
		} catch (RuntimeException | Error e) {
			interpretation.reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
		}
		return existing;
	}

	/**
	 * Synchronizes a list of child interpretations and instantiations, ensuring each child in the interpretation list has its instantiation
	 * in the instantiation list, and that any instantiations without an interpretation are removed and disposed.
	 *
	 * @param <I> The type of the interpretation of the children
	 * @param <E> The type of the instantiation of the children
	 * @param definitions The child interpretations to instantiate
	 * @param existing The existing instantiated children
	 * @param interpret The function to produce an instantiation for a child from an interpretation
	 * @param update The function to update an instantiated child
	 * @throws ModelInstantiationException If an error occurs instantiating or updating any children
	 */
	default <I extends ExElement.Interpreted<?>, E extends ExElement> void syncChildren(List<? extends I> definitions, List<E> existing,
		ExFunction<? super I, ? extends E, ModelInstantiationException> interpret,
		ExTriConsumer<? super E, ? super I, ExElement, ModelInstantiationException> update) throws ModelInstantiationException {
		try (Transaction t = Transactable.lock(definitions, false, null); Transaction t2 = Transactable.lock(existing, true, null)) {
			CollectionUtils.synchronize(existing, definitions, (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
			.<ModelInstantiationException> simpleX(interpret)//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> {
				try {
					update.accept(el.getLeftValue(), el.getRightValue(), this);
				} catch (RuntimeException | Error e) {
					el.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.onCommonX(el -> {
				try {
					update.accept(el.getLeftValue(), el.getRightValue(), this);
				} catch (RuntimeException | Error e) {
					el.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.rightOrder()//
			.adjust();
		}
	}

	/**
	 * An abstract implementation of {@link ExElement}. {@link ExElement} is an interface to allow implementations to implement more than
	 * one type of element, but all implementations should probably extend or be backed by this.
	 */
	public abstract class Abstract extends AbstractIdentifiable implements ExElement, Cloneable {
		private ExElement theParent;
		private String theDocument;
		private DocumentMap<EnvInstWithInh> theLocalModels;
		private boolean isModelPersistent;
		private ClassMap<ExAddOn<?>> theAddOns;
		private Set<ExAddOn<?>> theAddOnSequence;
		private ErrorReporting theReporting;
		private String theTypeName;
		private SettableValue<Boolean> isDestroyed;
		private ModelSetInstance theUpdatingModels;

		private QonfigPromise thePromise;

		/** @param id The identification for this element */
		protected Abstract(Object id) {
			if (id == null)
				throw new NullPointerException();
			initIdentity(id);
			theAddOns = new ClassMap<>();
			theAddOnSequence = new LinkedHashSet<>();
			isDestroyed = SettableValue.<Boolean> build().withValue(false).build();
		}

		@Override
		public String getTypeName() {
			return theTypeName;
		}

		@Override
		protected Object createIdentity() {
			throw new IllegalStateException("Should have been initialized");
		}

		@Override
		public ExElement getParentElement() {
			return theParent;
		}

		@Override
		public String getDocument() {
			return theDocument;
		}

		@Override
		public <E extends ExElement> E as(Class<E> type, LocatedFilePosition errorPosition) throws ModelInstantiationException {
			if (type.isInstance(this))
				return (E) this;
			else
				throw new ModelInstantiationException(
					"This implementation requires an element implementation of type " + type.getName() + ", not " + getClass().getName(),
					errorPosition == null ? reporting().getPosition() : errorPosition, 0);
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
		public ModelInstantiator getModels(String document) {
			if (theLocalModels != null) {
				EnvInstWithInh models = theLocalModels.get(document);
				if (models != null)
					return models.models;
			}
			if (theParent != null)
				return theParent.getModels(document);
			else
				return null;
		}

		@Override
		public void addLogicalParent(ExElement parent) {
			if (!(parent instanceof Abstract))
				return;
			if (((Abstract) parent).theLocalModels != null) {
				if (theLocalModels == null)
					theLocalModels = ((Abstract) parent).theLocalModels.extend();
				else {
					for (Map.Entry<String, EnvInstWithInh> ee : ((Abstract) parent).theLocalModels.entrySet()) {
						theLocalModels.compute(ee.getKey(), (k, myEE) -> {
							if (myEE != null && myEE.owner == this)
								return myEE;
							else
								return ee.getValue();
						});
					}
				}
			}
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
		public ErrorReporting reporting(String file) {
			if (file == null || file.equals(theReporting.getPosition().getFileLocation()))
				return theReporting;
			else if (thePromise != null && file.equals(thePromise.reporting().getPosition().getFileLocation()))
				return thePromise.reporting();
			else
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
			if (getIdentity() != interpreted.getIdentity())
				throw new IllegalArgumentException("Wrong interpretation: " + interpreted + " for " + this);
			Interpreted.Abstract<ExElement> myInterpreted = (Interpreted.Abstract<ExElement>) interpreted;
			myInterpreted.instantiated(this);
			theReporting = interpreted.reporting();
			if (parent == this)
				throw new IllegalArgumentException("An element cannot be its own parent");
			theParent = parent;
			theDocument = interpreted.getDocument();
			addLogicalParent(parent);
			theTypeName = interpreted.getDefinition().getElement().getType().getName();
			if (theLocalModels == null)
				theLocalModels = new DocumentMap<>(null);

			// Create add-ons
			List<ExAddOn<?>> addOns = new ArrayList<>(theAddOnSequence);
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
			theAddOnSequence.addAll(addOns);

			if (thePromise != null
				&& (interpreted.getPromise() == null || thePromise.getIdentity() != interpreted.getPromise().getIdentity())) {
				thePromise.destroy();
				thePromise = null;
			}
			if (thePromise == null && interpreted.getPromise() != null) {
				thePromise = interpreted.getPromise().create(this);
			}
			if (thePromise != null) {
				thePromise.update(interpreted.getPromise());
				addLogicalParent(thePromise);
			}

			isModelPersistent = interpreted.isModelInstancePersistent();
			try {
				for (ExAddOn<?> addOn : theAddOnSequence)
					((ExAddOn<ExElement>) addOn).preUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);

				for (Map.Entry<String, Interpreted.Abstract.EnvInterpWithInh> env : myInterpreted.theExpressoEnvs.entrySet()) {
					if (env.getValue().owner == myInterpreted)
						theLocalModels.put(env.getKey(), new EnvInstWithInh(env.getValue().env.getModels().instantiate(), this));
				}
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
		}

		/**
		 * A utility for instantiating a (potentially null) expression
		 *
		 * @param <MV> The type of the model value to create
		 * @param expression The expression to instantiate
		 * @return The instantiated expression, or null if the expression is null
		 * @throws ModelInstantiationException If the expression throws an exception upon instantiation
		 */
		protected <MV> ModelValueInstantiator<MV> instantiate(InterpretedValueSynth<?, MV> expression) throws ModelInstantiationException {
			return expression == null ? null : expression.instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.preInstantiated();
			if (thePromise != null)
				thePromise.instantiated();
			if (theLocalModels != null) {
				for (EnvInstWithInh model : theLocalModels.values()) {
					if (model.owner == this)
						model.models.instantiate();
				}
			}
			for (ExAddOn<?> addOn : theAddOnSequence)
				addOn.instantiated();
		}

		@Override
		public final ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			try {
				for (ExAddOn<?> addOn : theAddOnSequence)
					addOn.preInstantiate();

				try {
					if (thePromise != null)
						models = thePromise.instantiate(models);
					theUpdatingModels = models = doInstantiate(models);

					for (ExAddOn<?> addOn : theAddOnSequence)
						addOn.postInstantiate(models);
					models = theUpdatingModels;
				} finally {
					if (!isModelPersistent)
						theUpdatingModels = null;
				}
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
			return models;
		}

		/**
		 * Performs implementation-specific instantiation for the element. Also instantiates add-ons and external content.
		 *
		 * @param myModels The model instance for this element to use for its values
		 * @return The possibly augmented models
		 * @throws ModelInstantiationException If this element could not be instantiated
		 */
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theUpdatingModels = myModels;
			if (theLocalModels != null && !theLocalModels.isEmpty()) {
				Observable<?> modelUntil = Observable.or(myModels.getUntil(), onDestroy());
				ModelSetInstanceBuilder builder = ObservableModelSet.createMultiModelInstanceBag(modelUntil)//
					.withAll(myModels);
				for (EnvInstWithInh model : theLocalModels.values()) {
					if (model.owner != this || builder.getTopLevelModels().contains(model.models.getIdentity()))
						continue;
					theUpdatingModels = myModels = model.models.createInstance(modelUntil)//
						.withAll(myModels)//
						.build();
					builder.withAll(myModels);
				}
				theUpdatingModels = myModels = builder.build();
			}
			for (ExAddOn<?> addOn : theAddOnSequence)
				theUpdatingModels = myModels = addOn.instantiate(myModels);
			return myModels;
		}

		/**
		 * A utility for evaluating a (potentially null) model instantiator
		 *
		 * @param <MV> The type of the model value to create
		 * @param instantiator The instantiator to get the model value for
		 * @param models The model instance set to get the value from
		 * @return The model value, or null if the instantiator was null
		 * @throws ModelInstantiationException If the instantiator throws it
		 */
		protected <MV> MV get(ModelValueInstantiator<MV> instantiator, ModelSetInstance models) throws ModelInstantiationException {
			return instantiator == null ? null : instantiator.get(models);
		}

		@Override
		public Abstract copy(ExElement parent) {
			Abstract copy = clone();
			copy.theParent = parent;
			copy.theAddOns = new ClassMap<>();
			copy.theAddOnSequence = new LinkedHashSet<>();
			copy.isDestroyed = SettableValue.<Boolean> build().withValue(false).build();

			copy.theLocalModels = parent == null ? new DocumentMap<>(null) : ((Abstract) parent).theLocalModels.extend();
			for (Map.Entry<String, EnvInstWithInh> env : theLocalModels.entrySet()) {
				if (env.getValue().owner == this)
					copy.theLocalModels.put(env.getKey(), new EnvInstWithInh(env.getValue().models, copy));
			}

			Map<ExAddOn<?>, ExAddOn<?>> addOns = new HashMap<>();
			for (ExAddOn<?> addOn : theAddOnSequence) {
				ExAddOn<?> addOnCopy = ((ExAddOn<ExElement>) addOn).copy(copy);
				addOns.put(addOn, addOnCopy);
				copy.theAddOnSequence.add(addOnCopy);
				copy.theAddOns.put(addOnCopy.getClass(), addOnCopy);
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
			if (!isDestroyed.get().booleanValue())
				isDestroyed.set(true, null);
		}

		static class EnvInstWithInh {
			final ModelInstantiator models;
			final ExElement owner;

			EnvInstWithInh(ModelInstantiator models, ExElement owner) {
				this.models = models;
				this.owner = owner;
			}
		}
	}

	/**
	 * A simple boolean comparison method for 2 strings which takes several steps to optimize for frequent calls in this particular case
	 *
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
		if (location1.hashCode() != location2.hashCode() || location1.length() != location2.length())
			return false;
		// This function also checks equality from the end, which is more likely to differ sooner for documents
		for (int i = location1.length() - 1; i >= 0; i--) {
			if (location1.charAt(i) != location2.charAt(i))
				return false;
		}
		return true;
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

		@Override
		public Void alias(String alias) {
			// Alias not supported for this constant
			return this;
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
	static <E extends ExElement, P extends Def<? extends E>, T> QonfigInterpreterCore.QonfigValueCreator<T> creator(Class<P> parentType,
		ExBiFunction<Def<? extends E>, QonfigElementOrAddOn, T, QonfigInterpretationException> creator) {
		return session -> {
			Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (parent != null) {
				// Check the type, but don't use the returned element def, as it may have different model visibility
				parent.as(parentType, session.reporting().getPosition());
			}
			return creator.apply((Def<? extends E>) parent, session.getFocusType());
		};
	}

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExElement}
	 *
	 * @param <T> The type of the element
	 * @param creator Function to create the element from the parent and type
	 * @return The Qonfig interpretation creator
	 */
	static <T> QonfigInterpreterCore.QonfigValueCreator<T> creator(
		ExBiFunction<Def<?>, QonfigElementOrAddOn, T, QonfigInterpretationException> creator) {
		return session -> creator.apply(session.as(ExpressoQIS.class).getElementRepresentation(), session.getFocusType());
	}

	/**
	 * Creates a Qonfig interpretation creator for an {@link ExElement}
	 *
	 * @param <T> The type of the element
	 * @param creator Function to create the element from the parent and type
	 * @return The Qonfig interpretation creator
	 */
	static <T> QonfigInterpreterCore.QonfigValueCreator<T> creator(
		ExTriFunction<Def<?>, QonfigElementOrAddOn, ? super ExpressoQIS, T, QonfigInterpretationException> creator) {
		return session -> {
			ExpressoQIS exSession = session.as(ExpressoQIS.class);
			return creator.apply(exSession.getElementRepresentation(), session.getFocusType(), exSession);
		};
	}
}

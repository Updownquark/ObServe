package org.observe.expresso.qonfig;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Def.Abstract.JoinedCollection;
import org.qommons.ClassMap;
import org.qommons.Identifiable;
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

		QonfigPromise.Def<?> getPromise();

		/**
		 * @param attrName The name of the attribute to get
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
		 * @return The given definition if it is up-to-date, or the newly interpreted one
		 * @throws QonfigInterpretationException If the definition could not be interpreted
		 * @throws IllegalArgumentException If no such child role exists
		 */
		<D extends ExElement.Def<?>> D syncChild(Class<? extends D> type, D def, ExpressoQIS session, String childName,
			ExBiConsumer<? super D, ExpressoQIS, QonfigInterpretationException> update)
				throws QonfigInterpretationException, IllegalArgumentException;

		default <T extends ExElement.Def<?>> void syncChildren(Class<T> defType, List<? extends T> defs, List<ExpressoQIS> sessions)
			throws QonfigInterpretationException {
			syncChildren(defType, defs, sessions, Def::update);
		}

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
			private Map<ElementTypeTraceability.QonfigElementKey, SingleTypeTraceability<? super E, ?, ?>> theTraceability;
			private CompiledExpressoEnv theExpressoEnv;
			private ErrorReporting theReporting;

			private QonfigPromise.Def<?> thePromise;
			private ExtElementView theExternalView;

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
				AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (ao == null && theExternalView != null)
					ao = (AO) theExternalView.theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				return ao;
			}

			@Override
			public Collection<ExAddOn.Def<? super E, ?>> getAddOns() {
				if (thePromise != null)
					return new JoinedCollection<>(theAddOns.getAllValues(), theExternalView.theExtAddOns.getAllValues());
				else
					return theAddOns.getAllValues();
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
					.simpleE(child -> child.interpret(defType, update))//
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
							SingleTypeTraceability.join(theTraceability, SingleTypeTraceability.traceabilityFor(addOn.getClass(),
								theElement.getDocument().getDocToolkit(), theReporting));
						}
					}
				}

				try {
					for (ExAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.preUpdate(session.asElement(addOn.getType()), this);
					if (theExternalView != null) {
						theExternalView.preUpdateAddOns(session.setExpressoEnv(thePromise.getExternalExpressoEnv()));
						session.setExpressoEnv(theExpressoEnv);
					}

					doUpdate(session);

					for (ExAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.postUpdate(session.asElement(addOn.getType()), this);
					if (theExternalView != null) {
						theExternalView.postUpdateAddOns(session.setExpressoEnv(thePromise.getExternalExpressoEnv()));
						session.setExpressoEnv(theExpressoEnv);
					}

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

			@Override
			public void setExpressoEnv(CompiledExpressoEnv env) {
				theExpressoEnv = env;
				if (thePromise != null)
					thePromise.setExpressoEnv(env);
			}

			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				if (thePromise != null) {
					theExternalView.update(session.setExpressoEnv(thePromise.getExternalExpressoEnv()));
					session.setExpressoEnv(theExpressoEnv);
				}
				for (ExAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
					addOn.update(session.asElement(addOn.getType()), this);
				if (theExternalView != null) {
					theExternalView.updateAddOns(session.setExpressoEnv(thePromise.getExternalExpressoEnv()));
					session.setExpressoEnv(theExpressoEnv);
				}
			}

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
					SingleTypeTraceability.join(theTraceability,
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

			static class ExtViewIdentity {
				private final Object theElementId;

				ExtViewIdentity(Object elementId) {
					theElementId = elementId;
				}

				@Override
				public String toString() {
					return theElementId + ".extView";
				}
			}

			private class ExtElementView implements ExElement.Def<ExElement> {
				private final ExtViewIdentity theViewId;
				private final ClassMap<ExAddOn.Def<? super E, ?>> theExtAddOns;
				private final ErrorReporting theExtReporting;

				ExtElementView() {
					theViewId = new ExtViewIdentity(Abstract.this.getIdentity());
					theExtAddOns = new ClassMap<>();
					theExtReporting = Abstract.this.reporting()//
						.at(theElement.getExternalContent().getFilePosition());
				}

				@Override
				public ExtViewIdentity getIdentity() {
					return theViewId;
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
					AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					if (ao == null && thePromise != null)
						ao = (AO) theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
					return ao;
				}

				@Override
				public Collection<ExAddOn.Def<? super ExElement, ?>> getAddOns() {
					return (Collection<ExAddOn.Def<? super ExElement, ?>>) (Collection<?>) new JoinedCollection<>(
						theExtAddOns.getAllValues(), theAddOns.getAllValues());
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

				void preUpdateAddOns(ExpressoQIS session) throws QonfigInterpretationException {
					for (ExAddOn.Def<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.preUpdate(session.asElement(addOn.getType()), Abstract.this);
				}

				void updateAddOns(ExpressoQIS session) throws QonfigInterpretationException {
					for (ExAddOn.Def<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.update(session.asElement(addOn.getType()), Abstract.this);
				}

				void postUpdateAddOns(ExpressoQIS session) throws QonfigInterpretationException {
					for (ExAddOn.Def<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.postUpdate(session.asElement(addOn.getType()), Abstract.this);
				}

				@Override
				public String toString() {
					return theViewId.toString();
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

		QonfigPromise.Interpreted<?> getPromise();

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

		<M, MV extends M> InterpretedValueSynth<M, MV> interpret(LocatedExpression expression, ModelInstanceType<M, MV> type)
			throws ExpressoInterpretationException;

		public <M, MV extends M, X1 extends Throwable, X2 extends Throwable> InterpretedValueSynth<M, MV> interpret(
			LocatedExpression expression, ModelInstanceType<M, MV> type,
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, X1, X2> handler)
				throws ExpressoInterpretationException, X1, X2;

		default <D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> I syncChild(D definition, I existing,
			ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
				throws ExpressoInterpretationException {
			return syncChild(definition, existing, (d, env) -> interpret.apply(d), update);
		}

		<D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> I syncChild(D definition, I existing,
			ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

		default <D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> void syncChildren(List<? extends D> definitions,
			List<I> existing, ExFunction<? super D, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update)
				throws ExpressoInterpretationException {
			syncChildren(definitions, existing, (d, env) -> interpret.apply(d), update);
		}

		<D extends ExElement.Def<?>, I extends ExElement.Interpreted<?>> void syncChildren(List<? extends D> definitions, List<I> existing,
			ExBiFunction<? super D, InterpretedExpressoEnv, ? extends I, ExpressoInterpretationException> interpret,
			ExBiConsumer<? super I, InterpretedExpressoEnv, ExpressoInterpretationException> update) throws ExpressoInterpretationException;

		Runnable onInstantiation(ExConsumer<? super E, ModelInstantiationException> task);

		ObservableValue<Boolean> isDestroyed();

		default Observable<ObservableValueEvent<Boolean>> destroyed() {
			return isDestroyed().changes().filterP(evt -> Boolean.TRUE.equals(evt.getNewValue())).take(1);
		}

		void destroy();

		/**
		 * An abstract implementation of {@link Interpreted}. {@link Interpreted} is an interface to allow implementations to implement more
		 * than one type of element, but all implementations should probably extend or be backed by this.
		 *
		 * @param <E> The type of element that this interpretation is for
		 */
		public abstract class Abstract<E extends ExElement> implements Interpreted<E> {
			private final Def.Abstract<? super E> theDefinition;
			private Interpreted<?> theParent;
			private QonfigPromise.Interpreted<?> thePromise;
			private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theAddOns;
			private final SettableValue<Boolean> isDestroyed;
			private InterpretedExpressoEnv theExpressoEnv;
			private InterpretedModelSet theUnifiedModel;
			private Boolean isModelInstancePersistent;
			private boolean isInterpreting;
			private ListenerList<ExConsumer<? super E, ModelInstantiationException>> theOnInstantiations;

			private ExtElementView theExternalView;

			/**
			 * @param definition The definition that is producing this interpretation
			 * @param parent The interpretation from the parent element
			 */
			protected Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = (Def.Abstract<? super E>) definition;
				if (parent != null)
					setParentElement(parent);
				theAddOns = new ClassMap<>();
				for (ExAddOn.Def<? super E, ?> addOn : theDefinition.theAddOns.getAllValues()) {
					ExAddOn.Interpreted<? super E, ?> interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					if (interp != null) // It is allowed for add-on definitions not to produce interpretations
						theAddOns.put(interp.getClass(), interp);
				}
				isDestroyed = SettableValue.build(boolean.class).withValue(false).build();

				if (theDefinition.theExternalView != null)
					theExternalView = new ExtElementView();
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
				AO ao = (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				if (ao == null && theExternalView != null)
					ao = (AO) theExternalView.theExtAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
				return ao;
			}

			@Override
			public Collection<ExAddOn.Interpreted<? super E, ?>> getAddOns() {
				if (thePromise != null)
					return new Def.Abstract.JoinedCollection<>(theAddOns.getAllValues(), theExternalView.theExtAddOns.getAllValues());
				else
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
			public <M, MV extends M> InterpretedValueSynth<M, MV> interpret(LocatedExpression expression, ModelInstanceType<M, MV> type)
				throws ExpressoInterpretationException {
				if (expression == null)
					return null;
				InterpretedExpressoEnv env;
				if (thePromise == null || documentsMatch(expression.getFilePosition().getFileLocation(),
					theDefinition.getPromise().getElement().getDocument().getLocation()))
					env = theExpressoEnv;
				else
					env = thePromise.getExternalExpressoEnv();
				return expression.interpret(type, env);
			}

			@Override
			public <M, MV extends M, X1 extends Throwable, X2 extends Throwable> InterpretedValueSynth<M, MV> interpret(
				LocatedExpression expression, ModelInstanceType<M, MV> type,
				ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, X1, X2> handler)
					throws ExpressoInterpretationException, X1, X2 {
				if (expression == null)
					return null;
				InterpretedExpressoEnv env;
				if (thePromise == null || documentsMatch(expression.getFilePosition().getFileLocation(),
					theDefinition.getPromise().getElement().getDocument().getLocation()))
					env = theExpressoEnv;
				else
					env = thePromise.getExternalExpressoEnv();
				return expression.interpret(type, env, handler);
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
				.adjust(new CollectionUtils.CollectionSynchronizerE<I, D, ExpressoInterpretationException>() {
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
			protected final void update(InterpretedExpressoEnv parentEnv) throws ExpressoInterpretationException {
				if (isInterpreting)
					return;
				isInterpreting = true;
				try {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.preUpdate(this);
					if (theExternalView != null)
						theExternalView.preUpdateAddOns();

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
					for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.postUpdate(this);
					if (theExternalView != null)
						theExternalView.postUpdateAddOns();
					postUpdate();
				} catch (RuntimeException | Error e) {
					reporting().error(e.getMessage(), e);
				} finally {
					isInterpreting = false;
				}
			}

			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				theExpressoEnv = expressoEnv;
				if (thePromise != null) {
					thePromise.update(theExpressoEnv, this);
					theExpressoEnv = thePromise.getExpressoEnv();
				}
				for (ExAddOn.Interpreted<? super E, ?> addOn : theAddOns.getAllValues())
					addOn.update(this);
				if (theExternalView != null)
					theExternalView.updateAddOns();
			}

			protected void postUpdate() throws ExpressoInterpretationException {}

			InterpretedModelSet getUnifiedModel() {
				if (thePromise == null)
					return theExpressoEnv.getModels();
				else if (theUnifiedModel == null) {
					ObservableModelSet.Builder modelBuilder = ObservableModelSet.build(toString() + ".unified",
						theExpressoEnv.getModels().getNameChecker());
					modelBuilder.withAll(theExpressoEnv.getModels());
					modelBuilder.withAll(thePromise.getExternalExpressoEnv().getModels());
					try {
						theUnifiedModel = modelBuilder.build().createInterpreted(theExpressoEnv);
						theUnifiedModel.interpret(theExpressoEnv);
					} catch (ExpressoInterpretationException e) {
						throw new IllegalStateException("This should not happen--should have been 100% interpreted already", e);
					}
				}
				return theUnifiedModel;
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}

			class ExtElementView implements Interpreted<ExElement> {
				private final Def.Abstract<?>.ExtElementView theExtDefinition;
				private final ClassMap<ExAddOn.Interpreted<? super E, ?>> theExtAddOns;

				ExtElementView() {
					theExtDefinition = ((Def.Abstract<?>) theDefinition).theExternalView;
					theExtAddOns = new ClassMap<>();

					for (ExAddOn.Def<? super E, ?> addOn : theExtDefinition.theExtAddOns.getAllValues()) {
						ExAddOn.Interpreted<? super E, ?> interp = (ExAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
						if (interp != null) // It is allowed for add-on definitions not to produce interpretations
							theExtAddOns.put(interp.getClass(), interp);
					}
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
					return (Collection<ExAddOn.Interpreted<? super ExElement, ?>>) (Collection<?>) new JoinedCollection<>(
						theExtAddOns.getAllValues(), theAddOns.getAllValues());
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
				public <M, MV extends M> InterpretedValueSynth<M, MV> interpret(LocatedExpression expression, ModelInstanceType<M, MV> type)
					throws ExpressoInterpretationException {
					return Abstract.this.interpret(expression, type);
				}

				@Override
				public <M, MV extends M, X1 extends Throwable, X2 extends Throwable> InterpretedValueSynth<M, MV> interpret(
					LocatedExpression expression, ModelInstanceType<M, MV> type,
					ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, X1, X2> handler)
						throws ExpressoInterpretationException, X1, X2 {
					return Abstract.this.interpret(expression, type, handler);
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

				void preUpdateAddOns() throws ExpressoInterpretationException {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.preUpdate(Abstract.this);
				}

				void updateAddOns() throws ExpressoInterpretationException {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.update(Abstract.this);
				}

				void postUpdateAddOns() throws ExpressoInterpretationException {
					for (ExAddOn.Interpreted<? super E, ?> addOn : theExtAddOns.getAllValues())
						addOn.postUpdate(Abstract.this);
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

	/**
	 * An abstract implementation of {@link ExElement}. {@link ExElement} is an interface to allow implementations to implement more than
	 * one type of element, but all implementations should probably extend or be backed by this.
	 */
	public abstract class Abstract implements ExElement, Cloneable {
		private final Object theId;
		private ExElement theParent;
		private ModelInstantiator theLocalModel;
		private ModelInstantiator theUnifiedModel;
		private boolean isModelPersistent;
		private ClassMap<ExAddOn<?>> theAddOns;
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
			if (theUnifiedModel != null)
				return theUnifiedModel;
			else if (theLocalModel != null)
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
			if (parent == this)
				throw new IllegalArgumentException("An element cannot be its own parent");
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

			Interpreted.Abstract<?> myInterpreted = (Interpreted.Abstract<?>) interpreted;
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
			if (interpreted.getPromise() != null)
				theUnifiedModel = myInterpreted.getUnifiedModel().instantiate();
			else
				theUnifiedModel = null;
			isModelPersistent = interpreted.isModelInstancePersistent();
			try {
				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).preUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
				if (theExternalView != null)
					theExternalView.preUpdateAddOns(myInterpreted.theExternalView);

				doUpdate(interpreted);

				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).postUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
				if (theExternalView != null)
					theExternalView.postUpdateAddOns(myInterpreted.theExternalView);
			} catch (RuntimeException | Error e) {
				reporting().error(e.getMessage(), e);
			}
		}

		protected void doUpdate(Interpreted<?> interpreted) {
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				((ExAddOn<ExElement>) addOn)
				.update(interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
			if (theExternalView != null)
				theExternalView.update(interpreted, theParent);
		}

		@Override
		public void instantiated() {
			if (theLocalModel != null)
				theLocalModel.instantiate();
			if (theUnifiedModel != null)
				theUnifiedModel.instantiate();
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				addOn.instantiated();
			if (theExternalView != null)
				theExternalView.instantiated();
		}

		@Override
		public final ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
			ModelSetInstance myModels = null;
			try {
				for (ExAddOn<?> addOn : theAddOns.getAllValues())
					addOn.preInstantiate();

				myModels = createElementModel(models);
				try {
					if (thePromise != null)
						thePromise.instantiate(models);
					doInstantiate(myModels);

					for (ExAddOn<?> addOn : theAddOns.getAllValues())
						addOn.postInstantiate(myModels);
					if (theExternalView != null)
						theExternalView.postInstantiateAddOns(myModels);
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
			if (theLocalModel == null && theUnifiedModel == null) {
				theUpdatingModels = parentModels;
				return theUpdatingModels;
			}

			ModelSetInstance localModels;
			Observable<?> modelUntil = Observable.or(parentModels.getUntil(), onDestroy());
			if (theLocalModel != null) {
				ObservableModelSet.ModelSetInstanceBuilder builder = theLocalModel.createInstance(modelUntil);
				if (theLocalModel.getInheritance().contains(parentModels.getModel().getIdentity())) {
					builder.withAll(parentModels);
				} else {
					// The parent models are unified, which is an instantiation construct.
					// The above statement would fail because this element's models were not constructed with unification in mind.
					// Therefore we have to dissect it and take what we need.
					for (ModelComponentId inh : theLocalModel.getInheritance()) {
						if (parentModels.getModel().getInheritance().contains(inh))
							builder.withAll(parentModels.getInherited(inh));
					}
				}
				localModels = builder.build();
			} else
				localModels = parentModels;

			if (theUnifiedModel != null) {
				ModelSetInstanceBuilder builder = theUnifiedModel.createInstance(modelUntil);
				builder.withAll(localModels);
				builder.withAll(thePromise.getExternalModels(parentModels, modelUntil));
				theUpdatingModels = builder.build();
			} else
				theUpdatingModels = localModels;
			return theUpdatingModels;
		}

		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			theUpdatingModels = myModels;
			for (ExAddOn<?> addOn : theAddOns.getAllValues())
				addOn.instantiate(myModels);
			if (theExternalView != null)
				theExternalView.instantiate(myModels);
		}

		@Override
		public Abstract copy(ExElement parent) {
			Abstract copy = clone();
			copy.theParent = parent;
			copy.theAddOns = new ClassMap<>();
			copy.isDestroyed = SettableValue.build(boolean.class).withValue(false).build();
			for (ExAddOn<?> addOn : theAddOns.getAllValues()) {
				ExAddOn<?> addOnCopy = ((ExAddOn<ExElement>) addOn).copy(copy);
				copy.theAddOns.put(addOnCopy.getClass(), addOnCopy);
			}
			if (thePromise != null)
				copy.thePromise = thePromise.copy(copy);

			if (theExternalView != null)
				copy.theExternalView = theExternalView.copy(this);

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

		private class ExtElementView implements ExElement, Cloneable {
			private final Object theExtId;
			private final ErrorReporting theExtReporting;
			private ClassMap<ExAddOn<?>> theExtAddOns;

			ExtElementView(Interpreted.Abstract<?>.ExtElementView interpreted) {
				theExtId = interpreted.getIdentity();
				theExtReporting = interpreted.reporting();
				theExtAddOns = new ClassMap<>();
			}

			ExtElementView(Abstract.ExtElementView toCopy) {
				theExtId = toCopy.getIdentity();
				theExtReporting = toCopy.reporting();
				theExtAddOns = new ClassMap<>();
			}

			@Override
			public Object getIdentity() {
				return theExtId;
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
			public void update(Interpreted<?> interpreted, ExElement parent) {
				Interpreted.Abstract<?> owner = (Interpreted.Abstract<?>) interpreted;
				Interpreted.Abstract<?>.ExtElementView myInterpreted = owner.theExternalView;
				// Create add-ons
				CollectionUtils
				.synchronize(new ArrayList<>(theExtAddOns.getAllValues()), new ArrayList<>(myInterpreted.theExtAddOns.getAllValues()),
					(inst, interp) -> inst.getInterpretationType() == interp.getClass())//
				.adjust(new CollectionUtils.CollectionSynchronizer<ExAddOn<?>, ExAddOn.Interpreted<?, ?>>() {
					@Override
					public boolean getOrder(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
						theExtAddOns.compute(element.getLeftValue().getClass(), __ -> null);
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
						ExAddOn<?> instance = ((ExAddOn.Interpreted<ExElement, ?>) element.getRightValue())
							.create(ExElement.Abstract.this);
						if (instance != null) {
							theExtAddOns.put(instance.getClass(), instance);
							return element.useValue(instance);
						} else
							return element.preserve();
					}

					@Override
					public ElementSyncAction common(ElementSyncInput<ExAddOn<?>, ExAddOn.Interpreted<?, ?>> element) {
						return element.preserve();
					}
				}, CollectionUtils.AdjustmentOrder.RightOrder);

				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).update(
						myInterpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);

				thePromise.update(owner.getPromise());
			}

			void preUpdateAddOns(Interpreted.Abstract<?>.ExtElementView interpreted) {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).preUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
			}

			void postUpdateAddOns(Interpreted.Abstract<?>.ExtElementView interpreted) {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).postUpdate(
						interpreted.getAddOn((Class<? extends ExAddOn.Interpreted<ExElement, ?>>) addOn.getInterpretationType()), this);
			}

			@Override
			public void instantiated() {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					addOn.instantiated();
				thePromise.instantiated();
			}

			void preInstantiateAddOns() {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).preInstantiate();
			}

			@Override
			public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).instantiate(models);
				return models;
			}

			void postInstantiateAddOns(ModelSetInstance models) throws ModelInstantiationException {
				for (ExAddOn<?> addOn : theExtAddOns.getAllValues())
					((ExAddOn<ExElement>) addOn).postInstantiate(models);
			}

			@Override
			public ExtElementView copy(ExElement element) {
				ExtElementView copy = ((Abstract) element).new ExtElementView(this);

				for (ExAddOn<?> addOn : theExtAddOns.getAllValues()) {
					ExAddOn<?> addOnCopy = ((ExAddOn<ExElement>) addOn).copy(copy);
					copy.theExtAddOns.put(addOnCopy.getClass(), addOnCopy);
				}
				return copy;
			}

			@Override
			public ObservableValue<Boolean> isDestroyed() {
				return Abstract.this.isDestroyed();
			}

			@Override
			public void destroy() {}
		}
	}

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

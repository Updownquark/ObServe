package org.observe.expresso.qonfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.QonfigElementKey;
import org.observe.expresso.qonfig.ExpressoExternalDocument.AttributeValueSatisfier;
import org.qommons.MultiInheritanceSet;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.PartialQonfigElement;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.Builder;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigElementView;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigPromiseDef;
import org.qommons.io.LocatedFilePosition;

/** A reference to an external expresso document that will be loaded and injected into the source document as content */
public class ExpressoExternalReference extends ExElement.Abstract implements QonfigPromise {
	/** The XML name of this element */
	public static final String EXT_REFERENCE = "external-reference";

	/**
	 * {@link ExpressoExternalReference} definition
	 *
	 * @param <P> The type of element to create
	 */
	@ExElementTraceable(toolkit = QonfigExternalDocument.QONFIG_REFERENCE_TK,
		qonfigType = EXT_REFERENCE,
		interpretation = Interpreted.class,
		instance = ExpressoExternalReference.class)
	public static class Def<P extends ExpressoExternalReference> extends ExElement.Def.Abstract<P> implements QonfigPromise.Def<P> {
		private ExElement.Def<?> theFulfilledContent;
		private ExpressoExternalDocument.Def<?> theExternalContent;
		private final BetterMultiMap<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> theChildren;

		/**
		 * @param parent The parent element in the source document
		 * @param qonfigType The Qonfig type of this element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theChildren = BetterHashMultiMap.<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> build().buildMultiMap();
		}

		/** @return The loaded external document whose content to inject into the source document */
		@QonfigAttributeGetter("ref")
		public QonfigDocument getReference() {
			return theExternalContent == null ? null : theExternalContent.getElement().getDocument();
		}

		/** @return The promised &lt;element-def> type of this promise To satisfy traceability */
		@QonfigAttributeGetter("promised")
		public QonfigElementDef getPromisedType() {
			return ((QonfigPromiseDef) getQonfigType()).getPromisedType();
		}

		/** @return The promised &lt;add-on> inheritance of this promise To satisfy traceability */
		@QonfigAttributeGetter("promised-inheritance")
		public MultiInheritanceSet<QonfigAddOn> getPromisedInheritance() {
			return ((QonfigPromiseDef) getQonfigType()).getPromisedInheritance();
		}

		@Override
		public ExElement.Def<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		/** @return The content to inject into the source document */
		public ExpressoExternalDocument.Def<?> getExternalContent() {
			return theExternalContent;
		}

		@Override
		public <D extends ExElement.Def<?>> D as(Class<D> type, LocatedFilePosition errorPosition) throws QonfigInterpretationException {
			return theFulfilledContent.as(type, errorPosition);
		}

		/**
		 * @return The child placeholders in the {@link #getExternalContent() external content} to be satisfied with children specified on
		 *         this element
		 */
		public BetterMultiMap<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> getChildren() {
			return BetterCollections.unmodifiableMultiMap(theChildren);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theFulfilledContent = content;

			update(session);
		}

		@Override
		protected Collection<QonfigAddOn> getElementInheritance() {
			// Only include the promise type's inheritance
			return getElement().getType().getInheritance();
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			QonfigDocument extContentDoc = theFulfilledContent.getElement().getExternalContent().getDocument();
			QonfigElement.Builder extContentBuilder = QonfigElement.buildRoot(false, //
				session.reporting().at(extContentDoc.getPartialRoot().getFilePosition()), extContentDoc,
				(QonfigElementDef) extContentDoc.getPartialRoot().getType(), extContentDoc.getPartialRoot().getDescription());
			buildExtContent(extContentBuilder, extContentDoc.getPartialRoot(),
				session.getType(ExpressoSessionImplV0_1.CORE, ExpressoExternalDocument.EXPRESSO_EXTERNAL_DOCUMENT).getChild("fulfillment"));
			QonfigElement extContentRoot = extContentBuilder.buildFull();
			ExpressoQIS extContentSession = session.interpretRoot(extContentRoot);
			String doc = getDocument();
			CompiledExpressoEnv parentEnv = session.getExpressoEnv(doc);
			extContentSession.setExpressoEnv(doc, parentEnv
				// Keep the non-structured parsers from the top level
				.withAllNonStructuredParsers(parentEnv).withOperators(parentEnv.getUnaryOperators().copy()//
					.build(), parentEnv.getBinaryOperators().copy().build()));
			if (theExternalContent == null || !ExElement.typesEqual(theExternalContent.getElement(), extContentDoc.getPartialRoot()))
				theExternalContent = extContentSession.interpret(ExpressoExternalDocument.Def.class);
			theExternalContent.update(extContentSession, this);
			setExpressoEnv(extContentDoc.getLocation(), theExternalContent.getExpressoEnv(extContentDoc.getLocation()));

			super.doUpdate(session);
		}

		private void buildExtContent(Builder builder, PartialQonfigElement element, QonfigChildDef fulfillmentRole) {
			// if (element.getParentRoles().contains(fulfillmentRole)) {
			// // The content may contain attributes specific to add-ons inherited by roles it fulfills
			// // The content as the fulfillment of the external content won't know of these roles
			// // theFulfilledContent.getElement().copy(builder.ignoreExtraAttributes(true));
			// theFulfilledContent.getElement().copy(builder, null, null, null);
			// } else {
			element.copyAttributes(builder);
			for (PartialQonfigElement child : element.getChildren()) {
				builder.withChild2(child.getParentRoles(), child.getType(), null, cb -> {
					buildExtContent(cb, child, fulfillmentRole);
				}, child.getFilePosition(), child.getDescription());
			}
			// }
		}

		@Override
		protected void postUpdate() throws QonfigInterpretationException {
			super.postUpdate();
			// Find our children
			theChildren.clear();
			findChildren(getFulfilledContent());

			// Set up traceability
			Map<QonfigElementKey, ElementTypeTraceability.SingleTypeTraceabilityBuilder<?, ?, ?>> builders = new LinkedHashMap<>();
			for (Map.Entry<QonfigAttributeDef.Declared, AttributeValueSatisfier> attr : theExternalContent.getAttributeValues()
				.entrySet()) {
				QonfigElementKey key = new QonfigElementKey(attr.getKey().getOwner());
				ElementTypeTraceability.SingleTypeTraceability<?, ?, ?> traceability = getTraceability().get(key);
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<?, ?, ?> builder = builders.get(key);
				if (builder == null) {
					builder = traceability == null //
						? ElementTypeTraceability.build(key.toolkitName, key.toolkitMajorVersion, key.toolkitMinorVersion, key.typeName)//
							: traceability.copy();
					builders.put(key, builder);
				}
				builder.withAttribute(attr.getKey().getName(), __ -> attr.getValue().getValue(),
					interp -> ((ExpressoExternalDocument.Interpreted<?>) interp).getExternalAttribute(attr.getKey()));
			}
			for (QonfigChildDef.Declared childDef : theChildren.keySet()) {
				QonfigElementKey key = new QonfigElementKey(childDef.getOwner());
				ElementTypeTraceability.SingleTypeTraceability<?, ?, ?> traceability = getTraceability().get(key);
				ElementTypeTraceability.SingleTypeTraceabilityBuilder<?, ?, ?> builder = builders.get(key);
				if (builder == null) {
					builder = traceability == null //
						? ElementTypeTraceability.build(key.toolkitName, key.toolkitMajorVersion, key.toolkitMinorVersion, key.typeName)//
							: traceability.copy();
					builders.put(key, builder);
				}
				builder.withChild(childDef.getName(), __ -> (BetterList<ExpressoChildPlaceholder.Def<?>>) theChildren.get(childDef),
					interp -> {
						return ((ExpressoExternalDocument.Interpreted<?>) interp).getChildren(childDef);
					}, inst -> {
						return ((ExpressoExternalDocument) inst).getChildren(childDef);
					});
			}
			for (Map.Entry<QonfigElementKey, ElementTypeTraceability.SingleTypeTraceabilityBuilder<?, ?, ?>> builder : builders.entrySet())
				((Map<QonfigElementKey, ElementTypeTraceability.SingleTypeTraceability<?, ?, ?>>) (Map<?, ?>) getTraceability())
				.put(builder.getKey(), builder.getValue().build());
		}

		private void findChildren(ExElement.Def<?> content) {
			if (content.getPromise() instanceof ExpressoChildPlaceholder.Def) {
				ExpressoChildPlaceholder.Def<?> child = (ExpressoChildPlaceholder.Def<?>) content.getPromise();
				if (child.getDocumentParent() == getFulfilledContent()) {
					QonfigChildDef childDef = QonfigElementView.of(getElement()).children().getDefinition(child.getRefRoleName());
					child.setRefRole(childDef);
					theChildren.add(childDef.getDeclared(), child);
				}
			}
			for (ExElement.Def<?> child : content.getAllDefChildren())
				findChildren(child);
		}

		@Override
		public Interpreted<? extends P> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	/**
	 * {@link ExpressoExternalReference} interpretation
	 *
	 * @param <P> The type of element to create
	 */
	public static class Interpreted<P extends ExpressoExternalReference> extends ExElement.Interpreted.Abstract<P>
	implements QonfigPromise.Interpreted<P> {
		private ExElement.Interpreted<?> theFulfilledContent;
		private ExpressoExternalDocument.Interpreted<?> theExternalContent;

		Interpreted(Def<? super P> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super P> getDefinition() {
			return (Def<? super P>) super.getDefinition();
		}

		@Override
		public ExElement.Interpreted<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		/** @return The interpreted content to inject into the source document */
		public ExpressoExternalDocument.Interpreted<?> getExternalContent() {
			return theExternalContent;
		}

		@Override
		public <I extends ExElement.Interpreted<?>> I as(Class<I> type, LocatedFilePosition errorPosition)
			throws ExpressoInterpretationException {
			return theFulfilledContent.as(type, errorPosition);
		}

		@Override
		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theFulfilledContent = content;
			addLogicalParent(theFulfilledContent);

			theExternalContent = syncChild(getDefinition().getExternalContent(), theExternalContent, ec -> ec.interpret(),
				ec -> ec.update(this));
			addLogicalParent(theExternalContent);

			super.update();
		}

		@Override
		public QonfigPromise create(ExElement content) {
			return new ExpressoExternalReference(getIdentity(), content);
		}
	}

	private ExElement theFulfilledContent;
	private ExpressoExternalDocument theExternalContent;

	ExpressoExternalReference(Object id, ExElement fulfilledContent) {
		super(id);
		theFulfilledContent = fulfilledContent;
	}

	@Override
	public <E extends ExElement> E as(Class<E> type, LocatedFilePosition errorPosition) throws ModelInstantiationException {
		return theFulfilledContent.as(type, errorPosition);
	}

	@Override
	public void update(QonfigPromise.Interpreted<?> interpreted) throws ModelInstantiationException {
		update(interpreted, null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		addLogicalParent(theFulfilledContent);

		ExpressoExternalReference.Interpreted<?> myInterpreted = (ExpressoExternalReference.Interpreted<?>) interpreted;
		theExternalContent = syncChild(myInterpreted.getExternalContent(), theExternalContent, ec -> ec.create(theFulfilledContent),
			(ec, interp, parent) -> ec.update(interp, null));
		addLogicalParent(theExternalContent);

		super.doUpdate(interpreted);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theExternalContent.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		return ObservableModelSet.createMultiModelInstanceBag(myModels.getUntil())//
			.withAll(myModels)//
			.withAll(theExternalContent.instantiate(myModels))//
			.build();
	}

	@Override
	public ExpressoExternalReference copy(ExElement content) {
		ExpressoExternalReference copy = (ExpressoExternalReference) super.copy(null);
		copy.theFulfilledContent = content;
		copy.theExternalContent = copy.theExternalContent.copy(content);
		return copy;
	}
}

package org.observe.expresso.qonfig;

import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.qonfig.ElementTypeTraceability.QonfigElementKey;
import org.observe.expresso.qonfig.ExpressoExternalContent.AttributeValueSatisfier;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.PartialQonfigElement;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.Builder;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigElementView;
import org.qommons.config.QonfigInterpretationException;

public class ExpressoExternalReference extends ExElement.Abstract implements QonfigPromise {
	public static final String QONFIG_REFERENCE_TK = "Qonfig-Reference v0.1";
	public static final String EXT_REFERENCE = "external-reference";

	@ExElementTraceable(toolkit = QONFIG_REFERENCE_TK,
		qonfigType = EXT_REFERENCE,
		interpretation = Interpreted.class,
		instance = ExpressoExternalReference.class)
	public static class Def<P extends ExpressoExternalReference> extends ExElement.Def.Abstract<P> implements QonfigPromise.Def<P> {
		private ExElement.Def<?> theFulfilledContent;
		private ExpressoExternalContent.Def<?> theExternalContent;
		private final BetterMultiMap<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> theChildren;
		private CompiledExpressoEnv theExtExpressoEnv;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theChildren = BetterHashMultiMap.<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> build().buildMultiMap();
		}

		@QonfigAttributeGetter("ref")
		public QonfigDocument getReference() {
			return theExternalContent == null ? null : theExternalContent.getElement().getDocument();
		}

		@Override
		public ExElement.Def<?> getFulfilledContent() {
			return theFulfilledContent;
		}

		public ExpressoExternalContent.Def<?> getExternalContent() {
			return theExternalContent;
		}

		public BetterMultiMap<QonfigChildDef.Declared, ExpressoChildPlaceholder.Def<?>> getChildren() {
			return BetterCollections.unmodifiableMultiMap(theChildren);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theFulfilledContent = content;

			update(session);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			QonfigDocument extContentDoc = theFulfilledContent.getElement().getExternalContent().getDocument();
			QonfigElement.Builder extContentBuilder = QonfigElement.buildRoot(false, session.reporting(), extContentDoc,
				(QonfigElementDef) extContentDoc.getPartialRoot().getType(), extContentDoc.getPartialRoot().getDescription());
			buildExtContent(extContentBuilder, extContentDoc.getPartialRoot(),
				session.getType(ExpressoSessionImplV0_1.CORE, ExpressoExternalContent.EXPRESSO_EXTERNAL_CONTENT).getChild("fulfillment"));
			QonfigElement extContentRoot = extContentBuilder.buildFull();
			ExpressoQIS extContentSession = session.interpretRoot(extContentRoot).setExpressoEnv(CompiledExpressoEnv.STANDARD_JAVA);
			if (theExternalContent == null || !ExElement.typesEqual(theExternalContent.getElement(), extContentDoc.getPartialRoot()))
				theExternalContent = extContentSession.interpret(ExpressoExternalContent.Def.class);
			theExternalContent.update(extContentSession, theFulfilledContent);
			theExtExpressoEnv = theExternalContent.getExpressoEnv();

			super.doUpdate(session);
		}

		private void buildExtContent(Builder builder, PartialQonfigElement element, QonfigChildDef fulfillmentRole) {
			if (element.getParentRoles().contains(fulfillmentRole)) {
				// The content may contain attributes specific to add-ons inherited by roles it fulfills
				// The content as the fulfillment of the external content won't know of these roles
				theFulfilledContent.getElement().copy(builder.ignoreExtraAttributes(true));
			} else {
				element.copyAttributes(builder);
				for (PartialQonfigElement child : element.getChildren()) {
					builder.withChild2(child.getParentRoles(), child.getType(), cb -> {
						buildExtContent(cb, child, fulfillmentRole);
					}, element.getFilePosition(), element.getDescription());
				}
			}
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
					interp -> ((ExpressoExternalContent.Interpreted<?>) interp).getExternalAttribute(attr.getKey()));
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
						return ((ExpressoExternalContent.Interpreted<?>) interp).getChildren(childDef);
					}, inst -> {
						return ((ExpressoExternalContent) inst).getChildren(childDef);
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
		public CompiledExpressoEnv getExternalExpressoEnv() {
			return theExtExpressoEnv;
		}

		@Override
		public void setExternalExpressoEnv(CompiledExpressoEnv env) {
			theExtExpressoEnv = env;
		}

		@Override
		public Interpreted<? extends P> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	public static class Interpreted<P extends ExpressoExternalReference> extends ExElement.Interpreted.Abstract<P>
	implements QonfigPromise.Interpreted<P> {
		private ExElement.Interpreted<?> theFulfilledContent;
		private ExpressoExternalContent.Interpreted<?> theExternalContent;
		private InterpretedExpressoEnv theExtExpressoEnv;

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

		public ExpressoExternalContent.Interpreted<?> getExternalContent() {
			return theExternalContent;
		}

		@Override
		public InterpretedExpressoEnv getExternalExpressoEnv() {
			return theExtExpressoEnv;
		}

		@Override
		public void setExternalExpressoEnv(InterpretedExpressoEnv env) {
			theExtExpressoEnv = env;
		}

		@Override
		public void update(InterpretedExpressoEnv env, ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theFulfilledContent = content;

			if (theExternalContent == null || theExternalContent.getIdentity() != getDefinition().getExternalContent().getIdentity()) {
				if (theExternalContent != null)
					theExternalContent.destroy();
				theExternalContent = getDefinition().getExternalContent().interpret();
			}
			theExternalContent.update(content);
			theExtExpressoEnv = theExternalContent.getExpressoEnv().forChild(getDefinition().getExternalExpressoEnv());

			super.update(env);
		}

		@Override
		public QonfigPromise create(ExElement content) {
			return new ExpressoExternalReference(getIdentity(), content);
		}
	}

	private ModelInstantiator theExtModels;
	private ExElement theFulfilledContent;
	private ExpressoExternalContent theExternalContent;

	ExpressoExternalReference(Object id, ExElement fulfilledContent) {
		super(id);
		theFulfilledContent = fulfilledContent;
	}

	@Override
	public void update(QonfigPromise.Interpreted<?> interpreted) throws ModelInstantiationException {
		update(interpreted, null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		ExpressoExternalReference.Interpreted<?> myInterpreted = (ExpressoExternalReference.Interpreted<?>) interpreted;
		if (theExternalContent != null && theExternalContent.getIdentity() != myInterpreted.getIdentity()) {
			theExternalContent.destroy();
			theExternalContent = null;
		}
		if (theExternalContent == null)
			theExternalContent = myInterpreted.getExternalContent().create(theFulfilledContent);
		theExternalContent.update(myInterpreted.getExternalContent(), null);
		theExtModels = myInterpreted.getExternalExpressoEnv().getModels().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theExternalContent.instantiated();
		theExtModels.instantiate();
	}

	@Override
	public ModelInstantiator getExtModels() {
		return theExtModels;
	}

	@Override
	protected void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {
		super.addRuntimeModels(builder, elementModels);
		theExternalContent.addRuntimeModels(builder, elementModels);
		ModelSetInstance extBuilder = theExtModels.createInstance(builder.getUntil())//
			.withAll(builder)//
			.build();
		builder.withAll(extBuilder);
	}

	@Override
	public ExpressoExternalReference copy(ExElement content) {
		ExpressoExternalReference copy = (ExpressoExternalReference) super.copy(null);
		copy.theFulfilledContent = content;
		copy.theExternalContent = copy.theExternalContent.copy(content);
		return copy;
	}
}

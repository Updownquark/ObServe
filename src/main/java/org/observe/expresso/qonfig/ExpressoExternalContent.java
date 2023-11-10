package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.Builder;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecificationType;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ExpressoExternalContent extends QonfigExternalContent {
	public static final String EXPRESSO_EXTERNAL_CONTENT = "expresso-external-content";
	private static final String CONTENT_ENV_PROPERTY = "Expresso$Content";

	public interface AttributeValueSatisfier {
		Object getValue();

		<M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
			throws QonfigInterpretationException;

		public static class Literal<T> implements AttributeValueSatisfier {
			private final String theValueType;
			private final CompiledModelValue<SettableValue<?>> theModelValue;

			public Literal(String valueType, TypeToken<T> type, T value, String text) {
				theValueType = valueType;
				theModelValue = CompiledModelValue.literal(type, value, text);
			}

			@Override
			public Object getValue() {
				return theModelValue;
			}

			@Override
			public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
				throws QonfigInterpretationException {
				if (extValue.getModelType(env) != ModelTypes.Value)
					throw new QonfigInterpretationException("Only value-type model values are supported for attribute type " + theValueType,
						extValue.reporting().getPosition(), 0);
				return (CompiledModelValue<M>) theModelValue;
			}

			@Override
			public String toString() {
				return theModelValue.toString();
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = EXPRESSO_EXTERNAL_CONTENT,
		interpretation = Interpreted.class,
		instance = ExpressoExternalContent.class)
	public static class Def<C extends ExpressoExternalContent> extends QonfigExternalContent.Def<C> {
		private ObservableModelSet.Built theContentModelModel;
		private ModelComponentId theContentModelVariable;

		private final Map<QonfigAttributeDef.Declared, AttributeValueSatisfier> theAttributeValues;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theAttributeValues = new LinkedHashMap<>();
		}

		public ExpressoHeadSection.Def getHead() {
			return getAddOn(ExpressoDocument.Def.class).getHead();
		}

		public ObservableModelSet.Built getContentModelModel() {
			return theContentModelModel;
		}

		public ModelComponentId getContentModelVariable() {
			return theContentModelVariable;
		}

		public Map<QonfigAttributeDef.Declared, AttributeValueSatisfier> getAttributeValues() {
			return Collections.unmodifiableMap(theAttributeValues);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			theContentModelModel = ObservableModelSet.build(CONTENT_ENV_PROPERTY, ObservableModelSet.JAVA_NAME_CHECKER)//
				.with(CONTENT_ENV_PROPERTY, ModelTypes.Value.forType(ModelSetInstance.class),
					ModelValueInstantiator.of(msi -> new ContentModelHolder()), null)//
				.withAll(CompiledExpressoEnv.STANDARD_JAVA.getModels())//
				.build();
			theContentModelVariable = theContentModelModel.getLocalComponent(CONTENT_ENV_PROPERTY).getIdentity();

			theAttributeValues.clear();
			initFulfills(session);
			for (QonfigAttributeDef attr : getFulfills().getAllAttributes().values()) {
				if (getContent().getPromise() != null) {
					Object value = getContent().getPromise().getAttribute(attr);
					if (value instanceof AttributeValueSatisfier)
						theAttributeValues.put(attr.getDeclared(), (AttributeValueSatisfier) value);
					else if (value instanceof CompiledModelValue) {
						CompiledModelValue<?> cmv = (CompiledModelValue<?>) value;
						theAttributeValues.put(attr.getDeclared(), new AttributeValueSatisfier() {
							@Override
							public Object getValue() {
								return cmv;
							}

							@Override
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
								throws QonfigInterpretationException {
								ModelType<?> cmvMT;
								try {
									cmvMT = cmv.getModelType(env);
								} catch (ExpressoCompilationException e) {
									throw new QonfigInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
								}
								if (extValue.getModelType(env) != cmvMT)
									throw new QonfigInterpretationException(
										"Only " + cmvMT + "-type model values are supported for this attribute",
										extValue.reporting().getPosition(), 0);
								return (CompiledModelValue<M>) cmv;
							}
						});
					} else if (value instanceof CompiledExpression) {
						CompiledExpression expression = (CompiledExpression) value;
						theAttributeValues.put(attr.getDeclared(), new AttributeValueSatisfier() {
							@Override
							public Object getValue() {
								return expression;
							}

							@Override
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
								throws QonfigInterpretationException {
								return new PlaceholderExtValue<>(extValue, expression, theContentModelVariable);
							}
						});
					} else if (value != null) {
						theAttributeValues.put(attr.getDeclared(), new AttributeValueSatisfier.Literal<>(value.getClass().getName(),
							TypeTokens.get().of((Class<Object>) value.getClass()), value, value.toString()));
					} else
						theAttributeValues.put(attr.getDeclared(), //
							populateAttributeValue(attr, getContent().getElement().getPromise(),
								getContent().getElement().getPromise().getAttributes().get(attr.getDeclared()), session));
				} else
					theAttributeValues.put(attr.getDeclared(), //
						populateAttributeValue(attr, getContent().getElement().getPromise(),
							getContent().getElement().getPromise().getAttributes().get(attr.getDeclared()), session));
			}

			setExpressoEnv(session.getExpressoEnv().with(theContentModelModel));
			session.setExpressoEnv(getExpressoEnv());
			session.put(ExtModelValueElement.EXT_MODEL_VALUE_HANDLER, new ExtModelValueElement.ExtModelValueHandler() {
				@Override
				public <M> void handleExtValue(ExtModelValueElement.Def<M> value, Builder builder, ExpressoQIS valueSession)
					throws QonfigInterpretationException {
					populateExtModelValue(value, builder, valueSession);
				}
			});

			super.doUpdate(session);
		}

		protected <M> void populateExtModelValue(ExtModelValueElement.Def<M> extValue, ObservableModelSet.Builder builder,
			ExpressoQIS valueSession) throws QonfigInterpretationException {
			QonfigAttributeDef attr = extValue.getAddOn(AttributeBackedModelValue.Def.class).getSourceAttribute();
			String name = extValue.getAddOn(ExNamed.Def.class).getName();
			QonfigValue value = getContent().getElement().getPromise().getAttributes().get(attr.getDeclared());
			LocatedFilePosition source;
			if (value != null)
				source = LocatedFilePosition.of(value.fileLocation, value.position.getPosition(0));
			else
				source = valueSession.getElement().getPositionInFile();
			AttributeValueSatisfier satisfier = theAttributeValues.get(attr.getDeclared());
			if (satisfier != null)
				builder.withMaker(name, satisfier.satisfy(extValue, getExpressoEnv()), source);
		}

		protected AttributeValueSatisfier populateAttributeValue(QonfigAttributeDef attr, QonfigElement element, QonfigValue value,
			ExpressoQIS session) throws QonfigInterpretationException {
			if (attr.getType() instanceof QonfigValueType.Custom) {
				if (value == null)
					reporting().error(attr.getType().getName() + "-typed attributes must be required to be supported model values");
				else if (attr.getSpecification() != SpecificationType.Required)
					reporting().warn(attr.getType().getName() + "-typed attributes must be required to be supported model values");
				else {
					QonfigValueType.Custom custom = (QonfigValueType.Custom) attr.getType();
					if (custom.getCustomType() instanceof ExpressionValueType) {
						CompiledExpression expression;
						try {
							expression = new CompiledExpression(//
								session.getExpressoParser().parse(((QonfigExpression) value.value).text), element,
								LocatedPositionedContent.of(value.fileLocation, value.position), getContent()::getExpressoEnv);
						} catch (ExpressoParseException e) {
							throw new QonfigInterpretationException(e.getMessage(),
								LocatedFilePosition.of(value.fileLocation, value.position.getPosition(e.getErrorOffset())),
								e.getErrorLength());
						}
						return new AttributeValueSatisfier() {
							@Override
							public Object getValue() {
								return expression;
							}

							@Override
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
								throws QonfigInterpretationException {
								return new PlaceholderExtValue<>(extValue, expression, theContentModelVariable);
							}
						};
					} else {
						return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
							TypeTokens.get().of((Class<Object>) value.value.getClass()), value.value, value.text);
					}
				}
			} else if (value != null && value.value != null) {
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
					TypeTokens.get().of((Class<Object>) value.value.getClass()), value.value, value.text);
			} else if (attr.getType() == QonfigValueType.STRING)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(), TypeTokens.get().STRING, null, null);
			else if (attr.getType() == QonfigValueType.INT)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(), TypeTokens.get().INT, 0, null);
			else if (attr.getType() == QonfigValueType.BOOLEAN)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(), TypeTokens.get().BOOLEAN, false, null);
			else
				reporting().error(attr.getType().getName() + "-typed attributes must be required to be supported model values");
			return null;
		}

		@Override
		public Interpreted<? extends C> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	public static class Interpreted<C extends ExpressoExternalContent> extends QonfigExternalContent.Interpreted<C> {
		private InterpretedModelSet theContentModelModel;

		Interpreted(Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		public InterpretedModelSet getContentModelModel() {
			return theContentModelModel;
		}

		public Object getExternalAttribute(QonfigAttributeDef.Declared attribute) {
			// TODO
			return null;
		}

		public List<ExpressoChildPlaceholder.Interpreted<?>> getChildren(QonfigChildDef.Declared child) {
			// TODO
			return Collections.emptyList();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theContentModelModel = getDefinition().getContentModelModel().createInterpreted(env);
			theContentModelModel.interpret(env);

			env.put(CONTENT_ENV_PROPERTY, getContent().getExpressoEnv());
			super.doUpdate(env);
		}

		public ExpressoExternalContent create(ExElement content) {
			return new ExpressoExternalContent(getIdentity(), content);
		}
	}

	private ModelInstantiator theContentModelModel;
	private ModelComponentId theContentModelVariable;
	private ModelInstantiator theContentModels;

	ExpressoExternalContent(Object id, ExElement content) {
		super(id);
	}

	public List<ExpressoChildPlaceholder> getChildren(QonfigChildDef.Declared child) {
		// TODO
		return Collections.emptyList();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		ExpressoExternalContent.Interpreted<?> myInterpreted = (ExpressoExternalContent.Interpreted<?>) interpreted;
		theContentModelModel = myInterpreted.getContentModelModel().instantiate();
		theContentModelVariable = myInterpreted.getDefinition().getContentModelVariable();
		theContentModels = myInterpreted.getExpressoEnv().getModels().instantiate();

		super.doUpdate(interpreted);
	}

	@Override
	protected void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {
		ModelSetInstance standardJava = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels().createInstance(builder.getUntil())
			.build();
		ModelSetInstance contentModelModel = theContentModelModel.createInstance(builder.getUntil())//
			.withAll(standardJava)//
			.build();
		((ContentModelHolder) contentModelModel.get(theContentModelVariable)).setContentModels(elementModels);
		builder.withAll(contentModelModel);

		builder.withAll(theContentModels.createInstance(builder.getUntil())//
			.withAll(contentModelModel)//
			.build());

		super.addRuntimeModels(builder, elementModels);
	}

	@Override
	public ExpressoExternalContent copy(ExElement content) {
		return (ExpressoExternalContent) super.copy(null);
	}

	static class PlaceholderExtValue<M> implements CompiledModelValue<M> {
		private final ExtModelValueElement.Def<M> theSpec;
		private final CompiledExpression theExpression;
		private final ModelComponentId theContentModelVariable;

		PlaceholderExtValue(ExtModelValueElement.Def<M> spec, CompiledExpression expression, ModelComponentId contentModelVariable) {
			theSpec = spec;
			theExpression = expression;
			theContentModelVariable = contentModelVariable;
		}

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			return theSpec.getModelType();
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			InterpretedExpressoEnv contentEnv = env.get(CONTENT_ENV_PROPERTY, InterpretedExpressoEnv.class);
			if (contentEnv == null)
				throw new IllegalStateException("No " + CONTENT_ENV_PROPERTY + " found");
			InterpretedValueSynth<M, ?> attrValueSynth = theExpression.interpret(theSpec.getType(env), contentEnv);
			InterpretedValueSynth<?, ContentModelHolder> contentModel = (InterpretedValueSynth<?, ContentModelHolder>) env.getModels()
				.getComponent(theContentModelVariable);
			return of(attrValueSynth, contentModel);
		}

		private <M, MV extends M> InterpretedValueSynth<M, MV> of(InterpretedValueSynth<M, MV> attrValueSynth,
			InterpretedValueSynth<?, ContentModelHolder> contentModel) {
			return InterpretedValueSynth.of(attrValueSynth.getType(),
				() -> instantiate(attrValueSynth.instantiate(), contentModel.instantiate()), attrValueSynth, contentModel);
		}

		private <MV> ModelValueInstantiator<MV> instantiate(ModelValueInstantiator<MV> attrValue,
			ModelValueInstantiator<ContentModelHolder> contentModel) {
			return new ModelValueInstantiator<MV>() {
				@Override
				public void instantiate() {
					attrValue.instantiate();
					contentModel.instantiate();
				}

				@Override
				public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
					ModelSetInstance contentModelInstance = contentModel.get(models).get();
					return attrValue.get(contentModelInstance);
				}

				@Override
				public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
					return get(newModels);
				}
			};
		}

		@Override
		public String toString() {
			return theExpression.toString();
		}
	}

	static class ContentModelHolder implements SettableValue<ModelSetInstance> {
		private ModelSetInstance theContentModels;

		void setContentModels(ModelSetInstance contentModels) {
			theContentModels = contentModels;
		}

		@Override
		public ModelSetInstance get() {
			return theContentModels;
		}

		@Override
		public Observable<ObservableValueEvent<ModelSetInstance>> noInitChanges() {
			return Observable.empty();
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.baseId(CONTENT_ENV_PROPERTY, this);
		}

		@Override
		public TypeToken<ModelSetInstance> getType() {
			return TypeTokens.get().of(ModelSetInstance.class);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public <V extends ModelSetInstance> ModelSetInstance set(V value, Object cause)
			throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public <V extends ModelSetInstance> String isAcceptable(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}
	}
}

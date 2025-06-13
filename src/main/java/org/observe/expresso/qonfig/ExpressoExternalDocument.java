package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.observe.Observable;
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
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigExternalRefPromise;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecificationType;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/**
 * An external expresso document loaded by an {@link ExpressoExternalReference} to be injected into a source expresso document
 */
public class ExpressoExternalDocument extends QonfigExternalDocument {
	/** The XML name of this element */
	public static final String EXPRESSO_EXTERNAL_DOCUMENT = "expresso-external-document";
	private static final String REFERENCE_ENV_PROPERTY = "Expresso$Reference$Env";

	/** A satisfier for an externally-specified model value in external content */
	public interface AttributeValueSatisfier {
		/** @return The value of the attribute, for traceability */
		Object getValue();

		/**
		 * @param <M> The model type of the model value
		 * @param extValue The external model value definition
		 * @param env The expresso environment for interpreting model types of expressions
		 * @return The compiled model value to satisfy the external model value
		 * @throws QonfigInterpretationException If the external model value could not be satisfied
		 */
		<M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
			throws QonfigInterpretationException;

		/**
		 * An {@link AttributeValueSatisfier} satisfied by a literal value
		 *
		 * @param <T> The type of the value
		 */
		public static class Literal<T> implements AttributeValueSatisfier {
			private final String theValueType;
			private final CompiledModelValue<SettableValue<?>> theModelValue;

			/**
			 * @param valueType The name of the type of the value (for error messaging)
			 * @param type The type of the value
			 * @param value The value
			 * @param text The text representing the value (for error messaging and debugging)
			 */
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

	/**
	 * {@link ExpressoExternalDocument} definition
	 *
	 * @param <C> The sub-type of element to create
	 */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = EXPRESSO_EXTERNAL_DOCUMENT,
		interpretation = Interpreted.class,
		instance = ExpressoExternalDocument.class)
	public static class Def<C extends ExpressoExternalDocument> extends QonfigExternalDocument.Def<C> {
		// private ObservableModelSet.Built theContentModelModel;
		// private ModelComponentId theContentModelVariable;

		private final Map<QonfigAttributeDef.Declared, AttributeValueSatisfier> theAttributeValues;
		private ModelComponentId thePromiseModelVariable;

		/**
		 * @param parent The parent element in the external document
		 * @param qonfigType The Qonfig type of this element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theAttributeValues = new LinkedHashMap<>();
		}

		/** @return The head section in the external content */
		public ExpressoHeadSection.Def getHead() {
			return getAddOn(ExpressoDocument.Def.class).getHead();
		}

		// /** @return The expresso model for the external content */
		// public ObservableModelSet.Built getContentModelModel() {
		// return theContentModelModel;
		// }
		//
		// /**
		// * @return The model ID of the variable in which the {@link #getContentModelModel() external content's model} will be stored. This
		// * is to facilitate evaluation of expressions in external content
		// */
		// public ModelComponentId getContentModelVariable() {
		// return theContentModelVariable;
		// }

		/** @return The model ID of the value containing the model set of the reference document */
		public ModelComponentId getPromiseModelVariable() {
			return thePromiseModelVariable;
		}

		/** @return Satisfiers for attribute values declared as externally-satisfied values by this external content */
		public Map<QonfigAttributeDef.Declared, AttributeValueSatisfier> getAttributeValues() {
			return Collections.unmodifiableMap(theAttributeValues);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			String doc = getDocument();
			ObservableModelSet.Builder builder = ExFlexibleElementModelAddOn.augmentElementModel(session.getExpressoEnv(doc).getModels(),
				this);
			CompiledExpressoEnv docEnv = getExpressoEnv(doc);
			docEnv = docEnv.with(builder);
			setExpressoEnv(doc, docEnv);
			session.setExpressoEnv(doc, docEnv);
			builder.with(REFERENCE_ENV_PROPERTY, ModelTypes.Value.forType(ModelSetInstance.class),
				ModelValueInstantiator.of(msi -> SettableValue.<ModelSetInstance> create()), null);
			thePromiseModelVariable = builder.getComponentIfExists(REFERENCE_ENV_PROPERTY).getIdentity();

			// theContentModelModel = ObservableModelSet.build(REFERENCE_ENV_PROPERTY, ObservableModelSet.JAVA_NAME_CHECKER)//
			// .with(REFERENCE_ENV_PROPERTY, ModelTypes.Value.forType(ModelSetInstance.class),
			// ModelValueInstantiator.of(msi -> new ContentModelHolder()), null)//
			// .withAll(CompiledExpressoEnv.STANDARD_JAVA.getModels())//
			// .build();
			// theContentModelVariable = theContentModelModel.getLocalComponent(REFERENCE_ENV_PROPERTY).getIdentity();

			theAttributeValues.clear();
			initFulfills(session);
			for (QonfigAttributeDef attr : getFulfills().getAllAttributes().values()) {
				if (QonfigExternalRefPromise.REFERENCE_TOOLKIT.matches(attr.getDeclarer()))
					continue;
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
								return parallel((CompiledModelValue<M>) cmv, env, thePromiseModelVariable);
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
								return parallel(CompiledModelValue.of(attr.getName(), extValue.getModelType(), //
									iee -> expression.interpret(extValue.getType(iee), iee)), //
									env, thePromiseModelVariable);
								// return new PlaceholderExtValue<>(extValue, expression, theContentModelVariable);
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

			// setExpressoEnv(doc, session.getExpressoEnv(doc).with(theContentModelModel));
			session.setExpressoEnv(doc, getExpressoEnv(doc));
			session.put(ExtModelValueElement.EXT_MODEL_VALUE_HANDLER, new ExtModelValueElement.ExtModelValueHandler() {
				@Override
				public <M> void handleExtValue(ExtModelValueElement.Def<M> value, Builder builder2, ExpressoQIS valueSession)
					throws QonfigInterpretationException {
					populateExtModelValue(value, builder2, valueSession);
				}
			});

			super.doUpdate(session);
		}

		/**
		 * @param <M> The model type of the value
		 * @param extValue The external value declaration
		 * @param builder The model builder to populate
		 * @param valueSession The Expresso interpretation session to use for traceability
		 * @throws QonfigInterpretationException If the external value could not be interpreted
		 */
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
			if (satisfier != null) {
				String doc = getFulfilledPromise().getDocument();
				builder.withMaker(name, satisfier.satisfy(extValue, getExpressoEnv(doc)), source);
			}
		}

		/**
		 * @param attr The definition of the attribute to interpret
		 * @param element The promise element that loaded the external content
		 * @param value The value of the attribute from the loading document
		 * @param session The expresso interpretation session to use for parsing expressions
		 * @return A satisfier to satisfy an externally-specified model value using the value of the attribute
		 * @throws QonfigInterpretationException If the attribute could not be handled
		 */
		protected AttributeValueSatisfier populateAttributeValue(QonfigAttributeDef attr, QonfigElement element, QonfigValue value,
			ExpressoQIS session) throws QonfigInterpretationException {
			ErrorReporting reporting;
			String prefix;
			if (Objects.equals(element.getType().getLocatedPosition().getFileLocation(),
				attr.getDeclared().getLocatedPosition().getFileLocation())) {
				reporting = reporting().at(attr.getDeclared().getLocatedPosition());
				prefix = "";
			} else {
				reporting = reporting().at(element.getType().getLocatedPosition());
				prefix = attr.getOwner().getDeclarer().getName() + ":" + attr.getOwner().getName() + "." + attr.getName() + ": ";
			}
			if (attr.getType() instanceof QonfigValueType.Custom) {
				if (value == null) {
					if (attr.getSpecification() == SpecificationType.Forbidden)
						return null;
					reporting.error(
						prefix + attr.getType().getName() + "-typed attributes must be required or defaulted to be supported model values");
				} else if (attr.getSpecification() != SpecificationType.Required && attr.getDefaultValue() == null)
					reporting.warn(
						prefix + attr.getType().getName() + "-typed attributes must be required or defaulted to be supported model values");
				else {
					QonfigValueType.Custom custom = (QonfigValueType.Custom) attr.getType();
					if (custom.getCustomType() instanceof ExpressionValueType) {
						CompiledExpression expression;
						Supplier<CompiledExpressoEnv> env = () -> getContent().getExpressoEnv(getDocument());
						try {
							expression = new CompiledExpression(//
								session.getExpressoParser().parse(((QonfigExpression) value.value).text), element,
								LocatedPositionedContent.of(value.fileLocation, value.position), env);
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
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv attEnv)
								throws QonfigInterpretationException {
								return parallel(CompiledModelValue.of(attr.getName(), extValue.getModelType(), //
									iee -> expression.interpret(extValue.getType(iee), iee)), //
									attEnv, thePromiseModelVariable);
								// return new PlaceholderExtValue<>(extValue, expression, theContentModelVariable);
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
			else if (attr.getType() == QonfigValueType.QONFIG_TYPE)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
					TypeTokens.get().keyFor(QonfigValueType.QonfigTypeReference.class).parameterized(QonfigElementOrAddOn.class), null,
					null);
			else if (attr.getType() == QonfigValueType.QONFIG_ELEMENT_TYPE)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
					TypeTokens.get().keyFor(QonfigValueType.QonfigTypeReference.class).parameterized(QonfigElementDef.class), null, null);
			else if (attr.getType() == QonfigValueType.QONFIG_ADD_ON)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
					TypeTokens.get().keyFor(QonfigValueType.QonfigTypeReference.class).parameterized(QonfigAddOn.class), null, null);
			else if (attr.getType() == QonfigValueType.QONFIG_ADD_ON_SET)
				return new AttributeValueSatisfier.Literal<>(attr.getType().getName(),
					TypeTokens.get().keyFor(Set.class).parameterized(
						TypeTokens.get().keyFor(QonfigValueType.QonfigTypeReference.class).parameterized(QonfigElementOrAddOn.class)),
					null, null);
			else
				reporting.error(
					prefix + attr.getType().getName() + "-typed attributes must be required or defaulted to be supported model values");
			return null;
		}

		private static <M> CompiledModelValue<M> parallel(CompiledModelValue<M> modelValue, CompiledExpressoEnv env,
			ModelComponentId refModelId) throws QonfigInterpretationException {
			ModelType<M> modelType;
			try {
				modelType = modelValue.getModelType(env);
			} catch (ExpressoCompilationException e) {
				throw new QonfigInterpretationException(e.getPosition(), e.getErrorLength(), e);
			}
			return CompiledModelValue.of(modelValue::toString, modelType, iee -> {
				iee = iee.get(REFERENCE_ENV_PROPERTY, InterpretedExpressoEnv.class);
				InterpretedValueSynth<M, M> ivs = (InterpretedValueSynth<M, M>) modelValue.interpret(iee);
				return InterpretedValueSynth.of(ivs.getType(), () -> {
					ModelValueInstantiator<M> mvi = ivs.instantiate();
					return ModelValueInstantiator.of(msi -> {
						msi = ((SettableValue<ModelSetInstance>) msi.get(refModelId)).get();
						return mvi.get(msi);
					});
				}, ivs);
			});
		}

		@Override
		public Interpreted<? extends C> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	/**
	 * {@link ExpressoExternalDocument} interpretation
	 *
	 * @param <C> The sub-type of element to create
	 */
	public static class Interpreted<C extends ExpressoExternalDocument> extends QonfigExternalDocument.Interpreted<C> {
		// private InterpretedModelSet theContentModelModel;

		Interpreted(Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		// /** @return The expresso model for the external content */
		// public InterpretedModelSet getContentModelModel() {
		// return theContentModelModel;
		// }

		/**
		 * @param attribute The attribute to get the value for
		 * @return The interpreted value for the given attribute
		 */
		public Object getExternalAttribute(QonfigAttributeDef.Declared attribute) {
			// TODO This method is only to support traceability, specifically to support hover and other features in QWYSIWYG.
			return null;
		}

		/**
		 * @param child The child to get the value for
		 * @return The interpreted value for the given child
		 */
		public List<ExpressoChildPlaceholder.Interpreted<?>> getChildren(QonfigChildDef.Declared child) {
			// TODO This method is only to support traceability, specifically to support hover and other features in QWYSIWYG.
			return Collections.emptyList();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			InterpretedExpressoEnv promiseEnv = getFulfilledPromise().getDefaultEnv();
			getDefaultEnv().put(REFERENCE_ENV_PROPERTY, promiseEnv);

			// if(g
			// ObservableModelSet.ExternalModelSetBuilder extBuilder=ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
			// for(Map.Entry<QonfigAttributeDef.Declared, AttributeValueSatisfier> attr :
			// theContentModelModel = getDefinition().getContentModelModel().createInterpreted(env);
			// theContentModelModel.interpret(env);
			//
			// getDefaultEnv().put(REFERENCE_ENV_PROPERTY, env);
			super.doUpdate();
		}

		/**
		 * @param content The external content
		 * @return The external content instantiation
		 */
		public ExpressoExternalDocument create(ExElement content) {
			return new ExpressoExternalDocument(getIdentity(), content);
		}
	}

	private ModelComponentId thePromiseModelVariable;
	// private ModelInstantiator theContentModelModel;
	// private ModelComponentId theContentModelVariable;
	// private ModelInstantiator theContentModels;

	ExpressoExternalDocument(Object id, ExElement content) {
		super(id);
	}

	/**
	 * @param child The child to get the value for
	 * @return The instantiated value for the given child
	 */
	public List<ExpressoChildPlaceholder> getChildren(QonfigChildDef.Declared child) {
		// TODO This method is only to support traceability, specifically to support hover and other features in QWYSIWYG.
		return Collections.emptyList();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		// ExpressoExternalDocument.Interpreted<?> myInterpreted = (ExpressoExternalDocument.Interpreted<?>) interpreted;
		// theContentModelModel = myInterpreted.getContentModelModel().instantiate();
		// theContentModelVariable = myInterpreted.getDefinition().getContentModelVariable();
		// theContentModels = myInterpreted.getDefaultEnv().getModels().instantiate();
		//
		super.doUpdate(interpreted);
		ExpressoExternalDocument.Interpreted<?> myInterpreted = (ExpressoExternalDocument.Interpreted<?>) interpreted;
		thePromiseModelVariable = myInterpreted.getDefinition().getPromiseModelVariable();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		// We have to instantiate the document model ourselves because it requires the reference model to be populated
		ModelInstantiator docModels = getModels(getDocument());
		if (docModels != null && docModels.getComponents().contains(thePromiseModelVariable)) {
			Observable<?> modelUntil = Observable.or(myModels.getUntil(), onDestroy());
			ModelSetInstanceBuilder builder = docModels.createInstance(modelUntil);
			((SettableValue<ModelSetInstance>) builder.get(thePromiseModelVariable)).set(myModels);
			myModels = ObservableModelSet.createMultiModelInstanceBag(modelUntil)//
				.withAll(myModels)//
				.withAll(builder.withAll(myModels).build())//
				.build();
		}
		return super.doInstantiate(myModels);
	}

	// @Override
	// public ModelSetInstance augmentWithLocalModels(ModelSetInstance parentModels) throws ModelInstantiationException {
	// ModelSetInstance standardJava = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.getModels().createInstance(parentModels.getUntil())
	// .build();
	// ModelSetInstance contentModelModel = theContentModelModel.createInstance(parentModels.getUntil())//
	// .withAll(standardJava)//
	// .build();
	// ((ContentModelHolder) contentModelModel.get(theContentModelVariable)).setContentModels(parentModels);
	// ModelSetInstance withContentModels = ObservableModelSet.createMultiModelInstanceBag(parentModels.getUntil())//
	// .withAll(parentModels)//
	// .withAll(contentModelModel)//
	// .build();
	// return super.augmentWithLocalModels(withContentModels);
	// }

	@Override
	public ExpressoExternalDocument copy(ExElement content) {
		return (ExpressoExternalDocument) super.copy(null);
	}
	//
	// static class PlaceholderExtValue<M> implements CompiledModelValue<M> {
	// private final ExtModelValueElement.Def<M> theSpec;
	// private final CompiledExpression theExpression;
	// private final ModelComponentId theContentModelVariable;
	//
	// PlaceholderExtValue(ExtModelValueElement.Def<M> spec, CompiledExpression expression, ModelComponentId contentModelVariable) {
	// theSpec = spec;
	// theExpression = expression;
	// theContentModelVariable = contentModelVariable;
	// }
	//
	// @Override
	// public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
	// return theSpec.getModelType();
	// }
	//
	// @Override
	// public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
	// InterpretedExpressoEnv contentEnv = env.get(REFERENCE_ENV_PROPERTY, InterpretedExpressoEnv.class);
	// if (contentEnv == null)
	// throw new IllegalStateException("No " + REFERENCE_ENV_PROPERTY + " found");
	// InterpretedValueSynth<M, ?> attrValueSynth = theExpression.interpret(theSpec.getType(env), contentEnv);
	// InterpretedValueSynth<?, ContentModelHolder> contentModel = (InterpretedValueSynth<?, ContentModelHolder>) env.getModels()
	// .getComponent(theContentModelVariable);
	// return of(attrValueSynth, contentModel);
	// }
	//
	// private <MV extends M> InterpretedValueSynth<M, MV> of(InterpretedValueSynth<M, MV> attrValueSynth,
	// InterpretedValueSynth<?, ContentModelHolder> contentModel) {
	// return InterpretedValueSynth.of(attrValueSynth.getType(),
	// () -> instantiate(attrValueSynth.instantiate(), contentModel.instantiate()), attrValueSynth, contentModel);
	// }
	//
	// private <MV> ModelValueInstantiator<MV> instantiate(ModelValueInstantiator<MV> attrValue,
	// ModelValueInstantiator<ContentModelHolder> contentModel) {
	// return new ModelValueInstantiator<MV>() {
	// @Override
	// public void instantiate() throws ModelInstantiationException {
	// attrValue.instantiate();
	// contentModel.instantiate();
	// }
	//
	// @Override
	// public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
	// ModelSetInstance contentModelInstance = contentModel.get(models).get();
	// return attrValue.get(contentModelInstance);
	// }
	//
	// @Override
	// public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels)
	// throws ModelInstantiationException {
	// return get(newModels);
	// }
	// };
	// }
	//
	// @Override
	// public String toString() {
	// return theExpression.toString();
	// }
	// }
	//
	// static class ContentModelHolder extends AbstractIdentifiable implements SettableValue<ModelSetInstance> {
	// private ModelSetInstance theContentModels;
	//
	// void setContentModels(ModelSetInstance contentModels) {
	// theContentModels = contentModels;
	// }
	//
	// @Override
	// public ModelSetInstance get() {
	// return theContentModels;
	// }
	//
	// @Override
	// public Observable<ObservableValueEvent<ModelSetInstance>> noInitChanges() {
	// return Observable.empty();
	// }
	//
	// @Override
	// public long getStamp() {
	// return 0;
	// }
	//
	// @Override
	// protected Object createIdentity() {
	// return Identifiable.baseId(REFERENCE_ENV_PROPERTY, this);
	// }
	//
	// @Override
	// public ContentModelHolder alias(String alias) {
	// super.alias(alias);
	// return this;
	// }
	//
	// @Override
	// public Transaction lock(boolean write, Object cause) {
	// return Transaction.NONE;
	// }
	//
	// @Override
	// public Transaction tryLock(boolean write, Object cause) {
	// return Transaction.NONE;
	// }
	//
	// @Override
	// public boolean isLockSupported() {
	// return false;
	// }
	//
	// @Override
	// public Collection<Cause> getCurrentCauses() {
	// return Collections.emptyList();
	// }
	//
	// @Override
	// public ModelSetInstance set(ModelSetInstance value) throws IllegalArgumentException, UnsupportedOperationException {
	// throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	// }
	//
	// @Override
	// public String isAcceptable(ModelSetInstance value) {
	// return StdMsg.UNSUPPORTED_OPERATION;
	// }
	//
	// @Override
	// public ObservableValue<String> isEnabled() {
	// return SettableValue.ALWAYS_DISABLED;
	// }
	// }
}

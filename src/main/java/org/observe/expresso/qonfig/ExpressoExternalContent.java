package org.observe.expresso.qonfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParseException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigValueType;
import org.qommons.config.SpecificationType;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ExpressoExternalContent extends ExElement.Abstract implements QonfigExternalContent {
	public static final String EXPRESSO_EXTERNAL_CONTENT = "expresso-external-content";

	public interface AttributeValueSatisfier {
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
	public static class Def<C extends ExpressoExternalContent> extends ExElement.Def.Abstract<C> implements QonfigExternalContent.Def<C> {
		private QonfigElementDef theFulfills;
		private Expresso.Def theHead;
		private ExElement.Def<?> theContent;

		private final Map<QonfigAttributeDef.Declared, AttributeValueSatisfier> theAttributeValues;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theAttributeValues = new LinkedHashMap<>();
		}

		@QonfigChildGetter("head")
		public Expresso.Def getHead() {
			return theHead;
		}

		@Override
		public QonfigElementDef getFulfills() {
			return theFulfills;
		}

		@Override
		public ExElement.Def<?> getContent() {
			return theContent;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> content) throws QonfigInterpretationException {
			theContent = content;
			super.update(session);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			try {
				theFulfills = session.getElement().getDocument().getDocToolkit().getElement(//
					session.getAttributeText("fulfills"));
			} catch (IllegalArgumentException e) {
				reporting().error(e.getMessage(), e);
			}

			theAttributeValues.clear();
			for (QonfigAttributeDef attr : theFulfills.getAllAttributes().values()) {
				if (theContent.getPromise() != null) {
					Object value = theContent.getPromise().getAttribute(attr);
					if (value instanceof AttributeValueSatisfier)
						theAttributeValues.put(attr.getDeclared(), (AttributeValueSatisfier) value);
					else if (value instanceof CompiledModelValue) {
						CompiledModelValue<?> cmv = (CompiledModelValue<?>) value;
						theAttributeValues.put(attr.getDeclared(), new AttributeValueSatisfier() {
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
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
								throws QonfigInterpretationException {
								return new PlaceholderExtValue<>(extValue, expression);
							}
						});
					} else if (value != null) {
						theAttributeValues.put(attr.getDeclared(), new AttributeValueSatisfier.Literal<>(value.getClass().getName(),
							TypeTokens.get().of((Class<Object>) value.getClass()), value, value.toString()));
					} else
						theAttributeValues.put(attr.getDeclared(), //
							populateAttributeValue(attr, theContent.getElement().getPromise(),
								theContent.getElement().getPromise().getAttributes().get(attr.getDeclared()), session));
				} else
					theAttributeValues.put(attr.getDeclared(), //
						populateAttributeValue(attr, theContent.getElement().getPromise(),
							theContent.getElement().getPromise().getAttributes().get(attr.getDeclared()), session));
			}

			session.put(ObservableModelElement.PREVENT_MODEL_BUILDING,
				(Predicate<ObservableModelElement.Def<?, ?>>) model -> model instanceof ObservableModelElement.ExtModelElement.Def);
			theHead = ExElement.useOrReplace(Expresso.Def.class, theHead, session, "head");
			ObservableModelSet.Builder builder;
			if (theHead.getExpressoEnv().getModels() instanceof ObservableModelSet.Builder)
				builder = (ObservableModelSet.Builder) theHead.getExpressoEnv().getModels();
			else
				builder = theHead.getExpressoEnv().getModels().wrap(getElement().getType().getName() + ".local");
			for (ObservableModelElement.Def<?, ?> model : theHead.getModelElement().getSubModels()) {
				if (model instanceof ObservableModelElement.ExtModelElement.Def)
					populateExtModelValues((ObservableModelElement.ExtModelElement.Def<?>) model, builder, session);
			}
			setExpressoEnv(theHead.getExpressoEnv().with(builder));
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
								LocatedPositionedContent.of(value.fileLocation, value.position), session);
						} catch (ExpressoParseException e) {
							throw new QonfigInterpretationException(e.getMessage(),
								LocatedFilePosition.of(value.fileLocation, value.position.getPosition(e.getErrorOffset())),
								e.getErrorLength());
						}
						return new AttributeValueSatisfier() {
							@Override
							public <M> CompiledModelValue<M> satisfy(ExtModelValueElement.Def<M> extValue, CompiledExpressoEnv env)
								throws QonfigInterpretationException {
								return new PlaceholderExtValue<>(extValue, expression);
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

		protected void populateExtModelValues(ObservableModelElement.ExtModelElement.Def<?> model, ObservableModelSet.Builder builder,
			ExpressoQIS session) throws QonfigInterpretationException {
			builder = builder.createSubModel(model.getName(), model.getElement().getPositionInFile());
			for (ExtModelValueElement.Def<?> extValue : model.getValues()) {
				QonfigAttributeDef attr = extValue.getAddOn(AttributeBackedModelValue.Def.class).getSourceAttribute();
				String name = extValue.getAddOn(ExNamed.Def.class).getName();
				QonfigValue value = theContent.getElement().getPromise().getAttributes().get(attr.getDeclared());
				LocatedFilePosition source;
				if (value != null)
					source = LocatedFilePosition.of(value.fileLocation, value.position.getPosition(0));
				else
					source = session.getElement().getPositionInFile();
				AttributeValueSatisfier satisfier = theAttributeValues.get(attr.getDeclared());
				if (satisfier != null)
					builder.withMaker(name, satisfier.satisfy(extValue, getExpressoEnv()), source);
			}
			for (ObservableModelElement.ExtModelElement.Def<?> subModel : model.getSubModels())
				populateExtModelValues(subModel, builder, session);
		}

		@Override
		public Interpreted<? extends C> interpret() {
			return new Interpreted<>(this, null);
		}
	}

	public static class Interpreted<C extends ExpressoExternalContent> extends ExElement.Interpreted.Abstract<C>
	implements QonfigExternalContent.Interpreted<C> {
		private Expresso theHead;
		private ExElement.Interpreted<?> theContent;

		Interpreted(ExElement.Def<? super C> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super C> getDefinition() {
			return (Def<? super C>) super.getDefinition();
		}

		public Expresso getHead() {
			return theHead;
		}

		@Override
		public ExElement.Interpreted<?> getContent() {
			return theContent;
		}

		@Override
		public void update(ExElement.Interpreted<?> content) throws ExpressoInterpretationException {
			theContent = content;

			super.update(InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			if (theHead != null && getDefinition().getHead().getIdentity() != theHead.getIdentity()) {
				theHead.destroy();
				theHead = null;
			}
			if (theHead == null)
				theHead = getDefinition().getHead().interpret(this);

			theHead.updateExpresso(env);
			setExpressoEnv(theHead.getExpressoEnv());
		}
	}

	private final ExElement theContent;

	ExpressoExternalContent(Object id, ExElement content) {
		super(id);
		theContent = content;
	}

	@Override
	public ExElement getContent() {
		return theContent;
	}

	@Override
	public ExpressoExternalContent copy(ExElement parent) {
		return (ExpressoExternalContent) super.copy(parent);
	}

	static class PlaceholderExtValue<M> implements CompiledModelValue<M> {
		private final ExtModelValueElement.Def<M> theSpec;
		private final CompiledExpression theExpression;

		PlaceholderExtValue(ExtModelValueElement.Def<M> spec, CompiledExpression expression) {
			theSpec = spec;
			theExpression = expression;
		}

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			return theSpec.getModelType();
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			return theExpression.interpret(theSpec.getType(env), env);
		}

		@Override
		public String toString() {
			return theExpression.toString();
		}
	}
}

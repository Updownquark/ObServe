package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickValueWidget.WidgetValueSupplier;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class DynamicStyledDocument<T> extends StyledDocument<T> {
	public static final String DYNAMIC_STYLED_DOCUMENT = "dynamic-styled-document";
	private static final SingleTypeTraceability<DynamicStyledDocument<?>, Interpreted<?, ?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, DYNAMIC_STYLED_DOCUMENT, Def.class,
			Interpreted.class, DynamicStyledDocument.class);

	public static class Def<D extends DynamicStyledDocument<?>> extends StyledDocument.Def<D> {
		private CompiledExpression theRoot;
		private CompiledExpression theChildren;
		private CompiledExpression theFormat;
		private CompiledExpression thePostText;
		private TextStyleElement.Def theTextStyle;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return false; // Editing not implemented
		}

		@QonfigAttributeGetter("root")
		public CompiledExpression getRoot() {
			return theRoot;
		}

		@QonfigAttributeGetter("children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		@QonfigAttributeGetter("format")
		public CompiledExpression getFormat() {
			return theFormat;
		}

		@QonfigAttributeGetter("post-text")
		public CompiledExpression getPostText() {
			return thePostText;
		}

		@QonfigChildGetter("text-style")
		public TextStyleElement.Def getTextStyle() {
			return theTextStyle;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theRoot = session.getAttributeExpression("root");
			theChildren = session.getAttributeExpression("children");
			theFormat = session.getAttributeExpression("format");
			thePostText = session.getAttributeExpression("post-text");
			theTextStyle = ExElement.useOrReplace(TextStyleElement.Def.class, theTextStyle, session, "text-style");
			getAddOn(ExWithElementModel.Def.class).satisfyElementValueType("node", ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getValueType()));
		}

		@Override
		public Interpreted<?, ? extends D> interpret(ExElement.Interpreted<?> parent) {
			// I cannot figure out how to pacify the generics here
			return (Interpreted<?, ? extends D>) new Interpreted<>((Def<DynamicStyledDocument<Object>>) this, parent);
		}
	}

	public static class Interpreted<T, D extends DynamicStyledDocument<T>> extends StyledDocument.Interpreted<T, D> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theRoot;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theChildren;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormat;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostText;
		private TextStyleElement.Interpreted theTextStyle;

		public Interpreted(Def<? super D> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			/* TODO
			 * It's really unfortunate that we have to do this, but in order to generate an independent, listenable collection for each node,
			 * we have to keep the model instance around to create copies.
			 *
			 * In other widgets I've created so far, the values needed from elements in a collection could be transient, where the value is
			 * inspected and then the observable value could be repurposed for the next child.  But we need to keep the child collection
			 * around for each node independently so we can be notified of changes deep in the hierarchy.
			 *
			 * This is a problem because the ModelSetInstance API currently keeps and exposes its source models.
			 * This means that the structures used to create the models, including expressions, text, Qonfig elements, etc.
			 * must be kept around in memory.  Up to now I'd been able to get away with releasing these after the widget instances were built.
			 *
			 * We also need a reference to the InterpretedValueSynth to create the collection from model copies.  That structure is typically
			 * composed of references to other model values in the form of InterpretedModelComponentNodes, which have references to the models.
			 *
			 * I think the solution to this is to divorce ModelSetInstance and InterpretedValueSynth from the source models.
			 * Once that's done, I think we can delete this comment and accept the way this is done.
			 */
			persistModelInstances(true);
		}

		@Override
		public Def<? super D> getDefinition() {
			return (Def<? super D>) super.getDefinition();
		}

		@Override
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) getOrInitRoot().getType().getType(0);
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitRoot() throws ExpressoInterpretationException {
			if (theRoot == null) {
				if (getDefinition().getRoot() != null)
					theRoot = getDefinition().getRoot().interpret(ModelTypes.Value.<T> anyAsV(), getExpressoEnv());
				else
					theRoot = ((WidgetValueSupplier.Interpreted<T, ?>) getParentElement()).getValue();
			}
			return theRoot;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getRoot() {
			return theRoot;
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getChildren() {
			return theChildren;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getFormat() {
			return theFormat;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getPostText() {
			return thePostText;
		}

		public TextStyleElement.Interpreted getTextStyle() {
			return theTextStyle;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theRoot = null;
			super.doUpdate(env);

			getOrInitRoot(); // Initialize theRoot
			theChildren = getDefinition().getChildren().interpret(ModelTypes.Collection.forType(getValueType()), getExpressoEnv());
			if (getDefinition().getFormat() != null)
				theFormat = getDefinition().getFormat()
				.interpret(ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(getValueType())), env);
			else
				theFormat = QuickTextWidget.getDefaultFormat(getValueType(), false, reporting().getPosition());
			thePostText = getDefinition().getPostText() == null ? null
				: getDefinition().getPostText().interpret(ModelTypes.Value.STRING, getExpressoEnv());
			if (theTextStyle == null || theTextStyle.getDefinition() != getDefinition().getTextStyle())
				theTextStyle = getDefinition().getTextStyle().interpret(this);
			theTextStyle.updateElement(getExpressoEnv());
		}

		@Override
		public DynamicStyledDocument<T> create(ExElement parent) {
			return new DynamicStyledDocument<>(this, parent);
		}
	}

	public interface StyledTextAreaContext<T> {
		SettableValue<T> getNodeValue();

		public class Default<T> implements StyledTextAreaContext<T> {
			private final SettableValue<T> theNodeValue;

			public Default(SettableValue<T> nodeValue) {
				theNodeValue = nodeValue;
			}

			public Default(TypeToken<T> type) {
				this(SettableValue.build(type).build());
			}

			@Override
			public SettableValue<T> getNodeValue() {
				return theNodeValue;
			}
		}
	}

	private SettableValue<SettableValue<T>> theRoot;
	private TextStyleElement theTextStyle;
	private final SettableValue<T> theNodeValue;
	private Format<T> theFormat;

	private ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> theValueType;
	private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theChildrenSynth;
	private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostTextSynth;

	public DynamicStyledDocument(Interpreted<T, ?> interpreted, ExElement parent) {
		super(interpreted, parent);
		TypeToken<T> valueType;
		try {
			valueType = interpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not interpreted?", e);
		}
		theRoot = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(valueType))
			.build();
		theNodeValue = SettableValue.build(valueType).build();
	}

	@Override
	public ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> getValueType() {
		return theValueType;
	}

	public SettableValue<T> getRoot() {
		return SettableValue.flatten(theRoot);
	}

	public boolean hasPostText() {
		return thePostTextSynth != null;
	}

	public TextStyleElement getTextStyle() {
		return theTextStyle;
	}

	public SettableValue<T> getNodeValue() {
		return theNodeValue;
	}

	public ObservableCollection<? extends T> getChildren(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), modelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		// After synthesizing and returning the children for the node, we can discard the model copy
		return theChildrenSynth.get(modelCopy);
	}

	public Format<T> getFormat() {
		return theFormat;
	}

	public TextStyle getStyle(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance widgetModelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), widgetModelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		ModelSetInstance styleElementModelCopy = getTextStyle().getUpdatingModels().copy()//
			.withAll(widgetModelCopy)//
			.build();

		return theTextStyle.getStyle().copy(styleElementModelCopy);
	}

	public SettableValue<String> getPostText(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), modelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		// After synthesizing and returning the post text, we can discard the model copy
		return thePostTextSynth.get(modelCopy);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", getNodeValue());
		super.updateModel(interpreted, myModels);
		theRoot.set(myInterpreted.getRoot().get(myModels), null);
		theFormat = myInterpreted.getFormat().get(myModels).get();
		if (theTextStyle == null || theTextStyle.getIdentity() != myInterpreted.getTextStyle().getDefinition().getIdentity())
			theTextStyle = myInterpreted.getTextStyle().create(this);
		theTextStyle.update(myInterpreted.getTextStyle(), myModels);

		TypeToken<T> valueType;
		try {
			valueType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new ModelInstantiationException("Not evaluated yet??!!", e.getPosition(), e.getErrorLength(), e);
		}
		theValueType = ModelTypes.Value.forType(valueType);
		theChildrenSynth = myInterpreted.getChildren();
		thePostTextSynth = myInterpreted.getPostText();
	}
}

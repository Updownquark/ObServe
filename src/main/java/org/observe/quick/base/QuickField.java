package org.observe.quick.base;

import java.util.Collections;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on automatically inherited by widget contents in a {@link QuickFieldPanel} */
public class QuickField extends ExAddOn.Abstract<QuickWidget> {
	/** The XML name of this add-on */
	public static final String FIELD = "field";

	/** {@link QuickField} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FIELD,
		interpretation = Interpreted.class,
		instance = QuickField.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickField> {
		private CompiledExpression theFieldLabel;
		private boolean isFill;
		private boolean isVFill;
		private QuickPostField.Def<?> thePost;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The content widget
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.singleton((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class);
		}

		/** @return The text label for the field widget */
		@QonfigAttributeGetter("field-label")
		public CompiledExpression getFieldLabel() {
			return theFieldLabel;
		}

		/** @return Whether the widget should be stretched to fill the horizontal space of the container */
		@QonfigAttributeGetter("fill")
		public boolean isFill() {
			return isFill;
		}

		/** @return Whether the widget should be stretched to fill the vertical space of the container */
		@QonfigAttributeGetter("v-fill")
		public boolean isVFill() {
			return isVFill;
		}

		/** @return The component to place on the trailing side of this field component */
		@QonfigChildGetter("post")
		public QuickPostField.Def<?> getPost() {
			return thePost;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldLabel = element.getAttributeExpression("field-label", session);
			isFill = Boolean.TRUE.equals(session.getAttribute("fill", Boolean.class));
			isVFill = Boolean.TRUE.equals(session.getAttribute("v-fill", Boolean.class));
			thePost = element.syncChild(QuickPostField.Def.class, thePost, session, "post");
		}

		@Override
		public <E2 extends QuickWidget> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickField} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, QuickField> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theName;
		private QuickPostField.Interpreted<?> thePost;

		/**
		 * @param definition The definition to interpret
		 * @param element The content widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<? extends QuickWidget> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The text label for the field widget */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getFieldLabel() {
			return theName;
		}

		/** @return The component to place on the trailing side of this field component */
		public QuickPostField.Interpreted<?> getPost() {
			return thePost;
		}

		@Override
		public void update(ExElement.Interpreted<? extends QuickWidget> element) throws ExpressoInterpretationException {
			super.update(element);
			theName = getElement().interpret(getDefinition().getFieldLabel(), ModelTypes.Value.STRING);
			thePost = getElement().syncChild(getDefinition().getPost(), thePost, def -> def.interpret(getElement()),
				QuickPostField.Interpreted::updatePostField);
		}

		@Override
		public Class<QuickField> getInstanceType() {
			return QuickField.class;
		}

		@Override
		public QuickField create(ExElement widget) {
			return new QuickField(widget);
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theFieldLabelInstantiator;
	private SettableValue<String> theFieldLabel;
	private QuickPostField thePost;

	/** @param widget The content widget */
	protected QuickField(ExElement widget) {
		super(widget);
	}

	/** @return The text label for the field widget */
	public SettableValue<String> getFieldLabel() {
		return theFieldLabel;
	}

	/** @return The component to place on the trailing side of this field component */
	public QuickPostField getPost() {
		return thePost;
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super QuickWidget, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		QuickField.Interpreted myInterpreted = (QuickField.Interpreted) interpreted;
		theFieldLabelInstantiator = myInterpreted.getFieldLabel() == null ? null : myInterpreted.getFieldLabel().instantiate();
		if (myInterpreted.getPost() == null) {
			if (thePost != null)
				thePost.destroy();
			thePost = null;
		} else if (thePost == null)
			thePost = myInterpreted.getPost().create();
		if (thePost != null)
			thePost.update(myInterpreted.getPost(), getElement());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theFieldLabelInstantiator != null)
			theFieldLabelInstantiator.instantiate();
		if (thePost != null)
			thePost.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);
		theFieldLabel = theFieldLabelInstantiator == null ? null : theFieldLabelInstantiator.get(models);
		if (thePost != null)
			thePost.instantiate(models);
		return models;
	}

	@Override
	public QuickField copy(ExElement element) {
		QuickField copy = (QuickField) super.copy(element);

		copy.thePost = thePost == null ? null : thePost.copy(element);

		return copy;
	}
}

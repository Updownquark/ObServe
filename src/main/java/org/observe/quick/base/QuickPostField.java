package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A widget tagged on to the trailing end of a field in a &lt;field-panel> */
public class QuickPostField extends ExElement.Abstract {
	/** The XML name of this element */
	public static final String POST_FIELD = "post-field";

	/**
	 * {@link QuickPostField} definition
	 *
	 * @param <F> The sub-type of post-field to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = POST_FIELD,
		interpretation = Interpreted.class,
		instance = QuickPostField.class)
	public static class Def<F extends QuickPostField> extends ExElement.Def.Abstract<F> {
		private QuickWidget.Def<?> theContent;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the field
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The widget to append to the field */
		@QonfigChildGetter("content")
		public QuickWidget.Def<?> getContent() {
			return theContent;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theContent = syncChild(QuickWidget.Def.class, theContent, session, "content");
		}

		/**
		 * Interprets this definition
		 *
		 * @param parent The parent of the new interpreted element
		 * @return The post-field interpretation
		 */
		public Interpreted<? extends F> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickPostField} interpretation
	 *
	 * @param <F> The sub-type of button to create
	 */
	public static class Interpreted<F extends QuickPostField> extends ExElement.Interpreted.Abstract<F> {
		private QuickWidget.Interpreted<?> theContent;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the field
		 */
		protected Interpreted(Def<? super F> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super F> getDefinition() {
			return (Def<? super F>) super.getDefinition();
		}

		/** @return The widget to append to the field */
		public QuickWidget.Interpreted<?> getContent() {
			return theContent;
		}

		/**
		 * Updates this interpretation
		 *
		 * @throws ExpressoInterpretationException If an error occurs during interpretation
		 */
		public void updatePostField() throws ExpressoInterpretationException {
			update();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();
			theContent = syncChild(getDefinition().getContent(), theContent, d -> d.interpret(this), i -> i.updateElement());
		}

		/** @return The post-field instance */
		public F create() {
			return (F) new QuickPostField(getIdentity());
		}
	}

	private QuickWidget theContent;

	/** @param id The element ID for this post-field */
	protected QuickPostField(Object id) {
		super(id);
	}

	/** @return The widget to append to the field */
	public QuickWidget getContent() {
		return theContent;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		QuickPostField.Interpreted<?> myInterpreted = (QuickPostField.Interpreted<?>) interpreted;
		if (theContent == null)
			theContent = myInterpreted.getContent().create();
		theContent.update(myInterpreted.getContent(), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theContent.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		theContent.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickPostField copy(ExElement parent) {
		QuickPostField copy = (QuickPostField) super.copy(parent);

		copy.theContent = theContent.copy(this);

		return copy;
	}
}

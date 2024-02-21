package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickTextWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A text widget that the user may be able to edit, changing the value represented
 *
 * @param <T> The type of the value represented by the widget
 */
public interface QuickEditableTextWidget<T> extends QuickTextWidget<T> {
	/** The XML name of this element */
	public static final String EDITABLE_TEXT_WIDGET = "editable-text-widget";

	/**
	 * {@link QuickEditableTextWidget} definition
	 *
	 * @param <W> The sub-type of text widget to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = EDITABLE_TEXT_WIDGET,
		interpretation = Interpreted.class,
		instance = QuickEditableTextWidget.class)
	public interface Def<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def<W> {
		/**
		 * @return Whether the value represented by the widget should be modified as the user types, or only when signalled by a "commit"
		 *         event, e.g. pressing ENTER or leaving the widget's focus
		 */
		@QonfigAttributeGetter("commit-on-type")
		boolean isCommitOnType();

		/** @return The model ID of the variable that the current error status of the widget will be made available to expressions by */
		ModelComponentId getErrorVariable();

		/** @return The model ID of the variable that the current warning status of the widget will be made available to expressions by */
		ModelComponentId getWarningVariable();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract {@link QuickEditableTextWidget} definition implementation
		 *
		 * @param <W> The sub-type of text widget to create
		 */
		public abstract class Abstract<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def.Abstract<W>
		implements Def<W> {
			private boolean isCommitOnType;
			private ModelComponentId theErrorVariable;
			private ModelComponentId theWarningVariable;

			/**
			 * @param parent The parent element of the widget
			 * @param type The Qonfig type of the widget
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public boolean isCommitOnType() {
				return isCommitOnType;
			}

			@Override
			public ModelComponentId getErrorVariable() {
				return theErrorVariable;
			}

			@Override
			public ModelComponentId getWarningVariable() {
				return theWarningVariable;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				isCommitOnType = session.getAttribute("commit-on-type", boolean.class);

				ExWithElementModel.Def elValues = getAddOn(ExWithElementModel.Def.class);
				theErrorVariable = elValues.getElementValueModelId("error");
				theWarningVariable = elValues.getElementValueModelId("warning");
			}
		}
	}

	/**
	 * {@link QuickEditableTextWidget} interpretation
	 *
	 * @param <T> The type of the value represented by the widget
	 * @param <W> The sub-type of text widget to create
	 */
	public interface Interpreted<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		/**
		 * Abstract {@link QuickEditableTextWidget} interpretation implementation
		 *
		 * @param <T> The type of the value represented by the widget
		 * @param <W> The sub-type of text widget to create
		 */
		public abstract class Abstract<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the widget
			 */
			protected Abstract(QuickEditableTextWidget.Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}
		}
	}

	/** Model context for a {@link QuickEditableTextWidget} */
	public interface EditableTextWidgetContext {
		/** @return Non-null when the value represented by the edited text is illegal for the widget's value */
		SettableValue<String> getError();

		/** @return Non-null when the value represented by the edited text is inadvisable for the widget's value */
		SettableValue<String> getWarning();

		/** Default {@link EditableTextWidgetContext} implementation */
		public class Default implements EditableTextWidgetContext {
			private final SettableValue<String> theError;
			private final SettableValue<String> theWarning;

			/**
			 * @param error The error status for the editing text
			 * @param warning The warning status for the editing text
			 */
			public Default(SettableValue<String> error, SettableValue<String> warning) {
				theError = error;
				theWarning = warning;
			}

			/**
			 * @param error The error status for the editing text
			 * @param warning The warning status for the editing text
			 */
			public Default(ObservableValue<String> error, ObservableValue<String> warning) {
				theError = SettableValue.asSettable(error, __ -> "Not settable");
				theWarning = SettableValue.asSettable(warning, __ -> "Not settable");
			}

			@Override
			public SettableValue<String> getError() {
				return theError;
			}

			@Override
			public SettableValue<String> getWarning() {
				return theWarning;
			}
		}
	}

	/**
	 * @param ctx The model context for this editable text widget
	 * @return This widget
	 * @throws ModelInstantiationException If the model context could not be installed
	 */
	QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException;

	/**
	 * Abstract {@link QuickEditableTextWidget} implementation
	 *
	 * @param <T> The type of the value represented by the widget
	 */
	public static abstract class Abstract<T> extends QuickTextWidget.Abstract<T> implements QuickEditableTextWidget<T> {
		private boolean isCommitOnType;
		private ModelComponentId theErrorVariable;
		private ModelComponentId theWarningVariable;
		private SettableValue<SettableValue<String>> theErrorStatus;
		private SettableValue<SettableValue<String>> theWarningStatus;

		/** @param id The element ID for this widget */
		protected Abstract(Object id) {
			super(id);
			theErrorStatus = SettableValue.<SettableValue<String>> build().build();
			theWarningStatus = SettableValue.<SettableValue<String>> build().build();
		}

		/**
		 * @return Whether the value represented by the widget should be modified as the user types, or only when signalled by a "commit"
		 *         event, e.g. pressing ENTER or leaving the widget's focus
		 */
		public boolean isCommitOnType() {
			return isCommitOnType;
		}

		@Override
		public QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException {
			theErrorStatus.set(ctx.getError(), null);
			theWarningStatus.set(ctx.getWarning(), null);
			return this;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			QuickEditableTextWidget.Interpreted<T, ?> myInterpreted = (QuickEditableTextWidget.Interpreted<T, ?>) interpreted;
			theErrorVariable = myInterpreted.getDefinition().getErrorVariable();
			theWarningVariable = myInterpreted.getDefinition().getWarningVariable();
			isCommitOnType = myInterpreted.getDefinition().isCommitOnType();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theErrorVariable, myModels, SettableValue.flatten(theErrorStatus));
			ExFlexibleElementModelAddOn.satisfyElementValue(theWarningVariable, myModels, SettableValue.flatten(theWarningStatus));
		}

		@Override
		public QuickEditableTextWidget.Abstract<T> copy(ExElement parent) {
			QuickEditableTextWidget.Abstract<T> copy = (QuickEditableTextWidget.Abstract<T>) super.copy(parent);

			copy.theErrorStatus = SettableValue.<SettableValue<String>> build().build();
			copy.theWarningStatus = SettableValue.<SettableValue<String>> build().build();

			return copy;
		}
	}
}

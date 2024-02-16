package org.observe.quick.base;

import java.io.File;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A button that represents the state of a file-type value and pops up a file chooser to select a new file when clicked */
public class QuickFileButton extends QuickValueWidget.Abstract<File> {
	/** The XML name of this element */
	public static final String FILE_BUTTON = "file-button";

	/** {@link QuickFileButton} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FILE_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickFileButton.class)
	public static class Def extends QuickValueWidget.Def.Abstract<QuickFileButton> {
		private boolean isOpen;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether the file is to be read (and so must exist) or saved to (and so might not yet exist) */
		@QonfigAttributeGetter("open")
		public boolean isOpen() {
			return isOpen;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isOpen = session.getAttribute("open", boolean.class);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickFileButton} interpretation */
	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<File, QuickFileButton> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<File>> getTargetType() {
			return ModelTypes.Value.forType(File.class);
		}

		@Override
		public QuickFileButton create() {
			return new QuickFileButton(getIdentity());
		}
	}

	private boolean isOpen;

	/** @param id The element ID for this widget */
	protected QuickFileButton(Object id) {
		super(id);
	}

	/** @return Whether the file is to be read (and so must exist) or saved to (and so might not yet exist) */
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		isOpen = myInterpreted.getDefinition().isOpen();
	}
}

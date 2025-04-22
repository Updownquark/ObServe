package org.observe.quick.base;

import java.io.File;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExpressoTransformations;
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
		private CompiledExpression theDefaultDir;
		private CompiledExpression theFileDescrip;

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

		/** @return The initial directory for the file chooser */
		@QonfigAttributeGetter("default-dir")
		public CompiledExpression getDefaultDir() {
			return theDefaultDir;
		}

		/** @return The description for the type of file selectable */
		@QonfigAttributeGetter("file-descrip")
		public CompiledExpression getFileDescrip() {
			return theFileDescrip;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theDefaultDir = getAttributeExpression("default-dir", session);
			theFileDescrip = getAttributeExpression("file-descrip", session);
			isOpen = session.getAttribute("open", boolean.class);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickFileButton} interpretation */
	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<File, QuickFileButton> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<File>> theDefaultDir;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theFileDescrip;

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

		/** @return The initial directory for the file chooser */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<File>> getDefaultDir() {
			return theDefaultDir;
		}

		/** @return The description for the type of file selectable */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getFileDescrip() {
			return theFileDescrip;
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theDefaultDir = interpret(getDefinition().getDefaultDir(), ModelTypes.Value.forType(File.class));
			theFileDescrip = ExpressoTransformations.parseFilter(getDefinition().getFileDescrip(), this, true);
		}

		@Override
		public QuickFileButton create() {
			return new QuickFileButton(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<File>> theDefaultDirInstantiator;
	private ModelValueInstantiator<SettableValue<String>> theFileDescripInstantiator;

	private boolean isOpen;
	private SettableValue<SettableValue<File>> theDefaultDir;
	private SettableValue<SettableValue<String>> theFileDescrip;

	/** @param id The element ID for this widget */
	protected QuickFileButton(Object id) {
		super(id);

		theDefaultDir = SettableValue.create();
		theFileDescrip = SettableValue.create();
	}

	/** @return Whether the file is to be read (and so must exist) or saved to (and so might not yet exist) */
	public boolean isOpen() {
		return isOpen;
	}

	/** @return The initial directory for the file chooser */
	public SettableValue<File> getDefaultDir() {
		return SettableValue.flatten(theDefaultDir);
	}

	/** @return The description for the type of file selectable */
	public SettableValue<String> getFileDescrip() {
		return SettableValue.flatten(theFileDescrip);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		isOpen = myInterpreted.getDefinition().isOpen();
		theDefaultDirInstantiator = myInterpreted.getDefaultDir() == null ? null : myInterpreted.getDefaultDir().instantiate();
		theFileDescripInstantiator = myInterpreted.getFileDescrip() == null ? null : myInterpreted.getFileDescrip().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theDefaultDirInstantiator != null)
			theDefaultDirInstantiator.instantiate();
		if (theFileDescripInstantiator != null)
			theFileDescripInstantiator.instantiate();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		// Populate with a value so the default directory is persistent for the session at least
		theDefaultDir.set(theDefaultDirInstantiator == null ? SettableValue.create() : theDefaultDirInstantiator.get(myModels), null);
		theFileDescrip.set(theFileDescripInstantiator == null ? null : theFileDescripInstantiator.get(myModels), null);
		return myModels;
	}

	@Override
	public QuickFileButton copy(ExElement parent) {
		QuickFileButton copy = (QuickFileButton) super.copy(parent);

		copy.theDefaultDir = SettableValue.create();
		copy.theFileDescrip = SettableValue.create();

		return copy;
	}
}

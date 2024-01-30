package org.observe.quick.base;

import java.io.File;
import java.util.List;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickDialog;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickFileChooser extends ExElement.Abstract implements QuickDialog {
	public static final String FILE_CHOOSER = "file-chooser";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FILE_CHOOSER,
		interpretation = Interpreted.class,
		instance = QuickFileChooser.class)
	public static class Def extends ExElement.Def.Abstract<QuickFileChooser> implements QuickDialog.Def<QuickFileChooser> {
		private ModelComponentId theChosenFilesVariable;
		private boolean isOpen;
		private boolean isFilesSelectable;
		private boolean isDirectoriesSelectable;
		private boolean isMultiSelectable;
		private CompiledExpression theDirectory;
		private CompiledExpression theOnSelect;
		private CompiledExpression theOnCancel;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ModelComponentId getChosenFilesVariable() {
			return theChosenFilesVariable;
		}

		@QonfigAttributeGetter("open")
		public boolean isOpen() {
			return isOpen;
		}

		@QonfigAttributeGetter("files-selectable")
		public boolean isFilesSelectable() {
			return isFilesSelectable;
		}

		@QonfigAttributeGetter("directories-selectable")
		public boolean isDirectoriesSelectable() {
			return isDirectoriesSelectable;
		}

		@QonfigAttributeGetter("multi-selectable")
		public boolean isMultiSelectable() {
			return isMultiSelectable;
		}

		@QonfigAttributeGetter("directory")
		public CompiledExpression getDirectory() {
			return theDirectory;
		}

		@QonfigAttributeGetter("on-select")
		public CompiledExpression getOnSelect() {
			return theOnSelect;
		}

		@QonfigAttributeGetter("on-cancel")
		public CompiledExpression getOnCancel() {
			return theOnCancel;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theChosenFilesVariable = elModels.getElementValueModelId("chosenFiles");
			elModels.satisfyElementValueType(theChosenFilesVariable, ModelTypes.Collection.forType(File.class));
			isOpen = session.getAttribute("open", boolean.class);
			isFilesSelectable = session.getAttribute("files-selectable", boolean.class);
			isDirectoriesSelectable = session.getAttribute("directories-selectable", boolean.class);
			if (!isFilesSelectable && !isDirectoriesSelectable)
				reporting().error("Neither files nor directories are selectable");
			isMultiSelectable = session.getAttribute("multi-selectable", boolean.class);
			theDirectory = getAttributeExpression("directory", session);
			theOnSelect = getAttributeExpression("on-select", session);
			theOnCancel = getAttributeExpression("on-cancel", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickFileChooser>
	implements QuickDialog.Interpreted<QuickFileChooser> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<File>> theDirectory;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnSelect;
		private InterpretedValueSynth<ObservableAction, ObservableAction> theOnCancel;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<File>> getDirectory() {
			return theDirectory;
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnSelect() {
			return theOnSelect;
		}

		public InterpretedValueSynth<ObservableAction, ObservableAction> getOnCancel() {
			return theOnCancel;
		}

		@Override
		public void updateDialog(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			update(expressoEnv);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			super.doUpdate(expressoEnv);

			theDirectory = interpret(getDefinition().getDirectory(), ModelTypes.Value.forType(File.class));
			theOnSelect = interpret(getDefinition().getOnSelect(), ModelTypes.Action.instance());
			theOnCancel = interpret(getDefinition().getOnCancel(), ModelTypes.Action.instance());
		}

		@Override
		public QuickFileChooser create() {
			return new QuickFileChooser(getIdentity());
		}
	}

	private ModelComponentId theChosenFilesVariable;
	private ModelValueInstantiator<SettableValue<File>> theDirectoryInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnSelectInstantiator;
	private ModelValueInstantiator<ObservableAction> theOnCancelInstantiator;

	private boolean isOpen;
	private boolean isFilesSelectable;
	private boolean isDirectoriesSelectable;
	private boolean isMultiSelectable;
	private SettableValue<SettableValue<File>> theDirectory;
	private SettableValue<ObservableAction> theOnSelect;
	private SettableValue<ObservableAction> theOnCancel;
	private ObservableCollection<File> theChosenFiles;

	QuickFileChooser(Object id) {
		super(id);
		theDirectory = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<File>> parameterized(File.class))
			.build();
		theOnSelect = SettableValue.build(ObservableAction.class).build();
		theOnCancel = SettableValue.build(ObservableAction.class).build();
		theChosenFiles = ObservableCollection.build(File.class).build();
	}

	public boolean isOpen() {
		return isOpen;
	}

	public boolean isFilesSelectable() {
		return isFilesSelectable;
	}

	public boolean isDirectoriesSelectable() {
		return isDirectoriesSelectable;
	}

	public boolean isMultiSelectable() {
		return isMultiSelectable;
	}

	public SettableValue<File> getDirectory() {
		return SettableValue.flatten(theDirectory);
	}

	public ObservableAction getOnSelect() {
		return ObservableAction.flatten(theOnSelect);
	}

	public ObservableAction getOnCancel() {
		return ObservableAction.flatten(theOnCancel);
	}

	public String filesChosen(List<File> chosenFiles) {
		try (Transaction t = theChosenFiles.lock(true, null)) {
			theChosenFiles.clear();
			theChosenFiles.addAll(chosenFiles);
		}
		String enabled = theOnSelect.get().isEnabled().get();
		if (enabled == null)
			theOnSelect.get().act(null);
		return enabled;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		isOpen = myInterpreted.getDefinition().isOpen();
		isFilesSelectable = myInterpreted.getDefinition().isFilesSelectable();
		isDirectoriesSelectable = myInterpreted.getDefinition().isDirectoriesSelectable();
		isMultiSelectable = myInterpreted.getDefinition().isMultiSelectable();
		theChosenFilesVariable = myInterpreted.getDefinition().getChosenFilesVariable();
		theDirectoryInstantiator = myInterpreted.getDirectory() == null ? null : myInterpreted.getDirectory().instantiate();
		theOnSelectInstantiator = myInterpreted.getOnSelect().instantiate();
		theOnCancelInstantiator = myInterpreted.getOnCancel() == null ? null : myInterpreted.getOnCancel().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theDirectoryInstantiator != null)
			theDirectoryInstantiator.instantiate();
		theOnSelectInstantiator.instantiate();
		if (theOnCancelInstantiator != null)
			theOnCancelInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		if (theDirectoryInstantiator != null)
			theDirectory.set(theDirectoryInstantiator.get(myModels), null);
		else {
			SettableValue<File> dir = SettableValue.build(File.class).build();
			if (theDirectory.get() != null)
				dir.set(theDirectory.get().get(), null);
			theDirectory.set(dir, null);
		}
		theOnSelect.set(theOnSelectInstantiator.get(myModels), null);
		theOnCancel.set(theOnCancelInstantiator == null ? ObservableAction.DO_NOTHING : theOnCancelInstantiator.get(myModels), null);
		ExFlexibleElementModelAddOn.satisfyElementValue(theChosenFilesVariable, myModels, theChosenFiles);
	}

	@Override
	public QuickFileChooser copy(ExElement parent) {
		QuickFileChooser copy = (QuickFileChooser) super.copy(parent);

		copy.theDirectory = SettableValue.build(theDirectory.getType()).build();
		copy.theOnSelect = SettableValue.build(ObservableAction.class).build();
		copy.theOnCancel = SettableValue.build(ObservableAction.class).build();
		copy.theChosenFiles = ObservableCollection.build(File.class).build();

		return copy;
	}
}

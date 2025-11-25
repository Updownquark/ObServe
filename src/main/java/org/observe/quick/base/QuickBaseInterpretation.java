package org.observe.quick.base;

import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickWidget;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** {@link QonfigInterpretation} for the Quick-Base toolkit */
public class QuickBaseInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Quick-Base";

	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String BASE = "Quick-Base v0.1";

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		// Simple widgets
		interpreter.createWith(QuickLabel.LABEL, QuickLabel.Def.class, ExElement.creator(QuickLabel.Def::new));
		interpreter.createWith(QuickTextField.TEXT_FIELD, QuickTextField.Def.class, ExElement.creator(QuickTextField.Def::new));
		interpreter.createWith(QuickCheckBox.CHECK_BOX, QuickCheckBox.Def.class, ExElement.creator(QuickCheckBox.Def::new));
		interpreter.createWith(QuickToggleButton.TOGGLE_BUTTON, QuickToggleButton.Def.class, ExElement.creator(QuickToggleButton.Def::new));
		interpreter.createWith(QuickRadioButton.RADIO_BUTTON, QuickRadioButton.Def.class, ExElement.creator(QuickRadioButton.Def::new));
		interpreter.createWith(QuickButton.BUTTON, QuickButton.Def.class, ExElement.creator(QuickButton.Def::new));
		interpreter.createWith(QuickFileButton.FILE_BUTTON, QuickFileButton.Def.class, ExElement.creator(QuickFileButton.Def::new));
		interpreter.createWith(QuickComboBox.COMBO_BOX, QuickComboBox.Def.class, ExElement.creator(QuickComboBox.Def::new));
		interpreter.createWith(QuickSlider.SLIDER, QuickSlider.Def.class, ExElement.creator(QuickSlider.Def::new));
		interpreter.createWith(QuickSpinner.SPINNER, QuickSpinner.Def.class, ExElement.creator(QuickSpinner.Def::new));
		interpreter.createWith(QuickColorChooser.COLOR_CHOOSER, QuickColorChooser.Def.class, ExElement.creator(QuickColorChooser.Def::new));
		interpreter.createWith(QuickRadioButtons.RADIO_BUTTONS, QuickRadioButtons.Def.class, ExElement.creator(QuickRadioButtons.Def::new));
		interpreter.createWith(QuickToggleButtons.TOGGLE_BUTTONS, QuickToggleButtons.Def.class,
			ExElement.creator(QuickToggleButtons.Def::new));
		interpreter.createWith(QuickTextArea.TEXT_AREA, QuickTextArea.Def.class, ExElement.creator(QuickTextArea.Def::new));
		interpreter.createWith(QuickProgressBar.PROGRESS_BAR, QuickProgressBar.Def.class, ExElement.creator(QuickProgressBar.Def::new));
		interpreter.createWith(DynamicStyledDocument.DYNAMIC_STYLED_DOCUMENT, DynamicStyledDocument.Def.class,
			ExElement.creator(DynamicStyledDocument.Def::new));
		interpreter.createWith(StyledDocument.TEXT_STYLE, StyledDocument.TextStyleElement.Def.class,
			ExElement.creator(StyledDocument.TextStyleElement.Def::new));
		interpreter.createWith(QuickSpacer.SPACER, QuickSpacer.Def.class, ExElement.creator(QuickSpacer.Def::new));
		interpreter.createWith(QuickSeparator.SEPARATOR, QuickSeparator.Def.class, ExElement.creator(QuickSeparator.Def::new));
		interpreter.createWith(QuickCustomComponent.CUSTOM_COMPONENT, QuickCustomComponent.Def.class,
			ExElement.creator(QuickCustomComponent.Def::new));

		// Containers
		interpreter.createWith(QuickVariableContainer.MultiWidget.MULTI_WIDGET, QuickVariableContainer.MultiWidget.Def.class,
			ExElement.creator(QuickVariableContainer.MultiWidget.Def::new));
		interpreter.createWith(QuickBox.BOX, QuickBox.Def.class, ExElement.creator(QuickBox.Def::new));
		interpreter.createWith(QuickFieldPanel.FIELD_PANEL, QuickFieldPanel.Def.class, ExElement.creator(QuickFieldPanel.Def::new));
		interpreter.createWith(QuickField.FIELD, QuickField.Def.class, ExAddOn.creator(QuickWidget.Def.class, QuickField.Def::new));
		interpreter.createWith(QuickPostField.POST_FIELD, QuickPostField.Def.class, ExElement.creator(QuickPostField.Def::new));
		interpreter.createWith(QuickSplit.SPLIT, QuickSplit.Def.class, ExElement.creator(QuickSplit.Def::new));
		interpreter.createWith(QuickScrollPane.SCROLL, QuickScrollPane.Def.class, ExElement.creator(QuickScrollPane.Def::new));

		// Layouts
		interpreter.createWith(QuickInlineLayout.INLINE_LAYOUT, QuickInlineLayout.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickInlineLayout.Def::new));
		interpreter.createWith(QuickSimpleLayout.SIMPLE_LAYOUT, QuickSimpleLayout.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickSimpleLayout.Def::new));
		interpreter.createWith(QuickSimpleLayout.SIMPLE_LAYOUT_CHILD, QuickSimpleLayout.Child.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickSimpleLayout.Child.Def::new));
		interpreter.createWith(QuickBorderLayout.BORDER_LAYOUT, QuickBorderLayout.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickBorderLayout.Def::new));
		interpreter.createWith(QuickBorderLayout.Child.BORDER_LAYOUT_CHILD, QuickBorderLayout.Child.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickBorderLayout.Child.Def::new));
		interpreter.createWith(QuickGridFlowLayout.GRID_FLOW_LAYOUT, QuickGridFlowLayout.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickGridFlowLayout.Def::new));
		interpreter.createWith(QuickLayerLayout.LAYER_LAYOUT, QuickLayerLayout.Def.class,
			ExAddOn.creator(QuickWidget.Def.class, QuickLayerLayout.Def::new));

		// Table
		interpreter.createWith(QuickTable.TABLE, QuickTable.Def.class, ExElement.creator(QuickTable.Def::new));
		interpreter.createWith(QuickTableColumn.SingleColumnSet.COLUMN, QuickTableColumn.SingleColumnSet.Def.class,
			ExElement.<ValueTyped<?>, ValueTyped.Def<?>, QuickTableColumn.SingleColumnSet.Def> creator(
				(Class<ValueTyped.Def<?>>) (Class<?>) ValueTyped.Def.class, QuickTableColumn.SingleColumnSet.Def::new));
		interpreter.createWith(QuickTableColumn.VariableColumns.VARIABLE_COLUMNS, QuickTableColumn.VariableColumns.Def.class,
			ExElement.<ValueTyped<?>, ValueTyped.Def<?>, QuickTableColumn.VariableColumns.Def> creator(
				(Class<ValueTyped.Def<?>>) (Class<?>) ValueTyped.Def.class, QuickTableColumn.VariableColumns.Def::new));
		interpreter.createWith(QuickTableColumn.ColumnEditing.COLUMN_EDITING, QuickTableColumn.ColumnEditing.Def.class,
			ExElement.<QuickTableColumn.TableColumnSet<?>, QuickTableColumn.TableColumnSet.Def<?>, QuickTableColumn.ColumnEditing.Def> creator(
				(Class<QuickTableColumn.TableColumnSet.Def<?>>) (Class<?>) QuickTableColumn.TableColumnSet.Def.class,
				QuickTableColumn.ColumnEditing.Def::new));
		interpreter.createWith(QuickTableColumn.ColumnEditType.RowModifyEditType.MODIFY,
			QuickTableColumn.ColumnEditType.RowModifyEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowModifyEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith(QuickTableColumn.ColumnEditType.RowReplaceEditType.REPLACE,
			QuickTableColumn.ColumnEditType.RowReplaceEditType.Def.class,
			session -> new QuickTableColumn.ColumnEditType.RowReplaceEditType.Def((QonfigAddOn) session.getFocusType(),
				(QuickTableColumn.ColumnEditing.Def) session.getElementRepresentation()));
		interpreter.createWith(ValueAction.Single.SINGLE_VALUE_ACTION, ValueAction.Single.Def.class,
			ExElement.creator(ValueAction.Single.Def::new));
		interpreter.createWith(ValueAction.Multi.MULTI_VALUE_ACTION, ValueAction.Multi.Def.class,
			ExElement.creator(ValueAction.Multi.Def::new));

		// Tabs
		interpreter.createWith(QuickTabs.TABS, QuickTabs.Def.class, ExElement.creator(QuickTabs.Def::new));
		interpreter.createWith(QuickTabs.Tab.TAB, QuickTabs.Tab.Def.class, ExAddOn.creator(QuickWidget.Def.class, QuickTabs.Tab.Def::new));

		// Tree
		interpreter.createWith(QuickTree.TREE, QuickTree.Def.class, ExElement.creator(QuickTree.Def::new));
		interpreter.createWith(DynamicTreeModel.DYNAMIC_TREE_MODEL, DynamicTreeModel.Def.class,
			ExElement.creator(DynamicTreeModel.Def::new));
		interpreter.createWith(StaticTreeNode.TREE_NODE, StaticTreeNode.Def.class, ExElement.creator(StaticTreeNode.Def::new));

		// Dialogs
		interpreter.createWith(QuickInfoDialog.INFO_DIALOG, QuickInfoDialog.Def.class, ExElement.creator(QuickInfoDialog.Def::new));
		interpreter.createWith(QuickConfirm.CONFIRM, QuickConfirm.Def.class, ExElement.creator(QuickConfirm.Def::new));
		interpreter.createWith(QuickFileChooser.FILE_CHOOSER, QuickFileChooser.Def.class, ExElement.creator(QuickFileChooser.Def::new));
		interpreter.createWith(GeneralDialog.GENERAL_DIALOG, GeneralDialog.Def.class, ExElement.creator(GeneralDialog.Def::new));

		// Menus
		interpreter.createWith(QuickMenuContainer.MENU_CONTAINER, QuickMenuContainer.Def.class,
			ExAddOn.creator(QuickMenuContainer.Def::new));
		interpreter.createWith(QuickMenuBar.MENU_BAR, QuickMenuBar.Def.class, ExElement.creator(QuickMenuBar.Def::new));
		interpreter.createWith(QuickMenu.MENU, QuickMenu.Def.class, ExElement.creator(QuickMenu.Def::new));
		interpreter.createWith(QuickMenuItem.MENU_ITEM, QuickMenuItem.Def.class, ExElement.creator(QuickMenuItem.Def::new));
		interpreter.createWith(QuickCheckBoxMenuItem.CHECK_BOX_MENU_ITEM, QuickCheckBoxMenuItem.Def.class,
			ExElement.creator(QuickCheckBoxMenuItem.Def::new));

		// Dragging
		interpreter.createWith(QuickTransfer.TRANSFER_SOURCE, QuickTransfer.TransferSource.Def.class,
			ExElement.creator(QuickTransfer.TransferSource.Def::new));
		interpreter.createWith(QuickTransfer.TRANSFER_ACCEPT, QuickTransfer.TransferAccept.Def.class,
			ExElement.creator(QuickTransfer.TransferAccept.Def::new));
		interpreter.createWith(QuickTransfer.AS_OBJECT, QuickTransfer.AsObject.Def.class,
			ExElement.creator(QuickTransfer.AsObject.Def::new));
		interpreter.createWith(QuickTransfer.AS_TEXT, QuickTransfer.AsText.Def.class, ExElement.creator(QuickTransfer.AsText.Def::new));
		return interpreter;
	}
}

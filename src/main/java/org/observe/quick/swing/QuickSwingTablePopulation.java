package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.event.CellEditorListener;
import javax.swing.text.StyledDocument;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.Iconized;
import org.observe.quick.KeyCode;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickValueWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.ValueAction;
import org.observe.quick.base.ValueAction.Multi;
import org.observe.quick.base.ValueAction.Single;
import org.observe.quick.swing.QuickSwingColumnSet.TabularContext;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.util.ObservableUtils;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.observe.util.swing.CellDecorator;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.ComponentPropertyManager;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.ModelRow;
import org.observe.util.swing.ObservableCellEditor;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.AbstractObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.ObservableStyledDocument;
import org.observe.util.swing.ObservableTextArea;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.Alert;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.CollectionWidgetBuilder;
import org.observe.util.swing.PanelPopulation.ComboEditor;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.LabelEditor;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SimpleComponentEditor;
import org.observe.util.swing.Shading;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.collect.CollectionUtils;
import org.qommons.io.Format;

/** Code to populate Quick-sourced tables in Java swing */
class QuickSwingTablePopulation {
	static class InterpretedSwingTableColumn<R, R2, C> {
		private final QuickTableColumn<R, C> theColumn;
		private final Function<R2, R> theReverse;
		final CategoryRenderStrategy<R2, C> theCRS;

		public InterpretedSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column, boolean virtual,
			TriConsumer<R2, R, QuickWidget> update,
			Function<R2, R> reverse, TabularContext<R> context, Observable<?> until, Supplier<? extends ComponentEditor<?, ?>> parent,
				Map<Object, QuickSwingPopulator<QuickWidget>> swingRenderers, Map<Object, QuickSwingPopulator<QuickWidget>> swingEditors,
				QuickSwingTransfer dragging)
					throws ModelInstantiationException {
			theColumn = column;
			theReverse = reverse;
			QuickSwingTableColumn<R, R2, C> renderer = new QuickSwingTableColumn<>(update, theReverse, quickParent, column, virtual,
				context, parent, swingRenderers, swingEditors);
			theCRS = renderer.getCRS();
			Integer width = column.getWidth();
			if (column.getMinWidth() != null)
				theCRS.withWidth("min", column.getMinWidth());
			else if (width != null)
				theCRS.withWidth("min", width);
			if (column.getPrefWidth() != null)
				theCRS.withWidth("pref", column.getPrefWidth());
			else if (width != null)
				theCRS.withWidth("pref", width);
			if (column.getMaxWidth() != null)
				theCRS.withWidth("max", column.getMaxWidth());
			else if (width != null)
				theCRS.withWidth("max", width);

			theCRS.withRenderer(renderer);
			theCRS.withCellTooltip(renderer::getTooltip);
			// The listeners may take a performance hit, so only add listening if they're there
			boolean[] mouseKey = new boolean[2];
			for (QuickWidget r : column.getRenderers()) {
				for (QuickEventListener listener : r.getEventListeners()) {
					if (listener instanceof QuickMouseListener)
						mouseKey[0] = true;
					else if (listener instanceof QuickKeyListener)
						mouseKey[1] = true;
				}
			}
			if (mouseKey[0])
				theCRS.addMouseListener(renderer);
			else
				theCRS.removeMouseListener(renderer);
			if (mouseKey[1])
				theCRS.withKeyListener(renderer);
			else
				theCRS.withKeyListener(null);
			if (column.getEditing() != null)
				theCRS.withMutation(renderer::mutation);
			dragging.configureTransferSources(cell -> {
				context.activeValue.set(theReverse.apply(cell.getModelValue()));
				context.selected.set(cell.isSelected());
				context.rowIndex.set(cell.getRowIndex());
				context.columnIndex.set(cell.getColumnIndex());
			}, column.getTransferSources(), theCRS::dragSource);
			dragging.configureTransferAccepters(cell -> {
				context.activeValue.set(theReverse.apply(cell.getModelValue()));
				context.selected.set(cell.isSelected());
				context.rowIndex.set(cell.getRowIndex());
				context.columnIndex.set(cell.getColumnIndex());
			}, column.getTransferAccepters(), theCRS.getMutator()::dragAccept);
		}

		public QuickTableColumn<R, C> getColumn() {
			return theColumn;
		}

		public CategoryRenderStrategy<R2, C> getCRS() {
			return theCRS;
		}
	}

	static class QuickSwingRenderComponent<R, R2, C> {
		private final QuickSwingRenderer<R, R2, C> theSwingRenderer;
		private final QuickWidget theRenderer;
		private final boolean isVirtual;
		private ObservableCellRenderer<R2, C> theDelegate;
		private SimpleComponentEditor<?, ?> theComponent;
		private Runnable thePreRender;
		private ObservableValue<String> theTooltip;

		QuickSwingRenderComponent(QuickSwingRenderer<R, R2, C> swingRenderer, QuickWidget renderer, boolean virtual) {
			theSwingRenderer = swingRenderer;
			theRenderer = renderer;
			isVirtual = virtual;
		}

		public QuickSwingRenderer<R, R2, C> getSwingRenderer() {
			return theSwingRenderer;
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		public void preRender() {
			if (thePreRender != null)
				thePreRender.run();
		}

		public String getTooltip() {
			return theTooltip == null ? null : theTooltip.get();
		}

		public Component getCellRendererComponent(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
			if (thePreRender != null)
				thePreRender.run();
			Component render;
			if (theDelegate != null)
				render = theDelegate.getCellRendererComponent(parent, cell, ctx);
			else if (theComponent != null) {
				render = theComponent.getComponent();
			} else { // No renderer specified, use default
				theDelegate = ObservableCellRenderer.formatted(String::valueOf);
				render = theDelegate.getCellRendererComponent(parent, cell, ctx);
			}
			return render;
		}

		boolean hasRenderer() {
			return theDelegate != null || theComponent != null;
		}

		void delegateTo(ObservableCellRenderer<R2, C> delegate) {
			theDelegate = delegate;
		}

		void renderWith(SimpleComponentEditor<?, ?> component, Runnable preRender) {
			theComponent = component;
			thePreRender = preRender;
		}

		void setTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
		}

		public ObservableValue<String> getTooltipValue() {
			return theTooltip;
		}

		public boolean isVirtual() {
			return isVirtual;
		}
	}

	static class QuickSwingRenderer<R, R2, C> extends AbstractObservableCellRenderer<R2, C> {
		final TriConsumer<R2, R, QuickWidget> theUpdate;
		final Function<R2, R> theReverse;
		private final QuickWidget theQuickParent;
		private final Supplier<? extends ComponentEditor<?, ?>> theParent;
		private final List<QuickSwingRenderComponent<R, R2, C>> theRenderers;
		private final SimpleObservable<Void> theRenderUntil;
		private final QuickWithBackground.BackgroundContext theRendererContext;
		protected final TabularContext<R> theRenderTableContext;
		private final Supplier<C> theValue;

		Supplier<String> isEnabled;
		private boolean isRenderingEnabled;

		private boolean isUpdating;
		private JLabel theDefaultRenderer;

		QuickSwingRenderer(TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse, QuickWidget quickParent, Supplier<C> value,
			List<QuickWidget> renderers, TabularContext<R> ctx, Supplier<? extends ComponentEditor<?, ?>> parent,
				Map<Object, QuickSwingPopulator<QuickWidget>> swingRenderers, boolean virtual) throws ModelInstantiationException {
			theUpdate = update;
			theReverse = reverse;
			theQuickParent = quickParent;
			theParent = parent;
			theValue = value;
			theRenderers = new ArrayList<>(renderers.size());
			theRenderTableContext = ctx;
			theRenderUntil = new SimpleObservable<>();

			theRendererContext = new QuickWithBackground.BackgroundContext.Default();
			for (QuickWidget renderer : renderers) {
				renderer.setContext(theRendererContext);
				QuickSwingRenderComponent<R, R2, C> component = new QuickSwingRenderComponent<>(this, renderer, virtual);
				swingRenderers.get(renderer.getIdentity()).populate(new SwingCellPopulator<>(component), renderer);
				if (component.hasRenderer())
					theRenderers.add(component);
			}
		}

		protected JComponent getOwner() {
			ComponentEditor<?, ?> parentEditor = theParent.get();
			if (parentEditor == null)
				return null;
			else if (parentEditor.getEditor() instanceof JComponent)
				return (JComponent) parentEditor.getEditor();
			else if (parentEditor.getComponent() instanceof JComponent)
				return (JComponent) parentEditor.getComponent();
			else
				return null;
		}

		protected void onOwner(Consumer<JComponent> action) {
			JComponent owner = getOwner();
			if (owner != null)
				action.accept(owner);
		}

		public List<QuickSwingRenderComponent<R, R2, C>> getRenderers() {
			return Collections.unmodifiableList(theRenderers);
		}

		public ComponentEditor<?, ?> getParent() {
			return theParent.get();
		}

		public QuickWidget getQuickParent() {
			return theQuickParent;
		}

		public TabularContext<R> getContext() {
			return theRenderTableContext;
		}

		public boolean isUpdating() {
			return isUpdating;
		}

		public void setEnabled(Supplier<String> enabled, boolean renderEnabled) {
			isEnabled = enabled;
			isRenderingEnabled = renderEnabled;
		}

		public boolean isRenderingEnabled() {
			return isRenderingEnabled;
		}

		@Override
		public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
			setCellContext(cell, theRenderTableContext, false);
			for (QuickSwingRenderComponent<R, R2, C> component : theRenderers) {
				if (component.getRenderer().isVisible().get()) {
					if (component.getRenderer() instanceof QuickTextWidget) {
						component.preRender();
						String text = ((QuickTextWidget<C>) component.getRenderer()).getCurrentText();
						theRenderUntil.onNext(null);
						return text;
					} else
						break;
				}
			}
			C colValue = theValue.get();
			return colValue == null ? "" : colValue.toString();
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
			isUpdating = true;
			try {
				setCellContext(cell, theRenderTableContext, true);
				for (QuickSwingRenderComponent<R, R2, C> component : theRenderers) {
					if (component.getRenderer().isVisible().get()) {
						Component render = component.getCellRendererComponent(parent, cell, ctx);
						theRenderUntil.onNext(null);
						return render;
					}
				}
				if (theDefaultRenderer == null)
					theDefaultRenderer = new JLabel();
				theDefaultRenderer.setText(String.valueOf(cell.getCellValue()));
				return theDefaultRenderer;
			} finally {
				isUpdating = false;
			}
		}

		void setRowContext(ModelRow<? extends R2> row, TabularContext<R> tableCtx, boolean withValue, Object cause) {
			R reversed = theReverse.apply(row.getModelValue());
			if (withValue || tableCtx.activeValue.get() != reversed) {
				// Had an issue with trees where the path was actually the same, but not identical.
				// If the active value is eventing, that almost certainly means it's being populated with the same value currently.
				if (tableCtx.activeValue.isEventing()) {
					if (!Objects.equals(tableCtx.activeValue.get(), reversed))
						theQuickParent.reporting()
						.error("Got some mixed up observables: " + tableCtx.activeValue.get() + "->" + reversed);
				} else
					tableCtx.activeValue.set(reversed, cause);
			}
			if (tableCtx.selected.get().booleanValue() != row.isSelected())
				tableCtx.selected.set(row.isSelected(), cause);
			if (tableCtx.rowIndex.get().intValue() != row.getRowIndex())
				tableCtx.rowIndex.set(row.getRowIndex(), cause);
		}

		void setCellContext(ModelCell<? extends R2, ? extends C> cell, TabularContext<R> tableCtx, boolean withValue) {
			try (Causable.CausableInUse cause = Causable.cause()) {
				setRowContext(cell, tableCtx, withValue, cause);
				if (tableCtx.columnIndex.get().intValue() != cell.getColumnIndex())
					tableCtx.columnIndex.set(cell.getColumnIndex(), cause);
				if (tableCtx == theRenderTableContext && theRendererContext != null) {
					theRendererContext.isHovered().set(cell.isCellHovered(), cause);
					theRendererContext.isFocused().set(cell.isCellFocused(), cause);
					if (cell.isCellHovered()) {
						theRendererContext.isPressed().set(theQuickParent.isPressed().get(), cause);
						theRendererContext.isRightPressed().set(theQuickParent.isRightPressed().get(), cause);
					} else {
						theRendererContext.isPressed().set(false, cause);
						theRendererContext.isRightPressed().set(false, cause);
					}
				}
				if (isRenderingEnabled) {
					String enabled = isEnabled == null ? null : isEnabled.get();
					if (enabled != null && !enabled.equals(cell.isEnabled()))
						cell.setEnabled(enabled);
				}
			}
		}

		String getTooltip(ModelCell<? extends R2, ? extends C> cell) {
			setCellContext(cell, theRenderTableContext, false);
			return getTooltip();
		}

		String getTooltip() {
			String tt = null;
			for (QuickSwingRenderComponent<R, R2, C> component : theRenderers) {
				if (component.getRenderer().isVisible().get()) {
					tt = component.getTooltip();
					break;
				}
			}
			if (tt != null)
				return tt;
			String enabled = null;
			if (isEnabled != null && isRenderingEnabled) {
				enabled = isEnabled.get();
			}
			if (enabled == null && theRenderers instanceof QuickValueWidget)
				enabled = ((QuickValueWidget<?>) theRenderers).getDisabled().get();
			if (enabled != null)
				return enabled;
			return null;
		}
	}

	private static class CompositeCellEditorComponent<R, R2, C> {
		private final QuickSwingTableColumn<R, R2, C> theTableColumn;
		private final QuickWidget theEditor;
		private final SettableValue<Boolean> isVisible;

		private ObservableCellEditor<R2, C> theCellEditor;
		private ObservableValue<String> theTooltip;

		public CompositeCellEditorComponent(QuickSwingTableColumn<R, R2, C> swingColumn, QuickWidget editor,
			SettableValue<Boolean> visible) {
			theTableColumn = swingColumn;
			theEditor = editor;
			isVisible = visible;
		}

		public QuickSwingTableColumn<R, R2, C> getTableColumn() {
			return theTableColumn;
		}

		public QuickWidget getEditor() {
			return theEditor;
		}

		public boolean isVisible() {
			return Boolean.TRUE.equals(isVisible.get());
		}

		ObservableCellEditor<R2, C> getCellEditor() {
			return theCellEditor;
		}

		public String getTooltip() {
			return theTooltip == null ? null : theTooltip.get();
		}

		public ObservableValue<String> getTooltipValue() {
			return theTooltip;
		}

		void withEditor(ObservableCellEditor<R2, C> editor) {
			theCellEditor = editor;
		}

		void setTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
		}
	}

	private static class QuickCompositeCellEditor<R, C> implements ObservableCellEditor<R, C> {
		private final List<? extends CompositeCellEditorComponent<?, R, C>> theComponents;
		private int theCurrentEditor;

		QuickCompositeCellEditor(List<? extends CompositeCellEditorComponent<?, R, C>> components) {
			theComponents = components;
			theCurrentEditor = -1;
		}

		boolean checkEditor() {
			for (int i = 0; i < theComponents.size(); i++) {
				if (Boolean.TRUE.equals(theComponents.get(i).isVisible())) {
					theCurrentEditor = i;
					return true;
				}
			}
			theCurrentEditor = -1;
			return false;
		}

		@Override
		public boolean isCellEditable(EventObject anEvent) {
			return theCurrentEditor >= 0 && theComponents.get(theCurrentEditor).getCellEditor().isCellEditable(anEvent);
		}

		@Override
		public boolean shouldSelectCell(EventObject anEvent) {
			return theCurrentEditor >= 0 && theComponents.get(theCurrentEditor).getCellEditor().isCellEditable(anEvent);
		}

		@Override
		public void addCellEditorListener(CellEditorListener l) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().addCellEditorListener(l);
		}

		@Override
		public void removeCellEditorListener(CellEditorListener l) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().removeCellEditorListener(l);
		}

		@Override
		public ModelCell<R, C> getEditingCell() {
			return theComponents.get(theCurrentEditor).getCellEditor().getEditingCell();
		}

		@Override
		public ObservableCellEditor<R, C> decorate(CellDecorator<R, C> decorator) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().decorate(decorator);
			return this;
		}

		@Override
		public ObservableCellEditor<R, C> modify(Function<Component, Runnable> modify) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().modify(modify);
			return this;
		}

		@Override
		public ObservableCellEditor<R, C> withCellTooltip(Function<? super ModelCell<? extends R, ? extends C>, String> tooltip) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().withCellTooltip(tooltip);
			return this;
		}

		@Override
		public ObservableCellEditor<R, C> withHovering(IntSupplier hoveredRow, IntSupplier hoveredColumn) {
			for (CompositeCellEditorComponent<?, R, C> component : theComponents)
				component.getCellEditor().withHovering(hoveredRow, hoveredColumn);
			return this;
		}

		@Override
		public ObservableCellEditor<R, C> withClicks(int clickCount) {
			return this;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			return theComponents.get(theCurrentEditor).getCellEditor().getTableCellEditorComponent(table, value, isSelected, row, column);
		}

		@Override
		public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
			return theComponents.get(theCurrentEditor).getCellEditor().getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf,
				row);
		}

		@Override
		public Component getCellEditorComponent(Component component, ModelCell<R, C> cell, ObservableCollection<R> model,
			CategoryRenderStrategy<R, C> rendering) {
			return theComponents.get(theCurrentEditor).getCellEditor().getCellEditorComponent(component, cell, model, rendering);
		}

		@Override
		public boolean stopCellEditing() {
			return theComponents.get(theCurrentEditor).getCellEditor().stopCellEditing();
		}

		@Override
		public void cancelCellEditing() {
			theComponents.get(theCurrentEditor).getCellEditor().cancelCellEditing();
		}

		@Override
		public Object getCellEditorValue() {
			return theComponents.get(theCurrentEditor).getCellEditor().getCellEditorValue();
		}

		public String getTooltip() {
			if (theCurrentEditor >= 0)
				return theComponents.get(theCurrentEditor).getTooltip();
			return null;
		}
	}

	static class QuickSwingTableColumn<R, R2, C> extends QuickSwingRenderer<R, R2, C>
	implements CategoryMouseListener<R2, C>, CategoryKeyListener<R2, C> {
		private final QuickTableColumn<R, C> theColumn;
		private final CategoryRenderStrategy<R2, C> theCRS;

		private final TabularContext<R> theEditContext;
		private final List<CompositeCellEditorComponent<R, R2, C>> theEditors;

		QuickSwingTableColumn(TriConsumer<R2, R, QuickWidget> update, Function<R2, R> reverse, QuickWidget quickParent,
			QuickTableColumn<R, C> column, boolean virtual, QuickSwingColumnSet.TabularContext<R> ctx,
			Supplier<? extends ComponentEditor<?, ?>> parent, Map<Object, QuickSwingPopulator<QuickWidget>> swingRenderers,
				Map<Object, QuickSwingPopulator<QuickWidget>> swingEditors) throws ModelInstantiationException {
			super(update, reverse, quickParent, column.getValue(), column.getRenderers(), ctx, parent, swingRenderers, virtual);
			theColumn = column;

			theCRS = new CategoryRenderStrategy<>(column.getName().get(), TypeTokens.getRawType(column.getType()), row -> {
				try (Causable.CausableInUse cause = Causable.cause()) {
					setRowContext(row, ctx, false, cause);
					return column.getValue().get();
				}
			}, true);

			if (theColumn.getEditing() != null) {
				QuickTableColumn.ColumnEditing<R, C> editing = theColumn.getEditing();
				theEditContext = new TabularContext<>(editing.getEditRowValue(), editing.getRowIndex(), editing.getColumnIndex(),
					editing.isSelected());
				theEditors = new ArrayList<>(theColumn.getEditing().getEditors().size());
				for (int e = 0; e < theColumn.getEditing().getEditors().size(); e++) {
					QuickWidget editor = theColumn.getEditing().getEditors().get(e);
					QuickSwingPopulator<QuickWidget> swingEditor = swingEditors.get(editor.getIdentity());
					if (swingEditor != null) {
						SettableValue<Boolean> viz = theColumn.getEditing().getEditorVisibilities().get(e);
						CompositeCellEditorComponent<R, R2, C> component = new CompositeCellEditorComponent<>(this, editor, viz);
						swingEditor.populate(new SwingCellPopulator<>(component), editor);
						if (component.getCellEditor() != null)
							theEditors.add(component);
					}
				}
			} else {
				theEditors = Collections.emptyList();
				theEditContext = null;
			}

			if (!theEditors.isEmpty())
				setEnabled(() -> {
					String msg = theColumn.getEditing().isEditable().get();
					if (msg != null)
						return msg;
					boolean hasEditor = false;
					for (CompositeCellEditorComponent<R, R2, C> editor : theEditors) {
						if (editor.isVisible()) {
							hasEditor = true;
							break;
						}
					}
					if (!hasEditor)
						return "No editor can edit this value";
					return null;
				}, theColumn.getEditing().isRenderingEnabled());
		}

		public QuickTableColumn<R, C> getColumn() {
			return theColumn;
		}

		public CategoryRenderStrategy<R2, C> getCRS() {
			return theCRS;
		}

		SettableValue<C> getEditorValue() {
			return theColumn.getEditing().getEditColumnValue();
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
			try {
				Component rendered = super.renderCell(parent, cell, ctx);
				onOwner(o -> o.setCursor(rendered.getCursor()));
				return rendered;
			} catch (RuntimeException e) {
				theColumn.reporting().error("Error rendering cell", e);
				return null;
			}
		}

		void mutation(CategoryRenderStrategy<R2, C>.CategoryMutationStrategy mutation) {
			if (!theEditors.isEmpty()) {
				QuickCompositeCellEditor<R2, C> cellEditor = new QuickCompositeCellEditor<>(theEditors);
				mutation.editableIf(cell -> {
					setCellContext(cell, theRenderTableContext, false);
					if (isEnabled != null) {
						if (isEnabled.get() != null)
							return false;
					} else if (theColumn.getEditing().getFilteredColumnEditValue().isEnabled().get() != null)
						return false;
					if (cellEditor.checkEditor()) {
						setEditCell(cell);
						return true;
					} else
						return false;
				});
				if (theColumn.getEditing().isAcceptable() != null) {
					mutation.filterAccept((rowEl, colValue) -> {
						render(rowEl.get(), colValue, theEditContext);
						getEditorValue().set(colValue, null);
						return theColumn.getEditing().isAcceptable().get();
					});
				} else {
					mutation.filterAccept((rowEl, colValue) -> {
						render(rowEl.get(), colValue, theEditContext);
						return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(colValue);
					});
				}
				if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowModifyEditType) {
					QuickTableColumn.ColumnEditType.RowModifyEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowModifyEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.mutateAttribute((rowValue, colValue) -> {
						render(rowValue, colValue, theEditContext);
						getEditorValue().set(colValue, null);
						editType.getCommit().act(null);
					});
					mutation.withRowUpdate(editType.isRowUpdate());
				} else if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowReplaceEditType) {
					QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C>) theColumn
						.getEditing().getType();
					if (LambdaUtils.isTrivial(theReverse)) {
						mutation.withRowValueSwitch((rowValue, colValue) -> {
							render(rowValue, colValue, theEditContext);
							getEditorValue().set(colValue, null);
							return (R2) editType.getReplacement().get();
						});
					} else if (theUpdate != null) {
						mutation.mutateAttribute((rowValue, colValue) -> {
							render(rowValue, colValue, theEditContext);
							getEditorValue().set(colValue, null);
							theUpdate.accept(rowValue, editType.getReplacement().get(), getQuickParent());
						});
					} else {
						theColumn.getEditing().reporting().error("Cannot support edit type " + theColumn.getEditing().getType()
							+ " for a mapped table without an update scheme");
					}
				} else
					theColumn.getEditing().reporting().error("Unhandled column edit type: " + theColumn.getEditing().getType());
				mutation.withEditorTooltip(cell -> cellEditor.getTooltip());
				mutation.withEditor(cellEditor);
				Integer clicks = theColumn.getEditing().getClicks();
				if (clicks != null) {
					for (CompositeCellEditorComponent<R, R2, C> editor : theEditors)
						editor.getCellEditor().withClicks(clicks);
				}
			}
		}

		void render(R2 rowValue, C colValue, TabularContext<R> ctx) {
			R rv = theReverse.apply(rowValue);
			if (ctx.activeValue.get() != rv)
				ctx.activeValue.set(rv, null);
			if (ctx.rowIndex.get().intValue() != 0)
				ctx.rowIndex.set(0, null);
			if (ctx.columnIndex.get().intValue() != 0)
				ctx.columnIndex.set(0, null);
			if (ctx.selected.get())
				ctx.selected.set(false, null);
		}

		void setEditCell(ModelCell<? extends R2, ? extends C> cell) {
			setCellContext(cell, theEditContext, false);
			// theEditContext.activeValue.set(theReverse.apply(cell.getModelValue()), null);
		}

		String isEditAcceptable(ModelCell<R2, C> cell, C editValue) {
			if (cell == null)
				return "Nothing being edited";
			setEditCell(cell);
			getEditorValue().set(editValue, null);
			return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(editValue);
		}

		@Override
		public void keyPressed(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
			if (code == null)
				return;
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
						QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
						if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
							continue;
						keyL.isAltPressed().set(e.isAltDown(), e);
						keyL.isCtrlPressed().set(e.isControlDown(), e);
						keyL.isShiftPressed().set(e.isShiftDown(), e);
						keyL.getEventKeyCode().set(code, e);
						if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void keyReleased(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
			if (code == null)
				return;
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
						QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
						if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
							continue;
						keyL.isAltPressed().set(e.isAltDown(), e);
						keyL.isCtrlPressed().set(e.isControlDown(), e);
						keyL.isShiftPressed().set(e.isShiftDown(), e);
						keyL.getEventKeyCode().set(code, e);
						if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void keyTyped(ModelCell<? extends R2, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			char ch = e.getKeyChar();
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickKeyListener.QuickKeyTypedListener) {
						QuickKeyListener.QuickKeyTypedListener keyL = (QuickKeyListener.QuickKeyTypedListener) listener;
						if (keyL.getCharFilter() != 0 && keyL.getCharFilter() != ch)
							continue;
						keyL.isAltPressed().set(e.isAltDown(), e);
						keyL.isCtrlPressed().set(e.isControlDown(), e);
						keyL.isShiftPressed().set(e.isShiftDown(), e);
						keyL.getTypedChar().set(ch, e);
						if (!keyL.testFilter())
							continue;
						keyL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public boolean isMovementListener() {
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener)
						return true;
				}
			}
			return false;
		}

		@Override
		public void mouseClicked(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseClickListener) {
						QuickMouseListener.QuickMouseClickListener mouseL = (QuickMouseListener.QuickMouseClickListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						else if (mouseL.getClickCount() > 0 && e.getClickCount() != mouseL.getClickCount())
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						mouseL.getEventButton().set(eventButton, e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void mousePressed(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMousePressedListener) {
						QuickMouseListener.QuickMousePressedListener mouseL = (QuickMouseListener.QuickMousePressedListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						mouseL.getEventButton().set(eventButton, e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void mouseReleased(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseReleasedListener) {
						QuickMouseListener.QuickMouseReleasedListener mouseL = (QuickMouseListener.QuickMouseReleasedListener) listener;
						if (mouseL.getButton() != null && mouseL.getButton() != eventButton)
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						mouseL.getEventButton().set(eventButton, e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void mouseEntered(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Enter)
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void mouseExited(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Exit)
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}

		@Override
		public void mouseMoved(ModelCell<? extends R2, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext, true);
			String tt = getTooltip();
			for (QuickSwingRenderComponent<R, R2, C> renderer : getRenderers()) {
				if (!renderer.getRenderer().isVisible().get())
					continue;
				for (QuickEventListener listener : renderer.getRenderer().getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
						QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
						if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Move)
							continue;
						mouseL.isAltPressed().set(e.isAltDown(), e);
						mouseL.isCtrlPressed().set(e.isControlDown(), e);
						mouseL.isShiftPressed().set(e.isShiftDown(), e);
						mouseL.getEventX().set(e.getX(), e);
						mouseL.getEventY().set(e.getY(), e);
						if (!mouseL.testFilter())
							continue;
						mouseL.getAction().act(e);
					}
				}
			}
			String newTT = getTooltip();
			if (!Objects.equals(tt, newTT))
				onOwner(o -> o.setToolTipText(newTT));
		}
	}

	static class SwingCellPopulator<R, R2, C>
	implements PanelPopulation.PartialPanelPopulatorImpl<Container, SwingCellPopulator<R, R2, C>> {
		private final QuickSwingRenderComponent<R, R2, C> theRenderer;
		private final CompositeCellEditorComponent<R, R2, C> theEditor;

		Color nonSelectionBG;
		Color nonSelectionFG;
		Color selectionBG;
		Color selectionFG;

		public SwingCellPopulator(QuickSwingRenderComponent<R, R2, C> renderer) {
			theRenderer = renderer;
			theEditor = null;
			UIDefaults uiValues = UIManager.getDefaults();
			nonSelectionBG = uiValues.getColor("Table.background");
			nonSelectionFG = uiValues.getColor("Table.foreground");
			selectionBG = uiValues.getColor("Table.selectionBackground");
			selectionFG = uiValues.getColor("Table.selectionForeground");
		}

		public SwingCellPopulator(CompositeCellEditorComponent<R, R2, C> editor) {
			theEditor = editor;
			theRenderer = null;

			UIDefaults uiValues = UIManager.getDefaults();
			nonSelectionBG = uiValues.getColor("Table.background");
			nonSelectionFG = uiValues.getColor("Table.foreground");
			selectionBG = uiValues.getColor("Table.selectionBackground");
			selectionFG = uiValues.getColor("Table.selectionForeground");
		}

		SwingCellPopulator<R, R2, C> unsupported(String message) {
			(theEditor != null ? theEditor.getEditor() : theRenderer.getRenderer()).reporting()//
			.warn(message + " unsupported for cell " + (theRenderer != null ? "renderer" : "editor") + " holder");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return unsupported("Glass pane");
		}

		@Override
		public Container getContainer() {
			throw new IllegalStateException(
				"Container retrieval unsupported for cell " + (theRenderer != null ? "renderer" : "editor") + " holder");
		}

		@Override
		public Component decorate(Component c) {
			return c;
		}

		@Override
		public boolean supportsShading() {
			return false;
		}

		@Override
		public SwingCellPopulator<R, R2, C> withShading(Shading shading) {
			return unsupported("Shading");
		}

		@Override
		public boolean isSyntheticRenderer() {
			return theRenderer.isVirtual();
		}

		@Override
		public SwingCellPopulator<R, R2, C> withFieldName(ObservableValue<String> fieldName) {
			return unsupported("Field name");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyFieldLabel(Consumer<FontAdjuster> font) {
			return unsupported("Field label");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostLabel(ObservableValue<String> postLabel) {
			return unsupported("Post label");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostButton(String buttonText, ObservableAction action,
			Consumer<ButtonEditor<JButton, ?>> modify) {
			return unsupported("Post button");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPostContent(Consumer<PanelPopulator<JPanel, ?>> content) {
			return unsupported("Post content");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withFont(Consumer<FontAdjuster> font) {
			return unsupported("Font");
		}

		@Override
		public Container getEditor() {
			throw new IllegalStateException(
				"Container retrieval unsupported for cell " + (theRenderer != null ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> disableWith(ObservableValue<String> disabled) {
			return unsupported("Enablement");
		}

		@Override
		public SwingCellPopulator<R, R2, C> visibleWhen(ObservableValue<Boolean> visible) {
			return unsupported("Visibility");
		}

		@Override
		public SwingCellPopulator<R, R2, C> removeWhen(Observable<?> remove) {
			return unsupported("removeWhen");
		}

		@Override
		public SwingCellPopulator<R, R2, C> addNextAt(int componentIndex) {
			return unsupported("addNextAt");
		}

		@Override
		public SwingCellPopulator<R, R2, C> fill() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, R2, C> fillV() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, R2, C> decorate(Consumer<ComponentDecorator> decoration) {
			return unsupported("Decorate");
		}

		@Override
		public SwingCellPopulator<R, R2, C> repaintOn(Observable<?> repaint) {
			return unsupported("Repaint");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyEditor(Consumer<? super Container> modify) {
			return unsupported("General editor modifier");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyComponent(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public SwingCellPopulator<R, R2, C> modifyAssociatedComponents(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public void modifyAssociatedComponent(Component component) {
		}

		@Override
		public Component getComponent() {
			throw new IllegalStateException(
				"Container retrieval unsupported for cell " + (theRenderer != null ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withLayoutConstraints(Object constraints) {
			return unsupported("Layout constraints for cell renderer holder");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
			return unsupported("Popup menu");
		}

		@Override
		public SwingCellPopulator<R, R2, C> onMouse(Consumer<MouseEvent> onMouse) {
			return unsupported("Mouse events");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withName(String name) {
			return unsupported("Name");
		}

		@Override
		public SwingCellPopulator<R, R2, C> withTooltip(ObservableValue<String> tooltip) {
			return unsupported("Tooltip");
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return ObservableValue.of(null);
		}

		@Override
		public ObservableValue<String> getTooltipContents() {
			return null;
		}

		@Override
		public Observable<?> getUntil() {
			if (theRenderer != null && theRenderer.getRenderer() != null)
				return theRenderer.getRenderer().onDestroy();
			else
				return Observable.empty();
		}

		@Override
		public void doAdd(SimpleComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (theRenderer != null)
				theRenderer.renderWith(field, field::reset);
			else {
				withGenericEditor(field);
			}
		}

		@Override
		public void doAdd(int offset, SimpleComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			unsupported("Indexed add");
		}

		void withGenericEditor(ComponentEditor<?, ?> field) {
			SettableValue<C> editValue = theEditor.getTableColumn().getEditorValue();
			ObservableValue<String> fieldTooltip = field.getTooltipContents();
			SettableValue<String> editTooltip = SettableValue.create();
			field.withTooltip(fieldTooltip == null ? editTooltip//
				: ObservableValue.firstValue(tt -> tt != null, null, fieldTooltip, editTooltip));
			ObservableCellEditor<R2, C> editor = ObservableCellEditor.<R2, C, Component> createGenericEditor(field.getComponent(), //
				(component, cell, editorValue, tooltip) -> {
					editTooltip.set(tooltip);
					return ObservableUtils.link(editorValue, editValue);
				}, 2);
			theEditor.withEditor(editor);
		}

		boolean isManaged(Component c, String property) {
			for (PropertyChangeListener listener : c.getPropertyChangeListeners(property)) {
				if (listener instanceof ComponentPropertyManager)
					return true;
			}
			return false;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<LabelEditor<JLabel, ?>> modify) {
			if (theRenderer != null) {
				LabelRenderEditor editor = new LabelRenderEditor(theRenderer.getSwingRenderer());
				if (modify != null)
					modify.accept(editor);
				JLabel[] label = new JLabel[1];
				PanelPopulation.PartialPanelPopulatorImpl.super.addLabel(fieldName, field, format, tf -> {
					if (modify != null)
						modify.accept(tf);
					label[0] = tf.getEditor();
				});

				ObservableCellRenderer<R2, C> delegate = new AbstractObservableCellRenderer<R2, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
						return format.apply(field.get());
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
						if (!isManaged(label[0], "background")) {
							if (cell.getRowIndex() >= 0) {
								label[0].setOpaque(true);
								label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
							} else // For combo boxes, the row index is -1 and the label should not be opaque
								label[0].setOpaque(false);
						}
						if (!isManaged(label[0], "foreground"))
							label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
						F fieldV = field.get();
						label[0].setText(format.apply(fieldV));
						String enabled = cell.isEnabled();
						if (enabled == null && theRenderer.getRenderer() instanceof QuickValueWidget)
							enabled = ((QuickValueWidget<?>) theRenderer.getRenderer()).getDisabled().get();
						label[0].setEnabled(enabled == null);
						editor.decorate(label[0]);
						// I'm adding this condition. It wasn't here before, but then I had an issue
						// with a combo table renderer's cells looking enabled when they were disabled.
						if (!theRenderer.getSwingRenderer().isRenderingEnabled())
							cell.setEnabled(null); // Don't let the super class muck with our style
						return label[0];
					}
				};
				theRenderer.delegateTo(delegate);
			} else
				unsupported("Label");
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addIcon(String fieldName, ObservableValue<Icon> icon,
			Consumer<ComponentEditor<JLabel, ?>> modify) {
			if (theRenderer != null) {
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.<R2, C> formatted(c -> "").setIcon(cell -> {
					return icon.get();
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theRenderer.getSwingRenderer());
				if (modify != null)
					modify.accept(editor);
				theRenderer.delegateTo(delegate);
			} else
				PanelPopulation.PartialPanelPopulatorImpl.super.addIcon(fieldName, icon, modify);
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<Object> action, Consumer<ComponentEditor<JLabel, ?>> modify) {
			if (theRenderer != null) {
				JLabel[] label = new JLabel[1];
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.linkRenderer(cell -> {
					label[0].setOpaque(true);
					if (!isManaged(label[0], "background"))
						label[0].setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(label[0], "foreground"))
						label[0].setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					label[0].setEnabled(cell.isEnabled() == null);
					F fieldValue = field.get();
					cell.setEnabled(null); // Don't let the super class muck with our style
					return format.apply(fieldValue);
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theRenderer.getSwingRenderer());
				if (modify != null)
					modify.accept(editor);
				label[0] = editor.getEditor();
				theRenderer.delegateTo(delegate);
			} else
				PanelPopulation.PartialPanelPopulatorImpl.super.addLink(fieldName, field, format, action, modify);
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<ButtonEditor<JCheckBox, ?>> modify) {
			if (theRenderer != null) {
				JCheckBox check = new JCheckBox();
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.checkRenderer(check, cell -> {
					check.setOpaque(true);
					if (!isManaged(check, "background"))
						check.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(check, "foreground"))
						check.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					return Boolean.TRUE.equals(field.get());
				});
				ButtonRenderEditor<JCheckBox, ?> editor = new ButtonRenderEditor<>(null, theRenderer.getSwingRenderer());
				if (modify != null)
					modify.accept(editor);
				theRenderer.delegateTo(delegate);
			} else {
				if (TypeTokens.getRawType(TypeTokens.get().unwrap(theEditor.getTableColumn().getColumn().getType())) != boolean.class)
					theEditor.getEditor().reporting().error("Check box editor can only be used for boolean-type columns, not "
						+ theEditor.getTableColumn().getColumn().getType());
				else {
					JCheckBox check = new JCheckBox();
					ButtonRenderEditor<JCheckBox, ?> fieldEditor = new ButtonRenderEditor<>(null,
						ObservableCellEditor.createCheckBoxEditor(check, null, cell -> {
							check.setOpaque(true);
							if (!isManaged(check, "background"))
								check.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
							if (!isManaged(check, "foreground"))
								check.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
						}), check);
					if (modify != null)
						modify.accept(fieldEditor);
					theEditor.withEditor(fieldEditor.getCellEditor());
				}
			}
			return this;
		}

		@Override
		public SwingCellPopulator<R, R2, C> addButton(String buttonText, ObservableAction action,
			Consumer<ButtonEditor<JButton, ?>> modify) {
			ButtonRenderEditor<JButton, ?>[] editor = new SwingCellPopulator.ButtonRenderEditor[1];
			if (theRenderer != null) {
				JButton button = new JButton();
				ObservableCellRenderer<R2, C> delegate = ObservableCellRenderer.buttonRenderer(button, cell -> {
					if (!isManaged(button, "background"))
						button.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(button, "foreground"))
						button.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
					return editor[0].theButtonText == null ? null : editor[0].theButtonText.get();
				});
				editor[0] = new ButtonRenderEditor<>(buttonText, delegate);
				delegate.modify(comp -> editor[0].decorateButton((JButton) comp));
				if (modify != null)
					modify.accept(editor[0]);
				theRenderer.delegateTo(delegate);
			} else {
				JButton button = new JButton();
				editor[0] = new ButtonRenderEditor<>(buttonText, ObservableCellEditor.createButtonCellEditor(colValue -> {
					editor[0].decorateButton(button);
					return editor[0].getButtonText().get();
				}, button, cell -> {
					if (!isManaged(button, "background"))
						button.setBackground(cell.isSelected() ? selectionBG : nonSelectionBG);
					if (!isManaged(button, "foreground"))
						button.setForeground(cell.isSelected() ? selectionFG : nonSelectionFG);
				}, cell -> {
					action.act(null);
					return cell.getCellValue();
				}), button);
				if (modify != null)
					modify.accept(editor[0]);
				theEditor.withEditor(editor[0].getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<ComponentEditor<ObservableTextField<F>, ?>> modify) {
			if (theRenderer != null)
				PanelPopulation.PartialPanelPopulatorImpl.super.addTextField(fieldName, field, format, modify);
			else {
				ObservableTextField<C>[] textField = new ObservableTextField[1];
				ObservableCellEditor<R, C> cellEditor = ObservableCellEditor.createTextEditor((Format<C>) format, tf -> textField[0] = tf);
				FieldRenderEditor<ObservableTextField<C>> fieldEditor = new FieldRenderEditor<>(cellEditor, textField[0]);
				if (modify != null)
					modify.accept((ComponentEditor<ObservableTextField<F>, ?>) (ComponentEditor<?, ?>) fieldEditor);
				theEditor.withEditor(fieldEditor.getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc,
			Consumer<ComponentEditor<ObservableTextArea<F>, ?>> modify) {
			if (theRenderer != null) {
				FieldRenderEditor<ObservableTextArea<F>> editor = new FieldRenderEditor<>(theRenderer.getSwingRenderer());
				if (modify != null)
					modify.accept(editor);
				ObservableTextArea<F>[] textArea = new ObservableTextArea[1];
				SimpleObservable<Void> renderUntil = new SimpleObservable<>();
				PanelPopulation.PartialPanelPopulatorImpl.super.addStyledTextArea(fieldName, doc, tf -> {
					if (modify != null)
						modify.accept(tf);
					textArea[0] = tf.getEditor();
				});
				textArea[0].setMargin(new Insets(0, 0, 0, 0));

				ObservableCellRenderer<R2, C> delegate = new AbstractObservableCellRenderer<R2, C>() {
					@Override
					public String renderAsText(ModelCell<? extends R2, ? extends C> cell) {
						return doc.toString();
					}

					@Override
					protected Component renderCell(Component parent, ModelCell<? extends R2, ? extends C> cell, CellRenderContext ctx) {
						editor.decorate(textArea[0]);
						doc.refresh(null);
						ObservableStyledDocument.synchronize(doc, ((StyledDocument) textArea[0].getDocument()), renderUntil);
						textArea[0].setDocument(textArea[0].getDocument());
						renderUntil.onNext(null);
						return textArea[0];
					}
				};
				theRenderer.delegateTo(delegate);
			} else
				PanelPopulation.PartialPanelPopulatorImpl.super.addStyledTextArea(fieldName, doc, modify);
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, R2, C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<C> values;
			if (availableValues instanceof ObservableCollection)
				values = (ObservableCollection<C>) availableValues;
			else
				values = ObservableCollection.of((List<C>) availableValues);
			if (theRenderer != null)
				PanelPopulation.PartialPanelPopulatorImpl.super.addComboField(fieldName, value, availableValues, modify);
			else {
				JComboBox<C> combo = new JComboBox<>();
				ComboRenderEditor editor = new ComboRenderEditor(
					ObservableCellEditor.createComboEditor(String::valueOf, combo, (editCell, until) -> {
						return values;
					}), combo);
				if (modify != null)
					modify.accept((ComboEditor<F, ?>) editor);
				theEditor.withEditor(editor.getCellEditor());
			}
			return this;
		}

		abstract class AbstractFieldRenderEditor<COMP extends Component, E extends AbstractFieldRenderEditor<COMP, E>>
		implements ComponentEditor<COMP, E> {
			private final ObservableCellRenderer<R2, C> theCellRenderer;
			private final ObservableCellEditor<R2, C> theCellEditor;
			private final COMP theEditorComponent;

			protected AbstractFieldRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				theCellRenderer = cellRenderer;
				theCellEditor = null;
				theEditorComponent = null;
			}

			protected AbstractFieldRenderEditor(ObservableCellEditor<R2, C> cellEditor, COMP component) {
				theCellRenderer = null;
				theCellEditor = cellEditor;
				theEditorComponent = component;
			}

			public ObservableCellRenderer<R2, C> getCellRenderer() {
				return theCellRenderer;
			}

			public ObservableCellEditor<R2, C> getCellEditor() {
				return theCellEditor;
			}

			E unsupported(String message) {
				theRenderer.getRenderer().reporting()
				.warn(message + " unsupported for cell " + (theCellRenderer == null ? "editor" : "renderer"));
				return (E) this;
			}

			@Override
			public Component decorate(Component c) {
				return c;
			}

			@Override
			public E withTooltip(ObservableValue<String> tooltip) {
				if (theCellRenderer != null)
					theRenderer.setTooltip(tooltip);
				else
					theEditor.setTooltip(tooltip);
				return (E) this;
			}

			@Override
			public ObservableValue<String> getTooltip() {
				if (theRenderer != null)
					return theRenderer.getTooltipValue();
				else
					return theEditor.getTooltipValue();
			}

			@Override
			public ObservableValue<String> getTooltipContents() {
				return getTooltip();
			}

			@Override
			public Observable<?> getUntil() {
				return SwingCellPopulator.this.getUntil();
			}

			@Override
			public E withFieldName(ObservableValue<String> fieldName) {
				return unsupported("Field name");
			}

			@Override
			public E modifyFieldLabel(Consumer<FontAdjuster> font) {
				return unsupported("Field label");
			}

			@Override
			public E withFont(Consumer<FontAdjuster> font) {
				if (theCellRenderer != null)
					theCellRenderer.decorate((cell, deco) -> font.accept(deco));
				else
					theCellEditor.decorate((cell, deco) -> font.accept(deco));
				return (E) this;
			}

			@Override
			public COMP getEditor() {
				return theEditorComponent;
			}

			@Override
			public E disableWith(ObservableValue<String> disabled) {
				// Disablement is unsupported, but don't throw a fit
				return (E) this;
			}

			@Override
			public E visibleWhen(ObservableValue<Boolean> visible) {
				// Visibility is unsupported, but don't throw a fit
				return (E) this;
			}

			@Override
			public E removeWhen(Observable<?> remove) {
				return unsupported("removeWhen");
			}

			@Override
			public E fill() {
				return unsupported("Fill");
			}

			@Override
			public E fillV() {
				return unsupported("Fill");
			}

			@Override
			public E decorate(Consumer<ComponentDecorator> decoration) {
				if (theCellRenderer != null)
					theCellRenderer.decorate((cell, deco) -> decoration.accept(deco));
				else
					theCellEditor.decorate((cell, deco) -> decoration.accept(deco));
				return (E) this;
			}

			@Override
			public E repaintOn(Observable<?> repaint) {
				unsupported("Repaint");
				return (E) this;
			}

			@Override
			public E modifyEditor(Consumer<? super COMP> modify) {
				if (theCellRenderer != null) {
					theCellRenderer.modify(comp -> {
						modify.accept((COMP) comp);
						return null;
					});
				} else {
					theCellEditor.modify(comp -> {
						modify.accept((COMP) comp);
						return null;
					});
				}
				return (E) this;
			}

			@Override
			public E modifyComponent(Consumer<Component> component) {
				if (theCellRenderer != null) {
					theCellRenderer.modify(comp -> {
						component.accept(comp);
						return null;
					});
				} else {
					theCellEditor.modify(comp -> {
						component.accept(comp);
						return null;
					});
				}
				return (E) this;
			}

			@Override
			public E modifyAssociatedComponents(Consumer<Component> component) {
				if (theCellRenderer != null)
					theCellRenderer.modifyAssociated(component);
				return (E) this;
			}

			@Override
			public void modifyAssociatedComponent(Component component) {
			}

			@Override
			public Component getComponent() {
				unsupported("Component retrieval");
				return null;
			}

			@Override
			public Alert alert(String title, String message) {
				return theRenderer.getSwingRenderer().getParent().alert(title, message);
			}

			@Override
			public E withLayoutConstraints(Object constraints) {
				return unsupported("Layout constraints");
			}

			@Override
			public E withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
				theRenderer.getSwingRenderer().getParent().withPopupMenu(menu);
				return (E) this;
			}

			@Override
			public E onMouse(Consumer<MouseEvent> onMouse) {
				return unsupported("Mouse events");
			}

			@Override
			public E withName(String name) {
				return (E) this;
			}

			@Override
			public E withPostLabel(ObservableValue<String> postLabel) {
				return unsupported("Post label");
			}

			@Override
			public E withPostButton(String buttonText, ObservableAction action, Consumer<ButtonEditor<JButton, ?>> modify) {
				return unsupported("Post button");
			}

			@Override
			public E withPostContent(Consumer<PanelPopulator<JPanel, ?>> content) {
				return unsupported("Post content");
			}
		}

		class FieldRenderEditor<COMP extends Component> extends AbstractFieldRenderEditor<COMP, FieldRenderEditor<COMP>> {
			FieldRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
			}

			FieldRenderEditor(ObservableCellEditor<R2, C> cellEditor, COMP editorComponent) {
				super(cellEditor, editorComponent);
			}
		}

		class LabelRenderEditor extends AbstractFieldRenderEditor<JLabel, LabelRenderEditor>
		implements LabelEditor<JLabel, LabelRenderEditor> {
			private ObservableValue<? extends Icon> theIcon;

			LabelRenderEditor(ObservableCellEditor<R2, C> cellEditor, JLabel editorComponent) {
				super(cellEditor, editorComponent);
			}

			LabelRenderEditor(ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
			}

			@Override
			public LabelRenderEditor withIcon(ObservableValue<? extends Icon> icon) {
				theIcon = icon;
				return this;
			}

			@Override
			public Component decorate(Component c) {
				super.decorate(c);
				Icon icon;
				try {
					icon = theIcon == null ? null : theIcon.get();
				} catch (RuntimeException e) {
					theRenderer.getRenderer().reporting().error(e.toString(), e);
					icon = null;
				}
				if (icon != null && !c.isEnabled()) {
					icon = UIManager.getLookAndFeel().getDisabledIcon((JLabel) c, icon);
				}
				((JLabel) c).setIcon(icon);
				return c;
			}
		}

		class ButtonRenderEditor<B extends AbstractButton, E extends ButtonRenderEditor<B, E>> extends AbstractFieldRenderEditor<B, E>
		implements ButtonEditor<B, E> {
			ObservableValue<String> theButtonText;
			private ObservableValue<? extends Icon> theIcon;
			private ObservableValue<String> theDisabled;

			ButtonRenderEditor(String buttonText, ObservableCellRenderer<R2, C> cellRenderer) {
				super(cellRenderer);
				theButtonText = ObservableValue.of(buttonText);
			}

			ButtonRenderEditor(String buttonText, ObservableCellEditor<R2, C> cellEditor, B editorComponent) {
				super(cellEditor, editorComponent);
				theButtonText = ObservableValue.of(buttonText);
			}

			@Override
			public E withIcon(ObservableValue<? extends Icon> icon) {
				theIcon = icon;
				return (E) this;
			}

			@Override
			public E withText(ObservableValue<String> text) {
				theButtonText = text;
				return (E) this;
			}

			public ObservableValue<String> getButtonText() {
				return theButtonText;
			}

			@Override
			public E disableWith(ObservableValue<String> disabled) {
				if (theDisabled == null)
					theDisabled = disabled;
				else {
					ObservableValue<String> old = theDisabled;
					theDisabled = ObservableValue.firstValue(msg -> msg != null, () -> null, old, disabled);
				}
				return (E) this;
			}

			public Runnable decorateButton(JButton button) {
				button.setIcon(theIcon == null ? null : theIcon.get());
				String disabled = theDisabled == null ? null : theDisabled.get();
				button.setEnabled(disabled == null);
				if (disabled != null)
					button.setToolTipText(disabled);
				return null;
			}
		}

		class ComboRenderEditor extends AbstractFieldRenderEditor<JComboBox<C>, ComboRenderEditor>
		implements ComboEditor<C, ComboRenderEditor> {
			private Function<? super C, String> theValueTooltip;
			private IntSupplier theHoveredItem;

			public ComboRenderEditor(ObservableCellEditor<R2, C> cellEditor, JComboBox<C> editorComponent) {
				super(cellEditor, editorComponent);
			}

			void setHoveredItem(IntSupplier hoveredItem) {
				theHoveredItem = hoveredItem;
			}

			@Override
			public ComboRenderEditor renderWith(ObservableCellRenderer<C, C> renderer) {
				getCellEditor().modify(combo -> {
					((JComboBox<C>) combo).setRenderer(new ListCellRenderer<C>() {
						@Override
						public Component getListCellRendererComponent(JList<? extends C> list, C value, int index, boolean isSelected,
							boolean cellHasFocus) {
							boolean hovered = theHoveredItem != null && theHoveredItem.getAsInt() == index;
							ModelCell<C, C> cell = new ModelCell.Default<>(() -> value, value, index, 0, isSelected, cellHasFocus, hovered,
								hovered, true, true);
							if (index >= 0 || theEditor.getTableColumn().isRenderingEnabled()) {
								ModelCell<R2, C> editCell = getCellEditor().getEditingCell();
								if (editCell != null) {
									theEditor.getTableColumn().setEditCell(editCell);
									String enabled = theEditor.getTableColumn().isEditAcceptable(getCellEditor().getEditingCell(), value);
									cell.setEnabled(enabled);
								}
							}
							return renderer.getCellRendererComponent(list, cell, CellRenderContext.DEFAULT);
						}
					});
					return null;
				});
				return this;
			}

			@Override
			public ComboRenderEditor withValueTooltip(Function<? super C, String> tooltip) {
				theEditor.setTooltip(theEditor.getTableColumn().getEditorValue().map(tooltip));
				theValueTooltip = tooltip;
				return this;
			}

			@Override
			public String getTooltip(C value) {
				return theValueTooltip == null ? null : theValueTooltip.apply(value);
			}
		}
	}

	static <R> QuickSwingTableAction<R, ValueAction.Single<R>> interpretValueAction(ValueAction.Single.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return new QuickSwingTableAction<R, ValueAction.Single<R>>() {
			@Override
			public <R2> void addAction(CollectionWidgetBuilder<R2, ?, ?> table, Function<R2, R> reverse, Single<R> action)
				throws ModelInstantiationException {
				ValueAction.SingleValueActionContext<R> ctx = new ValueAction.SingleValueActionContext.Default<>();
				action.setActionContext(ctx);
				long[] lastUpdate = new long[1];
				table.withAction(null, LambdaUtils.printableConsumer(v -> {
					if (!Objects.equals(v, ctx.getActionValue().get()))
						ctx.getActionValue().set(reverse.apply(v), null);
					action.getAction().act(null);
				}, () -> action.getAction().toString(), null), ta -> {
					ta.allowForEmpty(action.allowForEmpty());
					ta.allowForMultiple(action.allowForMultiple());
					ta.displayAsButton(action.isButton());
					ta.displayAsPopup(action.isPopup());
					ta.allowWhen(v -> {
						/* Had a problem here where updates to selection, e.g. via a model change, weren't updating action enablement.
						 * This was because the equals call in the if below didn't trigger, so the action value isn't updated,
						 * so the stamp isn't changed, so the out-of-date cached enablement was used.
						 *
						 * However, the if here serves the purpose that setting this value many times can be costly.
						 * So here's my solution.
						 */
						long now = System.currentTimeMillis();
						if (!ctx.getActionValue().isEventing()
							&& (now - lastUpdate[0] > 50 || !Objects.equals(v, ctx.getActionValue().get()))) {
							lastUpdate[0] = now;
							ctx.getActionValue().set(reverse.apply(v), null);
						}
						return action.getAction().isEnabled().get();
					}, null);
					ta.disableWith(action.getAction().isEnabled());
					ta.modifyButton(btn -> {
						btn.withText(action.getName());
						btn.withIcon(action.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
						btn.withTooltip(action.getTooltip());
					});
				});
			}
		};
	}

	static <R> QuickSwingTableAction<R, ValueAction.Multi<R>> interpretMultiValueAction(ValueAction.Multi.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		String location = interpreted.toString();
		return new QuickSwingTableAction<R, ValueAction.Multi<R>>() {
			@Override
			public <R2> void addAction(CollectionWidgetBuilder<R2, ?, ?> table, Function<R2, R> reverse, Multi<R> action)
				throws ModelInstantiationException {
				ValueAction.MultiValueActionContext<R> ctx = new ValueAction.MultiValueActionContext.Default<>();
				action.setActionContext(ctx);
				Supplier<List<R>>[] actionValues = new Supplier[1];
				long[] lastUpdate = new long[1];
				ObservableValue<String> enabled = action.getAction().isEnabled();
				table.withMultiAction(null, LambdaUtils.<List<? extends R2>> printableConsumer(values -> {
					if (!ctx.getActionValues().equals(values)) {
						try (Transaction t = ctx.getActionValues().lock(true, null)) {
							CollectionUtils.synchronize(ctx.getActionValues(), values, (av, v) -> Objects.equals(av, reverse.apply(v)))//
							.simple(reverse)//
							.rightOrder()//
							.adjust();
						}
					}
					action.getAction().act(null);
					lastUpdate[0] = System.currentTimeMillis();
					CollectionUtils.synchronize(ctx.getActionValues(), actionValues[0].get()).simple(r -> r).adjust();
				}, () -> action.getAction().toString(), null), ta -> {
					actionValues[0] = () -> QommonsUtils.map(ta.getActionItems(), reverse, true);
					ta.allowForEmpty(action.allowForEmpty());
					ta.allowForMultiple(true);
					ta.displayAsButton(action.isButton());
					ta.displayAsPopup(action.isPopup());
					ta.allowWhenMulti(values -> {
						boolean equal = ctx.getActionValues().equals(values);
						boolean cycle = enabled.isEventing();
						if (!equal && cycle) {
							/* If the enablement of an action has both internal (depending on the action values)
							 * and external (e.g. from the head section) components, a change that affects both may cause a cycle here.
							 * I believe the cycle should always resolve itself by a subsequent call to this method. */
							return "Cycle@" + location;
						}
						/* Had a problem here where updates to selection, e.g. via a model change, weren't updating action enablement.
						 * This was because the equals call in the if below didn't trigger, so the action value isn't updated,
						 * so the stamp isn't changed, so the out-of-date cached enablement was used.
						 *
						 * However, the if here serves the purpose that setting this value many times can be costly.
						 * So here's my solution.
						 */
						long now = System.currentTimeMillis();
						if (!equal || (!cycle && now - lastUpdate[0] > 3)) {
							lastUpdate[0] = now;
							try (Transaction t = ctx.getActionValues().lock(true, null)) {
								CollectionUtils.synchronize(ctx.getActionValues(), values, (av, v) -> Objects.equals(av, reverse.apply(v)))//
								.simple(reverse)//
								.commonUses(true, true)//
								.rightOrder()//
								.adjust();
							}
						}
						return action.isEnabled().get();
					}, null);
					ta.disableWith(enabled);
					ta.modifyButton(btn -> {
						btn.withText(action.getName());
						btn.withIcon(action.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
						btn.withTooltip(action.getTooltip());
					});
				});
			}
		};
	}
}

package org.observe.quick.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.KeyCode;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.TabularWidget.TabularContext;
import org.observe.quick.base.ValueAction;
import org.observe.quick.swing.QuickSwingPopulator.QuickCoreSwing;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingTableAction;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryKeyListener;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseListener;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.ObservableCellEditor;
import org.observe.util.swing.ObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.AbstractObservableCellRenderer;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.Alert;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.ComboEditor;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SliderEditor;
import org.qommons.Causable;
import org.qommons.Transformer;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.io.Format;

/** Code to populate Quick-sourced tables in Java wing */
class QuickSwingTablePopulation {
	static class InterpretedSwingTableColumn<R, C> {
		private final QuickTableColumn<R, C> theColumn;
		final CategoryRenderStrategy<R, C> theCRS;
		private ObservableCollection<InterpretedSwingTableColumn<R, ?>> theColumns;
		private ElementId theElementId;

		public InterpretedSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column, TabularContext<R> context,
			Transformer<ExpressoInterpretationException> tx, Observable<?> until, Supplier<ComponentEditor<?, ?>> parent,
			QuickSwingPopulator<QuickWidget> swingRenderer, QuickSwingPopulator<QuickWidget> swingEditor)
				throws ModelInstantiationException {
			theColumn = column;
			theCRS = new CategoryRenderStrategy<>(column.getName().get(), column.getType(), row -> {
				context.getRenderValue().set(row, null);
				return column.getValue().get();
			});

			theColumn.getName().noInitChanges().takeUntil(until).act(evt -> {
				theCRS.setName(evt.getNewValue());
				refresh();
			});
			column.getHeaderTooltip().changes().takeUntil(until).act(evt -> {
				theCRS.withHeaderTooltip(evt.getNewValue());
				refresh();
			});
			QuickSwingTableColumn<R, C> renderer = new QuickSwingTableColumn<>(quickParent, column, context, tx, until, parent,
				swingRenderer, swingEditor);
			theCRS.withRenderer(renderer);
			theCRS.withValueTooltip(renderer::getTooltip);
			// The listeners may take a performance hit, so only add listening if they're there
			boolean[] mouseKey = new boolean[2];
			for (QuickEventListener listener : column.getRenderer().getEventListeners()) {
				if (listener instanceof QuickMouseListener)
					mouseKey[0] = true;
				else if (listener instanceof QuickKeyListener)
					mouseKey[1] = true;
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
		}

		public void init(ObservableCollection<InterpretedSwingTableColumn<R, ?>> columns, ElementId id) {
			theColumns = columns;
			theElementId = id;
		}

		public CategoryRenderStrategy<R, C> getCRS() {
			return theCRS;
		}

		void refresh() {
			if (theElementId != null)
				theColumns.mutableElement(theElementId).set(this);
		}
	}

	static class QuickSwingTableColumn<R, C> extends AbstractObservableCellRenderer<R, C>
	implements CategoryMouseListener<R, C>, CategoryKeyListener<R, C> {
		private final QuickWidget theQuickParent;
		private final QuickTableColumn<R, C> theColumn;
		private final QuickWidget theRenderer;
		private final QuickWidget.WidgetContext theRendererContext;
		private final TabularWidget.TabularContext<R> theRenderTableContext;
		private final Supplier<ComponentEditor<?, ?>> theParent;
		private final SimpleObservable<Void> theRenderUntil;
		private ObservableCellRenderer<R, C> theDelegate;
		private AbstractComponentEditor<?, ?> theComponent;
		private Runnable thePreRender;

		private final QuickTableColumn.ColumnEditContext<R, C> theEditContext;
		private ObservableCellEditor<R, C> theCellEditor;

		private ObservableValue<String> theTooltip;
		private final QuickMouseListener.MouseButtonListenerContext theMouseContext;
		private final QuickKeyListener.KeyTypedContext theKeyTypeContext;
		private final QuickKeyListener.KeyCodeContext theKeyCodeContext;

		QuickSwingTableColumn(QuickWidget quickParent, QuickTableColumn<R, C> column, TabularWidget.TabularContext<R> ctx,
			Transformer<ExpressoInterpretationException> tx, Observable<?> until, Supplier<ComponentEditor<?, ?>> parent,
			QuickSwingPopulator<QuickWidget> swingRenderer, QuickSwingPopulator<QuickWidget> swingEditor)
				throws ModelInstantiationException {
			theQuickParent = quickParent;
			theColumn = column;
			theRenderer = column.getRenderer();
			theRenderTableContext = ctx;
			theParent = parent;
			theRenderUntil = new SimpleObservable<>();

			// TODO The interpretation/transformation of the renderer and editor should be done elsewhere
			if (swingRenderer != null) {
				swingRenderer.populate(new SwingCellPopulator<>(this, true, Observable.or(until, theRenderUntil)), theRenderer);

				theRendererContext = new QuickWidget.WidgetContext.Default();
				theRenderer.setContext(theRendererContext);
			} else
				theRendererContext = null;
			if (theColumn.getEditing() != null) {
				if (swingEditor != null) {
					swingEditor.populate(new SwingCellPopulator<>(this, false, Observable.or(until, theRenderUntil)),
						theColumn.getEditing().getEditor());
				}
				theEditContext = new QuickTableColumn.ColumnEditContext.Default<>(theColumn.getColumnSet().getRowType(),
					theColumn.getType(),
					theColumn.getEditing().reporting().getFileLocation().getPosition(0).toShortString());
				theColumn.getEditing().setEditorContext(theEditContext);
			} else
				theEditContext = null;

			theMouseContext = new QuickMouseListener.MouseButtonListenerContext.Default();
			theKeyTypeContext = new QuickKeyListener.KeyTypedContext.Default();
			theKeyCodeContext = new QuickKeyListener.KeyCodeContext.Default();

			if (theRenderer != null) {
				for (QuickEventListener listener : theRenderer.getEventListeners()) {
					if (listener instanceof QuickMouseListener.QuickMouseButtonListener)
						((QuickMouseListener.QuickMouseButtonListener) listener).setListenerContext(theMouseContext);
					else if (listener instanceof QuickMouseListener)
						((QuickMouseListener) listener).setListenerContext(theMouseContext);
					else if (listener instanceof QuickKeyListener.QuickKeyTypedListener)
						((QuickKeyListener.QuickKeyTypedListener) listener).setListenerContext(theKeyTypeContext);
					else if (listener instanceof QuickKeyListener.QuickKeyCodeListener)
						((QuickKeyListener.QuickKeyCodeListener) listener).setListenerContext(theKeyCodeContext);
					else
						listener.reporting().error("Unhandled cell renderer listener type: " + listener.getClass().getName());
				}
			}
		}

		public QuickTableColumn<R, C> getColumn() {
			return theColumn;
		}

		public QuickWidget getRenderer() {
			return theRenderer;
		}

		public TabularWidget.TabularContext<R> getContext() {
			return theRenderTableContext;
		}

		public ComponentEditor<?, ?> getParent() {
			return theParent.get();
		}

		void withEditor(ObservableCellEditor<R, C> editor) {
			theCellEditor = editor;
		}

		void mutation(CategoryRenderStrategy<R, C>.CategoryMutationStrategy mutation) {
			if (theColumn.getEditing() != null) {
				mutation.editableIf((rowValue, colValue) -> {
					theRenderTableContext.getRenderValue().set(rowValue, null);
					theRenderTableContext.getRowIndex().set(0, null);
					theRenderTableContext.getColumnIndex().set(0, null);
					theRenderTableContext.isSelected().set(false, null);
					return theColumn.getEditing().getFilteredColumnEditValue().isEnabled().get() == null;
				});
				mutation.filterAccept((rowEl, colValue) -> {
					theRenderTableContext.getRenderValue().set(rowEl.get(), null);
					theRenderTableContext.getRowIndex().set(0, null);
					theRenderTableContext.getColumnIndex().set(0, null);
					theRenderTableContext.isSelected().set(false, null);
					return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(colValue);
				});
				if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowModifyEditType) {
					QuickTableColumn.ColumnEditType.RowModifyEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowModifyEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.mutateAttribute((rowValue, colValue) -> {
						theEditContext.getRenderValue().set(rowValue, null);
						editType.getCommit().act(null);
					});
					mutation.withRowUpdate(editType.isRowUpdate());
				} else if (theColumn.getEditing().getType() instanceof QuickTableColumn.ColumnEditType.RowReplaceEditType) {
					QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C> editType = (QuickTableColumn.ColumnEditType.RowReplaceEditType<R, C>) theColumn
						.getEditing().getType();
					mutation.withRowValueSwitch((rowValue, colValue) -> {
						theEditContext.getRenderValue().set(rowValue, null);
						theEditContext.getEditColumnValue().set(colValue, null);
						return editType.getReplacement().get();
					});
				} else
					theColumn.getEditing().reporting().error("Unhandled column edit type: " + theColumn.getEditing().getType());
				if (theCellEditor != null)
					mutation.withEditor(theCellEditor);
				Integer clicks = theColumn.getEditing().getClicks();
				if (clicks != null)
					mutation.clicks(clicks);
			}
		}

		String isEditAcceptable(ModelCell<R, C> cell, C editValue) {
			setCellContext(cell, theRenderTableContext);
			return theColumn.getEditing().getFilteredColumnEditValue().isAcceptable(editValue);
		}

		void delegateTo(ObservableCellRenderer<R, C> delegate) {
			theDelegate = delegate;
		}

		void renderWith(AbstractComponentEditor<?, ?> component, Runnable preRender) {
			theComponent = component;
			thePreRender = preRender;
		}

		public void setTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
		}

		void setCellContext(ModelCell<? extends R, ? extends C> cell, TabularWidget.TabularContext<R> tableCtx) {
			try (Causable.CausableInUse cause = Causable.cause()) {
				tableCtx.isSelected().set(cell.isSelected(), cause);
				tableCtx.getRowIndex().set(cell.getRowIndex(), cause);
				tableCtx.getColumnIndex().set(cell.getColumnIndex(), cause);
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
			}
		}

		String getTooltip(R modelValue, C columnValue) {
			if (theTooltip == null)
				return null;
			theRenderTableContext.getRenderValue().set(modelValue, null);
			return theTooltip.get();
		}

		@Override
		public String renderAsText(ModelCell<? extends R, ? extends C> cell) {
			setCellContext(cell, theRenderTableContext);
			if (theRenderer instanceof QuickTextWidget) {
				if (thePreRender != null)
					thePreRender.run();
				String text = ((QuickTextWidget<C>) theRenderer).getCurrentText();
				theRenderUntil.onNext(null);
				return text;
			} else {
				C colValue = theColumn.getValue().get();
				return colValue == null ? "" : colValue.toString();
			}
		}

		@Override
		protected Component renderCell(Component parent, ModelCell<? extends R, ? extends C> cell, CellRenderContext ctx) {
			setCellContext(cell, theRenderTableContext);
			if (thePreRender != null)
				thePreRender.run();
			Component render;
			if (theDelegate != null)
				render = theDelegate.getCellRendererComponent(parent, cell, ctx);
			else
				render = theComponent.getComponent();
			theRenderUntil.onNext(null);
			return render;
		}

		@Override
		public void keyPressed(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
			if (code == null)
				return;
			theKeyCodeContext.getKeyCode().set(code, e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
					QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
					if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
						continue;
					else if (!keyL.getFilter().get().booleanValue())
						continue;
					keyL.getAction().act(e);
				}
			}
		}

		@Override
		public void keyReleased(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
			if (code == null)
				return;
			theKeyCodeContext.getKeyCode().set(code, e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickKeyListener.QuickKeyCodeListener) {
					QuickKeyListener.QuickKeyCodeListener keyL = (QuickKeyListener.QuickKeyCodeListener) listener;
					if (!keyL.isPressed() || (keyL.getKeyCode() != null && keyL.getKeyCode() != code))
						continue;
					else if (!keyL.getFilter().get().booleanValue())
						continue;
					keyL.getAction().act(e);
				}
			}
		}

		@Override
		public void keyTyped(ModelCell<? extends R, ? extends C> cell, KeyEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			char ch = e.getKeyChar();
			theKeyTypeContext.getTypedChar().set(ch, e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickKeyListener.QuickKeyTypedListener) {
					QuickKeyListener.QuickKeyTypedListener keyL = (QuickKeyListener.QuickKeyTypedListener) listener;
					if (keyL.getCharFilter() != 0 && keyL.getCharFilter() != ch)
						continue;
					else if (!keyL.getFilter().get().booleanValue())
						continue;
					keyL.getAction().act(e);
				}
			}
		}

		@Override
		public boolean isMovementListener() {
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseMoveListener)
					return true;
			}
			return false;
		}

		@Override
		public void mouseClicked(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getMouseButton().set(eventButton, e);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseButtonListener) {
					QuickMouseListener.QuickMouseButtonListener mouseL = (QuickMouseListener.QuickMouseButtonListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseButtonEventType.Click
						|| (mouseL.getButton() != null && mouseL.getButton() != eventButton))
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}

		@Override
		public void mousePressed(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getMouseButton().set(eventButton, e);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseButtonListener) {
					QuickMouseListener.QuickMouseButtonListener mouseL = (QuickMouseListener.QuickMouseButtonListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseButtonEventType.Press
						|| (mouseL.getButton() != null && mouseL.getButton() != eventButton))
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}

		@Override
		public void mouseReleased(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			QuickMouseListener.MouseButton eventButton = QuickCoreSwing.checkMouseEventType(e, null);
			if (eventButton == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getMouseButton().set(eventButton, e);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseButtonListener) {
					QuickMouseListener.QuickMouseButtonListener mouseL = (QuickMouseListener.QuickMouseButtonListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseButtonEventType.Release
						|| (mouseL.getButton() != null && mouseL.getButton() != eventButton))
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}

		@Override
		public void mouseEntered(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
					QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Enter)
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}

		@Override
		public void mouseExited(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
					QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Exit)
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}

		@Override
		public void mouseMoved(ModelCell<? extends R, ? extends C> cell, MouseEvent e) {
			if (cell == null)
				return;
			setCellContext(cell, theRenderTableContext);
			theMouseContext.getX().set(e.getX(), e);
			theMouseContext.getY().set(e.getY(), e);
			for (QuickEventListener listener : theRenderer.getEventListeners()) {
				if (listener instanceof QuickMouseListener.QuickMouseMoveListener) {
					QuickMouseListener.QuickMouseMoveListener mouseL = (QuickMouseListener.QuickMouseMoveListener) listener;
					if (mouseL.getEventType() != QuickMouseListener.MouseMoveEventType.Move)
						continue;
					else if (!mouseL.getFilter().get().booleanValue())
						continue;
					mouseL.getAction().act(e);
				}
			}
		}
	}

	static class SwingCellPopulator<R, C> implements PanelPopulation.PartialPanelPopulatorImpl<Container, SwingCellPopulator<R, C>> {
		private final QuickSwingTableColumn<R, C> theCell;
		private final boolean isRenderer;
		private final Observable<?> theUntil;
		private final List<Consumer<ComponentEditor<?, ?>>> theModifiers;

		public SwingCellPopulator(QuickSwingTableColumn<R, C> cell, boolean renderer, Observable<?> until) {
			theCell = cell;
			isRenderer = renderer;
			theUntil = until;
			theModifiers = new ArrayList<>();
		}

		SwingCellPopulator<R, C> unsupported(String message) {
			theCell.getRenderer().reporting().warn(message + " unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return unsupported("Glass pane");
		}

		@Override
		public Container getContainer() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public void addModifier(Consumer<ComponentEditor<?, ?>> modifier) {
			theModifiers.add(modifier);
		}

		@Override
		public void removeModifier(Consumer<ComponentEditor<?, ?>> modifier) {
			theModifiers.remove(modifier);
		}

		@Override
		public SwingCellPopulator<R, C> withFieldName(ObservableValue<String> fieldName) {
			return unsupported("Field name");
		}

		@Override
		public SwingCellPopulator<R, C> modifyFieldLabel(Consumer<FontAdjuster> font) {
			return unsupported("Field label");
		}

		@Override
		public SwingCellPopulator<R, C> withFont(Consumer<FontAdjuster> font) {
			return unsupported("Font");
		}

		@Override
		public Container getEditor() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, C> visibleWhen(ObservableValue<Boolean> visible) {
			return unsupported("Visibility");
		}

		@Override
		public SwingCellPopulator<R, C> fill() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, C> fillV() {
			return unsupported("Fill");
		}

		@Override
		public SwingCellPopulator<R, C> decorate(Consumer<ComponentDecorator> decoration) {
			return unsupported("Decorate");
		}

		@Override
		public SwingCellPopulator<R, C> repaintOn(Observable<?> repaint) {
			return unsupported("Repaint");
		}

		@Override
		public SwingCellPopulator<R, C> modifyEditor(Consumer<? super Container> modify) {
			return unsupported("General editor modifier");
		}

		@Override
		public SwingCellPopulator<R, C> modifyComponent(Consumer<Component> component) {
			return unsupported("General component modifier");
		}

		@Override
		public Component getComponent() {
			throw new IllegalStateException("Container retrieval unsupported for cell " + (isRenderer ? "renderer" : "editor") + " holder");
		}

		@Override
		public SwingCellPopulator<R, C> withLayoutConstraints(Object constraints) {
			return unsupported("Layout constraints for cell renderer holder");
		}

		@Override
		public SwingCellPopulator<R, C> withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
			return unsupported("Popup menu");
		}

		@Override
		public SwingCellPopulator<R, C> onMouse(Consumer<MouseEvent> onMouse) {
			return unsupported("Mouse events");
		}

		@Override
		public SwingCellPopulator<R, C> withName(String name) {
			return unsupported("Name");
		}

		@Override
		public SwingCellPopulator<R, C> withTooltip(ObservableValue<String> tooltip) {
			return unsupported("Tooltip");
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public <C2 extends ComponentEditor<?, ?>> C2 modify(C2 component) {
			unsupported("General component modifier");
			return component;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (isRenderer)
				theCell.renderWith(field, field::reset);
			else
				unsupported("This editor type");
		}

		@Override
		public <F> SwingCellPopulator<R, C> addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<FieldEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.formatted((r, c) -> {
					theCell.getContext().getRenderValue().set(r, null);
					F fieldValue = field.get();
					return format.apply(fieldValue);
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theCell);
				if (modify != null)
					modify.accept(editor);
				for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
					modifier.accept(editor);
				theCell.delegateTo(delegate);
			} else
				unsupported("Label");
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.<R, C> formatted(c -> "").setIcon(cell -> {
					theCell.getContext().getRenderValue().set(cell.getModelValue(), null);
					return icon.get();
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theCell);
				if (modify != null)
					modify.accept(editor);
				for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
					modifier.accept(editor);
				theCell.delegateTo(delegate);
			} else
				unsupported("Icon");
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, C> addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<Object> action, Consumer<FieldEditor<JLabel, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.linkRenderer(cell -> {
					theCell.getContext().getRenderValue().set(cell.getModelValue(), null);
					F fieldValue = field.get();
					return format.apply(fieldValue);
				});
				FieldRenderEditor<JLabel> editor = new FieldRenderEditor<>(theCell);
				if (modify != null)
					modify.accept(editor);
				for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
					modifier.accept(editor);
				theCell.delegateTo(delegate);
			} else
				unsupported("Link");
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> addCheckField(String fieldName, SettableValue<Boolean> field,
			Consumer<FieldEditor<JCheckBox, ?>> modify) {
			if (isRenderer) {
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.checkRenderer(cell -> {
					return Boolean.TRUE.equals(field.get());
				});
				FieldRenderEditor<JCheckBox> editor = new FieldRenderEditor<>(theCell);
				if (modify != null)
					modify.accept(editor);
				for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
					modifier.accept(editor);
				theCell.delegateTo(delegate);
			} else {
				if (TypeTokens.getRawType(TypeTokens.get().unwrap(theCell.getColumn().getType())) != boolean.class)
					theCell.getColumn().getEditing().getEditor().reporting()
					.error("Check box editor can only be used for boolean-type columns, not " + theCell.getColumn().getType());
				else {
					FieldRenderEditor<JCheckBox> fieldEditor = new FieldRenderEditor<>(ObservableCellEditor.createCheckBoxEditor());
					if (modify != null)
						modify.accept(fieldEditor);
					theCell.withEditor(fieldEditor.getCellEditor());
				}
			}
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> addButton(String buttonText, ObservableAction<?> action,
			Consumer<ButtonEditor<JButton, ?>> modify) {
			if (isRenderer) {
				ButtonRenderEditor[] editor = new SwingCellPopulator.ButtonRenderEditor[1];
				ObservableCellRenderer<R, C> delegate = ObservableCellRenderer.buttonRenderer(cell -> {
					return editor[0].theButtonText == null ? null : editor[0].theButtonText.get();
				});
				editor[0] = new ButtonRenderEditor(buttonText, delegate);
				delegate.modify(comp -> editor[0].decorateButton((JButton) comp));
				if (modify != null)
					modify.accept(editor[0]);
				for (Consumer<ComponentEditor<?, ?>> modifier : theModifiers)
					modifier.accept(editor[0]);
				theCell.delegateTo(delegate);
			} else {
				ButtonRenderEditor[] editor = new SwingCellPopulator.ButtonRenderEditor[1];
				editor[0] = new ButtonRenderEditor(buttonText, ObservableCellEditor.createButtonCellEditor(colValue -> {
					return editor[0].getButtonText().get();
				}, cell -> {
					action.act(null);
					return cell.getCellValue();
				}));
				if (modify != null)
					modify.accept(editor[0]);
				theCell.withEditor(editor[0].getCellEditor());
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, C> addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addTextField(fieldName, field, format, modify);
			else {
				FieldRenderEditor<ObservableTextField<C>> fieldEditor = new FieldRenderEditor<>(
					ObservableCellEditor.createTextEditor(format));
				if (modify != null)
					modify.accept((FieldEditor<ObservableTextField<F>, ?>) (FieldEditor<?, ?>) fieldEditor);
				theCell.withEditor(fieldEditor.getCellEditor());
			}
			return this;
		}

		@Override
		public SwingCellPopulator<R, C> addSlider(String fieldName, SettableValue<Double> value,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addSlider(fieldName, value, modify);
			else { // TODO
				theCell.getColumn().getEditing().getEditor().reporting().error("Slider cell editing is not implemented");
			}
			return this;
		}

		@Override
		public <F> SwingCellPopulator<R, C> addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<C> values;
			if (availableValues instanceof ObservableCollection)
				values = (ObservableCollection<C>) availableValues;
			else
				values = ObservableCollection.of(theCell.getColumn().getType(), (List<C>) availableValues);
			if (isRenderer)
				PanelPopulation.PartialPanelPopulatorImpl.super.addComboField(fieldName, value, availableValues, modify);
			else {
				ComboRenderEditor[] editor = new SwingCellPopulator.ComboRenderEditor[1];
				editor[0] = new ComboRenderEditor(ObservableCellEditor.createComboEditor(String::valueOf, (v, until) -> values));
				if (modify != null)
					modify.accept((ComboEditor<F, ?>) editor[0]);
				theCell.withEditor(editor[0].getCellEditor());
			}
			return this;
		}

		abstract class AbstractFieldRenderEditor<COMP extends Component, E extends AbstractFieldRenderEditor<COMP, E>>
		implements FieldEditor<COMP, E> {
			private final ObservableCellRenderer<R, C> theCellRenderer;
			private final ObservableCellEditor<R, C> theCellEditor;

			protected AbstractFieldRenderEditor(ObservableCellRenderer<R, C> cellRenderer) {
				theCellRenderer = cellRenderer;
				theCellEditor = null;
			}

			public AbstractFieldRenderEditor(ObservableCellEditor<R, C> cellEditor) {
				theCellRenderer = null;
				theCellEditor = cellEditor;
			}

			public ObservableCellRenderer<R, C> getCellRenderer() {
				return theCellRenderer;
			}

			public ObservableCellEditor<R, C> getCellEditor() {
				return theCellEditor;
			}

			E unsupported(String message) {
				theCell.getRenderer().reporting()
				.warn(message + " unsupported for cell " + (theCellRenderer == null ? "editor" : "renderer"));
				return (E) this;
			}

			@Override
			public E withTooltip(ObservableValue<String> tooltip) {
				theCell.setTooltip(tooltip);
				return (E) this;
			}

			@Override
			public Observable<?> getUntil() {
				return theUntil;
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
				theCellEditor.decorate((cell, deco) -> font.accept(deco));
				return (E) this;
			}

			@Override
			public COMP getEditor() {
				unsupported("Editor retrieval");
				return null;
			}

			@Override
			public E visibleWhen(ObservableValue<Boolean> visible) {
				return unsupported("Visible");
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
			public Component getComponent() {
				unsupported("Component retrieval");
				return null;
			}

			@Override
			public Alert alert(String title, String message) {
				return theCell.getParent().alert(title, message);
			}

			@Override
			public E withLayoutConstraints(Object constraints) {
				return unsupported("Layout constraints");
			}

			@Override
			public E withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
				theCell.getParent().withPopupMenu(menu);
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
			public E withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
				return unsupported("Post button");
			}
		}

		class FieldRenderEditor<COMP extends Component> extends AbstractFieldRenderEditor<COMP, FieldRenderEditor<COMP>> {
			FieldRenderEditor(ObservableCellRenderer<R, C> cellRenderer) {
				super(cellRenderer);
			}

			FieldRenderEditor(ObservableCellEditor<R, C> cellEditor) {
				super(cellEditor);
			}
		}

		class ButtonRenderEditor extends AbstractFieldRenderEditor<JButton, ButtonRenderEditor>
		implements ButtonEditor<JButton, ButtonRenderEditor> {
			ObservableValue<String> theButtonText;
			private ObservableValue<? extends Icon> theIcon;
			private ObservableValue<String> theDisabled;

			ButtonRenderEditor(String buttonText, ObservableCellRenderer<R, C> cellRenderer) {
				super(cellRenderer);
				theButtonText = ObservableValue.of(String.class, buttonText);
			}

			ButtonRenderEditor(String buttonText, ObservableCellEditor<R, C> cellEditor) {
				super(cellEditor);
				theButtonText = ObservableValue.of(String.class, buttonText);
			}

			@Override
			public ButtonRenderEditor withIcon(ObservableValue<? extends Icon> icon) {
				theIcon = icon;
				return this;
			}

			@Override
			public ButtonRenderEditor withText(ObservableValue<String> text) {
				theButtonText = text;
				return this;
			}

			public ObservableValue<String> getButtonText() {
				return theButtonText;
			}

			@Override
			public ButtonRenderEditor disableWith(ObservableValue<String> disabled) {
				if (theDisabled == null)
					theDisabled = disabled;
				else {
					ObservableValue<String> old = theDisabled;
					theDisabled = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, old, disabled);
				}
				return this;
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

			public ComboRenderEditor(ObservableCellEditor<R, C> cellEditor) {
				super(cellEditor);
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
							return renderer.getCellRendererComponent(list,
								new ModelCell.Default<>(() -> value, value, index, 0, isSelected, cellHasFocus, hovered, hovered, true,
									true, //
									theCell.isEditAcceptable(getCellEditor().getEditingCell(), value)),
								CellRenderContext.DEFAULT);
						}
					});
					return null;
				});
				return this;
			}

			@Override
			public ComboRenderEditor withValueTooltip(Function<? super C, String> tooltip) {
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
		return (table, action) -> {
			ValueAction.SingleValueActionContext<R> ctx = new ValueAction.SingleValueActionContext.Default<>(action.getValueType());
			action.setActionContext(ctx);
			table.withAction(null, v -> {
				ctx.getActionValue().set(v, null);
				action.getAction().act(null);
			}, ta -> {
				ta.allowForEmpty(false);
				ta.allowForMultiple(action.allowForMultiple());
				ta.displayAsButton(action.isButton());
				ta.displayAsPopup(action.isPopup());
				ta.allowWhen(v -> {
					ctx.getActionValue().set(v, null);
					return action.isEnabled().get();
				}, null);
				ta.modifyButton(btn -> {
					btn.withText(action.getName());
					btn.withIcon(action.getIcon());
					btn.withTooltip(action.getTooltip());
				});
			});
		};
	}

	static <R> QuickSwingTableAction<R, ValueAction.Multi<R>> interpretMultiValueAction(ValueAction.Multi.Interpreted<R, ?> interpreted,
		Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
		return (table, action) -> {
			ValueAction.MultiValueActionContext<R> ctx = new ValueAction.MultiValueActionContext.Default<>(action.getValueType());
			action.setActionContext(ctx);
			Supplier<List<R>>[] actionValues = new Supplier[1];
			table.withMultiAction(null, values -> {
				ctx.getActionValues().addAll(values);
				action.getAction().act(null);
				CollectionUtils.synchronize(ctx.getActionValues(), actionValues[0].get()).simple(r -> r).adjust();
			}, ta -> {
				actionValues[0] = ta::getActionItems;
				ta.allowForEmpty(false);
				ta.allowForMultiple(true);
				ta.displayAsButton(action.isButton());
				ta.displayAsPopup(action.isPopup());
				ta.allowWhenMulti(values -> {
					CollectionUtils.synchronize(ctx.getActionValues(), values).simple(r -> r).adjust();
					return action.isEnabled().get();
				}, null);
				ta.modifyButton(btn -> {
					btn.withText(action.getName());
					btn.withIcon(action.getIcon());
					btn.withTooltip(action.getTooltip());
				});
			});
		};
	}
}

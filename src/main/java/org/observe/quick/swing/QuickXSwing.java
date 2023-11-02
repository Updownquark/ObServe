package org.observe.quick.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JPanel;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickTableColumn;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.base.ValueAction;
import org.observe.quick.ext.QuickCollapsePane;
import org.observe.quick.ext.QuickComboButton;
import org.observe.quick.ext.QuickMultiSlider;
import org.observe.quick.ext.QuickMultiSlider.SliderBgRenderer;
import org.observe.quick.ext.QuickMultiSlider.SliderHandleRenderer;
import org.observe.quick.ext.QuickShaded;
import org.observe.quick.ext.QuickShading;
import org.observe.quick.ext.QuickTreeTable;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.MultiRangeSlider.Range;
import org.observe.util.swing.MultiRangeSlider.RangePoint;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.CollapsePanel;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.Shading;
import org.qommons.Causable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.ex.CheckedExceptionWrapper;

import com.google.common.reflect.TypeToken;

/** Swing implementation for the Quick-X toolkit */
public class QuickXSwing implements QuickInterpretation {
	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		QuickSwingPopulator.<QuickWidget, QuickShaded, QuickShaded.Interpreted> modifyForAddOn(tx, QuickShaded.Interpreted.class,
			(Class<QuickWidget.Interpreted<QuickWidget>>) (Class<?>) QuickWidget.Interpreted.class, (ao, qsp, tx2) -> {
				qsp.addModifier((comp, w) -> {
					QuickShaded shaded = w.getAddOn(QuickShaded.class);
					ObservableValue<QuickShading> shading = shaded.getShading();
					if (comp instanceof PanelPopulator) {
						PanelPopulator<?, ?> p = (PanelPopulator<?, ?>) comp;
						shading.changes().takeUntil(p.getUntil()).act(evt -> {
							try {
								p.withShading(
									evt.getNewValue() == null ? null : evt.getNewValue().createShading(w, () -> p.getEditor().repaint()));
							} catch (ModelInstantiationException e) {
								w.reporting().error(e.getMessage(), e);
							}
						});
					}
				});
			});
		tx.with(QuickCollapsePane.Interpreted.class, QuickSwingContainerPopulator.class, SwingCollapsePane::new);
		tx.with(QuickComboButton.Interpreted.class, QuickSwingPopulator.class, SwingComboButton::new);
		tx.with(QuickTreeTable.Interpreted.class, QuickSwingPopulator.class, SwingTreeTable::new);
		tx.with(QuickMultiSlider.Interpreted.class, QuickSwingPopulator.class, SwingMultiSlider::new);
	}

	static class SwingCollapsePane extends QuickSwingContainerPopulator.Abstract<QuickCollapsePane> {
		QuickSwingPopulator<QuickWidget> header;
		QuickSwingPopulator<QuickWidget> content;

		SwingCollapsePane(QuickCollapsePane.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			header = interpreted.getHeader() == null ? null : tx.transform(interpreted.getHeader(), QuickSwingPopulator.class);
			content = tx.transform(interpreted.getContents().getFirst(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulateContainer(ContainerPopulator<?, ?> panel, QuickCollapsePane quick,
			Consumer<ComponentEditor<?, ?>> component)
				throws ModelInstantiationException {
			content.populate(new CollapsePanePopulator(panel, quick, header, component), quick.getContents().getFirst());
		}

		private static class CollapsePanePopulator extends AbstractQuickContainerPopulator {
			private ContainerPopulator<?, ?> thePopulator;
			private QuickCollapsePane theCollapsePane;
			private QuickSwingPopulator<QuickWidget> theInterpretedHeader;
			private Consumer<ComponentEditor<?, ?>> theComponent;
			private Shading theShading;

			public CollapsePanePopulator(ContainerPopulator<?, ?> populator, QuickCollapsePane collapsePane,
				QuickSwingPopulator<QuickWidget> interpretedHeader, Consumer<ComponentEditor<?, ?>> component) {
				thePopulator = populator;
				theCollapsePane = collapsePane;
				theInterpretedHeader = interpretedHeader;
				theComponent = component;
			}

			@Override
			public Observable<?> getUntil() {
				return thePopulator.getUntil();
			}

			@Override
			public boolean supportsShading() {
				return true;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				theShading = shading;
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.addCollapsePanel(false, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
					cp -> populateCollapsePane(cp, panel, layout, false));
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.addCollapsePanel(true, new JustifiedBoxLayout(true).mainJustified().crossJustified(),
					cp -> populateCollapsePane(cp, panel, null, true));
				return this;
			}

			private void populateCollapsePane(CollapsePanel<JXCollapsiblePane, JPanel, ?> cp, Consumer<PanelPopulator<JPanel, ?>> panel,
				LayoutManager layout, boolean verticalLayout) {
				theComponent.accept(cp);
				cp.animated(theCollapsePane.isAnimated());
				if (theShading != null)
					cp.withShading(theShading);
				if (theInterpretedHeader != null) {
					try {
						theInterpretedHeader.populate(new CollapsePaneHeaderPopulator(cp),
							theCollapsePane.getHeader());
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}
				if (theCollapsePane.isCollapsed() != null)
					cp.withCollapsed(theCollapsePane.isCollapsed());
				if (verticalLayout)
					cp.addVPanel(panel);
				else
					cp.addHPanel(null, layout, panel);
			}
		}

		private static class CollapsePaneHeaderPopulator extends AbstractQuickContainerPopulator {
			private final PanelPopulation.CollapsePanel<?, ?, ?> thePopulator;
			private Shading theShading;

			CollapsePaneHeaderPopulator(PanelPopulation.CollapsePanel<?, ?, ?> populator) {
				thePopulator = populator;
			}

			@Override
			public boolean supportsShading() {
				return true;
			}

			@Override
			public AbstractQuickContainerPopulator withShading(Shading shading) {
				theShading = shading;
				return this;
			}

			@Override
			public Observable<?> getUntil() {
				return thePopulator.getUntil();
			}

			@Override
			public AbstractQuickContainerPopulator addHPanel(String fieldName, LayoutManager layout,
				Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.withHeader(p -> {
					if (theShading != null)
						p.withShading(theShading);
					panel.accept(p);
				});
				return this;
			}

			@Override
			public AbstractQuickContainerPopulator addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.withHeader(p -> p.addVPanel(p2 -> {
					if (theShading != null)
						p2.withShading(theShading);
					panel.accept(p2);
				}));
				return this;
			}
		}
	}

	static class SwingComboButton<T> extends QuickSwingPopulator.Abstract<QuickComboButton<T>> {
		private QuickSwingPopulator<QuickWidget> theRenderer;

		SwingComboButton(QuickComboButton.Interpreted<T, QuickComboButton<T>> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getRenderer() != null)
				theRenderer = tx.transform(interpreted.getRenderer(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickComboButton<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			ComponentEditor<?, ?>[] combo = new ComponentEditor[1];
			MultiValueRenderable.MultiValueRenderContext<T> ctx = new MultiValueRenderable.MultiValueRenderContext.Default<>(
				quick.getValues().getType());
			quick.setContext(ctx);
			TabularWidget.TabularContext<T> tableCtx = new TabularWidget.TabularContext<T>() {
				private final SettableValue<Integer> theRowIndex = SettableValue.build(int.class).withValue(0).build();
				private final SettableValue<Integer> theColumnIndex = SettableValue.build(int.class).withValue(0).build();

				@Override
				public SettableValue<T> getActiveValue() {
					return ctx.getActiveValue();
				}

				@Override
				public SettableValue<Boolean> isSelected() {
					return ctx.isSelected();
				}

				@Override
				public SettableValue<Integer> getRowIndex() {
					return theRowIndex;
				}

				@Override
				public SettableValue<Integer> getColumnIndex() {
					return theColumnIndex;
				}
			};
			quick.setContext(tableCtx);
			SettableValue<T> selectedValue = SettableValue.build(quick.getValues().getType()).build();
			QuickSwingTablePopulation.QuickSwingRenderer<T, T> renderer = theRenderer == null ? null
				: new QuickSwingTablePopulation.QuickSwingRenderer<>(quick, quick.getValues().getType(), selectedValue, quick.getRenderer(),
					tableCtx, () -> combo[0], theRenderer);
			panel.addComboButton(null, quick.getValues(), (value, cause) -> {
				ctx.getActiveValue().set(value, cause);
				quick.getAction().act(cause);
			}, cb -> {
				combo[0] = cb;
				component.accept(cb);
				cb.withText(quick.getText());
				cb.withIcon(quick.getIcon());
				if (theRenderer != null) {
					cb.renderWith(renderer);
					cb.withValueTooltip(v -> renderer.getTooltip(v, v));
				}
			});
		}
	}

	static class SwingTreeTable<T> extends QuickSwingPopulator.Abstract<QuickTreeTable<T>> {
		private final TypeToken<BetterList<T>> theRowType;
		private final QuickSwingPopulator<QuickWidget> theRenderer;
		private final QuickSwingPopulator<QuickWidget> theEditor;
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theColumnRenderers = new HashMap<>();
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theColumnEditors = new HashMap<>();
		private List<QuickSwingTableAction<BetterList<T>, ?>> interpretedActions;

		private boolean tableInitialized;

		SwingTreeTable(QuickTreeTable.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			if (interpreted.getTreeColumn() == null) {
				theRenderer = null;
				theEditor = null;
			} else {
				theRenderer = interpreted.getTreeColumn().getRenderer() == null ? null
					: tx.transform(interpreted.getTreeColumn().getRenderer(), QuickSwingPopulator.class);
				if (interpreted.getTreeColumn().getEditing() == null || interpreted.getTreeColumn().getEditing().getEditor() == null)
					theEditor = null;
				else
					theEditor = tx.transform(interpreted.getTreeColumn().getEditing().getEditor(), QuickSwingPopulator.class);
			}

			theRowType = interpreted.getValueType();
			boolean[] renderersInitialized = new boolean[1];
			Subscription sub;
			try {
				sub = interpreted.getColumns().subscribe(evt -> {
					boolean colRenderer = false;
					try {
						switch (evt.getType()) {
						case add:
							colRenderer = true;
							if (evt.getNewValue().getRenderer() != null)
								theColumnRenderers.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
							colRenderer = false;
							if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
								theColumnEditors.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
							break;
						case remove:
							theColumnRenderers.remove(evt.getOldValue().getIdentity());
							theColumnEditors.remove(evt.getOldValue().getIdentity());
							break;
						case set:
							if (evt.getOldValue().getIdentity() != evt.getNewValue().getIdentity()) {
								theColumnRenderers.remove(evt.getOldValue().getIdentity());
								theColumnEditors.remove(evt.getOldValue().getIdentity());
							}
							colRenderer = true;
							if (evt.getNewValue().getRenderer() != null)
								theColumnRenderers.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getRenderer(), QuickSwingPopulator.class));
							colRenderer = false;
							if (evt.getNewValue().getEditing() != null && evt.getNewValue().getEditing().getEditor() != null)
								theColumnEditors.put(evt.getNewValue().getIdentity(),
									tx.transform(evt.getNewValue().getEditing().getEditor(), QuickSwingPopulator.class));
							break;
						}
					} catch (ExpressoInterpretationException e) {
						if (renderersInitialized[0])
							(colRenderer ? evt.getNewValue().getRenderer() : evt.getNewValue().getEditing().getEditor()).reporting()
							.at(e.getErrorOffset()).error(e.getMessage(), e);
						else
							throw new CheckedExceptionWrapper(e);
					}
				}, true);
			} catch (CheckedExceptionWrapper e) {
				if (e.getCause() instanceof ExpressoInterpretationException)
					throw (ExpressoInterpretationException) e.getCause();
				else
					throw new ExpressoInterpretationException(e.getMessage(), interpreted.reporting().getPosition(), 0, e.getCause());
			}
			renderersInitialized[0] = true;
			interpreted.destroyed().act(__ -> sub.unsubscribe());
			// TODO Changes to actions collection?
			interpretedActions = BetterList.<ValueAction.Interpreted<BetterList<T>, ?>, QuickSwingTableAction<BetterList<T>, ?>, ExpressoInterpretationException> of2(
				interpreted.getActions().stream(),
				a -> (QuickSwingTableAction<BetterList<T>, ?>) tx.transform(a, QuickSwingTableAction.class));
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTreeTable<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			InterpretedSwingTableColumn<BetterList<T>, T> treeColumn;
			ValueHolder<PanelPopulation.TreeTableEditor<T, ?>> treeHolder = new ValueHolder<>();
			TabularWidget.TabularContext<BetterList<T>> tableCtx = new TabularWidget.TabularContext.Default<>(theRowType,
				quick.reporting().getPosition().toShortString());
			if (quick.getTreeColumn() == null)
				treeColumn = null;
			else {
				treeColumn = new InterpretedSwingTableColumn<>(quick,
					(QuickTableColumn<BetterList<T>, T>) quick.getTreeColumn().getColumns().getFirst(), tableCtx, panel.getUntil(),
					treeHolder, theRenderer, theEditor);
			}
			TypeToken<BetterList<T>> pathType = TypeTokens.get().keyFor(BetterList.class)
				.<BetterList<T>> parameterized(quick.getNodeType());
			quick.setContext(tableCtx);
			ObservableCollection<InterpretedSwingTableColumn<BetterList<T>, ?>> columns = quick.getAllColumns().flow()//
				.map((Class<InterpretedSwingTableColumn<BetterList<T>, ?>>) (Class<?>) InterpretedSwingTableColumn.class, col -> {
					try {
						return new InterpretedSwingTableColumn<>(quick, col, tableCtx, panel.getUntil(), treeHolder,
							theColumnRenderers.get(col.getColumnSet().getIdentity()),
							theColumnEditors.get(col.getColumnSet().getIdentity()));
					} catch (ModelInstantiationException e) {
						if (tableInitialized) {
							col.getColumnSet().reporting().error(e.getMessage(), e);
							return null;
						} else
							throw new CheckedExceptionWrapper(e);
					}
				})//
				.filter(col -> col == null ? "Column failed to create" : null)//
				.catchUpdates(ThreadConstraint.ANY)//
				// TODO collectActive(onWhat?)
				.collect();
			Subscription columnsSub = columns.subscribe(evt -> {
				if (evt.getNewValue() != null)
					evt.getNewValue().init(columns, evt.getElementId());
			}, true);
			panel.getUntil().take(1).act(__ -> columnsSub.unsubscribe());
			ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>> crss = columns.flow()//
				.map((Class<CategoryRenderStrategy<BetterList<T>, ?>>) (Class<?>) CategoryRenderStrategy.class, //
					col -> col.getCRS())//
				.collect();
			Map<BetterList<T>, ObservableCollection<? extends T>> childrenCache = new HashMap<>();
			panel.addTreeTable3(quick.getModel().getValue(), (parentPath, nodeUntil) -> {
				ObservableCollection<? extends T> children = childrenCache.get(parentPath);
				if (children != null)
					return children;
				try {
					children = quick.getModel().getChildren(ObservableValue.of(pathType, parentPath), nodeUntil);
					childrenCache.put(parentPath, children);
					nodeUntil.take(1).act(__ -> childrenCache.remove(parentPath));
					return children;
				} catch (ModelInstantiationException e) {
					quick.reporting().error("Could not create children for " + parentPath, e);
					return null;
				}
			}, treeTable -> {
				component.accept(treeTable);
				treeHolder.accept(treeTable);
				if (quick.getSelection() != null)
					treeTable.withSelection(quick.getSelection(), false);
				if (quick.getMultiSelection() != null)
					treeTable.withSelection(quick.getMultiSelection());
				if (quick.getNodeSelection() != null)
					treeTable.withValueSelection(quick.getNodeSelection(), false);
				if (quick.getNodeMultiSelection() != null)
					treeTable.withValueSelection(quick.getNodeMultiSelection());
				if (treeColumn != null)
					treeTable.withRender(treeColumn.getCRS());
				treeTable.withColumns(crss);
				treeTable.withLeafTest2(path -> {
					try (Transaction t = QuickCoreSwing.rendering()) {
						tableCtx.getActiveValue().set(path, null);
						return quick.getModel().isLeaf(path);
					}
				});
				treeTable.withRootVisible(quick.isRootVisible());
				try {
					for (int a = 0; a < interpretedActions.size(); a++)
						((QuickSwingTableAction<BetterList<T>, ValueAction<BetterList<T>>>) interpretedActions.get(a)).addAction(treeTable,
							quick.getActions().get(a));
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
			tableInitialized = true;
		}
	}

	static class SwingMultiSlider extends QuickSwingPopulator.Abstract<QuickMultiSlider> {
		private final Transformer<ExpressoInterpretationException> theTransformer;

		SwingMultiSlider(QuickMultiSlider.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theTransformer = tx;
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickMultiSlider quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			HandleRenderer handleRenderer = quick.getHandleRenderer() == null ? null
				: new HandleRenderer(false, quick.getValues(), quick.getHandleRenderer(), theTransformer);
			BgRenderer bgRenderer = quick.getBgRenderers().isEmpty() ? null
				: new BgRenderer(quick.getBgRenderers(), Observable.or(panel.getUntil(), quick.onDestroy()));
			panel.addMultiSlider(null, quick.getValues(), slider -> {
				component.accept(slider);
				if (bgRenderer != null)
					bgRenderer.setSlider(slider.getEditor());
				slider.withBounds(quick.getMin(), quick.getMax());
				if (quick.isOrderEnforced())
					slider.getEditor().setValidator(MultiRangeSlider.RangeValidator.NO_OVERLAP_ENFORCE_RANGE);
				else
					slider.getEditor().setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
				if (handleRenderer != null)
					slider.getEditor().setRangeRenderer(handleRenderer);
				if (bgRenderer != null)
					slider.getEditor().setRenderer(bgRenderer);
			});
		}

		static class HandleRenderer extends MultiRangeSlider.RangeRenderer.Default {
			private final ObservableCollection<Double> theValues;
			private final QuickMultiSlider.SliderHandleRenderer theQuickRenderer;
			private final QuickMultiSlider.SliderHandleRenderer.HandleRenderContext theHandleContext;
			private final QuickWithBackground.BackgroundContext theBackgroundContext;
			private final ObservableValue<Cursor> theCursor;

			private BasicStroke theStroke;

			HandleRenderer(boolean vertical, ObservableCollection<Double> values, SliderHandleRenderer quickRenderer,
				Transformer<ExpressoInterpretationException> tx) throws ModelInstantiationException {
				super(vertical);
				theValues = values;
				theQuickRenderer = quickRenderer;
				withColor(__ -> getLineColor(), __ -> getFillColor());
				theHandleContext = new QuickMultiSlider.SliderHandleRenderer.HandleRenderContext.Default();
				theBackgroundContext = new QuickWithBackground.BackgroundContext.Default();
				theQuickRenderer.setHandleContext(theHandleContext);
				theQuickRenderer.setContext(theBackgroundContext);
				theCursor = theQuickRenderer.getStyle().getMouseCursor().map(quickCursor -> {
					try {
						return quickCursor == null ? null : tx.transform(quickCursor, Cursor.class);
					} catch (ExpressoInterpretationException e) {
						theQuickRenderer.reporting().error("Unsupported cursor: " + quickCursor, e);
						return null;
					}
				});
			}

			@Override
			public Component renderRange(CollectionElement<Range> range, RangePoint hovered, RangePoint focused) {
				setContext(range, hovered != null, focused != null);

				Integer thick = theQuickRenderer.getStyle().getLineThickness().get();
				if (thick == null)
					thick = 1;
				if (theStroke == null || theStroke.getLineWidth() != thick.intValue())
					theStroke = new BasicStroke(thick);
				return super.renderRange(range, hovered, focused);
			}

			private void setContext(CollectionElement<Range> range, boolean hovered, boolean focused) {
				theHandleContext.getHandleValue().set(range.get().getValue(), null);
				theHandleContext.getHandleIndex().set(theValues.getElementsBefore(range.getElementId()), null);
				theBackgroundContext.isHovered().set(hovered, null);
				theBackgroundContext.isFocused().set(focused, null);
				// TODO Clicked
			}

			@Override
			public String getTooltip(CollectionElement<Range> range, RangePoint point) {
				if (theQuickRenderer.getTooltip() != null) {
					setContext(range, getHovered() != null, getFocused() != null);
					return theQuickRenderer.getTooltip().get();
				} else
					return super.getTooltip(range, point);
			}

			Color getLineColor() {
				Color color = theQuickRenderer.getStyle().getLineColor().get();
				return color == null ? getForeground() : color;
			}

			Color getFillColor() {
				Color color = theQuickRenderer.getStyle().getColor().get();
				return color == null ? getBackground() : color;
			}

			@Override
			public Cursor getCursor(CollectionElement<Range> range, RangePoint point, boolean focused) {
				Cursor cursor = theCursor.get();
				if (cursor != null)
					return cursor;
				return super.getCursor(range, point, focused);
			}
		}

		static class BgRenderer extends MultiRangeSlider.MRSliderRenderer.Default {
			private final List<QuickMultiSlider.SliderBgRenderer> theQuickRenderers;
			private MultiRangeSlider theSlider;

			BgRenderer(List<SliderBgRenderer> quickRenderers, Observable<?> until) {
				theQuickRenderers = quickRenderers;
				List<Observable<? extends Causable>> listening = new ArrayList<>();
				for (QuickMultiSlider.SliderBgRenderer bgr : theQuickRenderers) {
					if (bgr.getMaxValue() != null)
						listening.add(bgr.getMaxValue().noInitChanges());
				}
				setLineThickness(2);
				Observable.onRootFinish(Observable.or(listening.toArray(new Observable[listening.size()]))).takeUntil(until)
				.act(__ -> update(false));
				update(true);
			}

			void setSlider(MultiRangeSlider slider) {
				theSlider = slider;
			}

			void update(boolean init) {
				clearColorRanges();

				// First renderers defined should have priority
				for (int i = theQuickRenderers.size() - 1; i >= 0; i--) {
					QuickMultiSlider.SliderBgRenderer bgr = theQuickRenderers.get(i);
					Color color = bgr.getStyle().getColor().get();
					if (color == null)
						continue;
					withColorRange(bgr.getMaxValue() == null ? Double.POSITIVE_INFINITY : bgr.getMaxValue().get(), color);
				}

				if (!init && theSlider != null)
					theSlider.repaint();
			}
		}
	}
}
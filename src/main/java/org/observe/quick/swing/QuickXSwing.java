package org.observe.quick.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.Iconized;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.QuickTable;
import org.observe.quick.base.TabularWidget;
import org.observe.quick.ext.QuickBarChart;
import org.observe.quick.ext.QuickCollapsePane;
import org.observe.quick.ext.QuickComboButton;
import org.observe.quick.ext.QuickMultiSlider;
import org.observe.quick.ext.QuickMultiSlider.SliderBgRenderer;
import org.observe.quick.ext.QuickMultiSlider.SliderHandleRenderer;
import org.observe.quick.ext.QuickSettingsMenu;
import org.observe.quick.ext.QuickShaded;
import org.observe.quick.ext.QuickShading;
import org.observe.quick.ext.QuickSuperTable;
import org.observe.quick.ext.QuickTiledPane;
import org.observe.quick.ext.QuickTreeTable;
import org.observe.quick.ext.QuickValueSelector;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.swing.AbstractLayout;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.MultiRangeSlider.Range;
import org.observe.util.swing.MultiRangeSlider.RangePoint;
import org.observe.util.swing.ObservableValueSelector;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.CollapsePanel;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.Shading;
import org.observe.util.swing.TableContentControl;
import org.observe.util.swing.TiledPane;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.ex.CheckedExceptionWrapper;

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
		tx.with(QuickSettingsMenu.Interpreted.class, QuickSwingPopulator.class, SwingSettingsMenu::new);
		tx.with(QuickTiledPane.Interpreted.class, QuickSwingPopulator.class, SwingTiledPane::new);
		tx.with(QuickSuperTable.Interpreted.class, QuickSwingPopulator.class, SwingSuperTable::new);
		tx.with(QuickValueSelector.Interpreted.class, QuickSwingPopulator.class, SwingValueSelector::new);
		tx.with(QuickBarChart.Interpreted.class, QuickSwingPopulator.class, SwingBarChart::new);
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
			Consumer<ComponentEditor<?, ?>> component) throws ModelInstantiationException {
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
						theInterpretedHeader.populate(new CollapsePaneHeaderPopulator(cp), theCollapsePane.getHeader());
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}
				if (theCollapsePane.isCollapsed() != null)
					cp.withCollapsed(theCollapsePane.isCollapsed());
				if (verticalLayout)
					cp.addVPanel(p -> panel.accept(p.fill().fillV()));
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
			MultiValueRenderable.MultiValueRenderContext<T> ctx = new MultiValueRenderable.MultiValueRenderContext.Default<>();
			quick.setContext(ctx);
			TabularWidget.TabularContext<T> tableCtx = new TabularWidget.TabularContext<T>() {
				private final SettableValue<Integer> theRowIndex = SettableValue.<Integer> build().withValue(0).build();
				private final SettableValue<Integer> theColumnIndex = SettableValue.<Integer> build().withValue(0).build();

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
			SettableValue<T> selectedValue = SettableValue.<T> build().build();
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> renderer = theRenderer == null ? null
				: new QuickSwingTablePopulation.QuickSwingRenderer<>(null, LambdaUtils.identity(), quick, selectedValue,
					quick.getRenderer(), tableCtx, () -> combo[0], theRenderer);
			panel.addComboButton(null, quick.getValues(), (value, cause) -> {
				ctx.getActiveValue().set(value, cause);
				quick.getAction().act(cause);
			}, cb -> {
				combo[0] = cb;
				component.accept(cb);
				cb.withText(quick.getText());
				cb.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
				if (theRenderer != null) {
					cb.renderWith(renderer);
					cb.withValueTooltip(v -> renderer.getTooltip(v, v));
				}
			});
		}
	}

	static class SwingTreeTable<N> extends QuickBaseSwing.SwingTree<N, QuickTreeTable<N>> {
		private final QuickSwingColumnSet<BetterList<N>, BetterList<N>> theColumns;

		SwingTreeTable(QuickTreeTable.Interpreted<N> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
			theColumns = new QuickSwingColumnSet<>(interpreted, interpreted.getColumns(), tx, null, LambdaUtils.identity());
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTreeTable<N> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			TabularWidget.TabularContext<BetterList<N>> ctx = new TabularWidget.TabularContext.Default<>(
				quick.reporting().getPosition().toShortString());
			quick.setContext(ctx);
			ValueHolder<PanelPopulation.TreeTableEditor<N, ?>> treeHolder = new ValueHolder<>();
			InterpretedSwingTableColumn<BetterList<N>, BetterList<N>, N> treeColumn = getTreeColumn(quick, treeHolder, ctx,
				panel.getUntil());
			QuickSwingColumnSet<BetterList<N>, BetterList<N>>.Populator columnPopulator = theColumns.createPopulator(quick,
				quick.getAllColumns(), ctx, panel.getUntil());
			panel.addTreeTable3(quick.getModel().getValue(), childrenProducer(quick), treeTable -> {
				component.accept(treeTable);
				treeHolder.accept(treeTable);
				populateTree(treeTable, quick, ctx);
				if (treeColumn != null)
					treeTable.withRender(treeColumn.getCRS());
				columnPopulator.populate(treeTable);
			});
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

	static class SwingSettingsMenu extends QuickSwingPopulator.Abstract<QuickSettingsMenu> {
		private final List<QuickSwingPopulator<?>> theChildren;

		SwingSettingsMenu(QuickSettingsMenu.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theChildren = new ArrayList<>(interpreted.getContents().size());
			for (QuickWidget.Interpreted<?> child : interpreted.getContents())
				theChildren.add(tx.transform(child, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickSettingsMenu quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addSettingsMenu(menu -> {
				component.accept(menu);
				for (int c = 0; c < theChildren.size(); c++) {
					try {
						((QuickSwingPopulator<QuickWidget>) theChildren.get(c)).populate(menu, quick.getContents().get(c));
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				}
			});
		}
	}

	static class SwingTiledPane<T> extends QuickSwingPopulator.Abstract<QuickTiledPane<T>> {
		private final QuickSwingLayout<QuickLayout> theLayout;
		private final QuickSwingPopulator<QuickWidget> theRenderer;

		SwingTiledPane(QuickTiledPane.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			interpreted.persistModelInstances(true);
			theLayout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			theRenderer = interpreted.getRenderer() == null ? null : tx.transform(interpreted.getRenderer(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTiledPane<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {

			Observable<?> until = Observable.or(panel.getUntil(), quick.onDestroy());
			TiledPane<T> tiledPane = new TiledPane<>(quick.getValues(), until);

			LayoutManager layoutInst = theLayout.create(panel, quick.getLayout());
			if (layoutInst instanceof AbstractLayout)
				tiledPane.setLayout(layoutInst);
			else
				quick.reporting().error("The provided layout (" + quick.getLayout() + ") is not supported for a " + quick.getTypeName());
			PanelPopulation.ComponentEditor<?, ?>[] populator = new PanelPopulation.ComponentEditor[1];

			// Let the Quick widget proper do the rendering
			TabularWidget.TabularContext<T> renderCtx = new TabularWidget.TabularContext.Default<>(quick.toString());
			quick.setContext(renderCtx);
			QuickWidget renderer = quick.getRenderer();
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingRenderer = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), quick, quick.getActiveValue(), renderer, renderCtx, () -> populator[0], theRenderer);

			// Now we need to make copies of the Quick tiled pane so the other 2 renderers (one for hover, one for focus) are independent
			// of each other and the renderer
			QuickWithBackground.BackgroundContext bgCtx = new QuickWithBackground.BackgroundContext() {
				@Override
				public SettableValue<Boolean> isHovered() {
					return quick.isHovered();
				}

				@Override
				public SettableValue<Boolean> isFocused() {
					return quick.isFocused();
				}

				@Override
				public SettableValue<Boolean> isPressed() {
					return quick.isPressed();
				}

				@Override
				public SettableValue<Boolean> isRightPressed() {
					return quick.isRightPressed();
				}
			};

			QuickTiledPane<T> hoverCopy = quick.copy(quick.getParentElement());
			ModelSetInstance hoverModels = quick.getModels().createCopy(quick.getUpdatingModels(), quick.getUpdatingModels().getUntil())
				.build();
			hoverCopy.instantiate(hoverModels);
			hoverCopy.setContext(bgCtx);
			TabularWidget.TabularContext<T> hoverCtx = new TabularWidget.TabularContext.Default<>(quick.toString() + "(hover)");
			hoverCopy.setContext(hoverCtx);
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingHover = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), hoverCopy, hoverCopy.getActiveValue(), hoverCopy.getRenderer(), hoverCtx, () -> populator[0],
				theRenderer);

			QuickTiledPane<T> focusCopy = quick.copy(quick.getParentElement());
			ModelSetInstance focusModels = quick.getModels().createCopy(quick.getUpdatingModels(), quick.getUpdatingModels().getUntil())
				.build();
			focusCopy.instantiate(focusModels);
			focusCopy.setContext(bgCtx);
			TabularWidget.TabularContext<T> focusCtx = new TabularWidget.TabularContext.Default<>(quick.toString() + "(focus)");
			focusCopy.setContext(focusCtx);
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingFocus = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), focusCopy, focusCopy.getActiveValue(), focusCopy.getRenderer(), focusCtx, () -> populator[0],
				theRenderer);

			// Support modifying values in the collection

			hoverCtx.getActiveValue().noInitChanges().takeUntil(until).act(evt -> {
				if (!swingHover.isUpdating()) {
					try {
						tiledPane.getValues().mutableElement(tiledPane.getValues().getElement(hoverCtx.getRowIndex().get()).getElementId())//
						.set(evt.getNewValue());
					} catch (RuntimeException e) {
						quick.reporting().error("Unable to modify value[" + hoverCtx.getRowIndex().get() + "]=" + evt.getNewValue(), e);
					}
				}
			});
			focusCtx.getActiveValue().noInitChanges().takeUntil(until).act(evt -> {
				if (!swingFocus.isUpdating()) {
					try {
						tiledPane.getValues().mutableElement(tiledPane.getValues().getElement(focusCtx.getRowIndex().get()).getElementId())//
						.set(evt.getNewValue());
					} catch (RuntimeException e) {
						quick.reporting().error("Unable to modify value[" + focusCtx.getRowIndex().get() + "]=" + evt.getNewValue(), e);
					}
				}
			});

			tiledPane.setConstantSizing(quick.isConstantSizing());
			tiledPane.setRendering(swingRenderer, swingHover, swingFocus);

			panel.addComponent(null, tiledPane, pop -> {
				populator[0] = pop;
				component.accept(pop);
			});
		}
	}

	static class SwingSuperTable<R> extends QuickBaseSwing.SwingTable<R, R> {
		private SettableValue<TableContentControl> theContentControl;
		private Component theSearchField;

		SwingSuperTable(QuickSuperTable.Interpreted<R, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTable<R> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			QuickSuperTable<R> superQuick = (QuickSuperTable<R>) quick;
			if (superQuick.isSearchable()) {
				theContentControl = SettableValue.create(TableContentControl.DEFAULT);
				panel.addVPanel(inner -> {
					component.accept(inner);
					if (superQuick.isSearchable()) {
						inner.addTextField(null, theContentControl, TableContentControl.FORMAT, field -> {
							TableContentControl.configureSearchField(field, true);
							field.modifyEditor(tf -> theSearchField = tf);
						});
					}
					try {
						super.doPopulate(inner, quick, t -> t.fill().fillV());
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				});
			} else
				super.doPopulate(panel, superQuick, component);
		}

		@Override
		protected void modifyTable(TableBuilder<R, ?, ?> table, QuickTable<R> quick) {
			super.modifyTable(table, quick);
			QuickSuperTable<R> superQuick = (QuickSuperTable<R>) quick;
			table.fill().fillV();
			if (superQuick.isSearchable()) {
				table.withFiltering(theContentControl);
				table.modifyAssociatedComponent(theSearchField);
			}
			if (superQuick.getItemName() != null)
				table.withItemName(superQuick.getItemName());
			if (superQuick.getAdaptiveHeight() != null) {
				table.withAdaptiveHeight(//
					superQuick.getAdaptiveHeight().getMinRows(-1), //
					superQuick.getAdaptiveHeight().getPrefRows(-1), //
					superQuick.getAdaptiveHeight().getMaxRows(Integer.MAX_VALUE));
			}
			theContentControl = null;
			theSearchField = null;
		}
	}

	static class SwingValueSelector<A, I> extends QuickSwingPopulator.Abstract<QuickValueSelector<A, I>> {
		private final QuickBaseSwing.SwingTable<A, ObservableValueSelector.SelectableValue<A, I>> theAvailableTable;
		private final QuickSwingColumnSet<A, ObservableValueSelector.SelectableValue<A, I>> theAvailableColumns;
		private final QuickBaseSwing.SwingTable<I, ObservableValueSelector.SelectableValue<A, I>> theIncludedTable;
		private final QuickSwingColumnSet<I, ObservableValueSelector.SelectableValue<A, I>> theIncludedColumns;

		SwingValueSelector(QuickValueSelector.Interpreted<A, I> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			TriConsumer<ObservableValueSelector.SelectableValue<A, I>, A, QuickWidget> update = (selValue, r, tbl) -> {
				((QuickTable<A>) tbl).getRows().mutableElement(selValue.getSourceElement().getElementId()).set(r);
			};
			Function<ObservableValueSelector.SelectableValue<A, I>, A> reverse = ObservableValueSelector.SelectableValue::getSource;
			theAvailableTable = tx.transform(new QuickBaseSwing.MappedTableConfig<>(interpreted.getAvailable(), //
				update, reverse), QuickBaseSwing.SwingTable.class);
			theAvailableColumns = new QuickSwingColumnSet<>(interpreted.getAvailable(), interpreted.getAvailable().getColumns(), tx, update,
				reverse);
			Function<ObservableValueSelector.SelectableValue<A, I>, I> destValue = ObservableValueSelector.SelectableValue::getDest;
			theIncludedTable = tx.transform(new QuickBaseSwing.MappedTableConfig<>(interpreted.getIncluded(), //
				null, destValue), QuickBaseSwing.SwingTable.class);
			theIncludedColumns = new QuickSwingColumnSet<>(interpreted.getIncluded(), interpreted.getIncluded().getColumns(), tx, null,
				ObservableValueSelector.SelectableValue::getDest);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickValueSelector<A, I> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			SettableValue<I> include = quick.getIncludeValue();
			QuickValueSelector.ValueSelectorContext<A, I> ctx = new QuickValueSelector.ValueSelectorContext.Default<>();
			quick.setValueSelectorContext(ctx);
			Observable<?> until = Observable.or(panel.getUntil(), quick.onDestroy());
			ObservableValueSelector<A, I>[] selector = new ObservableValueSelector[1];

			TabularWidget.TabularContext<A> availableCtx = new TabularWidget.TabularContext.Default<>(
				quick.getAvailable().reporting().getPosition().toShortString());
			quick.getAvailable().setContext(availableCtx);
			QuickSwingColumnSet<A, ObservableValueSelector.SelectableValue<A, I>>.Populator availableColumnsPopulator = theAvailableColumns
				.createPopulator(quick.getAvailable(), quick.getAvailable().getAllColumns(), availableCtx, until);

			TabularWidget.TabularContext<I> includedCtx = new TabularWidget.TabularContext.Default<>(
				quick.getIncluded().reporting().getPosition().toShortString());
			quick.getIncluded().setContext(includedCtx);
			QuickSwingColumnSet<I, ObservableValueSelector.SelectableValue<A, I>>.Populator includedColumnsPopulator = theIncludedColumns
				.createPopulator(quick.getIncluded(), quick.getIncluded().getAllColumns(), includedCtx, until);

			selector[0] = ObservableValueSelector.<A, I> build(quick.getAvailable().getRows(), sourceTable -> {
				theAvailableTable.populateTable(sourceTable, quick.getAvailable(), availableColumnsPopulator);
				ObservableCollection<A> availableRows = quick.getAvailable().getRows();
				ObservableCollection<ObservableValueSelector.SelectableValue<A, I>> selectedSVs = quick.getAvailable().getMultiSelection()
					.flow()//
					.<ObservableValueSelector.SelectableValue<A, I>> transform(tx -> tx//
						.map(v -> {
							if (selector[0] == null)
								return null;
							CollectionElement<A> avEl = availableRows.getElement(v, true);
							if (avEl == null)
								return null;
							ElementId svId = selector[0].getDisplayed().getEquivalentElement(avEl.getElementId());
							return svId == null ? null : selector[0].getDisplayed().getElement(svId).get();
						})//
						.withReverse(ObservableValueSelector.SelectableValue::getSource)//
						)//
					.filter(el -> el == null ? "No such element" : null)//
					.collectActive(until);
				sourceTable.withSelection(selectedSVs);
				SettableValue<ObservableValueSelector.SelectableValue<A, I>> selectedSV = quick.getAvailable().getSelection()//
					.<ObservableValueSelector.SelectableValue<A, I>> transformReversible(tx -> tx//
						.map(v -> {
							if (selector[0] == null)
								return null;
							CollectionElement<A> avEl = availableRows.getElement(v, true);
							if (avEl == null)
								return null;
							ElementId svId = selector[0].getDisplayed().getEquivalentElement(avEl.getElementId());
							return svId == null ? null : selector[0].getDisplayed().getElement(svId).get();
						})//
						.withReverse(ObservableValueSelector.SelectableValue::getSource)//
						);
				sourceTable.withSelection(selectedSV, false);
			}, destTable -> {
				theIncludedTable.populateTable(destTable, quick.getIncluded(), includedColumnsPopulator);
				// Can't respect selection here, can we?
			}, av -> {
				ctx.getAvailableValue().set(av, null);
				return include.get();
			})//
				.withFilterCommitOnType(true)//
				.withUntil(panel.getUntil())//
				.withItemName(quick.getItemName())//
				.build();
			Subscription includedSub = ObservableCollectionSynchronization.synchronize(selector[0].getIncluded().flow()//
				.<I> transform(tx -> tx.cache(false)//
					.map(ObservableValueSelector.SelectableValue::getDest)//
					)//
				.collectPassive(), quick.getIncluded().getRows())//
				.strictOrder().synchronize();
			until.take(1).act(__ -> includedSub.unsubscribe());
			panel.addComponent(null, selector[0], c -> component.accept(c));
		}
	}

	static class SwingBarChart<T> extends QuickSwingPopulator.Abstract<QuickBarChart<T>> {
		SwingBarChart(QuickBarChart.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx) {
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickBarChart<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			QuickSwingBarChart<T> chart = new QuickSwingBarChart<>(quick);
			panel.addComponent(null, chart, c -> component.accept(c));
		}
	}
}
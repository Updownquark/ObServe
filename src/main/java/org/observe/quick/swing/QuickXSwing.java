package org.observe.quick.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.qonfig.DocumentMap;
import org.observe.quick.Iconized;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.QuickButton;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.QuickTable;
import org.observe.quick.ext.QuickAbstractMultiSlider;
import org.observe.quick.ext.QuickAbstractMultiSlider.Interpreted;
import org.observe.quick.ext.QuickAbstractMultiSlider.SliderBgRenderer;
import org.observe.quick.ext.QuickAbstractMultiSlider.SliderHandleRenderer;
import org.observe.quick.ext.QuickCollapsePane;
import org.observe.quick.ext.QuickComboButton;
import org.observe.quick.ext.QuickMultiRangeSlider;
import org.observe.quick.ext.QuickMultiSlider;
import org.observe.quick.ext.QuickSettingsMenu;
import org.observe.quick.ext.QuickShaded;
import org.observe.quick.ext.QuickShading;
import org.observe.quick.ext.QuickSuperTable;
import org.observe.quick.ext.QuickTreeTable;
import org.observe.quick.ext.QuickValueSelector;
import org.observe.quick.ext.QuickVirtualMultiPane;
import org.observe.quick.swing.QuickSwingColumnSet.TabularContext;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingContainerPopulator;
import org.observe.quick.swing.QuickSwingTablePopulation.InterpretedSwingTableColumn;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.swing.AbstractLayout;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.MultiRangeSlider;
import org.observe.util.swing.MultiRangeSlider.Range;
import org.observe.util.swing.MultiRangeSlider.RangePoint;
import org.observe.util.swing.ObservableValueSelector;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.CollapsePanel;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.SliderEditor;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.Shading;
import org.observe.util.swing.TableContentControl;
import org.observe.util.swing.VirtualMultiPane;
import org.qommons.BiTuple;
import org.qommons.BreakpointHere;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.Transformer;
import org.qommons.TriConsumer;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.ErrorReporting;

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
		tx.with(QuickMultiRangeSlider.Interpreted.class, QuickSwingPopulator.class, SwingMultiRangeSlider::new);
		tx.with(QuickSettingsMenu.Interpreted.class, QuickSwingPopulator.class, SwingSettingsMenu::new);
		tx.with(QuickVirtualMultiPane.Interpreted.class, QuickSwingPopulator.class, SwingVirtualMultiPane::new);
		tx.with(QuickSuperTable.Interpreted.class, QuickSwingPopulator.class, SwingSuperTable::new);
		tx.with(QuickValueSelector.Interpreted.class, QuickSwingPopulator.class, SwingValueSelector::new);
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
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.addCollapsePanel(true,
					new JustifiedBoxLayout(true).mainJustified().crossJustified().setShowingInvisible(showInvisible),
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
			public AbstractQuickContainerPopulator addVPanel(boolean showInvisible, Consumer<PanelPopulator<JPanel, ?>> panel) {
				thePopulator.withHeader(p -> p.addVPanel(showInvisible, p2 -> {
					if (theShading != null)
						p2.withShading(theShading);
					panel.accept(p2);
				}));
				return this;
			}
		}
	}

	static class SwingComboButton<T> extends QuickSwingPopulator.Abstract<QuickComboButton<T>> {
		private final Map<Object, QuickSwingPopulator<QuickWidget>> theRenderers;

		SwingComboButton(QuickComboButton.Interpreted<T, QuickComboButton<T>> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theRenderers = new HashMap<>();
			for (QuickWidget.Interpreted<?> renderer : interpreted.getRenderers())
				theRenderers.put(renderer.getIdentity(), tx.transform(renderer, QuickSwingPopulator.class));
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickComboButton<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			ComponentEditor<?, ?>[] combo = new ComponentEditor[1];
			TabularContext<T> tableCtx = new TabularContext<>(quick.getActiveValue(), null, null, quick.isSelected());
			SettableValue<T> selectedValue = SettableValue.<T> build().build();
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> renderer;
			renderer = new QuickSwingTablePopulation.QuickSwingRenderer<>(null, LambdaUtils.identity(), quick, selectedValue,
				quick.getRenderers(), tableCtx, () -> combo[0], theRenderers, true);
			panel.addComboButton(null, quick.getValues(), (value, cause) -> {
				quick.getActiveValue().set(value, cause);
				quick.getAction().act(cause);
			}, cb -> {
				combo[0] = cb;
				component.accept(cb);
				cb.withText(quick.getText());
				cb.withIcon(quick.getAddOn(Iconized.class).getIcon().map(img -> img == null ? null : new ImageIcon(img)));
				cb.disableWith(quick.getAction().isEnabled());
				cb.renderWith(renderer);
				cb.withValueTooltip(v -> {
					ModelCell<T, T> cell = new ModelCell.Default<>(() -> v, v, 0, 0, false, false, false, false, false, true);
					return renderer.getTooltip(cell);
				});
			});
		}
	}

	static class SwingTreeTable<N, C> extends QuickBaseSwing.SwingTree<N, QuickTreeTable<N, C>> {
		private final QuickSwingColumnSet<BetterList<N>, BetterList<N>> theColumns;

		SwingTreeTable(QuickTreeTable.Interpreted<N, C> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
			theColumns = new QuickSwingColumnSet<>(interpreted, interpreted.getColumns(), tx, null, LambdaUtils.identity());
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTreeTable<N, C> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			TabularContext<BetterList<N>> ctx = new TabularContext<>(quick.getActiveValue(), quick.getRowIndex(), quick.getColumnIndex(),
				quick.isSelected());
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

	static abstract class AbstractSwingMultiSlider<T, S extends QuickAbstractMultiSlider<T>> extends QuickSwingPopulator.Abstract<S> {
		private final Transformer<ExpressoInterpretationException> theTransformer;

		AbstractSwingMultiSlider(QuickAbstractMultiSlider.Interpreted<T, S> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theTransformer = tx;
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, S quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			BgRenderer bgRenderer = quick.getBgRenderers().isEmpty() ? null
				: new BgRenderer(quick.getBgRenderers(), Observable.or(panel.getUntil(), quick.onDestroy()));
			createSlider(panel, quick, slider -> {
				component.accept(slider);
				HandleRenderer<T> handleRenderer;
				try {
					handleRenderer = quick.getHandleRenderer() == null ? null : new HandleRenderer<>(quick.isVertical(), quick.getValues(),
						slider.getEditor().getRanges(), quick.getHandleRenderer(), theTransformer);
				} catch (ModelInstantiationException e) {
					throw new CheckedExceptionWrapper(e);
				}
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

		protected abstract void createSlider(PanelPopulation.PanelPopulator<?, ?> panel, S quick,
			Consumer<PanelPopulation.SliderEditor<? extends MultiRangeSlider, ?>> onSlider);

		static class HandleRenderer<T> extends MultiRangeSlider.RangeRenderer.Default {
			private final ObservableCollection<T> theValues;
			private final ObservableCollection<Range> theRanges;
			private final QuickAbstractMultiSlider.SliderHandleRenderer<T> theQuickRenderer;
			private final QuickAbstractMultiSlider.MultiSliderContext<T> theHandleContext;
			private final QuickWithBackground.BackgroundContext theBackgroundContext;
			private final ObservableValue<Cursor> theCursor;

			private BasicStroke theStroke;

			HandleRenderer(boolean vertical, ObservableCollection<T> values, ObservableCollection<Range> ranges,
				SliderHandleRenderer<T> quickRenderer, Transformer<ExpressoInterpretationException> tx) throws ModelInstantiationException {
				super(vertical);
				theValues = values;
				theRanges = ranges;
				theQuickRenderer = quickRenderer;
				withColor(__ -> getLineColor(), __ -> getFillColor());
				theHandleContext = new QuickAbstractMultiSlider.MultiSliderContext.Default<>();
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
				BetterList<ElementId> equivIds = theRanges.getSourceElements(range.getElementId(), theValues);
				if (equivIds.isEmpty()) {
					System.out.println("No equivalent value found for range " + range.get());
					BreakpointHere.breakpoint();
					theRanges.getSourceElements(range.getElementId(), theValues);
				} else if (equivIds.size() > 1) {
					System.out.println("Multiple equivalent value found for range " + range.get());
					BreakpointHere.breakpoint();
					theRanges.getSourceElements(range.getElementId(), theValues);
				}

				CollectionElement<T> valueEl = theValues.getElement(equivIds.getFirst());
				theHandleContext.getHandleValue().set(valueEl.get(), null);
				theHandleContext.getHandleIndex().set(theValues.getElementsBefore(valueEl.getElementId()), null);
				theBackgroundContext.isHovered().set(hovered, null);
				theBackgroundContext.isFocused().set(focused, null);
				// TODO Pressed, right-pressed
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
			private final List<QuickAbstractMultiSlider.SliderBgRenderer> theQuickRenderers;
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

	static class SwingMultiSlider extends AbstractSwingMultiSlider<Double, QuickMultiSlider> {
		SwingMultiSlider(QuickMultiSlider.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
		}

		@Override
		protected void createSlider(PanelPopulation.PanelPopulator<?, ?> panel, QuickMultiSlider quick,
			Consumer<SliderEditor<? extends MultiRangeSlider, ?>> onSlider) {
			panel.addMultiSlider(null, quick.getValues(), onSlider::accept);
		}
	}

	static class SwingMultiRangeSlider<T> extends AbstractSwingMultiSlider<T, QuickMultiRangeSlider<T>> {
		SwingMultiRangeSlider(Interpreted<T, QuickMultiRangeSlider<T>> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
		}

		@Override
		protected void createSlider(PanelPopulation.PanelPopulator<?, ?> panel, QuickMultiRangeSlider<T> quick,
			Consumer<SliderEditor<? extends MultiRangeSlider, ?>> onSlider) {
			QuickAbstractMultiSlider.MultiSliderContext<T> ctx = new QuickAbstractMultiSlider.MultiSliderContext.Default<>(//
				SettableValue.<T> build().withDescription(quick.getActiveValueVariable().toString()).build(), //
				SettableValue.<Integer> build().withDescription(quick.getActiveIndexVariable().toString()).build());
			quick.setSliderContext(ctx);
			ObservableCollection<T> values = quick.getValues();
			SettableValue<Double> min = quick.getRangeMin();
			SettableValue<Double> max = quick.getRangeMax();
			boolean[] nullWarned = new boolean[1];
			ErrorReporting reporting = quick.reporting();
			boolean sourceModifying = quick.requiresSourceModification();
			ObservableCollection<Range> ranges;
			Transformation.ReversibleTransformationPrecursor<T, Range, ?> rangeTxBuilder = new Transformation.ReversibleTransformationPrecursor<>();
			ReversibleTransformation<T, Range> rangeTransform = rangeTxBuilder//
				.map(v -> {
					if (v == null && !nullWarned[0]) {
						nullWarned[0] = true;
						reporting.warn("Null values in range sliders will not be editable");
					}
					ctx.getHandleValue().set(v);
					ctx.getHandleIndex().set(values.indexOf(v));
					Double minV = min.get();
					Double maxV = max.get();
					if (minV != null && maxV != null)
						return Range.forMinMax(minV, maxV);
					else
						return null;
				})//
				.modifySource((v, r) -> {
					ctx.getHandleValue().set(v);
					ctx.getHandleIndex().set(values.indexOf(v));
					if (!Objects.equals(min.get(), r.getMin()))
						min.set(r.getMin());
					if (!Objects.equals(max.get(), r.getMax()))
						max.set(r.getMax());
				}, mod -> mod//
					.disableWith(tv -> {
						ctx.getHandleValue().set(tv.getCurrentSource());
						ctx.getHandleIndex().set(values.indexOf(tv.getCurrentSource()));
						String msg = min.isEnabled().get();
						if (msg == null)
							msg = max.isEnabled().get();
						return msg;
					}).rejectWith((v, r) -> {
						if (r == null)
							return "Cannot set a null range";
						ctx.getHandleValue().set(v);
						ctx.getHandleIndex().set(values.indexOf(v));
						String msg = Objects.equals(min.get(), r.getMin()) ? null : min.isAcceptable(r.getMin());
						if (msg == null)
							msg = Objects.equals(max.get(), r.getMax()) ? null : max.isAcceptable(r.getMax());
						return msg;
					})//
					);
			if (sourceModifying) { // Easy peasy
				ranges = values.flow()//
					.<Range> transform(__ -> rangeTransform)//
					.collectActive(quick.onDestroy());
			} else {
				ranges = values.flow()//
					// Create a value to facilitate notification of changes on each element without affecting the source
					.<BiTuple<SettableValue<T>, SettableValue<Range>>> transform(tx -> tx//
						.build((v, txv) -> {
							BiTuple<SettableValue<T>, SettableValue<Range>> value = txv.getPreviousResult();
							if (value == null) {
								SettableValue<T> sourceV = SettableValue.create(v);
								SettableValue<Range> rangeV = sourceV.transformReversible(__ -> rangeTransform);
								value = new BiTuple<>(sourceV, rangeV);
							} else
								value.getValue1().set(v);
							return value;
						}))//
					.flattenValues(BiTuple::getValue2)//
					.collectActive(quick.onDestroy());
			}
			panel.addMultiRangeSlider(null, ranges, onSlider::accept);
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

	static class SwingVirtualMultiPane<T> extends QuickSwingPopulator.Abstract<QuickVirtualMultiPane<T>> {
		private final QuickSwingLayout<QuickLayout> theLayout;
		private final QuickSwingPopulator<QuickWidget> theRenderer;
		private final DocumentMap<ModelInstantiator> theModels;

		SwingVirtualMultiPane(QuickVirtualMultiPane.Interpreted<T> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			interpreted.persistModelInstances(true);
			theLayout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			theRenderer = interpreted.getRenderer() == null ? null : tx.transform(interpreted.getRenderer(), QuickSwingPopulator.class);
			theModels = interpreted.instantiateLocalModels();
		}

		private ModelSetInstance copyModels(QuickVirtualMultiPane<T> quick) throws ModelInstantiationException {
			ModelSetInstanceBuilder builder = ObservableModelSet.createMultiModelInstanceBag(quick.getUpdatingModels().getUntil());
			for (ModelInstantiator model : theModels.values())
				builder.withAll(model.createCopy(quick.getUpdatingModels(), quick.getUpdatingModels().getUntil()).build());
			return builder.build();
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickVirtualMultiPane<T> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {

			Observable<?> until = Observable.or(panel.getUntil(), quick.onDestroy());
			VirtualMultiPane<T> tiledPane = new VirtualMultiPane<>(quick.getValues(), until);

			LayoutManager layoutInst = theLayout.create(panel, quick.getLayout());
			if (layoutInst instanceof AbstractLayout)
				tiledPane.setLayout(layoutInst);
			else
				quick.reporting().error("The provided layout (" + quick.getLayout() + ") is not supported for a " + quick.getTypeName());
			PanelPopulation.ComponentEditor<?, ?>[] populator = new PanelPopulation.ComponentEditor[1];

			// Let the Quick widget proper do the rendering
			TabularContext<T> renderCtx = new TabularContext<>(quick.getActiveValue(), quick.getValueIndex(), null, quick.isSelected());
			QuickWidget renderer = quick.getRenderer();
			Map<Object, QuickSwingPopulator<QuickWidget>> rendererMap = Collections.singletonMap(renderer.getIdentity(), theRenderer);
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingRenderer = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), quick, quick.getActiveValue(), Arrays.asList(renderer), renderCtx, () -> populator[0], rendererMap,
				false);

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

			QuickVirtualMultiPane<T> hoverCopy = quick.copy(quick.getParentElement());
			ModelSetInstance hoverModels = copyModels(quick);
			hoverCopy.instantiate(hoverModels);
			hoverCopy.setContext(bgCtx);
			TabularContext<T> hoverCtx = new TabularContext<>(hoverCopy.getActiveValue(), hoverCopy.getValueIndex(), null,
				hoverCopy.isSelected());
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingHover = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), hoverCopy, hoverCopy.getActiveValue(), Arrays.asList(hoverCopy.getRenderer()), hoverCtx,
				() -> populator[0], rendererMap, false);

			QuickVirtualMultiPane<T> focusCopy = quick.copy(quick.getParentElement());
			ModelSetInstance focusModels = copyModels(quick);
			focusCopy.instantiate(focusModels);
			focusCopy.setContext(bgCtx);
			TabularContext<T> focusCtx = new TabularContext<>(focusCopy.getActiveValue(), focusCopy.getValueIndex(), null,
				focusCopy.isSelected());
			QuickSwingTablePopulation.QuickSwingRenderer<T, T, T> swingFocus = new QuickSwingTablePopulation.QuickSwingRenderer<>(null,
				LambdaUtils.identity(), focusCopy, focusCopy.getActiveValue(), Arrays.asList(focusCopy.getRenderer()), focusCtx,
				() -> populator[0], rendererMap, false);

			// Support modifying values in the collection

			long[] hoverFocusKnownValuesStamp = new long[2];
			hoverFocusKnownValuesStamp[0] = tiledPane.getValues().getStamp();
			hoverFocusKnownValuesStamp[1] = hoverFocusKnownValuesStamp[0];
			hoverCopy.getActiveValue().noInitChanges().takeUntil(until).act(evt -> {
				if (swingHover.isUpdating())
					return;
				long newValuesStamp = tiledPane.getValues().getStamp();
				// If the collection hasn't changed but the value has, the input is from the UI and we should update the collection element
				if (newValuesStamp == hoverFocusKnownValuesStamp[0]) {
					try {
						MutableCollectionElement<T> element = tiledPane.getValues()
							.mutableElement(tiledPane.getValues().getElement(hoverCopy.getValueIndex().get()).getElementId());
						if (element.isAcceptable(evt.getNewValue()) == null)
							element.set(evt.getNewValue());
					} catch (RuntimeException e) {
						quick.reporting().error("Unable to modify value[" + hoverCopy.getValueIndex().get() + "]=" + evt.getNewValue(), e);
					}
					hoverFocusKnownValuesStamp[0] = tiledPane.getValues().getStamp();
				} else
					hoverFocusKnownValuesStamp[0] = newValuesStamp;
			});
			focusCopy.getActiveValue().noInitChanges().takeUntil(until).act(evt -> {
				if (swingFocus.isUpdating())
					return;
				long newValuesStamp = tiledPane.getValues().getStamp();
				// If the collection hasn't changed but the value has, the input is from the UI and we should update the collection element
				if (newValuesStamp == hoverFocusKnownValuesStamp[1]) {
					try {
						MutableCollectionElement<T> element = tiledPane.getValues()
							.mutableElement(tiledPane.getValues().getElement(focusCopy.getValueIndex().get()).getElementId());
						if (element.isAcceptable(evt.getNewValue()) == null)
							element.set(evt.getNewValue());
					} catch (RuntimeException e) {
						quick.reporting().error("Unable to modify value[" + focusCopy.getValueIndex().get() + "]=" + evt.getNewValue(), e);
					}
					hoverFocusKnownValuesStamp[1] = tiledPane.getValues().getStamp();
				} else
					hoverFocusKnownValuesStamp[1] = newValuesStamp;
			});

			tiledPane.setConstantSizing(quick.isConstantSizing());
			tiledPane.setRendering(swingRenderer, swingHover, swingFocus);

			panel.addComponent(null, tiledPane, pop -> {
				populator[0] = pop;
				component.accept(pop);
			});
		}
	}

	static class SwingSuperTable<R, C> extends QuickBaseSwing.SwingTable<R, R, C> {
		private SettableValue<TableContentControl> theContentControl;
		private Component theSearchField;

		SwingSuperTable(QuickSuperTable.Interpreted<R, C, ?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(interpreted, tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickTable<R, C> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			QuickSuperTable<R, C> superQuick = (QuickSuperTable<R, C>) quick;
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
		protected void modifyTable(TableBuilder<R, ?, ?> table, QuickTable<R, C> quick) {
			super.modifyTable(table, quick);
			QuickSuperTable<R, C> superQuick = (QuickSuperTable<R, C>) quick;
			QuickSuperTable.WithRowDragging rowDragging = superQuick.getRowDragging();
			if (rowDragging != null) {
				ObservableAction postDrag = rowDragging.getPostDrag();
				Runnable postDragRun = postDrag == null ? null : () -> postDrag.act(null);
				table.withDraggableRows(true, postDragRun);
			}
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
		private final QuickBaseSwing.SwingTable<A, ObservableValueSelector.SelectableValue<A, I>, Object> theAvailableTable;
		private final QuickSwingColumnSet<A, ObservableValueSelector.SelectableValue<A, I>> theAvailableColumns;
		private final QuickBaseSwing.SwingTable<I, ObservableValueSelector.SelectableValue<A, I>, Object> theIncludedTable;
		private final QuickSwingColumnSet<I, ObservableValueSelector.SelectableValue<A, I>> theIncludedColumns;
		private final QuickSwingPopulator<QuickButton> theIncludeAllConfig;
		private final QuickSwingPopulator<QuickButton> theIncludeConfig;
		private final QuickSwingPopulator<QuickButton> theExcludeConfig;
		private final QuickSwingPopulator<QuickButton> theExcludeAllConfig;

		SwingValueSelector(QuickValueSelector.Interpreted<A, I> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			TriConsumer<ObservableValueSelector.SelectableValue<A, I>, A, QuickWidget> update = (selValue, r, tbl) -> {
				((QuickTable<A, Object>) tbl).getRows().mutableElement(selValue.getSourceElement().getElementId()).set(r);
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
			theIncludeAllConfig = interpreted.getIncludeAllConfig() == null ? null
				: tx.transform(interpreted.getIncludeAllConfig(), QuickSwingPopulator.class);
			theIncludeConfig = interpreted.getIncludeConfig() == null ? null
				: tx.transform(interpreted.getIncludeConfig(), QuickSwingPopulator.class);
			theExcludeConfig = interpreted.getExcludeConfig() == null ? null
				: tx.transform(interpreted.getExcludeConfig(), QuickSwingPopulator.class);
			theExcludeAllConfig = interpreted.getExcludeAllConfig() == null ? null
				: tx.transform(interpreted.getExcludeAllConfig(), QuickSwingPopulator.class);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickValueSelector<A, I> quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			SettableValue<I> include = quick.getIncludeValue();
			QuickValueSelector.ValueSelectorContext<A, I> ctx = new QuickValueSelector.ValueSelectorContext.Default<>();
			quick.setValueSelectorContext(ctx);
			Observable<?> until = Observable.or(panel.getUntil(), quick.onDestroy());
			ObservableValueSelector<A, I>[] selector = new ObservableValueSelector[1];

			TabularContext<A> availableCtx = new TabularContext<>(quick.getAvailable().getActiveValue(), quick.getAvailable().getRowIndex(),
				quick.getAvailable().getColumnIndex(), quick.getAvailable().isSelected());
			QuickSwingColumnSet<A, ObservableValueSelector.SelectableValue<A, I>>.Populator availableColumnsPopulator = theAvailableColumns
				.createPopulator(quick.getAvailable(), quick.getAvailable().getAllColumns(), availableCtx, until);

			TabularContext<I> includedCtx = new TabularContext<>(quick.getIncluded().getActiveValue(), quick.getIncluded().getRowIndex(),
				quick.getIncluded().getColumnIndex(), quick.getIncluded().isSelected());
			QuickSwingColumnSet<I, ObservableValueSelector.SelectableValue<A, I>>.Populator includedColumnsPopulator = theIncludedColumns
				.createPopulator(quick.getIncluded(), quick.getIncluded().getAllColumns(), includedCtx, until);

			selector[0] = ObservableValueSelector.<A, I> build(quick.getAvailable().getRows(), sourceTable -> {
				theAvailableTable.populateTable(sourceTable, availableCtx, (QuickTable<A, Object>) quick.getAvailable(),
					availableColumnsPopulator);
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
				theIncludedTable.populateTable(destTable, includedCtx, (QuickTable<I, Object>) quick.getIncluded(),
					includedColumnsPopulator);
				// Can't respect selection here, can we?
			}, av -> {
				ctx.getAvailableValue().set(av, null);
				return include.get();
			})//
				.withFilterCommitOnType(true)//
				.withUntil(panel.getUntil())//
				.withItemName(quick.getItemName())//
				.configureIncludeAll(
					theIncludeAllConfig == null ? null : btn -> theIncludeAllConfig.modify(btn, quick.getIncludeAllConfig()))//
				.configureInclude(theIncludeConfig == null ? null : btn -> theIncludeConfig.modify(btn, quick.getIncludeConfig()))//
				.configureExclude(theExcludeConfig == null ? null : btn -> theExcludeConfig.modify(btn, quick.getExcludeConfig()))//
				.configureExcludeAll(
					theExcludeAllConfig == null ? null : btn -> theExcludeAllConfig.modify(btn, quick.getExcludeAllConfig()))//
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
}
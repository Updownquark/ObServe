package org.observe.util.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.jdesktop.swingx.JXTreeTable;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.dbug.Dbug;
import org.observe.dbug.DbugAnchorType;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulationImpl.*;
import org.observe.util.swing.WindowPopulation.JMenuBuilder;
import org.qommons.BreakpointHere;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** This utility class simplifies the population of many styles of user interface */
public class PanelPopulation {
	// TODO Replace "MIG" everywhere here with "grid"

	/**
	 * <p>
	 * This method creates an API structure that makes building a panel of vertical fields very easy.
	 * </p>
	 * <p>
	 * This API uses net.miginfocom.swing.MigLayout, but this class is not packaged with ObServe, so it must be provided by the caller. If
	 * the panel's layout is not a MigLayout (or the panel is null), the API will attempt to reflectively instantiate one, so MigLayout must
	 * be on the classpath.
	 * </p>
	 * <p>
	 * If the MigLayout is set for the container's layout, its fillx and hidemode 3 layout constraints should be set.
	 * </p>
	 *
	 * @param <C> The type of the container
	 * @param panel The container to add the field widgets to. If null, a new {@link JPanel} will be created (be sure
	 *        net.miginfocom.swing.MigLayout is in your classpath to use this).
	 * @param until The observable that, when fired, will release all associated resources
	 * @return The API structure to add fields with
	 */
	public static <C extends Container> PanelPopulator<C, ?> populateVPanel(C panel, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		if (panel == null)
			panel = (C) new ConformingPanel();
		return new PanelPopulationImpl.MigFieldPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, String layoutType, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return populateHPanel(panel, layoutType == null ? null : makeLayout(layoutType), until);
	}

	public static <C extends Container> PanelPopulator<C, ?> populateHPanel(C panel, LayoutManager layout, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		if (panel == null)
			panel = (C) new ConformingPanel(layout);
		else if (layout != null)
			panel.setLayout(layout);
		return new PanelPopulationImpl.SimpleHPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	/**
	 * Populates a panel without installing a new layout
	 *
	 * @param <C> The type of the container
	 * @param panel The container to add the field widgets to
	 * @param until The observable that, when fired, will release all associated resources
	 * @return The API structure to add fields with
	 */
	public static <C extends Container> PanelPopulator<C, ?> populatePanel(C panel, Observable<?> until) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return new PanelPopulationImpl.SimpleHPanel<>(null, panel, until == null ? Observable.empty() : until);
	}

	public static <R> TableBuilder<R, ?> buildTable(ObservableCollection<R> rows) {
		if (!EventQueue.isDispatchThread())
			System.err.println(
				"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
		return new SimpleTableBuilder<>(rows, Observable.empty());
	}

	public static Alert alert(Component parent, String title, String message) {
		return new PanelPopulationImpl.SimpleAlert(parent, title, message);
	}

	public static void showTempDialog(Component parent, Consumer<WindowBuilder<JDialog, ?>> window, Observable<?> until) {
		SimpleObservable<Void> end = new SimpleObservable<>();
		if (until == null)
			until = end;
		else
			until = Observable.or(until, end);
		WindowBuilder<JDialog, ?> windowBuilder = WindowPopulation.populateDialog(null, until, true)//
			.modal(true);
		windowBuilder.getWindow().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					windowBuilder.getWindow().setVisible(false);
			}
		});
		window.accept(windowBuilder);
		windowBuilder.run(parent);
		end.onNext(null);
	}

	public interface ContainerPopulator<C extends Container, P extends ContainerPopulator<C, P>> extends ComponentEditor<C, P> {
		<R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table);

		<F> P addTree(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify);

		<F> P addTree2(ObservableValue<? extends F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify);

		<F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify);

		<F> P addTreeTable2(ObservableValue<F> root, Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify);

		P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs);

		P addSplit(boolean vertical, Consumer<SplitPane<?>> split);

		P addScroll(String fieldName, Consumer<ScrollPane<?>> scroll);

		<S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify);

		P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addHPanel(String fieldName, String layoutType, Consumer<PanelPopulator<JPanel, ?>> panel) {
			return addHPanel(fieldName, makeLayout(layoutType), panel);
		}

		P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addCollapsePanel(boolean vertical, String layoutType, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			return addCollapsePanel(vertical, makeLayout(layoutType), panel);
		}

		P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel);

		P withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		@Override
		default Alert alert(String title, String message) {
			return new SimpleAlert(getContainer(), title, message);
		}

		C getContainer();

		void addModifier(Consumer<ComponentEditor<?, ?>> modifier);

		void removeModifier(Consumer<ComponentEditor<?, ?>> modifier);
	}

	public interface PanelPopulator<C extends Container, P extends PanelPopulator<C, P>> extends ContainerPopulator<C, P> {
		<F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify);

		<F> P addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify);

		default <F> P addStyledTextArea(String fieldName, SettableValue<F> root, Format<F> format,
			Function<? super F, ? extends ObservableCollection<? extends F>> children, BiConsumer<? super F, ? super BgFontAdjuster> style,
				Consumer<FieldEditor<JTextPane, ?>> modify) {
			return addStyledTextArea(fieldName, new ObservableStyledDocument<F>(root, format, ThreadConstraint.EDT, getUntil()) {
				@Override
				protected ObservableCollection<? extends F> getChildren(F value) {
					return children.apply(value);
				}

				@Override
				protected void adjustStyle(F value, BgFontAdjuster style2) {
					style.accept(value, style2);
				}
			}, modify);
		}

		<F> P addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc, Consumer<FieldEditor<JTextPane, ?>> modify);

		default P addLabel(String fieldName, String label, Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, ObservableValue.of(label), v -> v, modify);
		}

		default <F> P addLabel(String fieldName, ObservableValue<F> field, Format<F> format, Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, field, v -> format.format(v), modify);
		}

		<F> P addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<FieldEditor<JLabel, ?>> modify);

		P addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify);

		<F> P addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format, Consumer<Object> action,
			Consumer<FieldEditor<JLabel, ?>> modify);

		P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify);

		P addToggleButton(String fieldName, SettableValue<Boolean> field, String text, Consumer<ButtonEditor<JToggleButton, ?>> modify);

		/* TODO
		 * slider
		 * progress bar
		 * spinner
		 * offloaded actions
		 * menu button
		 * (multi-) split pane (with configurable splitter panel)
		 * scroll pane
		 * value selector
		 * settings menu
		 * rotation
		 * form controls (e.g. press enter in a text field and a submit action (also tied to a button) fires)
		 * styles: borders, background...
		 *
		 * Dragging!!!
		 *
		 * Complete common locking (RRWL, CLS)
		 */

		default P addIntSpinnerField(String fieldName, SettableValue<Integer> value,
			Consumer<SteppedFieldEditor<JSpinner, Integer, ?>> modify) {
			return addSpinnerField(fieldName, new JSpinner(new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1)), value,
				Number::intValue, modify);
		}

		default P addDoubleSpinnerField(String fieldName, SettableValue<Double> value,
			Consumer<SteppedFieldEditor<JSpinner, Double, ?>> modify) {
			return addSpinnerField(fieldName,
				new JSpinner(new SpinnerNumberModel(0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0)), value,
				Number::doubleValue, modify);
		}

		<F> P addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value, Function<? super F, ? extends F> purifier,
			Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify);

		P addSlider(String fieldName, SettableValue<Double> value, Consumer<SliderEditor<MultiRangeSlider, ?>> modify);

		P addMultiSlider(String fieldName, ObservableCollection<Double> values, Consumer<SliderEditor<MultiRangeSlider, ?>> modify);

		default P addRangeSlider(String fieldName, SettableValue<Double> min, SettableValue<Double> max,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			return addRangeSlider(fieldName, MultiRangeSlider.transformToRange(min, max, getUntil()), modify);
		}

		P addRangeSlider(String fieldName, SettableValue<MultiRangeSlider.Range> range, Consumer<SliderEditor<MultiRangeSlider, ?>> modify);

		P addMultiRangeSlider(String fieldName, ObservableCollection<MultiRangeSlider.Range> values,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify);

		default <F> P addComboField(String fieldName, SettableValue<F> value, Consumer<ComboEditor<F, ?>> modify, F... availableValues) {
			return addComboField(fieldName, value, Arrays.asList(availableValues), modify);
		}

		<F> P addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify);

		P addFileField(String fieldName, SettableValue<File> value, boolean open, Consumer<FieldEditor<ObservableFileButton, ?>> modify);

		default <F> P addRadioField(String fieldName, SettableValue<F> value, F[] values,
			Consumer<ToggleEditor<F, JRadioButton, ?>> modify) {
			return addToggleField(fieldName, value, Arrays.asList(values), JRadioButton.class, __ -> new JRadioButton(), modify);
		}

		<F, TB extends JToggleButton> P addToggleField(String fieldName, SettableValue<F> value, List<? extends F> values,
			Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator, Consumer<ToggleEditor<F, TB, ?>> modify);

		// public <F> MigPanelPopulatorField<F, JToggleButton> addToggleField(String fieldName, SettableValue<F> value,
		// boolean radio, F... availableValues) {
		// return addToggleField(fieldName, value, radio, Arrays.asList(availableValues));
		// }
		//
		// public <F> MigPanelPopulatorField<F, JToggleButton> addToggleField(String fieldName, SettableValue<F> value, boolean radio,
		// List<? extends F> availableValues) {
		// }
		//
		// public <F> MigPanelPopulatorField<F, JSlider> addSliderField(String fieldName, SettableValue<Integer> value) {}
		//
		// public <F> MigPanelPopulatorField<F, ObservableTreeModel> addTree(Object root,
		// Function<Object, ? extends ObservableCollection<?>> branching) {}
		//
		// P addMultiple(String fieldName, Consumer<PanelPopulator<JPanel, ?>> panel);

		default P addButton(String buttonText, Consumer<Object> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			return addButton(buttonText, new ObservableAction<Void>() {
				@Override
				public TypeToken<Void> getType() {
					return TypeTokens.get().of(Void.class);
				}

				@Override
				public Void act(Object cause) throws IllegalStateException {
					action.accept(cause);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return SettableValue.ALWAYS_ENABLED;
				}
			}, modify);
		}

		P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify);

		<F> P addComboButton(String buttonText, ObservableCollection<F> values, BiConsumer<? super F, Object> action,
			Consumer<ComboButtonBuilder<F, ComboButton<F>, ?>> modify);

		P addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress);

		<R> P addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list);

		default P spacer(int size) {
			return spacer(size, null);
		}

		default P spacer(int size, Consumer<Component> component) {
			Component box = Box.createRigidArea(new Dimension(size, size));
			if (component != null)
				component.accept(box);
			return addComponent(null, box, null);
		}

		P addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu);
	}

	public interface PartialPanelPopulatorImpl<C extends Container, P extends PartialPanelPopulatorImpl<C, P>>
	extends PanelPopulator<C, P> {
		@Override
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled);

		<C2 extends ComponentEditor<?, ?>> C2 modify(C2 component);

		default void doAdd(AbstractComponentEditor<?, ?> field) {
			doAdd(field, false);
		}

		default void doAdd(AbstractComponentEditor<?, ?> field, boolean scrolled) {
			doAdd(field, field.createFieldNameLabel(getUntil()), field.createPostLabel(getUntil()), scrolled);
		}

		@Override
		default <F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextField<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextField<>(field, format, getUntil()), getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				field.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addTextArea(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextArea<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextArea<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextArea<>(field, format, getUntil()), getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				field.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel, true);
			return (P) this;
		}

		@Override
		default <F> P addStyledTextArea(String fieldName, ObservableStyledDocument<F> doc, Consumer<FieldEditor<JTextPane, ?>> modify) {
			JTextPane editor = new JTextPane();
			editor.setEditable(false); // Editing not currently supported
			// Default tabs are ridiculously long. Btw, I don't know what the units here are, but they're not characters. Maybe pixels.
			if (doc.getRootStyle().getFontAttributes().getAttribute(StyleConstants.TabSet) == null) {
				List<TabStop> tabs = new ArrayList<>();
				for (int i = 30; i < 3000; i += 30)
					tabs.add(new TabStop(i));
				StyleConstants.setTabSet(doc.getRootStyle().getFontAttributes(), new TabSet(tabs.toArray(new TabStop[tabs.size()])));
			}
			// Set default attributes
			Enumeration<?> attrNames = editor.getParagraphAttributes().getAttributeNames();
			while (attrNames.hasMoreElements()) {
				Object attr = attrNames.nextElement();
				if (doc.getRootStyle().getFontAttributes().getAttribute(attr) == null)
					doc.getRootStyle().getFontAttributes().addAttribute(attr, editor.getParagraphAttributes().getAttribute(attr));
			}
			// Make the document's style the master
			editor.setParagraphAttributes(doc.getRootStyle().getFontAttributes(), true);
			StyledDocument styledDoc;
			if (editor.getDocument() instanceof StyledDocument)
				styledDoc = (StyledDocument) editor.getDocument();
			else {
				styledDoc = new DefaultStyledDocument();
				editor.setDocument(styledDoc);
			}
			ObservableStyledDocument.synchronize(doc, styledDoc, getUntil());

			SimpleFieldEditor<JTextPane, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, editor, getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				doc.getRootValue().noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel, true);
			return (P) this;
		}

		@Override
		default <F> P addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			field.changes().takeUntil(getUntil()).act(evt -> {
				label.setText(format.apply(evt.getNewValue()));
			});
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				field.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addIcon(String fieldName, ObservableValue<Icon> icon, Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			icon.changes().takeUntil(getUntil()).act(evt -> label.setIcon(evt.getNewValue()));
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (icon instanceof SettableValue) {
				((SettableValue<Icon>) icon).isEnabled().combine((enabled, tt) -> enabled == null ? tt : enabled, fieldPanel.getTooltip())
				.changes().takeUntil(getUntil()).act(evt -> label.setToolTipText(evt.getNewValue()));
			}
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				icon.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addLink(String fieldName, ObservableValue<F> field, Function<? super F, String> format, Consumer<Object> action,
			Consumer<FieldEditor<JLabel, ?>> modify) {
			return addLabel(fieldName, field, format, label -> {
				modify(label);
				ComponentDecorator normalDeco = new ComponentDecorator().withForeground(Color.blue);
				ComponentDecorator hoverDeco = new ComponentDecorator().withForeground(Color.blue).underline();
				label.modifyComponent(comp -> {
					normalDeco.decorate(comp);
					MouseAdapter mouse = new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							action.accept(e);
						}

						@Override
						public void mouseEntered(MouseEvent e) {
							hoverDeco.decorate(comp);
						}

						@Override
						public void mouseExited(MouseEvent e) {
							normalDeco.decorate(comp);
						}
					};
					comp.addMouseListener(mouse);
					comp.addMouseMotionListener(mouse);
				});
				if (modify != null)
					modify.accept(label);
			});
		}

		@Override
		default P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getUntil());
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			modify(fieldPanel);
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				field.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addToggleButton(String fieldName, SettableValue<Boolean> field, String text,
			Consumer<ButtonEditor<JToggleButton, ?>> modify) {
			SimpleButtonEditor<JToggleButton, ?> fieldPanel = new SimpleButtonEditor<>(fieldName, new JToggleButton(), text,
				ObservableAction.nullAction(TypeTokens.get().WILDCARD, null), false, getUntil());
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			modify(fieldPanel);
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				field.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F> P addSpinnerField(String fieldName, JSpinner spinner, SettableValue<F> value, Function<? super F, ? extends F> purifier,
			Consumer<SteppedFieldEditor<JSpinner, F, ?>> modify) {
			SimpleSteppedFieldEditor<JSpinner, F, ?> fieldPanel = new SimpleSteppedFieldEditor<>(fieldName, spinner, stepSize -> {
				((SpinnerNumberModel) spinner.getModel()).setStepSize((Number) stepSize);
			}, getUntil());
			ObservableSwingUtils.spinnerFor(spinner, fieldPanel.getTooltip().get(), value, purifier);
			modify(fieldPanel);
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addSlider(String fieldName, SettableValue<Double> value, Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			SimpleMultiSliderEditor<?> compEditor = SimpleMultiSliderEditor.createForValue(fieldName, value, getUntil());
			modify(compEditor);
			if (modify != null)
				modify.accept(compEditor);
			if (compEditor.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())//
				.act(__ -> compEditor.decorate(compEditor.getComponent()));
			doAdd(compEditor);
			return (P) this;
		}

		@Override
		default P addMultiSlider(String fieldName, ObservableCollection<Double> values,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			SimpleMultiSliderEditor<?> compEditor = SimpleMultiSliderEditor.createForValues(fieldName, values, getUntil());
			modify(compEditor);
			if (modify != null)
				modify.accept(compEditor);
			if (compEditor.isDecorated())
				values.simpleChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())//
				.act(__ -> compEditor.decorate(compEditor.getComponent()));
			doAdd(compEditor);
			return (P) this;
		}

		@Override
		default P addRangeSlider(String fieldName, SettableValue<MultiRangeSlider.Range> range,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			SimpleMultiSliderEditor<?> compEditor = SimpleMultiSliderEditor.createForRange(fieldName, range, getUntil());
			modify(compEditor);
			if (modify != null)
				modify.accept(compEditor);
			if (compEditor.isDecorated())
				range.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())//
				.act(__ -> compEditor.decorate(compEditor.getComponent()));
			doAdd(compEditor);
			return (P) this;
		}

		@Override
		default P addMultiRangeSlider(String fieldName, ObservableCollection<MultiRangeSlider.Range> values,
			Consumer<SliderEditor<MultiRangeSlider, ?>> modify) {
			SimpleMultiSliderEditor<?> compEditor = SimpleMultiSliderEditor.createForRanges(fieldName, values, getUntil());
			modify(compEditor);
			if (modify != null)
				modify.accept(compEditor);
			if (compEditor.isDecorated())
				values.simpleChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())//
				.act(__ -> compEditor.decorate(compEditor.getComponent()));
			doAdd(compEditor);
			return (P) this;
		}

		@Override
		default <F> P addComboField(String fieldName, SettableValue<F> value, List<? extends F> availableValues,
			Consumer<ComboEditor<F, ?>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (availableValues instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) availableValues;
			else
				observableValues = ObservableCollection.of(value.getType(), availableValues);

			SimpleComboEditor<F, ?> fieldPanel = new SimpleComboEditor<>(fieldName, new JComboBox<>(), value, getUntil());
			modify(fieldPanel);
			if (modify != null)
				modify.accept(fieldPanel);
			ObservableComboBoxModel.ComboHookup hookup = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(),
				fieldPanel::getTooltip, observableValues, value);
			fieldPanel.setHoveredItem(hookup::getHoveredItem);
			getUntil().take(1).act(__ -> hookup.unsubscribe());
			if (fieldPanel.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default <F, TB extends JToggleButton> P addToggleField(String fieldName, SettableValue<F> value, List<? extends F> values,
			Class<TB> buttonType, Function<? super F, ? extends TB> buttonCreator, Consumer<ToggleEditor<F, TB, ?>> modify) {
			ObservableCollection<? extends F> observableValues;
			if (values instanceof ObservableCollection)
				observableValues = (ObservableCollection<F>) values;
			else
				observableValues = ObservableCollection.of(value.getType(), values);
			SimpleToggleButtonPanel<F, TB, ?> radioPanel = new SimpleToggleButtonPanel<>(fieldName, observableValues, value, //
				buttonType, buttonCreator, getUntil());
			modify(radioPanel);
			if (modify != null)
				modify.accept(radioPanel);
			if (radioPanel.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> radioPanel.decorate(radioPanel.getComponent()));
			doAdd(radioPanel);
			return (P) this;
		}

		@Override
		default P addFileField(String fieldName, SettableValue<File> value, boolean open,
			Consumer<FieldEditor<ObservableFileButton, ?>> modify) {
			SimpleFieldEditor<ObservableFileButton, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableFileButton(value, open, getUntil()), getUntil());
			modify(fieldPanel);
			fieldPanel.getTooltip().changes().takeUntil(getUntil()).act(evt -> fieldPanel.getEditor().setToolTipText(evt.getNewValue()));
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			SimpleButtonEditor<JButton, ?> field = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, false, getUntil())
				.withText(buttonText);
			modify(field);
			if (modify != null)
				modify.accept(field);
			doAdd(field);
			return (P) this;
		}

		@Override
		default <F> P addComboButton(String buttonText, ObservableCollection<F> values, BiConsumer<? super F, Object> action,
			Consumer<ComboButtonBuilder<F, ComboButton<F>, ?>> modify) {
			SimpleComboButtonEditor<F, ComboButton<F>, ?> field = new SimpleComboButtonEditor<>(null, buttonText, values, action,
				getUntil());
			modify(field);
			if (modify != null)
				modify.accept(field);
			doAdd(field);
			return (P) this;
		}

		@Override
		default P addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress) {
			SimpleProgressEditor<?> editor = new SimpleProgressEditor<>(fieldName, getUntil());
			modify(editor);
			progress.accept(editor);
			if (editor.isDecorated())
				editor.getProgress().noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> editor.decorate(editor.getComponent()));
			doAdd(editor);
			return (P) this;
		}

		@Override
		default P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
			SimpleTabPaneEditor<?> tabPane = new SimpleTabPaneEditor<>(getUntil());
			modify(tabPane);
			tabs.accept(tabPane);
			doAdd(tabPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addSplit(boolean vertical, Consumer<SplitPane<?>> split) {
			SimpleSplitEditor<?> splitPane = new SimpleSplitEditor<>(vertical, getUntil());
			modify(splitPane);
			split.accept(splitPane);
			doAdd(splitPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addScroll(String fieldName, Consumer<ScrollPane<?>> scroll) {
			SimpleScrollEditor<?> scrollPane = new SimpleScrollEditor<>(fieldName, getUntil());
			modify(scrollPane);
			scroll.accept(scrollPane);
			doAdd(scrollPane);
			return (P) this;
		}

		@Override
		default <R> P addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list) {
			SimpleListBuilder<R, ?> tb = new SimpleListBuilder<>(rows.safe(ThreadConstraint.EDT, getUntil()), getUntil());
			modify(tb);
			list.accept(tb);
			doAdd(tb);
			return (P) this;
		}

		@Override
		default <R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
			SimpleTableBuilder<R, ?> tb = new SimpleTableBuilder<>(rows, getUntil());
			modify(tb);
			table.accept(tb);
			doAdd(tb, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTree(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify) {
			SimpleTreeBuilder<F, ?> treeEditor = SimpleTreeBuilder.createTree(root, children, getUntil());
			modify(treeEditor);
			if (modify != null)
				modify.accept(treeEditor);
			doAdd(treeEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTree2(ObservableValue<? extends F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify) {
			SimpleTreeBuilder<F, ?> treeEditor = SimpleTreeBuilder.createTree2(root, children, getUntil());
			modify(treeEditor);
			if (modify != null)
				modify.accept(treeEditor);
			doAdd(treeEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify) {
			SimpleTreeTableBuilder<F, ?> treeTableEditor = SimpleTreeTableBuilder.createTreeTable(root, children, getUntil());
			modify(treeTableEditor);
			if (modify != null)
				modify.accept(treeTableEditor);
			doAdd(treeTableEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTreeTable2(ObservableValue<F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeTableEditor<F, ?>> modify) {
			SimpleTreeTableBuilder<F, ?> treeTableEditor = SimpleTreeTableBuilder.createTreeTable2(root, children, getUntil());
			modify(treeTableEditor);
			if (modify != null)
				modify.accept(treeTableEditor);
			doAdd(treeTableEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
			SimpleFieldEditor<S, ?> subPanel = new SimpleFieldEditor<>(fieldName, component, getUntil());
			modify(subPanel);
			if (modify != null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel) {
			SimpleHPanel<JPanel, ?> subPanel = new SimpleHPanel<>(fieldName, new ConformingPanel(layout), getUntil());
			modify(subPanel);
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			MigFieldPanel<JPanel, ?> subPanel = new MigFieldPanel<>(null, new ConformingPanel(), getUntil());
			modify(subPanel);
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu) {
			SettingsMenuImpl<JPanel, ?> settingsMenu = new SettingsMenuImpl<>(null, new ConformingPanel(), getUntil());
			modify(settingsMenu);
			if (menu != null)
				menu.accept(settingsMenu);
			doAdd(settingsMenu, null, null, false);
			return (P) this;
		}

		@Override
		default P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			JXCollapsiblePane cp = new JXCollapsiblePane();
			cp.setLayout(layout);
			SimpleCollapsePane collapsePanel = new SimpleCollapsePane(cp, getUntil(), vertical, layout);
			modify(collapsePanel);
			panel.accept(collapsePanel);
			doAdd(collapsePanel, null, null, false);
			return (P) this;
		}
	}

	public interface ComponentEditor<E, P extends ComponentEditor<E, P>> extends Tooltipped<P> {
		Observable<?> getUntil();

		default P withFieldName(String fieldName) {
			return withFieldName(fieldName == null ? null : ObservableValue.of(fieldName));
		}

		P withFieldName(ObservableValue<String> fieldName);

		P modifyFieldLabel(Consumer<FontAdjuster> font);

		P withFont(Consumer<FontAdjuster> font);

		E getEditor();

		P visibleWhen(ObservableValue<Boolean> visible);

		/**
		 * Specifies that this component should fill the horizontal dimension of its panel (e.g. should expand so that there is no empty
		 * space on the right side of its panel).
		 *
		 * @return This editor
		 */
		P fill();

		/**
		 * Specifies that this component should expand to fill the vertical dimension of its panel (e.g. should expand so that there is no
		 * empty space at the bottom of its panel).
		 *
		 * @return This editor
		 */
		P fillV();

		P decorate(Consumer<ComponentDecorator> decoration);

		P repaintOn(Observable<?> repaint);

		P modifyEditor(Consumer<? super E> modify);

		P modifyComponent(Consumer<Component> component);

		Component getComponent();

		Alert alert(String title, String message);

		P withLayoutConstraints(Object constraints);

		default P grabFocus() {
			// This method is typically called when the component is declared, usually before the editor is built or installed.
			// We'll give the Swing system the chance to finish its work and try a few times as well.
			Duration interval = Duration.ofMillis(20);
			QommonsTimer.getCommonInstance().build(() -> {
				Component component = getComponent();
				if (component != null && component.isVisible())
					component.requestFocus();
			}, interval, false).times(5).onEDT().runNextIn(interval);
			return (P) this;
		}

		P withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu);

		P onMouse(Consumer<MouseEvent> onMouse);

		default P onClick(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_CLICKED)
					onClick.accept(evt);
			});
		}

		default P onHover(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_MOVED)
					onClick.accept(evt);
			});
		}

		default P onEnter(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_ENTERED)
					onClick.accept(evt);
			});
		}

		default P onExit(Consumer<MouseEvent> onClick) {
			return onMouse(evt -> {
				if (evt.getID() == MouseEvent.MOUSE_EXITED)
					onClick.accept(evt);
			});
		}

		/**
		 * @param name The name for the component (typically for debugging)
		 * @return This editor
		 */
		P withName(String name);
	}

	public static abstract class AbstractComponentEditor<E, P extends AbstractComponentEditor<E, P>> implements ComponentEditor<E, P> {
		private final Observable<?> theUntil;
		private final E theEditor;
		private ObservableValue<String> theFieldName;
		private Consumer<FontAdjuster> theFieldLabelModifier;
		private Object theLayoutConstraints;
		private boolean isFillH;
		private boolean isFillV;
		private Consumer<Component> theComponentModifier;
		private ObservableValue<String> theTooltip;
		private boolean isTooltipHandled;
		private SettableValue<ObservableValue<String>> theSettableTooltip;
		private ComponentDecorator theDecorator;
		private List<Consumer<ComponentDecorator>> theDecorators;
		private Observable<?> theRepaint;
		private Consumer<MouseEvent> theMouseListener;
		private String theName;
		private PanelPopulator<?, ?> theGlassPane;
		private Component theBuiltComponent;

		protected Consumer<FontAdjuster> theFont;
		private ObservableValue<Boolean> isVisible;
		private Consumer<MenuBuilder<JPopupMenu, ?>> thePopupMenu;

		protected AbstractComponentEditor(String fieldName, E editor, Observable<?> until) {
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
			theEditor = editor;
			theUntil = until == null ? Observable.empty() : until;
			theSettableTooltip = SettableValue
				.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<String>> parameterized(String.class)).build();
			theTooltip = ObservableValue.flatten(theSettableTooltip);
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public E getEditor() {
			return theEditor;
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			theFieldName = fieldName;
			return (P) this;
		}

		@Override
		public P modifyFieldLabel(Consumer<FontAdjuster> labelModifier) {
			if (theFieldLabelModifier == null)
				theFieldLabelModifier = labelModifier;
			else {
				Consumer<FontAdjuster> prev = theFieldLabelModifier;
				theFieldLabelModifier = f -> {
					prev.accept(f);
					labelModifier.accept(f);
				};
			}
			return (P) this;
		}

		@Override
		public P withFont(Consumer<FontAdjuster> font) {
			if (theFont == null)
				theFont = font;
			else {
				Consumer<FontAdjuster> prev = theFont;
				theFont = f -> {
					prev.accept(f);
					font.accept(f);
				};
			}
			return (P) this;
		}

		@Override
		public P visibleWhen(ObservableValue<Boolean> visible) {
			isVisible = visible;
			return (P) this;
		}

		@Override
		public P withLayoutConstraints(Object constraints) {
			theLayoutConstraints = constraints;
			return (P) this;
		}

		Object getLayoutConstraints() {
			return theLayoutConstraints;
		}

		@Override
		public P fill() {
			isFillH = true;
			return (P) this;
		}

		@Override
		public P fillV() {
			isFillV = true;
			return (P) this;
		}

		@Override
		public P withTooltip(ObservableValue<String> tooltip) {
			theSettableTooltip.set(tooltip, null);
			return (P) this;
		}

		ObservableValue<String> getTooltip() {
			isTooltipHandled = true;
			return theTooltip;
		}

		@Override
		public P decorate(Consumer<ComponentDecorator> decoration) {
			if (theDecorator == null) {
				theDecorator = new ComponentDecorator();
				theDecorators = new ArrayList<>();
			}
			decoration.accept(theDecorator);
			theDecorators.add(decoration);
			if (theBuiltComponent != null)
				theDecorator.adjust(theBuiltComponent);
			return (P) this;
		}

		@Override
		public P repaintOn(Observable<?> repaint) {
			if (theRepaint == null)
				theRepaint = repaint;
			else
				theRepaint = Observable.or(theRepaint, repaint);
			return (P) this;
		}

		protected P withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			if (theGlassPane == null)
				theGlassPane = new SimpleHPanel<>(null, new JPanel(layout), getUntil());
			panel.accept(theGlassPane);
			return (P) this;
		}

		boolean isDecorated() {
			return theDecorator != null;
		}

		@Override
		public P modifyEditor(Consumer<? super E> modify) {
			modify.accept(getEditor());
			return (P) this;
		}

		@Override
		public P modifyComponent(Consumer<Component> component) {
			if (theBuiltComponent != null)
				component.accept(theBuiltComponent);
			else if (theComponentModifier == null)
				theComponentModifier = component;
			else {
				Consumer<Component> old = theComponentModifier;
				theComponentModifier = comp -> {
					old.accept(comp);
					component.accept(comp);
				};
			}
			return (P) this;
		}

		@Override
		public Alert alert(String title, String message) {
			return new SimpleAlert(getComponent(), title, message);
		}

		@Override
		public P withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
			thePopupMenu = menu;
			return (P) this;
		}

		@Override
		public P onMouse(Consumer<MouseEvent> onMouse) {
			if (theMouseListener == null)
				theMouseListener = onMouse;
			else {
				Consumer<MouseEvent> old = theMouseListener;
				theMouseListener = event -> {
					old.accept(event);
					onMouse.accept(event);
				};
			}
			return (P) this;
		}

		@Override
		public P withName(String name) {
			theName = name;
			return (P) this;
		}

		protected <C extends JComponent> C onFieldName(C fieldNameComponent, Consumer<String> fieldName, Observable<?> until) {
			if (theFieldName == null)
				return null;
			theFieldName.changes().takeUntil(until).act(evt -> fieldName.accept(evt.getNewValue()));
			if (fieldNameComponent != null && theFieldLabelModifier != null)
				new FontAdjuster().configure(theFieldLabelModifier).adjust(fieldNameComponent);
			if (fieldNameComponent != null && theFont != null)
				new FontAdjuster().configure(theFont).adjust(fieldNameComponent);
			return fieldNameComponent;
		}

		protected Component createFieldNameLabel(Observable<?> until) {
			if (theFieldName == null)
				return null;
			JLabel fieldNameLabel = new JLabel(theFieldName.get());
			return onFieldName(fieldNameLabel, fieldNameLabel::setText, until);
		}

		private boolean decorated = false;

		protected Component decorate(Component c) {
			if (!isTooltipHandled && theEditor instanceof JComponent)
				theTooltip.changes().takeUntil(getUntil()).act(evt -> ((JComponent) theEditor).setToolTipText(evt.getNewValue()));
			if (theDecorator != null)
				theDecorator.decorate(c);
			if (theRepaint != null) {
				Component fc = c;
				theRepaint.takeUntil(theUntil).act(__ -> {
					if (theDecorator != null) {
						for (Consumer<ComponentDecorator> deco : theDecorators)
							deco.accept(theDecorator);
						theDecorator.decorate(fc);
					}
					fc.repaint();
				});
			}
			if (decorated)
				return c;
			decorated = true;
			if (theName != null)
				c.setName(theName);
			class JPMBuilder extends AbstractComponentEditor<JPopupMenu, JPMBuilder> implements MenuBuilder<JPopupMenu, JPMBuilder> {
				ObservableValue<String> theDisablement;

				public JPMBuilder(Observable<?> until) {
					super(null, new JPopupMenu(), until);
				}

				@Override
				public JPMBuilder withText(ObservableValue<String> text) {
					System.err.println("Text cannot be set for a popup menu");
					return this;
				}

				@Override
				public JPMBuilder disableWith(ObservableValue<String> disabled) {
					if (theDisablement == null)
						theDisablement = disabled;
					else
						theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, e -> e != null, null, theDisablement,
						disabled);
					return this;
				}

				@Override
				public JPMBuilder withPostLabel(ObservableValue<String> postLabel) {
					System.err.println("Post label cannot be set for a popup menu");
					return this;
				}

				@Override
				public JPMBuilder withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
					System.err.println("Post Button cannot be set for a popup menu");
					return this;
				}

				@Override
				public JPMBuilder withIcon(ObservableValue<? extends Icon> icon) {
					System.err.println("Icon not supported for a popup menu");
					return this;
				}

				@Override
				protected Component createPostLabel(Observable<?> until) {
					return null;
				}

				@Override
				public JPMBuilder withSubMenu(String name, Consumer<MenuBuilder<JMenu, ?>> subMenu) {
					JMenu jmenu = null;
					for (int m = 0; m < getEditor().getComponentCount(); m++) {
						if (getEditor().getComponent(m) instanceof JMenu && ((JMenu) getEditor().getComponent(m)).getText().equals(name)) {
							jmenu = (JMenu) getEditor().getComponent(m);
							break;
						}
					}
					boolean found = jmenu != null;
					if (!found)
						jmenu = new JMenu(name);
					JMenuBuilder<?> builder = new JMenuBuilder<>(jmenu, getUntil());
					subMenu.accept(builder);
					if (!found)
						getEditor().add((JMenu) builder.getComponent());
					return this;
				}

				@Override
				public JPMBuilder withAction(String name, ObservableAction<?> action, Consumer<ButtonEditor<JMenuItem, ?>> ui) {
					JMenuItem item = new JMenuItem(name);
					ButtonEditor<JMenuItem, ?> button = new PanelPopulationImpl.SimpleButtonEditor<>(name, item, name, action, false,
						getUntil());
					if (ui != null) {
						ui.accept(button);
					}
					getEditor().add((JMenuItem) button.getComponent());
					return this;
				}
			}
			JPMBuilder menuBuilder;
			JPopupMenu popup;
			if (thePopupMenu != null) {
				menuBuilder = new JPMBuilder(getUntil());
				thePopupMenu.accept(menuBuilder);
				popup = (JPopupMenu) menuBuilder.createComponent();
			} else {
				menuBuilder = null;
				popup = null;
			}
			if (theMouseListener != null || popup != null) {
				// The popup needs to be added to the editor if possible.
				// If it's added to a parent, it may not appear for the editor itself
				Component finalC;
				if (theEditor instanceof Component)
					finalC = (Component) theEditor;
				else
					finalC = c;
				finalC.addMouseListener(new MouseListener() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if (theMouseListener != null)
							theMouseListener.accept(e);
						if (popup != null //
							&& e.getClickCount() == 1 && SwingUtilities.isRightMouseButton(e)) {
							boolean anyActionsVisible = false;
							for (Component item : popup.getComponents()) {
								if (item.isVisible()) {
									anyActionsVisible = true;
									break;
								}
							}
							if (anyActionsVisible)
								popup.show(finalC, e.getX(), e.getY());
						}
					}

					@Override
					public void mousePressed(MouseEvent e) {
						if (theMouseListener != null)
							theMouseListener.accept(e);
					}

					@Override
					public void mouseReleased(MouseEvent e) {
						if (theMouseListener != null)
							theMouseListener.accept(e);
					}

					@Override
					public void mouseEntered(MouseEvent e) {
						if (theMouseListener != null)
							theMouseListener.accept(e);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						if (theMouseListener != null)
							theMouseListener.accept(e);
					}
				});
			}
			if (theComponentModifier != null)
				theComponentModifier.accept(c);
			if (theGlassPane != null) {
				JPanel panel = new JPanel(new LayerLayout());
				panel.add(c);
				panel.add(theGlassPane.getComponent());
				c = panel;
			}
			return c;
		}

		protected Component createComponent() {
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component, this should be overridden");
			return (Component) theEditor;
		}

		public void reset() {
			theBuiltComponent = null;
		}

		@Override
		public final Component getComponent() {
			if (theBuiltComponent == null) {
				theBuiltComponent = decorate(createComponent());
			}
			return theBuiltComponent;
		}

		protected boolean isFill() {
			return isFillH;
		}

		protected boolean isFillV() {
			return isFillV;
		}

		protected ObservableValue<Boolean> isVisible() {
			return isVisible;
		}

		protected abstract Component createPostLabel(Observable<?> until);
	}

	public interface SettingsMenu<C extends Container, P extends SettingsMenu<C, P>> extends PanelPopulator<C, P>, Iconized<P> {
	}

	public interface Tooltipped<T extends Tooltipped<T>> {
		default T withTooltip(String tooltip) {
			return withTooltip(tooltip == null ? null : ObservableValue.of(tooltip));
		}

		T withTooltip(ObservableValue<String> tooltip);
	}

	public interface FieldEditor<E, P extends FieldEditor<E, P>> extends ComponentEditor<E, P>, Tooltipped<P> {
		default P withPostLabel(String postLabel) {
			return withPostLabel(postLabel == null ? null : ObservableValue.of(postLabel));
		}

		P withPostLabel(ObservableValue<String> postLabel);

		default P withPostButton(String buttonText, Consumer<Object> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			return withPostButton(buttonText, new ObservableAction<Void>() {
				@Override
				public TypeToken<Void> getType() {
					return TypeTokens.get().of(Void.class);
				}

				@Override
				public Void act(Object cause) throws IllegalStateException {
					action.accept(cause);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return SettableValue.ALWAYS_ENABLED;
				}
			}, modify);
		}

		P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify);
	}

	public interface Iconized<I> {
		default I withIcon(Class<?> resourceAnchor, String location, int width, int height) {
			return withIcon(ObservableSwingUtils.getFixedIcon(resourceAnchor, location, width, height));
		}

		default I withIcon(Icon icon) {
			return withIcon(icon == null ? null : ObservableValue.of(icon));
		}

		I withIcon(ObservableValue<? extends Icon> icon);
	}

	public interface ButtonEditor<B extends JComponent, P extends ButtonEditor<B, P>> extends FieldEditor<B, P>, Iconized<P> {
		default P withText(String text) {
			return withText(text == null ? null : ObservableValue.of(text));
		}

		P withText(ObservableValue<String> text);

		P disableWith(ObservableValue<String> disabled);
	}

	public interface ImageControl {
		ImageControl setLocation(String location);

		ImageControl variableLocation(ObservableValue<String> location);

		ImageControl variable(ObservableValue<? extends Icon> icon);

		ImageControl withSize(int width, int height);
	}

	public interface ComboEditor<F, P extends ComboEditor<F, P>> extends FieldEditor<JComboBox<F>, P> {
		default P renderAs(Function<? super F, String> renderer) {
			return renderWith(ObservableCellRenderer.formatted(renderer));
		}

		P renderWith(ObservableCellRenderer<F, F> renderer);

		P withValueTooltip(Function<? super F, String> tooltip);

		String getTooltip(F value);
	}

	public interface SteppedFieldEditor<E, F, P extends SteppedFieldEditor<E, F, P>> extends FieldEditor<E, P> {
		P withStepSize(F stepSize);
	}

	public interface SliderEditor<E, P extends SliderEditor<E, P>> extends FieldEditor<E, P> {
		default P withBounds(double min, double max) {
			return withBounds(ObservableValue.of(min), ObservableValue.of(max));
		}

		P withBounds(ObservableValue<Double> min, ObservableValue<Double> max);

		P adjustBoundsForValue(boolean adjustForValue);

		P enforceNoOverlap(boolean noOverlap, boolean withinSliderBounds);
	}

	public interface ProgressEditor<P extends ProgressEditor<P>> extends FieldEditor<JProgressBar, P> {
		P withTaskLength(ObservableValue<Integer> length);

		P withProgress(ObservableValue<Integer> progress);

		default P withTaskLength(int length) {
			return withTaskLength(ObservableValue.of(length));
		}

		P indeterminate(ObservableValue<Boolean> indeterminate);

		default P indeterminate() {
			return indeterminate(ObservableValue.of(true));
		}

		P withProgressText(ObservableValue<String> text);
	}

	public interface ToggleEditor<F, TB extends JToggleButton, P extends ToggleEditor<F, TB, P>> extends FieldEditor<Map<F, TB>, P> {
		P render(BiConsumer<? super TB, ? super F> renderer);

		P withValueTooltip(Function<? super F, String> tooltip);
	}

	public interface TabPaneEditor<E, P extends TabPaneEditor<E, P>> extends ComponentEditor<E, P> {
		int getTabCount();

		default P withVTab(Object tabID, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withVTab(tabID, getTabCount(), panel, tabModifier);
		}

		P withVTab(Object tabID, int tabIndex, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier);

		default P withHTab(Object tabID, String layoutType, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, makeLayout(layoutType), panel, tabModifier);
		}

		default P withHTab(Object tabID, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			return withHTab(tabID, getTabCount(), layout, panel, tabModifier);
		}

		P withHTab(Object tabID, int tabIndex, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel,
			Consumer<TabEditor<?>> tabModifier);

		default P withTab(Object tabID, Component tabComponent, Consumer<TabEditor<?>> tabModifier) {
			return withTab(tabID, getTabCount(), tabComponent, tabModifier);
		}

		P withTab(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier);

		P withSelectedTab(SettableValue<?> tabID);

		P onSelectedTab(Consumer<ObservableValue<Object>> tabID);
	}

	public interface TabEditor<P extends TabEditor<P>> {
		default P setName(String name) {
			if (getName() instanceof SettableValue)
				((SettableValue<String>) getName()).set(name, null);
			else
				setName(ObservableValue.of(name));
			return (P) this;
		}

		P setName(ObservableValue<String> name);

		ObservableValue<String> getName();

		default P setImage(ObservableValue<Image> icon) {
			return setIcon(icon == null ? null : icon.map(img -> new ImageIcon(img)));
		}

		P setIcon(ObservableValue<Icon> icon);

		ObservableValue<Icon> getIcon();

		P selectOn(Observable<?> select);

		P onSelect(Consumer<ObservableValue<Boolean>> onSelect);

		P remove();

		P select();

		P setRemovable(boolean removable);

		P onRemove(Consumer<Object> onRemove);
	}

	public interface SplitPane<P extends SplitPane<P>> extends ComponentEditor<JSplitPane, P> {
		P firstV(Consumer<PanelPopulator<?, ?>> vPanel);

		default P firstH(String layoutType, Consumer<PanelPopulator<?, ?>> hPanel) {
			return firstH(makeLayout(layoutType), hPanel);
		}

		P firstH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel);

		P first(Component component);

		P lastV(Consumer<PanelPopulator<?, ?>> vPanel);

		default P lastH(String layoutType, Consumer<PanelPopulator<?, ?>> hPanel) {
			return lastH(makeLayout(layoutType), hPanel);
		}

		P lastH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel);

		P last(Component component);

		P withSplitLocation(int split);
		P withSplitProportion(double split);

		P withSplitLocation(SettableValue<Integer> split);

		P withSplitProportion(SettableValue<Double> split);
	}

	public interface ScrollPane<P extends ScrollPane<P>> extends ComponentEditor<JScrollPane, P> {
		P withVContent(Consumer<PanelPopulator<?, ?>> panel);

		default P withHContent(String layoutType, Consumer<PanelPopulator<?, ?>> panel) {
			return withHContent(makeLayout(layoutType), panel);
		}

		P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		P withContent(Component component);

		P withVRowHeader(Consumer<PanelPopulator<?, ?>> panel);

		default P withHRowHeader(String layoutType, Consumer<PanelPopulator<?, ?>> panel) {
			return withHRowHeader(makeLayout(layoutType), panel);
		}

		P withHRowHeader(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		P withRowHeader(Component component);

		default P withHColumnHeader(String layoutType, Consumer<PanelPopulator<?, ?>> panel) {
			return withHColumnHeader(makeLayout(layoutType), panel);
		}

		P withHColumnHeader(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel);

		P withColumnHeader(Component component);
	}

	public interface CollapsePanel<CP extends Container, C extends Container, P extends CollapsePanel<CP, C, P>>
	extends PanelPopulator<C, P> {
		P withCollapsed(SettableValue<Boolean> collapsed);

		P animated(boolean animated);

		P modifyCP(Consumer<CP> cp);

		P withHeader(Consumer<PanelPopulator<JPanel, ?>> header);
	}

	public interface CollectionWidgetBuilder<R, C extends Component, P extends CollectionWidgetBuilder<R, C, P>>
	extends ComponentEditor<C, P> {
		P withSelection(SettableValue<R> selection, boolean enforceSingleSelection);

		P withSelection(ObservableCollection<R> selection);

		List<R> getSelection();

		P disableWith(ObservableValue<String> disabled);

		P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod);

		default P withAction(String actionName, Consumer<? super R> action, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(actionName, values -> {
				for (R value : values)
					action.accept(value);
			}, actionMod);
		}

		P withMultiAction(String actionName, Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod);

		P withItemName(String itemName);

		String getItemName();

		P withActionsOnTop(boolean actionsOnTop);
	}

	public interface ListWidgetBuilder<R, C extends Component, P extends ListWidgetBuilder<R, C, P>> {
		P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod);

		P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod);

		ObservableCollection<? extends R> getRows();

		String getItemName();
	}

	public interface ListBuilder<R, P extends ListBuilder<R, P>>
	extends CollectionWidgetBuilder<R, LittleList<R>, P>, ListWidgetBuilder<R, LittleList<R>, P>, FieldEditor<LittleList<R>, P> {
		P render(Consumer<CategoryRenderStrategy<R, R>> render);
	}

	public interface AbstractTableBuilder<R, C extends Component, P extends AbstractTableBuilder<R, C, P>> {
		P withColumns(ObservableCollection<? extends CategoryRenderStrategy<R, ?>> columns);

		P withColumn(CategoryRenderStrategy<R, ?> column);

		default <C> P withColumn(String name, TypeToken<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			CategoryRenderStrategy<R, C> col = new CategoryRenderStrategy<>(name, type, accessor);
			if (column != null)
				column.accept(col);
			return withColumn(col);
		}

		default <C> P withColumn(String name, Class<C> type, Function<? super R, ? extends C> accessor, //
			Consumer<CategoryRenderStrategy<R, C>> column) {
			return withColumn(name, TypeTokens.get().of(type), accessor, column);
		}

		P withColumnHeader(boolean columnHeader);

		P withAdaptiveHeight(int minRows, int prefRows, int maxRows);

		P scrollable(boolean scrollable);
	}

	public interface TableBuilder<R, P extends TableBuilder<R, P>>
	extends CollectionWidgetBuilder<R, JTable, P>, ListWidgetBuilder<R, JTable, P>, AbstractTableBuilder<R, JTable, P> {
		@SuppressWarnings("rawtypes")
		static final DbugAnchorType<TableBuilder> DBUG = Dbug.common().anchor(TableBuilder.class, a -> a//
			.withField("type", true, false, TypeTokens.get().keyFor(TypeToken.class).wildCard())//
			.withEvent("create").withEvent("adjustWidth").withEvent("layoutColumns").withEvent("adjustHeight")//
			);

		default P withNameColumn(Function<? super R, String> getName, BiConsumer<? super R, String> setName, boolean unique,
			Consumer<CategoryRenderStrategy<R, String>> column) {
			return withColumn("Name", String.class, getName, col -> {
				if (setName != null) {
					col.withMutation(mut -> {
						mut.asText(Format.TEXT).mutateAttribute2((row, name) -> {
							setName.accept(row, name);
							return name;
						}).withRowUpdate(true);
						if (unique)
							mut.filterAccept((rowEl, name) -> {
								if (StringUtils.isUniqueName(getRows(), getName, name, rowEl.get())) {
									return null;
								} else {
									String itemName = getItemName();
									return new StringBuilder(StringUtils.getIndefiniteArticle(name)).append(' ').append(itemName)
										.append(" named \"").append(name).append("\" already exists").toString();
								}
							});
					});
				}
				if (column != null)
					column.accept(col);
			});
		}

		P withInitialSelection(Predicate<? super R> initSelection);

		P withIndexColumn(String columnName, Consumer<CategoryRenderStrategy<R, Integer>> column);

		<C> P withDynamicColumns(Function<? super R, ? extends Collection<? extends C>> columnValues, //
			Comparator<? super C> columnSort, //
			Function<? super C, CategoryRenderStrategy<R, ?>> columnCreator);

		P withTableOption(Consumer<? super PanelPopulator<?, ?>> panel);

		P withFiltering(ObservableValue<? extends TableContentControl> filter);

		P withCountTitle(String displayedText);

		ObservableCollection<R> getFilteredRows();

		P withMove(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P withMoveToEnd(boolean up, Consumer<DataAction<R, ?>> actionMod);

		P dragSourceRow(Consumer<? super Dragging.TransferSource<R>> source);

		P dragAcceptRow(Consumer<? super Dragging.TransferAccepter<R, R, R>> accept);

		P withMouseListener(ObservableTableModel.RowMouseListener<? super R> listener);
	}

	public interface DataAction<R, A extends DataAction<R, A>> {
		List<R> getActionItems();

		/**
		 * @param allowed Whether this action should be enabled when multiple values are selected
		 * @return This action
		 */
		A allowForMultiple(boolean allowed);

		/**
		 * @param allowed Whether this action should be enabled when no values are selected
		 * @return This action
		 */
		A allowForEmpty(boolean allowed);

		/**
		 * @param allowed Whether this action should be enabled when the action is not {@link #allowWhen(Function, Consumer) allowed} for
		 *        some values, but the set of the values for which the action is allowed match this action's
		 *        {@link #allowForMultiple(boolean) multi} and {@link #allowForEmpty(boolean) empty} selection settings.
		 * @return This action
		 */
		A allowForAnyEnabled(boolean allowed);

		/**
		 * @param display Whether this action should be displayed to the user disabled or hidden completely when not enabled
		 * @return This action
		 */
		A displayWhenDisabled(boolean display);

		/**
		 * @param button Whether this action should be available as a button in a panel adjacent to the widget
		 * @return This action
		 */
		A displayAsButton(boolean button);

		/**
		 * @param popup Whether this action should be available as a menu item in a popup menu, typically accessed via right-click
		 * @return This action
		 */
		A displayAsPopup(boolean popup);

		/** @return Whether this action should be available as a button in a panel adjacent to the widget */
		boolean isButton();

		/** @return Whether this action should be available as a menu item in a popup menu, typically accessed via right-click */
		boolean isPopup();

		boolean isAllowedForMultiple();

		boolean isAllowedForEmpty();

		boolean isDisplayedWhenDisabled();

		A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation);

		A allowWhenMulti(Function<? super List<? extends R>, String> filter, Consumer<ActionEnablement<List<? extends R>>> operation);

		A withTooltip(Function<? super List<? extends R>, String> tooltip);

		// A disableWith(Function<? super List<? extends R>, String> disabled);

		A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod);

		default A confirm(String alertTitle, String alertText, boolean confirmType) {
			return confirm(alertTitle, selected -> alertText, confirmType);
		}

		A confirm(String alertTitle, Function<List<? extends R>, String> alertText, boolean confirmType);

		/**
		 * @param alertTitle The title for the alert dialog
		 * @param alertPreText The beginning of the question to use to confirm the action on the items
		 * @param postText The end of the question to use to confirm the action on the items
		 * @param confirmType True for OK/Cancel, false for Yes/No
		 * @return This action
		 */
		A confirmForItems(String alertTitle, String alertPreText, String postText, boolean confirmType);

		A modifyButton(Consumer<ButtonEditor<?, ?>> buttonMod);
	}

	public static class ActionEnablement<E> implements Function<E, String> {
		private Function<? super E, String> theEnablement;

		public ActionEnablement(Function<? super E, String> enablement) {
			theEnablement = enablement;
		}

		public ActionEnablement<E> or(Function<? super E, String> filter, Consumer<ActionEnablement<E>> operation) {
			Function<? super E, String> newFilter;
			if (operation == null) {
				newFilter = filter;
			} else {
				ActionEnablement<E> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			}
			if (theEnablement == null)
				theEnablement = filter;
			else {
				Function<? super E, String> oldFilter = theEnablement;
				// TODO Use LambdaUtils here to make the Object methods work well
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg == null)
						return null;
					return newFilter.apply(value);
				};
			}
			return this;
		}

		public ActionEnablement<E> and(Function<? super E, String> filter, Consumer<ActionEnablement<E>> operation) {
			Function<? super E, String> newFilter;
			if (operation == null) {
				newFilter = filter;
			} else {
				ActionEnablement<E> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			}
			if (theEnablement == null)
				theEnablement = filter;
			else {
				Function<? super E, String> oldFilter = theEnablement;
				// TODO Use LambdaUtils here to make the Object methods work well
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return this;
		}

		@Override
		public String apply(E value) {
			if (theEnablement == null)
				return null;
			else
				return theEnablement.apply(value);
		}

		@Override
		public String toString() {
			return String.valueOf(theEnablement);
		}
	}

	public interface AbstractTreeEditor<F, C extends Component, P extends AbstractTreeEditor<F, C, P>>
	extends CollectionWidgetBuilder<BetterList<F>, C, P> {
		ObservableValue<? extends F> getRoot();

		CategoryRenderStrategy<BetterList<F>, F> getRender();

		P withRender(CategoryRenderStrategy<BetterList<F>, F> render);

		default P withRender(Consumer<CategoryRenderStrategy<BetterList<F>, F>> render) {
			render.accept(getRender());
			return (P) this;
		}

		P withValueSelection(SettableValue<F> selection, boolean enforceSingleSelection);

		P withValueSelection(ObservableCollection<F> selection);

		P withLeafTest(Predicate<? super F> leafTest);

		boolean isVisible(List<? extends F> path);

		boolean isExpanded(List<? extends F> path);
	}

	public interface TreeEditor<F, P extends TreeEditor<F, P>> extends AbstractTreeEditor<F, JTree, P> {
	}

	public interface TreeTableEditor<F, P extends TreeTableEditor<F, P>>
	extends AbstractTreeEditor<F, JXTreeTable, P>, AbstractTableBuilder<BetterList<F>, JXTreeTable, P> {
	}

	public interface Alert {
		Alert info();

		Alert warning();

		Alert error();

		Alert withIcon(String location, Consumer<ImageControl> image);

		void display();

		/**
		 * @param confirmType Whether to display "Yes" and "No" (false), or "OK" and "Cancel" (true) for the confirmation
		 * @return Whether the user clicked "Yes" or "OK"
		 */
		boolean confirm(boolean confirmType);

		<T> T input(TypeToken<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify);

		default <T> T input(Class<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify) {
			return input(TypeTokens.get().of(type), format, initial, modify);
		}

		Color inputColor(boolean withAlpha, Color initial, Function<Color, String> filter);

		default Color inputColor(boolean withAlpha, Color initial) {
			return inputColor(withAlpha, initial, __ -> null);
		}
	}

	public interface WindowBuilder<W extends Window, P extends WindowBuilder<W, P>> {
		W getWindow();

		default P withTitle(String title) {
			return withTitle(ObservableValue.of(TypeTokens.get().STRING, title));
		}
		P withTitle(ObservableValue<String> title);
		ObservableValue<String> getTitle();

		default P withIcon(Class<?> clazz, String location) {
			ImageIcon icon = ObservableSwingUtils.getIcon(clazz, location);
			if (icon != null)
				withIcon(icon.getImage());
			return (P) this;
		}
		default P withIcon(Image icon) {
			return withIcon(ObservableValue.of(TypeTokens.get().of(Image.class), icon));
		}
		P withIcon(ObservableValue<? extends Image> icon);
		ObservableValue<? extends Image> getIcon();

		P withX(SettableValue<Integer> x);
		P withY(SettableValue<Integer> y);
		P withWidth(SettableValue<Integer> width);
		P withHeight(SettableValue<Integer> height);
		default P withLocation(ObservableConfig locationConfig) {
			withX(locationConfig.asValue(Integer.class).at("x").withFormat(Format.INT, () -> null).buildValue(null));
			return withY(locationConfig.asValue(Integer.class).at("y").withFormat(Format.INT, () -> getWindow().getX()).buildValue(null));
		}
		default P withSize(ObservableConfig sizeConfig) {
			withWidth(sizeConfig.asValue(Integer.class).at("width").withFormat(Format.INT, () -> null).buildValue(null));
			return withHeight(sizeConfig.asValue(Integer.class).at("height").withFormat(Format.INT, () -> null).buildValue(null));
		}
		default P withBounds(ObservableConfig boundsConfig) {
			withLocation(boundsConfig);
			return withSize(boundsConfig);
		}

		default P withSize(int width, int height) {
			withWidth(SettableValue.build(int.class).withValue(width).build());
			withHeight(SettableValue.build(int.class).withValue(height).build());
			return (P) this;
		}

		P withVisible(SettableValue<Boolean> visible);

		P withMenuBar(Consumer<MenuBarBuilder<?>> menuBar);

		default P disposeOnClose(boolean dispose) {
			return withCloseAction(dispose ? WindowConstants.DISPOSE_ON_CLOSE : WindowConstants.HIDE_ON_CLOSE);
		}

		/**
		 * @param closeAction The action to perform when the user closes the window
		 * @return This builder
		 * @see WindowConstants#DO_NOTHING_ON_CLOSE
		 * @see WindowConstants#HIDE_ON_CLOSE
		 * @see WindowConstants#DISPOSE_ON_CLOSE
		 * @see WindowConstants#EXIT_ON_CLOSE
		 */
		P withCloseAction(int closeAction);

		P withVContent(Consumer<PanelPopulator<?, ?>> content);

		default P withHContent(String layoutType, Consumer<PanelPopulator<?, ?>> content) {
			return withHContent(layoutType == null ? null : makeLayout(layoutType), content);
		}

		P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> content);

		P withContent(Component content);

		P run(Component relativeTo);
	}

	public interface DialogBuilder<D extends JDialog, P extends DialogBuilder<D, P>> extends WindowBuilder<D, P> {
		default P modal(boolean modal) {
			return withModality(modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
		}

		default P withModality(ModalityType modality) {
			return withModality(ObservableValue.of(modality));
		}

		P withModality(ObservableValue<ModalityType> modality);
	}

	public interface MenuBuilder<B extends JComponent, M extends MenuBuilder<B, M>> extends ButtonEditor<B, M> {
		M withAction(String name, ObservableAction<?> action, Consumer<ButtonEditor<JMenuItem, ?>> ui);

		default M withAction(String name, Consumer<Object> action, Consumer<ButtonEditor<JMenuItem, ?>> ui) {
			return withAction(name, ObservableAction.of(TypeTokens.get().VOID, cause -> {
				action.accept(cause);
				return null;
			}), ui);
		}

		M withSubMenu(String name, Consumer<MenuBuilder<JMenu, ?>> subMenu);
	}

	public interface MenuBarBuilder<M extends MenuBarBuilder<M>> {
		M withMenu(String menuName, Consumer<MenuBuilder<JMenu, ?>> menu);
	}

	public interface ComboButtonBuilder<F, B extends ComboButton<F>, P extends ComboButtonBuilder<F, B, P>> extends ButtonEditor<B, P> {
		default P renderAs(Function<? super F, String> renderer) {
			return renderWith(ObservableCellRenderer.formatted(renderer));
		}

		default P renderWith(ObservableCellRenderer<F, F> renderer) {
			return render(r -> r.withRenderer(renderer));
		}

		default P withValueTooltip(Function<? super F, String> tooltip) {
			return render(r -> r.withValueTooltip((__, f) -> tooltip.apply(f)));
		}

		P render(Consumer<CategoryRenderStrategy<F, F>> render);
	}

	// public interface

	// Drag and cut/copy/paste

	static final String MIG_LAYOUT_CLASS_NAME = "net.miginfocom.swing.MigLayout";

	static final Constructor<? extends LayoutManager2> MIG_LAYOUT_CREATOR;

	static {
		Constructor<? extends LayoutManager2> creator;
		try {
			creator = Class.forName(MIG_LAYOUT_CLASS_NAME).asSubclass(LayoutManager2.class).getConstructor(String.class, String.class);
		} catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
			System.err.println("Could not retrieve needed " + MIG_LAYOUT_CLASS_NAME + " constructor");
			e.printStackTrace();
			creator = null;
		}
		MIG_LAYOUT_CREATOR = creator;
	}

	static LayoutManager2 createMigLayout(boolean withInsets, Supplier<String> err) {
		String layoutConstraints = "fillx, hidemode 3";
		if (!withInsets)
			layoutConstraints += ", insets 0";
		LayoutManager2 migLayout;
		try {
			migLayout = MIG_LAYOUT_CREATOR.newInstance(layoutConstraints, "[shrink][grow][shrink]");
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
			| SecurityException e) {
			throw new IllegalStateException(
				ObservableSwingUtils.class.getName() + " could not instantiate " + MIG_LAYOUT_CLASS_NAME + ": " + err.get(), e);
		}
		return migLayout;
	}

	static LayoutManager makeLayout(String layoutType) {
		LayoutManager layout;
		if (layoutType == null)
			layoutType = "box";
		switch (layoutType.toLowerCase()) {
		case "mig":
			layout = createMigLayout(false, () -> "use addHPanel(String, LayoutManager, Consumer)");
			break;
		case "ctr":
		case "center":
			layout = new JustifiedBoxLayout(false).mainCenter();
			break;
		case "box":
		case "just":
		case "justify":
		case "justified":
		default:
			layout = new JustifiedBoxLayout(false).mainJustified();
			break;
		}
		return layout;
	}

	private PanelPopulation() {}
}

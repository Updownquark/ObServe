package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXMultiThumbSlider;
import org.jdesktop.swingx.JXPanel;
import org.jdesktop.swingx.multislider.MultiThumbModel;
import org.jdesktop.swingx.multislider.ThumbDataEvent;
import org.jdesktop.swingx.multislider.ThumbDataListener;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils.FontAdjuster;
import org.observe.util.swing.PanelPopulation.ActionEnablement;
import org.observe.util.swing.PanelPopulation.Alert;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.CollapsePanel;
import org.observe.util.swing.PanelPopulation.CollectionWidgetBuilder;
import org.observe.util.swing.PanelPopulation.ComboEditor;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.FieldEditor;
import org.observe.util.swing.PanelPopulation.ImageControl;
import org.observe.util.swing.PanelPopulation.ListBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.ProgressEditor;
import org.observe.util.swing.PanelPopulation.ScrollPane;
import org.observe.util.swing.PanelPopulation.SettingsMenu;
import org.observe.util.swing.PanelPopulation.SliderEditor;
import org.observe.util.swing.PanelPopulation.SplitPane;
import org.observe.util.swing.PanelPopulation.SteppedFieldEditor;
import org.observe.util.swing.PanelPopulation.TabEditor;
import org.observe.util.swing.PanelPopulation.TabPaneEditor;
import org.observe.util.swing.PanelPopulation.TableBuilder;
import org.observe.util.swing.PanelPopulation.ToggleEditor;
import org.observe.util.swing.PanelPopulation.TreeEditor;
import org.observe.util.swing.PanelPopulation.TreeTableEditor;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.qommons.BiTuple;
import org.qommons.Colors;
import org.qommons.Identifiable;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

class PanelPopulationImpl {
	private PanelPopulationImpl() {
	}

	public interface PartialPanelPopulatorImpl<C extends Container, P extends PartialPanelPopulatorImpl<C, P>>
	extends PanelPopulator<C, P> {
		@Override
		Observable<?> getUntil();

		void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled);

		Supplier<Transactable> getLock();

		default void doAdd(SimpleFieldEditor<?, ?> field) {
			doAdd(field, false);
		}

		default void doAdd(SimpleFieldEditor<?, ?> field, boolean scrolled) {
			doAdd(field, field.createFieldNameLabel(getUntil()), field.createPostLabel(getUntil()), scrolled);
		}

		@Override
		default <F> P addTextField(String fieldName, SettableValue<F> field, Format<F> format,
			Consumer<FieldEditor<ObservableTextField<F>, ?>> modify) {
			SimpleFieldEditor<ObservableTextField<F>, ?> fieldPanel = new SimpleFieldEditor<>(fieldName,
				new ObservableTextField<>(field, format, getUntil()), getLock());
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
				new ObservableTextArea<>(field, format, getUntil()), getLock());
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
		default <F> P addLabel(String fieldName, ObservableValue<F> field, Function<? super F, String> format,
			Consumer<FieldEditor<JLabel, ?>> modify) {
			JLabel label = new JLabel();
			field.changes().takeUntil(getUntil()).act(evt -> {
				label.setText(format.apply(evt.getNewValue()));
			});
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getLock());
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
			SimpleFieldEditor<JLabel, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, label, getLock());
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
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getLock());
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
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
				ObservableAction.nullAction(TypeTokens.get().WILDCARD, null), getLock(), false);
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
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
			SimpleSteppedFieldEditor<JSpinner, F, ?> fieldPanel = new SimpleSteppedFieldEditor<>(fieldName, spinner, getLock(),
				stepSize -> {
					((SpinnerNumberModel) spinner.getModel()).setStepSize((Number) stepSize);
				});
			ObservableSwingUtils.spinnerFor(spinner, fieldPanel.getTooltip().get(), value, purifier);
			if (modify != null)
				modify.accept(fieldPanel);
			if (fieldPanel.isDecorated())
				value.noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> fieldPanel.decorate(fieldPanel.getComponent()));
			doAdd(fieldPanel);
			return (P) this;
		}

		@Override
		default P addMultiSlider(String fieldName, ObservableCollection<Double> values, Consumer<SliderEditor<?, ?>> modify) {
			SimpleMultiSliderEditor<?> compEditor = new SimpleMultiSliderEditor<>(fieldName, values, getLock());
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
			SimpleComboEditor<F, ?> fieldPanel = new SimpleComboEditor<>(fieldName, new JComboBox<>(), getLock());
			if (modify != null)
				modify.accept(fieldPanel);
			Subscription sub = ObservableComboBoxModel.comboFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), fieldPanel::getTooltip,
				observableValues, value);
			getUntil().take(1).act(__ -> sub.unsubscribe());
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
				buttonType, buttonCreator, getLock());
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
				new ObservableFileButton(value, open, getUntil()), getLock());
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
			SimpleButtonEditor<JButton, ?> field = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, getLock(), false)
				.withText(buttonText);
			if (modify != null)
				modify.accept(field);
			doAdd(field);
			return (P) this;
		}

		@Override
		default P addProgressBar(String fieldName, Consumer<ProgressEditor<?>> progress) {
			SimpleProgressEditor<?> editor = new SimpleProgressEditor<>(fieldName, getLock());
			progress.accept(editor);
			if (editor.isDecorated())
				editor.getProgress().noInitChanges().safe(ThreadConstraint.EDT).takeUntil(getUntil())
				.act(__ -> editor.decorate(editor.getComponent()));
			doAdd(editor);
			return (P) this;
		}

		@Override
		default P addTabs(Consumer<TabPaneEditor<JTabbedPane, ?>> tabs) {
			SimpleTabPaneEditor<?> tabPane = new SimpleTabPaneEditor<>(getLock(), getUntil());
			tabs.accept(tabPane);
			doAdd(tabPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addSplit(boolean vertical, Consumer<SplitPane<?>> split) {
			SimpleSplitEditor<?> splitPane = new SimpleSplitEditor<>(vertical, getLock(), getUntil());
			split.accept(splitPane);
			doAdd(splitPane, null, null, false);
			return (P) this;
		}

		@Override
		default P addScroll(String fieldName, Consumer<ScrollPane<?>> scroll) {
			SimpleScrollEditor<?> scrollPane = new SimpleScrollEditor<>(fieldName, getLock(), getUntil());
			scroll.accept(scrollPane);
			doAdd(scrollPane);
			return (P) this;
		}

		@Override
		default <R> P addList(ObservableCollection<R> rows, Consumer<ListBuilder<R, ?>> list) {
			SimpleListBuilder<R, ?> tb = new SimpleListBuilder<>(rows.safe(ThreadConstraint.EDT, getUntil()), getLock());
			list.accept(tb);
			doAdd(tb);
			return (P) this;
		}

		@Override
		default <R> P addTable(ObservableCollection<R> rows, Consumer<TableBuilder<R, ?>> table) {
			SimpleTableBuilder<R, ?> tb = new SimpleTableBuilder<>(rows, getLock());
			table.accept(tb);
			doAdd(tb, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTree(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeEditor<F, ?>> modify) {
			SimpleTreeBuilder<F, ?> treeEditor = SimpleTreeBuilder.createTree(getLock(), root, children);
			if (modify != null)
				modify.accept(treeEditor);
			doAdd(treeEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTree2(ObservableValue<? extends F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeEditor<F, ?>> modify) {
			SimpleTreeBuilder<F, ?> treeEditor = SimpleTreeBuilder.createTree2(getLock(), root, children);
			if (modify != null)
				modify.accept(treeEditor);
			doAdd(treeEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTreeTable(ObservableValue<F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children,
			Consumer<TreeTableEditor<F, ?>> modify) {
			SimpleTreeTableBuilder<F, ?> treeTableEditor = SimpleTreeTableBuilder.createTreeTable(getLock(), root, children);
			if (modify != null)
				modify.accept(treeTableEditor);
			doAdd(treeTableEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <F> P addTreeTable2(ObservableValue<F> root,
			Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Consumer<TreeTableEditor<F, ?>> modify) {
			SimpleTreeTableBuilder<F, ?> treeTableEditor = SimpleTreeTableBuilder.createTreeTable2(getLock(), root, children);
			if (modify != null)
				modify.accept(treeTableEditor);
			doAdd(treeTableEditor, null, null, false);
			return (P) this;
		}

		@Override
		default <S> P addComponent(String fieldName, S component, Consumer<FieldEditor<S, ?>> modify) {
			SimpleFieldEditor<S, ?> subPanel = new SimpleFieldEditor<>(fieldName, component, getLock());
			if (modify != null)
				modify.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addHPanel(String fieldName, LayoutManager layout, Consumer<PanelPopulator<JPanel, ?>> panel) {
			SimpleHPanel<JPanel> subPanel = new SimpleHPanel<>(fieldName, new ConformingPanel(layout), getLock(), getUntil());
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel);
			return (P) this;
		}

		@Override
		default P addVPanel(Consumer<PanelPopulator<JPanel, ?>> panel) {
			MigFieldPanel<JPanel, ?> subPanel = new MigFieldPanel<>(new ConformingPanel(), getUntil(), getLock());
			if (panel != null)
				panel.accept(subPanel);
			doAdd(subPanel, null, null, false);
			return (P) this;
		}

		@Override
		default P addSettingsMenu(Consumer<SettingsMenu<JPanel, ?>> menu) {
			SettingsMenuImpl<JPanel, ?> settingsMenu = new SettingsMenuImpl<>(new ConformingPanel(), getUntil(), getLock());
			if (menu != null)
				menu.accept(settingsMenu);
			doAdd(settingsMenu, null, null, false);
			return (P) this;
		}

		@Override
		default P addCollapsePanel(boolean vertical, LayoutManager layout, Consumer<CollapsePanel<JXCollapsiblePane, JXPanel, ?>> panel) {
			JXCollapsiblePane cp = new JXCollapsiblePane();
			cp.setLayout(layout);
			SimpleCollapsePane collapsePanel = new SimpleCollapsePane(cp, getUntil(), getLock(), vertical, layout);
			panel.accept(collapsePanel);
			doAdd(collapsePanel, null, null, false);
			return (P) this;
		}
	}

	static abstract class AbstractComponentEditor<E, P extends AbstractComponentEditor<E, P>> implements ComponentEditor<E, P> {
		final Supplier<Transactable> theLock;
		private final E theEditor;
		private Object theLayoutConstraints;
		private boolean isFillH;
		private boolean isFillV;
		private Consumer<Component> theComponentModifier;
		private ObservableValue<String> theTooltip;
		private boolean isTooltipHandled;
		private SettableValue<ObservableValue<String>> theSettableTooltip;
		private ComponentDecorator theDecorator;
		private Consumer<MouseEvent> theMouseListener;
		private String theName;

		private ObservableValue<Boolean> isVisible;

		AbstractComponentEditor(E editor, Supplier<Transactable> lock) {
			theEditor = editor;
			theLock = lock;
			theSettableTooltip = SettableValue
				.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<String>> parameterized(String.class)).build();
			theTooltip = ObservableValue.flatten(theSettableTooltip);
		}

		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public E getEditor() {
			return theEditor;
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
			if (theDecorator == null)
				theDecorator = new ComponentDecorator();
			decoration.accept(theDecorator);
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
			if (theComponentModifier == null)
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

		private boolean decorated = false;

		protected Component decorate(Component c) {
			if (theDecorator != null)
				theDecorator.decorate(c);
			if (decorated)
				return c;
			decorated = true;
			if (theName != null)
				c.setName(theName);
			if (theMouseListener != null)
				c.addMouseListener(new MouseListener() {
					@Override
					public void mouseClicked(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mousePressed(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseReleased(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseEntered(MouseEvent e) {
						theMouseListener.accept(e);
					}

					@Override
					public void mouseExited(MouseEvent e) {
						theMouseListener.accept(e);
					}
				});
			if (theComponentModifier != null)
				theComponentModifier.accept(c);
			return c;
		}

		protected Component getOrCreateComponent(Observable<?> until) {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			if (!isTooltipHandled && theEditor instanceof JComponent)
				theTooltip.changes().takeUntil(until).act(evt -> ((JComponent) theEditor).setToolTipText(evt.getNewValue()));
			return decorate((Component) theEditor);
		}

		@Override
		public Component getComponent() {
			// Subclasses should override this if the editor is not a component or is not the component that should be added
			if (!(theEditor instanceof Component))
				throw new IllegalStateException("Editor is not a component");
			return (Component) theEditor;
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

		protected abstract Component createFieldNameLabel(Observable<?> until);

		protected abstract Component createPostLabel(Observable<?> until);
	}

	static class VizChanger implements Consumer<ObservableValueEvent<Boolean>> {
		private final Component theComponent;
		private final Component theFieldLabel;
		private boolean shouldBeVisible;

		public VizChanger(Component component, Component fieldLabel) {
			theComponent = component;
			theFieldLabel = fieldLabel;
		}

		@Override
		public void accept(ObservableValueEvent<Boolean> evt) {
			boolean visible = evt.getNewValue();
			shouldBeVisible = visible;
			if (EventQueue.isDispatchThread())
				setVisible(visible);
			else {
				EventQueue.invokeLater(() -> {
					if (shouldBeVisible == visible)
						setVisible(visible);
				});
			}
		}

		private void setVisible(boolean visible) {
			if (visible == theComponent.isVisible())
				return;
			if (theFieldLabel != null)
				theFieldLabel.setVisible(visible);
			theComponent.setVisible(visible);
			if (theComponent != null)
				theComponent.setVisible(visible);
			if (theComponent.getParent() != null)
				theComponent.getParent().revalidate();
		}
	}

	static class MigFieldPanel<C extends Container, P extends MigFieldPanel<C, P>> extends AbstractComponentEditor<C, P>
	implements PartialPanelPopulatorImpl<C, P> {
		private final Observable<?> theUntil;

		MigFieldPanel(C container, Observable<?> until, Supplier<Transactable> lock) {
			super(//
				container = (container != null ? container
					: (C) new ConformingPanel(PanelPopulation.createMigLayout(true, () -> "install the layout before using this class"))), //
				lock);
			theUntil = until == null ? Observable.empty() : until;
			if (container.getLayout() == null
				|| !PanelPopulation.MIG_LAYOUT_CLASS_NAME.equals(container.getLayout().getClass().getName())) {
				LayoutManager2 migLayout = PanelPopulation.createMigLayout(true, () -> "install the layout before using this class");
				container.setLayout(migLayout);
			}
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel, "align right");
			StringBuilder constraints = new StringBuilder();
			if (field.getLayoutConstraints() != null)
				constraints.append(field.getLayoutConstraints());
			if (field.isFill()) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("growx, pushx");
			} else if (fieldLabel == null && postLabel == null) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("align center");
			}
			if (field.isFillV()) {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("growy, pushy");
			}
			if (postLabel != null) {
				if (fieldLabel == null) {
					if (constraints.length() > 0)
						constraints.append(", ");
					constraints.append("span 2");
				}
			} else {
				if (constraints.length() > 0)
					constraints.append(", ");
				constraints.append("span, wrap");
			}
			Component component = field.getOrCreateComponent(getUntil());
			if (component == null)
				throw new IllegalStateException();
			if (scrolled)
				getContainer().add(new JScrollPane(component), constraints.toString());
			else
				getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel, "wrap");
			if (field.isVisible() != null)
				field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel));
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}
	}

	static class SettingsMenuImpl<C extends Container, P extends SettingsMenuImpl<C, P>> extends MigFieldPanel<C, P>
	implements SettingsMenu<C, P> {
		private ObservableValue<? extends Icon> theIcon;
		private JLabel theComponent;
		private final JPopupMenu thePopup;
		private final JLabel theMenuCloser;
		private boolean hasShown;

		SettingsMenuImpl(C container, Observable<?> until, Supplier<Transactable> lock) {
			super(container, until, lock);
			thePopup = new JPopupMenu();
			theIcon = ObservableValue.of(Icon.class, ObservableSwingUtils.getFixedIcon(null, "icons/gear.png", 20, 20));
			theMenuCloser = new JLabel();
			theMenuCloser.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
						close();
					}
				}
			});
		}

		@Override
		public P withIcon(ObservableValue<? extends Icon> icon) {
			theIcon = icon;
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (theComponent != null)
				return theComponent;

			theComponent = new JLabel();
			theIcon.changes().takeUntil(until).act(evt -> {
				theComponent.setIcon(evt.getNewValue());
				theMenuCloser.setIcon(evt.getNewValue());
			});
			if (getTooltip() != null) {
				getTooltip().changes().takeUntil(until).act(evt -> {
					theComponent.setToolTipText(evt.getNewValue());
					theMenuCloser.setToolTipText(evt.getNewValue());
				});
			}
			decorate(theComponent);
			decorate(theMenuCloser);

			thePopup.setLayout(new BorderLayout());
			JPanel topPanel = new JPanel(new JustifiedBoxLayout(false).mainTrailing());
			topPanel.add(theMenuCloser);
			thePopup.add(topPanel, BorderLayout.NORTH);
			thePopup.add(getContainer(), BorderLayout.CENTER);
			thePopup.setBorder(BorderFactory.createLineBorder(Color.black, 2));
			theComponent.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
						open();
					}
				}
			});
			return theComponent;
		}

		/** Opens the menu. Normally called internally. */
		public void open() {
			Runnable action = () -> {
				Point gearRelPopup = getGearRelPopup();

				thePopup.show(theComponent, -gearRelPopup.x, -gearRelPopup.y);

				if (!hasShown) {
					gearRelPopup = getGearRelPopup();
					Point screenLoc = theComponent.getLocationOnScreen();
					thePopup.setLocation(screenLoc.x - gearRelPopup.x, screenLoc.y - gearRelPopup.y);
					hasShown = true;
				}
			};
			if (EventQueue.isDispatchThread()) {
				action.run();
			} else {
				EventQueue.invokeLater(action);
			}
		}

		private Point getGearRelPopup() {
			Point gearRelPopup = theMenuCloser.getLocation();
			Component c = theMenuCloser.getParent();
			while (c != thePopup) {
				gearRelPopup.x += c.getLocation().x;
				gearRelPopup.y += c.getLocation().y;
				c = c.getParent();
			}
			return gearRelPopup;
		}

		/** Closes the menu. Normally called internally. */
		public void close() {
			Runnable action = () -> {
				thePopup.setVisible(false);
			};
			if (EventQueue.isDispatchThread()) {
				action.run();
			} else {
				EventQueue.invokeLater(action);
			}
		}
	}

	static class SimpleFieldEditor<E, P extends SimpleFieldEditor<E, P>> extends AbstractComponentEditor<E, P>
	implements FieldEditor<E, P> {
		private ObservableValue<String> theFieldName;
		private Consumer<FontAdjuster<?>> theFieldLabelModifier;
		private ObservableValue<String> thePostLabel;
		private SimpleButtonEditor<JButton, ?> thePostButton;
		private Consumer<FontAdjuster<?>> theFont;

		SimpleFieldEditor(String fieldName, E editor, Supplier<Transactable> lock) {
			super(editor, lock);
			theFieldName = fieldName == null ? null : ObservableValue.of(fieldName);
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			theFieldName = fieldName;
			return (P) this;
		}

		@Override
		public P withPostLabel(ObservableValue<String> postLabel) {
			if (thePostButton != null) {
				System.err.println("A field can only have one post component");
				thePostButton = null;
			}
			thePostLabel = postLabel;
			return (P) this;
		}

		@Override
		public P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
			if (thePostLabel != null) {
				System.err.println("A field can only have one post component");
				thePostLabel = null;
			}
			thePostButton = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, getLock(), true);
			if (modify != null)
				modify.accept(thePostButton);
			return (P) this;
		}

		@Override
		public P modifyFieldLabel(Consumer<FontAdjuster<?>> labelModifier) {
			if (theFieldLabelModifier == null)
				theFieldLabelModifier = labelModifier;
			else {
				Consumer<FontAdjuster<?>> prev = theFieldLabelModifier;
				theFieldLabelModifier = f -> {
					prev.accept(f);
					labelModifier.accept(f);
				};
			}
			return (P) this;
		}

		@Override
		public P withFont(Consumer<FontAdjuster<?>> font) {
			if (theFont == null)
				theFont = font;
			else {
				Consumer<FontAdjuster<?>> prev = theFont;
				theFont = f -> {
					prev.accept(f);
					font.accept(f);
				};
			}
			return (P) this;
		}

		protected <C extends JComponent> C onFieldName(C fieldNameComponent, Consumer<String> fieldName, Observable<?> until) {
			if (theFieldName == null)
				return null;
			theFieldName.changes().takeUntil(until).act(evt -> fieldName.accept(evt.getNewValue()));
			if (fieldNameComponent != null && theFieldLabelModifier != null)
				theFieldLabelModifier.accept(new FontAdjuster<>(fieldNameComponent));
			if (fieldNameComponent != null && theFont != null)
				theFont.accept(new FontAdjuster<>(fieldNameComponent));
			return fieldNameComponent;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			if (theFieldName == null)
				return null;
			JLabel fieldNameLabel = new JLabel(theFieldName.get());
			return onFieldName(fieldNameLabel, fieldNameLabel::setText, until);
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			if (thePostLabel != null) {
				JLabel postLabel = new JLabel(thePostLabel.get());
				thePostLabel.changes().takeUntil(until).act(evt -> postLabel.setText(evt.getNewValue()));
				if (theFont != null)
					theFont.accept(new FontAdjuster<>(postLabel));
				return postLabel;
			} else if (thePostButton != null)
				return thePostButton.getOrCreateComponent(until);
			else
				return null;
		}

		@Override
		protected Component decorate(Component c) {
			if (theFont != null)
				theFont.accept(new FontAdjuster<>(c));
			return super.decorate(c);
		}
	}

	static class SimpleHPanel<C extends Container> extends SimpleFieldEditor<C, SimpleHPanel<C>>
	implements PartialPanelPopulatorImpl<C, SimpleHPanel<C>> {
		private final Observable<?> theUntil;

		SimpleHPanel(String fieldName, C editor, Supplier<Transactable> lock, Observable<?> until) {
			super(fieldName, editor, lock);
			theUntil = until;
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public Supplier<Transactable> getLock() {
			return theLock;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel);
			Component component = field.getOrCreateComponent(getUntil());
			Object constraints;
			if ((field.isFill() || field.isFillV()) && getContainer().getLayout().getClass().getName().startsWith("net.mig")) {
				StringBuilder constraintsStr = new StringBuilder();
				if (field.isFill()) {
					if (constraintsStr.length() > 0)
						constraintsStr.append(", ");
					constraintsStr.append("growx, pushx");
				}
				if (field.isFillV()) {
					if (constraintsStr.length() > 0)
						constraintsStr.append(", ");
					constraintsStr.append("growy, pushy");
				}
				constraints = constraintsStr.toString();
			} else
				constraints = field.getLayoutConstraints();
			if (scrolled)
				getContainer().add(new JScrollPane(component), constraints);
			else
				getContainer().add(component, constraints);
			if (postLabel != null)
				getContainer().add(postLabel);
			if (field.isVisible() != null) {
				if (field.isVisible() != null)
					field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel));
			}
		}

		@Override
		public SimpleHPanel<C> addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getLock());
			fieldPanel.getEditor().setHorizontalTextPosition(SwingConstants.LEADING);
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.onFieldName(fieldPanel.getEditor(), name -> fieldPanel.getEditor().setText(name), getUntil());
			doAdd(fieldPanel, null, fieldPanel.createPostLabel(theUntil), false);
			return this;
		}
	}

	static class SimpleImageControl implements ImageControl {
		private final SettableValue<ObservableValue<? extends Icon>> theSettableIcon;
		private final ObservableValue<Icon> theIcon;
		private final SimpleObservable<Void> theTweakObservable;
		private int theWidth;
		private int theHeight;
		private double theOpacity;

		public SimpleImageControl(String location) {
			theTweakObservable = new SimpleObservable<>();
			theSettableIcon = SettableValue.build((Class<ObservableValue<? extends Icon>>) (Class<?>) ObservableValue.class).build();
			setLocation(location);
			theIcon = ObservableValue.flatten(theSettableIcon).refresh(theTweakObservable);
			theWidth = -1;
			theHeight = -1;
			theOpacity = 1;
		}

		ImageIcon getIcon(String location) {
			if (location == null)
				return null;
			ImageIcon icon = ObservableSwingUtils.getIcon(getClass(), location);
			return adjustIcon(icon);
		}

		ImageIcon adjustIcon(ImageIcon icon) {
			if (icon != null) {
				if ((theWidth >= 0 && icon.getIconWidth() != theWidth) || (theHeight >= 0 && icon.getIconHeight() != theHeight)) {
					icon = new ImageIcon(icon.getImage().getScaledInstance(//
						theWidth >= 0 ? theWidth : icon.getIconWidth(), //
							theHeight >= 0 ? theHeight : icon.getIconHeight(), //
								Image.SCALE_SMOOTH));
				}
			}
			return icon;
		}

		ObservableValue<Icon> getIcon() {
			return theIcon;
		}

		@Override
		public ImageControl setLocation(String location) {
			return variableLocation(ObservableValue.of(TypeTokens.get().STRING, location));
		}

		@Override
		public ImageControl variableLocation(ObservableValue<String> location) {
			return variable(location.map(this::getIcon));
		}

		@Override
		public ImageControl variable(ObservableValue<? extends Icon> icon) {
			theSettableIcon.set(icon, null);
			return this;
		}

		@Override
		public ImageControl withSize(int width, int height) {
			theWidth = width;
			theHeight = height;
			theTweakObservable.onNext(null);
			return this;
		}

		@Override
		public ImageControl withOpacity(double opacity) {
			if (opacity < 0 || opacity >= 1)
				throw new IllegalArgumentException("Opacity must be between 0 and 1, not " + opacity);
			theOpacity = opacity;
			theTweakObservable.onNext(null);
			return this;
		}
	}

	static class SimpleButtonEditor<B extends AbstractButton, P extends SimpleButtonEditor<B, P>> extends SimpleFieldEditor<B, P>
	implements ButtonEditor<B, P> {
		private final ObservableAction<?> theAction;
		private ObservableValue<String> theText;
		private ObservableValue<? extends Icon> theIcon;
		private ObservableValue<String> theDisablement;
		private final boolean isPostButton;

		SimpleButtonEditor(String fieldName, B button, String buttonText, ObservableAction<?> action, Supplier<Transactable> lock,
			boolean postButton) {
			super(fieldName, button, lock);
			theAction = action;
			theText = ObservableValue.of(TypeTokens.get().STRING, buttonText);
			isPostButton = postButton;
		}

		public ObservableValue<String> getText() {
			return theText;
		}

		public ObservableValue<? extends Icon> getIcon() {
			return theIcon;
		}

		@Override
		public P withText(ObservableValue<String> text) {
			theText = text;
			return (P) this;
		}

		@Override
		public P withIcon(ObservableValue<? extends Icon> icon) {
			theIcon = icon;
			if (icon != null)
				getEditor().setMargin(new Insets(2, 2, 2, 2));
			return (P) this;
		}

		@Override
		public P disableWith(ObservableValue<String> disabled) {
			if (theDisablement == null || disabled == null)
				theDisablement = disabled;
			else {
				ObservableValue<String> old = theDisablement;
				theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, old, disabled);
			}
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			getEditor().addActionListener(evt -> theAction.act(evt));
			ObservableValue<String> enabled;
			if (theDisablement != null) {
				enabled = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement,
					theAction.isEnabled());
			} else
				enabled = theAction.isEnabled();
			enabled.combine((e, tt) -> e == null ? tt : e, getTooltip()).changes().takeUntil(until)
			.act(evt -> getEditor().setToolTipText(evt.getNewValue()));
			enabled.takeUntil(until).changes().act(evt -> getEditor().setEnabled(evt.getNewValue() == null));
			if (theText != null)
				theText.changes().takeUntil(until).act(evt -> getEditor().setText(evt.getNewValue()));
			if (theIcon != null)
				theIcon.changes().takeUntil(until).act(evt -> getEditor().setIcon(evt.getNewValue()));
			return super.getOrCreateComponent(until);
		}

		@Override
		public P withFieldName(ObservableValue<String> fieldName) {
			if (isPostButton)
				throw new IllegalStateException("No field name for a post-button");
			return super.withFieldName(fieldName);
		}

		@Override
		public P withPostLabel(ObservableValue<String> postLabel) {
			if (isPostButton)
				throw new IllegalStateException("No post label for a post-button");
			return super.withPostLabel(postLabel);
		}

		@Override
		public P modifyFieldLabel(Consumer<FontAdjuster<?>> labelModifier) {
			if (isPostButton)
				throw new IllegalStateException("No field label for a post-button");
			return super.modifyFieldLabel(labelModifier);
		}
	}

	static class SimpleSteppedFieldEditor<E, F, P extends SimpleSteppedFieldEditor<E, F, P>> extends SimpleFieldEditor<E, P>
	implements SteppedFieldEditor<E, F, P> {
		private final Consumer<F> theStepSizeChange;

		SimpleSteppedFieldEditor(String fieldName, E editor, Supplier<Transactable> lock, Consumer<F> stepSizeChange) {
			super(fieldName, editor, lock);
			theStepSizeChange = stepSizeChange;
		}

		@Override
		public P withStepSize(F stepSize) {
			theStepSizeChange.accept(stepSize);
			return (P) this;
		}
	}

	static class SimpleMultiSliderEditor<P extends SimpleMultiSliderEditor<P>> extends SimpleFieldEditor<JXMultiThumbSlider<Double>, P>
	implements SliderEditor<JXMultiThumbSlider<Double>, P> {
		private final ObservableCollection<Double> theValues;
		private ObservableValue<Double> theMinValue;
		private ObservableValue<Double> theMaxValue;
		private boolean isAdjustingBoundsForValue;
		private boolean isCreated;

		public SimpleMultiSliderEditor(String fieldName, ObservableCollection<Double> values, Supplier<Transactable> lock) {
			super(fieldName, new JXMultiThumbSlider<>(), lock);
			theValues = values;
			theMinValue = SettableValue.build(double.class).withValue(0.0).build();
			theMaxValue = SettableValue.build(double.class).withValue(1.0).build();
		}

		@Override
		public P withMinimum(ObservableValue<Double> min) {
			theMinValue = min;
			return (P) this;
		}

		@Override
		public P withMaximum(ObservableValue<Double> max) {
			theMaxValue = max;
			return (P) this;
		}

		@Override
		public P adjustBoundsForValue(boolean adjustForValue) {
			isAdjustingBoundsForValue = adjustForValue;
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (!isCreated) {
				MultiThumbModel<Double> model = getEditor().getModel();
				for (int i = model.getThumbCount() - 1; i >= 0; i--)
					model.removeThumb(i);
				ObservableCollection<Double> safeValues = theValues.safe(ThreadConstraint.EDT, until);
				// The JXMultiThumbSlider doesn't actually handle minimum value correctly,
				// so we have to set it to zero and offset all the values by it.
				theMinValue.safe(ThreadConstraint.EDT, until).changes().takeUntil(until).act(evt -> {
					model.setMaximumValue(theMaxValue.get().floatValue() - evt.getNewValue().floatValue());
					if (model.getThumbCount() > 0) {
						for (int i = 0; i < safeValues.size(); i++)
							model.getThumbAt(0).setPosition(safeValues.get(i).floatValue() - theMinValue.get().floatValue());
					}
				});
				theMaxValue.safe(ThreadConstraint.EDT, until).changes().takeUntil(until).act(evt -> {
					model.setMaximumValue(evt.getNewValue().floatValue() - theMinValue.get().floatValue());
				});
				JPanel trackRenderer = new JPanel() {
					@Override
					public void paint(Graphics g) {
						Dimension sz = getEditor().getSize();
						g.setColor(Color.black);
						g.fillRect(0, 4, sz.width, 1);
						g.fillRect(0, 1, 1, sz.height - 8);
						g.fillRect(sz.width - 1, 1, 1, sz.height - 8);
					}
				};
				boolean[] thumbSelected = new boolean[1];
				boolean[] thumbEnabled = new boolean[1];
				JPanel thumbRenderer = new JPanel() {
					@Override
					public void paint(Graphics g) {
						if (!thumbEnabled[0])
							g.setColor(Color.lightGray);
						else if (thumbSelected[0])
							g.setColor(Colors.darkSeaGreen);
						else
							g.setColor(Colors.antiqueWhite);
						g.fillOval(getWidth() / 4, 0, getWidth() / 2, getHeight() - 1);
						g.setColor(Colors.darkBlue);
						g.drawOval(getWidth() / 4, 0, getWidth() / 2, getHeight() - 1);
					}
				};
				getEditor().setTrackRenderer(slider -> trackRenderer);
				getEditor().setThumbRenderer((slider, index, selected) -> {
					String enabled = safeValues.mutableElement(safeValues.getElement(index).getElementId()).isEnabled();
					thumbEnabled[0] = enabled == null;
					thumbSelected[0] = selected;
					if (selected)
						slider.setToolTipText(enabled);
					return thumbRenderer;
				});
				boolean[] callbackLock = new boolean[1];
				Subscription sub = safeValues.subscribe(evt -> {
					if (callbackLock[0])
						return;
					callbackLock[0] = true;
					try {
						switch (evt.getType()) {
						case add:
							double newPos = bound(evt.getNewValue(), evt);
							model.insertThumb((float) newPos, evt.getNewValue(), evt.getIndex());
							break;
						case remove:
							model.removeThumb(evt.getIndex());
							break;
						case set:
							newPos = bound(evt.getNewValue(), evt);
							model.getThumbAt(evt.getIndex()).setPosition((float) newPos);
							break;
						}
					} finally {
						callbackLock[0] = false;
					}
				}, true);
				ThumbDataListener listener = new ThumbDataListener() {
					private int theThumbIndex = -1;

					@Override
					public void positionChanged(ThumbDataEvent event) {
						if (callbackLock[0])
							return;
						MutableCollectionElement<Double> element = safeValues
							.mutableElement(safeValues.getElement(event.getIndex()).getElementId());
						if (element.isAcceptable(theMinValue.get() + event.getThumb().getPosition()) == null) {
							if (theThumbIndex >= 0 && theThumbIndex != event.getIndex())
								flush();
							theThumbIndex = event.getIndex();
							QommonsTimer.getCommonInstance().doAfterInactivity(this, this::flush, Duration.ofMillis(100)).onEDT();
						} else {
							callbackLock[0] = true;
							try {
								model.getThumbAt(event.getIndex()).setPosition(element.get().floatValue() - theMinValue.get().floatValue());
							} finally {
								callbackLock[0] = false;
							}
						}
					}

					void flush() {
						callbackLock[0] = true;
						try (Transaction t = safeValues.lock(true, null)) {
							MutableCollectionElement<Double> element = safeValues
								.mutableElement(safeValues.getElement(theThumbIndex).getElementId());
							double pos = model.getThumbAt(theThumbIndex).getPosition() + theMinValue.get();
							if (element.isAcceptable(pos) == null)
								element.set(pos);
							else
								model.getThumbAt(theThumbIndex).setPosition(element.get().floatValue() - theMinValue.get().floatValue());
							theThumbIndex = -1;
						} finally {
							callbackLock[0] = false;
						}
					}

					@Override
					public void thumbAdded(ThumbDataEvent event) {
					}

					@Override
					public void thumbRemoved(ThumbDataEvent event) {
					}

					@Override
					public void valueChanged(ThumbDataEvent event) {
					}
				};
				model.addThumbDataListener(listener);
				until.take(1).act(__ -> {
					sub.unsubscribe();
					model.removeThumbDataListener(listener);
				});
			}
			return super.getOrCreateComponent(until);
		}

		private double bound(double value, Object cause) {
			if (value < theMinValue.get()) {
				if (isAdjustingBoundsForValue && theMinValue instanceof SettableValue
					&& ((SettableValue<Double>) theMinValue).isAcceptable(value) == null) {
					((SettableValue<Double>) theMinValue).set(value, cause);
				} else
					value = theMinValue.get();
			} else if (value > theMaxValue.get()) {
				if (isAdjustingBoundsForValue && theMaxValue instanceof SettableValue
					&& ((SettableValue<Double>) theMaxValue).isAcceptable(value) == null) {
					((SettableValue<Double>) theMaxValue).set(value, cause);
				} else
					value = theMaxValue.get();
			}
			return value - theMinValue.get();
		}
	}

	static class SimpleComboEditor<F, P extends SimpleComboEditor<F, P>> extends SimpleFieldEditor<JComboBox<F>, P>
	implements ComboEditor<F, P> {
		private Function<? super F, String> theValueTooltip;

		SimpleComboEditor(String fieldName, JComboBox<F> editor, Supplier<Transactable> lock) {
			super(fieldName, editor, lock);
		}

		@Override
		public P renderWith(ObservableCellRenderer<F, F> renderer) {
			getEditor().setRenderer(renderer);
			return (P) this;
		}

		@Override
		public P withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		@Override
		public String getTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}
	}

	static class SimpleToggleButtonPanel<F, TB extends JToggleButton, P extends SimpleToggleButtonPanel<F, TB, P>>
	extends SimpleFieldEditor<Map<F, TB>, P> implements ToggleEditor<F, TB, P> {
		private final JPanel thePanel;
		private final ObservableCollection<? extends F> theValues;
		private final SettableValue<F> theValue;
		private final Class<TB> theButtonType;
		private final Function<? super F, ? extends TB> theButtonCreator;
		private ObservableCollection<TB> theButtons;

		private BiConsumer<? super TB, ? super F> theRenderer;
		private Function<? super F, String> theValueTooltip;

		SimpleToggleButtonPanel(String fieldName, ObservableCollection<? extends F> values, SettableValue<F> value, Class<TB> buttonType,
			Function<? super F, ? extends TB> buttonCreator, Supplier<Transactable> lock) {
			super(fieldName, new HashMap<>(), lock);
			thePanel = new ConformingPanel(
				new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING).crossJustified());

			theValue = value;
			theValues = values;

			theButtonType = buttonType;
			theButtonCreator = buttonCreator;
			theRenderer = (button, v) -> button.setText(String.valueOf(v));
		}

		@Override
		public P render(BiConsumer<? super TB, ? super F> renderer) {
			theRenderer = renderer;
			return (P) this;
		}

		@Override
		public P withValueTooltip(Function<? super F, String> tooltip) {
			theValueTooltip = tooltip;
			return (P) this;
		}

		void render(TB button, F value) {
			theRenderer.accept(button, value);
		}

		String getValueTooltip(F value) {
			return theValueTooltip == null ? null : theValueTooltip.apply(value);
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (theButtons == null) {
				ObservableCollection<? extends F> safeValues = theValues.safe(ThreadConstraint.EDT, until);
				ObservableCollection<TB>[] _buttons = new ObservableCollection[1];
				Subscription valueSub = ObservableSwingUtils.togglesFor(safeValues, theValue, TypeTokens.get().of(theButtonType),
					theButtonCreator, b -> _buttons[0] = b, this::render, this::getValueTooltip);
				theButtons = _buttons[0];
				for (JToggleButton button : theButtons) {
					thePanel.add(button);
				}
				theButtons.changes().takeUntil(until).act(evt -> {
					for (CollectionChangeEvent.ElementChange<TB> change : evt.getElements()) {
						switch (evt.type) {
						case add:
							thePanel.add(change.newValue, change.index);
							break;
						case remove:
							thePanel.remove(change.index);
							break;
						case set:
							break; // Re-rendering is handled by the togglesFor method
						}
					}
				});
				if (until != null)
					until.take(1).act(__ -> valueSub.unsubscribe());
			}
			return decorate(thePanel);
		}

		@Override
		public Component getComponent() {
			return decorate(thePanel);
		}
	}

	static class SimpleProgressEditor<P extends SimpleProgressEditor<P>> extends SimpleFieldEditor<JProgressBar, P>
	implements ProgressEditor<P> {
		private ObservableValue<Integer> theTaskLength;
		private ObservableValue<Integer> theProgress;
		private ObservableValue<Boolean> isIndeterminate;
		private ObservableValue<String> theText;
		private boolean isInitialized;

		public SimpleProgressEditor(String fieldName, Supplier<Transactable> lock) {
			super(fieldName, new JProgressBar(JProgressBar.HORIZONTAL), lock);
		}

		@Override
		public P withTaskLength(ObservableValue<Integer> length) {
			theTaskLength = length;
			return (P) this;
		}

		@Override
		public P withProgress(ObservableValue<Integer> progress) {
			theProgress = progress;
			if (theTaskLength == null)
				theTaskLength = ObservableValue.of(100); // Assume it's a percentage
			return (P) this;
		}

		ObservableValue<Integer> getProgress() {
			return theProgress;
		}

		@Override
		public P indeterminate(ObservableValue<Boolean> indeterminate) {
			isIndeterminate = indeterminate;
			return (P) this;
		}

		@Override
		public P indeterminate() {
			if (theProgress == null)
				withProgress(ObservableValue.of(0));
			return ProgressEditor.super.indeterminate();
		}

		@Override
		public P withProgressText(ObservableValue<String> text) {
			theText = text;
			return (P) this;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (!isInitialized) {
				isInitialized = true;
				if (theProgress == null)
					throw new IllegalStateException("No progress value set");
				Observable.or(theTaskLength.noInitChanges(), theProgress.noInitChanges(), //
					isIndeterminate == null ? Observable.empty() : isIndeterminate.noInitChanges(), //
						theText == null ? Observable.empty() : theText.noInitChanges())//
				.takeUntil(until)//
				.act(__ -> updateProgress());
				updateProgress();
			}
			return super.getOrCreateComponent(until);
		}

		void updateProgress() {
			if ((isIndeterminate != null && Boolean.TRUE.equals(isIndeterminate.get()))//
				|| theTaskLength.get() == null || theTaskLength.get() <= 0//
				|| theProgress.get() == null || theProgress.get() < 0)
				getEditor().setIndeterminate(true);
			else {
				getEditor().setMaximum(theTaskLength.get());
				getEditor().setValue(theProgress.get());
				getEditor().setIndeterminate(false);
			}
			String text = theText == null ? null : theText.get();
			getEditor().setStringPainted(text != null);
			if (text != null)
				getEditor().setString(text);
		}
	}

	static class SimpleTabPaneEditor<P extends SimpleTabPaneEditor<P>> extends AbstractComponentEditor<JTabbedPane, P>
	implements TabPaneEditor<JTabbedPane, P> {
		static class Tab {
			final Object id;
			final SimpleTabEditor<?> tab;
			Component component;
			SimpleObservable<Void> tabEnd;
			boolean isRemovable;
			ObservableValue<String> theName;
			ObservableValue<Icon> theIcon;
			Observable<?> until;
			Consumer<Object> onRemove;

			Tab(Object id, SimpleTabEditor<?> tab) {
				this.id = id;
				this.tab = tab;
			}
		}

		private final Observable<?> theUntil;
		private final Map<Object, Tab> theTabs;
		private final Map<Component, Tab> theTabsByComponent;
		private final SettableValue<Object> theSelectedTabId;
		private List<Runnable> thePostCreateActions;
		private Tab theSelectedTab;

		SimpleTabPaneEditor(Supplier<Transactable> lock, Observable<?> until) {
			super(new JTabbedPane(), lock);
			theUntil = until;
			theTabs = new LinkedHashMap<>();
			theTabsByComponent = new IdentityHashMap<>();
			theSelectedTabId = SettableValue.build(Object.class).build();
			thePostCreateActions = new LinkedList<>();
		}

		@Override
		public int getTabCount() {
			return getEditor().getTabCount();
		}

		@Override
		public P withVTab(Object tabID, int tabIndex, Consumer<PanelPopulator<?, ?>> panel, Consumer<TabEditor<?>> tabModifier) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			panel.accept(fieldPanel);
			return withTabImpl(tabID, tabIndex, fieldPanel.getContainer(), tabModifier, fieldPanel);
		}

		@Override
		public P withHTab(Object tabID, int tabIndex, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel,
			Consumer<TabEditor<?>> tabModifier) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), theLock, theUntil);
			panel.accept(hPanel);
			return withTabImpl(tabID, tabIndex, hPanel.getContainer(), tabModifier, hPanel);
		}

		@Override
		public P withTab(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier) {
			return withTabImpl(tabID, tabIndex, tabComponent, tabModifier, null);
		}

		P withTabImpl(Object tabID, int tabIndex, Component tabComponent, Consumer<TabEditor<?>> tabModifier,
			AbstractComponentEditor<?, ?> panel) {
			if (tabID == null)
				throw new NullPointerException();
			SimpleTabEditor<?> t = new SimpleTabEditor<>(this, tabID, tabComponent);
			tabModifier.accept(t);
			Tab tab = new Tab(tabID, t);
			tab.onRemove = t.onRemove;
			Tab oldTab = theTabs.put(tabID, tab);
			if (oldTab != null) {
				oldTab.tabEnd.onNext(null);
				tab.tabEnd = oldTab.tabEnd;
				theTabsByComponent.remove(oldTab.component);
			} else
				tab.tabEnd = SimpleObservable.build().withIdentity(Identifiable.baseId("tab " + tabID, new BiTuple<>(this, tabID))).build();
			Observable<?> tabUntil = Observable.or(tab.tabEnd, theUntil);
			tab.until = tabUntil;
			tab.component = t.getComponent(tabUntil);
			if (theTabsByComponent.put(tab.component, tab) != null)
				throw new IllegalStateException("Duplicate tab components (" + tabID + ")");
			if (panel != null && panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(tab.component, null));
			if (oldTab != null) {
				for (int i = 0; i < getEditor().getTabCount(); i++) {
					if (getEditor().getComponentAt(i) == oldTab.component) {
						if (getEditor().getSelectedIndex() == i && tab.tab.getOnSelect() != null)
							tab.tab.getOnSelect().set(true, null);
						getEditor().setComponentAt(i, tab.component);
						break;
					}
				}
			} else
				getEditor().add(tab.component, tabIndex);
			tab.theName = t.getName();
			if (tab.theName != null)
				t.theName.changes().takeUntil(tabUntil).act(evt -> {
					getEditor().setTitleAt(getTabIndex(tabID), evt.getNewValue());
				});
			tab.theIcon = t.getIcon();
			if (tab.theIcon != null)
				t.theIcon.changes().takeUntil(tabUntil).act(evt -> {
					getEditor().setIconAt(getTabIndex(tabID), evt.getNewValue());
				});
			if (t.getSelection() != null) {
				t.getSelection().takeUntil(tabUntil).act(__ -> ObservableSwingUtils.onEQ(() -> {
					getEditor().setSelectedComponent(tab.component);
				}));
			}
			setRemovable(tabID, t.isRemovable);
			return (P) this;
		}

		@Override
		public P withSelectedTab(SettableValue<?> tabID) {
			Runnable action = () -> {
				tabID.changes().takeUntil(theUntil).act(evt -> ObservableSwingUtils.onEQ(() -> {
					if ((evt.getNewValue() == null || theTabs.containsKey(evt.getNewValue()))//
						&& !Objects.equals(theSelectedTabId.get(), evt.getNewValue())) {
						theSelectedTabId.set(evt.getNewValue(), evt.isTerminated() ? null : evt);
					}
				}));
				theSelectedTabId.noInitChanges().takeUntil(theUntil).act(evt -> {
					if (!Objects.equals(evt.getNewValue(), tabID.get()))
						((SettableValue<Object>) tabID).set(evt.getNewValue(), evt);
				});
			};
			if (thePostCreateActions != null)
				thePostCreateActions.add(action);
			else
				action.run();
			return (P) this;
		}

		@Override
		public P onSelectedTab(Consumer<ObservableValue<Object>> tabID) {
			tabID.accept(theSelectedTabId.unsettable());
			return (P) this;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		private int getTabIndex(Object tabId) {
			int t = 0;
			for (Object tabId2 : theTabs.keySet()) {
				if (tabId2.equals(tabId))
					break;
				t++;
			}
			if (t == theTabs.size())
				return -1;
			return t;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			Component c = super.getOrCreateComponent(until);

			ObservableSwingUtils.onEQ(() -> {
				boolean[] initialized = new boolean[1];
				ChangeListener tabListener = evt -> {
					if (!initialized[0])
						return;
					Component selected = getEditor().getSelectedComponent();
					Tab selectedTab = selected == null ? null : theTabsByComponent.get(selected);
					if (selectedTab != theSelectedTab) {
						if (theSelectedTab != null && theSelectedTab.tab.getOnSelect() != null)
							theSelectedTab.tab.getOnSelect().set(false, evt);
						theSelectedTab = selectedTab;
						Object selectedTabId = selectedTab == null ? null : selectedTab.id;
						if (!Objects.equals(theSelectedTabId.get(), selectedTabId))
							theSelectedTabId.set(selectedTabId, evt);
						if (selectedTab != null && selectedTab.tab.getOnSelect() != null)
							selectedTab.tab.getOnSelect().set(true, evt);
					}
				};
				if (getEditor().getTabCount() > 0)
					getEditor().setSelectedIndex(0);
				initialized[0] = true;
				theSelectedTabId.changes().takeUntil(until).act(evt -> {
					Object tabID = evt.getNewValue();
					if (evt.getNewValue() == null) {
						if (theSelectedTab == null)
							return;
						getEditor().setSelectedIndex(-1);
					} else {
						Tab tab = theTabs.get(evt.getNewValue());
						if (theSelectedTab == tab)
							return;
						getEditor().setSelectedComponent(tab.component);
					}
				});
				Component selected = getEditor().getSelectedComponent();
				theSelectedTab = selected == null ? null : theTabsByComponent.get(selected);
				getEditor().addChangeListener(tabListener);
				until.take(1).act(__ -> getEditor().removeChangeListener(tabListener));
				List<Runnable> pcas = thePostCreateActions;
				thePostCreateActions = null;
				for (Runnable action : pcas)
					action.run();
			});
			return c;
		}

		boolean remove(Object tabId, Object cause) {
			Tab found = theTabs.remove(tabId);
			if (found == null)
				return false;
			getEditor().remove(found.component);
			found.tabEnd.onNext(null);
			if (found.onRemove != null)
				found.onRemove.accept(cause);
			return true;
		}

		boolean select(Object tabId) {
			Tab found = theTabs.get(tabId);
			if (found == null)
				return false;
			getEditor().setSelectedComponent(found.component);
			return true;
		}

		void setRemovable(Object tabId, boolean removable) {
			Tab found = theTabs.get(tabId);
			if (found == null)
				return;
			if (found.isRemovable == removable)
				return;
			found.isRemovable = removable;
			int t = getTabIndex(tabId);
			if (t < 0)
				return; // Maybe removed already
			if (getEditor().getTabCount() <= t)
				return; // ??
			JComponent tabC = (JComponent) getEditor().getTabComponentAt(t);
			if (removable) {
				if (tabC == null) {
					tabC = new ConformingPanel(new JustifiedBoxLayout(false));
					JLabel title = new JLabel(getEditor().getTitleAt(t));
					if (found.theName != null)
						found.theName.changes().takeUntil(found.until).act(evt -> {
							title.setText(evt.getNewValue());
						});
					if (found.theIcon != null)
						found.theIcon.changes().takeUntil(found.until).act(evt -> {
							title.setIcon(evt.getNewValue());
						});
					tabC.add(title);
					getEditor().setTabComponentAt(t, tabC);
				}
				JLabel removeLabel = new JLabel(ObservableSwingUtils.getFixedIcon(null, "/icons/redX.png", 8, 8));
				tabC.add(removeLabel);
				removeLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						remove(tabId, e);
					}
				});
			} else if (tabC != null) {
				for (int c = tabC.getComponentCount() - 1; c >= 0; c--) {
					if (tabC instanceof JLabel) {
						tabC.remove(c);
						break;
					}
				}
			}
		}
	}

	static class SimpleTabEditor<P extends SimpleTabEditor<P>> implements TabEditor<P> {
		private final SimpleTabPaneEditor<?> theTabs;
		@SuppressWarnings("unused")
		private final Object theID;
		private final Component theComponent;
		private ObservableValue<String> theName;
		private ObservableValue<Icon> theIcon;
		private Observable<?> theSelection;
		private SettableValue<Boolean> theOnSelect;
		private boolean isRemovable;
		private Consumer<Object> onRemove;

		public SimpleTabEditor(SimpleTabPaneEditor<?> tabs, Object id, Component component) {
			theTabs = tabs;
			theID = id;
			theComponent = component;
		}

		@Override
		public P setName(ObservableValue<String> name) {
			theName = name;
			return (P) this;
		}

		@Override
		public ObservableValue<String> getName() {
			return theName;
		}

		@Override
		public P setIcon(ObservableValue<Icon> icon) {
			theIcon = icon;
			return (P) this;
		}

		@Override
		public ObservableValue<Icon> getIcon() {
			return theIcon;
		}

		@Override
		public P selectOn(Observable<?> select) {
			if (theSelection == null)
				theSelection = select;
			else
				theSelection = Observable.or(theSelection, select);
			return (P) this;
		}

		@Override
		public P onSelect(Consumer<ObservableValue<Boolean>> onSelect) {
			if (theOnSelect == null)
				theOnSelect = SettableValue.build(boolean.class).nullable(false).withValue(false).build();
			onSelect.accept(theOnSelect);
			return (P) this;
		}

		@Override
		public P remove() {
			theTabs.remove(theID, null);
			return (P) this;
		}

		@Override
		public P select() {
			theTabs.select(theID);
			return (P) this;
		}

		@Override
		public P setRemovable(boolean removable) {
			isRemovable = removable;
			theTabs.setRemovable(theID, removable);
			return (P) this;
		}

		@Override
		public P onRemove(Consumer<Object> onRemove) {
			this.onRemove = onRemove;
			return (P) this;
		}

		protected Component getComponent(Observable<?> until) {
			if (theName == null)
				throw new IllegalArgumentException("Failed to set name on tab for " + theComponent);
			theName.changes().takeUntil(until).act(evt -> theComponent.setName(evt.getNewValue()));
			return theComponent;
		}

		Observable<?> getSelection() {
			return theSelection;
		}

		SettableValue<Boolean> getOnSelect() {
			return theOnSelect;
		}
	}

	static class SimpleSplitEditor<P extends SimpleSplitEditor<P>> extends AbstractComponentEditor<JSplitPane, P> implements SplitPane<P> {
		private final Observable<?> theUntil;
		private int theDivLocation = -1;
		private double theDivProportion = Double.NaN;
		private SettableValue<Integer> theObsDivLocation;
		private SettableValue<Double> theObsDivProportion;

		SimpleSplitEditor(boolean vertical, Supplier<Transactable> lock, Observable<?> until) {
			super(new JSplitPane(vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT), lock);
			theUntil = until;
		}

		boolean hasSetFirst;
		boolean hasSetLast;
		boolean initialized;

		@Override
		public P firstV(Consumer<PanelPopulator<?, ?>> vPanel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			vPanel.accept(fieldPanel);
			first(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P firstH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel> panel = new SimpleHPanel<>(null, new ConformingPanel(layout), theLock, theUntil);
			hPanel.accept(panel);
			first(panel.getOrCreateComponent(theUntil));
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(panel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P first(Component component) {
			if (hasSetFirst)
				throw new IllegalStateException("First component has already been configured");
			hasSetFirst = true;
			getEditor().setLeftComponent(component);
			return (P) this;
		}

		@Override
		public P lastV(Consumer<PanelPopulator<?, ?>> vPanel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			vPanel.accept(fieldPanel);
			last(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P lastH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel> panel = new SimpleHPanel<>(null, new ConformingPanel(layout), theLock, theUntil);
			hPanel.accept(panel);
			last(panel.getOrCreateComponent(theUntil));
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(panel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P last(Component component) {
			if (hasSetLast)
				throw new IllegalStateException("Last component has already been configured");
			hasSetLast = true;
			getEditor().setRightComponent(component);
			return (P) this;
		}

		@Override
		public P withSplitLocation(int split) {
			theDivLocation = split;
			return (P) this;
		}

		@Override
		public P withSplitProportion(double split) {
			theDivProportion = split;
			return (P) this;
		}

		@Override
		public P withSplitLocation(SettableValue<Integer> split) {
			if (theObsDivProportion != null)
				throw new IllegalStateException("Cannot set the div location and the div proportion both");
			theObsDivLocation = split;
			return (P) this;
		}

		@Override
		public P withSplitProportion(SettableValue<Double> split) {
			if (theObsDivLocation != null)
				throw new IllegalStateException("Cannot set the div location and the div proportion both");
			theObsDivProportion = split;
			return (P) this;
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			boolean init = !initialized;
			Component c = super.getOrCreateComponent(until);
			if (init) {
				initialized = true;
				SettableValue<Integer> divLoc = theObsDivLocation;
				SettableValue<Double> divProp = theObsDivProportion;
				if (divLoc != null || divProp != null) {
					boolean[] callbackLock = new boolean[1];
					ComponentAdapter visListener = new ComponentAdapter() {
						@Override
						public void componentShown(ComponentEvent e) {
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									if (divProp != null)
										getEditor().setDividerLocation(divProp.get());
									else
										getEditor().setDividerLocation(divLoc.get());
								} finally {
									callbackLock[0] = false;
								}
							});
						}

						@Override
						public void componentResized(ComponentEvent e) {
							if (divProp != null) {
								if (callbackLock[0])
									return;
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(divProp.get());
								} finally {
									callbackLock[0] = false;
								}
							}
						}
					};
					if (getEditor().getLeftComponent() != null) {
						getEditor().getLeftComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getRightComponent() == null || !getEditor().getRightComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									callbackLock[0] = true;
									try {
										if (divProp != null)
											getEditor().setDividerLocation(divProp.get());
										else
											getEditor().setDividerLocation(divLoc.get());
									} finally {
										callbackLock[0] = false;
									}
								});
							}
						});
					}
					if (getEditor().getRightComponent() != null) {
						getEditor().getRightComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getLeftComponent() == null || !getEditor().getLeftComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									callbackLock[0] = true;
									try {
										if (divProp != null)
											getEditor().setDividerLocation(divProp.get());
										else
											getEditor().setDividerLocation(divLoc.get());
									} finally {
										callbackLock[0] = false;
									}
								});
							}
						});
					}
					IntSupplier length = getEditor().getOrientation() == JSplitPane.VERTICAL_SPLIT ? getEditor()::getHeight
						: getEditor()::getWidth;
					getEditor().addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
						if (callbackLock[0])
							return;
						callbackLock[0] = true;
						try {
							if (divProp != null)
								divProp.set(getEditor().getDividerLocation() * 1.0 / length.getAsInt(), evt);
							else
								divLoc.set(getEditor().getDividerLocation(), evt);
						} finally {
							callbackLock[0] = false;
						}
					});
					if (divProp != null) {
						divProp.changes().takeUntil(until).act(evt -> {
							if (callbackLock[0])
								return;
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(evt.getNewValue());
								} finally {
									callbackLock[0] = false;
								}
							});
						});
					} else {
						divLoc.changes().takeUntil(until).act(evt -> {
							if (callbackLock[0])
								return;
							EventQueue.invokeLater(() -> {
								callbackLock[0] = true;
								try {
									getEditor().setDividerLocation(evt.getNewValue());
								} finally {
									callbackLock[0] = false;
								}
							});
						});
					}
				} else if (theDivLocation >= 0 || !Double.isNaN(theDivProportion)) {
					if (c.isVisible()) {
						EventQueue.invokeLater(() -> {
							if (theDivLocation >= 0)
								getEditor().setDividerLocation(theDivLocation);
							else
								getEditor().setDividerLocation(theDivProportion);
						});
					} else {
						ComponentAdapter[] visListener = new ComponentAdapter[1];
						visListener[0] = new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								c.removeComponentListener(visListener[0]);
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						};
						c.addComponentListener(visListener[0]);
					}
					if (getEditor().getLeftComponent() != null) {
						getEditor().getLeftComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getRightComponent() == null || !getEditor().getRightComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						});
					}
					if (getEditor().getRightComponent() != null) {
						getEditor().getRightComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getLeftComponent() == null || !getEditor().getLeftComponent().isVisible())
									return;
								EventQueue.invokeLater(() -> {
									if (theDivLocation >= 0)
										getEditor().setDividerLocation(theDivLocation);
									else
										getEditor().setDividerLocation(theDivProportion);
								});
							}
						});
					}
				}
			}
			return c;
		}
	}

	static class SimpleScrollEditor<P extends SimpleScrollEditor<P>> extends SimpleFieldEditor<JScrollPane, P> implements ScrollPane<P> {
		private final Observable<?> theUntil;

		private boolean isContentSet;

		public SimpleScrollEditor(String fieldName, Supplier<Transactable> lock, Observable<?> until) {
			super(fieldName, new JScrollPane(), lock);
			getEditor().getVerticalScrollBar().setUnitIncrement(10);
			getEditor().getHorizontalScrollBar().setUnitIncrement(10);
			theUntil = until;
		}

		@Override
		public P withVContent(Consumer<PanelPopulator<?, ?>> panel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, theUntil, theLock);
			panel.accept(fieldPanel);
			withContent(fieldPanel.getOrCreateComponent(theUntil));
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			SimpleHPanel<JPanel> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), theLock, theUntil);
			panel.accept(hPanel);
			withContent(hPanel.getOrCreateComponent(theUntil));
			if (hPanel.isVisible() != null)
				hPanel.isVisible().changes().takeUntil(theUntil).act(new VizChanger(hPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withContent(Component component) {
			if (isContentSet)
				throw new IllegalStateException("Content has already been configured");
			isContentSet = true;
			getEditor().add(component);
			return (P) this;
		}
	}

	private static class CollapsePaneOuterLayout extends JustifiedBoxLayout {
		CollapsePaneOuterLayout() {
			super(true);
			mainJustified().crossJustified();
		}

		@Override
		protected Dimension getMinSize(Component component) {
			if (component instanceof JXCollapsiblePane && !((JXCollapsiblePane) component).isCollapsed())
				return super.getMinSize(((JXCollapsiblePane) component).getContentPane());
			return super.getMinSize(component);
		}

		@Override
		protected Dimension getPrefSize(Component component) {
			if (component instanceof JXCollapsiblePane && !((JXCollapsiblePane) component).isCollapsed())
				return super.getPrefSize(((JXCollapsiblePane) component).getContentPane());
			return super.getPrefSize(component);
		}

		@Override
		protected Dimension getMaxSize(Component component) {
			if (component instanceof JXCollapsiblePane && !((JXCollapsiblePane) component).isCollapsed())
				return super.getMaxSize(((JXCollapsiblePane) component).getContentPane());
			return super.getMaxSize(component);
		}
	}

	static class SimpleCollapsePane extends AbstractComponentEditor<JXPanel, SimpleCollapsePane>
	implements PartialPanelPopulatorImpl<JXPanel, SimpleCollapsePane>, CollapsePanel<JXCollapsiblePane, JXPanel, SimpleCollapsePane> {
		private final JXCollapsiblePane theCollapsePane;
		private final PartialPanelPopulatorImpl<JPanel, ?> theOuterContainer;
		private final PartialPanelPopulatorImpl<JXPanel, ?> theContentPanel;
		private SimpleHPanel<JPanel> theHeaderPanel;
		private PanelPopulator<JPanel, ?> theExposedHeaderPanel;
		private final Observable<?> theUntil;
		private final SettableValue<Boolean> theInternalCollapsed;

		private Icon theCollapsedIcon;
		private Icon theExpandedIcon;

		private SettableValue<Boolean> isCollapsed;
		private boolean isInitialized;

		SimpleCollapsePane(JXCollapsiblePane cp, Observable<?> until, Supplier<Transactable> lock, boolean vertical, LayoutManager layout) {
			super((JXPanel) cp.getContentPane(), lock);
			theCollapsePane = cp;
			theCollapsePane.setLayout(layout);
			theUntil = until;
			if (vertical)
				theContentPanel = new MigFieldPanel(getEditor(), getUntil(), lock);
			else
				theContentPanel = new SimpleHPanel(null, getEditor(), lock, getUntil());
			theOuterContainer = new SimpleHPanel(null, new ConformingPanel(new CollapsePaneOuterLayout()), lock, until);
			theHeaderPanel = new SimpleHPanel(null, new ConformingPanel(new BorderLayout()), lock, until);

			theCollapsedIcon = ObservableSwingUtils.getFixedIcon(PanelPopulation.class, "/icons/circlePlus.png", 16, 16);
			theExpandedIcon = ObservableSwingUtils.getFixedIcon(PanelPopulation.class, "/icons/circleMinus.png", 16, 16);
			theInternalCollapsed = SettableValue.build(boolean.class).withValue(theCollapsePane.isCollapsed()).build();
			theInternalCollapsed.set(theCollapsePane.isCollapsed(), null);
			theCollapsePane.addPropertyChangeListener("collapsed", evt -> {
				boolean collapsed = Boolean.TRUE.equals(evt.getNewValue());
				theInternalCollapsed.set(collapsed, evt);
				if (collapsed)
					theCollapsePane.setVisible(false);
			});
			MouseListener collapseMouseListener = new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					theCollapsePane.setVisible(true);
					theCollapsePane.setCollapsed(!theCollapsePane.isCollapsed());
				}
			};
			theHeaderPanel.fill().addHPanel(null, new JustifiedBoxLayout(false), p -> p//
				.decorate(cd -> cd.bold().withFontSize(16))//
				.addIcon(null, theInternalCollapsed.map(collapsed -> collapsed ? theCollapsedIcon : theExpandedIcon), icon -> {
					icon.withTooltip(theInternalCollapsed.map(collapsed -> collapsed ? "Expand" : "Collapse"));
					icon.modifyComponent(c -> c.addMouseListener(collapseMouseListener));
				})//
				.spacer(2)//
				.withLayoutConstraints(BorderLayout.WEST))//
			.addHPanel(null, new JustifiedBoxLayout(false).mainJustified().crossJustified(),
				p -> theExposedHeaderPanel = p.withLayoutConstraints(BorderLayout.CENTER));
			theHeaderPanel.getComponent().addMouseListener(collapseMouseListener);
			decorate(deco -> deco.withBorder(BorderFactory.createLineBorder(Color.black)));
		}

		@Override
		public Component getComponent() {
			return getOrCreateComponent(theUntil);
		}

		@Override
		public SimpleCollapsePane withCollapsed(SettableValue<Boolean> collapsed) {
			isCollapsed = collapsed;
			return this;
		}

		@Override
		public SimpleCollapsePane animated(boolean animated) {
			theCollapsePane.setAnimated(animated);
			return this;
		}

		@Override
		public SimpleCollapsePane modifyCP(Consumer<JXCollapsiblePane> cp) {
			cp.accept(theCollapsePane);
			return this;
		}

		@Override
		public SimpleCollapsePane withHeader(Consumer<PanelPopulator<JPanel, ?>> header) {
			header.accept(theExposedHeaderPanel);
			return this;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			theContentPanel.doAdd(field, fieldLabel, postLabel, scrolled);
		}

		@Override
		public JXPanel getContainer() {
			return theContentPanel.getContainer();
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			if (!isInitialized) {
				isInitialized = true;
				theOuterContainer.doAdd(theHeaderPanel, null, null, false);
				theOuterContainer.addComponent(null, theCollapsePane, c -> c.fill());

				SettableValue<Boolean> collapsed = isCollapsed;
				if (collapsed != null) {
					boolean[] feedback = new boolean[1];
					collapsed.changes().act(evt -> {
						if (feedback[0])
							return;
						feedback[0] = true;
						try {
							theCollapsePane.setCollapsed(Boolean.TRUE.equals(evt.getNewValue()));
						} finally {
							feedback[0] = false;
						}
					});
					theCollapsePane.addPropertyChangeListener("collapsed", new PropertyChangeListener() {
						@Override
						public void propertyChange(PropertyChangeEvent evt) {
							if (feedback[0])
								return;
							feedback[0] = true;
							try {
								collapsed.set(Boolean.TRUE.equals(evt.getNewValue()), evt);
							} finally {
								feedback[0] = false;
							}
						}
					});
				}
				theContentPanel.getComponent(); // Initialization
			}
			decorate(theOuterContainer.getComponent());
			return theOuterContainer.getComponent();
		}

		@Override
		public ObservableValue<String> getTooltip() {
			return null;
		}

		@Override
		protected Component createFieldNameLabel(Observable<?> until) {
			return null;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			return null;
		}
	}

	static Icon getAddIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/add.png", size, size);
	}

	static Icon getRemoveIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/remove.png", size, size);
	}

	static Icon getCopyIcon(int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/copy.png", size, size);
	}

	static Icon getMoveIcon(boolean up, int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/move" + (up ? "Up" : "Down") + ".png", size, size);
	}

	static Icon getMoveEndIcon(boolean up, int size) {
		return ObservableSwingUtils.getFixedIcon(null, "/icons/move" + (up ? "Top" : "Bottom") + ".png", size, size);
	}

	static class SimpleListBuilder<R, P extends SimpleListBuilder<R, P>> extends SimpleFieldEditor<LittleList<R>, P>
	implements ListBuilder<R, P> {
		class ListItemAction<A extends SimpleDataAction<R, A>> extends SimpleDataAction<R, A> {
			private final List<R> theActionItems;

			ListItemAction(String actionName, Consumer<? super List<? extends R>> action, Supplier<List<R>> selectedValues) {
				super(actionName, SimpleListBuilder.this, action, selectedValues);
				theActionItems = new ArrayList<>();
			}

			@Override
			void updateSelection(List<R> selectedValues, Object cause) {
				theActionItems.clear();
				theActionItems.addAll(selectedValues);
				super.updateSelection(selectedValues, cause);
			}
		}
		private String theItemName;
		private SettableValue<R> theSelectionValue;
		private ObservableCollection<R> theSelectionValues;
		private List<ListItemAction<?>> theActions;

		SimpleListBuilder(ObservableCollection<R> rows, Supplier<Transactable> lock) {
			super(null, new LittleList<>(new ObservableListModel<>(rows)), lock);
		}

		@Override
		public P render(Consumer<CategoryRenderStrategy<R, R>> render) {
			render.accept(getEditor().getRenderStrategy());
			return (P) this;
		}

		@Override
		public P withSelection(SettableValue<R> selection, boolean enforceSingleSelection) {
			theSelectionValue = selection;
			if (enforceSingleSelection)
				getEditor().getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			return (P) this;
		}

		@Override
		public P withSelection(ObservableCollection<R> selection) {
			theSelectionValues = selection;
			return (P) this;
		}

		@Override
		public List<R> getSelection() {
			return ObservableSwingUtils.getSelection(getEditor().getModel(), getEditor().getSelectionModel(), null);
		}

		@Override
		public P withAdd(Supplier<? extends R> creator, Consumer<DataAction<R, ?>> actionMod) {
			throw new UnsupportedOperationException("Not implemented yet");
		}

		private CollectionElement<R> findElement(R value) {
			ObservableCollection<R> rows = getEditor().getModel().getWrapped();
			CollectionElement<R> el = rows.getElement(value, false);
			if (el != null && el.get() != value) {
				CollectionElement<R> lastMatch = rows.getElement(value, true);
				if (!lastMatch.getElementId().equals(el.getElementId())) {
					if (lastMatch.get() == value)
						el = lastMatch;
					else {
						while (el.get() != value && !el.getElementId().equals(lastMatch.getElementId()))
							el = rows.getAdjacentElement(el.getElementId(), true);
					}
					if (el.get() != value)
						el = null;
				}
			}
			return el;
		}

		@Override
		public P withRemove(Consumer<? super List<? extends R>> deletion, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(null, deletion, action -> {
				action.allowForMultiple(false).withTooltip(items -> "Remove selected " + getItemName())//
				.modifyButton(button -> button.withIcon(getRemoveIcon(8)));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		@Override
		public P withCopy(Function<? super R, ? extends R> copier, Consumer<DataAction<R, ?>> actionMod) {
			return withMultiAction(null, values -> {
				try (Transaction t = getEditor().getModel().getWrapped().lock(true, null)) {
					betterCopy(copier);
				}
			}, action -> {
				String single = getItemName();
				String plural = StringUtils.pluralize(single);
				action.allowForMultiple(false).withTooltip(items -> "Duplicate selected " + (items.size() == 1 ? single : plural))//
				.modifyButton(button -> button.withIcon(getCopyIcon(16)));
				if (actionMod != null)
					actionMod.accept(action);
			});
		}

		private void betterCopy(Function<? super R, ? extends R> copier) {
			ObservableCollection<R> rows = getEditor().getModel().getWrapped();
			ListSelectionModel selModel = getEditor().getSelectionModel();
			IntList newSelection = new IntList();
			for (int i = selModel.getMinSelectionIndex(); i >= 0 && i <= selModel.getMaxSelectionIndex(); i++) {
				if (!selModel.isSelectedIndex(i))
					continue;
				CollectionElement<R> toCopy = rows.getElement(i);
				R copy = copier.apply(toCopy.get());
				CollectionElement<R> copied = findElement(copy);
				if (copied != null) {
				} else if (rows.canAdd(copy, toCopy.getElementId(), null) == null)
					copied = rows.addElement(copy, toCopy.getElementId(), null, true);
				else
					copied = rows.addElement(copy, false);
				if (copied != null)
					newSelection.add(rows.getElementsBefore(copied.getElementId()));
			}
			selModel.setValueIsAdjusting(true);
			selModel.clearSelection();
			for (int[] interval : ObservableSwingUtils.getContinuousIntervals(newSelection.toArray(), true))
				selModel.addSelectionInterval(interval[0], interval[1]);
			selModel.setValueIsAdjusting(false);
		}

		@Override
		public P withMultiAction(String actionName, Consumer<? super List<? extends R>> action, Consumer<DataAction<R, ?>> actionMod) {
			if (theActions == null)
				theActions = new ArrayList<>();
			ListItemAction<?>[] tableAction = new ListItemAction[1];
			tableAction[0] = new ListItemAction(actionName, action, () -> tableAction[0].theActionItems);
			if (actionMod != null)
				actionMod.accept(tableAction[0]);
			theActions.add(tableAction[0]);
			return (P) this;
		}

		@Override
		public P withItemName(String itemName) {
			theItemName = itemName;
			return (P) this;
		}

		@Override
		public String getItemName() {
			return theItemName;
		}

		@Override
		public ObservableCollection<? extends R> getRows() {
			return getEditor().getModel().getWrapped();
		}

		@Override
		protected Component getOrCreateComponent(Observable<?> until) {
			Supplier<List<R>> selectionGetter = () -> getSelection();
			ObservableListModel<R> model = getEditor().getModel();
			if (theSelectionValue != null)
				ObservableSwingUtils.syncSelection(getEditor(), model, getEditor()::getSelectionModel, model.getWrapped().equivalence(),
					theSelectionValue, until, (index, cause) -> {
						MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
							.mutableElement(getRows().getElement(index).getElementId());
						if (el.isAcceptable(el.get()) == null) {
							try (Transaction t = getRows().lock(true, cause)) {
								el.set(el.get());
							}
						}
					}, false);
			if (theSelectionValues != null)
				ObservableSwingUtils.syncSelection(getEditor(), model, getEditor()::getSelectionModel, model.getWrapped().equivalence(),
					theSelectionValues, until);

			if (theActions != null) {
				for (ListItemAction<?> action : theActions) {
					if (action.isAllowedForEmpty() || action.isAllowedForMultiple()) {
						System.err.println("Multi actions not supported yet");
					} else
						getEditor().addItemAction(itemActionFor(action, until));
				}
			}

			return decorate(new ScrollPaneLite(getEditor()));
		}

		private LittleList.ItemAction<R> itemActionFor(ListItemAction<?> tableAction, Observable<?> until) {
			class ItemAction<P extends ItemAction<P>> implements LittleList.ItemAction<R>, ButtonEditor<JButton, P> {
				private Action theAction;
				private boolean isEnabled;
				private ComponentDecorator theDecorator;

				@Override
				public void configureAction(Action action, R item, int index) {
					theAction = action;
					action.setEnabled(true);
					tableAction.updateSelection(Arrays.asList(item), null);
					if (tableAction.theButtonMod != null)
						tableAction.theButtonMod.accept(this);

					String s;
					if (tableAction.theTooltipString != null) {
						s = tableAction.theTooltipString.get();
						if (s != null)
							action.putValue(Action.LONG_DESCRIPTION, s);
					}
					if (tableAction.theEnabledString != null) {
						s = tableAction.theEnabledString.get();
						if (s != null) {
							action.setEnabled(false);
							action.putValue(Action.LONG_DESCRIPTION, s);
						}
					}
					if (theDecorator != null)
						action.putValue("decorator", theDecorator);
				}

				@Override
				public void actionPerformed(R item, int index, Object cause) {
					theAction = null;
					isEnabled = true;
					tableAction.updateSelection(Arrays.asList(item), null);
					if (tableAction.theButtonMod != null)
						tableAction.theButtonMod.accept(this);
					if (tableAction.theEnabledString != null) {
						String s = tableAction.theEnabledString.get();
						if (s != null)
							isEnabled = false;
					}
					if (isEnabled)
						tableAction.theObservableAction.act(cause);
				}

				@Override
				public P withFieldName(ObservableValue<String> fieldName) {
					return (P) this;
				}

				@Override
				public P withPostLabel(ObservableValue<String> postLabel) {
					return (P) this;
				}

				@Override
				public P withPostButton(String buttonText, ObservableAction<?> action, Consumer<ButtonEditor<JButton, ?>> modify) {
					return (P) this;
				}

				@Override
				public P withTooltip(ObservableValue<String> tooltip) {
					if (theAction != null && theAction.isEnabled())
						theAction.putValue(Action.LONG_DESCRIPTION, tooltip.get());
					return (P) this;
				}

				@Override
				public P modifyFieldLabel(Consumer<FontAdjuster<?>> font) {
					return (P) this;
				}

				@Override
				public P withFont(Consumer<FontAdjuster<?>> font) {
					if (theAction != null)
						theAction.putValue("font", font);
					return (P) this;
				}

				@Override
				public JButton getEditor() {
					throw new UnsupportedOperationException();
				}

				@Override
				public P visibleWhen(ObservableValue<Boolean> visible) {
					boolean vis = visible == null || visible.get();
					isEnabled &= vis;
					if (theAction != null)
						theAction.putValue("visible", vis);
					return (P) this;
				}

				@Override
				public P withLayoutConstraints(Object constraints) {
					return (P) this;
				}

				@Override
				public P fill() {
					return (P) this;
				}

				@Override
				public P fillV() {
					return (P) this;
				}

				@Override
				public P decorate(Consumer<ComponentDecorator> decoration) {
					if (theDecorator != null)
						theDecorator = new ComponentDecorator();
					decoration.accept(theDecorator);
					return (P) this;
				}

				@Override
				public P modifyComponent(Consumer<Component> component) {
					return (P) this; // TODO can we support this?
				}

				@Override
				public P modifyEditor(Consumer<? super JButton> modify) {
					return (P) this;
				}

				@Override
				public Component getComponent() {
					throw new UnsupportedOperationException();
				}

				@Override
				public Alert alert(String title, String message) {
					return SimpleListBuilder.this.alert(title, message);
				}

				@Override
				public P onMouse(Consumer<MouseEvent> onMouse) {
					throw new UnsupportedOperationException();
				}

				@Override
				public P withText(ObservableValue<String> text) {
					if (theAction != null)
						theAction.putValue(Action.NAME, text.get());
					return (P) this;
				}

				@Override
				public P withIcon(ObservableValue<? extends Icon> icon) {
					if (theAction != null)
						theAction.putValue(Action.SMALL_ICON, icon == null ? null : icon.get());
					return (P) this;
				}

				@Override
				public P disableWith(ObservableValue<String> disabled) {
					String msg = disabled.get();
					isEnabled &= (msg != null);
					if (theAction != null) {
						theAction.setEnabled(msg == null);
						if (msg != null)
							theAction.putValue(Action.LONG_DESCRIPTION, msg);
					}
					return (P) this;
				}

				@Override
				public P withName(String name) {
					return (P) this;
				}
			}
			return new ItemAction();
		}
	}

	static class SimpleDataAction<R, A extends SimpleDataAction<R, A>> implements DataAction<R, A> {
		private final CollectionWidgetBuilder<R, ?, ?> theWidget;
		private String theActionName;
		final Consumer<? super List<? extends R>> theAction;
		final Supplier<List<R>> theSelectedValues;
		private Function<? super R, String> theEnablement;
		private Function<? super List<? extends R>, String> theMultiEnablement;
		private Function<? super List<? extends R>, String> theTooltip;
		private boolean zeroAllowed;
		private boolean multipleAllowed;
		private boolean actWhenAnyEnabled;
		private boolean isDisplayedWhenDisabled;
		ObservableAction<?> theObservableAction;
		SettableValue<String> theEnabledString;
		SettableValue<String> theTooltipString;
		Consumer<ButtonEditor<?, ?>> theButtonMod;

		SimpleDataAction(String actionName, CollectionWidgetBuilder<R, ?, ?> widget, Consumer<? super List<? extends R>> action,
			Supplier<List<R>> selectedValues) {
			theWidget = widget;
			theActionName = actionName;
			theAction = action;
			theSelectedValues = selectedValues;
			theEnabledString = SettableValue.build(String.class).build();
			theObservableAction = new ObservableAction<Object>() {
				@Override
				public TypeToken<Object> getType() {
					return TypeTokens.get().OBJECT;
				}

				@Override
				public Object act(Object cause) throws IllegalStateException {
					List<R> selected = theSelectedValues.get();
					if (theEnablement != null)
						selected = QommonsUtils.filterMap(selected, v -> theEnablement.apply(v) == null, null);
					theAction.accept(selected);
					return null;
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return theEnabledString;
				}
			};
			isDisplayedWhenDisabled = true;
			multipleAllowed = true;
		}

		public String isEnabled() {
			return theEnabledString.get();
		}

		@Override
		public boolean isAllowedForMultiple() {
			return zeroAllowed;
		}

		@Override
		public boolean isAllowedForEmpty() {
			return multipleAllowed;
		}

		@Override
		public boolean isDisplayedWhenDisabled() {
			return isDisplayedWhenDisabled;
		}

		@Override
		public A allowForMultiple(boolean allow) {
			multipleAllowed = allow;
			return (A) this;
		}

		@Override
		public A allowForEmpty(boolean allow) {
			zeroAllowed = allow;
			return (A) this;
		}

		@Override
		public A allowForAnyEnabled(boolean allow) {
			actWhenAnyEnabled = allow;
			return (A) this;
		}

		@Override
		public A allowWhen(Function<? super R, String> filter, Consumer<ActionEnablement<R>> operation) {
			Function<? super R, String> newFilter;
			if (operation != null) {
				ActionEnablement<R> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			} else
				newFilter = filter;
			if (theEnablement == null)
				theEnablement = newFilter;
			else {
				Function<? super R, String> oldFilter = theEnablement;
				theEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return (A) this;
		}

		@Override
		public A allowWhenMulti(Function<? super List<? extends R>, String> filter,
			Consumer<ActionEnablement<List<? extends R>>> operation) {
			Function<? super List<? extends R>, String> newFilter;
			if (operation != null) {
				ActionEnablement<List<? extends R>> next = new ActionEnablement<>(filter);
				operation.accept(next);
				newFilter = next;
			} else
				newFilter = filter;
			if (theMultiEnablement == null)
				theMultiEnablement = newFilter;
			else {
				Function<? super List<? extends R>, String> oldFilter = theMultiEnablement;
				theMultiEnablement = value -> {
					String msg = oldFilter.apply(value);
					if (msg != null)
						return msg;
					return newFilter.apply(value);
				};
			}
			return (A) this;
		}

		@Override
		public A displayWhenDisabled(boolean displayedWhenDisabled) {
			isDisplayedWhenDisabled = displayedWhenDisabled;
			return (A) this;
		}

		@Override
		public A withTooltip(Function<? super List<? extends R>, String> tooltip) {
			theTooltip = tooltip;
			if (tooltip != null)
				theTooltipString = SettableValue.build(String.class).build();
			return (A) this;
		}

		@Override
		public A modifyAction(Function<? super ObservableAction<?>, ? extends ObservableAction<?>> actionMod) {
			theObservableAction = actionMod.apply(theObservableAction);
			return (A) this;
		}

		@Override
		public A confirm(String alertTitle, Function<List<? extends R>, String> alertText, boolean confirmType) {
			return modifyAction(action -> {
				return new ObservableAction<Void>() {
					@Override
					public TypeToken<Void> getType() {
						return TypeTokens.get().VOID;
					}

					@Override
					public Void act(Object cause) throws IllegalStateException {
						List<? extends R> selected = theWidget.getSelection();
						String text = alertText.apply(selected);
						if (!theWidget.alert(alertTitle, text).confirm(confirmType))
							return null;
						action.act(cause);
						return null;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return action.isEnabled();
					}
				};
			});
		}

		@Override
		public A confirmForItems(String alertTitle, String alertPreText, String alertPostText, boolean confirmType) {
			return modifyAction(action -> {
				return new ObservableAction<Void>() {
					@Override
					public TypeToken<Void> getType() {
						return TypeTokens.get().VOID;
					}

					@Override
					public Void act(Object cause) throws IllegalStateException {
						List<? extends R> selected = theWidget.getSelection();
						StringBuilder text = new StringBuilder();
						if (alertPreText != null && !alertPreText.isEmpty())
							text.append(alertPreText);
						if (selected.isEmpty())
							text.append("no ").append(StringUtils.pluralize(theWidget.getItemName()));
						else if (selected.size() == 1) {
							if (theWidget instanceof SimpleTableBuilder && ((SimpleTableBuilder<R, ?>) theWidget).getNameFunction() != null)
								text.append(theWidget.getItemName()).append(" \"")
								.append(((SimpleTableBuilder<R, ?>) theWidget).getNameFunction().apply(selected.get(0))).append('"');
							else
								text.append("1 ").append(theWidget.getItemName());
						} else
							text.append(selected.size()).append(' ').append(StringUtils.pluralize(theWidget.getItemName()));
						if (alertPostText != null && !alertPostText.isEmpty())
							text.append(alertPostText);
						if (!theWidget.alert(alertTitle, text.toString()).confirm(confirmType))
							return null;
						action.act(cause);
						return null;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return action.isEnabled();
					}
				};
			});
		}

		@Override
		public A modifyButton(Consumer<ButtonEditor<?, ?>> buttonMod) {
			if (theButtonMod == null)
				theButtonMod = buttonMod;
			else {
				Consumer<ButtonEditor<?, ?>> oldButtonMod = theButtonMod;
				theButtonMod = field -> {
					oldButtonMod.accept(field);
					buttonMod.accept(field);
				};
			}
			return (A) this;
		}

		@Override
		public List<R> getActionItems() {
			return theSelectedValues.get();
		}

		void updateSelection(List<R> selectedValues, Object cause) {
			if (!zeroAllowed && selectedValues.isEmpty())
				theEnabledString.set("Nothing selected", cause);
			else if (!multipleAllowed && selectedValues.size() > 1)
				theEnabledString.set("Multiple " + StringUtils.pluralize(theWidget.getItemName()) + " selected", cause);
			else {
				StringBuilder message = null;
				if (theMultiEnablement != null) {
					String msg = theMultiEnablement.apply(selectedValues);
					if (msg != null)
						message = new StringBuilder(msg);
				}
				if (message == null && theEnablement != null) {
					Set<String> messages = null;
					int allowedCount = 0;
					for (R value : selectedValues) {
						String msg = theEnablement.apply(value);
						if (msg == null)
							allowedCount++;
						else {
							if (messages == null)
								messages = new LinkedHashSet<>();
							messages.add(msg);
						}
					}
					boolean error = false;
					if (!actWhenAnyEnabled && messages != null) {
						error = true;
					} else if (allowedCount == 0 && !zeroAllowed) {
						error = true;
						if (messages == null) {
							messages = new LinkedHashSet<>();
							messages.add("Nothing selected");
						}
					} else if (allowedCount > 1 && !multipleAllowed) {
						error = true;
						if (messages == null) {
							messages = new LinkedHashSet<>();
							messages.add("Multiple " + StringUtils.pluralize(theWidget.getItemName()) + " selected");
						}
					}
					if (error) {
						message = new StringBuilder("<html>");
						boolean first = true;
						if (messages != null) {
							for (String msg : messages) {
								if (!first)
									message.append("<br>");
								else
									first = false;
								message.append(msg);
							}
						}
					}
				}
				theEnabledString.set(message == null ? null : message.toString(), cause);
			}

			if (theTooltipString != null && theEnabledString.get() == null) { // No point generating the tooltip if the disabled string will
				// show
				theTooltipString.set(theTooltip.apply(selectedValues), cause);
			}
		}

		void addButton(PanelPopulator<?, ?> panel) {
			panel.addButton(theActionName, theObservableAction, button -> {
				if (theTooltipString != null)
					button.withTooltip(theTooltipString);
				if (theButtonMod != null)
					theButtonMod.accept(button);
				if (!isDisplayedWhenDisabled)
					button.visibleWhen(theObservableAction.isEnabled().map(e -> e == null));
			});
		}
	}

	enum AlertType {
		PLAIN, INFO, WARNING, ERROR;
	}

	static class SimpleAlert implements Alert {
		private final Component theComponent;
		private String theTitle;
		private String theMessage;
		private AlertType theType;
		private SimpleImageControl theImage;

		SimpleAlert(Component component, String title, String message) {
			theComponent = component;
			theTitle = title;
			theMessage = message;
			theType = AlertType.PLAIN;
		}

		@Override
		public Alert info() {
			theType = AlertType.INFO;
			return this;
		}

		@Override
		public Alert warning() {
			theType = AlertType.WARNING;
			return this;
		}

		@Override
		public Alert error() {
			theType = AlertType.ERROR;
			return this;
		}

		@Override
		public Alert withIcon(String location, Consumer<ImageControl> image) {
			if (theImage == null)
				theImage = new SimpleImageControl(location);
			if (image != null)
				image.accept(theImage);
			return this;
		}

		@Override
		public void display() {
			Icon icon = theImage == null ? null : theImage.getIcon().get();
			EventQueue.invokeLater(theComponent::requestFocus);
			if (icon != null)
				JOptionPane.showMessageDialog(theComponent, theMessage, theTitle, getJOptionType(), icon);
			else
				JOptionPane.showMessageDialog(theComponent, theMessage, theTitle, getJOptionType());
		}

		@Override
		public boolean confirm(boolean confirmType) {
			Icon icon = theImage == null ? null : theImage.getIcon().get();
			int ct = confirmType ? JOptionPane.OK_CANCEL_OPTION : JOptionPane.YES_NO_OPTION;
			int result;
			if (icon != null)
				result = JOptionPane.showConfirmDialog(theComponent, theMessage, theTitle, ct, getJOptionType(), icon);
			else
				result = JOptionPane.showConfirmDialog(theComponent, theMessage, theTitle, ct, getJOptionType());
			return (result == JOptionPane.OK_OPTION || result == JOptionPane.YES_OPTION);
		}

		@Override
		public <T> T input(TypeToken<T> type, Format<T> format, T initial, Consumer<ObservableTextField<T>> modify) {
			SimpleObservable<Void> until = SimpleObservable.build().build();
			SettableValue<T> value = SettableValue.build(type).withValue(initial).build();
			WindowBuilder<JDialog, ?> dialog = WindowPopulation.populateDialog(null, until, true)//
				.modal(true).withTitle(theTitle);
			if (theImage != null)
				dialog.withIcon(theImage.getIcon().map(img -> ((ImageIcon) img).getImage()));
			boolean[] provided = new boolean[1];
			dialog.withVContent(panel -> {
				if (theMessage != null)
					panel.addLabel(null, theMessage, null);
				ValueHolder<ObservableTextField<T>> field = new ValueHolder<>();
				panel.addTextField(null, value, format, f -> f.fill().modifyEditor(field));
				if (modify != null)
					modify.accept(field.get());
				panel.addHPanel(null, new JustifiedBoxLayout(false).mainCenter().crossJustified(), buttons -> {
					buttons.addButton("OK", __ -> {
						provided[0] = true;
						dialog.getWindow().setVisible(false);
					}, btn -> btn.disableWith(field.get().getErrorState()))//
					.addButton("Cancel", __ -> dialog.getWindow().setVisible(false), null);
				});
			});
			if (EventQueue.isDispatchThread())
				dialog.run(theComponent);
			else {
				try {
					EventQueue.invokeAndWait(() -> dialog.run(theComponent));
				} catch (InvocationTargetException e) {
					e.getTargetException().printStackTrace();
					return null;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return null;
				}
			}
			until.onNext(null);
			if (provided[0])
				return value.get();
			else
				return null;
		}

		private int getJOptionType() {
			switch (theType) {
			case PLAIN:
				return JOptionPane.PLAIN_MESSAGE;
			case INFO:
				return JOptionPane.INFORMATION_MESSAGE;
			case WARNING:
				return JOptionPane.WARNING_MESSAGE;
			case ERROR:
				return JOptionPane.ERROR_MESSAGE;
			}
			return JOptionPane.ERROR_MESSAGE;
		}
	}
}

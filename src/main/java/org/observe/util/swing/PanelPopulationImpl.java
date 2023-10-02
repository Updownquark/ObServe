package org.observe.util.swing;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.ChangeListener;

import org.jdesktop.swingx.JXCollapsiblePane;
import org.jdesktop.swingx.JXPanel;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.ObservableCollection;
import org.observe.util.ObservableCollectionSynchronization;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableCellRenderer.CellRenderContext;
import org.observe.util.swing.PanelPopulation.*;
import org.qommons.BiTuple;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

class PanelPopulationImpl {
	private PanelPopulationImpl() {
	}

	static class VizChanger implements Consumer<ObservableValueEvent<Boolean>> {
		private final Component[] theComponents;
		private boolean shouldBeVisible;

		public VizChanger(Component... components) {
			theComponents = components;
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
			boolean effect = false;
			for (Component c : theComponents) {
				if (c == null || visible == c.isVisible())
					continue;
				effect = true;
				c.setVisible(visible);
			}
			if (effect) {
				if (theComponents[0].getParent() != null)
					theComponents[0].getParent().revalidate();
			}
		}
	}

	static class MigFieldPanel<C extends Container, P extends MigFieldPanel<C, P>> extends AbstractComponentEditor<C, P>
	implements PartialPanelPopulatorImpl<C, P> {
		MigFieldPanel(String fieldName, C container, Observable<?> until) {
			super(fieldName, //
				container != null ? container
					: (C) new ConformingPanel(PanelPopulation.createMigLayout(true, () -> "install the layout before using this class")),
					until);
			if (getContainer().getLayout() == null
				|| !PanelPopulation.MIG_LAYOUT_CLASS_NAME.equals(getContainer().getLayout().getClass().getName())) {
				LayoutManager2 migLayout = PanelPopulation.createMigLayout(true, () -> "install the layout before using this class");
				getContainer().setLayout(migLayout);
			}
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public P withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return super.withGlassPane(layout, panel);
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
			Component component = field.getComponent();
			if (component == null)
				throw new IllegalStateException();
			if (scrolled)
				getContainer().add(new JScrollPane(component), constraints.toString());
			else
				getContainer().add(component, constraints.toString());
			if (postLabel != null)
				getContainer().add(postLabel, "wrap");
			if (field.isVisible() != null)
				field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel, postLabel));
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
		private final JPopupMenu thePopup;
		private final JLabel theMenuCloser;
		private boolean hasShown;

		SettingsMenuImpl(String fieldName, C container, Observable<?> until) {
			super(fieldName, container, until);
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
		protected Component createComponent() {
			JLabel label = new JLabel();
			theIcon.changes().takeUntil(getUntil()).act(evt -> {
				label.setIcon(evt.getNewValue());
				theMenuCloser.setIcon(evt.getNewValue());
			});
			if (getTooltip() != null) {
				getTooltip().changes().takeUntil(getUntil()).act(evt -> {
					label.setToolTipText(evt.getNewValue());
					theMenuCloser.setToolTipText(evt.getNewValue());
				});
			}
			decorate(label);
			decorate(theMenuCloser);

			thePopup.setLayout(new BorderLayout());
			JPanel topPanel = new JPanel(new JustifiedBoxLayout(false).mainTrailing());
			topPanel.add(theMenuCloser);
			thePopup.add(topPanel, BorderLayout.NORTH);
			thePopup.add(getContainer(), BorderLayout.CENTER);
			thePopup.setBorder(BorderFactory.createLineBorder(Color.black, 2));
			label.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
						open();
					}
				}
			});
			return label;
		}

		/** Opens the menu. Normally called internally. */
		public void open() {
			Runnable action = () -> {
				Point gearRelPopup = getGearRelPopup();

				thePopup.show(getComponent(), -gearRelPopup.x, -gearRelPopup.y);

				if (!hasShown) {
					gearRelPopup = getGearRelPopup();
					Point screenLoc = getComponent().getLocationOnScreen();
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
		private ObservableValue<String> thePostLabel;
		private SimpleButtonEditor<JButton, ?> thePostButton;

		SimpleFieldEditor(String fieldName, E editor, Observable<?> until) {
			super(fieldName, editor, until);
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
		public P withPostButton(String buttonText, ObservableAction action, Consumer<ButtonEditor<JButton, ?>> modify) {
			if (thePostLabel != null) {
				System.err.println("A field can only have one post component");
				thePostLabel = null;
			}
			thePostButton = new SimpleButtonEditor<>(null, new JButton(), buttonText, action, true, getUntil());
			if (modify != null)
				modify.accept(thePostButton);
			return (P) this;
		}

		@Override
		protected Component createPostLabel(Observable<?> until) {
			if (thePostLabel != null) {
				JLabel postLabel = new JLabel(thePostLabel.get());
				thePostLabel.changes().takeUntil(until).act(evt -> postLabel.setText(evt.getNewValue()));
				if (theFont != null)
					new FontAdjuster().configure(theFont).adjust(postLabel);
				return postLabel;
			} else if (thePostButton != null)
				return thePostButton.getComponent();
			else
				return null;
		}

		@Override
		public Component decorate(Component c) {
			if (theFont != null)
				new FontAdjuster().configure(theFont).adjust(c);
			return super.decorate(c);
		}
	}

	static class SimpleHPanel<C extends Container, P extends SimpleHPanel<C, P>> extends SimpleFieldEditor<C, P>
	implements PartialPanelPopulatorImpl<C, P> {
		SimpleHPanel(String fieldName, C editor, Observable<?> until) {
			super(fieldName, editor, until);
		}

		@Override
		public C getContainer() {
			return getEditor();
		}

		@Override
		public P withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return super.withGlassPane(layout, panel);
		}

		@Override
		public void doAdd(AbstractComponentEditor<?, ?> field, Component fieldLabel, Component postLabel, boolean scrolled) {
			if (fieldLabel != null)
				getContainer().add(fieldLabel);
			Component component = field.getComponent();
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
			if (field.isVisible() != null)
				field.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(component, fieldLabel, postLabel));
		}

		@Override
		public P addCheckField(String fieldName, SettableValue<Boolean> field, Consumer<FieldEditor<JCheckBox, ?>> modify) {
			SimpleFieldEditor<JCheckBox, ?> fieldPanel = new SimpleFieldEditor<>(fieldName, new JCheckBox(), getUntil());
			fieldPanel.getEditor().setHorizontalTextPosition(SwingConstants.LEADING);
			Subscription sub = ObservableSwingUtils.checkFor(fieldPanel.getEditor(), fieldPanel.getTooltip(), field);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			if (modify != null)
				modify.accept(fieldPanel);
			fieldPanel.onFieldName(fieldPanel.getEditor(), name -> fieldPanel.getEditor().setText(name), getUntil());
			doAdd(fieldPanel, null, fieldPanel.createPostLabel(getUntil()), false);
			return (P) this;
		}
	}

	static class SimpleLabelEditor<L extends JLabel, P extends SimpleLabelEditor<L, P>> extends SimpleFieldEditor<L, P>
	implements LabelEditor<L, P> {
		private ObservableValue<? extends Icon> theIcon;

		SimpleLabelEditor(String fieldName, L editor, Observable<?> until) {
			super(fieldName, editor, until);
		}

		public ObservableValue<? extends Icon> getIcon() {
			return theIcon;
		}

		@Override
		public P withIcon(ObservableValue<? extends Icon> icon) {
			theIcon = icon;
			return (P) this;
		}

		@Override
		public Component decorate(Component c) {
			super.decorate(c);
			if (theIcon != null) {
				theIcon.changes().takeUntil(getUntil()).act(evt -> {
					((JLabel) c).setIcon(evt.getNewValue());
				});
			}
			return c;
		}
	}

	static class SimpleImageControl implements ImageControl {
		private final SettableValue<ObservableValue<? extends Icon>> theSettableIcon;
		private final ObservableValue<Icon> theIcon;
		private final SimpleObservable<Void> theTweakObservable;
		private int theWidth;
		private int theHeight;

		public SimpleImageControl(String location) {
			theTweakObservable = new SimpleObservable<>();
			theSettableIcon = SettableValue.build((Class<ObservableValue<? extends Icon>>) (Class<?>) ObservableValue.class).build();
			setLocation(location);
			theIcon = ObservableValue.flatten(theSettableIcon).refresh(theTweakObservable).map(this::adjustIcon);
			theWidth = -1;
			theHeight = -1;
		}

		ImageIcon getIcon(String location) {
			if (location == null)
				return null;
			return ObservableSwingUtils.getIcon(getClass(), location);
		}

		Icon adjustIcon(Icon icon) {
			if (icon instanceof ImageIcon) {
				if ((theWidth >= 0 && icon.getIconWidth() != theWidth) || (theHeight >= 0 && icon.getIconHeight() != theHeight)) {
					icon = new ImageIcon(((ImageIcon) icon).getImage().getScaledInstance(//
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
	}

	static class SimpleButtonEditor<B extends AbstractButton, P extends SimpleButtonEditor<B, P>> extends SimpleFieldEditor<B, P>
	implements ButtonEditor<B, P> {
		private final ObservableAction theAction;
		private ObservableValue<String> theText;
		private ObservableValue<? extends Icon> theIcon;
		private ObservableValue<String> theDisablement;
		private final boolean isPostButton;

		SimpleButtonEditor(String fieldName, B button, String buttonText, ObservableAction action, boolean postButton,
			Observable<?> until) {
			super(fieldName, button, until);
			theAction = action;
			theText = ObservableValue.of(TypeTokens.get().STRING, buttonText);
			theDisablement = action == null ? null : action.isEnabled();
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
				theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, Objects::nonNull, () -> null, old, disabled);
			}
			return (P) this;
		}

		@Override
		protected Component createComponent() {
			if (theAction != null)
				getEditor().addActionListener(theAction::act);
			ObservableValue<String> enabled;
			if (theDisablement != null)
				enabled = theDisablement;
			else
				enabled = ObservableValue.of(String.class, null);
			enabled.combine((e, tt) -> e == null ? tt : e, getTooltip()).changes().takeUntil(getUntil())
			.act(evt -> getEditor().setToolTipText(evt.getNewValue()));
			enabled.takeUntil(getUntil()).changes().act(evt -> getEditor().setEnabled(evt.getNewValue() == null));
			if (theText != null)
				theText.changes().takeUntil(getUntil()).act(evt -> getEditor().setText(evt.getNewValue()));
			if (theIcon != null)
				theIcon.changes().takeUntil(getUntil()).act(evt -> getEditor().setIcon(evt.getNewValue()));
			return getEditor();
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
		public P modifyFieldLabel(Consumer<FontAdjuster> labelModifier) {
			if (isPostButton)
				throw new IllegalStateException("No field label for a post-button");
			return super.modifyFieldLabel(labelModifier);
		}
	}

	static class SimpleSteppedFieldEditor<E, F, P extends SimpleSteppedFieldEditor<E, F, P>> extends SimpleFieldEditor<E, P>
	implements SteppedFieldEditor<E, F, P> {
		private final Consumer<F> theStepSizeChange;

		SimpleSteppedFieldEditor(String fieldName, E editor, Consumer<F> stepSizeChange, Observable<?> until) {
			super(fieldName, editor, until);
			theStepSizeChange = stepSizeChange;
		}

		@Override
		public P withStepSize(F stepSize) {
			theStepSizeChange.accept(stepSize);
			return (P) this;
		}
	}

	static class SimpleMultiSliderEditor<P extends SimpleMultiSliderEditor<P>> extends SimpleFieldEditor<MultiRangeSlider, P>
	implements SliderEditor<MultiRangeSlider, P> {
		private SettableValue<ObservableValue<Double>> theMinValue;
		private SettableValue<ObservableValue<Double>> theMaxValue;

		public static SimpleMultiSliderEditor<?> createForValue(String fieldName, SettableValue<Double> value, Observable<?> until) {
			SettableValue<ObservableValue<Double>>[] minMax = createMinMax();
			SettableValue<MultiRangeSlider.Range> sliderBounds = createSliderBounds(until, minMax);
			MultiRangeSlider slider = MultiRangeSlider.forValueExtent(false, sliderBounds, value,
				SettableValue.of(double.class, 0.0, "Range is not editable"), until);
			((MultiRangeSlider.RangeRenderer.Default) slider.getRangeRenderer()).withColor(r -> Color.blue, r -> Color.blue);
			return new SimpleMultiSliderEditor<>(fieldName, slider, minMax, until);
		}

		public static SimpleMultiSliderEditor<?> createForMinMax(String fieldName, SettableValue<Double> min, SettableValue<Double> max,
			Observable<?> until) {
			SettableValue<ObservableValue<Double>>[] minMax = createMinMax();
			SettableValue<MultiRangeSlider.Range> sliderBounds = createSliderBounds(until, minMax);
			MultiRangeSlider slider = MultiRangeSlider.forMinMax(false, sliderBounds, min, max, until);
			((MultiRangeSlider.RangeRenderer.Default) slider.getRangeRenderer()).withColor(r -> Color.blue, r -> Color.blue);
			return new SimpleMultiSliderEditor<>(fieldName, slider, minMax, until);
		}

		public static SimpleMultiSliderEditor<?> createForValues(String fieldName, ObservableCollection<Double> values,
			Observable<?> until) {
			SettableValue<ObservableValue<Double>>[] minMax = createMinMax();
			SettableValue<MultiRangeSlider.Range> sliderBounds = createSliderBounds(until, minMax);
			return new SimpleMultiSliderEditor<>(fieldName, //
				MultiRangeSlider.multi(false, sliderBounds, values.flow().transform(MultiRangeSlider.Range.class, tx -> tx.cache(false)//
					.map(v -> MultiRangeSlider.Range.forValueExtent(v, 0))//
					.replaceSource(r -> r.getValue(), null)//
					).collectPassive(), //
					until),
				minMax, until);
		}

		public static SimpleMultiSliderEditor<?> createForRange(String fieldName, SettableValue<MultiRangeSlider.Range> range,
			Observable<?> until) {
			SettableValue<ObservableValue<Double>>[] minMax = createMinMax();
			SettableValue<MultiRangeSlider.Range> sliderBounds = createSliderBounds(until, minMax);
			MultiRangeSlider slider = MultiRangeSlider.single(false, sliderBounds, range, until);
			((MultiRangeSlider.RangeRenderer.Default) slider.getRangeRenderer()).withColor(r -> Color.blue, r -> Color.blue);
			return new SimpleMultiSliderEditor<>(fieldName, slider, minMax, until);
		}

		public static SimpleMultiSliderEditor<?> createForRanges(String fieldName, ObservableCollection<MultiRangeSlider.Range> ranges,
			Observable<?> until) {
			SettableValue<ObservableValue<Double>>[] minMax = createMinMax();
			SettableValue<MultiRangeSlider.Range> sliderBounds = createSliderBounds(until, minMax);
			return new SimpleMultiSliderEditor<>(fieldName, //
				MultiRangeSlider.multi(false, sliderBounds, ranges, until), //
				minMax, until);
		}

		static SettableValue<ObservableValue<Double>>[] createMinMax() {
			SettableValue<ObservableValue<Double>> min = SettableValue
				.build((Class<ObservableValue<Double>>) (Class<?>) ObservableValue.class)
				.withValue(SettableValue.build(double.class).withValue(0.0).build()).build();
			SettableValue<ObservableValue<Double>> max = SettableValue
				.build((Class<ObservableValue<Double>>) (Class<?>) ObservableValue.class)
				.withValue(SettableValue.build(double.class).withValue(1.0).build()).build();
			return new SettableValue[] { min, max };
		}

		static SettableValue<MultiRangeSlider.Range> createSliderBounds(Observable<?> until,
			SettableValue<ObservableValue<Double>>... minMax) {
			SettableValue<Double> flatMin = SettableValue.flattenAsSettable(minMax[0], () -> 0.0);
			SettableValue<Double> flatMax = SettableValue.flattenAsSettable(minMax[1], () -> 1.0);
			return MultiRangeSlider.transformToRange(flatMin, flatMax, until);
		}

		private SimpleMultiSliderEditor(String fieldName, MultiRangeSlider slider, SettableValue<ObservableValue<Double>>[] minMax,
			Observable<?> until) {
			super(fieldName, slider.setMaxUpdateInterval(Duration.ofMillis(100)), until);
			theMinValue = minMax[0];
			theMaxValue = minMax[1];
		}

		@Override
		public P withBounds(ObservableValue<Double> min, ObservableValue<Double> max) {
			// Can't ever have min>max
			if (min.get() <= theMinValue.get().get()) {
				theMinValue.set(min, null);
				theMaxValue.set(max, null);
			} else {
				theMaxValue.set(max, null);
				theMinValue.set(min, null);
			}
			return (P) this;
		}

		@Override
		public P adjustBoundsForValue(boolean adjustForValue) {
			getEditor().setAdjustingBoundsForValue(adjustForValue);
			return (P) this;
		}

		@Override
		public P enforceNoOverlap(boolean noOverlap, boolean withinSliderBounds) {
			if (noOverlap) {
				if (withinSliderBounds)
					getEditor().setValidator(MultiRangeSlider.RangeValidator.NO_OVERLAP_ENFORCE_RANGE);
				else
					getEditor().setValidator(MultiRangeSlider.RangeValidator.NO_OVERLAP);
			} else if (withinSliderBounds)
				getEditor().setValidator(MultiRangeSlider.RangeValidator.ENFORCE_RANGE);
			else
				getEditor().setValidator(MultiRangeSlider.RangeValidator.FREE);
			return (P) this;
		}
	}

	static class SimpleComboEditor<F, P extends SimpleComboEditor<F, P>> extends SimpleFieldEditor<JComboBox<F>, P>
	implements ComboEditor<F, P> {
		private Function<? super F, String> theValueTooltip;
		private IntSupplier theHoveredItem;
		private final SettableValue<F> theSelection;

		SimpleComboEditor(String fieldName, JComboBox<F> editor, SettableValue<F> selection, Observable<?> until) {
			super(fieldName, editor, until);
			theSelection = selection;
		}

		void setHoveredItem(IntSupplier hoveredItem) {
			theHoveredItem = hoveredItem;
		}

		@Override
		public P renderWith(ObservableCellRenderer<F, F> renderer) {
			getEditor().setRenderer(new ListCellRenderer<F>() {
				@Override
				public Component getListCellRendererComponent(JList<? extends F> list, F value, int index, boolean isSelected,
					boolean cellHasFocus) {
					boolean hovered = theHoveredItem != null && theHoveredItem.getAsInt() == index;
					return renderer.getCellRendererComponent(list,
						new ModelCell.Default<>(() -> value, value, index, 0, isSelected, cellHasFocus, hovered, hovered, true, true, //
							theSelection.isAcceptable(value)),
						CellRenderContext.DEFAULT);
				}
			});
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
			Function<? super F, ? extends TB> buttonCreator, Observable<?> until) {
			super(fieldName, new HashMap<>(), until);
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
		protected Component createComponent() {
			ObservableCollection<? extends F> safeValues = theValues.safe(ThreadConstraint.EDT, getUntil());
			ObservableCollection<TB>[] _buttons = new ObservableCollection[1];
			Subscription valueSub = ObservableSwingUtils.togglesFor(safeValues, theValue, TypeTokens.get().of(theButtonType),
				theButtonCreator, b -> _buttons[0] = b, this::render, this::getValueTooltip);
			theButtons = _buttons[0];
			for (JToggleButton button : theButtons) {
				thePanel.add(button);
			}
			theButtons.changes().takeUntil(getUntil()).act(evt -> {
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
			getUntil().take(1).act(__ -> valueSub.unsubscribe());
			return thePanel;
		}
	}

	static class SimpleComboButtonEditor<F, B extends ComboButton<F>, P extends SimpleComboButtonEditor<F, B, P>>
	extends SimpleButtonEditor<B, P> implements PanelPopulation.ComboButtonBuilder<F, B, P> {
		public SimpleComboButtonEditor(String fieldName, String buttonText, ObservableCollection<F> values,
			BiConsumer<? super F, Object> action, Observable<?> until) {
			super(fieldName, (B) createButton(values, action, buttonText, until), buttonText, null, false, until);
		}

		static <F> ComboButton<F> createButton(ObservableCollection<F> values, BiConsumer<? super F, Object> action, String buttonText,
			Observable<?> until) {
			return new ComboButton<>(values, ComboButton.createDefaultComboBoxColumn(values.getType()), until)//
				.addListener(action)//
				;
		}

		@Override
		public P render(Consumer<CategoryRenderStrategy<F, F>> render) {
			render.accept((CategoryRenderStrategy<F, F>) getEditor().getColumn());
			return (P) this;
		}
	}

	static class SimpleProgressEditor<P extends SimpleProgressEditor<P>> extends SimpleFieldEditor<JProgressBar, P>
	implements ProgressEditor<P> {
		private ObservableValue<Integer> theTaskLength;
		private ObservableValue<Integer> theProgress;
		private ObservableValue<Boolean> isIndeterminate;
		private ObservableValue<String> theText;
		private boolean isInitialized;

		public SimpleProgressEditor(String fieldName, Observable<?> until) {
			super(fieldName, new JProgressBar(JProgressBar.HORIZONTAL), until);
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
		protected Component createComponent() {
			if (!isInitialized) {
				isInitialized = true;
				if (theProgress == null)
					throw new IllegalStateException("No progress value set");
				Observable.or(theTaskLength.noInitChanges(), theProgress.noInitChanges(), //
					isIndeterminate == null ? Observable.empty() : isIndeterminate.noInitChanges(), //
						theText == null ? Observable.empty() : theText.noInitChanges())//
				.takeUntil(getUntil())//
				.act(__ -> updateProgress());
				updateProgress();
			}
			return getEditor();
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

		private final Map<Object, Tab> theTabs;
		private final Map<Component, Tab> theTabsByComponent;
		private final SettableValue<Object> theSelectedTabId;
		private List<Runnable> thePostCreateActions;
		private Tab theSelectedTab;

		SimpleTabPaneEditor(Observable<?> until) {
			super(null, new JTabbedPane(), until);
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
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, null, getUntil());
			panel.accept(fieldPanel);
			return withTabImpl(tabID, tabIndex, fieldPanel.getContainer(), tabModifier, fieldPanel);
		}

		@Override
		public P withHTab(Object tabID, int tabIndex, LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel,
			Consumer<TabEditor<?>> tabModifier) {
			SimpleHPanel<JPanel, ?> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
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
			Observable<?> tabUntil = Observable.or(tab.tabEnd, getUntil());
			tab.until = tabUntil;
			tab.component = t.getComponent(tabUntil);
			if (theTabsByComponent.put(tab.component, tab) != null)
				throw new IllegalStateException("Duplicate tab components (" + tabID + ")");
			if (panel != null && panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(tab.component, null));
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
				tabID.changes().takeUntil(getUntil()).act(evt -> ObservableSwingUtils.onEQ(() -> {
					if ((evt.getNewValue() == null || theTabs.containsKey(evt.getNewValue()))//
						&& !Objects.equals(theSelectedTabId.get(), evt.getNewValue())) {
						theSelectedTabId.set(evt.getNewValue(), evt.isTerminated() ? null : evt);
					}
				}));
				theSelectedTabId.noInitChanges().takeUntil(getUntil()).act(evt -> {
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
		protected Component createComponent() {
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
				theSelectedTabId.changes().takeUntil(getUntil()).act(evt -> {
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
				getUntil().take(1).act(__ -> getEditor().removeChangeListener(tabListener));
				List<Runnable> pcas = thePostCreateActions;
				thePostCreateActions = null;
				for (Runnable action : pcas)
					action.run();
			});
			return getEditor();
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
		public P onRemove(Consumer<Object> onRem) {
			this.onRemove = onRem;
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
		private IntFunction<Integer> theSplitFunction;
		private BiPredicate<Integer, Integer> theSplitOnChange;
		private Observable<?> theSplitChanges;

		SimpleSplitEditor(boolean vertical, Observable<?> until) {
			super(null, new JSplitPane(vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT), until);
		}

		boolean hasSetFirst;
		boolean hasSetLast;

		@Override
		public P firstV(Consumer<PanelPopulator<?, ?>> vPanel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, null, getUntil());
			vPanel.accept(fieldPanel);
			first(fieldPanel.getComponent());
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P firstH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel, ?> panel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
			hPanel.accept(panel);
			first(panel.getComponent());
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(panel.getComponent(), null));
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
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, null, getUntil());
			vPanel.accept(fieldPanel);
			last(fieldPanel.getComponent());
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P lastH(LayoutManager layout, Consumer<PanelPopulator<?, ?>> hPanel) {
			SimpleHPanel<JPanel, ?> panel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
			hPanel.accept(panel);
			last(panel.getComponent());
			if (panel.isVisible() != null)
				panel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(panel.getComponent(), null));
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
		public P withSplit(IntFunction<Integer> split, BiPredicate<Integer, Integer> onChange, Observable<?> changes) {
			theSplitFunction = split;
			theSplitOnChange = onChange;
			theSplitChanges = changes;
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
		protected Component createComponent() {
			if (theSplitFunction != null) {
				boolean vertical = getEditor().getOrientation() == JSplitPane.VERTICAL_SPLIT;
				IntSupplier size = vertical ? () -> getEditor().getHeight() - getEditor().getDividerSize()
					: () -> getEditor().getWidth() - getEditor().getDividerSize();
					boolean[] callbackLock = new boolean[1];
					boolean[] hasConfiguredDL = new boolean[1];
					Runnable resize = () -> {
						callbackLock[0] = true;
						hasConfiguredDL[0] = true;
						try {
							Integer divLoc = theSplitFunction.apply(size.getAsInt());
							if (divLoc != null) {
								System.out.println("Setting DL to " + divLoc);
								getEditor().setDividerLocation(divLoc);
							}
						} finally {
							callbackLock[0] = false;
						}
					};
					ComponentAdapter visListener = new ComponentAdapter() {
						@Override
						public void componentShown(ComponentEvent e) {
							EventQueue.invokeLater(resize);
						}

						@Override
						public void componentResized(ComponentEvent e) {
							if (callbackLock[0])
								return;
							resize.run();
						}
					};
					getEditor().addComponentListener(visListener);
					if (getEditor().getLeftComponent() != null) {
						getEditor().getLeftComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getRightComponent() == null || !getEditor().getRightComponent().isVisible())
									return;
								EventQueue.invokeLater(resize);
							}
						});
					}
					if (getEditor().getRightComponent() != null) {
						getEditor().getRightComponent().addComponentListener(new ComponentAdapter() {
							@Override
							public void componentShown(ComponentEvent e) {
								if (getEditor().getLeftComponent() == null || !getEditor().getLeftComponent().isVisible())
									return;
								EventQueue.invokeLater(resize);
							}
						});
					}
					getEditor().addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
						if (callbackLock[0] || !hasConfiguredDL[0])
							return;
						if (theSplitOnChange != null) {
							if (!theSplitOnChange.test(((Number) evt.getNewValue()).intValue(), size.getAsInt()))
								EventQueue.invokeLater(resize);
						}
					});
					if (theSplitChanges != null)
						theSplitChanges.takeUntil(getUntil()).act(__ -> resize.run());
			} else {
				EventQueue.invokeLater(() -> {
					Component first = getEditor().getLeftComponent();
					Component last = getEditor().getRightComponent();
					boolean vertical = getEditor().getOrientation() == JSplitPane.VERTICAL_SPLIT;
					int firstSz = vertical ? first.getPreferredSize().height : first.getPreferredSize().width;
					int lastSz = vertical ? last.getPreferredSize().height : last.getPreferredSize().width;
					getEditor().setDividerLocation(firstSz * 1.0 / (firstSz + lastSz));
				});
			}
			return getEditor();
		}
	}

	static class SimpleScrollEditor<P extends SimpleScrollEditor<P>> extends SimpleFieldEditor<JScrollPane, P>
	implements PanelPopulation.ScrollPane<P> {
		private final FixedScrollPane theFixedScroll;
		private boolean isContentSet;

		public SimpleScrollEditor(String fieldName, Observable<?> until) {
			super(fieldName, new JScrollPane(), until);
			theFixedScroll = new FixedScrollPane(getEditor());
			getEditor().getVerticalScrollBar().setUnitIncrement(10);
			getEditor().getHorizontalScrollBar().setUnitIncrement(10);
		}

		@Override
		public P withVContent(Consumer<PanelPopulator<?, ?>> panel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, null, getUntil());
			panel.accept(fieldPanel);
			withContent(fieldPanel.getComponent());
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			SimpleHPanel<JPanel, ?> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
			panel.accept(hPanel);
			withContent(hPanel.getComponent());
			if (hPanel.isVisible() != null)
				hPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(hPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withContent(Component component) {
			if (isContentSet)
				throw new IllegalStateException("Content has already been configured");
			isContentSet = true;
			getEditor().setViewportView(component);
			return (P) this;
		}

		@Override
		protected Component createComponent() {
			return theFixedScroll.getLayer();
		}

		@Override
		public P withVRowHeader(Consumer<PanelPopulator<?, ?>> panel) {
			MigFieldPanel<JPanel, ?> fieldPanel = new MigFieldPanel<>(null, null, getUntil());
			panel.accept(fieldPanel);
			withRowHeader(fieldPanel.getComponent());
			if (fieldPanel.isVisible() != null)
				fieldPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(fieldPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withHRowHeader(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			SimpleHPanel<JPanel, ?> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
			panel.accept(hPanel);
			withRowHeader(hPanel.getComponent());
			if (hPanel.isVisible() != null)
				hPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(hPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withRowHeader(Component component) {
			getEditor().setRowHeaderView(component);
			return (P) this;
		}

		@Override
		public P withHColumnHeader(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			SimpleHPanel<JPanel, ?> hPanel = new SimpleHPanel<>(null, new ConformingPanel(layout), getUntil());
			panel.accept(hPanel);
			withColumnHeader(hPanel.getComponent());
			if (hPanel.isVisible() != null)
				hPanel.isVisible().changes().takeUntil(getUntil()).act(new VizChanger(hPanel.getComponent(), null));
			return (P) this;
		}

		@Override
		public P withColumnHeader(Component component) {
			getEditor().setColumnHeaderView(component);
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
		private SimpleHPanel<JPanel, ?> theHeaderPanel;
		private PanelPopulator<JPanel, ?> theExposedHeaderPanel;
		private final SettableValue<Boolean> theInternalCollapsed;

		private Icon theCollapsedIcon;
		private Icon theExpandedIcon;

		private SettableValue<Boolean> isCollapsed;

		SimpleCollapsePane(JXCollapsiblePane cp, Observable<?> until, boolean vertical, LayoutManager layout) {
			super(null, (JXPanel) cp.getContentPane(), until);
			theCollapsePane = cp;
			theCollapsePane.setLayout(layout);
			theCollapsePane.getContentPane().setLayout(layout);
			if (vertical)
				theContentPanel = new MigFieldPanel<>(null, getEditor(), getUntil());
			else
				theContentPanel = new SimpleHPanel<>(null, getEditor(), getUntil());
			theOuterContainer = new SimpleHPanel<>(null, new ConformingPanel(new CollapsePaneOuterLayout()), until);
			theHeaderPanel = new SimpleHPanel<>(null, new ConformingPanel(new BorderLayout()), until);

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
		public SimpleCollapsePane withGlassPane(LayoutManager layout, Consumer<PanelPopulator<?, ?>> panel) {
			return super.withGlassPane(layout, panel);
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
		protected Component createComponent() {
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

			ListItemAction(String actionName, Consumer<? super List<? extends R>> action, Supplier<List<R>> selectedValues,
				Observable<?> until) {
				super(actionName, SimpleListBuilder.this, action, selectedValues, false, until);
				theActionItems = new ArrayList<>();
				displayAsPopup(false);
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
		private ObservableValue<String> theDisablement;
		private List<ListItemAction<?>> theActions;

		SimpleListBuilder(ObservableCollection<R> rows, Observable<?> until) {
			super(null, new LittleList<>(new ObservableListModel<>(rows)), until);
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

		@Override
		public P withActionsOnTop(boolean actionsOnTop) {
			System.err.println("List.actionsOnTop unsupported");
			return (P) this;
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
		public P disableWith(ObservableValue<String> disabled) {
			if (theDisablement == null)
				theDisablement = disabled;
			else
				theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement,
				disabled);
			return (P) this;
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
			tableAction[0] = new ListItemAction<>(actionName, action, () -> tableAction[0].theActionItems, getUntil());
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
		protected Component createComponent() {
			ObservableListModel<R> model = getEditor().getModel();
			if (theSelectionValue != null)
				ObservableSwingUtils.syncSelection(getEditor(), model, getEditor()::getSelectionModel, model.getWrapped().equivalence(),
					theSelectionValue, getUntil(), (index, cause) -> {
						MutableCollectionElement<R> el = (MutableCollectionElement<R>) getRows()
							.mutableElement(getRows().getElement(index).getElementId());
						if (el.isAcceptable(el.get()) == null) {
							try (Transaction t = getRows().lock(true, cause)) {
								el.set(el.get());
							}
						}
					}, false);
			ObservableListSelectionModel<R> selectionModel = new ObservableListSelectionModel<>(model, null, getUntil());
			getEditor().setSelectionModel(selectionModel);
			if (theSelectionValues != null) {
				Subscription syncSub = ObservableCollectionSynchronization.synchronize(theSelectionValues, selectionModel).strictOrder()
					.synchronize();
				getUntil().take(1).act(__ -> syncSub.unsubscribe());
			}
			if (theDisablement != null) {
				theDisablement.changes().takeUntil(getUntil()).act(evt -> {
					// Let's not worry about tooltip here. We could mess up cell tooltips and stuff.
					getEditor().setEnabled(evt.getNewValue() == null);
				});
			}

			if (theActions != null) {
				for (ListItemAction<?> action : theActions) {
					if (action.isAllowedForEmpty() || action.isAllowedForMultiple()) {
						System.err.println("Multi actions not supported yet");
					} else
						getEditor().addItemAction(itemActionFor(action));
				}
			}

			return decorate(new ScrollPaneLite(getEditor()));
		}

		private LittleList.ItemAction<R> itemActionFor(ListItemAction<?> tableAction) {
			class ItemAction<P2 extends ItemAction<P2>> implements LittleList.ItemAction<R>, ButtonEditor<JButton, P2> {
				private Action theAction;
				private boolean isEnabled;
				private ComponentDecorator theDecorator;

				@Override
				public Observable<?> getUntil() {
					return SimpleListBuilder.this.getUntil();
				}

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
					s = tableAction.isEnabled();
					if (s != null) {
						action.setEnabled(false);
						action.putValue(Action.LONG_DESCRIPTION, s);
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
					String s = tableAction.isEnabled();
					if (s != null)
						isEnabled = false;
					if (isEnabled)
						tableAction.theObservableAction.act(cause);
				}

				@Override
				public P2 withFieldName(ObservableValue<String> fieldName) {
					return (P2) this;
				}

				@Override
				public P2 withPostLabel(ObservableValue<String> postLabel) {
					return (P2) this;
				}

				@Override
				public P2 withPostButton(String buttonText, ObservableAction action, Consumer<ButtonEditor<JButton, ?>> modify) {
					return (P2) this;
				}

				@Override
				public P2 withTooltip(ObservableValue<String> tooltip) {
					if (theAction != null && theAction.isEnabled())
						theAction.putValue(Action.LONG_DESCRIPTION, tooltip.get());
					return (P2) this;
				}

				@Override
				public P2 modifyFieldLabel(Consumer<FontAdjuster> font) {
					return (P2) this;
				}

				@Override
				public P2 withFont(Consumer<FontAdjuster> font) {
					if (theAction != null)
						theAction.putValue("font", font);
					return (P2) this;
				}

				@Override
				public P2 withPopupMenu(Consumer<MenuBuilder<JPopupMenu, ?>> menu) {
					System.err.println("Popup menu is not supported on a list action");
					return (P2) this;
				}

				@Override
				public JButton getEditor() {
					throw new UnsupportedOperationException();
				}

				@Override
				public P2 visibleWhen(ObservableValue<Boolean> visible) {
					boolean vis = visible == null || visible.get();
					isEnabled &= vis;
					if (theAction != null)
						theAction.putValue("visible", vis);
					return (P2) this;
				}

				@Override
				public P2 withLayoutConstraints(Object constraints) {
					return (P2) this;
				}

				@Override
				public P2 fill() {
					return (P2) this;
				}

				@Override
				public P2 fillV() {
					return (P2) this;
				}

				@Override
				public P2 decorate(Consumer<ComponentDecorator> decoration) {
					if (theDecorator == null)
						theDecorator = new ComponentDecorator();
					decoration.accept(theDecorator);
					return (P2) this;
				}

				@Override
				public Component decorate(Component c) {
					if (theDecorator != null)
						theDecorator.decorate(c);
					return c;
				}

				@Override
				public P2 repaintOn(Observable<?> repaint) {
					System.err.println("repaintOn() is not implemented for list action");
					return (P2) this;
				}

				@Override
				public P2 modifyComponent(Consumer<Component> component) {
					return (P2) this; // TODO can we support this?
				}

				@Override
				public P2 modifyEditor(Consumer<? super JButton> modify) {
					return (P2) this;
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
				public P2 onMouse(Consumer<MouseEvent> onMouse) {
					throw new UnsupportedOperationException();
				}

				@Override
				public P2 withText(ObservableValue<String> text) {
					if (theAction != null)
						theAction.putValue(Action.NAME, text.get());
					return (P2) this;
				}

				@Override
				public P2 withIcon(ObservableValue<? extends Icon> icon) {
					if (theAction != null)
						theAction.putValue(Action.SMALL_ICON, icon == null ? null : icon.get());
					return (P2) this;
				}

				@Override
				public P2 disableWith(ObservableValue<String> disabled) {
					String msg = disabled.get();
					isEnabled = isEnabled && (msg != null);
					if (theAction != null) {
						theAction.setEnabled(msg == null);
						if (msg != null)
							theAction.putValue(Action.LONG_DESCRIPTION, msg);
					}
					return (P2) this;
				}

				@Override
				public P2 withName(String name) {
					return (P2) this;
				}
			}
			return new ItemAction<>();
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
		private boolean isButton;
		private boolean isPopup;
		private boolean zeroAllowed;
		private boolean multipleAllowed;
		private boolean actWhenAnyEnabled;
		private boolean isDisplayedWhenDisabled;
		ObservableAction theObservableAction;
		SettableValue<String> theEnabledString;
		SettableValue<String> theTooltipString;
		Consumer<ButtonEditor<?, ?>> theButtonMod;
		Observable<?> theUntil;

		SimpleDataAction(String actionName, CollectionWidgetBuilder<R, ?, ?> widget, Consumer<? super List<? extends R>> action,
			Supplier<List<R>> selectedValues, boolean defaultAsButton, Observable<?> until) {
			theWidget = widget;
			theActionName = actionName;
			theAction = action;
			theSelectedValues = selectedValues;
			theUntil = until;
			theEnabledString = SettableValue.build(String.class).build();
			theObservableAction = new ObservableAction() {
				@Override
				public void act(Object cause) throws IllegalStateException {
					List<R> selected = theSelectedValues.get();
					if (theEnablement != null)
						selected = QommonsUtils.filterMap(selected, v -> theEnablement.apply(v) == null, null);
					theAction.accept(selected);
					updateSelection(getActionItems(), cause);
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return theEnabledString;
				}

				@Override
				public String toString() {
					return action.toString();
				}
			};
			isDisplayedWhenDisabled = true;
			multipleAllowed = true;
			isButton = defaultAsButton;
			isPopup = !defaultAsButton;
			EventQueue.invokeLater(() -> {
				updateSelection(selectedValues.get(), null);
			});
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
		public A displayAsButton(boolean button) {
			isButton = button;
			return (A) this;
		}

		@Override
		public A displayAsPopup(boolean popup) {
			isPopup = popup;
			return (A) this;
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
		public A disableWith(ObservableValue<String> disabled) {
			theObservableAction = theObservableAction.disableWith(disabled);
			disabled.noInitChanges().takeUntil(theUntil).act(evt -> refreshEnabled(evt));
			return (A) this;
		}

		private void refreshEnabled(Object cause) {
			List<R> selected = theSelectedValues.get();
			updateSelection(selected, cause);
		}

		@Override
		public A withTooltip(Function<? super List<? extends R>, String> tooltip) {
			theTooltip = tooltip;
			if (tooltip != null)
				theTooltipString = SettableValue.build(String.class).build();
			return (A) this;
		}

		@Override
		public A modifyAction(Function<? super ObservableAction, ? extends ObservableAction> actionMod) {
			theObservableAction = actionMod.apply(theObservableAction);
			return (A) this;
		}

		@Override
		public A confirm(String alertTitle, Function<List<? extends R>, String> alertText, boolean confirmType) {
			return modifyAction(action -> {
				return new ObservableAction() {
					@Override
					public void act(Object cause) throws IllegalStateException {
						List<? extends R> selected = theWidget.getSelection();
						String text = alertText.apply(selected);
						if (theWidget.alert(alertTitle, text).confirm(confirmType))
							action.act(cause);
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
				return new ObservableAction() {
					@Override
					public void act(Object cause) throws IllegalStateException {
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
						if (theWidget.alert(alertTitle, text.toString()).confirm(confirmType))
							action.act(cause);
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return action.isEnabled();
					}
				};
			});
		}

		@Override
		public boolean isButton() {
			return isButton;
		}

		@Override
		public boolean isPopup() {
			return isPopup;
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
			panel.addButton(theActionName, theObservableAction, this::modifyButtonEditor);
		}

		void modifyButtonEditor(ButtonEditor<?, ?> button) {
			button.withText(theActionName);
			if (theTooltipString != null)
				button.withTooltip(theTooltipString);
			if (theButtonMod != null)
				theButtonMod.accept(button);
			if (!isDisplayedWhenDisabled)
				button.visibleWhen(theObservableAction.isEnabled().map(e -> e == null));
		}
	}

	enum AlertType {
		PLAIN, INFO, WARNING, ERROR;
	}

	static class SwitchableFilterValue<T> extends AbstractIdentifiable implements SettableValue<T> {
		private final SettableValue<T> theWrapped;
		private Function<? super T, String> theFilter;

		SwitchableFilterValue(TypeToken<T> type, Function<? super T, String> filter) {
			theWrapped = SettableValue.build(type).build();
			theFilter = filter;
		}

		public SwitchableFilterValue<T> setFilter(Function<? super T, String> filter) {
			theFilter = filter;
			return this;
		}

		@Override
		protected Object createIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public T get() {
			return theWrapped.get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theWrapped.noInitChanges();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (theFilter != null) {
				String msg = theFilter.apply(value);
				if (StdMsg.UNSUPPORTED_OPERATION.equals(msg))
					throw new UnsupportedOperationException(msg);
				else if (msg != null)
					throw new IllegalArgumentException(msg);
			}
			return theWrapped.set(value, cause);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			String msg = theFilter == null ? null : theFilter.apply(value);
			if (msg == null)
				msg = theWrapped.isAcceptable(value);
			return msg;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theWrapped.isEnabled();
		}
	}
	static ObservableColorEditor COLOR_EDITOR;
	static JLabel COLOR_EDITING_MESSAGE;

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
			return result == JOptionPane.OK_OPTION;
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
				panel.addTextField(null, value, format, f -> f.fill().modifyEditor(tf -> {
					field.accept(tf);
					tf.onEnter((v, ke) -> {
						if (tf.getErrorState().get() == null) {
							provided[0] = true;
							dialog.getWindow().setVisible(false);
						}
					});
					tf.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent e) {
							if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
								dialog.getWindow().setVisible(false);
						}
					});
				}));
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

		@Override
		public Color inputColor(boolean withAlpha, Color initial, Function<Color, String> filter) {
			if (COLOR_EDITOR == null) {
				SwitchableFilterValue<Color> selected = new SwitchableFilterValue<>(TypeTokens.get().of(Color.class), filter);
				selected.set(initial, null);
				COLOR_EDITOR = new ObservableColorEditor(selected, true, SettableValue.build(boolean.class).withValue(withAlpha).build(),
					Observable.empty());
				COLOR_EDITING_MESSAGE = new JLabel(theMessage, theImage == null ? null : theImage.getIcon().get(), JLabel.LEADING);
				new FontAdjuster().withFontSize(16).adjust(COLOR_EDITING_MESSAGE);
			} else {
				((SwitchableFilterValue<Color>) COLOR_EDITOR.getSelectedColor()).setFilter(filter);
				((SettableValue<Boolean>) COLOR_EDITOR.isWithAlpha()).set(withAlpha, null);
				COLOR_EDITOR.clearLocalHistory();
				COLOR_EDITOR.addPersistentHistory(initial);
				COLOR_EDITOR.getSelectedColor().set(initial, null);
				COLOR_EDITING_MESSAGE.setText(theMessage);
				COLOR_EDITING_MESSAGE.setIcon(theImage == null ? null : theImage.getIcon().get());
			}
			boolean[] accept = new boolean[1];
			Runnable setup = () -> {
				JDialog dialog = new JDialog(theComponent == null ? null : SwingUtilities.getWindowAncestor(theComponent),
					JDialog.ModalityType.APPLICATION_MODAL);
				dialog.addComponentListener(new ComponentAdapter() {
					@Override
					public void componentHidden(ComponentEvent e) {
						dialog.getContentPane().removeAll();
					}
				});
				dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
				dialog.setTitle(theTitle);
				dialog.getContentPane().setLayout(new JustifiedBoxLayout(true).mainJustified().crossCenter());
				dialog.getContentPane().add(COLOR_EDITING_MESSAGE);
				dialog.getContentPane().add(COLOR_EDITOR);
				JPanel buttonPanel = new JPanel(new JustifiedBoxLayout(false).mainCenter());
				JButton ok = new JButton("OK");
				ok.addActionListener(evt -> {
					accept[0] = true;
					dialog.setVisible(false);
				});
				buttonPanel.add(ok);
				JButton cancel = new JButton("Cancel");
				cancel.addActionListener(evt -> dialog.setVisible(false));
				buttonPanel.add(cancel);
				dialog.getContentPane().add(buttonPanel);
				dialog.pack();
				dialog.setLocationRelativeTo(theComponent);
				dialog.setVisible(true);
			};
			if (EventQueue.isDispatchThread())
				setup.run();
			else {
				try {
					EventQueue.invokeAndWait(setup);
				} catch (InvocationTargetException e) {
					e.getTargetException().printStackTrace();
					return null;
				} catch (InterruptedException e) {
					e.printStackTrace();
					return null;
				}
			}
			return accept[0] ? COLOR_EDITOR.getSelectedColor().get() : null;
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

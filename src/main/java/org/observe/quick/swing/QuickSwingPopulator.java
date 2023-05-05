package org.observe.quick.swing;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.IllegalComponentStateException;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickBorder;
import org.observe.quick.QuickCore;
import org.observe.quick.QuickDocument2;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickTextElement.QuickTextStyle;
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWidget.Interpreted;
import org.observe.quick.QuickWindow;
import org.observe.quick.base.*;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.ComponentDecorator;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.ContainerPopulator;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.Transformer.Builder;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExRunnable;
import org.qommons.ex.ExTriConsumer;
import org.qommons.io.Format;

/**
 * <p>
 * Populates a {@link PanelPopulator} for a Quick widget.
 * </p>
 * <p>
 * This class contains lots of {@link Transformer} interpretation utilities for turning standard Quick libraries into Java Swing components.
 * </p>
 *
 * @param <W> The type of the Quick widget
 */
public interface QuickSwingPopulator<W extends QuickWidget> {
	/**
	 * @param panel The panel to populate
	 * @param quick The Quick widget to populate for
	 * @throws ModelInstantiationException If an problem occurs instantiating any components
	 */
	void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

	void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify);

	public abstract class Abstract<W extends QuickWidget> implements QuickSwingPopulator<W> {
		private final QuickWidget.Interpreted<? extends W> theInterpreted;
		private final List<ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException>> theModifiers;

		public Abstract(QuickWidget.Interpreted<? extends W> interpreted) {
			theInterpreted = interpreted;
			theModifiers = new LinkedList<>();
		}

		public QuickWidget.Interpreted<? extends W> getInterpreted() {
			return theInterpreted;
		}

		protected abstract void doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		public void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populate(panel, quick, () -> doPopulate(panel, quick));
		}

		protected <P extends ContainerPopulator<?, ?>> void populate(P panel, W quick, ExRunnable<ModelInstantiationException> populate)
			throws ModelInstantiationException {
			List<Consumer<ComponentEditor<?, ?>>> modifiers = new ArrayList<>(theModifiers.size());
			for (ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modifier : theModifiers) {
				Consumer<ComponentEditor<?, ?>> populationModifier = comp -> {
					try {
						modifier.accept(comp, quick);
					} catch (ModelInstantiationException e) {
						throw new CheckedExceptionWrapper(e);
					}
				};
				modifiers.add(populationModifier);
				panel.addModifier(populationModifier);
			}

			try {
				populate.run();
			} catch (CheckedExceptionWrapper e) {
				if (e.getCause() instanceof ModelInstantiationException)
					throw (ModelInstantiationException) e.getCause();
				throw e;
			} finally {
				for (Consumer<ComponentEditor<?, ?>> modifier : modifiers)
					panel.removeModifier(modifier);
			}
		}

		@Override
		public void addModifier(ExBiConsumer<ComponentEditor<?, ?>, ? super W, ModelInstantiationException> modify) {
			theModifiers.add(modify);
		}
	}

	public interface QuickSwingContainerPopulator<W extends QuickWidget> extends QuickSwingPopulator<W> {
		void populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

		@Override
		default void populate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
			populateContainer(panel, quick);
		}

		public abstract class Abstract<W extends QuickWidget> extends QuickSwingPopulator.Abstract<W>
		implements QuickSwingContainerPopulator<W> {
			public Abstract(Interpreted<? extends W> interpreted) {
				super(interpreted);
			}

			protected abstract void doPopulateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException;

			@Override
			public void populateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				populate(panel, quick, //
					() -> doPopulateContainer(panel, quick));
			}

			@Override
			protected void doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				doPopulateContainer(panel, quick);
			}
		}
	}

	/**
	 * Creates a Swing layout from a {@link QuickLayout}
	 *
	 * @param <L> The type of the Quick layout
	 */
	public interface QuickSwingLayout<L extends QuickLayout> {
		/**
		 * @param quick The Quick layout to create the layout for
		 * @return The swing layout interpretation of the Quick layout
		 * @throws ModelInstantiationException If a problem occurs instantiating the layout
		 */
		LayoutManager create(L quick) throws ModelInstantiationException;

		void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException;
	}

	public interface QuickSwingBorder {
		void decorate(ComponentDecorator deco, QuickBorder border, Component[] component) throws ModelInstantiationException;
	}

	/** Quick interpretation of the core toolkit for Swing */
	public class QuickCoreSwing implements QuickInterpretation {
		@Override
		public void configure(Builder<ExpressoInterpretationException> tx) {
			initMouseListening();
			tx.with(QuickDocument2.Interpreted.class, QuickApplication.class, (interpretedDoc, tx2) -> {
				QuickSwingPopulator<QuickWidget> interpretedBody = tx2.transform(interpretedDoc.getBody(), QuickSwingPopulator.class);
				return doc -> {
					try {
						EventQueue.invokeAndWait(() -> {
							QuickWindow window = doc.getAddOn(QuickWindow.class);
							WindowBuilder<?, ?> w = WindowPopulation.populateWindow(new JFrame(), doc.getModels().getUntil(), true, true);
							if (window != null) {
								switch (window.getInterpreted().getDefinition().getCloseAction()) {
								case DoNothing:
									w.withCloseAction(JFrame.DO_NOTHING_ON_CLOSE);
									break;
								case Hide:
									w.withCloseAction(JFrame.HIDE_ON_CLOSE);
									break;
								case Dispose:
									w.withCloseAction(JFrame.DISPOSE_ON_CLOSE);
									break;
								case Exit:
									w.withCloseAction(JFrame.EXIT_ON_CLOSE);
									break;
								}
								if (window.getX() != null)
									w.withX(window.getX());
								if (window.getY() != null)
									w.withY(window.getY());
								if (window.getWidth() != null)
									w.withWidth(window.getWidth());
								if (window.getHeight() != null)
									w.withHeight(window.getHeight());
								if (window.getTitle() != null)
									w.withTitle(window.getTitle());
								if (window.getVisible() != null)
									w.withVisible(window.getVisible());
							}
							w.withHContent(new JustifiedBoxLayout(true).mainJustified().crossJustified(), content -> {
								try {
									interpretedBody.populate(content, doc.getBody());
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
							w.run(null);
						});
					} catch (InterruptedException e) {
					} catch (InvocationTargetException e) {
						if (e.getTargetException() instanceof CheckedExceptionWrapper
							&& e.getTargetException().getCause() instanceof ModelInstantiationException)
							throw (ModelInstantiationException) e.getTargetException().getCause();
						System.err.println("Unhandled error");
						e.printStackTrace();
					} catch (RuntimeException | Error e) {
						System.err.println("Unhandled error");
						e.printStackTrace();
					}
				};
			});
			modifyForWidget(tx, QuickWidget.Interpreted.class, (qw, qsp, tx2) -> {
				QuickSwingBorder border = tx2.transform(qw.getBorder(), QuickSwingBorder.class);
				qsp.addModifier((comp, w) -> {
					ComponentDecorator deco = new ComponentDecorator();
					Runnable[] revert = new Runnable[1];
					Component[] component = new Component[1];
					try {
						comp.modifyComponent(c -> {
							revert[0] = deco.decorate(c);
							component[0] = c;
							try {
								w.setContext(new QuickWidget.WidgetContext.Default(//
									new MouseValueSupport(c, "hovered", null), //
									new FocusSupport(c), //
									new MouseValueSupport(c, "pressed", true), //
									new MouseValueSupport(c, "rightPressed", false)));
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
						if (w.getTooltip() != null)
							comp.withTooltip(w.getTooltip());
						if (w.isVisible() != null)
							comp.visibleWhen(w.isVisible());
						if (border != null) {
							comp.decorate(deco2 -> {
								try {
									border.decorate(deco2, w.getBorder(), component);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
						}
					} catch (CheckedExceptionWrapper e) {
						if (e.getCause() instanceof ModelInstantiationException)
							throw (ModelInstantiationException) e.getCause();
						throw e;
					}
					adjustFont(deco, w.getStyle());
					ObservableValue<Color> color = w.getStyle().getColor();
					deco.withBackground(color.get());
					Observable.or(color.noInitChanges(), fontChanges(w.getStyle())).act(__ -> {
						adjustFont(deco.reset(), w.getStyle());
						System.out.println(w + " font=" + deco.getForeground());
						deco.withBackground(color.get());
						if (component[0] != null) {
							revert[0].run();
							revert[0] = deco.decorate(component[0]);
							component[0].repaint();
						}
					});
				});
			});
			tx.with(QuickBorder.LineBorder.Interpreted.class, QuickSwingBorder.class, (iBorder, tx2) -> {
				return (deco, border, component) -> {
					ObservableValue<Color> color = border.getStyle().getBorderColor().map(c -> c != null ? c : Color.black);
					ObservableValue<Integer> thick = border.getStyle().getBorderThickness().map(t -> t != null ? t : 1);
					deco.withLineBorder(color.get(), thick.get(), false);
					Observable.or(color.noInitChanges(), thick.noInitChanges()).act(__ -> {
						deco.withLineBorder(color.get(), thick.get(), false);
						if (component[0] != null)
							component[0].repaint();
					});
				};
			});
			tx.with(QuickBorder.TitledBorder.Interpreted.class, QuickSwingBorder.class, (iBorder, tx2) -> {
				return (deco, border, component) -> {
					QuickBorder.TitledBorder titled = (QuickBorder.TitledBorder) border;
					ObservableValue<Color> color = titled.getStyle().getBorderColor().map(c -> c != null ? c : Color.black);
					ObservableValue<Integer> thick = titled.getStyle().getBorderThickness().map(t -> t != null ? t : 1);
					ObservableValue<String> title = titled.getTitle();
					Runnable[] revert = new Runnable[1];
					FontAdjuster font = new FontAdjuster();
					adjustFont(font, titled.getStyle());
					revert[0] = deco.withTitledBorder(title.get(), color.get(), font);
					Observable.or(color.noInitChanges(), thick.noInitChanges(), title.noInitChanges(), fontChanges(titled.getStyle()))
					.act(__ -> {
						revert[0].run();
						adjustFont(font.reset(), titled.getStyle());
						revert[0] = deco.withTitledBorder(title.get(), color.get(), font);
						// This call will just modify the thickness of the titled border
						deco.withLineBorder(color.get(), thick.get(), false);
						if (component[0] != null)
							component[0].repaint();
					});
				};
			});
		}

		static void adjustFont(FontAdjuster font, QuickTextStyle style) {
			Color color = style.getFontColor().get();
			if (color != null)
				font.withForeground(color);
			Double size = style.getFontSize().get();
			if (size != null)
				font.withFontSize(size.floatValue());
			Double weight = style.getFontWeight().get();
			if (weight != null)
				font.withFontWeight(weight.floatValue());
			Double slant = style.getFontSlant().get();
			if (slant != null)
				font.withFontSlant(slant.floatValue());

		}

		static Observable<?> fontChanges(QuickTextStyle style) {
			return Observable.or(style.getFontColor().noInitChanges(), style.getFontSize().noInitChanges(),
				style.getFontWeight().noInitChanges(), style.getFontSlant().noInitChanges());
		}

		private static boolean isMouseListening;
		private static volatile Point theMouseLocation;
		private static volatile boolean isLeftPressed;
		private static volatile boolean isRightPressed;

		private void initMouseListening() {
			if (isMouseListening)
				return;
			synchronized (QuickCore.class) {
				if (isMouseListening)
					return;
				Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
					MouseEvent mouse = (MouseEvent) event;
					theMouseLocation = mouse.getLocationOnScreen();
					switch (mouse.getID()) {
					case MouseEvent.MOUSE_PRESSED:
						isLeftPressed |= SwingUtilities.isLeftMouseButton(mouse);
						isRightPressed |= SwingUtilities.isRightMouseButton(mouse);
						break;
					case MouseEvent.MOUSE_RELEASED:
						if (SwingUtilities.isLeftMouseButton(mouse))
							isLeftPressed = false;
						if (SwingUtilities.isRightMouseButton(mouse))
							isRightPressed = false;
						break;
					}
				}, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
				isMouseListening = true;
			}
		}

		static class ComponentIdentity {
			private final Component theComponent;

			ComponentIdentity(Component component) {
				theComponent = component;
			}

			@Override
			public int hashCode() {
				return theComponent.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ComponentIdentity && theComponent.equals(((ComponentIdentity) obj).theComponent);
			}

			@Override
			public String toString() {
				String name = theComponent.getName();
				if (name != null)
					return theComponent.getClass().getSimpleName() + ":" + name;
				else
					return theComponent.getClass().getSimpleName();
			}
		}

		static class MouseValueSupport extends ObservableValue.LazyObservableValue<Boolean>
		implements SettableValue<Boolean>, MouseListener {
			private final Component theComponent;
			private final String theName;
			private final Boolean theButton;
			private BiConsumer<Boolean, Object> theListener;
			private boolean isListening;

			public MouseValueSupport(Component component, String name, Boolean button) {
				super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
				theComponent = component;
				theName = name;
				theButton = button;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(new ComponentIdentity(theComponent), theName);
			}

			@Override
			protected Boolean getSpontaneous() {
				if (theComponent == null)
					return false;
				boolean compVisible;
				if (theComponent instanceof JComponent)
					compVisible = ((JComponent) theComponent).isShowing();
				else
					compVisible = theComponent.isVisible();
				if (!compVisible)
					return false;
				if (theButton == null) { // No button filter
				} else if (theButton.booleanValue()) { // Left
					if (!isLeftPressed)
						return false;
				} else { // Right
					if (!isRightPressed)
						return false;
				}
				Point screenPos;
				try {
					screenPos = theComponent.getLocationOnScreen();
				} catch (IllegalComponentStateException e) {
					return false;
				}
				if (screenPos == null)
					return false;
				Point mousePos = theMouseLocation;
				if (mousePos == null || mousePos.x < screenPos.x || mousePos.y < screenPos.y)
					return false;
				if (mousePos.x >= screenPos.x + theComponent.getWidth() || mousePos.y >= screenPos.y + theComponent.getHeight())
					return false;
				Component child = theComponent.getComponentAt(mousePos.x - screenPos.x, mousePos.y - screenPos.y);
				return child == null || !child.isVisible();
			}

			@Override
			protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
				theListener = listener;
				setListening(true);
				return () -> setListening(false);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public boolean isLockSupported() {
				return false;
			}

			@Override
			public Boolean set(Boolean value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String isAcceptable(Boolean value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_DISABLED;
			}

			private void setListening(boolean listening) {
				if (listening == isListening)
					return;
				if (listening && theListener == null)
					return;
				isListening = listening;
				if (listening)
					theComponent.addMouseListener(this);
				else if (theComponent != null)
					theComponent.removeMouseListener(this);
				if (!listening)
					theListener = null;
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (theListener == null)
					return;
				if (theButton == null) { // No button filter
					return;
				} else if (theButton.booleanValue()) { // Left
					if (!SwingUtilities.isLeftMouseButton(e))
						return;
				} else { // Right
					if (!SwingUtilities.isRightMouseButton(e))
						return;
				}
				theListener.accept(true, e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (theListener == null)
					return;
				if (theButton == null) { // No button filter
					return;
				} else if (theButton.booleanValue()) { // Left
					if (!SwingUtilities.isLeftMouseButton(e))
						return;
				} else { // Right
					if (!SwingUtilities.isRightMouseButton(e))
						return;
				}
				theListener.accept(false, e);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				if (theListener == null)
					return;
				if (theButton == null) { // No button filter
				} else if (theButton.booleanValue()) { // Left
					if (!isLeftPressed)
						return;
				} else { // Right
					if (!isRightPressed)
						return;
				}
				theListener.accept(true, e);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (theListener == null)
					return;
				if (theButton == null) { // No button filter
				} else if (theButton.booleanValue()) { // Left
					if (!isLeftPressed)
						return;
				} else { // Right
					if (!isRightPressed)
						return;
				}
				theListener.accept(false, e);
			}

			@Override
			public void mouseClicked(MouseEvent e) { // No state change due to clicked
			}
		}

		class FocusSupport extends ObservableValue.LazyObservableValue<Boolean> implements SettableValue<Boolean>, FocusListener {
			private final Component theComponent;
			private BiConsumer<Boolean, Object> theListener;
			private boolean isListening;

			FocusSupport(Component component) {
				super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
				theComponent = component;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(theComponent, "focused");
			}

			@Override
			protected Boolean getSpontaneous() {
				return theComponent.isFocusOwner();
			}

			@Override
			protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
				theListener = listener;
				setListening(true);
				return () -> setListening(false);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public boolean isLockSupported() {
				return false;
			}

			@Override
			public Boolean set(Boolean value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String isAcceptable(Boolean value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_DISABLED;
			}

			private void setListening(boolean listening) {
				if (listening == isListening)
					return;
				if (listening && theListener == null)
					return;
				isListening = listening;
				if (listening)
					theComponent.addFocusListener(this);
				else if (theComponent != null)
					theComponent.removeFocusListener(this);
				if (!listening)
					theListener = null;
			}

			@Override
			public void focusGained(FocusEvent e) {
				if (theListener != null)
					theListener.accept(true, e);
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (theListener != null)
					theListener.accept(false, e);
			}
		}

	}

	/** Quick interpretation of the base toolkit for Swing */
	public class QuickBaseSwing implements QuickInterpretation {
		@Override
		public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
			// Simple widgets
			QuickSwingPopulator.<QuickLabel<?>, QuickLabel.Interpreted<?, ?>> interpretWidget(tx,
				QuickBaseSwing.gen(QuickLabel.Interpreted.class), QuickBaseSwing::interpretLabel);
			QuickSwingPopulator.<QuickTextField<?>, QuickTextField.Interpreted<?>> interpretWidget(tx,
				QuickBaseSwing.gen(QuickTextField.Interpreted.class), QuickBaseSwing::interpretTextField);
			QuickSwingPopulator.<QuickCheckBox, QuickCheckBox.Interpreted> interpretWidget(tx,
				QuickBaseSwing.gen(QuickCheckBox.Interpreted.class), QuickBaseSwing::interpretCheckBox);
			QuickSwingPopulator.<QuickButton, QuickButton.Interpreted> interpretWidget(tx, gen(QuickButton.Interpreted.class),
				QuickBaseSwing::interpretButton);

			// Containers
			QuickSwingPopulator.<QuickBox, QuickBox.Interpreted<?>> interpretContainer(tx, gen(QuickBox.Interpreted.class),
				QuickBaseSwing::interpretBox);
			QuickSwingPopulator.<QuickFieldPanel, QuickFieldPanel.Interpreted> interpretContainer(tx,
				gen(QuickFieldPanel.Interpreted.class), QuickBaseSwing::interpretFieldPanel);
			modifyForAddOn(tx, QuickField.Interpreted.class, (qw, qsp, tx2) -> {
				qsp.addModifier((comp, w) -> {
					if (w.getAddOn(QuickField.class).getName() != null)
						comp.withFieldName(w.getAddOn(QuickField.class).getName());
					if (qw.getDefinition().isFill())
						comp.fill();
				});
			});

			// Box layouts
			tx.with(QuickInlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
			tx.with(QuickSimpleLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretSimpleLayout);

			// Table
			QuickSwingPopulator.<QuickTable<?>, QuickTable.Interpreted<?>> interpretWidget(tx, gen(QuickTable.Interpreted.class),
				QuickBaseSwing::interpretTable);
		}

		static <T> Class<T> gen(Class<? super T> rawClass) {
			return (Class<T>) rawClass;
		}

		static QuickSwingContainerPopulator<QuickBox> interpretBox(QuickBox.Interpreted<?> interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			QuickSwingLayout<QuickLayout> layout = tx.transform(interpreted.getLayout(), QuickSwingLayout.class);
			BetterList<QuickSwingPopulator<QuickWidget>> contents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
			for (QuickSwingPopulator<QuickWidget> content : contents)
				layout.modifyChild(content);
			return createContainer(interpreted, (panel, quick) -> {
				LayoutManager layoutInst = layout.create(quick.getLayout());
				panel.addHPanel(null, layoutInst, p -> {
					int c = 0;
					for (QuickWidget content : quick.getContents()) {
						try {
							contents.get(c).populate(p, content);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
						c++;
					}
				});
			});
		}

		static QuickSwingLayout<QuickInlineLayout> interpretInlineLayout(QuickInlineLayout.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			boolean vertical = interpreted.getDefinition().isVertical();
			return new QuickSwingLayout<QuickInlineLayout>() {
				@Override
				public LayoutManager create(QuickInlineLayout quick) throws ModelInstantiationException {
					return new JustifiedBoxLayout(vertical)//
						.setMainAlignment(interpreted.getDefinition().getMainAlign())//
						.setCrossAlignment(interpreted.getDefinition().getCrossAlign());
				}

				@Override
				public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
				}
			};
		}

		static QuickSwingLayout<QuickSimpleLayout> interpretSimpleLayout(QuickSimpleLayout.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return new QuickSwingLayout<QuickSimpleLayout>() {
				@Override
				public LayoutManager create(QuickSimpleLayout quick) throws ModelInstantiationException {
					return new SimpleLayout();
				}

				@Override
				public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
					child.addModifier((comp, w) -> {
						Component[] component = new Component[1];
						comp.modifyComponent(c -> component[0] = c);
						Positionable h = w.getAddOn(Positionable.Horizontal.class);
						Positionable v = w.getAddOn(Positionable.Vertical.class);
						Sizeable width = w.getAddOn(Sizeable.Horizontal.class);
						Sizeable height = w.getAddOn(Sizeable.Vertical.class);
						SimpleLayout.SimpleConstraints childConstraint = simpleConstraints(h, v, width, height);
						comp.withLayoutConstraints(childConstraint);
						Observable.or(h.changes(), v.changes(), width.changes(), height.changes()).act(evt -> {
							if (component[0].getParent() != null)
								component[0].getParent().invalidate();
						});
					});
				}
			};
		}

		static SimpleLayout.SimpleConstraints simpleConstraints(Positionable h, Positionable v, Sizeable width, Sizeable height) {
			return new SimpleLayout.SimpleConstraints(//
				h.getLeading(), h.getCenter(), h.getTrailing(), //
				v.getLeading(), v.getCenter(), v.getTrailing(), //
				width.getSize(), enforceAbsolute(width.getMinimum()), enforceAbsolute(width.getPreferred()),
				enforceAbsolute(width.getMaximum()), //
				height.getSize(), enforceAbsolute(height.getMinimum()), enforceAbsolute(height.getPreferred()),
				enforceAbsolute(height.getMaximum())//
				);
		}

		static Supplier<Integer> enforceAbsolute(Supplier<QuickSize> size) {
			if (size == null)
				return LambdaUtils.constantSupplier(null, "null", null);
			return LambdaUtils.printableSupplier(() -> {
				QuickSize sz = size.get();
				if (sz == null)
					return null;
				else if (sz.type == QuickSize.SizeUnit.Pixels)
					return Math.round(sz.value);
				else {
					System.err.println("min/pref/max size constraints must be absolute: " + sz);
					return null;
				}
			}, size::toString, null);
		}

		static <T> QuickSwingPopulator<QuickLabel<T>> interpretLabel(QuickLabel.Interpreted<T, ?> interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return QuickSwingPopulator.<QuickLabel<T>, QuickLabel.Interpreted<T, QuickLabel<T>>> createWidget(
				(QuickLabel.Interpreted<T, QuickLabel<T>>) interpreted, (panel, quick) -> {
					Format<T> format = quick.getFormat().get();
					panel.addLabel(null, quick.getValue(), format, null);
				});
		}

		static <T> QuickSwingPopulator<QuickTextField<T>> interpretTextField(QuickTextField.Interpreted<T> interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return createWidget(interpreted, (panel, quick) -> {
				Format<T> format = quick.getFormat().get();
				boolean commitOnType = quick.getInterpreted().getDefinition().isCommitOnType();
				Integer columns = quick.getInterpreted().getDefinition().getColumns();
				panel.addTextField(null, quick.getValue(), format, tf -> {
					tf.modifyEditor(tf2 -> {
						try {
							quick.setContext(new QuickEditableTextWidget.EditableTextWidgetContext.Default(//
								tf2.getErrorState(), tf2.getWarningState()));
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
						if (commitOnType)
							tf2.setCommitOnType(commitOnType);
						if (columns != null)
							tf2.withColumns(columns);
					});
				});
			});
		}

		static QuickSwingPopulator<QuickCheckBox> interpretCheckBox(QuickCheckBox.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return createWidget(interpreted, (panel, quick) -> {
				panel.addCheckField(null, quick.getValue(), null);
			});
		}

		static QuickSwingContainerPopulator<QuickFieldPanel> interpretFieldPanel(QuickFieldPanel.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
			BetterList<QuickSwingPopulator<QuickWidget>> contents = BetterList.<QuickWidget.Interpreted<?>, QuickSwingPopulator<QuickWidget>, ExpressoInterpretationException> of2(
				interpreted.getContents().stream(), content -> tx.transform(content, QuickSwingPopulator.class));
			return createContainer(interpreted, (panel, quick) -> {
				panel.addVPanel(p -> {
					int c = 0;
					for (QuickWidget content : quick.getContents()) {
						try {
							contents.get(c).populate(p, content);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
						c++;
					}
				});
			});
		}

		static QuickSwingPopulator<QuickButton> interpretButton(QuickButton.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return createWidget(interpreted, (panel, quick) -> {
				panel.addButton(null, quick.getAction(), btn -> {
					if (quick.getText() != null)
						btn.withText(quick.getText());
				});
			});
		}

		static <R> QuickSwingPopulator<QuickTable<R>> interpretTable(QuickTable.Interpreted<R> interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return createWidget(interpreted, (panel, quick) -> {
				TabularWidget.TabularContext<R> ctx = new TabularWidget.TabularContext.Default<>(//
					SettableValue.build(quick.getInterpreted().getRowType()).build(), //
					SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(int.class).withValue(0).build(), //
					SettableValue.build(int.class).withValue(0).build()//
					);
				quick.setContext(ctx);
				ObservableCollection<CategoryRenderStrategy<R, ?>> columns = quick.getColumns().flow()//
					.map((Class<CategoryRenderStrategy<R, ?>>) (Class<?>) CategoryRenderStrategy.class, //
						column -> createRenderStrategy(column, ctx.getRenderValue()))//
					.collect();
				panel.addTable(quick.getRows(), table -> {
					table.withColumns(columns);
				});
			});
		}

		static <R, C> CategoryRenderStrategy<R, C> createRenderStrategy(QuickTableColumn<R, C> column, SettableValue<R> rowValue) {
			CategoryRenderStrategy<R, C> crs = new CategoryRenderStrategy<>(column.getName().get(), column.getType(), row -> {
				rowValue.set(row, null);
				return column.getValue().get();
			});
			column.getName().noInitChanges().act(evt -> crs.setName(evt.getNewValue()));
			return crs;
		}
	}

	/**
	 * Utility for interpretation of swing widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param interpreter Produces a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void interpretWidget(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExBiFunction<? super I, Transformer<ExpressoInterpretationException>, ? extends QuickSwingPopulator<? extends W>, ExpressoInterpretationException> interpreter) {
		transformer.with(interpretedType, QuickSwingPopulator.class, interpreter);
	}

	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<W>> QuickSwingPopulator<W> createWidget(I interpreted,
		ExBiConsumer<PanelPopulator<?, ?>, W, ModelInstantiationException> populator) {
		return new Abstract<W>(interpreted) {
			@Override
			protected void doPopulate(PanelPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				populator.accept(panel, quick);
			}
		};
	}

	/**
	 * Utility for interpretation of swing widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param interpreter Produces a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void interpretContainer(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExBiFunction<? super I, Transformer<ExpressoInterpretationException>, ? extends QuickSwingContainerPopulator<? extends W>, ExpressoInterpretationException> interpreter) {
		transformer.with(interpretedType, QuickSwingContainerPopulator.class, interpreter);
	}

	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> QuickSwingContainerPopulator<W> createContainer(
		I interpreted, ExBiConsumer<ContainerPopulator<?, ?>, W, ModelInstantiationException> populator) {
		return new QuickSwingContainerPopulator.Abstract<W>(interpreted) {
			@Override
			protected void doPopulateContainer(ContainerPopulator<?, ?> panel, W quick) throws ModelInstantiationException {
				populator.accept(panel, quick);
			}
		};
	}

	/**
	 * Utility for modification of swing widgets by Quick abstract widgets
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, I extends QuickWidget.Interpreted<? extends W>> void modifyForWidget(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExTriConsumer<? super I, QuickSwingPopulator<W>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(interpretedType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class,
			new Transformer.Modifier<I, QuickSwingPopulator<W>, ExpressoInterpretationException>() {
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(I source, T2 value, Transformer<ExpressoInterpretationException> tx)
				throws ExpressoInterpretationException {
				modifier.accept(source, value, tx);
				return value;
			}
		});
	}

	/**
	 * Utility for modification of swing widgets by Quick add-ons
	 *
	 * @param <W> The type of the Quick widget to interpret
	 * @param <I> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param interpretedType The type of the interpreted quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, AO extends QuickAddOn<W>, I extends QuickAddOn.Interpreted<W, ? extends AO>> void modifyForAddOn(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<I> interpretedType,
		ExTriConsumer<? super I, QuickSwingPopulator<W>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(interpretedType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class,
			new Transformer.Modifier<I, QuickSwingPopulator<W>, ExpressoInterpretationException>() {
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(I source, T2 value, Transformer<ExpressoInterpretationException> tx)
				throws ExpressoInterpretationException {
				modifier.accept(source, value, tx);
				return value;
			}
		});
	}
}

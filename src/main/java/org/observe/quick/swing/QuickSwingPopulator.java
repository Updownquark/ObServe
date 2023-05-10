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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
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
import org.observe.quick.*;
import org.observe.quick.QuickTextElement.QuickTextStyle;
import org.observe.quick.QuickWidget.Interpreted;
import org.observe.quick.base.*;
import org.observe.quick.base.QuickBox;
import org.observe.quick.base.QuickLayout;
import org.observe.quick.base.QuickSize;
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

	public interface QuickSwingEventListener<L extends QuickEventListener> {
		void addListener(Component c, L listener) throws ModelInstantiationException;
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
				List<QuickSwingEventListener<QuickEventListener>> listeners = BetterList.of2(//
					qw.getEventListeners().stream(), //
					l -> tx2.transform(l, QuickSwingEventListener.class));
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

								for (int i = 0; i < listeners.size(); i++)
									listeners.get(i).addListener(c, w.getEventListeners().get(i));
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
			tx.with(QuickMouseListener.QuickMouseButtonListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
				return (component, ql) -> {
					SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<QuickMouseListener.MouseButton> button = SettableValue.build(QuickMouseListener.MouseButton.class)
						.build();
					SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
					SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();

					QuickMouseListener.QuickMouseButtonListener mbl = (QuickMouseListener.QuickMouseButtonListener) ql;
					QuickMouseListener.MouseButton listenerButton = mbl.getInterpreted().getDefinition().getButton();
					mbl.setListenerContext(
						new QuickMouseListener.MouseButtonListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y, button));
					switch (mbl.getInterpreted().getDefinition().getEventType()) {
					case Click:
						component.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseClicked(MouseEvent evt) {
								QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
								if (eventButton == null)
									return;
								button.set(eventButton, evt);
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					case Press:
						component.addMouseListener(new MouseAdapter() {
							@Override
							public void mousePressed(MouseEvent evt) {
								QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
								if (eventButton == null)
									return;
								button.set(eventButton, evt);
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					case Release:
						component.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseReleased(MouseEvent evt) {
								QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
								if (eventButton == null)
									return;
								button.set(eventButton, evt);
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					default:
						throw new ModelInstantiationException(
							"Unrecognized mouse button event type: " + mbl.getInterpreted().getDefinition().getEventType(),
							mbl.getInterpreted().getDefinition().getElement().getPositionInFile(), 0);
					}
				};
			});
			tx.with(QuickMouseListener.QuickMouseMoveListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
				return (component, ql) -> {
					SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
					SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();

					QuickMouseListener.QuickMouseMoveListener mml = (QuickMouseListener.QuickMouseMoveListener) ql;
					mml.setListenerContext(
						new QuickMouseListener.MouseListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y));
					switch (mml.getInterpreted().getDefinition().getEventType()) {
					case Move:
						component.addMouseMotionListener(new MouseAdapter() {
							@Override
							public void mouseMoved(MouseEvent evt) {
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					case Enter:
						component.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseEntered(MouseEvent evt) {
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					case Exit:
						component.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseExited(MouseEvent evt) {
								altPressed.set(evt.isAltDown(), evt);
								ctrlPressed.set(evt.isControlDown(), evt);
								shiftPressed.set(evt.isShiftDown(), evt);
								x.set(evt.getX(), evt);
								y.set(evt.getY(), evt);
								if (Boolean.FALSE.equals(ql.getFilter().get()))
									return;
								ql.getAction().act(evt);
							}
						});
						break;
					default:
						throw new ModelInstantiationException(
							"Unrecognized mouse move event type: " + mml.getInterpreted().getDefinition().getEventType(),
							mml.getInterpreted().getDefinition().getElement().getPositionInFile(), 0);
					}
				};
			});
			tx.with(QuickMouseListener.QuickScrollListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
				return (component, ql) -> {
					SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
					SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();
					SettableValue<Integer> scrollAmount = SettableValue.build(int.class).withValue(0).build();

					QuickMouseListener.QuickScrollListener sl = (QuickMouseListener.QuickScrollListener) ql;
					sl.setListenerContext(
						new QuickMouseListener.ScrollListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y, scrollAmount));
					component.addMouseWheelListener(new MouseAdapter() {
						@Override
						public void mouseWheelMoved(MouseWheelEvent evt) {
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							scrollAmount.set(evt.getUnitsToScroll(), evt);
							if (Boolean.FALSE.equals(ql.getFilter().get()))
								return;
							ql.getAction().act(evt);
						}
					});
				};
			});
			tx.with(QuickKeyListener.QuickKeyTypedListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
				return (component, ql) -> {
					SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Character> charTyped = SettableValue.build(char.class).withValue((char) 0).build();
					QuickKeyListener.QuickKeyTypedListener tl = (QuickKeyListener.QuickKeyTypedListener) ql;
					tl.setListenerContext(new QuickKeyListener.KeyTypedContext.Default(altPressed, ctrlPressed, shiftPressed, charTyped));
					component.addKeyListener(new KeyAdapter() {
						@Override
						public void keyTyped(KeyEvent evt) {
							if (qil.getDefinition().getCharFilter() != 0 && evt.getKeyChar() != qil.getDefinition().getCharFilter())
								return;
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							charTyped.set(evt.getKeyChar(), evt);
							if (Boolean.FALSE.equals(ql.getFilter().get()))
								return;
							ql.getAction().act(evt);
						}
					});
				};
			});
			tx.with(QuickKeyListener.QuickKeyCodeListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
				return (component, ql) -> {
					SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
					SettableValue<KeyCode> keyCode = SettableValue.build(KeyCode.class).build();
					QuickKeyListener.QuickKeyCodeListener kl = (QuickKeyListener.QuickKeyCodeListener) ql;
					kl.setListenerContext(new QuickKeyListener.KeyCodeContext.Default(altPressed, ctrlPressed, shiftPressed, keyCode));
					component.addKeyListener(new KeyAdapter() {
						@Override
						public void keyPressed(KeyEvent e) {
							keyEvent(e, true);
						}

						@Override
						public void keyReleased(KeyEvent e) {
							keyEvent(e, false);
						}

						private void keyEvent(KeyEvent evt, boolean pressed) {
							if (pressed != qil.getDefinition().isPressed())
								return;
							KeyCode code = getKeyCodeFromAWT(evt.getKeyCode(), evt.getKeyLocation());
							if (code == null)
								return;
							if (qil.getDefinition().getKeyCode() != null && code != qil.getDefinition().getKeyCode())
								return;
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							keyCode.set(code, evt);
							if (Boolean.FALSE.equals(ql.getFilter().get()))
								return;
							ql.getAction().act(evt);
						}
					});
				};
			});
		}

		static QuickMouseListener.MouseButton checkMouseEventType(MouseEvent evt, QuickMouseListener.MouseButton listenerButton) {
			QuickMouseListener.MouseButton eventButton;
			if (SwingUtilities.isLeftMouseButton(evt))
				eventButton = QuickMouseListener.MouseButton.Left;
			else if (SwingUtilities.isRightMouseButton(evt))
				eventButton = QuickMouseListener.MouseButton.Right;
			else if (SwingUtilities.isMiddleMouseButton(evt))
				eventButton = QuickMouseListener.MouseButton.Middle;
			else
				return null; // I dunno, can't handle it
			if (listenerButton != null && eventButton != listenerButton)
				return null;
			return eventButton;
		}

		/**
		 * @param keyCode The key code (see java.awt.KeyEvent.VK_*, {@link KeyEvent#getKeyCode()})
		 * @param keyLocation The key's location (see java.awt.KeyEvent.KEY_LOCATION_*, {@link KeyEvent#getKeyLocation()}
		 * @return The Quick key code for the AWT codes
		 */
		public static KeyCode getKeyCodeFromAWT(int keyCode, int keyLocation) {
			switch (keyCode) {
			case KeyEvent.VK_ENTER:
				return KeyCode.ENTER;
			case KeyEvent.VK_BACK_SPACE:
				return KeyCode.BACKSPACE;
			case KeyEvent.VK_TAB:
				return KeyCode.TAB;
			case KeyEvent.VK_CANCEL:
				return KeyCode.CANCEL;
			case KeyEvent.VK_CLEAR:
				return KeyCode.CLEAR;
			case KeyEvent.VK_SHIFT:
				if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
					return KeyCode.SHIFT_LEFT;
				else
					return KeyCode.SHIFT_RIGHT;
			case KeyEvent.VK_CONTROL:
				if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
					return KeyCode.CTRL_LEFT;
				else
					return KeyCode.CTRL_RIGHT;
			case KeyEvent.VK_ALT:
				if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
					return KeyCode.ALT_LEFT;
				else
					return KeyCode.ALT_RIGHT;
			case KeyEvent.VK_PAUSE:
				return KeyCode.PAUSE;
			case KeyEvent.VK_CAPS_LOCK:
				return KeyCode.CAPS_LOCK;
			case KeyEvent.VK_ESCAPE:
				return KeyCode.ESCAPE;
			case KeyEvent.VK_SPACE:
				return KeyCode.SPACE;
			case KeyEvent.VK_PAGE_UP:
				return KeyCode.PAGE_UP;
			case KeyEvent.VK_PAGE_DOWN:
				return KeyCode.PAGE_DOWN;
			case KeyEvent.VK_END:
				return KeyCode.END;
			case KeyEvent.VK_HOME:
				return KeyCode.HOME;
			case KeyEvent.VK_LEFT:
				return KeyCode.LEFT_ARROW;
			case KeyEvent.VK_UP:
				return KeyCode.UP_ARROW;
			case KeyEvent.VK_RIGHT:
				return KeyCode.RIGHT_ARROW;
			case KeyEvent.VK_DOWN:
				return KeyCode.DOWN_ARROW;
			case KeyEvent.VK_COMMA:
			case KeyEvent.VK_LESS:
				return KeyCode.COMMA;
			case KeyEvent.VK_MINUS:
				if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
					return KeyCode.PAD_MINUS;
				else
					return KeyCode.MINUS;
			case KeyEvent.VK_UNDERSCORE:
				return KeyCode.MINUS;
			case KeyEvent.VK_PERIOD:
				if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
					return KeyCode.PAD_DOT;
				else
					return KeyCode.DOT;
			case KeyEvent.VK_GREATER:
				return KeyCode.DOT;
			case KeyEvent.VK_SLASH:
				if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
					return KeyCode.PAD_SLASH;
				else
					return KeyCode.FORWARD_SLASH;
			case KeyEvent.VK_0:
			case KeyEvent.VK_RIGHT_PARENTHESIS:
				return KeyCode.NUM_0;
			case KeyEvent.VK_1:
			case KeyEvent.VK_EXCLAMATION_MARK:
				return KeyCode.NUM_1;
			case KeyEvent.VK_2:
			case KeyEvent.VK_AT:
				return KeyCode.NUM_2;
			case KeyEvent.VK_3:
			case KeyEvent.VK_NUMBER_SIGN:
				return KeyCode.NUM_3;
			case KeyEvent.VK_4:
			case KeyEvent.VK_DOLLAR:
				return KeyCode.NUM_4;
			case KeyEvent.VK_5:
				return KeyCode.NUM_5;
			case KeyEvent.VK_6:
			case KeyEvent.VK_CIRCUMFLEX:
				return KeyCode.NUM_6;
			case KeyEvent.VK_7:
			case KeyEvent.VK_AMPERSAND:
				return KeyCode.NUM_7;
			case KeyEvent.VK_8:
			case KeyEvent.VK_ASTERISK:
				return KeyCode.NUM_8;
			case KeyEvent.VK_9:
			case KeyEvent.VK_LEFT_PARENTHESIS:
				return KeyCode.NUM_9;
			case KeyEvent.VK_SEMICOLON:
			case KeyEvent.VK_COLON:
				return KeyCode.SEMICOLON;
			case KeyEvent.VK_EQUALS:
				if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
					return KeyCode.PAD_EQUAL;
				else
					return KeyCode.EQUAL;
			case KeyEvent.VK_A:
				return KeyCode.A;
			case KeyEvent.VK_B:
				return KeyCode.B;
			case KeyEvent.VK_C:
				return KeyCode.C;
			case KeyEvent.VK_D:
				return KeyCode.D;
			case KeyEvent.VK_E:
				return KeyCode.E;
			case KeyEvent.VK_F:
				return KeyCode.F;
			case KeyEvent.VK_G:
				return KeyCode.G;
			case KeyEvent.VK_H:
				return KeyCode.H;
			case KeyEvent.VK_I:
				return KeyCode.I;
			case KeyEvent.VK_J:
				return KeyCode.J;
			case KeyEvent.VK_K:
				return KeyCode.K;
			case KeyEvent.VK_L:
				return KeyCode.L;
			case KeyEvent.VK_M:
				return KeyCode.M;
			case KeyEvent.VK_N:
				return KeyCode.N;
			case KeyEvent.VK_O:
				return KeyCode.O;
			case KeyEvent.VK_P:
				return KeyCode.P;
			case KeyEvent.VK_Q:
				return KeyCode.Q;
			case KeyEvent.VK_R:
				return KeyCode.R;
			case KeyEvent.VK_S:
				return KeyCode.S;
			case KeyEvent.VK_T:
				return KeyCode.T;
			case KeyEvent.VK_U:
				return KeyCode.U;
			case KeyEvent.VK_V:
				return KeyCode.V;
			case KeyEvent.VK_W:
				return KeyCode.W;
			case KeyEvent.VK_X:
				return KeyCode.X;
			case KeyEvent.VK_Y:
				return KeyCode.Y;
			case KeyEvent.VK_Z:
				return KeyCode.Z;
			case KeyEvent.VK_OPEN_BRACKET:
			case KeyEvent.VK_BRACELEFT:
				return KeyCode.LEFT_BRACE;
			case KeyEvent.VK_BACK_SLASH:
				return KeyCode.BACK_SLASH;
			case KeyEvent.VK_CLOSE_BRACKET:
			case KeyEvent.VK_BRACERIGHT:
				return KeyCode.RIGHT_BRACE;
			case KeyEvent.VK_NUMPAD0:
				return KeyCode.PAD_0;
			case KeyEvent.VK_NUMPAD1:
				return KeyCode.PAD_1;
			case KeyEvent.VK_NUMPAD2:
			case KeyEvent.VK_KP_DOWN:
				return KeyCode.PAD_2;
			case KeyEvent.VK_NUMPAD3:
				return KeyCode.PAD_3;
			case KeyEvent.VK_NUMPAD4:
			case KeyEvent.VK_KP_LEFT:
				return KeyCode.PAD_4;
			case KeyEvent.VK_NUMPAD5:
				return KeyCode.PAD_5;
			case KeyEvent.VK_NUMPAD6:
			case KeyEvent.VK_KP_RIGHT:
				return KeyCode.PAD_6;
			case KeyEvent.VK_NUMPAD7:
				return KeyCode.PAD_7;
			case KeyEvent.VK_NUMPAD8:
			case KeyEvent.VK_KP_UP:
				return KeyCode.PAD_8;
			case KeyEvent.VK_NUMPAD9:
				return KeyCode.PAD_9;
			case KeyEvent.VK_MULTIPLY:
				return KeyCode.PAD_MULTIPLY;
			case KeyEvent.VK_ADD:
				return KeyCode.PAD_PLUS;
			case KeyEvent.VK_SEPARATOR:
				return KeyCode.PAD_SEPARATOR;
			case KeyEvent.VK_SUBTRACT:
				return KeyCode.PAD_MINUS;
			case KeyEvent.VK_DECIMAL:
				return KeyCode.PAD_DOT;
			case KeyEvent.VK_DIVIDE:
				return KeyCode.PAD_SLASH;
			case KeyEvent.VK_DELETE:
				return KeyCode.PAD_BACKSPACE;
			case KeyEvent.VK_NUM_LOCK:
				return KeyCode.NUM_LOCK;
			case KeyEvent.VK_SCROLL_LOCK:
				return KeyCode.SCROLL_LOCK;
			case KeyEvent.VK_F1:
				return KeyCode.F1;
			case KeyEvent.VK_F2:
				return KeyCode.F2;
			case KeyEvent.VK_F3:
				return KeyCode.F3;
			case KeyEvent.VK_F4:
				return KeyCode.F4;
			case KeyEvent.VK_F5:
				return KeyCode.F5;
			case KeyEvent.VK_F6:
				return KeyCode.F6;
			case KeyEvent.VK_F7:
				return KeyCode.F7;
			case KeyEvent.VK_F8:
				return KeyCode.F8;
			case KeyEvent.VK_F9:
				return KeyCode.F9;
			case KeyEvent.VK_F10:
				return KeyCode.F10;
			case KeyEvent.VK_F11:
				return KeyCode.F11;
			case KeyEvent.VK_F12:
				return KeyCode.F12;
			case KeyEvent.VK_F13:
				return KeyCode.F13;
			case KeyEvent.VK_F14:
				return KeyCode.F14;
			case KeyEvent.VK_F15:
				return KeyCode.F15;
			case KeyEvent.VK_F16:
				return KeyCode.F16;
			case KeyEvent.VK_F17:
				return KeyCode.F17;
			case KeyEvent.VK_F18:
				return KeyCode.F18;
			case KeyEvent.VK_F19:
				return KeyCode.F19;
			case KeyEvent.VK_F20:
				return KeyCode.F20;
			case KeyEvent.VK_F21:
				return KeyCode.F21;
			case KeyEvent.VK_F22:
				return KeyCode.F22;
			case KeyEvent.VK_F23:
				return KeyCode.F23;
			case KeyEvent.VK_F24:
				return KeyCode.F24;
			case KeyEvent.VK_PRINTSCREEN:
				return KeyCode.PRINT_SCREEN;
			case KeyEvent.VK_INSERT:
				return KeyCode.INSERT;
			case KeyEvent.VK_HELP:
				return KeyCode.HELP;
			case KeyEvent.VK_META:
				return KeyCode.META;
			case KeyEvent.VK_BACK_QUOTE:
				return KeyCode.BACK_QUOTE;
			case KeyEvent.VK_QUOTE:
			case KeyEvent.VK_QUOTEDBL:
				return KeyCode.QUOTE;
			case KeyEvent.VK_WINDOWS:
				return KeyCode.COMMAND_KEY;
			case KeyEvent.VK_CONTEXT_MENU:
				return KeyCode.CONTEXT_MENU;
			default:
				return null;
			}
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
			QuickSwingPopulator.<QuickWidget, QuickField, QuickField.Interpreted> modifyForAddOn(tx, QuickField.Interpreted.class,
				(Class<QuickWidget.Interpreted<QuickWidget>>) (Class<?>) QuickWidget.Interpreted.class, (ao, qsp, tx2) -> {
					qsp.addModifier((comp, w) -> {
						if (w.getAddOn(QuickField.class).getFieldLabel() != null)
							comp.withFieldName(w.getAddOn(QuickField.class).getFieldLabel());
						if (ao.getDefinition().isFill())
							comp.fill();
					});
				});

			// Box layouts
			tx.with(QuickInlineLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretInlineLayout);
			tx.with(QuickSimpleLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretSimpleLayout);
			tx.with(QuickBorderLayout.Interpreted.class, QuickSwingLayout.class, QuickBaseSwing::interpretBorderLayout);

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
				else if (sz.percent == 0.0f)
					return sz.pixels;
				else {
					System.err.println("min/pref/max size constraints must be absolute: " + sz);
					return null;
				}
			}, size::toString, null);
		}

		static QuickSwingLayout<QuickBorderLayout> interpretBorderLayout(QuickBorderLayout.Interpreted interpreted,
			Transformer<ExpressoInterpretationException> tx) {
			return new QuickSwingLayout<QuickBorderLayout>() {
				@Override
				public LayoutManager create(QuickBorderLayout quick) throws ModelInstantiationException {
					return new BorderLayout();
				}

				@Override
				public void modifyChild(QuickSwingPopulator<?> child) throws ExpressoInterpretationException {
					child.addModifier((comp, w) -> {
						QuickBorderLayout.Region region = w.getAddOn(QuickBorderLayout.Child.class).getInterpreted().getDefinition()
							.getRegion();
						Component[] component = new Component[1];
						comp.modifyComponent(c -> component[0] = c);
						Sizeable size = w.getAddOn(Sizeable.class);
						BorderLayout.Constraints childConstraint = borderConstraints(region, size);
						comp.withLayoutConstraints(childConstraint);
						if (size != null) {
							size.changes().act(evt -> {
								if (component[0].getParent() != null)
									component[0].getParent().invalidate();
							});
						}
					});
				}
			};
		}

		static BorderLayout.Constraints borderConstraints(QuickBorderLayout.Region region, Sizeable size) {
			if (size == null)
				return new BorderLayout.Constraints(region, null, null, null, null);
			return new BorderLayout.Constraints(region, //
				size.getSize(), enforceAbsolute(size.getMinimum()), enforceAbsolute(size.getPreferred()),
				enforceAbsolute(size.getMaximum()));
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
						quick.getEmptyText().changes().takeUntil(tf.getUntil()).act(evt -> tf2.setEmptyText(evt.getNewValue()));
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
	 * @param <AOI> The type of the interpreted quick widget
	 *
	 * @param transformer The transformer builder to populate
	 * @param widgetType The type of the interpreted quick widget
	 * @param modifier Modifies a {@link QuickSwingPopulator} for interpreted widgets of the given type
	 */
	public static <W extends QuickWidget, AO extends QuickAddOn<? super W>, AOI extends QuickAddOn.Interpreted<? super W, ? extends AO>> void modifyForAddOn(//
		Transformer.Builder<ExpressoInterpretationException> transformer, Class<AOI> interpretedType,
		Class<? extends QuickWidget.Interpreted<W>> widgetType,
			ExTriConsumer<? super AOI, QuickSwingPopulator<?>, Transformer<ExpressoInterpretationException>, ExpressoInterpretationException> modifier) {
		transformer.modifyWith(widgetType, (Class<QuickSwingPopulator<W>>) (Class<?>) QuickSwingPopulator.class,
			new Transformer.Modifier<QuickWidget.Interpreted<W>, QuickSwingPopulator<W>, ExpressoInterpretationException>() {
			@Override
			public <T2 extends QuickSwingPopulator<W>> T2 modify(QuickWidget.Interpreted<W> source, T2 value,
				Transformer<ExpressoInterpretationException> tx) throws ExpressoInterpretationException {
				AOI addOn = source.getAddOn(interpretedType);
				if (addOn != null)
					modifier.accept(addOn, value, tx);
				return value;
			}
		});
	}
}
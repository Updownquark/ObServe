package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.KeyCode;
import org.observe.quick.Positionable;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickMouseListener.MouseButton;
import org.observe.quick.QuickMouseListener.MouseMoveEventType;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickSize;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.Sizeable;
import org.observe.quick.draw.QuickBorderedShape;
import org.observe.quick.draw.QuickCanvas;
import org.observe.quick.draw.QuickRectangle;
import org.observe.quick.draw.QuickShape;
import org.observe.quick.draw.QuickShapeCollection;
import org.observe.quick.draw.QuickShapeContainer;
import org.observe.quick.draw.QuickShapePublisher;
import org.observe.quick.draw.QuickSimpleShape;
import org.observe.quick.draw.StrokeDashing;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.BiTuple;
import org.qommons.Colors;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/** Swing implementation for Quick-Draw */
public class QuickDrawSwing implements QuickInterpretation {
	/**
	 * The transformation type required for a {@link QuickShapePublisher} to be drawn by {@link QuickDrawSwing}
	 *
	 * @param <P> The type of shape publisher
	 */
	public interface InterpretedQuickShapePublisher<P extends QuickShapePublisher> {
		QuickDrawShapePublisher interpret(P element) throws ModelInstantiationException;
	}

	/** Instantiated shape publisher class provided and understood by {@link QuickDrawSwing} implementations */
	public interface QuickDrawShapePublisher {
		ObservableCollection<QuickShapeInterpretation> getShapes();
	}

	public enum Opacity {
		None, Partial, Full;

		public Opacity or(Opacity other) {
			return QommonsUtils.max(this, other);
		}
	}

	/** An individual shape implementation for {@link QuickDrawSwing} */
	public interface QuickShapeInterpretation {
		Observable<?> update();

		void draw(Graphics2D gfx, Dimension screen);

		Point hit(Point containerPoint);

		Opacity getOpacity(Point point);

		QuickShapeInterpretation mouseEntered(MouseEvent e);

		QuickShapeInterpretation mouseMoved(MouseEvent e);

		void mouseDragged(MouseEvent e);

		void mouseExited(MouseEvent e);

		Opacity mousePressed(MouseEvent e);

		Opacity mouseReleased(MouseEvent e);

		Opacity mouseClicked(MouseEvent e);

		Opacity mouseWheelMoved(MouseWheelEvent e);

		boolean keyPressed(KeyEvent e);

		boolean keyReleased(KeyEvent e);

		boolean keyTyped(KeyEvent e);

		String getTooltip();

		void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed);
	}

	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		tx.with(QuickCanvas.Interpreted.class, QuickSwingPopulator.class, QuickCanvasPopulator::new);
		tx.with(QuickShapeCollection.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedShapeCollection::new);
		tx.with(QuickRectangle.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedRectangle::new);
	}

	static class InterpretedShapeContainer {
		private final Map<Object, InterpretedQuickShapePublisher<?>> thePublishers;

		public InterpretedShapeContainer(ExElement.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			thePublishers = new HashMap<>();
			for (QuickShapePublisher.Interpreted<?> shape : interpreted.getAddOn(QuickShapeContainer.Interpreted.class).getShapes()) {
				thePublishers.put(shape.getIdentity(), tx.transform(shape, InterpretedQuickShapePublisher.class));
			}
		}

		public List<QuickDrawShapePublisher> getPublishers(ExElement quick) throws ModelInstantiationException {
			List<QuickDrawShapePublisher> publishers = new ArrayList<>(thePublishers.size());
			for (QuickShapePublisher p : quick.getAddOn(QuickShapeContainer.class).getShapes()) {
				InterpretedQuickShapePublisher<?> interpP = thePublishers.get(p.getIdentity());
				if (interpP != null)
					publishers.add(((InterpretedQuickShapePublisher<QuickShapePublisher>) interpP).interpret(p));
			}
			return publishers;
		}
	}

	static abstract class SimpleShapeContainer implements QuickShapeInterpretation, QuickDrawShapePublisher {
		private final ObservableCollection<QuickShapeInterpretation> theContents;
		private final Observable<?> theUpdate;
		private final BetterSet<ElementId> theHovered;
		private boolean isPressed;
		private boolean isRightPressed;
		private boolean isDrawing;

		SimpleShapeContainer(List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theContents = ObservableCollection.of(publishers).flow()//
				.flatMap(pub -> pub.getShapes().flow())//
				.refreshEach(shape -> shape.update().filter(__ -> !isDrawing))//
				.collectActive(until);
			theUpdate = Observable.onRootFinish(theContents.simpleChanges());
			theHovered = BetterHashSet.build().build();
		}

		protected abstract CollectionElement<QuickShapeInterpretation> getFocus();

		protected abstract void setFocus(CollectionElement<QuickShapeInterpretation> shape);

		protected boolean isFocused(CollectionElement<QuickShapeInterpretation> shape) {
			CollectionElement<QuickShapeInterpretation> focus = getFocus();
			return focus != null && shape.getElementId().equals(focus.getElementId());
		}

		public boolean isPressed() {
			return isPressed;
		}

		public boolean isRightPressed() {
			return isRightPressed;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return getContents();
		}

		public ObservableCollection<QuickShapeInterpretation> getContents() {
			return theContents;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Dimension screen) {
			isDrawing = true;
			try {
				for (CollectionElement<QuickShapeInterpretation> shape = theContents
					.getTerminalElement(true); shape != null; shape = theContents.getAdjacentElement(shape.getElementId(), true)) {
					setState(shape);
					shape.get().draw(gfx, screen);
				}
			} finally {
				isDrawing = false;
			}
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			return mouseHover(e);
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			return mouseHover(e);
		}

		protected void setState(CollectionElement<QuickShapeInterpretation> shapeEl) {
			boolean hovered = theHovered.contains(shapeEl.getElementId());
			shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed, hovered && isRightPressed);
		}

		protected QuickShapeInterpretation mouseHover(MouseEvent e) {
			theHovered.removeIf(el -> !el.isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			for (CollectionElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); el != null; el = theContents
				.getAdjacentElement(el.getElementId(), false)) {
				setState(el);
				Point hit = el.get().hit(e.getPoint());
				if (done && theHovered.remove(el.getElementId())) {
					try (Transaction t = translateForHit(e, hit)) {
						setState(el);
						el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
					}
				} else if (hit != null) {
					try (Transaction t = translateForHit(e, hit)) {
						boolean newHover = theHovered.add(el.getElementId());
						setState(el);
						if (newHover) {
							QuickShapeInterpretation target = el.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED));
							if (first == null)
								first = target;
						} else {
							QuickShapeInterpretation target = el.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED));
							if (first == null)
								first = target;
						}
					}
					done = el.get().getOpacity(hit) == Opacity.Full;
				} else if (theHovered.remove(el.getElementId())) {
					setState(el);
					el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
			return first;
		}

		protected MouseEvent asType(MouseEvent source, int newId) {
			if (source.getID() == newId)
				return source;
			return new MouseEvent(source.getComponent(), MouseEvent.MOUSE_EXITED, source.getWhen(), source.getModifiers(), source.getX(),
				source.getY(), source.getClickCount(), source.isPopupTrigger(), source.getButton());
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			mouseAction(e, QuickShapeInterpretation::mouseDragged);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			isPressed = isRightPressed = true;
			Iterator<ElementId> hovered = theHovered.iterator();
			while (hovered.hasNext()) {
				ElementId el = hovered.next();
				if (!el.isPresent())
					continue;
				CollectionElement<QuickShapeInterpretation> shape = theContents.getElement(el);
				setState(shape);
				Point hit = shape.get().hit(e.getPoint());
				hovered.remove();
				try (Transaction t = translateForHit(e, hit)) {
					setState(shape);
					shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = true;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = true;
			CollectionElement<QuickShapeInterpretation> focus = null;
			Opacity opacity = Opacity.None;
			for (CollectionElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = theContents.getAdjacentElement(el.getElementId(), false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(e.getPoint());
				if (hit == null)
					continue;
				Opacity shapeOpacity;
				try (Transaction t = translateForHit(e, hit)) {
					shapeOpacity = shape.mousePressed(e);
				}
				if (shapeOpacity != Opacity.None)
					focus = el;
				opacity = opacity.or(shapeOpacity);
			}
			setFocus(focus);
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = false;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = false;
			return mouseAction(e, QuickShapeInterpretation::mouseReleased);
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			return mouseAction(e, QuickShapeInterpretation::mouseClicked);
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			return mouseAction(e, QuickShapeInterpretation::mouseWheelMoved);
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			CollectionElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyPressed(e);
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			CollectionElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyReleased(e);
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			CollectionElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyTyped(e);
			return true;
		}

		@Override
		public Point hit(Point containerPoint) {
			return containerPoint;
		}

		@Override
		public Opacity getOpacity(Point point) {
			Opacity opacity = Opacity.None;
			for (CollectionElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = theContents.getAdjacentElement(el.getElementId(), false)) {
				QuickShapeInterpretation shape = el.get();
				if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(point);
				if (hit == null)
					continue;
				opacity = opacity.or(shape.getOpacity(hit));
			}
			return opacity;
		}

		public <E extends MouseEvent> Opacity mouseAction(E e, BiConsumer<QuickShapeInterpretation, E> action) {
			Opacity opacity = Opacity.None;
			for (CollectionElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = theContents.getAdjacentElement(el.getElementId(), false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(e.getPoint());
				if (hit == null)
					continue;
				try (Transaction t = translateForHit(e, hit)) {
					action.accept(shape, e);
				}
				opacity = opacity.or(shape.getOpacity(hit));
			}
			return opacity;
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.isPresent());
			for (ElementId hovered : theHovered.reverse()) {
				CollectionElement<QuickShapeInterpretation> shape = getShapes().getElement(hovered);
				setState(shape);
				String tooltip = shape.get().getTooltip();
				if (tooltip != null)
					return tooltip;
			}
			return null;
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			isPressed = pressed;
			isRightPressed = rightPressed;
			if (!focused)
				setFocus(null);
		}
	}

	public static Transaction translateForHit(MouseEvent event, Point hit) {
		if (hit == null || (hit.x == event.getX() && hit.y == event.getY()))
			return Transaction.NONE;
		int xDiff = hit.x - event.getX();
		int yDiff = hit.y - event.getY();
		event.translatePoint(xDiff, yDiff);
		return () -> event.translatePoint(-xDiff, -yDiff);
	}

	static class QuickCanvasPopulator extends QuickSwingPopulator.Abstract<QuickCanvas> {
		private final QuickCanvas.Interpreted theCanvas;
		private final InterpretedShapeContainer thePublishing;

		QuickCanvasPopulator(QuickCanvas.Interpreted canvas, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theCanvas = canvas;
			thePublishing = new InterpretedShapeContainer(canvas, tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCanvas quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addComponent(null, new QuickCanvasComponent(quick, thePublishing.getPublishers(quick), panel.getUntil()),
				component::accept);
		}
	}

	static class QuickCanvasComponent extends JComponent {
		private final QuickCanvas theCanvas;
		private final SimpleShapeContainer theContainer;
		private final Sizeable theWidth;
		private final Sizeable theHeight;
		private CollectionElement<QuickShapeInterpretation> theFocus;

		QuickCanvasComponent(QuickCanvas canvas, List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theCanvas = canvas;
			theContainer = new SimpleShapeContainer(publishers, canvas.onDestroy()) {
				@Override
				protected CollectionElement<QuickShapeInterpretation> getFocus() {
					return theFocus;
				}

				@Override
				protected void setFocus(CollectionElement<QuickShapeInterpretation> shape) {
					theFocus = shape;
				}

				@Override
				public String getTooltip() {
					return null;
				}
			};
			theContainer.update().takeUntil(until).act(__ -> repaint());
			theWidth = canvas.getAddOn(Sizeable.Horizontal.class);
			theHeight = canvas.getAddOn(Sizeable.Vertical.class);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					publishSize();
				}
			});
			publishSize();
			class EventListener extends MouseAdapter implements KeyListener {
				@Override
				public void mouseEntered(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseEntered(e);
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseMoved(e);
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					theContainer.mouseDragged(e);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					theContainer.mouseExited(e);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					theContainer.mousePressed(e);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					theContainer.mouseReleased(e);
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					theContainer.mouseClicked(e);
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					theContainer.mouseWheelMoved(e);
				}

				@Override
				public void keyPressed(KeyEvent e) {
					theContainer.keyPressed(e);
				}

				@Override
				public void keyReleased(KeyEvent e) {
					theContainer.keyReleased(e);
				}

				@Override
				public void keyTyped(KeyEvent e) {
					theContainer.keyTyped(e);
				}
			}
			EventListener listener = new EventListener();
			addMouseListener(listener);
			addMouseMotionListener(listener);
			addMouseWheelListener(listener);
		}

		@Override
		public Dimension getPreferredSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getPreferred().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = 300;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getPreferred().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = 300;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMinimumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMinimum().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = 0;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMinimum().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = 0;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMaximumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMaximum().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = Integer.MAX_VALUE;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMaximum().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = Integer.MAX_VALUE;
			return new Dimension(w, h);
		}

		void publishSize() {
			Integer preW = theCanvas.getPublishWidth().get();
			Integer preH = theCanvas.getPublishHeight().get();
			if ((preW == null || preW.intValue() != getWidth()) && theCanvas.getPublishWidth().isAcceptable(getWidth()) == null)
				theCanvas.getPublishWidth().set(getWidth());

			if ((preH == null || preH.intValue() != getHeight()) && theCanvas.getPublishHeight().isAcceptable(getHeight()) == null)
				theCanvas.getPublishHeight().set(getHeight());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			theContainer.draw((Graphics2D) g, getSize());
		}
	}

	static class InterpretedShapeCollection<T> extends InterpretedShapeContainer
	implements InterpretedQuickShapePublisher<QuickShapeCollection<T>> {
		InterpretedShapeCollection(QuickShapeCollection.Interpreted<T> collection, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(collection, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickShapeCollection<T> element) throws ModelInstantiationException {
			return new QuickShapeCollectionPublisher<>(element, getPublishers(element));
		}
	}

	static class QuickShapeCollectionPublisher<T> extends SimpleShapeContainer {
		private final QuickShapeCollection<T> theCollection;
		private CollectionElement<T> theFocusElement;
		private CollectionElement<QuickShapeInterpretation> theFocus;
		private CollectionElement<T> theCurrentValue;
		private final BetterSet<BiTuple<ElementId, ElementId>> theHovered;
		private boolean isInAction;

		QuickShapeCollectionPublisher(QuickShapeCollection<T> collection, List<QuickDrawShapePublisher> shapes) {
			super(shapes, collection.onDestroy());
			theCollection = collection;
			theHovered = BetterHashSet.build().build();
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return Observable.or(super.update(), Observable.onRootFinish(theCollection.getValues().simpleChanges()));
		}

		@Override
		public void draw(Graphics2D gfx, Dimension screen) {
			isInAction = true;
			try {
				SettableValue<T> activeValue = theCollection.getActiveValue();
				SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
				int i = 0;
				for (CollectionElement<T> value = theCollection.getValues().getTerminalElement(true); //
					value != null; value = theCollection.getValues().getAdjacentElement(value.getElementId(), true)) {
					theCurrentValue = value;
					activeValue.set(value.get());
					activeIndex.set(i);
					super.draw(gfx, screen);
					i++;
				}
			} finally {
				isInAction = false;
			}
		}

		@Override
		protected QuickShapeInterpretation mouseHover(MouseEvent e) {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (CollectionElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					!done && valueEl != null; valueEl = theCollection.getValues().getAdjacentElement(valueEl.getElementId(), false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					for (CollectionElement<QuickShapeInterpretation> shapeEl = getContents().getTerminalElement(false); shapeEl != null; //
						shapeEl = getContents().getAdjacentElement(shapeEl.getElementId(), false)) {
						Point hit = shapeEl.get().hit(e.getPoint());
						if (done && theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
							try (Transaction t = translateForHit(e, hit)) {
								shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
							}
						} else if (hit != null) {
							try (Transaction t = translateForHit(e, hit)) {
								if (theHovered.add(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
									QuickShapeInterpretation target = shapeEl.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED));
									if (first == null)
										first = target;
								} else {
									QuickShapeInterpretation target = shapeEl.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED));
									if (first == null)
										first = target;
								}
							}
							done = shapeEl.get().getOpacity(hit) == Opacity.Full;
						} else if (theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
							shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
						}
					}
					i--;
				}
			} finally {
				isInAction = false;
			}
			return first;
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			CollectionElement<T> focusEl = null;
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (CollectionElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full
						&& valueEl != null; valueEl = theCollection.getValues().getAdjacentElement(valueEl.getElementId(), false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					Opacity valueOpacity = super.mousePressed(e);
					if (valueOpacity != Opacity.None)
						focusEl = valueEl;
					opacity = opacity.or(valueOpacity);
					i--;
				}
			} finally {
				isInAction = false;
			}
			theFocusElement = focusEl;
			return opacity;
		}

		@Override
		public void mouseExited(MouseEvent e) {
			Iterator<BiTuple<ElementId, ElementId>> hovered = theHovered.iterator();
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			while (hovered.hasNext()) {
				BiTuple<ElementId, ElementId> el = hovered.next();
				if (!el.getValue1().isPresent() || !el.getValue2().isPresent())
					continue;
				CollectionElement<T> valueEl = theCollection.getValues().getElement(el.getValue1());
				theCurrentValue = valueEl;
				activeValue.set(valueEl.get());
				activeIndex.set(theCollection.getValues().getElementsBefore(valueEl.getElementId()));
				CollectionElement<QuickShapeInterpretation> shape = getContents().getElement(el.getValue2());
				setState(shape);
				Point hit = shape.get().hit(e.getPoint());
				hovered.remove();
				try (Transaction t = translateForHit(e, hit)) {
					setState(shape);
					shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
		}

		@Override
		public <E extends MouseEvent> Opacity mouseAction(E e, BiConsumer<QuickShapeInterpretation, E> action) {
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (CollectionElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full
						&& valueEl != null; valueEl = theCollection.getValues().getAdjacentElement(valueEl.getElementId(), false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					opacity = opacity.or(super.mouseAction(e, action));
					i--;
				}
			} finally {
				isInAction = false;
			}
			return opacity;
		}

		@Override
		protected CollectionElement<QuickShapeInterpretation> getFocus() {
			if (!isInAction) {
				if (theFocusElement != null && !theFocusElement.getElementId().isPresent()) {
					theFocusElement = null;
					theFocus = null;
				}
				if (theFocusElement != null) {
					theCurrentValue = theFocusElement;
					theCollection.getActiveValue().set(theFocusElement.get());
					theCollection.getActiveValueIndex().set(theCollection.getValues().getElementsBefore(theFocusElement.getElementId()));
				}
			}
			return theFocus;
		}

		@Override
		protected void setFocus(CollectionElement<QuickShapeInterpretation> shape) {
			theFocus = shape;
		}

		@Override
		protected boolean isFocused(CollectionElement<QuickShapeInterpretation> shape) {
			return theFocusElement != null && theCurrentValue != null
				&& theFocusElement.getElementId().equals(theCurrentValue.getElementId())
				&& theFocus != null && theFocus.getElementId().equals(shape.getElementId());
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			isInAction = true;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try {
				for (BiTuple<ElementId, ElementId> hovered : theHovered.reverse()) {
					CollectionElement<T> valueEl = theCollection.getValues().getElement(hovered.getValue1());
					theCurrentValue = valueEl;
					QuickShapeInterpretation shape = getContents().getElement(hovered.getValue2()).get();
					activeValue.set(valueEl.get());
					activeIndex.set(theCollection.getValues().getElementsBefore(valueEl.getElementId()));
					String tooltip = shape.getTooltip();
					if (tooltip != null)
						return tooltip;
				}
			} finally {
				isInAction = false;
			}
			return null;
		}

		@Override
		protected void setState(CollectionElement<QuickShapeInterpretation> shapeEl) {
			boolean hovered = theHovered.contains(new BiTuple<>(theCurrentValue.getElementId(), shapeEl.getElementId()));
			shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed(), hovered && isRightPressed());
		}
	}

	static abstract class QuickDrawSingleShape<S extends QuickShape> implements QuickDrawShapePublisher, QuickShapeInterpretation {
		static BiConsumer<QuickEventListener, InputEvent> EVENT_NO_CONFIG = (ql, evt) -> {
		};

		static <L extends QuickEventListener, E extends InputEvent> BiConsumer<L, E> noConfig() {
			return (BiConsumer<L, E>) EVENT_NO_CONFIG;
		}

		private final S theShape;
		private final QuickWithBackground.BackgroundContext theBgCtx;

		protected QuickDrawSingleShape(S shape) {
			theShape = shape;
			theBgCtx = new QuickWithBackground.BackgroundContext.Default();
			theShape.setContext(theBgCtx);
		}

		public S getShape() {
			return theShape;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		protected <E extends InputEvent, L extends QuickEventListener> void input(Class<L> listenerType, Predicate<? super L> filter, E evt,
			BiConsumer<L, E> install) {
			for (QuickEventListener lstnr : theShape.getEventListeners()) {
				if (!listenerType.isInstance(lstnr))
					continue;

				L listener = (L) lstnr;
				if (filter != null && !filter.test(listener))
					continue;

				SettableValue<Boolean> altPressed = listener.isAltPressed();
				SettableValue<Boolean> ctrlPressed = listener.isCtrlPressed();
				SettableValue<Boolean> shiftPressed = listener.isShiftPressed();
				altPressed.set(evt.isAltDown(), evt);
				ctrlPressed.set(evt.isControlDown(), evt);
				shiftPressed.set(evt.isShiftDown(), evt);
				install.accept(listener, evt);
				if (listener.testFilter() && listener.getAction().isEnabled().get() == null)
					listener.getAction().act(evt);
			}
		}

		protected <L extends QuickMouseListener, E extends MouseEvent> Opacity mouse(Class<L> listenerType, Predicate<L> filter, E evt,
			BiConsumer<L, E> install) {
			Point hit = hit(evt.getPoint());
			if (hit == null)
				return Opacity.None;
			try (Transaction t = translateForHit(evt, hit)) {
				input(listenerType, filter, evt, install.andThen((listener, evt2) -> {
					SettableValue<Integer> x = listener.getEventX();
					SettableValue<Integer> y = listener.getEventY();
					x.set(evt2.getX(), evt2);
					y.set(evt2.getY(), evt2);
				}));
			}
			return getOpacity(hit);
		}

		protected Opacity mouseMove(MouseMoveEventType eventType, MouseEvent evt) {
			return mouse(QuickMouseListener.QuickMouseMoveListener.class, mml -> mml.getEventType() == eventType, evt, noConfig());
		}

		protected <L extends QuickMouseButtonListener> Opacity mouseButton(Class<L> listenerType, Predicate<? super L> filter,
			MouseEvent evt, BiConsumer<L, MouseEvent> install) {
			return mouse(listenerType, lstnr -> {
				MouseButton button = QuickCoreSwing.checkMouseEventType(evt, lstnr.getButton());
				if (button == null)
					return false;
				lstnr.getEventButton().set(button, evt);
				if (filter != null && !filter.test(lstnr))
					return false;
				return true;
			}, evt, install);
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			if (mouseMove(MouseMoveEventType.Enter, e) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			if (mouseMove(MouseMoveEventType.Move, e) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
			mouseMove(MouseMoveEventType.Exit, e);
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMousePressedListener.class, null, e, noConfig());
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMouseReleasedListener.class, null, e, noConfig());
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMouseClickListener.class, lstnr -> {
				if (lstnr.getClickCount() > 0 && e.getClickCount() != lstnr.getClickCount())
					return false;
				return true;
			}, e, noConfig());
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			return mouse(QuickMouseListener.QuickScrollListener.class, null, e, (lstnr, evt) -> {
				lstnr.getScrollAmount().set(evt.getScrollAmount());
			});
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (!lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			input(QuickKeyListener.QuickKeyTypedListener.class, lstnr -> {
				if (lstnr.getCharFilter() > 0 && lstnr.getCharFilter() != e.getKeyChar())
					return false;
				lstnr.getTypedChar().set(e.getKeyChar(), e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public String getTooltip() {
			return theShape.getTooltip().get();
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			// System.out.println(theShape + ":" + (hovered ? " hovered" : "") + (focused ? " focused" : "") + (pressed ? " pressed" : "")
			// + (rightPressed ? "right-pressed" : ""));
			theBgCtx.isHovered().set(hovered);
			theBgCtx.isFocused().set(focused);
			theBgCtx.isPressed().set(pressed);
			theBgCtx.isRightPressed().set(rightPressed);
		}
	}

	static abstract class QuickDrawBorderedShape<S extends QuickBorderedShape> extends QuickDrawSingleShape<S> {
		private final ObservableValue<Color> theColor;
		private final ObservableValue<Float> theOpacity;
		private final ObservableValue<Integer> theBorderThickness;
		private final ObservableValue<Color> theBorderColor;
		private final ObservableValue<StrokeDashing> theStrokeDash;
		private final Observable<?> theUpdate;

		protected QuickDrawBorderedShape(S shape) {
			super(shape);
			QuickBorderedShape.QuickBorderedShapeStyle style = shape.getStyle();
			theColor = style.getColor();
			theOpacity = style.getOpacity();
			theBorderThickness = style.getBorderThickness();
			theBorderColor = style.getBorderColor();
			theStrokeDash = style.getStrokeDash();
			theUpdate = Observable.or(theColor.noInitChanges(), theOpacity.noInitChanges(), theBorderThickness.noInitChanges(),
				theBorderColor.noInitChanges(), theStrokeDash.noInitChanges(), shape.getRepaint());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		public boolean isVisible() {
			return getShape().isVisible().get();
		}

		public Color getColor() {
			Color color = theColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public float getOpacity() {
			Float opacity = theOpacity.get();
			if (opacity == null)
				return 1.0f;
			return opacity;
		}

		public int getBorderThickness() {
			Integer thick = theBorderThickness.get();
			return thick == null ? 0 : thick;
		}

		public Color getBorderColor() {
			Color color = theBorderColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public StrokeDashing getStrokeDash() {
			StrokeDashing dash = theStrokeDash.get();
			return dash == null ? StrokeDashing.full : dash;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public Opacity getOpacity(Point point) {
			if (!isVisible())
				return Opacity.None;
			Color color = theColor.get();
			Float opacity = theOpacity.get();
			if (color == null) {
				if (opacity == null || opacity.floatValue() >= 1.0f)
					return Opacity.Full;
				else if (opacity.floatValue() <= 0.0f)
					return Opacity.None;
				else
					return Opacity.Partial;
			}
			int alpha;
			if (opacity == null || opacity.floatValue() == 1.0f)
				alpha = color.getAlpha();
			else
				alpha = Math.round(color.getAlpha() * opacity.floatValue());
			if (alpha <= 0)
				return Opacity.None;
			else if (alpha >= 255)
				return Opacity.Full;
			else
				return Opacity.Partial;
		}
	}

	static abstract class QuickDrawSimpleShape<S extends QuickSimpleShape> extends QuickDrawBorderedShape<S> {
		private final Observable<?> theUpdate;
		private Dimension theScreen;
		private final Rectangle theBounds;
		private AffineTransform theRotation;
		private AffineTransform theRotationInverse;

		protected QuickDrawSimpleShape(S shape) {
			super(shape);
			theBounds = new Rectangle();
			theUpdate = Observable.or(super.update(), shape.getRotation().noInitChanges(), shape.getWidth().noInitChanges(),
				shape.getHeight().noInitChanges());
		}

		public float getRotation() {
			Float rotation = getShape().getRotation().get();
			return rotation == null ? 0.0f : rotation.floatValue();
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public Point hit(Point containerPoint) {
			if (!isVisible())
				return null;

			setBounds();
			if (theRotationInverse != null) {
				Point2D.Float transformed = (Point2D.Float) theRotationInverse
					.transform(new Point2D.Float(containerPoint.x, containerPoint.y), new Point2D.Float(0, 0));
				containerPoint = new Point(Math.round(transformed.x), Math.round(transformed.y));
			}
			if (!theBounds.contains(containerPoint))
				return null;
			return getHit(containerPoint);
		}

		protected abstract Point getHit(Point point);

		@Override
		public void draw(Graphics2D gfx, Dimension screen) {
			if (!isVisible())
				return;

			theScreen = screen;
			setBounds();
			if (theRotation != null) {
				gfx.transform(theRotation);
			}
			try {
				doDraw(gfx, theBounds);
			} finally {
				if (theRotationInverse != null) {
					gfx.transform(theRotationInverse);
				}
			}
		}

		protected void setBounds() {
			float rotation = getRotation();
			Positionable hPos = getShape().getAddOn(Positionable.Horizontal.class);
			Positionable vPos = getShape().getAddOn(Positionable.Vertical.class);
			QuickSize specWidth = getShape().getWidth().get();
			QuickSize specHeight = getShape().getHeight().get();

			Point anchor = new Point();
			if (!evaluateDimension(x -> theBounds.x = x, w -> theBounds.width = w, a -> anchor.x = a, hPos, specWidth, theScreen.width,
				"width"))
				return;
			if (!evaluateDimension(y -> theBounds.y = y, h -> theBounds.height = h, a -> anchor.y = a, vPos, specHeight, theScreen.height,
				"height"))
				return;

			// The positioning determines the center of the rotation via the anchor.
			// E.g. If the user specifies the center of the shape, the center should stay where it is regardless of rotation.
			// Similarly if the specify the upper left corner, we should rotate around that.
			if (rotation == 0) {
				theRotation = theRotationInverse = null;
			} else {
				theRotation = AffineTransform.getRotateInstance(Math.cos(rotation), Math.sin(rotation), anchor.x, anchor.y);
				try {
					theRotationInverse = theRotation.createInverse();
				} catch (NoninvertibleTransformException e) {
					getShape().reporting().error(e.getMessage(), e);
				}
			}
		}

		protected boolean evaluateDimension(IntConsumer setPosition, IntConsumer setSize, IntConsumer setAnchor, Positionable positions,
			QuickSize specSize, int screenSize, String sizeName) {
			QuickSize leading = positions.getLeading().get();
			QuickSize center = positions.getCenter().get();
			QuickSize trailing = positions.getTrailing().get();
			int pos, size, anchor;
			// There are many ways of specifying the positioning and height of the shape here.
			// The user will get a warning if any of the attributes conflict,
			// but we still have to decide which attributes will take priority in that case.
			// We'll respect size first, then leading, then trailing, and center last.
			if (specSize != null) {
				size = specSize.evaluate(screenSize);
				if (leading != null)
					anchor = pos = leading.evaluate(screenSize);
				else if (trailing != null) {
					anchor = trailing.evaluate(screenSize);
					pos = anchor - size;
				} else if (center != null) {
					anchor = center.evaluate(screenSize);
					pos = anchor - size / 2;
				} else
					anchor = pos = 0;
			} else if (leading != null) {
				anchor = pos = leading.evaluate(screenSize);
				if (trailing != null) {
					anchor = trailing.evaluate(screenSize);
					size = anchor - pos;
				} else if (center != null) {
					anchor = center.evaluate(screenSize);
					size = (anchor - pos) * 2;
				} else {
					getShape().reporting()
					.warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
					return false;
				}
			} else if (center != null && trailing != null) { // Pretty weird, but we can deduce
				int centr = center.evaluate(screenSize);
				int trail = trailing.evaluate(screenSize);
				anchor = trail;
				pos = centr - (trail - centr);
				size = (trail - centr) * 2;
			} else {
				getShape().reporting()
				.warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
				return false;
			}
			if (size == 0)
				return false;
			else if (size < 0) {
				getShape().reporting().warn(StringUtils.capitalize(sizeName) + " evaluates negatively: " + size);
				return false;
			} else if (pos > screenSize || pos + size <= 0)
				return false;

			setPosition.accept(pos);
			setSize.accept(size);
			setAnchor.accept(anchor);
			return true;
		}

		protected abstract void doDraw(Graphics2D gfx, Rectangle bounds);
	}

	static class InterpretedRectangle extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickRectangle> {
		InterpretedRectangle(QuickRectangle.Interpreted<?> rectangle, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(rectangle, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickRectangle element) throws ModelInstantiationException {
			return new QuickDrawRectangle(element, getPublishers(element));
		}
	}

	static class QuickDrawRectangle extends QuickDrawSimpleShape<QuickRectangle> {
		private final SimpleShapeContainer theContainer;
		private final Observable<?> theUpdate;
		private CollectionElement<QuickShapeInterpretation> theSubFocus;

		QuickDrawRectangle(QuickRectangle rectangle, List<QuickDrawShapePublisher> contents) {
			super(rectangle);
			theContainer = new SimpleShapeContainer(contents, rectangle.onDestroy()) {
				@Override
				protected CollectionElement<QuickShapeInterpretation> getFocus() {
					return theSubFocus;
				}

				@Override
				protected void setFocus(CollectionElement<QuickShapeInterpretation> shape) {
					theSubFocus = shape;
				}
			};
			theUpdate = Observable.or(super.update(), theContainer.update());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		protected void doDraw(Graphics2D gfx, Rectangle bounds) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}

			if (!theContainer.getShapes().isEmpty()) {
				Graphics2D contentGfx = (Graphics2D) gfx.create(bounds.x, bounds.y, bounds.width, bounds.height);
				try {
					theContainer.draw(contentGfx, bounds.getSize());
				} finally {
					contentGfx.dispose();
				}
			}
		}

		@Override
		protected Point getHit(Point point) {
			return point;
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseEntered(e);
			if (hit == null)
				hit = super.mouseEntered(e);
			return hit;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseMoved(e);
			if (hit == null)
				hit = super.mouseMoved(e);
			return hit;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if (!isVisible())
				return;
			theContainer.mouseDragged(e);
			super.mouseDragged(e);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			if (!isVisible())
				return;
			theContainer.mouseExited(e);
			super.mouseExited(e);
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mousePressed(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mousePressed(e));
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseReleased(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseReleased(e));
			return opacity;
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseClicked(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseClicked(e));
			return opacity;
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseWheelMoved(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseWheelMoved(e));
			return opacity;
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyPressed(e))
				return true;
			return super.keyPressed(e);
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyReleased(e))
				return true;
			return super.keyReleased(e);
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyTyped(e))
				return true;
			return super.keyTyped(e);
		}

		@Override
		public String getTooltip() {
			String tooltip = theContainer.getTooltip();
			if (tooltip == null)
				tooltip = super.getTooltip();
			return tooltip;
		}
	}
}

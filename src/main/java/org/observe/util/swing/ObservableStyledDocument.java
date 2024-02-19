package org.observe.util.swing;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ListenerList;
import org.qommons.io.Format;

/**
 * Observable structure for a styled text document
 *
 * @param <T> The type of nodes in the structure
 */
public abstract class ObservableStyledDocument<T> implements Highlighter {
	private final ObservableValue<? extends T> theRoot;
	private final Format<T> theFormat;
	private final ThreadConstraint theThreading;

	private final DocumentNode theRootNode;
	private final Observable<?> theUntil;
	private final BgFontAdjuster theRootStyle;

	private final IdentityHashMap<T, DocumentNode> theNodesByValue;
	private final ListenerList<Consumer<? super ChangeEvent<T>>> theListeners;

	private Function<? super T, String> thePostNodeText;

	/**
	 * @param root The root node of the structure
	 * @param format The format to convert nodes to text
	 * @param threading The threading constraint for thread-safety in the structure
	 * @param until The observable to stop all listening
	 */
	protected ObservableStyledDocument(ObservableValue<? extends T> root, Format<T> format, ThreadConstraint threading,
		Observable<?> until) {
		theFormat = format;
		theThreading = threading;
		theUntil = until;
		theNodesByValue = new IdentityHashMap<>();

		theRootStyle = new BgFontAdjuster(new SimpleAttributeSet());
		theRoot = root.safe(theThreading, theUntil);
		theRootNode = new DocumentNode(null, theRoot.get());
		theListeners = ListenerList.build().build();

		if (EventQueue.isDispatchThread())
			theRoot.noInitChanges().takeUntil(theUntil).act(evt -> theRootNode.changed(evt.getNewValue(), evt, true, false));
		else
			EventQueue.invokeLater(() -> theRoot.noInitChanges().takeUntil(theUntil)
				.act(evt -> theRootNode.changed(evt.getNewValue(), Causable.broken(evt), true, false)));
	}

	public ObservableStyledDocument<T> withPostNodeText(Function<? super T, String> postText) {
		thePostNodeText = postText;
		if (EventQueue.isDispatchThread())
			theRootNode.changed(theRoot.get(), null, false, true);
		else
			EventQueue.invokeLater(() -> theRootNode.changed(theRoot.get(), null, false, true));
		return this;
	}

	/** @return The root node of the document */
	public DocumentNode getRoot() {
		return theRootNode;
	}

	public BgFontAdjuster getRootStyle() {
		return theRootStyle;
	}

	/** @return The root value of the document */
	public ObservableValue<? extends T> getRootValue() {
		return theRoot;
	}

	public void refresh(Object cause) {
		if (EventQueue.isDispatchThread())
			theRootNode.changed(theRoot.get(), cause, true, false);
		else
			EventQueue.invokeLater(() -> theRootNode.changed(theRoot.get(), Causable.broken(cause), true, false));
	}

	public void refresh(T value, Object cause) {
		DocumentNode node = theNodesByValue.get(value);
		if (node != null)
			node.changed(value, cause, true, false);
	}

	protected abstract ObservableCollection<? extends T> getChildren(T value);

	protected abstract void adjustStyle(T value, BgFontAdjuster style);

	protected String getPostText(T value) {
		return thePostNodeText == null ? null : thePostNodeText.apply(value);
	}

	public DocumentNode getNodeAt(int textPosition) {
		return theRootNode.getNodeAt(textPosition);
	}

	public DocumentNode getNodeFor(T value) {
		return theNodesByValue.get(value);
	}

	public Runnable addListener(Consumer<? super ChangeEvent<T>> listener) {
		return theListeners.add(listener, true);
	}

	void fireChange(DocumentNode node, CollectionChangeType type, boolean deep, Object cause) {
		ChangeEvent<T> event = new ChangeEvent<>(node, type, deep, node.getStart(), cause);
		try (Transaction t = event.use()) {
			theListeners.forEach(//
				l -> l.accept(event));
		}
	}

	// Highlighter methods

	private JTextComponent theEditor;

	@Override
	public void install(JTextComponent c) {
		theEditor = c;
	}

	@Override
	public void deinstall(JTextComponent c) {
		theEditor = null;
	}

	@Override
	public Object addHighlight(int p0, int p1, HighlightPainter p) throws BadLocationException {
		return this;
	}

	@Override
	public void removeHighlight(Object tag) {
	}

	@Override
	public void removeAllHighlights() {
	}

	@Override
	public void changeHighlight(Object tag, int p0, int p1) throws BadLocationException {
	}

	@Override
	public Highlight[] getHighlights() {
		return new Highlight[0];
	}

	@Override
	public void paint(Graphics g) {
		if (theEditor == null)
			return;
		Rectangle clip = g.getClipBounds();
		int top = clip.y;
		int textPosition = theEditor.viewToModel(new Point(0, top));

		paintNode(g, theRootNode, textPosition, 0, clip.x + clip.width, clip.y + clip.height);
	}

	private boolean paintNode(Graphics g, DocumentNode node, int index, int modelOffset, int width,
		int maxH) {
		Color nodeColor = node.getBackground();
		if (nodeColor != null)
			g.setColor(nodeColor);
		if (index < node.getLocalText().length()) {
			if (nodeColor != null && paintText(g, node.getLocalText(), index, modelOffset, width) > maxH)
				return false;
			index = 0;
		} else
			index -= node.getLocalText().length();
		modelOffset += node.getLocalText().length();
		for (DocumentNode child : node.getChildNodes()) {
			if (index < child.length()) {
				if (!paintNode(g, child, index, modelOffset, width, maxH))
					return false;
				index = 0;
			} else
				index -= child.length();
			modelOffset += child.length();
		}
		if (nodeColor != null && index < node.getPostText().length()) {
			g.setColor(nodeColor);
			if (paintText(g, node.getPostText(), index, modelOffset, width) > maxH)
				return false;
		}
		return true;
	}

	private int paintText(Graphics g, String text, int start, int modelOffset, int width) {
		Rectangle startBounds;
		try {
			startBounds = theEditor.modelToView(modelOffset + start);
		} catch (BadLocationException e) {
			return -1;
		}
		int lastStart = start;
		int nextNewLine = text.indexOf('\n', start);
		while (nextNewLine >= 0) {
			paintTextRect(g, startBounds.x, startBounds.y, width - startBounds.x, startBounds.height);
			lastStart = nextNewLine + 1;
			try {
				startBounds = theEditor.modelToView(modelOffset + lastStart);
			} catch (BadLocationException e) {
				return -1;
			}
			nextNewLine = text.indexOf('\n', lastStart);
		}
		if (lastStart < text.length()) {
			try {
				Rectangle endBounds = theEditor.modelToView(modelOffset + text.length());
				if (endBounds.y > startBounds.y) {
					paintTextRect(g, startBounds.x, startBounds.y, width - startBounds.x, startBounds.height);
					if (endBounds.y > startBounds.y + startBounds.height)
						g.fillRect(0, startBounds.y + startBounds.height, width, endBounds.y - startBounds.y - startBounds.height);
					if (endBounds.x > CHAR_START_TOLERANCE)
						g.fillRect(0, endBounds.y, endBounds.x, endBounds.height);
				} else
					paintTextRect(g, startBounds.x, startBounds.y, endBounds.x - startBounds.x, startBounds.height);
				return endBounds.y;
			} catch (BadLocationException e) {
			}
		}
		return -1;
	}

	private static final int CHAR_START_TOLERANCE = 6;

	private static void paintTextRect(Graphics g, int x, int y, int width, int height) {
		if (x <= CHAR_START_TOLERANCE) // Make sure we paint all the way to the start for the first character
			g.fillRect(0, y, width + x, height);
		else
			g.fillRect(x, y, width, height);
	}

	public class DocumentNode {
		private final DocumentNode theParent;
		private T theValue;
		private String theText;
		private String thePostText;
		private int theContentLength;

		private String thePreviousText;
		private String thePreviousPostText;

		private final SimpleObservable<Void> theNodeUntil;
		private ObservableCollection<? extends T> theChildren;
		private final List<DocumentNode> theChildNodes;

		private Color theBackground;

		DocumentNode(DocumentNode parent, T value) {
			theParent = parent;
			theNodeUntil = new SimpleObservable<>();
			theChildNodes = new ArrayList<>();
			theText = thePostText = thePreviousText = thePreviousPostText = "";
			changed(value, null, false, false);
		}

		public DocumentNode getParent() {
			return theParent;
		}

		public T getValue() {
			return theValue;
		}

		public String getLocalText() {
			return theText;
		}

		public String getPostText() {
			return thePostText;
		}

		public List<DocumentNode> getChildNodes() {
			return Collections.unmodifiableList(theChildNodes);
		}

		public int getStart() {
			if (theParent == null)
				return 0;
			int start = theParent.getStart();
			if (theParent.theText != null)
				start += theParent.theText.length();
			for (DocumentNode parentChild : theParent.theChildNodes) {
				if (parentChild == this)
					break;
				start += parentChild.theContentLength;
			}
			return start;
		}

		public int length() {
			return theContentLength;
		}

		public String getPreviousText() {
			return thePreviousText;
		}

		public String getPreviousPostText() {
			return thePreviousPostText;
		}

		public BgFontAdjuster getStyle() {
			BgFontAdjuster style;
			if (theParent == null)
				style = theRootStyle.clone();
			else
				style = theParent.getStyle();
			adjustStyle(style);
			return style;
		}

		public void adjustStyle(BgFontAdjuster style) {
			ObservableStyledDocument.this.adjustStyle(theValue, style);
			theBackground = style.getBackground();
		}

		public DocumentNode getNodeAt(int textPosition) {
			if (textPosition < 0 || textPosition > theContentLength)
				return null;
			if (theText != null) {
				if (textPosition < theText.length())
					return this;
				textPosition -= theText.length();
			}
			for (DocumentNode child : theChildNodes) {
				if (textPosition < child.theContentLength)
					return child.getNodeAt(textPosition);
				else
					textPosition -= child.theContentLength;
			}
			// In the post-text. The post-text properly belongs to the parent
			return theParent;
		}

		public Color getBackground() {
			return theBackground;
		}

		void changed(T value, Object cause, boolean withEvents, boolean deep) {
			if (theValue != null)
				theNodesByValue.remove(theValue);
			theValue = value;
			if (value != null)
				theNodesByValue.put(value, this);
			String text = theFormat.format(theValue);
			if (text == null)
				text = "";
			int[] lengthDiff = new int[1];
			if (!theText.equals(text)) {
				lengthDiff[0] = text.length() - theText.length();
				theText = text;
			}
			boolean deepChange = false;
			ObservableCollection<? extends T> children = getChildren(theValue);
			if (children == null) {
				if (theChildren != null) {
					if (!theChildNodes.isEmpty())
						deepChange = true;
					lengthDiff[0] -= removed();
				}
			} else if (theChildren == null || !children.getIdentity().equals(theChildren.getIdentity())) {
				deepChange = !theChildNodes.isEmpty();
				lengthDiff[0] -= removed();
				theChildren = children;
				ObservableCollection<? extends T> safeChildren = theChildren.safe(theThreading, Observable.or(theUntil, theNodeUntil));
				// Since this can only be called from the EDT, we can assume that safeChildren can be used like this safely
				CollectionUtils.synchronize(theChildNodes, safeChildren, (node, v) -> Objects.equals(node.getValue(), v))
				.simple(v -> new DocumentNode(this, v))//
				.onLeft(el -> lengthDiff[0] -= el.getLeftValue().theContentLength)//
				.onRight(el -> lengthDiff[0] += el.getLeftValue().theContentLength)//
				.onCommon(el -> {
					lengthDiff[0] -= el.getLeftValue().theContentLength;
					el.getLeftValue().changed(el.getRightValue(), cause, false, true);
					lengthDiff[0] += el.getLeftValue().theContentLength;
				})//
				.adjust();
				Subscription childSub = safeChildren.onChange(evt -> {
					switch (evt.getType()) {
					case add:
						DocumentNode node = new DocumentNode(this, evt.getNewValue());
						theChildNodes.add(evt.getIndex(), node);
						contentLengthChanged(node.theContentLength);
						fireChange(node, CollectionChangeType.add, false, evt);
						node.syncLengths();
						break;
					case remove:
						node = theChildNodes.remove(evt.getIndex());
						contentLengthChanged(-node.removed());
						fireChange(node, CollectionChangeType.remove, true, evt);
						break;
					case set:
						node = theChildNodes.get(evt.getIndex());
						node.changed(evt.getNewValue(), evt, true, false);
						break;
					}
				});
				deepChange |= !theChildNodes.isEmpty();
				theNodeUntil.take(1).act(__ -> childSub.unsubscribe());
			} else if (deep) {
				deepChange = true;
				for (DocumentNode child : theChildNodes)
					child.changed(child.getValue(), cause, false, true);
			}
			String postText = ObservableStyledDocument.this.getPostText(value);
			if (postText == null)
				postText = "";
			if (!thePostText.equals(postText)) {
				lengthDiff[0] += postText.length() - thePostText.length();
				thePostText = postText;
			}
			if (lengthDiff[0] != 0) {
				if (!withEvents)
					theContentLength += lengthDiff[0];
				else
					contentLengthChanged(lengthDiff[0]);
			}
			if (withEvents)
				fireChange(this, CollectionChangeType.set, deepChange, cause);
			syncLengths();
		}

		private void syncLengths() {
			thePreviousText = theText;
			thePreviousPostText = thePostText;
		}

		int removed() {
			theNodeUntil.onNext(null);
			theChildren = null;
			int removedLen = 0;
			for (DocumentNode child : theChildNodes) {
				removedLen += child.theContentLength;
				child.removed();
			}
			theChildNodes.clear();
			return removedLen;
		}

		void contentLengthChanged(int diff) {
			theContentLength += diff;
			if (theParent != null)
				theParent.contentLengthChanged(diff);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	public static class ChangeEvent<T> extends Causable.AbstractCausable {
		private final ObservableStyledDocument<? extends T>.DocumentNode theNode;
		private final CollectionChangeType theType;
		private final boolean isDeep;
		private final int theIndex;

		public ChangeEvent(ObservableStyledDocument<? extends T>.DocumentNode node, CollectionChangeType type, boolean deep, int index,
			Object cause) {
			super(cause);
			theNode = node;
			theType = type;
			isDeep = deep;
			theIndex = index;
		}

		public ObservableStyledDocument<? extends T>.DocumentNode getNode() {
			return theNode;
		}

		public CollectionChangeType getType() {
			return theType;
		}

		public boolean isDeep() {
			return isDeep;
		}

		public int getIndex() {
			return theIndex;
		}
	}

	public static void synchronize(ObservableStyledDocument<?> obsDoc, StyledDocument swingDoc, Observable<?> until) {
		try {
			if (swingDoc.getLength() > 0)
				swingDoc.remove(0, swingDoc.getLength());
			renderSwingNode(//
				obsDoc.getRoot(), true, swingDoc, 0, new BgFontAdjuster(new SimpleAttributeSet()), SimpleAttributeSet.EMPTY, true);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}
		Runnable remove = obsDoc.addListener(evt -> {
			try {
				switch (evt.getType()) {
				case add:
				case set:
					BgFontAdjuster style;
					if (evt.getNode().getParent() == null)
						style = new BgFontAdjuster(new SimpleAttributeSet());
					else
						style = evt.getNode().getParent().getStyle();
					AttributeSet parentStyle = new SimpleAttributeSet(style.getFontAttributes());
					renderSwingNode(evt.getNode(), evt.isDeep(), swingDoc, evt.getIndex(), style, parentStyle,
						evt.getType() == CollectionChangeType.add);
					break;
				case remove:
					if (!evt.getNode().getPreviousText().isEmpty())
						swingDoc.remove(evt.getIndex(), evt.getNode().getPreviousText().length());
					if (!evt.getNode().getPreviousPostText().isEmpty()) {
						int postTextIndex = evt.getIndex() + evt.getNode().length() - evt.getNode().getPreviousPostText().length();
						swingDoc.remove(postTextIndex, evt.getNode().getPreviousPostText().length());
					}
					break;
				}
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		});
		if (until != null)
			until.take(1).act(__ -> remove.run());
	}

	private static void renderSwingNode(ObservableStyledDocument<?>.DocumentNode node, boolean deep, StyledDocument swingDoc, int index,
		BgFontAdjuster style, AttributeSet parentStyle, boolean init) throws BadLocationException {
		node.adjustStyle(style);
		SimpleAttributeSet styleCopy = new SimpleAttributeSet(style.getFontAttributes());
		if (init) {
			if (!node.getLocalText().isEmpty())
				swingDoc.insertString(index, node.getLocalText(), styleCopy);
		} else if (!node.getLocalText().equals(node.getPreviousText())) {
			if (!node.getPreviousText().isEmpty())
				swingDoc.remove(index, node.getPreviousText().length());
			if (!node.getLocalText().isEmpty())
				swingDoc.insertString(index, node.getLocalText(), styleCopy);
		}
		if (!node.getLocalText().isEmpty())
			swingDoc.setCharacterAttributes(index, node.getLocalText().length(), styleCopy, init);

		int offset = node.getLocalText().length();
		if (deep) {
			for (ObservableStyledDocument<?>.DocumentNode child : node.getChildNodes()) {
				BgFontAdjuster childStyle;
				if (node.getChildNodes().size() > 1)
					childStyle = style.clone();
				else
					childStyle = style;
				renderSwingNode(child, true, swingDoc, index + offset, childStyle, styleCopy, init);
				offset += child.length();
			}
		} else {
			for (ObservableStyledDocument<?>.DocumentNode child : node.getChildNodes())
				offset += child.length();
		}

		// The post-text properly belongs to the parent
		if (init) {
			if (!node.getPostText().isEmpty())
				swingDoc.insertString(index + offset, node.getPostText(), parentStyle);
		} else if (!node.getPostText().equals(node.getPreviousPostText())) {
			if (!node.getPreviousPostText().isEmpty())
				swingDoc.remove(index + offset, node.getPreviousPostText().length());
			if (!node.getPostText().isEmpty())
				swingDoc.insertString(index + offset, node.getPostText(), parentStyle);
		}
		if (!node.getPostText().isEmpty()) {
			swingDoc.setCharacterAttributes(index + offset, node.getPostText().length(), parentStyle, true);
			offset += node.getPostText().length();
		}
	}
}

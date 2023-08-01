package org.observe.util.swing;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
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

public abstract class ObservableStyledDocument<T> {
	private final ObservableValue<? extends T> theRoot;
	private final Format<T> theFormat;
	private final ThreadConstraint theThreading;

	private final DocumentNode theRootNode;
	private final Observable<?> theUntil;
	private final BgFontAdjuster theRootStyle;

	private final IdentityHashMap<T, DocumentNode> theNodesByValue;
	private final ListenerList<Consumer<? super ChangeEvent<T>>> theListeners;

	private Function<? super T, String> thePostNodeText;

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
			EventQueue.invokeLater(
				() -> theRoot.noInitChanges().takeUntil(theUntil).act(evt -> theRootNode.changed(evt.getNewValue(), evt, true, false)));
	}

	public ObservableStyledDocument<T> withPostNodeText(Function<? super T, String> postText) {
		thePostNodeText = postText;
		if (EventQueue.isDispatchThread())
			theRootNode.changed(theRoot.get(), null, false, true);
		else
			EventQueue.invokeLater(() -> theRootNode.changed(theRoot.get(), null, false, true));
		return this;
	}

	public DocumentNode getRoot() {
		return theRootNode;
	}

	public BgFontAdjuster getRootStyle() {
		return theRootStyle;
	}

	public ObservableValue<? extends T> getRootValue() {
		return theRoot;
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
		} else if (node.getLocalText().equals(node.getPreviousText())) {
			swingDoc.setCharacterAttributes(index, node.getLocalText().length(), styleCopy, true);
		} else {
			if (!node.getPreviousText().isEmpty())
				swingDoc.remove(index, node.getPreviousText().length());
			if (!node.getLocalText().isEmpty())
				swingDoc.insertString(index, node.getLocalText(), styleCopy);
		}

		int offset = node.getLocalText().length();
		if (deep) {
			BgFontAdjuster childStyle;
			if (node.getChildNodes().size() > 1)
				childStyle = style.clone();
			else
				childStyle = style;
			for (ObservableStyledDocument<?>.DocumentNode child : node.getChildNodes()) {
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
		} else if (node.getPostText().equals(node.getPreviousPostText())) {
			swingDoc.setCharacterAttributes(index + offset, node.getPostText().length(), parentStyle, true);
		} else {
			if (!node.getPreviousPostText().isEmpty())
				swingDoc.remove(index + offset, node.getPreviousPostText().length());
			if (!node.getPostText().isEmpty())
				swingDoc.insertString(index + offset, node.getPostText(), parentStyle);
		}
	}
}

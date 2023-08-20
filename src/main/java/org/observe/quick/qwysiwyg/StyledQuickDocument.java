package org.observe.quick.qwysiwyg;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Colors;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.io.FilePosition;
import org.qommons.io.LocatedFilePosition;

public class StyledQuickDocument {
	private final String theDocumentURL;
	private final String theDocumentContent;
	private final DocumentComponent theRoot;
	private final Consumer<DocumentComponent> theLinkFollower;

	private final SettableValue<ObservableValue<String>> theTooltip;
	private final SettableValue<DocumentComponent> theHovered;

	private DocumentComponent theHoveredComponent;
	private boolean isControlPressed;

	public StyledQuickDocument(String documentURL, String content, Consumer<DocumentComponent> linkFollower) {
		theDocumentURL = documentURL;
		theDocumentContent = content;
		theLinkFollower = linkFollower;
		theTooltip = SettableValue
			.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<String>> parameterized(String.class)).build();
		theHovered = SettableValue.build(DocumentComponent.class).build();

		theRoot = new DocumentComponent(null, FilePosition.START);
	}

	public DocumentComponent getRoot() {
		return theRoot;
	}

	public ObservableValue<String> getTooltip() {
		return ObservableValue.flatten(theTooltip);
	}

	public SettableValue<DocumentComponent> getHovered() {
		return theHovered;
	}

	public void setHoveredComponent(DocumentComponent hoveredComp, boolean ctrlPressed) {
		boolean ctrlChanged = ctrlPressed != isControlPressed;
		isControlPressed = ctrlPressed;
		if (hoveredComp == theHoveredComponent) {
			if (ctrlChanged)
				theHoveredComponent.update();
			return;
		}
		if (theHoveredComponent != null && theHoveredComponent != hoveredComp) {
			if (theHoveredComponent.elementHovered != null)
				theHoveredComponent.elementHovered.set(false, null);
			else if (theHoveredComponent.opposite != null && theHoveredComponent.opposite.elementHovered != null)
				theHoveredComponent.opposite.elementHovered.set(false, null);
		}
		// System.out.println("Hovered: " + //
		// (theHoveredComponent == null ? "null" : (theHoveredComponent.print() + "=" + theHoveredComponent.element)) + "->" //
		// + (hoveredComp == null ? "null" : (hoveredComp.print() + "=" + hoveredComp.element)));
		DocumentComponent oldHovered = theHoveredComponent;
		theHoveredComponent = hoveredComp;
		if (hoveredComp == null) {
			if (oldHovered != null)
				oldHovered.update();
			theHovered.set(hoveredComp, null);
			theTooltip.set(null, null);
			return;
		}
		ObservableValue<String> tt = hoveredComp.getTooltip();
		// System.out.println("hovered=" + hoveredComp + ", tt=" + (tt == null ? "null" : tt.get()));
		theTooltip.set(tt, null);
		if (isControlPressed)
			hoveredComp.update();
		theHovered.set(hoveredComp, null);
		if (hoveredComp.elementHovered != null)
			hoveredComp.elementHovered.set(true, null);
		else if (hoveredComp.opposite != null && hoveredComp.opposite.elementHovered != null)
			hoveredComp.opposite.elementHovered.set(true, null);
	}

	public void setCtrlPressed(boolean ctrl) {
		if (ctrl == isControlPressed)
			return;
		isControlPressed = ctrl;
		if (theHoveredComponent != null)
			theHoveredComponent.update();
	}

	public class DocumentComponent {
		public final DocumentComponent parent;
		ElementId parentChild;
		public final FilePosition start;
		private FilePosition end;
		boolean bold;
		Color fontColor;
		public final ObservableCollection<DocumentComponent> children;
		DocumentComponent opposite;
		LocatedFilePosition target;
		Object descriptor;
		ExElement.Interpreted<?> interpreted;
		ExElement element;

		SettableValue<Boolean> elementHovered;

		String typeTooltip;
		Supplier<String> interpretedTooltip;
		ObservableValue<String> instanceTooltip;

		DocumentComponent(DocumentComponent parent, FilePosition start) {
			this.parent = parent;
			this.start = start;
			children = ObservableCollection.build(DocumentComponent.class).build();
			children.onChange(evt -> {
				if (evt.getType() == CollectionChangeType.add)
					evt.getNewValue().parentChild = evt.getElementId();
			});
			end = null;
		}

		public FilePosition getEnd() {
			return end;
		}

		public boolean isBold() {
			return bold;
		}

		public boolean isActiveLink() {
			return target != null && theHoveredComponent == this && isControlPressed && isEqual(target.getFileLocation(), theDocumentURL);
		}

		boolean isEqual(String fileLocation, String docURL) {
			return docURL.endsWith(fileLocation);
		}

		public Color getFontColor() {
			return fontColor;
		}

		public String getPostText() {
			int domainEnd;
			if (parent == null)
				domainEnd = theDocumentContent.length();
			else {
				DocumentComponent nextSib = CollectionElement.get(parent.children.getAdjacentElement(parentChild, true));
				if (nextSib != null)
					domainEnd = nextSib.start.getPosition();
				else if (parent.end != null)
					domainEnd = parent.end.getPosition();
				else
					domainEnd = theDocumentContent.length();
			}

			int endPos;
			if (end != null)
				endPos = end.getPosition();
			else
				return null;
			if (endPos == domainEnd)
				return null;
			return theDocumentContent.substring(endPos, domainEnd);
		}

		void update() {
			if (parentChild != null)
				parent.children.mutableElement(parentChild).set(this);
		}

		DocumentComponent addChild(FilePosition childStart) {
			if (childStart instanceof LocatedFilePosition)
				childStart = new FilePosition(childStart.getPosition(), childStart.getLineNumber(), childStart.getCharNumber());
			if (start.getPosition() > childStart.getPosition())
				throw new IllegalArgumentException("Adding child before the parent");
			else if (!children.isEmpty() && childStart.getPosition() < children.getLast().end.getPosition())
				throw new IllegalArgumentException("Adding child before end of the previous one");

			DocumentComponent child = new DocumentComponent(this, childStart);
			children.add(child);
			return child;
		}

		DocumentComponent end(FilePosition e) {
			if (e instanceof LocatedFilePosition)
				e = new FilePosition(e.getPosition(), e.getLineNumber(), e.getCharNumber());
			if (e.getPosition() < start.getPosition())
				throw new IllegalArgumentException("Ending before beginning");
			if (children != null && !children.isEmpty() && e.getPosition() < children.get(children.size() - 1).end.getPosition())
				throw new IllegalArgumentException("Ending parent before last child");
			if (parent != null && parent.end != null && parent.end.getPosition() > 0 && e.getPosition() > parent.end.getPosition())
				throw new IllegalArgumentException("Ending child after parent");
			this.end = e;
			return this;
		}

		DocumentComponent setOpposite(DocumentComponent opposite) {
			if (this.opposite != null)
				this.opposite.opposite = null;
			this.opposite = opposite;
			if (opposite != null)
				opposite.opposite = this;
			return this;
		}

		DocumentComponent color(Color color) {
			fontColor = color;
			return this;
		}

		ObservableValue<String> getTooltip() {
			String intTT = interpretedTooltip == null ? null : interpretedTooltip.get();
			if (typeTooltip == null && intTT == null && instanceTooltip == null)
				return null;
			StringBuilder str = new StringBuilder("<html>");
			if (typeTooltip != null) {
				str.append(typeTooltip);
				if (interpretedTooltip != null)
					str.append("<br><br>").append(intTT);
			} else
				str.append(intTT);
			if (instanceTooltip == null)
				return ObservableValue.of(str.toString());
			else {
				int preLen = str.length();
				return instanceTooltip.map(instTT -> {
					str.setLength(preLen);
					if (instTT != null)
						str.append("<br><br>Current Value: ").append(instTT);
					return str.toString();
				});
			}
		}

		DocumentComponent typeTooltip(String tt) {
			typeTooltip = tt;
			return this;
		}

		DocumentComponent interpretedTooltip(Supplier<String> tt) {
			interpretedTooltip = tt;
			return this;
		}

		DocumentComponent instanceTooltip(ObservableValue<String> tt) {
			instanceTooltip = tt;
			return this;
		}

		public void followLink() {
			System.out.println("Go to " + target);
			if (theLinkFollower != null) {
				DocumentComponent targetComponent = getSourceComponent(theRoot, target.getPosition());
				theLinkFollower.accept(targetComponent);
			}
		}

		public String print() {
			return "\"" + toString() + start;
		}

		public StringBuilder printHierarchy(StringBuilder str, int indent) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(start).append("..").append(end).append('(');
			if (descriptor != null)
				str.append("d:").append(descriptor).append(' ');
			if (fontColor != null)
				str.append("fc:").append(Colors.toString(fontColor)).append(' ');
			str.append("): ");

			str.append(toString().replace("\t", "\\t").replace("\n", "\\n")).append('\n');

			for (DocumentComponent child : children)
				child.printHierarchy(str, indent + 1);

			String post = getPostText();
			if (post != null) {
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(post.replace("\t", "\\t").replace("\n", "\\n")).append('\n');
			}

			return str;
		}

		int getTextLength() {
			int textEnd;
			if (!children.isEmpty())
				textEnd = children.getFirst().start.getPosition();
			else if (end != null)
				textEnd = end.getPosition();
			else
				textEnd = theDocumentContent.length();
			return textEnd - start.getPosition();
		}

		@Override
		public String toString() {
			int textEnd;
			if (!children.isEmpty())
				textEnd = children.getFirst().start.getPosition();
			else if (end != null)
				textEnd = end.getPosition();
			else
				textEnd = theDocumentContent.length();
			return theDocumentContent.substring(start.getPosition(), textEnd);
		}
	}

	public static DocumentComponent getSourceComponent(DocumentComponent parent, int sourcePosition) {
		if (parent.start.getPosition() == sourcePosition)
			return parent;
		if (!parent.children.isEmpty()) {
			int childIdx = ArrayUtils.binarySearch(parent.children, child -> Integer.compare(sourcePosition, child.start.getPosition()));
			if (childIdx < 0) {
				childIdx = -childIdx - 2;
				if (childIdx < 0 || sourcePosition >= parent.children.get(childIdx).getEnd().getPosition())
					return parent;
				return getSourceComponent(parent.children.get(childIdx), sourcePosition);
			} else
				return parent.children.get(childIdx);
		}
		return parent;
	}
}

package org.observe.quick.base;

import java.awt.Color;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.quick.style.FontStyleParser;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * A character sequence, sections of which may be styled. This is an easy way of creating a styled document and may be used as the root of a
 * &lt;dynamic-styled-document>. The quick-base.qss style sheet contains styles for nodes of this type (but it must be invoked explicitly by
 * the &lt;text-style> in the document).
 */
public class SimpleStyledTextModel implements CharSequence, Appendable {
	private static final int HAS_UNDERLINE_MASK = 1;
	private static final int HAS_STRIKE_THROUGH_MASK = 4;
	private static final int HAS_SUPER_SCRIPT_MASK = 16;
	private static final int HAS_SUB_SCRIPT_MASK = 64;

	/**
	 * A style attribute for text in a {@link SimpleStyledTextModel}
	 *
	 * @param <T> The type of the style value
	 */
	public interface Attribute<T> extends Supplier<T> {
		/**
		 * @param value The value for the attribute
		 * @return The text model
		 */
		SimpleStyledTextModel set(T value);

		/** @return Whether the attribute is set for the text model */
		default boolean hasAttribute() {
			return get() != null;
		}

		/**
		 * Clears the value of this attribute
		 *
		 * @return The text model
		 */
		default SimpleStyledTextModel clear() {
			return set(null);
		}
	}

	/** A double-type {@link SimpleStyledTextModel} text style attribute */
	public interface DoubleAttribute extends DoubleSupplier {
		/** @return The current value of the attribute */
		double get();

		@Override
		default double getAsDouble() {
			return get();
		}

		/**
		 * @param value The value for the attribute
		 * @return The text model
		 */
		SimpleStyledTextModel set(double value);

		/** @return Whether the attribute is set for the text model */
		default boolean hasAttribute() {
			return !Double.isNaN(get());
		}

		/**
		 * Clears the value of this attribute
		 *
		 * @return The text model
		 */
		default SimpleStyledTextModel clear() {
			return set(Double.NaN);
		}
	}

	/** A boolean-type {@link SimpleStyledTextModel} text style attribute */
	public interface BooleanAttribute extends BooleanSupplier {
		/** @return Whether the attribute is set for the text model */
		boolean hasAttribute();

		/** @return The current value of the attribute */
		boolean get();

		@Override
		default boolean getAsBoolean() {
			return get();
		}

		/**
		 * @param value The value for the attribute
		 * @return The text model
		 */
		SimpleStyledTextModel set(boolean value);

		/**
		 * Clears the value of this attribute
		 *
		 * @return The text model
		 */
		SimpleStyledTextModel clear();
	}

	/** @return The root value for a dynamic styled document containing a {@link SimpleStyledTextModel} */
	public static ObservableValue<SimpleStyledTextModel> createRoot() {
		SettableValue<SimpleStyledTextModel> value = SettableValue.build(SimpleStyledTextModel.class).build();
		value.set(new Root(value), null);
		return value.unsettable();
	}

	private final Root theRoot;
	private final SimpleStyledTextModel theParent;
	private Consumer<SimpleStyledTextModel> theParentChild;
	private final ObservableCollection<SimpleStyledTextModel> theChildren;
	private boolean isInBatch;
	private Transaction theChildBatch;
	private final StringBuilder theText;
	private Color theBgColor;
	private Color theFgColor;
	private double theFontWeight;
	private double theFontSize;
	private double theFontSlant;
	private int theBooleanAttrs;

	/** @param parent The parent text model of this sub sequence */
	protected SimpleStyledTextModel(SimpleStyledTextModel parent) {
		theParent = parent;
		if (this instanceof Root)
			theRoot = (Root) this;
		else
			theRoot = parent.theRoot;
		isInBatch = theRoot.isInBatch();
		theChildren = ObservableCollection.build(SimpleStyledTextModel.class).build();
		theChildren.onChange(evt -> {
			switch (evt.getType()) {
			case add:
				evt.getNewValue().asChild(theChildren.mutableElement(evt.getElementId())::set);
				break;
			case remove:
				evt.getOldValue().asChild(null);
				break;
			default:
				break;
			}
		});
		theText = new StringBuilder();
		reset();
	}

	/** @return The parent text model of this sub-sequence */
	public SimpleStyledTextModel getParent() {
		return theParent;
	}

	/** @return The children of this sequence */
	public ObservableCollection<SimpleStyledTextModel> getChildren() {
		return theChildren;
	}

	void asChild(Consumer<SimpleStyledTextModel> child) {
		theParentChild = child;
	}

	/**
	 * Starts a batch transaction
	 *
	 * @return A Transaction to {@link Transaction#close() close} when modifications are finished
	 */
	public Transaction batch() {
		if (isInBatch)
			return Transaction.NONE;
		theRoot.addToBatch(this);
		if (theChildBatch == null) {
			theChildBatch = theChildren.lock(true, null);
			theRoot.addToBatch(this);
		}
		return theRoot.batch();
	}

	/**
	 * Executes an action on this model in a {@link #batch()}
	 *
	 * @param action The action to execute
	 * @return This model
	 */
	public SimpleStyledTextModel inBatch(Consumer<SimpleStyledTextModel> action) {
		try (Transaction t = batch()) {
			action.accept(this);
		}
		return this;
	}

	/** Called when this model is changed */
	protected void changed() {
		if (isInBatch) {
		} else if (theRoot.isInBatch()) {
			isInBatch = true;
			theRoot.addToBatch(this);
		} else if (theParentChild != null)
			theParentChild.accept(this);
	}

	/** Called from the {@link Transaction#close()} method of the transaction returned by {@link #batch()} */
	protected void doChanged() {
		if (theChildBatch != null) {
			theChildBatch.close();
			theChildBatch = null;
		}
		if (theParentChild != null)
			theParentChild.accept(this);
	}

	/**
	 * Clears this model of both style and text
	 *
	 * @return This model
	 */
	public SimpleStyledTextModel clearAll() {
		try (Transaction t = batch()) {
			reset();
			changed();
		}
		return this;
	}

	/** Clears this mdoel of both style and text, but doesn't fire a change event */
	protected void reset() {
		theText.setLength(0);
		theChildren.clear();
		theBgColor = null;
		theFgColor = null;
		theFontWeight = Double.NaN;
		theFontSize = Double.NaN;
		theFontSlant = Double.NaN;
		theBooleanAttrs = 0;
	}

	/** Clears this models' text */
	public void clearText() {
		if (theText.length() == 0)
			return;
		theText.setLength(0);
		changed();
	}

	/**
	 * Clears this model's style
	 *
	 * @param deep Whether to also clear the style of this model's children
	 */
	public void clearStyle(boolean deep) {
		theBgColor = null;
		theFgColor = null;
		theFontWeight = Double.NaN;
		theFontSize = Double.NaN;
		theFontSlant = Double.NaN;
		theBooleanAttrs = 0;
		changed();

		if (deep) {
			for (SimpleStyledTextModel child : getChildren())
				child.clearStyle(true);
		}
	}

	@Override
	public int length() {
		return theText.length();
	}

	@Override
	public char charAt(int index) {
		return theText.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return theText.subSequence(start, end);
	}

	@Override
	public SimpleStyledTextModel append(CharSequence csq) {
		if (csq.length() == 0)
			return this;
		theText.append(csq);
		changed();
		return this;
	}

	@Override
	public SimpleStyledTextModel append(CharSequence csq, int start, int end) {
		if (start == end)
			return this;
		theText.append(csq, start, end);
		changed();
		return this;
	}

	@Override
	public SimpleStyledTextModel append(char c) {
		theText.append(c);
		changed();
		return this;
	}

	/** @return The background of this model */
	public Attribute<Color> bg() {
		return new Attribute<Color>() {
			@Override
			public Color get() {
				return theBgColor;
			}

			@Override
			public SimpleStyledTextModel set(Color value) {
				if (!Objects.equals(theBgColor, value)) {
					theBgColor = value;
					changed();
				}
				return SimpleStyledTextModel.this;
			}
		};
	}

	/** @return The foreground (text color) of this model */
	public Attribute<Color> fg() {
		return new Attribute<Color>() {
			@Override
			public Color get() {
				return theFgColor;
			}

			@Override
			public SimpleStyledTextModel set(Color value) {
				if (!Objects.equals(theFgColor, value)) {
					theFgColor = value;
					changed();
				}
				return SimpleStyledTextModel.this;
			}
		};
	}

	/** @return The font weight of this model */
	public DoubleAttribute fontWeight() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontWeight;
			}

			@Override
			public SimpleStyledTextModel set(double value) {
				if (theFontWeight != value) {
					theFontWeight = value;
					changed();
				}
				return SimpleStyledTextModel.this;
			}
		};
	}

	/**
	 * Sets this model's {@link #fontWeight()} to bold
	 *
	 * @return This model
	 */
	public SimpleStyledTextModel bold() {
		return fontWeight().set(FontStyleParser.bold);
	}

	/** @return The font size of this model */
	public DoubleAttribute fontSize() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontSize;
			}

			@Override
			public SimpleStyledTextModel set(double value) {
				theFontSize = value;
				changed();
				return SimpleStyledTextModel.this;
			}
		};
	}

	/** @return The font slant of this model */
	public DoubleAttribute fontSlant() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontSlant;
			}

			@Override
			public SimpleStyledTextModel set(double value) {
				theFontSlant = value;
				changed();
				return SimpleStyledTextModel.this;
			}
		};
	}

	/**
	 * Sets this model's {@link #fontSlant()} to italic
	 *
	 * @return This model
	 */
	public SimpleStyledTextModel italic() {
		return fontSlant().set(FontStyleParser.italic);
	}

	/** @return Whether text in this model is underlined */
	public BooleanAttribute underline() {
		return new BooleanAttributeImpl(HAS_UNDERLINE_MASK);
	}

	/** @return Whether text in this model is struck through */
	public BooleanAttribute strikeThrough() {
		return new BooleanAttributeImpl(HAS_STRIKE_THROUGH_MASK);
	}

	/** @return Whether text in this model is super script */
	public BooleanAttribute superScript() {
		return new BooleanAttributeImpl(HAS_SUPER_SCRIPT_MASK);
	}

	/** @return Whether text in this model is sub script */
	public BooleanAttribute subScript() {
		return new BooleanAttributeImpl(HAS_SUB_SCRIPT_MASK);
	}

	@Override
	public String toString() {
		return theText.toString();
	}

	class BooleanAttributeImpl implements BooleanAttribute {
		private final int theHasMask;

		BooleanAttributeImpl(int hasMask) {
			theHasMask = hasMask;
		}

		@Override
		public boolean hasAttribute() {
			return (theBooleanAttrs & theHasMask) != 0;
		}

		@Override
		public boolean get() {
			return (theBooleanAttrs & (theHasMask >>> 1)) != 0;
		}

		@Override
		public SimpleStyledTextModel set(boolean value) {
			int mask = theBooleanAttrs | theHasMask;
			int attrMask = theHasMask >>> 1;
			if (value)
				mask |= attrMask;
			else
				mask &= (~attrMask);
			if (theBooleanAttrs != mask) {
				theBooleanAttrs = mask;
				changed();
			}
			return SimpleStyledTextModel.this;
		}

		@Override
		public SimpleStyledTextModel clear() {
			if (!hasAttribute())
				return SimpleStyledTextModel.this;
			int attrMask = theHasMask | (theHasMask >>> 1);
			theBooleanAttrs &= ~attrMask;
			changed();
			return SimpleStyledTextModel.this;
		}
	}

	/**
	 * Creates a child of this model
	 *
	 * @return The new child model
	 */
	public SimpleStyledTextModel branch() {
		SimpleStyledTextModel child = new SimpleStyledTextModel(this);
		theChildren.add(child);
		return child;
	}

	/**
	 * Creates a child of this model
	 *
	 * @param child The action to perform on the new child
	 * @return This model
	 */
	public SimpleStyledTextModel branch(Consumer<SimpleStyledTextModel> child) {
		SimpleStyledTextModel ch = new SimpleStyledTextModel(this);
		child.accept(ch);
		theChildren.add(ch);
		return this;
	}

	static class Root extends SimpleStyledTextModel {
		private int theBatchCount;
		private final ListenerList<SimpleStyledTextModel> theBatch;

		Root(SettableValue<SimpleStyledTextModel> value) {
			super(null);
			asChild(root -> value.set(root, null));
			theBatch=ListenerList.build().build();
		}

		boolean isInBatch() {
			return theBatchCount!=0;
		}

		void addToBatch(SimpleStyledTextModel node) {
			theBatch.add(node, false);
		}

		@Override
		public Transaction batch() {
			int oldBC=theBatchCount;
			Transaction childT=oldBC==0 ? getChildren().lock(true, null) : Transaction.NONE;
			theBatchCount++;
			return ()->{
				theBatchCount=oldBC;
				childT.close();
				if (oldBC == 0) {
					theBatch.dumpAndClear(SimpleStyledTextModel::doChanged);
					doChanged();
				}
			};
		}
	}
}

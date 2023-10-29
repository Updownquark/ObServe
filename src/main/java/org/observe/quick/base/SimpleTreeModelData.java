package org.observe.quick.base;

import java.awt.Color;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.quick.style.FontStyleParser;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement;

public class SimpleTreeModelData implements CharSequence, Appendable {
	public interface Attribute<T> extends Supplier<T> {
		SimpleTreeModelData set(T value);

		default boolean hasAttribute() {
			return get() != null;
		}

		default SimpleTreeModelData clear() {
			return set(null);
		}
	}

	public interface DoubleAttribute extends DoubleSupplier {
		double get();

		@Override
		default double getAsDouble() {
			return get();
		}

		SimpleTreeModelData set(double value);

		default boolean hasAttribute() {
			return !Double.isNaN(get());
		}

		default SimpleTreeModelData clear() {
			return set(Double.NaN);
		}
	}

	public interface BooleanAttribute extends BooleanSupplier {
		boolean hasAttribute();

		boolean get();

		@Override
		default boolean getAsBoolean() {
			return get();
		}

		SimpleTreeModelData set(boolean value);

		SimpleTreeModelData clear();
	}

	public static SimpleTreeModelData createRoot() {
		return new SimpleTreeModelData(null);
	}

	private static final int HAS_UNDERLINE_MASK = 1;
	private static final int HAS_STRIKE_THROUGH_MASK = 4;
	private static final int HAS_SUPER_SCRIPT_MASK = 16;
	private static final int HAS_SUB_SCRIPT_MASK = 64;

	private final SimpleTreeModelData theParent;
	private final StringBuilder theText;
	private final ObservableCollection<SimpleTreeModelData> theChildren;
	private MutableCollectionElement<SimpleTreeModelData> theParentChild;
	private boolean isInTransaction;
	private boolean wasChanged;
	private Color theBgColor;
	private Color theFgColor;
	private double theFontWeight;
	private double theFontSize;
	private double theFontSlant;
	private int theBooleanAttrs;

	private SimpleTreeModelData(SimpleTreeModelData parent) {
		theParent = parent;
		theText = new StringBuilder();
		theChildren = ObservableCollection.build(SimpleTreeModelData.class).build();
		clearAll();

		theChildren.onChange(evt -> {
			switch (evt.getType()) {
			case add:
				evt.getNewValue().asChild(theChildren.mutableElement(evt.getElementId()));
				break;
			case remove:
				evt.getOldValue().asChild(null);
				break;
			default:
				break;
			}
		});
	}

	public SimpleTreeModelData getParent() {
		return theParent;
	}

	public Transaction batch() {
		Transaction chT = theChildren.lock(true, null);
		if (isInTransaction || theParentChild == null)
			return chT;
		isInTransaction = true;
		return () -> {
			chT.close();
			isInTransaction = false;
			if (wasChanged) {
				wasChanged = false;
				if (theParentChild != null)
					theParentChild.set(this);
			}
		};
	}

	public SimpleTreeModelData inBatch(Consumer<SimpleTreeModelData> op) {
		try (Transaction t = batch()) {
			op.accept(this);
		}
		return this;
	}

	private void changed() {
		if (isInTransaction)
			wasChanged = true;
		else if (theParentChild != null)
			theParentChild.set(this);
	}

	void asChild(MutableCollectionElement<SimpleTreeModelData> child) {
		theParentChild = child;
	}

	public StringBuilder getText() {
		return theText;
	}

	public ObservableCollection<SimpleTreeModelData> getChildren() {
		return theChildren;
	}

	public SimpleTreeModelData clearAll() {
		try (Transaction t = batch()) {
			theText.setLength(0);
			theChildren.clear();
			theBgColor = null;
			theFgColor = null;
			theFontWeight = Double.NaN;
			theFontSize = Double.NaN;
			theFontSlant = Double.NaN;
			theBooleanAttrs = 0;
			changed();
		}
		return this;
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
	public SimpleTreeModelData append(CharSequence csq) {
		if (csq.length() == 0)
			return this;
		theText.append(csq);
		changed();
		return this;
	}

	@Override
	public SimpleTreeModelData append(CharSequence csq, int start, int end) {
		if (start == end)
			return this;
		theText.append(csq, start, end);
		changed();
		return this;
	}

	@Override
	public SimpleTreeModelData append(char c) {
		theText.append(c);
		changed();
		return this;
	}

	public Attribute<Color> bg() {
		return new Attribute<Color>() {
			@Override
			public Color get() {
				return theBgColor;
			}

			@Override
			public SimpleTreeModelData set(Color value) {
				if (!Objects.equals(theBgColor, value)) {
					theBgColor = value;
					changed();
				}
				return SimpleTreeModelData.this;
			}
		};
	}

	public Attribute<Color> fg() {
		return new Attribute<Color>() {
			@Override
			public Color get() {
				return theFgColor;
			}

			@Override
			public SimpleTreeModelData set(Color value) {
				if (!Objects.equals(theFgColor, value)) {
					theFgColor = value;
					changed();
				}
				return SimpleTreeModelData.this;
			}
		};
	}

	public DoubleAttribute fontWeight() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontWeight;
			}

			@Override
			public SimpleTreeModelData set(double value) {
				if (theFontWeight != value) {
					theFontWeight = value;
					changed();
				}
				return SimpleTreeModelData.this;
			}
		};
	}

	public SimpleTreeModelData bold() {
		return fontWeight().set(FontStyleParser.bold);
	}

	public DoubleAttribute fontSize() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontSize;
			}

			@Override
			public SimpleTreeModelData set(double value) {
				theFontSize = value;
				changed();
				return SimpleTreeModelData.this;
			}
		};
	}

	public DoubleAttribute fontSlant() {
		return new DoubleAttribute() {
			@Override
			public double get() {
				return theFontSlant;
			}

			@Override
			public SimpleTreeModelData set(double value) {
				theFontSlant = value;
				changed();
				return SimpleTreeModelData.this;
			}
		};
	}

	public SimpleTreeModelData italic() {
		return fontSlant().set(FontStyleParser.italic);
	}

	public BooleanAttribute underline() {
		return new BooleanAttributeImpl(HAS_UNDERLINE_MASK);
	}

	public BooleanAttribute strikeThrough() {
		return new BooleanAttributeImpl(HAS_STRIKE_THROUGH_MASK);
	}

	public BooleanAttribute superScript() {
		return new BooleanAttributeImpl(HAS_SUPER_SCRIPT_MASK);
	}

	public BooleanAttribute subScript() {
		return new BooleanAttributeImpl(HAS_SUB_SCRIPT_MASK);
	}

	public SimpleTreeModelData branch() {
		SimpleTreeModelData child = new SimpleTreeModelData(this);
		theChildren.add(child);
		return child;
	}

	public SimpleTreeModelData branch(Consumer<SimpleTreeModelData> child) {
		SimpleTreeModelData ch = new SimpleTreeModelData(this);
		child.accept(ch);
		theChildren.add(ch);
		return this;
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
		public SimpleTreeModelData set(boolean value) {
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
			return SimpleTreeModelData.this;
		}

		@Override
		public SimpleTreeModelData clear() {
			if (!hasAttribute())
				return SimpleTreeModelData.this;
			int attrMask = theHasMask | (theHasMask >>> 1);
			theBooleanAttrs &= ~attrMask;
			changed();
			return SimpleTreeModelData.this;
		}
	}
}

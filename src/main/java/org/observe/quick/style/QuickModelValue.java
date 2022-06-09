package org.observe.quick.style;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.Named;

public class QuickModelValue<M, MV extends M> implements Named, ValueContainer<M, MV> {
	private final QuickStyleType theStyle;
	private final String theName;
	private final ModelInstanceType<M, MV> theType;
	private final int thePriority;

	public QuickModelValue(QuickStyleType style, String name, ModelInstanceType<M, MV> type, int priority) {
		theStyle = style;
		theName = name;
		theType = type;
		thePriority = priority;
	}

	public QuickStyleType getStyle() {
		return theStyle;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public ModelInstanceType<M, MV> getType() {
		return theType;
	}

	@Override
	public MV get(ModelSetInstance models) {
		return theStyle.getStyleSet().getModelValue(this);
	}

	public int getPriority() {
		return thePriority;
	}

	@Override
	public String toString() {
		return theStyle.getElement() + "." + theName + "(" + theType + ")";
	}
}

package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExTyped;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** Transfer support classes for the Quick-Base toolkit */
public class QuickTransfer {
	/** The XML name of the {@link TransferSource} element */
	public static final String TRANSFER_SOURCE = "transfer-source";
	/** The XML name of the {@link TransferAccept} element */
	public static final String TRANSFER_ACCEPT = "transfer-accept";
	/** The XML name of the {@link AsObject} element */
	public static final String AS_OBJECT = "as-object";
	/** The XML name of the {@link AsText} element */
	public static final String AS_TEXT = "as-text";

	/**
	 * The source component configuration for the ability to transfer values between widgets in quick
	 *
	 * @param <S> The type of values in the widget this transfer source is for
	 * @param <T> The type of transformed values produced by this source for acceptance in another widget (or the same widget)
	 */
	public static class TransferSource<S, T> extends ExElement.Abstract {
		/** Definition for a {@link TransferSource} */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TRANSFER_SOURCE,
			interpretation = Interpreted.class,
			instance = TransferSource.class)
		public static class Def extends ExElement.Def.Abstract<TransferSource<?, ?>> {
			private boolean isDraggable;
			private boolean isCopyable;
			private boolean isMovable;
			private CompiledExpression canTransform;
			private CompiledExpression theTransform;
			private final List<QuickDataFlavor.Def<?>> theFlavors;

			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theFlavors = new ArrayList<>();
			}

			/** @return Whether the user should be able to use drag operations to move values from this widget */
			@QonfigAttributeGetter("drag")
			public boolean isDraggable() {
				return isDraggable;
			}

			/** @return Whether the user should be able to use copy/paste operations to move values from this widget */
			@QonfigAttributeGetter("copy")
			public boolean isCopyable() {
				return isCopyable;
			}

			/** @return Whether the user should be able to use cut/paste operations to move values from this widget */
			@QonfigAttributeGetter("move")
			public boolean isMovable() {
				return isMovable;
			}

			/** @return Whether the currently-configured transfer source operation (likely dependent on the user's selection) is enabled */
			@QonfigAttributeGetter("can-transform")
			public CompiledExpression canTransform() {
				return canTransform;
			}

			/**
			 * @return Produces the transfer value for the currently-configured transfer source operation (likely dependent on the user's
			 *         selection)
			 */
			@QonfigAttributeGetter("transform")
			public CompiledExpression getTransform() {
				return theTransform;
			}

			/** @return Data flavors that this transfer source can produce data for */
			@QonfigChildGetter("flavor")
			public List<QuickDataFlavor.Def<?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				isDraggable = session.getAttribute("drag", boolean.class);
				isCopyable = session.getAttribute("copy", boolean.class);
				isMovable = session.getAttribute("move", boolean.class);
				canTransform = getAttributeExpression("can-transform", session);
				theTransform = getAttributeExpression("transform", session);
				syncChildren(QuickDataFlavor.Def.class, theFlavors, session.forChildren("flavor"));
			}

			/**
			 * @param parent The interpreted parent element
			 * @return The interpreted transfer source
			 */
			public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation for a {@link TransferSource}
		 *
		 * @param <S> The type of values in the widget this transfer source is for
		 * @param <T> The type of transformed values produced by this source for acceptance in another widget (or the same widget)
		 */
		public static class Interpreted<S, T> extends ExElement.Interpreted.Abstract<TransferSource<S, T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canTransform;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theTransform;
			private final List<QuickDataFlavor.Interpreted<? extends T, ?>> theFlavors;
			private TypeToken<T> theValueType;
			private TypeToken<?> theSuggestedDataType;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theFlavors = new ArrayList<>();
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return Whether the currently-configured transfer source operation (likely dependent on the user's selection) is enabled */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canTransform() {
				return canTransform;
			}

			/**
			 * @return Produces the transfer value for the currently-configured transfer source operation (likely dependent on the user's
			 *         selection)
			 */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTransform() {
				return theTransform;
			}

			/** @return Data flavors that this transfer source can produce data for */
			public List<QuickDataFlavor.Interpreted<? extends T, ?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			/** @return The type of transformed values produced by this source for acceptance in another widget (or the same widget) */
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			/**
			 * @param suggestedDataType The data type that this transfer source's parent thinks is most likely for the transfer data type
			 * @throws ExpressoInterpretationException If an error occurs interpreting this transfer source
			 */
			public void updateTransferSource(TypeToken<?> suggestedDataType)
				throws ExpressoInterpretationException {
				theSuggestedDataType = suggestedDataType;
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				// This type doesn't publish any values typed by the flavors like transfer-accept,
				// so we don't have to be as careful and can do things the standard way
				super.doUpdate();
				syncChildren(getDefinition().getFlavors(), theFlavors, f -> (QuickDataFlavor.Interpreted<? extends T, ?>) f.interpret(this),
					f -> f.updateDataFlavor(theSuggestedDataType));
				theValueType = TypeTokens.get().getCommonType(theFlavors.stream().map(f -> f.getDataType()).collect(Collectors.toList()));

				canTransform = interpret(getDefinition().canTransform(), ModelTypes.Value.BOOLEAN);
				theTransform = interpret(getDefinition().getTransform(), ModelTypes.Value.anyAs());
			}

			/** @return The instantiated transfer source */
			public TransferSource<S, T> create() {
				return new TransferSource<>(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<Boolean>> canTransformInstantiator;
		private ModelValueInstantiator<SettableValue<T>> theTransformInstantiator;

		private boolean isDraggable;
		private boolean isCopyable;
		private boolean isMovable;
		private SettableValue<Boolean> canTransform;
		private SettableValue<T> theTransform;
		private List<QuickDataFlavor<? extends T>> theFlavors;

		TransferSource(Object id) {
			super(id);
			theFlavors = new ArrayList<>();
		}

		/** @return Whether the user should be able to use drag operations to move values from this widget */
		public Boolean isDraggable() {
			return isDraggable;
		}

		/** @return Whether the user should be able to use copy/paste operations to move values from this widget */
		public Boolean isCopyable() {
			return isCopyable;
		}

		/** @return Whether the user should be able to use cut/paste operations to move values from this widget */
		public Boolean isMovable() {
			return isMovable;
		}

		/** @return Whether the currently-configured transfer source operation (likely dependent on the user's selection) is enabled */
		public SettableValue<Boolean> canTransform() {
			return canTransform;
		}

		/**
		 * @return Produces the transfer value for the currently-configured transfer source operation (likely dependent on the user's
		 *         selection)
		 */
		public SettableValue<T> getTransform() {
			return theTransform;
		}

		/** @return Data flavors that this transfer source can produce data for */
		public List<QuickDataFlavor<? extends T>> getFlavors() {
			return Collections.unmodifiableList(theFlavors);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<S, T> myInterpreted = (Interpreted<S, T>) interpreted;
			isDraggable = myInterpreted.getDefinition().isDraggable();
			isCopyable = myInterpreted.getDefinition().isCopyable();
			isMovable = myInterpreted.getDefinition().isMovable();
			canTransformInstantiator = myInterpreted.canTransform().instantiate();
			theTransformInstantiator = myInterpreted.getTransform() == null ? null : myInterpreted.getTransform().instantiate();
			syncChildren(myInterpreted.getFlavors(), theFlavors, f -> f.create(), QuickDataFlavor::update);
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			canTransformInstantiator.instantiate();
			if (theTransformInstantiator != null)
				theTransformInstantiator.instantiate();
			for (QuickDataFlavor<? extends T> flavor : theFlavors)
				flavor.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			canTransform = canTransformInstantiator.get(myModels);
			theTransform = theTransformInstantiator == null ? null : theTransformInstantiator.get(myModels);
			for (QuickDataFlavor<? extends T> flavor : theFlavors)
				flavor.instantiate(myModels);
			return myModels;
		}

		@Override
		public TransferSource<S, T> copy(ExElement parent) {
			TransferSource<S, T> copy = (TransferSource<S, T>) super.copy(parent);

			copy.theFlavors = new ArrayList<>();
			for (QuickDataFlavor<? extends T> flavor : theFlavors)
				copy.theFlavors.add(flavor.copy(copy));

			return copy;
		}
	}

	/**
	 * The target component configuration for the ability to transfer values between widgets in quick
	 *
	 * @param <T> The type of values in the widget this transfer source is for
	 * @param <S> The type of transformed values acceptable to this target from another widget (or the same widget)
	 */
	public static class TransferAccept<T, S> extends QuickStyledElement.Abstract {
		/** Definition for a {@link TransferAccept} */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TRANSFER_ACCEPT,
			interpretation = Interpreted.class,
			instance = TransferAccept.class)
		public static class Def extends QuickStyledElement.Def.Abstract<TransferAccept<?, ?>> {
			private ModelComponentId theTransferValueAs;
			private ModelComponentId theTransferValuesAs;
			private boolean isDraggable;
			private boolean isPasteable;
			private final List<QuickDataFlavor.Def<?>> theFlavors;
			private CompiledExpression canAccept;
			private boolean isCanAcceptOpOnSingle;
			private CompiledExpression theAccept;
			private boolean isAcceptOpOnSingle;
			private CompiledExpression theIconOffsetX;
			private CompiledExpression theIconOffsetY;

			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theFlavors = new ArrayList<>();
			}

			/** @return The variable that will contain each value to transfer into this widget */
			@QonfigAttributeGetter("transfer-value-as")
			public ModelComponentId getTransferValueAs() {
				return theTransferValueAs;
			}

			/** @return The variable that will contain the list of values to transfer into this widget */
			@QonfigAttributeGetter("transfer-values-as")
			public ModelComponentId getTransferValuesAs() {
				return theTransferValuesAs;
			}

			/** @return Whether the user should be able to use drag operations to move values into this widget */
			@QonfigAttributeGetter("drag")
			public boolean isDraggable() {
				return isDraggable;
			}

			/** @return Whether the user should be able to use copy/ or cut/paste operations to move values into this widget */
			@QonfigAttributeGetter("paste")
			public boolean isPasteable() {
				return isPasteable;
			}

			/** @return Data flavors that this transfer accepter can accept data for */
			@QonfigChildGetter("flavor")
			public List<QuickDataFlavor.Def<?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			/** @return Whether the current transfer operation is acceptable */
			@QonfigAttributeGetter("can-accept")
			public CompiledExpression canAccept() {
				return canAccept;
			}

			/** @return Whether the {@link #canAccept()} operation operates on the {@link #getTransferValueAs()} variable */
			public boolean isCanAcceptOpOnSingle() {
				return isCanAcceptOpOnSingle;
			}

			/** @return The action to perform the transfer operation */
			@QonfigAttributeGetter("accept")
			public CompiledExpression getAccept() {
				return theAccept;
			}

			/** @return Whether the {@link #getAccept()} operation operates on the {@link #getTransferValueAs()} variable */
			public boolean isAcceptOpOnSingle() {
				return isAcceptOpOnSingle;
			}

			/** @return Horizontal offset for the icon for the current drag transfer */
			@QonfigAttributeGetter("icon-offset-x")
			public CompiledExpression getIconOffsetX() {
				return theIconOffsetX;
			}

			/** @return Vertical offset for the icon for the current drag transfer */
			@QonfigAttributeGetter("icon-offset-y")
			public CompiledExpression getIconOffsetY() {
				return theIconOffsetY;
			}

			@Override
			public QuickInstanceStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new TransferAcceptStyle.Def(parentStyle, getAddOn(QuickStyled.Def.class), style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				String transferValueAs = session.getAttributeText("transfer-value-as");
				String transferValuesAs = session.getAttributeText("transfer-values-as");
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				if (transferValueAs != null) {
					theTransferValueAs = elModels.getElementValueModelId(transferValueAs);
					elModels.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theTransferValueAs, ModelTypes.Value,
						Interpreted::getDataType);
				} else
					theTransferValueAs = null;
				if (transferValuesAs != null) {
					theTransferValuesAs = elModels.getElementValueModelId(transferValuesAs);
					elModels.<Interpreted<?, ?>, ObservableCollection<?>> satisfyElementSingleValueType(theTransferValuesAs,
						ModelTypes.Collection, Interpreted::getDataType);
				} else
					theTransferValuesAs = null;

				isDraggable = session.getAttribute("drag", boolean.class);
				isPasteable = session.getAttribute("paste", boolean.class);
				syncChildren(QuickDataFlavor.Def.class, theFlavors, session.forChildren("flavor"));

				canAccept = getAttributeExpression("can-accept", session);
				if (hasVariable(canAccept.getExpression(), theTransferValueAs)) {
					if (hasVariable(canAccept.getExpression(), theTransferValuesAs)) {
						reporting().at(canAccept.getFilePosition())
						.error("Expressions may not use both variables '" + transferValueAs + "' and '" + transferValuesAs + "'");
					}
					isCanAcceptOpOnSingle = true;
				} else
					isCanAcceptOpOnSingle = false;

				theAccept = getAttributeExpression("accept", session);
				if (hasVariable(theAccept.getExpression(), theTransferValueAs)) {
					if (hasVariable(theAccept.getExpression(), theTransferValuesAs)) {
						reporting().at(theAccept.getFilePosition())
						.error("Expressions may not use both variables '" + transferValueAs + "' and '" + transferValuesAs + "'");
					}
					isAcceptOpOnSingle = true;
				} else
					isAcceptOpOnSingle = false;
				theIconOffsetX = getAttributeExpression("icon-offset-x", session);
				theIconOffsetY = getAttributeExpression("icon-offset-y", session);
			}

			private boolean hasVariable(ObservableExpression expression, ModelComponentId vbl) {
				if (vbl == null)
					return false;
				else
					return NameExpression.findNameExpression(expression, vbl.getName()) != null;
			}

			/**
			 * @param parent The interpreted parent for this transfer accepter
			 * @return The interpreted transfer accepter
			 */
			public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation for a {@link TransferAccept}
		 *
		 * @param <T> The type of values in the widget this transfer source is for
		 * @param <S> The type of transformed values acceptable to this target from another widget (or the same widget)
		 */
		public static class Interpreted<T, S> extends QuickStyledElement.Interpreted.Abstract<TransferAccept<T, S>> {
			private final List<QuickDataFlavor.Interpreted<? extends S, ?>> theFlavors;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canAccept;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theAccept;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theIconOffsetX;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theIconOffsetY;
			private TypeToken<?> theSuggestedDataType;
			private TypeToken<S> theDataType;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theFlavors = new ArrayList<>();
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return Data flavors that this transfer accepter can accept data for */
			public List<QuickDataFlavor.Interpreted<? extends S, ?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			/** @return Whether the current transfer operation is acceptable */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canAccept() {
				return canAccept;
			}

			/** @return The action to perform the transfer operation */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getAccept() {
				return theAccept;
			}

			/** @return Horizontal offset for the icon for the current drag transfer */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getIconOffsetX() {
				return theIconOffsetX;
			}

			/** @return Vertical offset for the icon for the current drag transfer */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getIconOffsetY() {
				return theIconOffsetY;
			}

			/**
			 * @return The type of transformed values acceptable to this target from another widget (or the same widget)
			 * @throws ExpressoInterpretationException If the data type could not be interpreted
			 */
			public TypeToken<S> getDataType() throws ExpressoInterpretationException {
				if (theDataType == null) { // Not evaluated yet--interpretation is happening
					List<TypeToken<? extends S>> flavorTypes = new ArrayList<>(theFlavors.size());
					for (QuickDataFlavor.Interpreted<? extends S, ?> flavor : theFlavors) {
						TypeToken<? extends S> flavorType = flavor.getDataType();
						if (flavorType != null)
							flavorTypes.add(flavorType);
					}
					if (flavorTypes.isEmpty())
						theDataType = (TypeToken<S>) TypeTokens.get().OBJECT;
					else
						theDataType = TypeTokens.get().getCommonType(flavorTypes);
				}
				return theDataType;
			}

			/**
			 * @param suggestedDataType The data type that this transfer accepter's parent thinks is most likely for the transfer data type
			 * @throws ExpressoInterpretationException If an error occurs interpreting this transfer accepter
			 */
			public void updateTransferAccepter(TypeToken<?> suggestedDataType)
				throws ExpressoInterpretationException {
				theSuggestedDataType = suggestedDataType;
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				// Create the flavor children first so we can determine the data type
				syncChildren(getDefinition().getFlavors(), theFlavors, f -> (QuickDataFlavor.Interpreted<? extends S, ?>) f.interpret(this),
					null);
				super.doUpdate();
				for (QuickDataFlavor.Interpreted<? extends S, ?> flavor : theFlavors)
					flavor.updateDataFlavor(theSuggestedDataType);
				getDataType();
				canAccept = interpret(getDefinition().canAccept(), ModelTypes.Value.BOOLEAN);
				theAccept = interpret(getDefinition().getAccept(), ModelTypes.Action.instance());
				theIconOffsetX = interpret(getDefinition().getIconOffsetX(), ModelTypes.Value.INT);
				theIconOffsetY = interpret(getDefinition().getIconOffsetY(), ModelTypes.Value.INT);
			}

			/** @return The instantiated transfer accepter */
			public TransferAccept<T, S> create() {
				return new TransferAccept<>(getIdentity());
			}
		}

		static class TransferAcceptStyle extends QuickInstanceStyle.Abstract {
			static class Def extends QuickInstanceStyle.Def.Abstract {
				Def(QuickInstanceStyle.Def parent, QuickStyled.Def styled, QuickCompiledStyle wrapped) {
					super(parent, styled, wrapped);
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parentEl.getAddOn(QuickStyled.Interpreted.class),
						(QuickStyled.QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}

			static class Interpreted extends QuickInstanceStyle.Interpreted.Abstract {
				Interpreted(QuickStyled.QuickInstanceStyle.Def definition, QuickStyled.Interpreted styled,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styled, parent, wrapped);
				}

				@Override
				public QuickInstanceStyle create(QuickStyled parent) {
					return new TransferAcceptStyle();
				}
			}
		}

		private ModelValueInstantiator<SettableValue<Boolean>> canAcceptInstantiator;
		private ModelValueInstantiator<ObservableAction> theAcceptInstantiator;
		private ModelValueInstantiator<SettableValue<Integer>> theIconOffsetXInstantiator;
		private ModelValueInstantiator<SettableValue<Integer>> theIconOffsetYInstantiator;

		private ModelComponentId theTransferValueAs;
		private ModelComponentId theTransferValuesAs;
		private SettableValue<S> theTransferValue;
		private ObservableCollection<S> theTransferValues;
		private boolean isDraggable;
		private boolean isPasteable;
		private List<QuickDataFlavor<? extends S>> theFlavors;
		private SettableValue<Boolean> canAccept;
		private boolean isCanAcceptOpOnSingle;
		private ObservableAction theAccept;
		private boolean isAcceptOpOnSingle;
		private SettableValue<Integer> theIconOffsetX;
		private SettableValue<Integer> theIconOffsetY;

		TransferAccept(Object id) {
			super(id);
			theFlavors = new ArrayList<>();
			theTransferValue = SettableValue.create();
			theTransferValues = ObservableCollection.create();
		}

		/** @return Container for each source value to transfer into this widget */
		public SettableValue<S> getTransferValue() {
			return theTransferValue;
		}

		/** @return Container for the list of source values to transfer into this widget */
		public ObservableCollection<S> getTransferValues() {
			return theTransferValues;
		}

		/** @return Whether the user should be able to use drag operations to move values into this widget */
		public boolean isDraggable() {
			return isDraggable;
		}

		/** @return Whether the user should be able to use copy/ or cut/paste operations to move values into this widget */
		public boolean isPasteable() {
			return isPasteable;
		}

		/** @return Data flavors that this transfer accepter can accept data for */
		public List<QuickDataFlavor<? extends S>> getFlavors() {
			return Collections.unmodifiableList(theFlavors);
		}

		/** @return Whether the current transfer operation is acceptable */
		public SettableValue<Boolean> canAccept() {
			return canAccept;
		}

		/** @return Whether the {@link #canAccept()} operation operates on the {@link Def#getTransferValueAs()} variable */
		public boolean isCanAcceptOpOnSingle() {
			return isCanAcceptOpOnSingle;
		}

		/** @return The action to perform the transfer operation */
		public ObservableAction getAccept() {
			return theAccept;
		}

		/** @return Whether the {@link #getAccept()} operation operates on the {@link Def#getTransferValueAs()} variable */
		public boolean isAcceptOpOnSingle() {
			return isAcceptOpOnSingle;
		}

		/** @return Horizontal offset for the icon for the current drag transfer */
		public SettableValue<Integer> getIconOffsetX() {
			return theIconOffsetX;
		}

		/** @return Vertical offset for the icon for the current drag transfer */
		public SettableValue<Integer> getIconOffsetY() {
			return theIconOffsetY;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<T, S> myInterpreted = (Interpreted<T, S>) interpreted;
			theTransferValueAs = myInterpreted.getDefinition().getTransferValueAs();
			theTransferValuesAs = myInterpreted.getDefinition().getTransferValuesAs();
			isDraggable = myInterpreted.getDefinition().isDraggable();
			isPasteable = myInterpreted.getDefinition().isPasteable();
			syncChildren(myInterpreted.getFlavors(), theFlavors, f -> f.create(), QuickDataFlavor::update);
			canAcceptInstantiator = myInterpreted.canAccept().instantiate();
			isCanAcceptOpOnSingle = myInterpreted.getDefinition().isCanAcceptOpOnSingle();
			theAcceptInstantiator = myInterpreted.getAccept().instantiate();
			isAcceptOpOnSingle = myInterpreted.getDefinition().isAcceptOpOnSingle();
			theIconOffsetXInstantiator = myInterpreted.getIconOffsetX().instantiate();
			theIconOffsetYInstantiator = myInterpreted.getIconOffsetY().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			for (QuickDataFlavor<? extends S> flavor : theFlavors)
				flavor.instantiated();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			if (theTransferValueAs != null)
				ExFlexibleElementModelAddOn.satisfyElementValue(theTransferValueAs, myModels, theTransferValue);
			if (theTransferValuesAs != null)
				ExFlexibleElementModelAddOn.satisfyElementValue(theTransferValuesAs, myModels, theTransferValues);
			canAccept = canAcceptInstantiator.get(myModels);
			theAccept = theAcceptInstantiator.get(myModels);
			theIconOffsetX = theIconOffsetXInstantiator.get(myModels);
			theIconOffsetY = theIconOffsetYInstantiator.get(myModels);

			for (QuickDataFlavor<? extends S> flavor : theFlavors)
				flavor.instantiate(myModels);
			return myModels;
		}

		@Override
		public TransferAccept<T, S> copy(ExElement parent) {
			TransferAccept<T, S> copy = (TransferAccept<T, S>) super.copy(parent);

			copy.theTransferValue = SettableValue.create();
			copy.theTransferValues = ObservableCollection.create();
			copy.theFlavors = new ArrayList<>();
			for (QuickDataFlavor<? extends S> flavor : theFlavors)
				copy.theFlavors.add(flavor.copy(copy));

			return copy;
		}
	}

	/**
	 * A data flavor for transfer of values between Quick widgets
	 *
	 * @param <T> The type of data for this flavor
	 */
	public static interface QuickDataFlavor<T> extends ExElement {
		/**
		 * Definition for a {@link QuickDataFlavor}
		 *
		 * @param <F> The type of data flavor
		 */
		public interface Def<F extends QuickDataFlavor<?>> extends ExElement.Def<F> {
			/**
			 * @param parent The interpreted parent
			 * @return The interpreted data flavor
			 */
			Interpreted<?, ? extends F> interpret(ExElement.Interpreted<?> parent);
		}

		/**
		 * Interpretation for a {@link QuickDataFlavor}
		 *
		 * @param <T> The type of data for this flavor
		 * @param <F> The type of data flavor
		 */
		public interface Interpreted<T, F extends QuickDataFlavor<T>> extends ExElement.Interpreted<F> {
			/** @return The type of transfer data for this flavor */
			TypeToken<T> getDataType();

			/**
			 * @param env The environment to use to interpret expressions
			 * @param suggestedType The data type that this data flavor's parent thinks is most likely for the transfer data type
			 * @throws ExpressoInterpretationException If an error occurs interpreting this data flavor
			 */
			void updateDataFlavor(TypeToken<?> suggestedType) throws ExpressoInterpretationException;

			/** @return The instantiated data flavor */
			F create();
		}

		@Override
		QuickDataFlavor<T> copy(ExElement parent);
	}

	/**
	 * Data flavor for simple transfer of objects in Quick
	 *
	 * @param <T> The type of object to transfer
	 */
	public static class AsObject<T> extends ExElement.Abstract implements QuickDataFlavor<T> {
		/** Definition for an {@link AsObject} */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = AS_OBJECT,
			interpretation = Interpreted.class,
			instance = AsObject.class)
		public static class Def extends ExElement.Def.Abstract<AsObject<?>> implements QuickDataFlavor.Def<AsObject<?>> {
			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation for an {@link AsObject}
		 *
		 * @param <T> The type of object to transfer
		 */
		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<AsObject<T>>
		implements QuickDataFlavor.Interpreted<T, AsObject<T>> {
			private TypeToken<T> theDataType;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public TypeToken<T> getDataType() {
				return theDataType;
			}

			@Override
			public void updateDataFlavor(TypeToken<?> suggestedType) throws ExpressoInterpretationException {
				theDataType = (TypeToken<T>) suggestedType;
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				TypeToken<T> configuredType = getAddOn(ExTyped.Interpreted.class).getValueType();
				if (configuredType != null)
					theDataType = configuredType;
			}

			@Override
			public AsObject<T> create() {
				return new AsObject<>(getIdentity());
			}
		}

		AsObject(Object id) {
			super(id);
		}

		@Override
		public AsObject<T> copy(ExElement parent) {
			AsObject<T> copy = (AsObject<T>) super.copy(parent);
			return copy;
		}
	}

	/** Data flavor for transfer of text data in Quick */
	public static class AsText extends ExElement.Abstract implements QuickDataFlavor<String> {
		/** Definition for an {@link AsText} */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = AS_TEXT,
			interpretation = Interpreted.class,
			instance = AsText.class)
		public static class Def extends ExElement.Def.Abstract<AsText> implements QuickDataFlavor.Def<AsText> {
			private String theMimeType;

			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The MIME (Multipurpose Internet Mail Extension) type of text to be transferred. E.g. 'text/html' or 'text/plain' */
			@QonfigAttributeGetter("mime-type")
			public String getMimeType() {
				return theMimeType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theMimeType = session.getAttributeText("mime-type");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for an {@link AsText} */
		public static class Interpreted extends ExElement.Interpreted.Abstract<AsText>
		implements QuickDataFlavor.Interpreted<String, AsText> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public TypeToken<String> getDataType() {
				return TypeTokens.get().STRING;
			}

			@Override
			public void updateDataFlavor(TypeToken<?> suggestedDataFlavor) throws ExpressoInterpretationException {
				update();
			}

			@Override
			public AsText create() {
				return new AsText(getIdentity());
			}
		}

		private String theMimeType;

		AsText(Object id) {
			super(id);
		}

		/** @return The MIME (Multipurpose Internet Mail Extension) type of text to be transferred. E.g. 'text/html' or 'text/plain' */
		public String getMimeType() {
			return theMimeType;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theMimeType = myInterpreted.getDefinition().getMimeType();
		}

		@Override
		public AsText copy(ExElement parent) {
			return (AsText) super.copy(parent);
		}
	}
}

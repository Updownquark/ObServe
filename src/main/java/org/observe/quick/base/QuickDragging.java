package org.observe.quick.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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

public class QuickDragging {
	public static final String TRANSFER_SOURCE = "transfer-source";
	public static final String TRANSFER_ACCEPT = "transfer-accept";
	public static final String AS_OBJECT = "as-object";
	public static final String AS_TEXT = "as-text";

	public static class TransferSource<S, T> extends ExElement.Abstract {
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TRANSFER_SOURCE,
			interpretation = Interpreted.class,
			instance = TransferSource.class)
		public static class Def extends ExElement.Def.Abstract<TransferSource> {
			private boolean isDraggable;
			private boolean isCopyable;
			private boolean isMovable;
			private CompiledExpression canTransform;
			private CompiledExpression theTransform;
			private final List<QuickDataFlavor.Def<?>> theFlavors;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theFlavors = new ArrayList<>();
			}

			@QonfigAttributeGetter("drag")
			public boolean isDraggable() {
				return isDraggable;
			}

			@QonfigAttributeGetter("copy")
			public boolean isCopyable() {
				return isCopyable;
			}

			@QonfigAttributeGetter("move")
			public boolean isMovable() {
				return isMovable;
			}

			@QonfigAttributeGetter("can-transform")
			public CompiledExpression canTransform() {
				return canTransform;
			}

			@QonfigAttributeGetter("transform")
			public CompiledExpression getTransform() {
				return theTransform;
			}

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

			public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canTransform() {
				return canTransform;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getTransform() {
				return theTransform;
			}

			public List<QuickDataFlavor.Interpreted<? extends T, ?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			public void updateTransferSource(InterpretedExpressoEnv env, TypeToken<?> suggestedDataType)
				throws ExpressoInterpretationException {
				theSuggestedDataType = suggestedDataType;
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				syncChildren(getDefinition().getFlavors(), theFlavors, f -> (QuickDataFlavor.Interpreted<? extends T, ?>) f.interpret(this),
					(f, env) -> f.updateDataFlavor(env, theSuggestedDataType));
				theValueType = TypeTokens.get().getCommonType(theFlavors.stream().map(f -> f.getDataType()).collect(Collectors.toList()));

				canTransform = interpret(getDefinition().canTransform(), ModelTypes.Value.BOOLEAN);
				theTransform = interpret(getDefinition().getTransform(), ModelTypes.Value.anyAs());
			}

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

		public Boolean isDraggable() {
			return isDraggable;
		}

		public Boolean isCopyable() {
			return isCopyable;
		}

		public Boolean isMovable() {
			return isMovable;
		}

		public SettableValue<Boolean> canTransform() {
			return canTransform;
		}

		public SettableValue<T> getTransform() {
			return theTransform;
		}

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
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			canTransform = canTransformInstantiator.get(myModels);
			theTransform = theTransformInstantiator == null ? null : theTransformInstantiator.get(myModels);
			for (QuickDataFlavor<? extends T> flavor : theFlavors)
				flavor.instantiate(myModels);
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

	public static class TransferAccept<T, S> extends QuickStyledElement.Abstract {
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

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theFlavors = new ArrayList<>();
			}

			@QonfigAttributeGetter("transfer-value-as")
			public ModelComponentId getTransferValueAs() {
				return theTransferValueAs;
			}

			@QonfigAttributeGetter("transfer-values-as")
			public ModelComponentId getTransferValuesAs() {
				return theTransferValuesAs;
			}

			@QonfigAttributeGetter("drag")
			public boolean isDraggable() {
				return isDraggable;
			}

			@QonfigAttributeGetter("paste")
			public boolean isPasteable() {
				return isPasteable;
			}

			@QonfigChildGetter("flavor")
			public List<QuickDataFlavor.Def<?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			@QonfigAttributeGetter("can-accept")
			public CompiledExpression canAccept() {
				return canAccept;
			}

			public boolean isCanAcceptOpOnSingle() {
				return isCanAcceptOpOnSingle;
			}

			@QonfigAttributeGetter("accept")
			public CompiledExpression getAccept() {
				return theAccept;
			}

			public boolean isAcceptOpOnSingle() {
				return isAcceptOpOnSingle;
			}

			@QonfigAttributeGetter("icon-offset-x")
			public CompiledExpression getIconOffsetX() {
				return theIconOffsetX;
			}

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
					elModels.satisfyElementValueType(theTransferValueAs, ModelTypes.Value,
						(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getDataType()));
				} else
					theTransferValueAs = null;
				if (transferValuesAs != null) {
					theTransferValuesAs = elModels.getElementValueModelId(transferValuesAs);
					elModels.satisfyElementValueType(theTransferValuesAs, ModelTypes.Collection,
						(interp, env) -> ModelTypes.Collection.forType(((Interpreted<?, ?>) interp).getDataType()));
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
				if (expression instanceof NameExpression && ((NameExpression) expression).getNames().size() == 1
					&& ((NameExpression) expression).getName().equals(vbl.getName()))
					return true;
				for (ObservableExpression component : expression.getComponents()) {
					if (hasVariable(component, vbl))
						return true;
				}
				return false;
			}

			public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

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

			public List<QuickDataFlavor.Interpreted<? extends S, ?>> getFlavors() {
				return Collections.unmodifiableList(theFlavors);
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> canAccept() {
				return canAccept;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getAccept() {
				return theAccept;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getIconOffsetX() {
				return theIconOffsetX;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getIconOffsetY() {
				return theIconOffsetY;
			}

			public TypeToken<S> getDataType() {
				return theDataType;
			}

			public void updateTransferAccepter(InterpretedExpressoEnv env, TypeToken<?> suggestedDataType)
				throws ExpressoInterpretationException {
				theSuggestedDataType = suggestedDataType;
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				syncChildren(getDefinition().getFlavors(), theFlavors, f -> (QuickDataFlavor.Interpreted<? extends S, ?>) f.interpret(this),
					(f, env2) -> f.updateDataFlavor(env2, theSuggestedDataType));
				theDataType = TypeTokens.get().getCommonType(theFlavors.stream().map(f -> f.getDataType()).collect(Collectors.toList()));
				super.doUpdate(expressoEnv);
				canAccept = getDefinition().canAccept().interpret(ModelTypes.Value.BOOLEAN, expressoEnv);
				theAccept = getDefinition().getAccept().interpret(ModelTypes.Action.instance(), expressoEnv);
				theIconOffsetX = getDefinition().getIconOffsetX().interpret(ModelTypes.Value.INT, expressoEnv);
				theIconOffsetY = getDefinition().getIconOffsetY().interpret(ModelTypes.Value.INT, expressoEnv);
			}

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
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parentEl.getAddOn(QuickStyled.Interpreted.class),
						(QuickStyled.QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent, env));
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

		public SettableValue<S> getTransferValue() {
			return theTransferValue;
		}

		public ObservableCollection<S> getTransferValues() {
			return theTransferValues;
		}

		public boolean isDraggable() {
			return isDraggable;
		}

		public boolean isPasteable() {
			return isPasteable;
		}

		public List<QuickDataFlavor<? extends S>> getFlavors() {
			return Collections.unmodifiableList(theFlavors);
		}

		public SettableValue<Boolean> canAccept() {
			return canAccept;
		}

		public boolean isCanAcceptOpOnSingle() {
			return isCanAcceptOpOnSingle;
		}

		public ObservableAction getAccept() {
			return theAccept;
		}

		public boolean isAcceptOpOnSingle() {
			return isAcceptOpOnSingle;
		}

		public SettableValue<Integer> getIconOffsetX() {
			return theIconOffsetX;
		}

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
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

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

	public static interface QuickDataFlavor<T> extends ExElement {
		public interface Def<F extends QuickDataFlavor<?>> extends ExElement.Def<F> {
			Interpreted<?, ? extends F> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<T, F extends QuickDataFlavor<T>> extends ExElement.Interpreted<F> {
			TypeToken<T> getDataType();

			void updateDataFlavor(InterpretedExpressoEnv env, TypeToken<?> suggestedType) throws ExpressoInterpretationException;

			F create();
		}

		@Override
		QuickDataFlavor<T> copy(ExElement parent);
	}

	public static class AsObject<T> extends ExElement.Abstract implements QuickDataFlavor<T> {
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = AS_OBJECT,
			interpretation = Interpreted.class,
			instance = AsObject.class)
		public static class Def extends ExElement.Def.Abstract<AsObject<?>> implements QuickDataFlavor.Def<AsObject<?>> {
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

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
			public void updateDataFlavor(InterpretedExpressoEnv env, TypeToken<?> suggestedType) throws ExpressoInterpretationException {
				theDataType = (TypeToken<T>) suggestedType;
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
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

	public static class AsText extends ExElement.Abstract implements QuickDataFlavor<String> {
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = AS_TEXT,
			interpretation = Interpreted.class,
			instance = AsText.class)
		public static class Def extends ExElement.Def.Abstract<AsText> {
			private String theMimeType;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter("mime-type")
			public String getMimeType() {
				return theMimeType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theMimeType = session.getAttributeText("mime-type");
			}
		}

		public static class Interpreted extends ExElement.Interpreted.Abstract<AsText> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}
		}

		private String theMimeType;

		AsText(Object id) {
			super(id);
		}

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

package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.VariableType;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.io.LocatedPositionedContent;

/** Qonfig Interpretation for the ExpressoBaseV0_1 API */
public class ExpressoBaseV0_1 implements QonfigInterpretation {
	/** The name of the expresso base toolkit */
	public static final String NAME = "Expresso-Base";

	/** The version of this implementation of the expresso base toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** Session key containing a model value's path */
	public static final String PATH_KEY = "model-path";
	/**
	 * Session key containing a model value's type, if known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String VALUE_TYPE_KEY = "value-type";
	/**
	 * Session key containing a model value's key-type, if applicable and known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String KEY_TYPE_KEY = "key-type";

	// /** Represents an application so that various models in this class can provide intelligent interaction with the user */
	// public interface AppEnvironment {
	// /** @return A function to provide the title of the application */
	// ModelValueSynth<SettableValue<?>, ? extends ObservableValue<String>> getTitle();
	//
	// /** @return A function to provide the icon representing the application */
	// ModelValueSynth<SettableValue<?>, ? extends ObservableValue<Image>> getIcon();
	// }

	private QonfigToolkit theExpressoToolkit;

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theExpressoToolkit = toolkit;
	}

	static class ExtModelValueElHolder {
		private final List<ExtModelValueElement.Def<?>> values;
		private final List<ExpressoQIS> sessions;

		private ExtModelValueElHolder() {
			values = new ArrayList<>();
			sessions = new ArrayList<>();
		}

		void update(ExElement.Def<?> parent) throws QonfigInterpretationException {
			for (int i = 0; i < values.size(); i++) {
				values.get(i).setParentElement(parent);
				values.get(i).update(sessions.get(i));
			}
		}

		static ExtModelValueElHolder add(ExtModelValueElHolder holder, ExtModelValueElement.Def<?> value, ExpressoQIS session) {
			if (holder == null)
				holder = new ExtModelValueElHolder();
			holder.values.add(value);
			holder.sessions.add(session);
			return holder;
		}
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("with-element-model", ExWithElementModel.Def.class, addOnCreator(ExWithElementModel.Def::new));
		interpreter.createWith("element-model-value", ExElementModelValue.Def.class,
			addOnCreator(ModelValueElement.Def.class, ExElementModelValue.Def::new));
		interpreter.createWith("with-local-model", ExWithLocalModel.Def.class, addOnCreator(ExWithLocalModel.Def::new));
		interpreter.createWith("named", ExNamed.Def.class, addOnCreator(ExNamed.Def::new));
		interpreter.createWith("typed", ExTyped.Def.class, addOnCreator(ExTyped.Def::new));
		interpreter.createWith("int-value", ExIntValue.Def.class, addOnCreator(ExIntValue.Def::new));
		interpreter.createWith("complex-operation", ExComplexOperation.class, addOnCreator(ExComplexOperation::new));
		interpreter.createWith("sort", ExSort.ExRootSort.class, creator(ExSort.ExRootSort::new));
		interpreter.createWith("sort-by", ExSort.ExSortBy.class, creator(ExSort.class, ExSort.ExSortBy::new));
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		interpreter.createWith("first-value", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.FirstValue::new));
		interpreter.createWith("hook", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.Hook::new));
		ExpressoTransformations.configureTransformation(interpreter);
		return interpreter;
	}

	public static <T> QonfigInterpreterCore.QonfigValueCreator<T> creator(BiFunction<ExElement.Def<?>, QonfigElementOrAddOn, T> creator) {
		return session -> creator.apply(session.as(ExpressoQIS.class).getElementRepresentation(), session.getFocusType());
	}

	public static <P extends ExElement.Def<?>, T> QonfigInterpreterCore.QonfigValueCreator<T> creator(Class<P> parentType,
		BiFunction<P, QonfigElementOrAddOn, T> creator) {
		return session -> {
			ExElement.Def<?> parent = session.as(ExpressoQIS.class).getElementRepresentation();
			if (parent != null && !parentType.isInstance(parent))
				throw new QonfigInterpretationException("This implementation requires a parent of type " + parentType.getName() + ", not "
					+ (parent == null ? "null" : parent.getClass().getName()), session.reporting().getPosition(), 0);
			return creator.apply((P) parent, session.getFocusType());
		};
	}

	public static <D extends ExElement.Def<?>, AO extends ExAddOn.Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> addOnCreator(
		Class<D> defType, BiFunction<QonfigAddOn, D, AO> creator) {
		return session -> {
			ExElement.Def<?> def = session.as(ExpressoQIS.class).getElementRepresentation();
			if (def != null && !defType.isInstance(def))
				throw new QonfigInterpretationException("This implementation requires an element definition of type " + defType.getName()
				+ ", not " + (def == null ? "null" : def.getClass().getName()), session.reporting().getPosition(),
				0);
			return creator.apply((QonfigAddOn) session.getFocusType(), (D) def);
		};
	}

	public static <AO extends ExAddOn.Def<?, ?>> QonfigInterpreterCore.QonfigValueCreator<AO> addOnCreator(
		BiFunction<QonfigAddOn, ExElement.Def<?>, AO> creator) {
		return session -> creator.apply((QonfigAddOn) session.getFocusType(), session.as(ExpressoQIS.class).getElementRepresentation());
	}

	ExpressoQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(ExpressoQIS.class);
	}

	void configureBaseModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("imports", ClassViewElement.class, creator(ClassViewElement::new));
		interpreter.createWith("import", ClassViewElement.ImportElement.class, creator(ClassViewElement.ImportElement::new));
		interpreter.createWith("models", ObservableModelElement.ModelSetElement.Def.class,
			creator(ObservableModelElement.ModelSetElement.Def::new));
		interpreter.createWith("expresso", Expresso.Def.class, creator(Expresso.Def::new));
		interpreter.modifyWith("map", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(KEY_TYPE_KEY) == null) {
					QonfigValue typeV = session.getElement().getAttributes().get(session.getAttributeDef(null, null, "key-type"));
					if (typeV != null && !typeV.text.isEmpty()) {
						session.put(KEY_TYPE_KEY,
							VariableType.parseType(new LocatedPositionedContent.Default(typeV.fileLocation, typeV.position)));
					}
				}
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepValue) throws QonfigInterpretationException {
				return value;
			}
		});
	}

	void configureExternalModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("ext-model", ObservableModelElement.ExtModelElement.Def.class,
			creator(ObservableModelElement.ExtModelElement.Def::new));
		interpreter.createWith("event", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Event, "event");
		})//
		.createWith("action", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Action,
				"action");
		})//
		.createWith("value", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Value, "value");
		})//
		.createWith("list", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Collection,
				"list");
		})//
		.createWith("set", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Set, "set");
		})//
		.createWith("sorted-list", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(),
				ModelTypes.SortedCollection, "sorted-list");
		})//
		.createWith("sorted-set", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.SortedSet,
				"sorted-set");
		})//
		.createWith("value-set", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.ValueSet,
				"value-set");
		})//
		.createWith("map", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Double<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Map, "map");
		})//
		.createWith("sorted-map", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Double<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.SortedMap,
				"sorted-map");
		})//
		.createWith("multi-map", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Double<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.MultiMap,
				"multi-map");
		})//
		.createWith("sorted-multi-map", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Double<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.SortedMultiMap,
				"sorted-multi-map");
		})//
		;
	}

	void configureInternalModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("model", ObservableModelElement.DefaultModelElement.Def.class,
			creator(ObservableModelElement.DefaultModelElement.Def::new));
		interpreter.createWith("model", ObservableModelElement.LocalModelElementDef.class,
			creator(ObservableModelElement.LocalModelElementDef::new));
		interpreter.createWith("constant", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.ConstantValueDef::new));
		interpreter.createWith("value", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.SimpleValueDef::new));
		interpreter.createWith("action", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.Action::new));
		interpreter.createWith("action-group", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.ActionGroup::new));
		interpreter.createWith("loop", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.Loop::new));
		interpreter.createWith("value-set", CompiledModelValue.class, creator(ExpressoQonfigValues.ValueSet::new));
		interpreter.createWith("element", ExpressoQonfigValues.CollectionElement.class,
			creator(ExpressoQonfigValues.CollectionElement::new));
		interpreter.createWith("list", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.PlainCollectionDef::new));
		interpreter.createWith("set", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.SetDef::new));
		interpreter.createWith("sorted-list", ModelValueElement.CompiledSynth.class,
			creator(ExpressoQonfigValues.SortedCollectionDef::new));
		interpreter.createWith("sorted-set", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.SortedSetDef::new));
		interpreter.createWith("entry", ExpressoQonfigValues.MapEntry.class, creator(ExpressoQonfigValues.MapEntry::new));
		interpreter.createWith("map", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.PlainMapDef::new));
		interpreter.createWith("sorted-map", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.SortedMapDef::new));
		interpreter.createWith("multi-map", ModelValueElement.CompiledSynth.class, creator(ExpressoQonfigValues.PlainMultiMapDef::new));
		interpreter.createWith("sorted-multi-map", ModelValueElement.CompiledSynth.class,
			creator(ExpressoQonfigValues.SortedMultiMapDef::new));
	}
}

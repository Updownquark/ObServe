package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExpressoQonfigValues.FieldValueDef;
import org.observe.expresso.qonfig.ExpressoTransformations.CaseOp;
import org.observe.expresso.qonfig.ExpressoTransformations.If;
import org.observe.expresso.qonfig.ExpressoTransformations.IfOp;
import org.observe.expresso.qonfig.ExpressoTransformations.Return;
import org.observe.expresso.qonfig.ExpressoTransformations.Switch;
import org.qommons.Version;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementDef;
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

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String BASE = "Expresso-Base 0.1";

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
		// Not needed
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(ExWithElementModel.WITH_ELEMENT_MODEL, ExWithElementModel.Def.class,
			ExAddOn.creator(ExWithElementModel.Def::new));
		interpreter.createWith(ExElementModelValue.ELEMENT_MODEL_VALUE, ExElementModelValue.Def.class,
			ExAddOn.creator(ModelValueElement.Def.class, ExElementModelValue.Def::new));
		interpreter.createWith(ExWithLocalModel.WITH_LOCAL_MODEL, ExWithLocalModel.Def.class, ExAddOn.creator(ExWithLocalModel.Def::new));
		interpreter.createWith(ExWithRequiredModels.WITH_REQUIRED_MODELS, ExWithRequiredModels.Def.class,
			ExAddOn.creator(ExWithRequiredModels.Def::new));
		interpreter.createWith(ExpressoDocument.EXPRESSO_DOCUMENT, ExpressoDocument.Def.class, ExAddOn.creator(ExpressoDocument.Def::new));

		// To support external content
		interpreter.createWith(ExpressoExternalDocument.EXPRESSO_EXTERNAL_DOCUMENT, ExpressoExternalDocument.Def.class,
			ExElement.creator(ExpressoExternalDocument.Def::new));
		interpreter.createWith(AttributeBackedModelValue.ATTR_BACKED_MODEL_VALUE, AttributeBackedModelValue.Def.class,
			ExAddOn.creator(ExtModelValueElement.Def.class, AttributeBackedModelValue.Def::new));
		interpreter.createWith(ExpressoChildPlaceholder.CHILD_PLACEHOLDER, ExpressoChildPlaceholder.Def.class,
			ExElement.creator(ExpressoChildPlaceholder.Def::new));
		// We have to explicitly support any external reference types
		QonfigElementDef extReference = interpreter.getToolkit()
			.getElement(QonfigExternalDocument.QONFIG_REFERENCE_TK + ":" + ExpressoExternalReference.EXT_REFERENCE);

		Set<QonfigToolkit> supported = new HashSet<>();
		for (QonfigToolkit toolkit : interpreter.getKnownToolkits())
			supportExternalReferences(toolkit, interpreter, extReference, supported);

		interpreter.createWith(ExNamed.NAMED, ExNamed.Def.class, ExAddOn.creator(ExNamed.Def::new));
		interpreter.createWith(ExTyped.TYPED, ExTyped.Def.class, ExAddOn.creator(ExTyped.Def::new));
		interpreter.createWith(ExMapModelValue.MAP_MODEL_VALUE, ExMapModelValue.Def.class, ExAddOn.creator(ExMapModelValue.Def::new));
		interpreter.createWith(ExIntValue.INT_VALUE, ExIntValue.Def.class, ExAddOn.creator(ExIntValue.Def::new));
		interpreter.createWith(ExpressoQonfigValues.FieldValueDef.FIELD_VALUE, FieldValueDef.class, ExElement.creator(FieldValueDef::new));
		interpreter.createWith(ExComplexOperation.COMPLEX_OPERATION, ExComplexOperation.class, ExAddOn.creator(ExComplexOperation::new));
		interpreter.createWith(ExSort.SORT, ExSort.ExRootSort.class, ExElement.creator(ExSort.ExRootSort::new));
		interpreter.createWith(ExSort.SORT_BY, ExSort.ExSortBy.class, ExElement.creator(ExSort.class, ExSort.ExSortBy::new));
		interpreter.createWith(If.IF, If.class, ExElement.creator(If::new));
		interpreter.createWith(IfOp.IF_OP, IfOp.class, ExAddOn.creator(IfOp::new));
		interpreter.createWith(Switch.SWITCH, Switch.class, ExElement.creator(Switch::new));
		interpreter.createWith(CaseOp.CASE_OP, CaseOp.class, ExAddOn.creator(CaseOp::new));
		interpreter.createWith(Return.RETURN, Return.class, ExElement.creator(Return::new));
		configureBaseModels(interpreter);
		configureExternalModels(interpreter);
		configureInternalModels(interpreter);
		ExpressoTransformations.configureTransformation(interpreter);
		return interpreter;
	}

	private void supportExternalReferences(QonfigToolkit toolkit, QonfigInterpreterCore.Builder interpreter, QonfigElementDef extReference,
		Set<QonfigToolkit> supported) {
		if (!supported.add(toolkit))
			return;
		for (QonfigElementDef type : toolkit.getDeclaredElements().values()) {
			if (extReference.isAssignableFrom(type))
				interpreter.createWith(type, ExpressoExternalReference.Def.class, ExElement.creator(ExpressoExternalReference.Def::new));
		}
		for (QonfigToolkit dep : toolkit.getDependencies().values())
			supportExternalReferences(dep, interpreter, extReference, supported);
	}

	void configureBaseModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(ClassViewElement.IMPORTS, ClassViewElement.class, ExElement.creator(ClassViewElement::new));
		interpreter.createWith(ClassViewElement.IMPORT, ClassViewElement.ImportElement.class,
			ExElement.creator(ClassViewElement.ImportElement::new));
		interpreter.createWith(ObservableModelElement.ModelSetElement.MODELS, ObservableModelElement.ModelSetElement.Def.class,
			ExElement.creator(ObservableModelElement.ModelSetElement.Def::new));
		interpreter.createWith(ExpressoHeadSection.HEAD, ExpressoHeadSection.Def.class, ExElement.creator(ExpressoHeadSection.Def::new));
		interpreter.modifyWith("map", Object.class, new QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				if (session.get(ExMapModelValue.KEY_TYPE_KEY) == null) {
					QonfigValue typeV = session.getElement().getAttributes().get(session.attributes().get("key-type").getDefinition());
					if (typeV != null && !typeV.text.isEmpty()) {
						session.put(ExMapModelValue.KEY_TYPE_KEY,
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
			ExElement.creator(ObservableModelElement.ExtModelElement.Def::new));
		interpreter.createWith("event", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.Single<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Event, "event");
		})//
		.createWith("action", ExtModelValueElement.Def.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			return new ExtModelValueElement.Def.UnTyped<>(exS.getElementRepresentation(), exS.getFocusType(), ModelTypes.Action);
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
			ExElement.creator(ObservableModelElement.DefaultModelElement.Def::new));
		interpreter.createWith("model", ObservableModelElement.LocalModelElementDef.class,
			ExElement.creator(ObservableModelElement.LocalModelElementDef::new));
		interpreter.createWith("constant", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.ConstantValueDef::new));
		interpreter.createWith("value", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.SimpleValueDef::new));
		interpreter.createWith("action", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.Action::new));
		interpreter.createWith("event", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.Event::new));
		interpreter.createWith("action-group", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.ActionGroup::new));
		interpreter.createWith("loop", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.Loop::new));
		interpreter.createWith("timer", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.Timer::new));
		interpreter.createWith("value-set", CompiledModelValue.class, ExElement.creator(ExpressoQonfigValues.ValueSet::new));
		interpreter.createWith("element", ExpressoQonfigValues.CollectionElement.class,
			ExElement.creator(ExpressoQonfigValues.CollectionElement::new));
		interpreter.createWith("list", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.PlainCollectionDef::new));
		interpreter.createWith("set", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.SetDef::new));
		interpreter.createWith("sorted-list", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.SortedCollectionDef::new));
		interpreter.createWith("sorted-set", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.SortedSetDef::new));
		interpreter.createWith("entry", ExpressoQonfigValues.MapEntry.class, ExElement.creator(ExpressoQonfigValues.MapEntry::new));
		interpreter.createWith("map", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.PlainMapDef::new));
		interpreter.createWith("sorted-map", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.SortedMapDef::new));
		interpreter.createWith("multi-map", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.PlainMultiMapDef::new));
		interpreter.createWith("sorted-multi-map", ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExpressoQonfigValues.SortedMultiMapDef::new));
		interpreter.createWith("hook", ModelValueElement.CompiledSynth.class, ExElement.creator(ExpressoQonfigValues.Hook::new));
	}
}

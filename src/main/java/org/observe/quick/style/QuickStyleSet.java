package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.quick.QuickCore;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreter;
import org.qommons.config.QonfigInterpreter.Builder;

public class QuickStyleSet {
	interface StyleModelValues {
		<M, MV extends M> StyleModelValues withModelValue(QuickModelValue<M, MV> modelValue, MV value);
	}

	private final Map<QonfigElementOrAddOn, QuickStyleType> theStyleTypes;
	private final ThreadLocal<Map<QuickModelValue<?, ?>, Object>> theModelValues;

	public QuickStyleSet() {
		theStyleTypes = new HashMap<>();
		theModelValues = ThreadLocal.withInitial(HashMap::new);
	}

	public synchronized QuickStyleType styled(QonfigElementOrAddOn element, ExpressoSession<?> session)
		throws QonfigInterpretationException {
		QuickStyleType styled = theStyleTypes.get(element);
		if (styled != null)
			return styled;
		else if (session == null)
			return null;
		List<QuickStyleType> parents = new ArrayList<>();
		QonfigElementOrAddOn superEl = element.getSuperElement();
		if (superEl != null)
			parents.add(styled(superEl, session));
		for (QonfigAddOn inh : element.getInheritance())
			parents.add(styled(inh, session));
		if (parents.isEmpty())
			parents = Collections.emptyList();
		else
			parents = Collections.unmodifiableList(parents);
		Map<String, QuickModelValue<?, ?>> declaredModelValues = new LinkedHashMap<>();
		Map<String, QuickModelValue<?, ?>> modelValues = new LinkedHashMap<>();
		Map<String, QuickStyleAttribute<?>> declaredAttributes = new LinkedHashMap<>();
		BetterMultiMap<String, QuickStyleAttribute<?>> attributes = BetterHashMultiMap.<String, QuickStyleAttribute<?>> build().buildMultiMap();
		styled = new QuickStyleType(this, element, parents, Collections.unmodifiableMap(declaredModelValues),
			Collections.unmodifiableMap(modelValues), Collections.unmodifiableMap(declaredAttributes),
			BetterCollections.unmodifiableMultiMap(attributes));

		QonfigElement modelEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(QuickCore.CORE.get().getAddOn("styled").getMetaSpec().getChild("widget-model").getDeclared()).peekFirst();
		if (modelEl != null) {
			QonfigInterpreter.Builder<?, ?> interpreterBuilder = QonfigInterpreter.build(QuickStyleSet.class, QuickCore.CORE.get())
				.forToolkit(QuickCore.CORE.get());
			configureModelInterpretation(interpreterBuilder, session);
			QonfigInterpreter<?> interpreter = interpreterBuilder.build();
			QonfigAttributeDef.Declared nameAttr = Expresso.EXPRESSO.get().getAddOn("model-element").getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared priorityAttr = QuickCore.CORE.get().getAddOn("widget-model-value").getAttribute("priority")
				.getDeclared();
			for (QonfigElement modelV : modelEl.getChildrenInRole(Expresso.EXPRESSO.get(), "model", "value")) {
				String name = modelV.getAttributeText(nameAttr);
				if (declaredModelValues.containsKey(name)) {
					session.withError("Multiple model values named '" + name + "' declared");
					continue;
				}
				ModelInstanceType<?, ?> type = interpreter.interpret(modelV).interpret(ModelInstanceType.class);
				declaredModelValues.put(name, new QuickModelValue<>(styled, name, type, //
					Integer.parseInt(modelV.getAttributeText(priorityAttr))));
			}
		}

		QonfigElement stylesEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(QuickCore.CORE.get().getAddOn("styled").getMetaSpec().getChild("styles").getDeclared()).peekFirst();
		if (stylesEl != null) {
			QonfigAttributeDef.Declared nameAttr = QuickCore.CORE.get().getElement("style-attribute").getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared typeAttr = QuickCore.CORE.get().getElement("style-attribute").getAttribute("type").getDeclared();
			QonfigAttributeDef.Declared trickleAttr = QuickCore.CORE.get().getElement("style-attribute").getAttribute("trickle-down")
				.getDeclared();
			for (QonfigElement styleAttr : stylesEl.getChildrenInRole(QuickCore.CORE.get(), "styles", "style-attribute")) {
				String name = styleAttr.getAttributeText(nameAttr);
				if (declaredAttributes.containsKey(name)) {
					session.withError("Multiple style attributes named '" + name + "' declared");
					continue;
				}
				declaredAttributes.put(name, new QuickStyleAttribute<>(styled, name, //
					Expresso.parseType(styleAttr.getAttributeText(typeAttr), session.getModels(), session.getClassView()), //
					styleAttr.getAttribute(trickleAttr, boolean.class)));
			}
		}
		modelValues.putAll(declaredModelValues);
		attributes.putAll(declaredAttributes);

		for (QuickStyleType parent : parents) {
			for (QuickModelValue<?, ?> mv : parent.getModelValues().values()) {
				if (modelValues.containsKey(mv.getName()))
					session.withWarning("Model value " + mv + " is name-eclipsed by " + modelValues.get(mv.getName()));
				else
					modelValues.putIfAbsent(mv.getName(), mv);
			}
			attributes.putAll(parent.getAttributes());
		}
		theStyleTypes.put(element, styled);
		return styled;
	}

	private void configureModelInterpretation(Builder<?, ?> interpreterBuilder, ExpressoSession<?> session) {
		interpreterBuilder.createWith("action", ModelInstanceType.class, session2 -> {
			return ModelTypes.Action
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("value", ModelInstanceType.class, session2 -> {
			return ModelTypes.Value
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("list", ModelInstanceType.class, session2 -> {
			return ModelTypes.Collection
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("sorted-list", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedCollection
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("set", ModelInstanceType.class, session2 -> {
			return ModelTypes.Set
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("sorted-set", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedSet
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView()));
		}).createWith("map", ModelInstanceType.class, session2 -> {
			return ModelTypes.Map.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getModels(), session.getClassView()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView())//
				);
		}).createWith("sorted-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getModels(), session.getClassView()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView())//
				);
		}).createWith("multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.MultiMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getModels(), session.getClassView()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView())//
				);
		}).createWith("sorted-multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMultiMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getModels(), session.getClassView()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getModels(), session.getClassView())//
				);
		});
	}

	public QuickStyleSet withModelValues(Consumer<StyleModelValues> modelValues) {
		Map<QuickModelValue<?, ?>, Object> values = theModelValues.get();
		values.clear();
		modelValues.accept(new StyleModelValues() {
			@Override
			public <M, MV extends M> StyleModelValues withModelValue(QuickModelValue<M, MV> modelValue, MV value) {
				values.put(modelValue, value);
				return this;
			}
		});
		return this;
	}

	public <M, MV extends M> MV getModelValue(QuickModelValue<M, MV> modelValue) {
		Map<QuickModelValue<?, ?>, Object> values = theModelValues.get();
		MV value = (MV) values.get(modelValue);
		if (value == null)
			throw new IllegalStateException("Model value '" + modelValue + "' has not been installed by the interpretation");
		return value;
	}
}

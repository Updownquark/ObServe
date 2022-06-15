package org.observe.quick.style;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.quick.QuickCore;
import org.observe.util.TypeTokens;
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

import com.google.common.reflect.TypeToken;

public class QuickStyleSet {
	public static final QonfigAddOn STYLED = QuickCore.CORE.get().getAddOn("styled");

	interface StyleModelValues {
		<T> StyleModelValues withModelValue(QuickModelValue<T> modelValue, SettableValue<T> value);
	}

	private final Map<QonfigElementOrAddOn, QuickStyleType> theStyleTypes;

	public QuickStyleSet() {
		theStyleTypes = new HashMap<>();
	}

	public synchronized QuickStyleType styled(QonfigElementOrAddOn element, ExpressoSession<?> session)
		throws QonfigInterpretationException {
		QuickStyleType styled = theStyleTypes.get(element);
		if (styled != null)
			return styled;
		else if (session == null)
			return null;
		else if (!STYLED.isAssignableFrom(element))
			return null;
		List<QuickStyleType> parents = new ArrayList<>();
		QonfigElementOrAddOn superEl = element.getSuperElement();
		if (superEl != null) {
			QuickStyleType parent = styled(superEl, session);
			if (parent != null)
				parents.add(parent);
		}
		for (QonfigAddOn inh : element.getInheritance()) {
			QuickStyleType parent = styled(inh, session);
			if (parent != null)
				parents.add(parent);
		}
		if (parents.isEmpty())
			parents = Collections.emptyList();
		else
			parents = Collections.unmodifiableList(parents);
		Map<String, QuickModelValue<?>> declaredModelValues = new LinkedHashMap<>();
		Map<String, QuickModelValue<?>> modelValues = new LinkedHashMap<>();
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
			QonfigAttributeDef.Declared nameAttr = Expresso.EXPRESSO.get().getAddOn("model-element").getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared typeAttr = Expresso.EXPRESSO.get().getElement("model-value").getAttribute("type").getDeclared();
			QonfigAttributeDef.Declared priorityAttr = QuickCore.CORE.get().getAddOn("widget-model-value").getAttribute("priority")
				.getDeclared();
			for (QonfigElement modelV : modelEl.getChildrenInRole(Expresso.EXPRESSO.get(), "model", "value")) {
				String name = modelV.getAttributeText(nameAttr);
				if (declaredModelValues.containsKey(name)) {
					session.withError("Multiple model values named '" + name + "' declared");
					continue;
				}
				TypeToken<?> type;
				try {
					type = TypeTokens.get().parseType(modelV.getAttributeText(typeAttr));
				} catch (IllegalArgumentException | ParseException e) {
					throw new QonfigInterpretationException("Could not parse type of style model value '" + name + "'", e);
				}
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
					Expresso.parseType(styleAttr.getAttributeText(typeAttr), session.getExpressoEnv()), //
					styleAttr.getAttribute(trickleAttr, boolean.class)));
			}
		}
		modelValues.putAll(declaredModelValues);
		attributes.putAll(declaredAttributes);

		for (QuickStyleType parent : parents) {
			for (QuickModelValue<?> mv : parent.getModelValues().values()) {
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

	public Set<QuickModelValue<?>> getModelValues(QonfigElement element, ExpressoSession<?> session) throws QonfigInterpretationException {
		Set<QuickModelValue<?>> modelValues = new LinkedHashSet<>();
		QuickStyleType type = styled(element.getType(), session);
		if (type != null)
			modelValues.addAll(type.getModelValues().values());
		for (QonfigAddOn inh : element.getInheritance().values()) {
			type = styled(element.getType(), session);
			if (type != null)
				modelValues.addAll(type.getModelValues().values());
		}
		return Collections.unmodifiableSet(modelValues);
	}

	private void configureModelInterpretation(Builder<?, ?> interpreterBuilder, ExpressoSession<?> session) {
		interpreterBuilder.createWith("action", ModelInstanceType.class, session2 -> {
			return ModelTypes.Action
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("value", ModelInstanceType.class, session2 -> {
			return ModelTypes.Value
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("list", ModelInstanceType.class, session2 -> {
			return ModelTypes.Collection
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("sorted-list", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedCollection
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("set", ModelInstanceType.class, session2 -> {
			return ModelTypes.Set
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("sorted-set", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedSet
				.forType(Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("map", ModelInstanceType.class, session2 -> {
			return ModelTypes.Map.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("sorted-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.MultiMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("sorted-multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMultiMap.forType(//
				Expresso.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				Expresso.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		});
	}
}

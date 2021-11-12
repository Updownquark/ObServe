package org.observe.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.util.ModelType.ModelInstanceType;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;

public interface ObservableModelSet {
	interface ValueContainer<M, MV extends M> extends Function<ModelSetInstance, MV> {
		ModelInstanceType<M, MV> getType();

		MV get(ModelSetInstance models);

		@Override
		default MV apply(ModelSetInstance models) {
			return get(models);
		}
	}

	static <M, MV extends M> ValueContainer<M, MV> container(Function<ModelSetInstance, MV> value, ModelInstanceType<M, MV> type) {
		class SyntheticValueContainer implements ValueContainer<M, MV> {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return type;
			}

			private Function<ModelSetInstance, MV> getValue() {
				return value;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return value.apply(extModels);
			}

			@Override
			public int hashCode() {
				return value.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SyntheticValueContainer))
					return false;
				return value.equals(((SyntheticValueContainer) obj).getValue());
			}

			@Override
			public String toString() {
				return value.toString();
			}
		}
		return new SyntheticValueContainer();
	}

	ObservableModelSet getParent();

	String getPath();

	ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException;

	default <M> ValueContainer<M, ?> get(String path, ModelType<M> type) throws QonfigInterpretationException {
		ValueContainer<?, ?> thing = get(path, true);
		if (thing.getType().getModelType() != type)
			throw new QonfigInterpretationException(path + " is a " + thing.getType() + ", not a " + type);
		return (ValueContainer<M, ?>) thing;
	}

	default <M, MV extends M> ValueContainer<M, MV> get(String path, ModelInstanceType<M, MV> type)
		throws QonfigInterpretationException {
		ValueContainer<Object, Object> thing = (ValueContainer<Object, Object>) get(path, true);
		if (type == null)
			return (ValueContainer<M, MV>) thing;
		return thing.getType().as(thing, type);
	}

	ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until);

	public static Builder build() {
		return new Builder(null, null, "");
	}

	public static ExternalModelSetBuilder buildExternal() {
		return new ExternalModelSetBuilder(null, "");
	}

	public static class ModelSetInstance {
		final Map<Default.Placeholder<?, ?>, Object> theThings;
		private Object theModelConfiguration;
		private final Observable<?> theUntil;

		ModelSetInstance(Observable<?> until) {
			theThings = new LinkedHashMap<>();
			theUntil = until;
		}

		public Object getModelConfiguration() {
			return theModelConfiguration;
		}

		void setModelConfiguration(Object modelConfiguration) {
			theModelConfiguration = modelConfiguration;
		}

		public Observable<?> getUntil() {
			return theUntil;
		}
	}

	public class ExternalModelSet {
		final ExternalModelSet theRoot;
		private final String thePath;
		final Map<String, Placeholder> theThings;

		ExternalModelSet(ExternalModelSet root, String path, Map<String, Placeholder> things) {
			theRoot = root == null ? this : root;
			thePath = path;
			theThings = things;
		}

		public String getPath() {
			return thePath;
		}

		public <M> M get(String path, ModelInstanceType<?, ?> type) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				ExternalModelSet subModel = theRoot.getSubModel(path.substring(0, dot));
				return subModel.get(path.substring(dot + 1), type);
			}
			Placeholder thing = theThings.get(path);
			if (thing == null)
				throw new QonfigInterpretationException("No such " + type + " declared: '" + pathTo(path) + "'");
			ModelType.ModelInstanceConverter<Object, M> converter = (ModelType.ModelInstanceConverter<Object, M>) thing.type.convert(type);
			if (converter == null)
				throw new QonfigInterpretationException("Cannot convert " + path + " (" + thing.type + ") to " + type);
			return converter.convert(thing.thing);
		}

		private ExternalModelSet getSubModel(String path) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder subModel = theThings.get(modelName);
			if (subModel == null)
				throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
			else if (subModel.type.getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return (ExternalModelSet) subModel.thing;
			else
				return ((ExternalModelSet) subModel.thing).getSubModel(path.substring(dot + 1));
		}

		String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().thing instanceof ExternalModelSet)
					((ExternalModelSet) thing.getValue().thing).print(str, indent + 1);
			}
		}

		static class Placeholder {
			final ModelInstanceType<?, ?> type;
			final Object thing;

			Placeholder(ModelInstanceType<?, ?> type, Object thing) {
				this.type = type;
				this.thing = thing;
			}
		}
	}

	public interface ValueGetter<T> {
		T get(ModelSetInstance models, ExternalModelSet extModels);
	}

	public class ExternalModelSetBuilder extends ExternalModelSet {
		ExternalModelSetBuilder(ExternalModelSetBuilder root, String path) {
			super(root, path, new LinkedHashMap<>());
		}

		public <M, MV extends M> ExternalModelSetBuilder with(String name, ModelInstanceType<M, MV> type, MV item)
			throws QonfigInterpretationException {
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new ExternalModelSet.Placeholder(type, item));
			return this;
		}

		public ExternalModelSetBuilder addSubModel(String name) throws QonfigInterpretationException {
			if (theThings.containsKey(name))
				throw new QonfigInterpretationException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			ExternalModelSetBuilder subModel = new ExternalModelSetBuilder((ExternalModelSetBuilder) theRoot, pathTo(name));
			theThings.put(name, new ExternalModelSet.Placeholder(ModelTypes.Model.instance(), subModel));
			return subModel;
		}

		public ExternalModelSet build() {
			return _build(null, getPath());
		}

		private ExternalModelSet _build(ExternalModelSet root, String path) {
			Map<String, ExternalModelSet.Placeholder> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			ExternalModelSet model = new ExternalModelSet(root, path, Collections.unmodifiableMap(things));
			if (root == null)
				root = model;
			for (Map.Entry<String, ExternalModelSet.Placeholder> thing : theThings.entrySet()) {
				if (thing.getValue().type.getModelType() == ModelTypes.Model)
					things.put(thing.getKey(), new ExternalModelSet.Placeholder(ModelTypes.Model.instance(), //
						((ExternalModelSetBuilder) thing.getValue().thing)._build(root, path + "." + thing.getKey())));
				else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}

	public class Default implements ObservableModelSet {
		private final Default theParent;
		protected final Default theRoot;
		protected final String thePath;
		protected final Map<String, Placeholder<?, ?>> theThings;
		protected final Function<ModelSetInstance, ?> theModelConfiguration;

		protected Default(Default root, Default parent, String path, Map<String, Placeholder<?, ?>> things,
			Function<ModelSetInstance, ?> modifiableConfiguration) {
			theRoot = root == null ? this : root;
			theParent = parent;
			thePath = path;
			theThings = things;
			theModelConfiguration = modifiableConfiguration;
		}

		@Override
		public Default getParent() {
			return theParent;
		}

		@Override
		public ValueContainer<?, ?> get(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.lastIndexOf('.');
			if (dot >= 0) {
				Default subModel = theRoot.getSubModel(path.substring(0, dot), required);
				return subModel == null ? null : subModel.get(path.substring(dot + 1), required);
			}
			ValueContainer<?, ?> thing = theThings.get(path);
			if (thing == null && theRoot != this) {
				Placeholder<?, ?> p = theRoot.theThings.get(path);
				if (p != null && p.model != null)
					return p;
			}
			if (thing == null && required)
				throw new QonfigInterpretationException("No such value " + path);
			return thing;
		}

		private Default getSubModel(String path, boolean required) throws QonfigInterpretationException {
			int dot = path.indexOf('.');
			String modelName;
			if (dot >= 0) {
				modelName = path.substring(0, dot);
			} else
				modelName = path;
			Placeholder<?, ?> subModel = theThings.get(modelName);
			if (subModel == null) {
				if (required)
					throw new QonfigInterpretationException("No such sub-model declared: '" + pathTo(modelName) + "'");
				else
					return null;
			} else if (subModel.type.getModelType() != ModelTypes.Model)
				throw new QonfigInterpretationException("'" + pathTo(modelName) + "' is a " + subModel.type + ", not a Model");
			if (dot < 0)
				return subModel.model;
			else
				return subModel.model.getSubModel(path.substring(dot + 1), required);
		}

		public String pathTo(String name) {
			if (thePath.isEmpty())
				return name;
			else
				return thePath + "." + name;
		}

		@Override
		public String getPath() {
			return thePath;
		}

		@Override
		public ModelSetInstance createInstance(ExternalModelSet extModel, Observable<?> until) {
			if (until == null)
				until = Observable.empty();
			ModelSetInstance modelSet = new ModelSetInstance(until);
			install(modelSet, extModel);
			return modelSet;
		}

		private void install(ModelSetInstance modelSet, ExternalModelSet extModel) {
			Object modelConfig = theModelConfiguration == null ? null : theModelConfiguration.apply(modelSet);
			modelSet.setModelConfiguration(modelConfig);
			for (Placeholder<?, ?> thing : theThings.values()) {
				if (thing.model != null) {
					thing.model.install(modelSet, extModel);
					modelSet.setModelConfiguration(modelConfig);
				} else
					modelSet.theThings.put(thing, thing.getter.get(modelSet, extModel));
			}
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(thePath.isEmpty() ? "<root>" : thePath);
			print(str, 1);
			return str.toString();
		}

		protected void print(StringBuilder str, int indent) {
			for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
				str.append('\n');
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(thing.getKey()).append(": ").append(thing.getValue().type);
				if (thing.getValue().model != null)
					thing.getValue().model.print(str, indent + 1);
			}
		}

		static class Placeholder<M, MV extends M> implements ValueContainer<M, MV> {
			final String thePath;
			final ModelInstanceType<M, MV> type;
			final ValueGetter<MV> getter;
			final Default model;

			Placeholder(String path, ModelInstanceType<M, MV> type, ValueGetter<MV> getter, Default model) {
				thePath = path;
				this.type = type;
				this.getter = getter;
				this.model = model;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return type;
			}

			@Override
			public MV get(ModelSetInstance extModels) {
				return (MV) extModels.theThings.get(this);
			}

			@Override
			public String toString() {
				return new StringBuilder(type.toString()).append('@').append(thePath).toString();
			}
		}
	}

	public class Builder extends Default implements ObservableModelSet {
		private Function<ModelSetInstance, ?> theModifiableConfiguration;

		private Builder(Builder root, Builder parent, String path) {
			super(root, parent, path, new LinkedHashMap<>(), null);
		}

		public Builder setModelConfiguration(Function<ModelSetInstance, ?> modelConfiguration) {
			theModifiableConfiguration = modelConfiguration;
			return this;
		}

		public <M, MV extends M> Builder with(String name, ModelInstanceType<M, MV> type, ValueGetter<MV> getter) {
			if (theThings.containsKey(name))
				throw new IllegalArgumentException(
					"A value of type " + theThings.get(name).type + " has already been added as '" + name + "'");
			theThings.put(name, new Placeholder<>(pathTo(name), type, getter, null));
			return this;
		}

		public Builder createSubModel(String name) {
			Placeholder<?, ?> thing = theThings.get(name);
			if (thing == null) {
				Builder subModel = new Builder((Builder) theRoot, this, pathTo(name));
				theThings.put(name, new Placeholder<>(pathTo(name), ModelTypes.Model.instance(), null, subModel));
				return subModel;
			} else if (thing.model != null)
				return (Builder) thing.model;
			else
				throw new IllegalArgumentException("A value of type " + thing.type + " has already been added as '" + name + "'");
		}

		public ObservableModelSet build() {
			return _build(null, null, thePath);
		}

		private Default _build(Default root, Default parent, String path) {
			Map<String, Placeholder<?, ?>> things = new LinkedHashMap<>(theThings.size() * 3 / 2 + 1);
			Default model = new Default(root, parent, path, Collections.unmodifiableMap(things), theModifiableConfiguration);
			if (root == null)
				root = model;
			for (Map.Entry<String, Placeholder<?, ?>> thing : theThings.entrySet()) {
				if (thing.getValue().type.getModelType() == ModelTypes.Model)
					things.put(thing.getKey(),
						new Placeholder<>(thing.getValue().thePath, ModelTypes.Model.instance(), null,
							((Builder) thing.getValue().model)._build(root, model, //
								(path.isEmpty() ? "" : path + ".") + thing.getKey())));
				else
					things.put(thing.getKey(), thing.getValue());
			}
			return model;
		}
	}
}

package org.observe.quick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ClassView;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ExpressoInterpreter.Builder;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;

import com.google.common.reflect.TypeToken;

public class QuickX extends QuickBase {
	public static final QonfigToolkitAccess EXT = new QonfigToolkitAccess(QuickBase.class, "quick-x.qtd", QuickBase.BASE);

	@Override
	public <QIS extends ExpressoSession<QIS>, B extends Builder<QIS, B>> B configureInterpreter(B interpreter) {
		super.configureInterpreter(interpreter);
		QonfigToolkit ext = EXT.get();
		ExpressoInterpreter.Builder<?, ?> tkInt = interpreter.forToolkit(ext);
		tkInt.createWith("tree-table", QuickComponentDef.class, this::interpretTreeTable);
		return interpreter;
	}

	private <T, E extends PanelPopulation.TreeTableEditor<T, E>> QuickComponentDef interpretTreeTable(ExpressoSession<?> session)
		throws QonfigInterpretationException {
		return interpretAbstractTree(session, new TreeMaker<T, E>() {
			TypeToken<CategoryRenderStrategy<BetterList<T>, ?>> columnType;
			Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>>> columnsAttr;
			List<Column<BetterList<T>, ?>> columns = new ArrayList<>();

			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue, ? extends SettableValue<T>> root)
				throws QonfigInterpretationException {
				ClassView cv = (ClassView) session.get("imports");
				TypeToken<T> type = (TypeToken<T>) root.getType().getType(0);
				columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class).<CategoryRenderStrategy<BetterList<T>, ?>> parameterized(//
					TypeTokens.get().keyFor(BetterList.class).parameterized(type), TypeTokens.get().WILDCARD);
				ObservableExpression columnsX = session.getAttribute("columns", ObservableExpression.class);
				columnsAttr = columnsX == null ? null : columnsX.evaluate(ModelTypes.Collection.forType(columnType), model, cv);
				session.put("model-type", type);
				for (ExpressoSession<?> columnEl : session.forChildren("column"))
					columns.add(columnEl.interpret(Column.class));
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData) {
				container.addTreeTable2(root, children, t -> {
					treeData.accept((E) t);
					if (columnsAttr != null) {
						// The flatten here is so columns can also be specified on the table.
						// Without this, additional columns could not be added if, as is likely, the columnsAttr collection is unmodifiable.
						t.withColumns(ObservableCollection.flattenCollections(columnType, //
							columnsAttr.apply(builder.getModels()), //
							ObservableCollection.build(columnType).build()).collect());
					}
					for (Column<BetterList<T>, ?> column : columns)
						t.withColumn(column.createColumn(builder.getModels()));
				});
			}
		});
	}
}

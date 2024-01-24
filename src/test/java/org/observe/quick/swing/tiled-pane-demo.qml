<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:x="Quick-X v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Quick Tiled Pane Demo`" close-action="exit">
	<head>
		<imports>
			<import>org.observe.quick.swing.SwingTestEntity</import>
		</imports>
		<models>
			<model name="app">
				<list name="values"	type="SwingTestEntity">
					<element>new SwingTestEntity("First")</element>
					<element>new SwingTestEntity("Second")</element>
					<element>new SwingTestEntity("Third")</element>
				</list>
				<value name="focusText" init="``" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify">
		<box layout="inline-layout" orientation="horizontal" main-align="leading">
			<button action="app.values.add(new SwingTestEntity(&quot;New Entity&quot;))" icon="`/icons/add.png`" />
		</box>
		<text-field value="app.focusText" />
		<scroll>
			<tiled-pane role="content" values="app.values" active-value-name="entity" layout="inline-layout" orientation="vertical">
				<model>
					<field-value name="name" source="entity==null ? null : entity.getName()" source-as="source" save="entity.setName(source)" />
					<field-value name="b" source="entity==null ? false : entity.getBoolean()" source-as="source" save="entity.setBoolean(source)" />
					<field-value name="dbl" source="entity==null ? 0.0 : entity.getDouble()" source-as="source" save="entity.setDouble(source)" />
				</model>
				<box layout="simple-layout">
					<line-border />
					<label left="1" top="3" width="80">Name:</label>
					<text-field left="44" top="3" right="`99%`-18.0" pref-width="200" value="name" />
					<label left="1" top="25" width="80">Bool:</label>
					<check-box left="44" top="25" value="b" />
					<label left="1" top="50" width="80">Double:</label>
					<text-field left="44" right="`99%`" top="50" bottom="`3xp`" value="dbl" />
					<label right="`99%`" top="3" width="15" height="8" icon="`/icons/redX.png`">
						<on-click>app.values.remove(entity)</on-click>
					</label>
				</box>
			</tiled-pane>
		</scroll>
		<text-field value="app.focusText" />
	</box>
</quick>

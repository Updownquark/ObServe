<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:tests="Quick-Swing-Tests v0.1" xmlns:quick="Quick-Base v0.1" with-extension="window"
	title="`Simple External Reference Demo`" close-action="exit">
	<head>
		<imports>
			<import>org.observe.quick.swing.SwingTestEntity</import>
		</imports>
		<models>
			<model name="app">
				<list name="entities1" type="SwingTestEntity">
					<element>new SwingTestEntity("Set 1, Entity 1")</element>
					<element>new SwingTestEntity("Set 1, Entity 2").setBoolean(true)</element>
					<element>new SwingTestEntity("Set 1, Entity 3")</element>
				</list>
				<list name="entities2" type="SwingTestEntity">
					<element>new SwingTestEntity("Set 2, Entity 1")</element>
					<element>new SwingTestEntity("Set 2, Entity 2").setBoolean(true)</element>
					<element>new SwingTestEntity("Set 2, Entity 3").setBoolean(true)</element>
					<element>new SwingTestEntity("Set 2, Entity 4")</element>
				</list>
			</model>
		</models>
		<style-sheet>
			<import-style-sheet name="base" ref="classpath://org/observe/quick/base/quick-base.qss" />
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify">
		<box layout="inline-layout" orientation="vertical" cross-align="center">
			<style attr="font-weight">`bold`</style>
			<label>The borders of each of the tables should update their titles and colors</label>
			<label>according to the number of rows with checks</label>
		</box>
		<entity-table entities="app.entities1">
			<titled-border role="border0" title="`Entity Set 1 (`+size+` true)`">
				<model>
					<transform name="size" source="app.entities1">
						<filter source-as="entity" test="entity.getBoolean()" />
						<size />
					</transform>
				</model>
			</titled-border>
		</entity-table>
		<entity-table entities="app.entities2">
			<model>
				<transform name="size" source="app.entities2">
						<filter source-as="entity" test="entity.getBoolean()" />
					<size />
				</transform>
			</model>
			<titled-border role="border0" title="`Entity Set 2 (`+size+` true)`" />
		</entity-table>
	</box>
</quick>

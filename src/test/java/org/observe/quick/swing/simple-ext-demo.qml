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
					<element>new SwingTestEntity("Set 1, Entity 2")</element>
					<element>new SwingTestEntity("Set 1, Entity 3")</element>
				</list>
				<list name="entities2" type="SwingTestEntity">
					<element>new SwingTestEntity("Set 2, Entity 1")</element>
					<element>new SwingTestEntity("Set 2, Entity 2")</element>
					<element>new SwingTestEntity("Set 2, Entity 3")</element>
				</list>
			</model>
		</models>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify" name="root">
		
		<entity-table entities="app.entities1">
			<titled-border title="`Entity Set 1 (`+size+`)`">
				<model>
					<transform name="size" source="app.entities2">
						<size />
					</transform>
				</model>
			</titled-border>
		</entity-table>
		<entity-table entities="app.entities2">
			<model>
				<transform name="size" source="app.entities2">
					<size />
				</transform>
			</model>
			<titled-border title="`Entity Set 2 (`+size+`)`" />
		</entity-table>
	</box>
</quick>

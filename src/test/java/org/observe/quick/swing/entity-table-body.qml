<?xml version="1.0" encoding="UTF-8"?>

<expresso-external-document xmlns:tests="Quick-Swing-Tests v0.1" xmlns:base="Quick-Base v0.1" fulfills="entity-table">
	<head role="head">
		<imports>
			<import>org.observe.quick.swing.SwingTestEntity</import>
		</imports>
		<models role="models">
			<ext-model name="attrs">
				<list name="entities" type="SwingTestEntity" source-attr="entities" />
			</ext-model>
			<model name="app">
				<transform name="color" source="attrs.entities">
					<filter source-as="entity" test="entity.getBoolean()" />
					<size />
					<switch type="java.awt.Color" default="`black`">
						<return case="1">`red`</return>
						<return case="2">`blue`</return>
						<return case="3">`green`</return>
						<return case="4">`purple`</return>
					</switch>
				</transform>
			</model>
		</models>
	</head>
	<table rows="attrs.entities" active-value-name="entity">
		<child-placeholder ref-role="border0">
			<style attr="border-color">app.color</style>
		</child-placeholder>
		<column name="`Name`" value="entity.getName()">
			<column-edit type="modify-row-value" column-edit-value-name="newName" commit="entity.setName(newName)">
				<text-field />
			</column-edit>
		</column>
		<column name="`Boolean`" value="entity.getBoolean()" column-value-name="b">
			<check-box value="b" />
			<column-edit type="modify-row-value" column-edit-value-name="newB" commit="entity.setBoolean(newB)" row-update="true">
				<check-box />
			</column-edit>
		</column>
	</table>
</expresso-external-document>

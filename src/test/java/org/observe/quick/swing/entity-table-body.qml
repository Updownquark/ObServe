<?xml version="1.0" encoding="UTF-8"?>

<expresso-external-content xmlns:tests="Quick-Swing-Tests v0.1" xmlns:base="Quick-Base v0.1" fulfills="entity-table">
	<expresso role="head">
		<imports>
			<import>org.observe.quick.swing.SwingTestEntity</import>
		</imports>
		<models role="models">
			<ext-model name="attrs">
				<list name="entities" type="SwingTestEntity" source-attr="entities" />
			</ext-model>
		</models>
	</expresso>
	<table role="fulfillment" rows="attrs.entities" active-value-name="entity">
		<child-placeholder ref-role="border" />
		<column name="`Name`" value="entity.getName()">
			<column-edit type="modify-row-value" column-edit-value-name="newName" commit="entity.setName(newName)">
				<text-field />
			</column-edit>
		</column>
		<column name="`Boolean`" value="entity.getBoolean()" column-value-name="b">
			<check-box value="b" />
			<column-edit type="modify-row-value" column-edit-value-name="newB" commit="entity.setBoolean(newB)">
				<check-box />
			</column-edit>
		</column>
	</table>
</expresso-external-content>

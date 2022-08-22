<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base 0.1" uses:swing="Quick-Swing 0.1" with-extension="window,swing:quick" look-and-feel="system"
	title="`Super-Basic`">
	<head>
		<models>
			<model name="appModel">
				<constant name="const1" type="String">`SOME CONSTANT`</constant>
				<value name="value1" type="int">0</value> <!-- Mutable value with initial value -->
				<list name="list1" type="int">
					<element>0</element>
					<element>1</element>
					<element>2</element>
					<element>3</element>
					<element>4</element>
				</list>
			</model>
			<ext-model name="extModel"> <!-- Declaration of model which must be provided to the document -->
				<value name="value1" type="double" />
			</ext-model>
		</models>
	</head>
	<box layout="border">
		<box region="north" layout="inline" orientation="horizontal">
			<label>External Value 1:</label>
			<label value="extModel.value1" />
		</box>
		<table region="west" rows="appModel.list1" selection="appModel.value1" value-name="row" render-value-name="column">
			<column name="Value" />
			<column name="Value + 1" value="row+1" />
		</table>
		<box layout="inline" orientation="vertical" main-align="center" cross-align="center">
			<label>Selected</label>
			<label value="appModel.value1" />
		</box>
	</box>
</quick>

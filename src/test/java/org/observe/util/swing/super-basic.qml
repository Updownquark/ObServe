<?xml version="1.0" encoding="UTF-8"?>

<quick uses:swing="quick-swing.qtd" uses:base="quick-base.qtd" with-extension="swing:quick" look-and-feel="system">
	<head>
		<title>Super-Basic</title>
		<models>
			<model name="appModel">
				<constant name="const1" type="String">SOME CONSTANT</constant>
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
				<ext-value name="value1" type="double" />
			</ext-model>
		</models>
	</head>
	<box layout="border-layout">
		<box region="north" layout="inline-layout" orientation="horizontal">
			<label text="External Value 1:" />
			<label value="extModel.value1" />
		</box>
		<table region="west" rows="appModel.list1" selection="appModel.value1">
			<column name="Value" />
		</table>
		<box layout="inline-layout" orientation="vertical" main-align="center" cross-align="center">
			<label text="Selected" />
			<label value="appModel.value1" />
		</box>
	</box>
</quick>

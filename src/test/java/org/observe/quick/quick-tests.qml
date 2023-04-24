<?xml version="1.0" encoding="UTF-8"?>

<testing uses:test="Expresso-Testing 0.1" uses:quick="Quick-Testing 0.1" uses:debug="Expresso-Debug v0.1">
	<test:head>
		<imports>
			<import>org.junit.Assert.*</import>
		</imports>
		<models>
			<ext-model name="ext">
				<value name="actionName" type="String" />
			</ext-model>
			<model name="model">
				<value name="intValue" type="int" />
				<value name="stringValue" type="String" />
			</model>
		</models>
	</test:head>
	<test name="superBasic">
		<box name="ui" layout="inline" orientation="vertical" main-align="justify">
			<label name="label1" value="model.intValue" />
			<box layout="inline" orientation="horizontal">
				<label name="label2" value="model.stringValue" />
			</box>
		</box>

		<action name="setIntV_0">model.intValue=21</action>
		<action name="checkLabel1_0">assertEquals(model.intValue, (int) ui.label1.getValue())</action>
		<action name="setIntV_1">model.intValue=512</action>
		<action name="checkLabel1_1">assertEquals(model.intValue, (int) ui.label1.getValue())</action>

		<action name="setStringV_0">model.stringValue="Some string"</action>
		<action name="checkLabel2_0">assertEquals(model.stringValue, (String) ui.label2.getValue())</action>
		<action name="setStringV_1">model.stringValue="Some other string"</action>
		<action name="checkLabel2_1">assertEquals(model.stringValue, (String) ui.label2.getValue())</action>
	</test>
	<test name="withElementModels">
		<box name="ui" layout="inline" orientation="vertical" main-align="justify">
			<model>
				<value name="intTimes2">model.intValue*2</value>
			</model>
			<label name="label1" value="intTimes2" />
			<box layout="inline" orientation="horizontal">
				<model>
					<value name="compositeString">model.stringValue+" "+intTimes2</value>
				</model>
				<label name="label2" value="compositeString" />
			</box>
		</box>

		<action name="setIntV_0">model.intValue=80</action>
		<action name="checkLabel1_0">assertEquals(160, (int) ui.label1.getValue())</action>
		<action name="setIntV_1">model.intValue=76</action>
		<action name="checkLabel1_1">assertEquals(152, (int) ui.label1.getValue())</action>

		<action name="setStringV_0">model.stringValue="Some string"</action>
		<action name="checkLabel2_0">assertEquals("Some string 152", (String) ui.label2.getValue())</action>
		<action name="setIntV_2">model.intValue=15</action>
		<action name="setStringV_1">model.stringValue="Some other string"</action>
		<action name="checkLabel2_1">assertEquals("Some other string 30", (String) ui.label2.getValue())</action>
	</test>
</testing>

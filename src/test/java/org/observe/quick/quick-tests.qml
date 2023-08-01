<?xml version="1.0" encoding="UTF-8"?>

<testing uses:test="Expresso-Testing 0.1" uses:quick="Quick-Testing 0.1" uses:base="Quick-Base v0.1" uses:debug="Expresso-Debug v0.1">
	<expresso>
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
				<value name="intValue2" type="int" />
			</model>
		</models>
	</expresso>
	<test name="superBasic">
		<box name="ui" layout="inline-layout" orientation="vertical" main-align="justify">
			<label name="label1" value="model.intValue" />
			<box layout="inline-layout" orientation="horizontal">
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
		<box name="ui" layout="inline-layout" orientation="vertical" main-align="justify">
			<model>
				<value name="intTimes2">model.intValue*2</value>
			</model>
			<label name="label1" value="intTimes2" />
			<box layout="inline-layout" orientation="horizontal">
				<model>
					<value name="compositeString">model.stringValue+" "+intTimes2</value>
				</model>
				<label name="label2" value="compositeString">
					<model>
						<value name="textLength">value.length()</value>
						<hook name="assignLength" on="textLength">model.intValue2=textLength</hook>
					</model>
				</label>
			</box>
		</box>

		<action name="setIntV_0">model.intValue=80</action>
		<action name="checkLabel1_0">assertEquals(160, (int) ui.label1.getValue())</action>
		<action name="setIntV_1">model.intValue=76</action>
		<action name="checkLabel1_1">assertEquals(152, (int) ui.label1.getValue())</action>

		<action name="setStringV_0">model.stringValue="Some string"</action>
		<action name="checkLabel2_0">assertEquals("Some string 152", (String) ui.label2.getValue())</action>
		<action name="checkLabel2Len_0">assertEquals(15, model.intValue2)</action>
		<action name="setIntV_2">model.intValue=15</action>
		<action name="setStringV_1">model.stringValue="Some other string"</action>
		<action name="checkLabel2_1">assertEquals("Some other string 30", (String) ui.label2.getValue())</action>
		<action name="checkLabel2Len_1">assertEquals(20, model.intValue2)</action>
	</test>
</testing>

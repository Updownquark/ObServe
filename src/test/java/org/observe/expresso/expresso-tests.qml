<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="../../../org/observe/quick/quick-base.qtd">
	<head>
		<imports>
			<import>org.observe.expresso.ExpressoTestEntity</import>
		</imports>
		<models>
			<model name="model1">
				<value name="test" init="new ExpressoTestEntity()" />
				<value name="expected" init="new ExpressoTestEntity()" />
	
				<value name="testInt">test.getInt()</value>
				<value name="testDbl">test.getDouble()</value>
				<value name="testBool">test.getBoolean()</value>
				<value name="testStr">test.getString()</value>
				<value name="entityInst">test.getInstant()</value>
	
				<value name="anyInt" init="10" />
				<value name="anyDbl" init="1000.0" />
				<value name="anyBool" init="true" />
				<value name="anyStr" init="&quot;Something&quot;" />
				<value type="java.time.Instant" name="anyInst" init="`05May2022 10:15:43am`" />
	
				<action name="assignInt">test.setInt(anyInt)</action>
				<action name="assignDbl">test.setDouble(anyDbl)</action>
				<action name="assignBool">test.setBoolean(anyBool)</action>
				<action name="assignStr">test.setString(anyStr)</action>
				<action name="assignInst">test.setInstant(anyInst)</action>
	
				<value name="error">test.assertEquals(expected)</value>
			</model>
			<model name="model2">
				<action name="assignInst">model1.anyInst=`01Jan2022 12:00pm`</action>
			</model>
		</models>
	</head>
	<box layout="inline" orientation="vertical" />
</quick>

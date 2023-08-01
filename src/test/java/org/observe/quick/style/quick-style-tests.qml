<?xml version="1.0" encoding="UTF-8"?>

<testing uses:style="Quick-Style-Test 0.1">
	<expresso>
		<imports>
			<import>org.junit.Assert.*</import>
			<import>org.observe.quick.style.QuickStyleTests.*</import>
		</imports>
		<models>
			<ext-model name="ext">
				<value name="actionName" type="String" />
			</ext-model>
			<model name="models">
				<value name="m0" type="boolean" />
				<value name="m1" type="int" />
				<value name="m2" type="boolean" />
				<value name="m3" type="int" />
				<value name="m4" type="boolean "/>
			</model>
		</models>
	</expresso>
	<test name="basicStyleNoWatch">
		<model>
			<!-- Basic styles that depend on model values with various conditions with varying priority and complexity -->
			<a name="a0" a="models.m0" b="models.m2" c="models.m1" d="models.m4">
				<style attr="s0">a</style>
				<style attr="s1">c*10</style>
				<style attr="s1" condition="a">models.m1</style>
				<style attr="s1" condition="b">models.m3</style>
				<style attr="s1" condition="a &amp;&amp; d">models.m1 + models.m3</style>
			</a>
		</model>
		<action name="assignA_1">models.m0=true</action>
		<action name="checkS0_1">assertEquals(ext.actionName, models.m0, a0.s0)</action>

		<!-- Default s1 condition, c=models.m1=0, so s1 should be 0 -->
		<action name="checkS1_1">assertEquals(ext.actionName, 0, a0.s1)</action>

		<!-- Change the value of the active -->
		<action name="assignM1_1">models.m1=25</action>
		<action name="checkS1_1">assertEquals(ext.actionName, 25, a0.s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_2">models.m0=false</action>
		<action name="checkS1_1">assertEquals(ext.actionName, 250, a0.s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM3">models.m3=50</action>
		<action name="checkS1_2">assertEquals(ext.actionName, 250, a0.s1)</action>

		<!-- Change to b condition -->
		<action name="assignB_1">models.m2=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>

		<!-- b=models.m2 has higher priority than a=models.m0, so making 'a' true won't affect s1 -->
		<action name="assignA_3">models.m0=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>

		<!-- a && d has higher priority than b though -->
		<action name="assignD_1">models.m4=true</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 75, a0.s1)</action>
		<!-- Back to b condition -->
		<action name="assignD_2">models.m4=false</action>
		<action name="checkS1_3">assertEquals(ext.actionName, 50, a0.s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM1_2">models.m1=300</action>
		<action name="checkS1_4">assertEquals(ext.actionName, 50, a0.s1)</action>

		<!-- Back to a condition -->
		<action name="assignB_2">models.m2=false</action>
		<action name="checkS1_4">assertEquals(ext.actionName, 300, a0.s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_4">models.m0=false</action>
		<action name="checkS1_5">assertEquals(ext.actionName, 3000, a0.s1)</action>

		<!-- No change, needs both a and d -->
		<action name="assignD_3">models.m4=true</action>
		<action name="checkS1_5">assertEquals(ext.actionName, 3000, a0.s1)</action>
	</test>
	<test name="basicStyleWithWatch">
		<!-- Basic styles that depend on model values with various conditions with varying priority and complexity -->
		<model>
			<a name="a0" a="models.m0" b="models.m2" c="models.m1" d="models.m4">
				<style attr="s0">a</style>
				<style attr="s1">c*10</style>
				<style attr="s1" condition="a">models.m1</style>
				<style attr="s1" condition="b">models.m3</style>
				<style attr="s1" condition="a &amp;&amp; d">models.m1 + models.m3</style>
			</a>
			<watch name="w_a0_s0">a0.s0</watch>
			<watch name="w_a0_s1">a0.s1</watch>
		</model>
		<action name="assignA_1">models.m0=true</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, models.m0, w_a0_s0)</action>

		<!-- Default s1 condition, c=models.m1=0, so s1 should be 0 -->
		<action name="checkWS0_1">assertEquals(ext.actionName, 0, w_a0_s1)</action>

		<!-- Change the value of the active -->
		<action name="assignM1_1">models.m1=25</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 25, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_2">models.m0=false</action>
		<action name="checkWS0_1">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM3">models.m3=50</action>
		<action name="checkWS0_2">assertEquals(ext.actionName, 250, w_a0_s1)</action>

		<!-- Change to b condition -->
		<action name="assignB_1">models.m2=true</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- b=models.m2 has higher priority than a=models.m0, so making 'a' true won't affect s1 -->
		<action name="assignA_3">models.m0=true</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- a && d has higher priority than b though -->
		<action name="assignD_1">models.m4=true</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 75, w_a0_s1)</action>
		<!-- Back to b condition -->
		<action name="assignD_2">models.m4=false</action>
		<action name="checkWS0_3">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action name="assignM1_2">models.m1=300</action>
		<action name="checkWS0_4">assertEquals(ext.actionName, 50, w_a0_s1)</action>

		<!-- Back to a condition -->
		<action name="assignB_2">models.m2=false</action>
		<action name="checkWS0_4">assertEquals(ext.actionName, 300, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action name="assignA_4">models.m0=false</action>
		<action name="checkWS0_5">assertEquals(ext.actionName, 3000, w_a0_s1)</action>

		<!-- No change, needs both a and d -->
		<action name="assignD_3">models.m4=true</action>
		<action name="checkWS0_5">assertEquals(ext.actionName, 3000, w_a0_s1)</action>
	</test>
	<test name="localStyleSheet">
		<model>
			<!-- Test styles that are prescribed by a style sheet defined in this document -->
			<a name="a0" a="localA" b="localB" c="localC" d="localD">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
				</model>
			</a>
			<b name="b0" e="localE" f="localF">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</b>
			<c name="c0" e="localE" f="localF" g="localG">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localG" type="boolean" init="false" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</c>
			<d name="d0" e="localE" f="localF" h="localH">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localH" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</d>
		</model>
		<style-sheet>
			<style element="a">
				<style attr="s0">!a</style>
				<style attr="s1">c</style>
				<style condition="b">
					<style attr="s0">d</style>
					<style attr="s1">c*100</style>
					<style condition="d">
						<style attr="s1">c*1_000_000</style>
					</style>
				</style>
			</style>
			<style element="b">
				<style attr="s3" condition="e">models.m3</style>
				<style attr="s4" condition="!e">models.m1</style>
				<style child="a">
					<style condition="e"> <!-- A child style dependent on an element model value from the parent -->
						<style attr="s1">f</style>
					</style>
				</style>
			</style>
		</style-sheet>
		
		<action name="initM1">models.m1=497683</action>
		<action name="initM3">models.m3=-42</action>
		
		<!-- a, d, and e are false -->
		<action name="init1">assertEquals(ext.actionName, true, a0.s0)</action>
		<action name="init2">assertEquals(ext.actionName, 0, a0.s1)</action>
		<action name="init3">assertEquals(ext.actionName, null, b0.s3)</action>
		<action name="init4">assertEquals(ext.actionName, models.m1, b0.s4)</action>

		<action name="assignB_1">a0.b=true</action>
		<action name="assignC_1">a0.c=250</action>
		<action name="checkA0S0_1">assertEquals(ext.actionName, false, a0.s0)</action>
		<action name="checkA0S1_1">assertEquals(ext.actionName, 25_000, a0.s1)</action>

		<action name="assignB0E_1">b0.e=true</action>
		<action name="checkB0S3_1">assertEquals(ext.actionName, models.m3, b0.s3)</action>
		<action name="checkB0S4_1">assertEquals(ext.actionName, null, b0.s4)</action>

		<action name="assignB0F_0">b0.f=18</action>
		<action name="assignB0ChC_0">b0.a.get(0).c=179</action>
		<action name="checkBChild_0">assertEquals(ext.actionName, 18, b0.a.get(0).s1)</action>
		<action name="assignB0E_1">b0.e=false</action>
		<action name="checkBChild_0">assertEquals(ext.actionName, 179, b0.a.get(0).s1)</action>
		<!-- TODO Include local styles also-->
	</test>
	<test name="importedStyleSheet">
		<!-- TODO Test styles that are prescribed by a style sheet imported from outside this document -->
		<model>
		</model>
		<action name="blank">models.m0=true</action>
	</test>
	<test name="inheritedStyle">
		<!-- TODO Test inheritance of styles from parent elements -->
		<model>
		</model>
		<action name="blank">models.m0=true</action>
	</test>
	<test name="withStyleSets">
		<model>
			<!-- a1 has a copy of all the same styles as are contained in the style set -->
			<a name="a1" a="models.m0" b="true" c="0" d="models.m2">
				<style condition="a" attr="s1">217
					<style condition="d">856</style>
				</style>
			</a>
			<!-- a2 uses the style set.  Its style is the same as a1 when the style set applies to it, i.e. when a is true -->
			<a name="a2" a="models.m0" b="true" c="0" d="models.m2">
				<style attr="s0">false</style>
				<style condition="a" style-set="testStyle" />
			</a>
		</model>
		<style-sheet>
			<style-set name="testStyle">
				<style element="a" attr="s1">217
					<style condition="d">856</style>
				</style>
				<style element="a" attr="s0">true</style> <!-- Just to test multiple styles in a style set -->
			</style-set>
		</style-sheet>

		<action name="init10">assertEquals(0, a1.s1)</action>
		<action name="applyStyleSet10">models.m0=true</action>
		<action name="test11">assertEquals(217, a1.s1)</action>
		<action name="applyCondition10">models.m2=true</action>
		<action name="test12">assertEquals(856, a1.s1)</action>
		<action name="applyStyleSet11">models.m0=false</action>
		<action name="test13">assertEquals(0, a1.s1)</action>

		<action name="reset0">models.m2=false</action>

		<action name="init20">assertEquals(0, a2.s1)</action>
		<action name="applyStyleSet20">models.m0=true</action>
		<action name="test21">assertEquals(217, a2.s1)</action>
		<action name="applyCondition20">models.m2=true</action>
		<action name="test22">assertEquals(856, a2.s1)</action>
		<action name="applyStyleSet21">models.m0=false</action>
		<action name="test23">assertEquals(0, a2.s1)</action>

		<action name="init30">assertEquals(false, a2.s0)</action>
		<action name="applyStyleSet30">models.m0=true</action>
		<action name="init30">assertEquals(true, a2.s0)</action>
	</test>
</testing>

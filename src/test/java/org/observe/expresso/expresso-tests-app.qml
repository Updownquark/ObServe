<?xml version="1.0" encoding="UTF-8"?>

<qonfig-app uses:app="Qonfig-App v0.1" app-file="expresso-tests.qml">
	<toolkit def="/org/observe/expresso/qonfig/expresso-core.qtd">
		<value-type>org.observe.expresso.qonfig.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/expresso/qonfig/expresso-base.qtd" />
	<toolkit def="/org/observe/expresso/qonfig/expresso-debug.qtd" />
	<toolkit def="/org/observe/expresso/expresso-test-framework.qtd" />
	<toolkit def="/org/observe/expresso/expresso-test.qtd" />
	<special-session>org.observe.expresso.qonfig.ExpressoSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.qonfig.ExpressoBaseV0_1</interpretation>
	<interpretation>org.observe.expresso.qonfig.ExpressoDebugV0_1</interpretation>
	<interpretation>org.observe.expresso.ExpressoTestFrameworkInterpretation</interpretation>
	<interpretation>org.observe.expresso.TestInterpretation</interpretation>
</qonfig-app>

// "Create enum 'A'" "true"
// ERROR: Unresolved reference: B
package p

fun foo() = <caret>A.B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
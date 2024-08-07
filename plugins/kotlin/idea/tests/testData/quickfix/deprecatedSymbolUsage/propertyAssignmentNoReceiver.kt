// "Replace with 'new'" "true"
// WITH_STDLIB

class A {
    @Deprecated("msg", ReplaceWith("new"))
    var old
        get() = new
        set(value) {
            new = value
        }

    var new = ""
}

fun foo() {
    val a = A()
    a.apply {
        <caret>old = "foo"
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
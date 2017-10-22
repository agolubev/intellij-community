// PROBLEM: Variable 'foo' is assigned to itself
// WITH_RUNTIME
// FIX: Remove self assignment

class Test {
    var foo = 1

    fun test() {
        with (Test()) {
            this.foo = <caret>foo
        }
    }
}
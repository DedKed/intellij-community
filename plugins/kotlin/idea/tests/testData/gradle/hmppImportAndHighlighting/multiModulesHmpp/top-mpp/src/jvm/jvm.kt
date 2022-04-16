actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>MyCancelException<!> = platform.lib.MyCancellationException

actual open class <!LINE_MARKER("descr='Has expects in common module'")!>OtherException<!> : platform.lib.MyIllegalStateException()

fun test() {
    cancel(MyCancelException()) // TYPE_MISMATCH

    other(OtherException())
}

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KCallable
import <!PLATFORM_CLASS_MAPPED_TO_KOTLIN!>java.lang.Cloneable<!>

fun foo(x: KAnnotatedElement): Boolean = true

class Foo {
    fun bar(a: Int, b: Int): KCallable<*> { TODO() }
}

fun jvmFun() {
}

// TEST builtinsAndStdlib
fun getKCallable(): KCallable<*> = ::jvmFun

fun <!LINE_MARKER!>main<!>() {
    val ref = ::jvmFun
    val typedRef: KCallable<*> = getKCallable()
    ref.call()
    typedRef.call()
    foo(Foo::bar)
}
//Test commonSealedWithPlatformInheritor
class PlatfromDerived : <!SEALED_INHERITOR_IN_DIFFERENT_MODULE!>Base<!>() // must be an error

fun test_2(b: Base) = when (b) {
    is Derived -> 1
}

//Test constructorsOfExpect
actual class <!LINE_MARKER("descr='Has expects in common module'")!>A<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonMember<!>() { }

    fun platformMember() { }
}

fun test() {
    A().commonMember()
    A().platformMember()
}

//Test correctOverloadResiltionAmbiguity
actual interface <!LINE_MARKER("descr='Has expects in common module'")!>A<!><T> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>foo<!>(x: T)
    fun foo(x: String)
}

fun main() {
    bar().<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>("")
}

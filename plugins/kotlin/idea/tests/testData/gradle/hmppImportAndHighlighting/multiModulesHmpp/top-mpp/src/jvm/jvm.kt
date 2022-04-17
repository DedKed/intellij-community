// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
actual class <!LINE_MARKER("descr='Has expects in common module'")!>constructorsOfExpect<!> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>commonMember<!>() { }

    fun platformMember() { }
}

fun test() {
    constructorsOfExpect().commonMember()
    constructorsOfExpect().platformMember()
}

//Test correctOverloadResiltionAmbiguity
actual interface <!LINE_MARKER("descr='Has expects in common module'")!>correctOverloadResiltionAmbiguity<!><T> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>foo<!>(x: T)
    fun foo(x: String)
}

fun main() {
    bar().<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>("")
}


//Test recursiveTypes
actual interface <!LINE_MARKER("descr='Has expects in common module'")!>recursiveTypes<!><T : recursiveTypes<T>> {
    actual fun <!LINE_MARKER("descr='Has expects in common module'")!>foo<!>(): T
    fun bar() : T
}

fun test_1(a: recursiveTypes<*>) {
    a.foo()
    a.bar()
    a.foo().foo()
    a.bar().bar()
    a.foo().bar()
    a.bar().foo()
}

fun test_2(b: B) {
    b.foo()
    b.bar()
    b.foo().foo()
    b.bar().bar()
    b.foo().bar()
    b.bar().foo()
}
//Test overrideExpect
actual typealias <!LINE_MARKER("descr='Has expects in common module'")!>Expect<!> = String

interface Derived : Base {
    override fun <!LINE_MARKER("descr='Overrides function in 'Base''")!>expectInReturnType<!>(): Expect

    override fun <!LINE_MARKER("descr='Overrides function in 'Base''")!>expectInArgument<!>(e: Expect)

    override fun Expect.<!LINE_MARKER("descr='Overrides function in 'Base''")!>expectInReceiver<!>()

    override val <!LINE_MARKER("descr='Overrides property in 'Base''")!>expectVal<!>: Expect

    override var <!LINE_MARKER("descr='Overrides property in 'Base''")!>expectVar<!>: Expect
}

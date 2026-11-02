package com.example.emulator

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class LibretroNativeContractTest {
    @Test
    fun videoFrameToggleIsDeclaredAsNativeJniMethod() {
        val method = LibretroNative::class.java.getDeclaredMethod(
            "nativeSetVideoFrameEnabled",
            Boolean::class.javaPrimitiveType
        )

        assertTrue("JNI video toggle must be a native method", Modifier.isNative(method.modifiers))
    }
}

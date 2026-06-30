package org.javacs.kt

import org.javacs.kt.classpath.ClassPathEntry
import org.javacs.kt.classpath.ClassPathResolver
import org.javacs.kt.classpath.or
import org.javacs.kt.classpath.plus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassPathProvidesSourcesTest {
    private fun resolver(name: String, sources: Boolean) = object : ClassPathResolver {
        override val resolverType = name
        override val classpath = emptySet<ClassPathEntry>()
        override val providesSources = sources
    }

    @Test fun `compiled-only resolver does not provide sources by default`() {
        val gradleLike = object : ClassPathResolver {
            override val resolverType = "Gradle"
            override val classpath = emptySet<ClassPathEntry>()
        }
        assertFalse(gradleLike.providesSources)
    }

    @Test fun `union provides sources when either side does`() {
        assertFalse((resolver("a", false) + resolver("b", false)).providesSources)
        assertTrue((resolver("a", false) + resolver("b", true)).providesSources)
        assertTrue((resolver("a", true) + resolver("b", false)).providesSources)
    }

    @Test fun `first non empty provides sources when either side does`() {
        assertFalse((resolver("a", false) or resolver("b", false)).providesSources)
        assertTrue((resolver("a", false) or resolver("b", true)).providesSources)
        assertTrue((resolver("a", true) or resolver("b", false)).providesSources)
    }
}

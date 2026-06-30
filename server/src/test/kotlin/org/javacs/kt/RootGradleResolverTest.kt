package org.javacs.kt

import org.javacs.kt.classpath.defaultClassPathResolver
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RootGradleResolverTest {
    private fun touch(dir: Path, name: String) {
        Files.createDirectories(dir)
        Files.write(dir.resolve(name), byteArrayOf())
    }

    private fun gradleResolverCount(root: Path): Int =
        Regex("Gradle").findAll(defaultClassPathResolver(listOf(root)).resolverType).count()

    @Test fun `rooted multi-project build uses a single gradle resolver`() {
        val root = Files.createTempDirectory("rooted")
        touch(root, "settings.gradle.kts")
        touch(root, "build.gradle.kts")
        touch(root.resolve("app"), "build.gradle.kts")
        touch(root.resolve("lib"), "build.gradle.kts")

        assertEquals(1, gradleResolverCount(root))
    }

    @Test fun `independent builds without root settings keep per-module resolvers`() {
        val root = Files.createTempDirectory("independent")
        touch(root.resolve("app"), "build.gradle")
        touch(root.resolve("lib"), "build.gradle")

        assertEquals(2, gradleResolverCount(root))
    }

    @Test fun `nested settings disables root collapsing`() {
        val root = Files.createTempDirectory("nested")
        touch(root, "settings.gradle.kts")
        touch(root, "build.gradle.kts")
        touch(root.resolve("nested"), "settings.gradle.kts")
        touch(root.resolve("nested"), "build.gradle.kts")

        assertEquals(2, gradleResolverCount(root))
    }
}

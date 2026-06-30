package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.FileSystems

fun defaultClassPathResolver(workspaceRoots: Collection<Path>, db: Database? = null): ClassPathResolver {
    val childResolver = WithStdlibResolver(
        ShellClassPathResolver.global(workspaceRoots.firstOrNull())
            .or(workspaceRoots.asSequence().flatMap { workspaceResolvers(it) }.joined)
    ).or(BackupClassPathResolver)

    return db?.let { CachedClassPathResolver(childResolver, it) } ?: childResolver
}

/** Searches the workspace for all files that could provide classpath info. */
private fun workspaceResolvers(workspaceRoot: Path): Sequence<ClassPathResolver> {
    val ignored: List<PathMatcher> = ignoredPathPatterns(workspaceRoot, workspaceRoot.resolve(".gitignore"))
    return folderResolvers(workspaceRoot, ignored).asSequence()
}

/** Searches the folder for all build-files. */
private fun folderResolvers(root: Path, ignored: List<PathMatcher>): Collection<ClassPathResolver> {
    val paths = root.toFile()
        .walk()
        .onEnter { file -> ignored.none { it.matches(file.toPath()) } }
        .map { it.toPath() }
        .toList()

    val rootGradle = rootGradleResolver(root, paths)
    val resolvers = mutableListOf<ClassPathResolver>()
    rootGradle?.let { resolvers.add(it) }
    for (path in paths) {
        if (rootGradle != null && isGradleBuildFile(path)) continue
        asClassPathProvider(path)?.let { resolvers.add(it) }
    }
    return resolvers
}

/**
 * For a single rooted Gradle build, one invocation of `kotlinLSPProjectDeps` at the root resolves
 * every subproject's classpath in one Gradle session, because the init script registers the task on
 * `allprojects`. Detecting that lets us avoid spawning one Gradle CLI per module. Only activates when
 * a settings file sits at the workspace root and no nested settings file defines an independent
 * build, so multi-build workspaces keep their per-module resolvers.
 */
private fun rootGradleResolver(root: Path, paths: List<Path>): ClassPathResolver? {
    val rootBuildFile = paths.firstOrNull { it.parent == root && isGradleBuildFile(it) } ?: return null
    val settingsFiles = paths.filter { isGradleSettingsFile(it) }
    val rootHasSettings = settingsFiles.any { it.parent == root }
    val nestedSettings = settingsFiles.any { it.parent != root }
    if (!rootHasSettings || nestedSettings) return null

    return GradleClassPathResolver(
        rootBuildFile,
        includeKotlinDSL = rootBuildFile.toString().endsWith(".kts"),
        versionFiles = paths.filter { isGradleBuildFile(it) },
    )
}

private fun isGradleBuildFile(path: Path): Boolean =
    path.fileName?.toString().let { it == "build.gradle" || it == "build.gradle.kts" }

private fun isGradleSettingsFile(path: Path): Boolean =
    path.fileName?.toString().let { it == "settings.gradle" || it == "settings.gradle.kts" }

/** Tries to read glob patterns from a gitignore. */
private fun ignoredPathPatterns(root: Path, gitignore: Path): List<PathMatcher> =
    gitignore.toFile()
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.map { it.removeSuffix("/") }
        ?.let { it + listOf(
            // Patterns that are ignored by default
            ".git"
        ) }
        ?.mapNotNull { try {
            LOG.debug("Adding ignore pattern '{}' from {}", it, gitignore)
            FileSystems.getDefault().getPathMatcher("glob:$root**/$it")
        } catch (e: Exception) {
            LOG.warn("Did not recognize gitignore pattern: '{}' ({})", it, e.message)
            null
        } }
        ?: emptyList()

/** Tries to create a classpath resolver from a file using as many sources as possible */
private fun asClassPathProvider(path: Path): ClassPathResolver? =
    MavenClassPathResolver.maybeCreate(path)
        ?: GradleClassPathResolver.maybeCreate(path)
        ?: ShellClassPathResolver.maybeCreate(path)

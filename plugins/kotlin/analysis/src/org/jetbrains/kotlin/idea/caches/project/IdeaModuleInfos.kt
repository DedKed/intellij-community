// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.LibraryScopeBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import com.intellij.util.SmartList
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle
import org.jetbrains.kotlin.idea.caches.resolve.util.enlargedSearchScope
import org.jetbrains.kotlin.idea.caches.trackers.KotlinModuleOutOfCodeBlockModificationTracker
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.framework.effectiveKind
import org.jetbrains.kotlin.idea.framework.platform
import org.jetbrains.kotlin.idea.klib.AbstractKlibLibraryInfo
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.project.findAnalyzerServices
import org.jetbrains.kotlin.idea.project.getStableName
import org.jetbrains.kotlin.idea.project.libraryToSourceAnalysisEnabled
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.util.rootManager
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.compat.toOldPlatform
import org.jetbrains.kotlin.platform.js.isJs
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.TopPackageNamesProvider
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.concurrent.ConcurrentHashMap

internal val LOG = Logger.getInstance(IdeaModuleInfo::class.java)

@Suppress("DEPRECATION_ERROR")
interface IdeaModuleInfo : org.jetbrains.kotlin.idea.caches.resolve.IdeaModuleInfo {
    fun contentScope(): GlobalSearchScope

    val moduleOrigin: ModuleOrigin

    val project: Project?

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = super.capabilities + mapOf(OriginCapability to moduleOrigin)

    override fun dependencies(): List<IdeaModuleInfo>
}

private val Project.libraryInfoCache: MutableMap<Library, List<LibraryInfo>>
    get() = cacheInvalidatingOnRootModifications { ConcurrentHashMap<Library, List<LibraryInfo>>() }

fun createLibraryInfo(project: Project, library: Library): List<LibraryInfo> =
    project.libraryInfoCache.getOrPut(library) {
        val approximatePlatform = if (library is LibraryEx && !library.isDisposed) {
            // for Native returns 'unspecifiedNativePlatform', thus "approximate"
            library.effectiveKind(project).platform
        } else {
            DefaultIdeTargetPlatformKindProvider.defaultPlatform
        }

        approximatePlatform.idePlatformKind.resolution.createLibraryInfo(project, library)
    }

internal fun TargetPlatform.canDependOn(other: IdeaModuleInfo, isHmppEnabled: Boolean): Boolean {
    if (isHmppEnabled) {
        // HACK: allow depending on stdlib even if platforms do not match
        if (isNative() && other is AbstractKlibLibraryInfo && other.libraryRoot.endsWith(KONAN_STDLIB_NAME)) return true

        val platformsWhichAreNotContainedInOther = this.componentPlatforms - other.platform.componentPlatforms
        if (platformsWhichAreNotContainedInOther.isEmpty()) return true

        // unspecifiedNativePlatform is effectively a wildcard for NativePlatform
        return platformsWhichAreNotContainedInOther.all { it is NativePlatform } &&
                NativePlatforms.unspecifiedNativePlatform.componentPlatforms.single() in other.platform.componentPlatforms
    } else {
        return this.isJvm() && other.platform.isJvm() ||
                this.isJs() && other.platform.isJs() ||
                this.isNative() && other.platform.isNative() ||
                this.isCommon() && other.platform.isCommon()
    }
}

interface ModuleSourceInfo : IdeaModuleInfo, TrackableModuleInfo, ModuleSourceInfoBase {
    val module: Module

    override val expectedBy: List<ModuleSourceInfo>

    override val displayedName get() = module.name

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.MODULE

    override val project: Project
        get() = module.project

    override val platform: TargetPlatform
        get() = TargetPlatformDetector.getPlatform(module)

    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        message = "This accessor is deprecated and will be removed soon, use API from 'org.jetbrains.kotlin.platform.*' packages instead",
        replaceWith = ReplaceWith("platform"),
        level = DeprecationLevel.ERROR
    )
    fun getPlatform(): org.jetbrains.kotlin.resolve.TargetPlatform = platform.toOldPlatform()

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(module.project)

    override fun createModificationTracker(): ModificationTracker =
        KotlinModuleOutOfCodeBlockModificationTracker(module)
}

sealed class ModuleSourceInfoWithExpectedBy(private val forProduction: Boolean) : ModuleSourceInfo {
    override val expectedBy: List<ModuleSourceInfo>
        get() {
            val expectedByModules = module.implementedModules
            return expectedByModules.mapNotNull { if (forProduction) it.productionSourceInfo() else it.testSourceInfo() }
        }

    override fun dependencies(): List<IdeaModuleInfo> = module.cacheByClassInvalidatingOnRootModifications(this::class.java) {
        module.getSourceModuleDependencies(forProduction, platform)
    }

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> {
        return module.cacheByClassInvalidatingOnRootModifications(KeyForModulesWhoseInternalsAreVisible::class.java) {
            module.additionalVisibleModules.mapNotNull { if (forProduction) it.productionSourceInfo() else it.testSourceInfo() }
        }
    }

    private object KeyForModulesWhoseInternalsAreVisible

}

data class ModuleProductionSourceInfo internal constructor(
    override val module: Module
) : ModuleSourceInfoWithExpectedBy(forProduction = true) {

    override val name = Name.special("<production sources for module ${module.name}>")

    override val stableName: Name by lazy { module.getStableName() }

    override fun contentScope(): GlobalSearchScope {
        return enlargedSearchScope(module.moduleProductionSourceScope, module, isTestScope = false)
    }
}

//TODO: (module refactoring) do not create ModuleTestSourceInfo when there are no test roots for module
@Suppress("DEPRECATION_ERROR")
data class ModuleTestSourceInfo internal constructor(override val module: Module) :
    ModuleSourceInfoWithExpectedBy(forProduction = false), org.jetbrains.kotlin.idea.caches.resolve.ModuleTestSourceInfo {

    override val name = Name.special("<test sources for module ${module.name}>")

    override val displayedName get() = KotlinIdeaAnalysisBundle.message("module.name.0.test", module.name)

    override val stableName: Name by lazy { module.getStableName() }

    override fun contentScope(): GlobalSearchScope = enlargedSearchScope(module.moduleTestSourceScope, module, isTestScope = true)

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> =
        module.cacheByClassInvalidatingOnRootModifications(KeyForModulesWhoseInternalsAreVisible::class.java) {
            val list = SmartList<ModuleInfo>()

            list.addIfNotNull(module.productionSourceInfo())

            TestModuleProperties.getInstance(module).productionModule?.let {
                list.addIfNotNull(it.productionSourceInfo())
            }

            list.addAll(list.closure { it.expectedBy })

            list.toHashSet()
        }

    private object KeyForModulesWhoseInternalsAreVisible
}

fun Module.productionSourceInfo(): ModuleProductionSourceInfo? = if (hasProductionRoots()) ModuleProductionSourceInfo(this) else null

fun Module.testSourceInfo(): ModuleTestSourceInfo? = if (hasTestRoots()) ModuleTestSourceInfo(this) else null

internal fun Module.correspondingModuleInfos(): List<ModuleSourceInfo> = listOfNotNull(testSourceInfo(), productionSourceInfo())

private fun Module.hasProductionRoots() =
    hasRootsOfType(JavaSourceRootType.SOURCE) || hasRootsOfType(SourceKotlinRootType) || (isNewMPPModule && sourceType == SourceType.PRODUCTION)

private fun Module.hasTestRoots() =
    hasRootsOfType(JavaSourceRootType.TEST_SOURCE) || hasRootsOfType(TestSourceKotlinRootType) || (isNewMPPModule && sourceType == SourceType.TEST)

private fun Module.hasRootsOfType(sourceRootType: JpsModuleSourceRootType<*>): Boolean =
    rootManager.contentEntries.any { it.getSourceFolders(sourceRootType).isNotEmpty() }

abstract class LibraryInfo(override val project: Project, val library: Library) :
    IdeaModuleInfo, LibraryModuleInfo, BinaryModuleInfo, TrackableModuleInfo {

    private val libraryWrapper = library.wrap()

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<library ${library.name}>")

    override val displayedName: String
        get() = KotlinIdeaAnalysisBundle.message("library.0", library.name.toString())

    override fun contentScope(): GlobalSearchScope = LibraryWithoutSourceScope(project, library)

    override fun dependencies(): List<IdeaModuleInfo> {
        val (libraries, sdks) = LibraryDependenciesCache.getInstance(project).getLibrariesAndSdksUsedWith(this)

        val result = LinkedHashSet<IdeaModuleInfo>(1 + sdks.size + libraries.size)
        result.add(this)

        result.addAll(sdks)

        result.addAll(libraries)

        return result.toList()
    }

    abstract override val platform: TargetPlatform // must override

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(project)

    private val _sourcesModuleInfo: SourceForBinaryModuleInfo by lazy { LibrarySourceInfo(project, library, this) }

    override val sourcesModuleInfo: SourceForBinaryModuleInfo
        get() = _sourcesModuleInfo

    override fun getLibraryRoots(): Collection<String> =
        library.getFiles(OrderRootType.CLASSES).mapNotNull(PathUtil::getLocalPath)

    override fun createModificationTracker(): ModificationTracker {
        if (!project.libraryToSourceAnalysisEnabled)
            return ModificationTracker.NEVER_CHANGED

        return ResolutionAnchorAwareLibraryModificationTracker(this)
    }

    override fun toString() = "${this::class.simpleName}(libraryName=${library.name}, libraryRoots=${getLibraryRoots()})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryInfo) return false

        return libraryWrapper == other.libraryWrapper
    }

    override fun hashCode() = libraryWrapper.hashCode()
}

data class LibrarySourceInfo(override val project: Project, val library: Library, override val binariesModuleInfo: BinaryModuleInfo) :
    IdeaModuleInfo, SourceForBinaryModuleInfo, LibraryModuleSourceInfoBase {

    override val name: Name = Name.special("<sources for library ${library.name}>")

    override val displayedName: String
        get() = KotlinIdeaAnalysisBundle.message("sources.for.library.0", library.name.toString())

    override fun sourceScope(): GlobalSearchScope = KotlinSourceFilterScope.librarySources(
        LibrarySourceScope(
            project,
            library
        ), project
    )

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> {
        return createLibraryInfo(project, library)
    }

    override val platform: TargetPlatform
        get() = binariesModuleInfo.platform

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = binariesModuleInfo.analyzerServices

    override fun toString() = "LibrarySourceInfo(libraryName=${library.name})"
}

//TODO: (module refactoring) there should be separate SdkSourceInfo but there are no kotlin source in existing sdks for now :)
data class SdkInfo(override val project: Project, val sdk: Sdk) : IdeaModuleInfo, SdkInfoBase {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.LIBRARY

    override val name: Name = Name.special("<sdk ${sdk.name}>")

    override val displayedName: String
        get() = KotlinIdeaAnalysisBundle.message("sdk.0", sdk.name)

    override fun contentScope(): GlobalSearchScope = SdkScope(project, sdk)

    override fun dependencies(): List<IdeaModuleInfo> = listOf(this)

    override val platform: TargetPlatform
        get() =
            if (sdk.sdkType is KotlinSdkType) CommonPlatforms.defaultCommonPlatform
            else JvmPlatforms.unspecifiedJvmPlatform // TODO(dsavvinov): provide proper target version

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices

    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = when (this.sdk.sdkType) {
            is JavaSdk -> super<IdeaModuleInfo>.capabilities + mapOf(JDK_CAPABILITY to true)
            else -> super<IdeaModuleInfo>.capabilities
        }
}

object NotUnderContentRootModuleInfo : IdeaModuleInfo, NonSourceModuleInfoBase {
    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER

    override val name: Name = Name.special("<special module for files not under source root>")

    override val displayedName: String
        get() = KotlinIdeaAnalysisBundle.message("special.module.for.files.not.under.source.root")

    override val project: Project?
        get() = null

    override fun contentScope(): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE

    //TODO: (module refactoring) dependency on runtime can be of use here
    override fun dependencies(): List<IdeaModuleInfo> = listOf(this)

    override val platform: TargetPlatform
        get() = DefaultIdeTargetPlatformKindProvider.defaultPlatform

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.single().findAnalyzerServices()
}

internal open class PoweredLibraryScopeBase(project: Project, classes: Array<VirtualFile>, sources: Array<VirtualFile>) :
    LibraryScopeBase(project, classes, sources), TopPackageNamesProvider {

    private val entriesVirtualFileSystems: Set<NewVirtualFileSystem>? = run {
        val fileSystems = mutableSetOf<NewVirtualFileSystem>()
        for (file in classes + sources) {
            val newVirtualFile = file as? NewVirtualFile ?: return@run null
            fileSystems.add(newVirtualFile.fileSystem)
        }
        fileSystems
    }

    override val topPackageNames: Set<String> by lazy {
        (classes + sources)
            .flatMap { it.children.toList() }
            .filter(VirtualFile::isDirectory)
            .map(VirtualFile::getName)
            .toSet() + "" // empty package is always present
    }

    override fun contains(file: VirtualFile): Boolean {
        ((file as? NewVirtualFile)?.fileSystem)?.let {
            if (entriesVirtualFileSystems != null && !entriesVirtualFileSystems.contains(it)) {
                return false
            }
        }
        return super.contains(file)
    }

}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class LibraryWithoutSourceScope(project: Project, private val library: Library) :
    PoweredLibraryScopeBase(project, library.getFiles(OrderRootType.CLASSES), arrayOf()) {

    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getClassRootForFile(file)

    override fun equals(other: Any?) = other is LibraryWithoutSourceScope && library == other.library

    override fun calcHashCode(): Int = library.hashCode()

    override fun toString() = "LibraryWithoutSourceScope($library)"
}

@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class LibrarySourceScope(project: Project, private val library: Library) :
    PoweredLibraryScopeBase(project, arrayOf(), library.getFiles(OrderRootType.SOURCES)) {

    override fun getFileRoot(file: VirtualFile): VirtualFile? = myIndex.getSourceRootForFile(file)

    override fun equals(other: Any?) = other is LibrarySourceScope && library == other.library

    override fun calcHashCode(): Int = library.hashCode()

    override fun toString() = "LibrarySourceScope($library)"
}

//TODO: (module refactoring) android sdk has modified scope
@Suppress("EqualsOrHashCode") // DelegatingGlobalSearchScope requires to provide calcHashCode()
private class SdkScope(project: Project, val sdk: Sdk) :
    PoweredLibraryScopeBase(project, sdk.rootProvider.getFiles(OrderRootType.CLASSES), arrayOf()) {

    override fun equals(other: Any?) = other is SdkScope && sdk == other.sdk

    override fun calcHashCode(): Int = sdk.hashCode()

    override fun toString() = "SdkScope($sdk)"
}

fun IdeaModuleInfo.isLibraryClasses() = this is SdkInfo || this is LibraryInfo

val OriginCapability = ModuleCapability<ModuleOrigin>("MODULE_ORIGIN")

enum class ModuleOrigin {
    MODULE,
    LIBRARY,
    OTHER
}

interface BinaryModuleInfo : IdeaModuleInfo {
    val sourcesModuleInfo: SourceForBinaryModuleInfo?
    fun binariesScope(): GlobalSearchScope {
        val contentScope = contentScope()
        if (contentScope === GlobalSearchScope.EMPTY_SCOPE) {
            return contentScope
        }

        val project = contentScope.project
            ?: error("Project is empty for scope $contentScope (${contentScope.javaClass.name})")

        return KotlinSourceFilterScope.libraryClassFiles(contentScope, project)
    }
}

interface SourceForBinaryModuleInfo : IdeaModuleInfo {
    val binariesModuleInfo: BinaryModuleInfo
    fun sourceScope(): GlobalSearchScope

    // module infos for library source do not have contents in the following sense:
    // we can not provide a collection of files that is supposed to be analyzed in IDE independently
    //
    // as of now each source file is analyzed separately and depends on corresponding binaries
    // see KotlinCacheServiceImpl#createFacadeForSyntheticFiles
    override fun contentScope(): GlobalSearchScope = GlobalSearchScope.EMPTY_SCOPE

    override fun dependencies() = listOf(this) + binariesModuleInfo.dependencies()

    override val moduleOrigin: ModuleOrigin
        get() = ModuleOrigin.OTHER
}

data class PlatformModuleInfo(
    override val platformModule: ModuleSourceInfo,
    private val commonModules: List<ModuleSourceInfo> // NOTE: usually contains a single element for current implementation
) : IdeaModuleInfo, CombinedModuleInfo, TrackableModuleInfo {
    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = platformModule.capabilities

    override fun contentScope() = GlobalSearchScope.union(containedModules.map { it.contentScope() }.toTypedArray())

    override val containedModules: List<ModuleSourceInfo> = listOf(platformModule) + commonModules

    override val project: Project
        get() = platformModule.module.project

    override val platform: TargetPlatform
        get() = platformModule.platform

    override val moduleOrigin: ModuleOrigin
        get() = platformModule.moduleOrigin

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(platformModule.module.project)

    override fun dependencies() = platformModule.dependencies()
        // This is needed for cases when we create PlatformModuleInfo in Kotlin Multiplatform Analysis Mode is set to COMPOSITE, see
        // KotlinCacheService.getResolutionFacadeWithForcedPlatform.
        // For SEPARATE-mode, this filter will be executed in getSourceModuleDependencies.kt anyway, so it's essentially a no-op
        .filter { NonHmppSourceModuleDependenciesFilter(platformModule.platform).isSupportedDependency(it) }

    override val expectedBy: List<ModuleInfo>
        get() = platformModule.expectedBy

    override fun modulesWhoseInternalsAreVisible() = containedModules.flatMap { it.modulesWhoseInternalsAreVisible() }

    override val name: Name = Name.special("<Platform module ${platformModule.name} including ${commonModules.map { it.name }}>")

    override val displayedName: String
        get() = KotlinIdeaAnalysisBundle.message(
            "platform.module.0.including.1",
            platformModule.displayedName,
            commonModules.map { it.displayedName }
        )

    override fun createModificationTracker() = platformModule.createModificationTracker()
}

fun IdeaModuleInfo.projectSourceModules(): List<ModuleSourceInfo>? =
    (this as? ModuleSourceInfo)?.let(::listOf) ?: (this as? PlatformModuleInfo)?.containedModules

enum class SourceType {
    PRODUCTION,
    TEST
}

internal val ModuleSourceInfo.sourceType get() = if (this is ModuleTestSourceInfo) SourceType.TEST else SourceType.PRODUCTION

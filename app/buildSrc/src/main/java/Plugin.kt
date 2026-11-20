
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File
import java.util.Properties
import java.util.Random

// Set non-zero value here to fix the random seed for reproducible builds
// CI builds are always reproducible
val RAND_SEED = if (System.getenv("CI") != null) 42 else 0
lateinit var RANDOM: Random

private val props = Properties()
private var commitHash = ""
private val supportAbis = setOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64", "riscv64")
private val defaultAbis = setOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")

object Config {
    operator fun get(key: String): String? {
        val v = props[key] as? String ?: return null
        return v.ifBlank { null }
    }

    fun contains(key: String) = get(key) != null

    val version: String get() = get("version") ?: commitHash
    /**
     * Application ID of the app. The optional commandline-only `appIdSuffix` (e.g. ".tv") lets a
     * development build install next to the official app. Core's APP_PACKAGE_NAME follows it,
     * so such a build never treats the official package as an impostor to uninstall.
     */
    val appId: String get() = "com.topjohnwu.magisk" + appIdSuffix
    val appIdSuffix: String get() = get("appIdSuffix") ?: ""
    val versionCode: Int get() = get("magisk.versionCode")!!.toInt()
    val stubVersion: String get() = get("magisk.stubVersion")!!
    val abiList: Set<String> get() {
        val abiList = get("abiList") ?: return defaultAbis
        return abiList.split(Regex("\\s*,\\s*")).toSet() intersect supportAbis
    }
}

fun Project.rootFile(path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file
    else File(rootProject.file(".."), path)
}

class MagiskPlugin : Plugin<Project> {
    override fun apply(project: Project) = project.applyPlugin()

    private fun Project.applyPlugin() {
        initRandom(rootProject.file("dict.txt"))
        props.clear()

        // Get gradle properties relevant to Magisk
        props.putAll(properties.filter { (key, _) -> key.startsWith("magisk.") })

        // Load config.prop
        val configPath: String? by this
        val configFile = rootFile(configPath ?: "config.prop")
        if (configFile.exists()) {
            configFile.inputStream().use {
                val config = Properties()
                config.load(it)
                // Remove properties that should be passed by commandline
                config.remove("abiList")
                config.remove("appIdSuffix")
                props.putAll(config)
            }
        }

        // Commandline override
        findProperty("abiList")?.let { props.put("abiList", it) }
        // Commandline only (not gradle.properties or the environment) and validated, so a stray value
        // can never change a release build or break the generated BuildConfig
        gradle.startParameter.projectProperties["appIdSuffix"]?.let {
            require(Regex("(\\.[a-z][a-z0-9_]*)+").matches(it)) { "Invalid appIdSuffix: $it" }
            props.put("appIdSuffix", it)
        }

        val repo = FileRepository(rootFile(".git"))
        val refId = repo.refDatabase.exactRef("HEAD").objectId
        commitHash = repo.newObjectReader().abbreviate(refId, 8).name()
    }
}

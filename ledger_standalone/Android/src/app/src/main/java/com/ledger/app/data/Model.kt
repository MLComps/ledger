package com.ledger.app.data

import android.content.Context
import com.google.gson.annotations.SerializedName
import java.io.File

data class ModelDataFile(
  val name: String,
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

const val IMPORTS_DIR = "__imports"
private val NORMALIZE_NAME_REGEX = Regex("[^a-zA-Z0-9]")

enum class ModelCapability {
  @SerializedName("llm_thinking") LLM_THINKING,
  @SerializedName("speculative_decoding") SPECULATIVE_DECODING,
}

enum class RuntimeType {
  @SerializedName("unknown") UNKNOWN,
  @SerializedName("litert_lm") LITERT_LM,
  @SerializedName("aicore") AICORE,
}

enum class AICoreModelReleaseStage {
  @SerializedName("stable") STABLE,
  @SerializedName("preview") PREVIEW,
}

enum class AICoreModelPreference {
  @SerializedName("fast") FAST,
  @SerializedName("full") FULL,
}

data class ModelFile(
  @SerializedName("fileName") val fileName: String,
  @SerializedName("commitHash") val commitHash: String,
)

data class Model(
  val name: String,
  val displayName: String = "",
  val info: String = "",
  var configs: List<Config> = listOf(),
  val learnMoreUrl: String = "",
  val bestForTaskIds: List<String> = listOf(),
  val minDeviceMemoryInGb: Int? = null,
  val url: String = "",
  val sizeInBytes: Long = 0L,
  var downloadFileName: String = "_",
  var version: String = "_",
  val extraDataFiles: List<ModelDataFile> = listOf(),
  val isLlm: Boolean = false,
  val aicoreReleaseStage: AICoreModelReleaseStage? = null,
  val aicorePreference: AICoreModelPreference? = null,
  val parentModelName: String? = null,
  val variantLabel: String? = null,
  val updatableModelFiles: List<ModelFile> = listOf(),
  val updateInfo: String = "",
  val runtimeType: RuntimeType = RuntimeType.UNKNOWN,
  val localFileRelativeDirPathOverride: String = "",
  val localModelFilePathOverride: String = "",
  val showRunAgainButton: Boolean = true,
  val showBenchmarkButton: Boolean = true,
  val isZip: Boolean = false,
  val unzipDir: String = "",
  val llmSupportImage: Boolean = false,
  val llmSupportAudio: Boolean = false,
  val capabilities: List<ModelCapability> = listOf(),
  val llmMaxToken: Int = 0,
  val accelerators: List<Accelerator> = listOf(),
  val visionAccelerator: Accelerator = Accelerator.GPU,
  val imported: Boolean = false,
  val capabilityToTaskTypes: Map<ModelCapability, List<String>> = mapOf(),
  var normalizedName: String = "",
  var instance: Any? = null,
  var initializing: Boolean = false,
  var cleanUpAfterInit: Boolean = false,
  var configValues: Map<String, Any> = mapOf(),
  var prevConfigValues: Map<String, Any> = mapOf(),
  var totalBytes: Long = 0L,
  var accessToken: String? = null,
  var updatable: Boolean = false,
  var latestModelFile: ModelFile? = null,
) {
  init {
    normalizedName = NORMALIZE_NAME_REGEX.replace(name, "_")
  }

  fun preProcess() {
    val configValues: MutableMap<String, Any> = mutableMapOf()
    for (config in this.configs) {
      configValues[config.key.label] = config.defaultValue
    }
    this.configValues = configValues
    this.totalBytes = this.sizeInBytes + this.extraDataFiles.sumOf { it.sizeInBytes }
  }

  fun getPath(context: Context, fileName: String = downloadFileName): String {
    if (imported) {
      return listOf(context.getExternalFilesDir(null)?.absolutePath ?: "", fileName)
        .joinToString(File.separator)
    }

    if (localModelFilePathOverride.isNotEmpty()) {
      return localModelFilePathOverride
    }

    if (localFileRelativeDirPathOverride.isNotEmpty()) {
      return listOf(
          context.getExternalFilesDir(null)?.absolutePath ?: "",
          localFileRelativeDirPathOverride,
          fileName,
        )
        .joinToString(File.separator)
    }

    val baseDir =
      listOf(context.getExternalFilesDir(null)?.absolutePath ?: "", normalizedName, version)
        .joinToString(File.separator)
    return if (this.isZip && this.unzipDir.isNotEmpty()) {
      listOf(baseDir, this.unzipDir).joinToString(File.separator)
    } else {
      listOf(baseDir, fileName).joinToString(File.separator)
    }
  }

  fun getIntConfigValue(key: ConfigKey, defaultValue: Int = 0): Int {
    return getTypedConfigValue(key = key, valueType = ValueType.INT, defaultValue = defaultValue)
      as Int
  }

  fun getFloatConfigValue(key: ConfigKey, defaultValue: Float = 0.0f): Float {
    return getTypedConfigValue(key = key, valueType = ValueType.FLOAT, defaultValue = defaultValue)
      as Float
  }

  fun getBooleanConfigValue(key: ConfigKey, defaultValue: Boolean = false): Boolean {
    return getTypedConfigValue(
      key = key,
      valueType = ValueType.BOOLEAN,
      defaultValue = defaultValue,
    )
      as Boolean
  }

  fun getStringConfigValue(key: ConfigKey, defaultValue: String = ""): String {
    return getTypedConfigValue(key = key, valueType = ValueType.STRING, defaultValue = defaultValue)
      as String
  }

  fun getExtraDataFile(name: String): ModelDataFile? {
    return extraDataFiles.find { it.name == name }
  }

  private fun getTypedConfigValue(key: ConfigKey, valueType: ValueType, defaultValue: Any): Any {
    return convertValueToTargetType(
      value = configValues.getOrDefault(key.label, defaultValue),
      valueType = valueType,
    )
  }
}

enum class ModelDownloadStatusType {
  NOT_DOWNLOADED,
  PARTIALLY_DOWNLOADED,
  IN_PROGRESS,
  UNZIPPING,
  SUCCEEDED,
  FAILED,
}

data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
  val remainingMs: Long = 0,
)

val EMPTY_MODEL: Model =
  Model(name = "empty", downloadFileName = "empty.tflite", url = "", sizeInBytes = 0L)

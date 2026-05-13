package com.ledger.app.data

import android.os.Build
import android.util.Log
import com.google.gson.annotations.SerializedName

private const val TAG = "LedgerModelAllowlist"

data class DefaultConfig(
  @SerializedName("topK") val topK: Int?,
  @SerializedName("topP") val topP: Float?,
  @SerializedName("temperature") val temperature: Float?,
  @SerializedName("accelerators") val accelerators: String?,
  @SerializedName("visionAccelerator") val visionAccelerator: String?,
  @SerializedName("maxContextLength") val maxContextLength: Int?,
  @SerializedName("maxTokens") val maxTokens: Int?,
)

data class SocModelFile(
  @SerializedName("modelFile") val modelFile: String?,
  @SerializedName("url") val url: String?,
  @SerializedName("commitHash") val commitHash: String?,
  @SerializedName("sizeInBytes") val sizeInBytes: Long?,
)

data class AllowedModel(
  val name: String,
  val modelId: String,
  val modelFile: String,
  val commitHash: String,
  val description: String,
  val sizeInBytes: Long,
  val defaultConfig: DefaultConfig,
  val taskTypes: List<String>,
  val disabled: Boolean? = null,
  val llmSupportImage: Boolean? = null,
  val llmSupportAudio: Boolean? = null,
  val capabilities: List<ModelCapability>? = null,
  val minDeviceMemoryInGb: Int? = null,
  val bestForTaskTypes: List<String>? = null,
  val localModelFilePathOverride: String? = null,
  val url: String? = null,
  val socToModelFiles: Map<String, SocModelFile>? = null,
  val runtimeType: RuntimeType? = null,
  val aicoreReleaseStage: AICoreModelReleaseStage? = null,
  val aicorePreference: AICoreModelPreference? = null,
  val parentModelName: String? = null,
  val variantLabel: String? = null,
  val capabilityToTaskTypes: Map<ModelCapability, List<String>>? = null,
  val updatableModelFiles: List<ModelFile>? = null,
  val updateInfo: String? = null,
) {
  fun toModel(): Model {
    var version = commitHash
    var downloadedFileName = modelFile
    var downloadUrl =
      url ?: "https://huggingface.co/$modelId/resolve/$commitHash/$modelFile?download=true"
    var sizeInBytes = sizeInBytes

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (socToModelFiles?.isNotEmpty() == true) {
        socToModelFiles[SOC]?.let { info ->
          Log.d(TAG, "Found soc-specific model files for model $name: $info")
          version = info.commitHash ?: "-"
          downloadedFileName = info.modelFile ?: "-"
          downloadUrl =
            info.url
              ?: "https://huggingface.co/$modelId/resolve/${info.commitHash}/${info.modelFile}?download=true"
          sizeInBytes = info.sizeInBytes ?: -1
        }
      }
    }

    val isLlmModel =
      taskTypes.contains(BuiltInTaskId.LLM_CHAT) ||
        taskTypes.contains(BuiltInTaskId.LLM_PROMPT_LAB) ||
        taskTypes.contains(BuiltInTaskId.LLM_ASK_AUDIO) ||
        taskTypes.contains(BuiltInTaskId.LLM_ASK_IMAGE) ||
        taskTypes.contains(BuiltInTaskId.LLM_LEDGER)

    var configs: MutableList<Config> = mutableListOf()
    var llmMaxToken = 1024
    var llmMaxContextLength: Int? = null
    var accelerators: List<Accelerator> = DEFAULT_ACCELERATORS
    var visionAccelerator: Accelerator = DEFAULT_VISION_ACCELERATOR

    val acceleratorsStr = defaultConfig.accelerators

    if (isLlmModel) {
      val defaultTopK: Int = defaultConfig.topK ?: DEFAULT_TOPK
      val defaultTopP: Float = defaultConfig.topP ?: DEFAULT_TOPP
      val defaultTemperature: Float = defaultConfig.temperature ?: DEFAULT_TEMPERATURE
      llmMaxToken = defaultConfig.maxTokens ?: 1024
      llmMaxContextLength = defaultConfig.maxContextLength
      if (acceleratorsStr != null) {
        val items = acceleratorsStr.split(",")
        accelerators = mutableListOf()
        for (item in items) {
          when (item.trim()) {
            "cpu" -> (accelerators as MutableList).add(Accelerator.CPU)
            "gpu" -> (accelerators as MutableList).add(Accelerator.GPU)
            "npu" -> (accelerators as MutableList).add(Accelerator.NPU)
            "tpu" -> (accelerators as MutableList).add(Accelerator.TPU)
          }
        }
      }
      if (defaultConfig.visionAccelerator != null) {
        visionAccelerator = when (defaultConfig.visionAccelerator) {
          "cpu" -> Accelerator.CPU
          "gpu" -> Accelerator.GPU
          "npu" -> Accelerator.NPU
          else -> Accelerator.GPU
        }
      }
      val npuOnly =
        accelerators.size == 1 &&
          (accelerators[0] == Accelerator.NPU || accelerators[0] == Accelerator.TPU)
      configs =
        (if (npuOnly) {
            createLlmChatConfigsForNpuModel(
              defaultMaxToken = llmMaxToken,
              accelerators = accelerators,
            )
          } else {
            createLlmChatConfigs(
              defaultTopK = defaultTopK,
              defaultTopP = defaultTopP,
              defaultTemperature = defaultTemperature,
              defaultMaxToken = llmMaxToken,
              defaultMaxContextLength = llmMaxContextLength,
              accelerators = accelerators,
              supportThinking = capabilities?.contains(ModelCapability.LLM_THINKING) == true,
              supportSpeculativeDecoding =
                capabilities?.contains(ModelCapability.SPECULATIVE_DECODING) == true,
            )
          })
          .toMutableList()
    }

    val learnMoreUrl = "https://huggingface.co/${modelId}"

    var showBenchmarkButton = true
    var showRunAgainButton = true
    if (isLlmModel) {
      showBenchmarkButton = false
      showRunAgainButton = false
    }
    return Model(
      name = name,
      version = version,
      info = description,
      url = downloadUrl,
      sizeInBytes = sizeInBytes,
      minDeviceMemoryInGb = minDeviceMemoryInGb,
      configs = configs,
      downloadFileName = downloadedFileName,
      showBenchmarkButton = showBenchmarkButton,
      showRunAgainButton = showRunAgainButton,
      learnMoreUrl = learnMoreUrl,
      llmSupportImage = llmSupportImage == true,
      llmSupportAudio = llmSupportAudio == true,
      capabilities = capabilities ?: emptyList(),
      llmMaxToken = llmMaxToken,
      accelerators = accelerators,
      visionAccelerator = visionAccelerator,
      bestForTaskIds = bestForTaskTypes ?: listOf(),
      localModelFilePathOverride = localModelFilePathOverride ?: "",
      isLlm = isLlmModel,
      runtimeType = runtimeType ?: RuntimeType.LITERT_LM,
      aicoreReleaseStage = aicoreReleaseStage,
      aicorePreference = aicorePreference,
      parentModelName = parentModelName,
      variantLabel = variantLabel,
      capabilityToTaskTypes = capabilityToTaskTypes ?: emptyMap(),
      updatableModelFiles = updatableModelFiles ?: listOf(),
      updateInfo = updateInfo ?: "",
      latestModelFile = ModelFile(fileName = downloadedFileName, commitHash = version),
    )
  }
}

data class ModelAllowlist(
  val models: List<AllowedModel>,
)

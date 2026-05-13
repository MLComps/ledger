package com.ledger.app.ui.modelsetup

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.data.DataStoreRepository
import com.ledger.app.data.DownloadRepository
import com.ledger.app.data.Model
import com.ledger.app.data.ModelAllowlist
import com.ledger.app.data.ModelDownloadStatus
import com.ledger.app.data.ModelDownloadStatusType
import com.ledger.app.data.ProjectConfig
import com.ledger.app.data.TMP_FILE_EXT
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues

private const val TAG = "ModelSetupViewModel"
private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
private const val TOKEN_EXPIRY_BUFFER_MS = 5 * 60 * 1000L

data class ModelSetupUiState(
  val models: List<Model> = listOf(),
  val downloadStatus: Map<String, ModelDownloadStatus> = mapOf(),
  val loading: Boolean = true,
  val error: String = "",
  val hfLoggedIn: Boolean = false,
  val hfTokenExpired: Boolean = false,
)

@HiltViewModel
class ModelSetupViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val downloadRepository: DownloadRepository,
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ModelSetupUiState())
  val uiState = _uiState.asStateFlow()

  private val externalFilesDir = context.getExternalFilesDir(null)

  val authService = AuthorizationService(context)
  var curAccessToken: String? = null
    private set

  init {
    loadModels()
    restoreToken()
  }

  private fun restoreToken() {
    viewModelScope.launch(Dispatchers.IO) {
      val stored = dataStoreRepository.readHfAccessToken() ?: return@launch
      val (token, expiresAt) = stored
      val expired = System.currentTimeMillis() >= expiresAt - TOKEN_EXPIRY_BUFFER_MS
      if (!expired) {
        curAccessToken = token
        _uiState.update { it.copy(hfLoggedIn = true, hfTokenExpired = false) }
      } else {
        _uiState.update { it.copy(hfLoggedIn = false, hfTokenExpired = true) }
      }
    }
  }

  fun getAuthorizationRequest(): AuthorizationRequest =
    AuthorizationRequest.Builder(
      ProjectConfig.authServiceConfig,
      ProjectConfig.clientId,
      ResponseTypeValues.CODE,
      android.net.Uri.parse(ProjectConfig.redirectUri),
    )
      .setScope("read-repos")
      .build()

  fun handleAuthResult(
    result: ActivityResult,
    onDone: (success: Boolean, errorMessage: String?) -> Unit,
  ) {
    val dataIntent = result.data
    if (dataIntent == null) {
      onDone(false, "Empty auth result")
      return
    }

    val response = AuthorizationResponse.fromIntent(dataIntent)
    val exception = AuthorizationException.fromIntent(dataIntent)

    when {
      response?.authorizationCode != null -> {
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
          when {
            tokenResponse?.accessToken != null && tokenResponse.accessTokenExpirationTime != null -> {
              val token = tokenResponse.accessToken!!
              val expiresAt = tokenResponse.accessTokenExpirationTime!!
              viewModelScope.launch(Dispatchers.IO) {
                dataStoreRepository.saveHfAccessToken(token, expiresAt)
              }
              curAccessToken = token
              _uiState.update { it.copy(hfLoggedIn = true, hfTokenExpired = false) }
              onDone(true, null)
            }
            tokenEx != null -> onDone(false, "Token exchange failed: ${tokenEx.message}")
            else -> onDone(false, "Token exchange failed")
          }
        }
      }
      exception != null -> onDone(false, exception.message)
      else -> onDone(false, null)
    }
  }

  fun logout() {
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.clearHfAccessToken()
    }
    curAccessToken = null
    _uiState.update { it.copy(hfLoggedIn = false, hfTokenExpired = false) }
  }

  fun loadModels() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val content = context.assets.open(MODEL_ALLOWLIST_FILENAME)
          .bufferedReader().use { it.readText() }
        val allowlist = Gson().fromJson(content, ModelAllowlist::class.java)
        val ledgerModels = allowlist.models
          .filter { it.disabled != true && it.taskTypes.contains("llm_ledger") }
          .map { it.toModel().also { m -> m.preProcess() } }

        val statusMap = ledgerModels.associate { model ->
          model.name to getModelDownloadStatus(model)
        }

        _uiState.update {
          it.copy(models = ledgerModels, downloadStatus = statusMap, loading = false)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load model allowlist", e)
        _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to load models") }
      }
    }
  }

  fun downloadModel(model: Model) {
    setDownloadStatus(
      model = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )
    downloadRepository.downloadModel(
      model = model,
      accessToken = curAccessToken,
      onStatusUpdated = { m, s -> setDownloadStatus(m, s) },
    )
  }

  fun cancelDownload(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    deleteModel(model)
    setDownloadStatus(model, ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED))
  }

  fun deleteModel(model: Model) {
    val dir = File(externalFilesDir, model.normalizedName)
    if (dir.exists()) dir.deleteRecursively()
    setDownloadStatus(model, ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED))
  }

  fun discardPartialDownload(model: Model) {
    val tmpFile = File(
      externalFilesDir,
      listOf(model.normalizedName, model.version, "${model.downloadFileName}.$TMP_FILE_EXT")
        .joinToString(File.separator),
    )
    if (tmpFile.exists()) tmpFile.delete()
    setDownloadStatus(model, ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED))
  }

  fun isModelDownloaded(model: Model): Boolean {
    val modelRelativePath = listOf(model.normalizedName, model.version, model.downloadFileName)
      .joinToString(File.separator)
    return model.downloadFileName.isNotEmpty() &&
      File(externalFilesDir, modelRelativePath).exists()
  }

  override fun onCleared() {
    super.onCleared()
    authService.dispose()
  }

  private fun setDownloadStatus(model: Model, status: ModelDownloadStatus) {
    if (status.status == ModelDownloadStatusType.FAILED ||
      status.status == ModelDownloadStatusType.NOT_DOWNLOADED) {
      val tmpFile = File(
        externalFilesDir,
        listOf(model.normalizedName, model.version, "${model.downloadFileName}.$TMP_FILE_EXT")
          .joinToString(File.separator)
      )
      if (tmpFile.exists()) tmpFile.delete()
    }
    _uiState.update {
      it.copy(downloadStatus = it.downloadStatus + (model.name to status))
    }
  }

  private fun getModelDownloadStatus(model: Model): ModelDownloadStatus {
    val tmpPath = File(
      externalFilesDir,
      listOf(model.normalizedName, model.version, "${model.downloadFileName}.$TMP_FILE_EXT")
        .joinToString(File.separator)
    )
    return when {
      isModelDownloaded(model) -> ModelDownloadStatus(
        status = ModelDownloadStatusType.SUCCEEDED,
        receivedBytes = model.sizeInBytes,
        totalBytes = model.sizeInBytes,
      )
      tmpPath.exists() -> ModelDownloadStatus(
        status = ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
        receivedBytes = tmpPath.length(),
        totalBytes = model.totalBytes,
      )
      else -> ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
    }
  }
}

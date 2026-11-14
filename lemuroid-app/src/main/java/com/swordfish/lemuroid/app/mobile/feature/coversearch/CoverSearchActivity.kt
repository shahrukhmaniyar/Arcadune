package com.swordfish.lemuroid.app.mobile.feature.coversearch

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneColors
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcaduneIconPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ArcadunePillButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shows a Google Images search for a game's box art. The user long-presses an image to use it
 * as the game's custom cover. The chosen image is downloaded into the cache directory and its
 * file:// uri is returned as the activity result, ready for CustomCoverManager.importCover.
 */
class CoverSearchActivity : AppCompatActivity() {
    private val scope: CoroutineScope = MainScope()
    private lateinit var webView: WebView
    private var queryText by mutableStateOf("")
    private var loadProgress by mutableFloatStateOf(1f)
    private var pendingImageSource by mutableStateOf<String?>(null)
    private var isDownloading by mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        queryText =
            savedInstanceState?.getString(STATE_QUERY)
                ?: intent.getStringExtra(EXTRA_QUERY).orEmpty()

        webView =
            WebView(this).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setBackgroundColor(0xFF0B0A12.toInt())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onProgressChanged(
                            view: WebView?,
                            newProgress: Int,
                        ) {
                            loadProgress = (newProgress / 100f).coerceIn(0f, 1f)
                        }
                    }
                setOnLongClickListener { onWebViewLongClick() }
            }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            },
        )

        if (savedInstanceState == null) {
            runSearch(queryText)
        } else {
            webView.restoreState(savedInstanceState)
        }

        setContent {
            AppTheme {
                val focusManager = LocalFocusManager.current
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(ArcaduneColors.Background),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            ArcaduneColors.Purple.copy(alpha = 0.08f),
                                            ArcaduneColors.Background,
                                        ),
                                    ),
                                )
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ArcaduneIconPill(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = android.R.string.cancel),
                                onClick = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                            )

                            val fieldShape = RoundedCornerShape(999.dp)
                            Row(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(fieldShape)
                                        .background(ArcaduneColors.Surface)
                                        .border(1.dp, ArcaduneColors.GlassEdge, fieldShape)
                                        .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = ArcaduneColors.OnSurfaceDim,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (queryText.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.cover_search_query_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ArcaduneColors.OnSurfaceDim,
                                            maxLines = 1,
                                        )
                                    }
                                    BasicTextField(
                                        value = queryText,
                                        onValueChange = { queryText = it },
                                        singleLine = true,
                                        textStyle =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                color = ArcaduneColors.OnSurface,
                                            ),
                                        cursorBrush = SolidColor(ArcaduneColors.Purple),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions =
                                            KeyboardActions(
                                                onSearch = {
                                                    focusManager.clearFocus()
                                                    runSearch(queryText)
                                                },
                                            ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (queryText.isNotEmpty()) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable { queryText = "" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = null,
                                            tint = ArcaduneColors.OnSurfaceDim,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            ArcadunePillButton(
                                label = stringResource(id = R.string.cover_search_button),
                                highlighted = true,
                                height = 42.dp,
                                onClick = {
                                    focusManager.clearFocus()
                                    runSearch(queryText)
                                },
                            )
                        }

                        Text(
                            text = stringResource(id = R.string.cover_search_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcaduneColors.OnSurfaceDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnimatedVisibility(visible = loadProgress < 1f) {
                        LinearProgressIndicator(
                            progress = { loadProgress },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = ArcaduneColors.Purple,
                            trackColor = ArcaduneColors.SurfaceHigh,
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.06f)),
                    )

                    AndroidView(
                        factory = {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                    )
                }

                val candidateSource = pendingImageSource
                if (candidateSource != null) {
                    val previewShape = RoundedCornerShape(16.dp)
                    ArcaduneDialog(
                        onDismissRequest = {
                            if (!isDownloading) pendingImageSource = null
                        },
                        icon = Icons.Filled.Image,
                        title = { Text(text = stringResource(id = R.string.cover_search_use_image_title)) },
                        dismissButton = {
                            ArcadunePillButton(
                                label = stringResource(id = android.R.string.cancel),
                                enabled = !isDownloading,
                                onClick = { pendingImageSource = null },
                            )
                        },
                        confirmButton = {
                            ArcadunePillButton(
                                label = stringResource(id = R.string.cover_search_use_image_confirm),
                                highlighted = true,
                                enabled = !isDownloading,
                                onClick = { downloadAndFinish(candidateSource) },
                            )
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(144.dp)
                                        .clip(previewShape)
                                        .background(ArcaduneColors.SurfaceHigh)
                                        .border(1.dp, ArcaduneColors.GlassEdge, previewShape),
                            ) {
                                AsyncImage(
                                    model =
                                        ImageRequest.Builder(this@CoverSearchActivity)
                                            .data(candidateSource)
                                            .addHeader("User-Agent", USER_AGENT)
                                            .crossfade(true)
                                            .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = stringResource(id = R.string.cover_search_use_image_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ArcaduneColors.OnSurfaceDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun runSearch(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) return

        val url =
            Uri.parse("https://www.google.com/search").buildUpon()
                .appendQueryParameter("tbm", "isch")
                .appendQueryParameter("q", query)
                .build()
                .toString()
        webView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString(STATE_QUERY, queryText)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        pendingImageSource = null
        scope.cancel()
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun onWebViewLongClick(): Boolean {
        val hit = webView.hitTestResult
        val isImage =
            hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val imageSource = hit.extra
        if (!isImage || imageSource.isNullOrBlank()) return false

        pendingImageSource = imageSource
        return true
    }

    private fun downloadAndFinish(imageSource: String) {
        isDownloading = true
        Toast.makeText(this, R.string.cover_search_downloading, Toast.LENGTH_SHORT).show()
        scope.launch {
            val file =
                try {
                    withContext(Dispatchers.IO) { downloadImage(imageSource) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download cover from search")
                    null
                }

            isDownloading = false
            pendingImageSource = null

            if (file == null) {
                Toast.makeText(
                    this@CoverSearchActivity,
                    R.string.cover_search_failed,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            setResult(RESULT_OK, Intent().setData(Uri.fromFile(file)))
            finish()
        }
    }

    private fun downloadImage(imageSource: String): File {
        val bytes =
            if (imageSource.startsWith("data:")) {
                // data:[<mime>][;base64],<payload>
                val payload = imageSource.substringAfter(',')
                if (imageSource.substringBefore(',').contains(";base64")) {
                    Base64.decode(payload, Base64.DEFAULT)
                } else {
                    Uri.decode(payload).toByteArray()
                }
            } else {
                val request =
                    Request.Builder()
                        .url(imageSource)
                        .header("User-Agent", USER_AGENT)
                        .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    response.body?.bytes() ?: error("Empty response body")
                }
            }

        if (bytes.isEmpty()) error("Empty image")

        val destination = File(cacheDir, "cover_search_result")
        destination.writeBytes(bytes)
        return destination
    }

    companion object {
        const val EXTRA_QUERY = "EXTRA_QUERY"
        private const val STATE_QUERY = "STATE_QUERY"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build()
        }
    }
}

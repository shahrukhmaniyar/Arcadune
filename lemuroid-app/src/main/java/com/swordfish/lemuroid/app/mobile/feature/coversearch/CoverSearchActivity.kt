package com.swordfish.lemuroid.app.mobile.feature.coversearch

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.swordfish.lemuroid.R
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

/**
 * Shows a Google Images search for a game's box art. The user long-presses an image to use it
 * as the game's custom cover. The chosen image is downloaded into the cache directory and its
 * file:// uri is returned as the activity result, ready for CustomCoverManager.importCover.
 */
class CoverSearchActivity : AppCompatActivity() {
    private val scope: CoroutineScope = MainScope()
    private val httpClient by lazy { OkHttpClient() }
    private lateinit var webView: WebView
    private lateinit var queryInput: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialQuery =
            savedInstanceState?.getString(STATE_QUERY)
                ?: intent.getStringExtra(EXTRA_QUERY).orEmpty()
        title = getString(R.string.cover_search_title)

        webView =
            WebView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                setOnLongClickListener { onWebViewLongClick() }
            }

        queryInput =
            EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setText(initialQuery)
                setSelectAllOnFocus(true)
                hint = getString(R.string.cover_search_query_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setSingleLine(true)
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        runSearch()
                        true
                    } else {
                        false
                    }
                }
            }

        val searchButton =
            Button(this).apply {
                text = getString(R.string.cover_search_button)
                setOnClickListener { runSearch() }
            }

        val searchBar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padding = (8 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
                addView(queryInput)
                addView(searchButton)
            }

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                fitsSystemWindows = true
                addView(searchBar)
                addView(webView)
            }
        setContentView(root)

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
            runSearch()
            Toast.makeText(this, R.string.cover_search_hint, Toast.LENGTH_LONG).show()
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun runSearch() {
        val query = queryInput.text.toString().trim()
        if (query.isEmpty()) return

        val url = Uri.parse("https://www.google.com/search").buildUpon()
            .appendQueryParameter("tbm", "isch")
            .appendQueryParameter("q", query)
            .build()
            .toString()
        webView.loadUrl(url)

        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(queryInput.windowToken, 0)
        queryInput.clearFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
        outState.putString(STATE_QUERY, queryInput.text.toString())
    }

    override fun onDestroy() {
        scope.cancel()
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

        AlertDialog.Builder(this)
            .setTitle(R.string.cover_search_use_image_title)
            .setMessage(R.string.cover_search_use_image_message)
            .setPositiveButton(R.string.cover_search_use_image_confirm) { _, _ ->
                downloadAndFinish(imageSource)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        return true
    }

    private fun downloadAndFinish(imageSource: String) {
        Toast.makeText(this, R.string.cover_search_downloading, Toast.LENGTH_SHORT).show()
        scope.launch {
            val file =
                try {
                    withContext(Dispatchers.IO) { downloadImage(imageSource) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to download cover from search")
                    null
                }

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
                val request = Request.Builder().url(imageSource).build()
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
    }
}

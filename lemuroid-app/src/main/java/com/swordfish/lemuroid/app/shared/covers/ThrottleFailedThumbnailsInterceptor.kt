package com.swordfish.lemuroid.app.shared.covers

import android.util.LruCache
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Thrown instead of a request when the same thumbnail already failed with a server error code. */
class PreviouslyFailedThumbnailException(code: Int) :
    IOException("Thumbnail previously failed with code: $code")

object ThrottleFailedThumbnailsInterceptor : Interceptor {
    private val failedThumbnailsStatusCode = LruCache<String, Int>(256 * 1024)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestUrl = chain.request().url.toString()
        val previousFailure = failedThumbnailsStatusCode[requestUrl]
        if (previousFailure != null) {
            throw PreviouslyFailedThumbnailException(previousFailure)
        }

        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            failedThumbnailsStatusCode.put(chain.request().url.toString(), response.code)
        }

        return response
    }
}

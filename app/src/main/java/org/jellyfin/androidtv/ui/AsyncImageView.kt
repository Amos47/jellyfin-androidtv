package org.jellyfin.androidtv.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.asImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.BlurHashDecoder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds

/**
 * An extension to the [ImageView] that makes it easy to load images from the network.
 * The [load] function takes a url, blurhash and placeholder to asynchronously load the image
 * using the lifecycle of the current fragment or activity.
 */
class AsyncImageView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr), KoinComponent {
	private val lifeCycleOwner get() = findViewTreeLifecycleOwner()
	private val styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.AsyncImageView, defStyleAttr, 0)
	private val imageLoader by inject<ImageLoader>()
	private var loadJob: Job? = null

	/**
	 * The duration of the crossfade when changing switching the images of the url, blurhash and
	 * placeholder.
	 */
	@Suppress("MagicNumber")
	var crossFadeDuration = styledAttributes.getInt(R.styleable.AsyncImageView_crossfadeDuration, 100).milliseconds

	/**
	 * Shape the image to a circle and remove all corners.
	 */
	var circleCrop = styledAttributes.getBoolean(R.styleable.AsyncImageView_circleCrop, false)

	/**
	 * Load an image from the network using [url]. When the [url] is null or returns a bad response
	 * the [placeholder] is shown. A [blurHash] is shown while loading the image. An aspect ratio is
	 * required when using a BlurHash or the sizing will be incorrect.
	 *
	 * When loading [url] fails, each of the [fallbackUrls] is tried in order until one succeeds. Network failures stop
	 * the fallback early, see [allowsImageFallback].
	 */
	@JvmOverloads
	fun load(
		url: String? = null,
		blurHash: String? = null,
		placeholder: Drawable? = null,
		aspectRatio: Double = 1.0,
		blurHashResolution: Int = 32,
		fallbackUrls: List<String> = emptyList(),
	) = doOnAttach {
		// Cancel the previous load if still running
		loadJob?.cancel()

		loadJob = lifeCycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
			var placeholderOrBlurHash = placeholder

			// Only show blurhash if an image is going to be loaded from the network
			val isLowRamDevice = context.getSystemService<ActivityManager>()?.isLowRamDevice == true
			if (url != null && blurHash != null && !isLowRamDevice && aspectRatio > 0) withContext(Dispatchers.IO) {
				val blurHashBitmap = BlurHashDecoder.decode(
					blurHash,
					if (aspectRatio > 1) round(blurHashResolution * aspectRatio).toInt() else blurHashResolution,
					if (aspectRatio >= 1) blurHashResolution else round(blurHashResolution / aspectRatio).toInt(),
				)
				if (blurHashBitmap != null) placeholderOrBlurHash = blurHashBitmap.toDrawable(resources)
			}

			// Show placeholder when there is no image to load
			if (url == null) {
				val request = ImageRequest.Builder(context).apply {
					target(this@AsyncImageView)
					data(placeholder)
					if (circleCrop) transformations(CircleCropTransformation())
				}.build()

				imageLoader.enqueue(request).job.await()
				return@launch
			}

			// Start loading image, continuing with the fallbacks when it fails
			val urls = listOf(url) + fallbackUrls
			for ((index, candidateUrl) in urls.withIndex()) {
				val isLastCandidate = index == urls.lastIndex
				val candidateRequest = ImageRequest.Builder(context).apply {
					val crossFadeDurationMs = crossFadeDuration.inWholeMilliseconds.toInt()
					if (crossFadeDurationMs > 0) crossfade(crossFadeDurationMs)
					else crossfade(false)

					target(this@AsyncImageView)
					data(candidateUrl)
					placeholder(placeholderOrBlurHash?.asImage())
					if (circleCrop) transformations(CircleCropTransformation())
					error(placeholder?.asImage())
				}.build()

				val result = imageLoader.enqueue(candidateRequest).job.await()
				if (result !is ErrorResult || isLastCandidate || !result.throwable.allowsImageFallback()) break

				Timber.d(result.throwable, "Failed to load image %s, trying fallback", candidateUrl)
			}
		}
	}
}

/**
 * Whether a failed image load should continue with the next fallback image. Network failures (no connection, timeouts)
 * would fail for every image from the same server, so those stop immediately instead of sending more requests. Missing
 * images (HTTP errors) and images that can't be decoded do continue.
 */
fun Throwable.allowsImageFallback(): Boolean = this !is IOException

package org.jellyfin.androidtv.ui.composable

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.util.BlurHashDecoder

private data class AsyncImageState(
	val url: String?,
	val fallbackUrls: List<String>,
	val blurHash: String?,
)

@Composable
fun AsyncImage(
	modifier: Modifier = Modifier,
	url: String? = null,
	fallbackUrls: List<String> = emptyList(),
	blurHash: String? = null,
	placeholder: Drawable? = null,
	aspectRatio: Float = 1f,
	blurHashResolution: Int = 32,
	scaleType: ImageView.ScaleType? = null,
) {
	// Only the important properties are added to AsyncImageState
	var state by remember { mutableStateOf<AsyncImageState?>(null) }

	AndroidView(
		modifier = modifier,
		factory = { context ->
			AsyncImageView(context).also { view ->
				view.adjustViewBounds = true
				view.scaleType = scaleType ?: ImageView.ScaleType.FIT_CENTER
			}
		},
		update = { view ->
			val compositionState = AsyncImageState(url, fallbackUrls, blurHash)
			if (state != compositionState) {
				state = compositionState

				view.load(
					url = compositionState.url,
					fallbackUrls = compositionState.fallbackUrls,
					blurHash = compositionState.blurHash,
					placeholder = placeholder,
					aspectRatio = aspectRatio.toDouble(),
					blurHashResolution = blurHashResolution,
				)
			}
		},
	)
}

@Composable
fun blurHashPainter(
	blurHash: String,
	size: IntSize,
	punch: Float = 1f,
): Painter = remember(blurHash, size, punch) {
	val bitmap = BlurHashDecoder.decode(
		blurHash = blurHash,
		width = size.width,
		height = size.height,
		punch = punch,
	)

	BitmapPainter(requireNotNull(bitmap).asImageBitmap())
}

package wanjie.quicklook

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import wanjie.quicklook.data.BookmarkStore
import wanjie.quicklook.data.SafManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickLookApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val bookmarkStore by lazy { BookmarkStore(this) }
    val safManager by lazy { SafManager(this) }

    override fun onCreate() {
        super.onCreate()
        // 首次启动播种默认书签
        appScope.launch { bookmarkStore.seedDefaultsIfEmpty() }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}

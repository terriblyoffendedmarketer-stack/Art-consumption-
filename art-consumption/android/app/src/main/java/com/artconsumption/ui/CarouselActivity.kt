package com.artconsumption.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.artconsumption.data.ArtDatabase
import com.artconsumption.data.ArtPost
import com.artconsumption.data.ContentScanner
import com.artconsumption.data.FirebaseSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CarouselActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcode = intent.getStringExtra(EXTRA_SHORTCODE)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                )
            ) {
                ArtCarouselScreen(shortcode)
            }
        }
    }

    companion object {
        private const val EXTRA_SHORTCODE = "shortcode"

        fun intent(context: Context, shortcode: String): Intent {
            return Intent(context, CarouselActivity::class.java).apply {
                putExtra(EXTRA_SHORTCODE, shortcode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}

@Composable
fun ArtCarouselScreen(targetShortcode: String?) {
    val context = LocalContext.current
    var allPosts by remember { mutableStateOf<List<ArtPost>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dao = ArtDatabase.get(context).artDao()
            val sync = FirebaseSync(context)

            if (dao.getPostCount() == 0) {
                sync.sync()
            }

            val posts = dao.getAllPostsOnce()
            allPosts = posts

            if (targetShortcode != null) {
                val idx = posts.indexOfFirst { it.shortcode == targetShortcode }
                if (idx >= 0) currentIndex = idx
            }
            loading = false
        }
    }

    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (allPosts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No art content available.\nCheck your internet connection.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
        }
        return
    }

    val post = allPosts[currentIndex]
    val slideModels = remember(currentIndex) { getSlideModels(post) }

    if (slideModels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No slides for this post", color = Color.Gray)
        }
        return
    }

    PostCarousel(
        post = post,
        slideModels = slideModels,
        postIndex = currentIndex,
        postCount = allPosts.size,
        onNextPost = {
            if (allPosts.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % allPosts.size
            }
        },
        onPrevPost = {
            if (allPosts.isNotEmpty()) {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else allPosts.size - 1
            }
        }
    )
}

private fun getSlideModels(post: ArtPost): List<Any> {
    val urls = FirebaseSync.getSlideUrls(post)
    if (urls.isNotEmpty()) return urls
    return ContentScanner.getSlidesForPost(post)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCarousel(
    post: ArtPost,
    slideModels: List<Any>,
    postIndex: Int,
    postCount: Int,
    onNextPost: () -> Unit,
    onPrevPost: () -> Unit,
) {
    var showCaption by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { slideModels.size })

    LaunchedEffect(post.shortcode) {
        pagerState.scrollToPage(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showCaption = !showCaption },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(slideModels[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Could not load image", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                )
            }
        }

        // Slide dots
        if (slideModels.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(slideModels.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                    if (index < slideModels.size - 1) Spacer(Modifier.width(4.dp))
                }
            }
        }

        // Caption overlay
        AnimatedVisibility(
            visible = showCaption,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp)
                    .height(200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "@${post.handle}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = post.caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        // Top bar: handle, IG link, slide counter
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "@${post.handle}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            Text(
                text = "View on IG",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        val url = "https://www.instagram.com/p/${post.shortcode}/"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            if (slideModels.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${slideModels.size}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }

        // Post navigation: prev / next buttons at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 70.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "◀  Prev",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onPrevPost() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Text(
                text = "${postIndex + 1} / $postCount",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Next  ▶",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onNextPost() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

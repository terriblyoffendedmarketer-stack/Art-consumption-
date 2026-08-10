package com.artconsumption.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.artconsumption.data.ArtDatabase
import com.artconsumption.data.ArtPost
import com.artconsumption.data.ContentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    var post by remember { mutableStateOf<ArtPost?>(null) }
    var slides by remember { mutableStateOf<List<File>>(emptyList()) }
    var allPosts by remember { mutableStateOf<List<ArtPost>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dao = ArtDatabase.get(context).artDao()

            ContentScanner(context).scan()

            val posts = dao.getAllPostsOnce()
            allPosts = posts

            val target = if (targetShortcode != null) {
                posts.indexOfFirst { it.shortcode == targetShortcode }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            currentIndex = target

            if (posts.isNotEmpty()) {
                post = posts[target]
                slides = ContentScanner.getSlidesForPost(posts[target])
            }
        }
    }

    if (post == null || slides.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Copy art content to\nArtConsumption/content/\non your device",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
        }
        return
    }

    PostCarousel(
        post = post!!,
        slides = slides,
        onNextPost = {
            if (allPosts.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % allPosts.size
                post = allPosts[currentIndex]
                slides = ContentScanner.getSlidesForPost(allPosts[currentIndex])
            }
        },
        onPrevPost = {
            if (allPosts.isNotEmpty()) {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else allPosts.size - 1
                post = allPosts[currentIndex]
                slides = ContentScanner.getSlidesForPost(allPosts[currentIndex])
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCarousel(
    post: ArtPost,
    slides: List<File>,
    onNextPost: () -> Unit,
    onPrevPost: () -> Unit,
) {
    var showCaption by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { slides.size })

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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(slides[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        if (slides.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(slides.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                    if (index < slides.size - 1) Spacer(Modifier.width(4.dp))
                }
            }
        }

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

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "@${post.handle}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            if (slides.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${slides.size}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

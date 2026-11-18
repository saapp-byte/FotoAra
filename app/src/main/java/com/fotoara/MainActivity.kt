package com.fotoara

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val embedder: Embedder = ColorEmbedder()
    private lateinit var store: EmbeddingStore
    private lateinit var status: TextView
    private lateinit var adapter: PhotoAdapter

    private val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startIndexing() else status.text = "Galeri izni gerekli."
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = EmbeddingStore(File(filesDir, "emb_${embedder.id}.txt"))

        val input = EditText(this).apply {
            hint = "Ara: örn. mavi, kırmızı, siyah"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> search(text.toString()); true }
        }
        status = TextView(this).apply { setPadding(0, dp(8), 0, dp(8)) }
        adapter = PhotoAdapter { uri ->
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        val grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            this.adapter = this@MainActivity.adapter
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(40), dp(12), dp(12))
            addView(input, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2))
            addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            startIndexing() else permLauncher.launch(permission)
    }

    private fun startIndexing() {
        lifecycleScope.launch(Dispatchers.Default) {
            Indexer.run(this@MainActivity, store, embedder) { done, total ->
                withContext(Dispatchers.Main) { status.text = "Taranıyor: $done / $total" }
            }
            withContext(Dispatchers.Main) { status.text = "Hazır: ${store.size()} fotoğraf" }
        }
    }

    private fun search(query: String) {
        lifecycleScope.launch {
            val q = embedder.embedText(query)
            if (q.all { it == 0f }) {
                status.text = "Bu aşamada sadece renk anlıyorum (kırmızı, mavi, siyah...)"
                return@launch
            }
            val ids = withContext(Dispatchers.Default) {
                store.all()
                    .map { (id, v) -> id to q.indices.sumOf { (q[it] * v[it]).toDouble() } }
                    .filter { it.second > 0.15 }
                    .sortedByDescending { it.second }
                    .take(90)
                    .map { it.first }
            }
            adapter.submit(ids)
            status.text = "${ids.size} sonuç"
        }
    }
}

class PhotoAdapter(private val onClick: (android.net.Uri) -> Unit) :
    RecyclerView.Adapter<PhotoAdapter.VH>() {
    private var ids = listOf<Long>()
    class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    fun submit(list: List<Long>) { ids = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val h = parent.resources.displayMetrics.widthPixels / 3
        val iv = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
        }
        return VH(iv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = Indexer.photoUri(ids[position])
        holder.iv.load(uri) { crossfade(true) }
        holder.iv.setOnClickListener { onClick(uri) }
    }

    override fun getItemCount() = ids.size
}

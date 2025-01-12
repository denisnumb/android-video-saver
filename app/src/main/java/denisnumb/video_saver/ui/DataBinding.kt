package denisnumb.video_saver.ui

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide

@BindingAdapter("videoPreview")
fun loadPreview(imageView: ImageView, previewUrl: String?){
    Glide.with(imageView).load(previewUrl).into(imageView)
}
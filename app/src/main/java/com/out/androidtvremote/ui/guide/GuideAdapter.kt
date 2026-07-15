package com.out.androidtvremote.ui.guide

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.out.androidtvremote.R


class GuideAdapter(private val slides: List<GuideSlide>) :
    RecyclerView.Adapter<GuideAdapter.GuideViewHolder>() {

    class GuideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.guideImage)
        private val title: TextView = view.findViewById(R.id.guideTitle)
        private val description: TextView = view.findViewById(R.id.guideDescription)

        fun bind(slide: GuideSlide) {
//            image.setImageResource(slide.image)
            title.text = slide.title
            description.text = slide.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuideViewHolder {
        return GuideViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.guide_slide_layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: GuideViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount() = slides.size
}
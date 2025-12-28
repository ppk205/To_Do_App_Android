package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.navigation.fragment.findNavController
import com.example.morp_prj.R
import com.example.morp_prj.utils.PreferenceManager
import com.google.android.material.button.MaterialButton

class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = view.findViewById<ViewPager2>(R.id.view_pager)
        val images = listOf(
            R.drawable.img_onboarding_work,
            R.drawable.img_onboarding_work_2,
            R.drawable.img_onboarding_work_3
        )
        val adapter = ImagePagerAdapter(images)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = images.size

        // Dots
        val dot1 = view.findViewById<ImageView>(R.id.dot1)
        val dot2 = view.findViewById<ImageView>(R.id.dot2)
        val dot3 = view.findViewById<ImageView>(R.id.dot3)
        val dots = listOf(dot1, dot2, dot3)

        fun updateDots(selected: Int) {
            for (i in dots.indices) {
                if (i == selected) {
                    dots[i].setImageResource(R.drawable.dot_active)
                } else {
                    dots[i].setImageResource(R.drawable.dot_inactive)
                }
            }
        }

        updateDots(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)
            }
        })

        view.findViewById<MaterialButton>(R.id.btn_get_started)?.setOnClickListener {
            findNavController().navigate(R.id.action_onboarding_to_login)
        }

        view.findViewById<View>(R.id.btn_guest_mode)?.setOnClickListener {
            // Lưu phiên làm việc Guest Mode
            // Hàm này sẽ xóa dữ liệu cũ (clearLoginData) trước khi set guest
            val preferenceManager = PreferenceManager(requireContext())
            preferenceManager.saveGuestSession()

            findNavController().navigate(R.id.action_onboarding_to_home)
        }
    }

    private class ImagePagerAdapter(private val images: List<Int>) : RecyclerView.Adapter<ImagePagerAdapter.VH>() {
        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val img: ImageView = itemView.findViewById(R.id.img_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_image, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.img.setImageResource(images[position])
        }

        override fun getItemCount(): Int = images.size
    }
}
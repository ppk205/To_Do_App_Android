package com.example.morp_prj.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morp_prj.R
import com.example.morp_prj.data.repository.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationsFragment : Fragment() {

    private var pageIndex = 0
    private var adapter: NotificationsAdapter? = null
    private lateinit var repo: NotificationRepository

    private val pageSize = 10

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = NotificationRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvNotifications)
        val btnLoadMore = view.findViewById<TextView>(R.id.btnLoadMore)

        adapter = NotificationsAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Mark as read once user opens this screen
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            repo.markAllRead()
        }

        // initial load
        pageIndex = 0
        loadPage(reset = true)

        btnLoadMore.setOnClickListener {
            pageIndex += 1
            loadPage(reset = false)
        }
    }

    private fun loadPage(reset: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val offset = pageIndex * pageSize
            val items = withContext(Dispatchers.IO) {
                repo.page(limit = pageSize, offset = offset)
                    .map { NotificationUiMapper.fromEntity(it) }
            }

            if (reset) {
                adapter?.replaceAll(items)
            } else {
                adapter?.append(items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }
}
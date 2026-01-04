package com.example.morp_prj.ui

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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

        // Chặn back press - không cho người dùng quay lại màn hình trước login
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Không làm gì - chặn back press hoàn toàn
            }
        })

        // Menu (modern API)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.notifications_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_clear_notifications -> {
                        confirmClearAll()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        repo = NotificationRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvNotifications)
        val btnLoadMore = view.findViewById<TextView>(R.id.btnLoadMore)

        adapter = NotificationsAdapter(onAction = { item, actionId ->
            when (actionId) {
                R.id.action_delete -> {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        repo.deleteOne(item.id, item.dedupeKey)
                    }
                    adapter?.removeById(item.id)
                }
                // R.id.action_more -> future actions
            }
        })
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Mark as read once user opens this screen (local-only for guest)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            repo.markAllRead()
        }

        // initial load
        pageIndex = 0
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Pull latest from server before showing (no-op in guest mode)
            repo.syncFromServer()
        }
        loadPage(reset = true)

        btnLoadMore.setOnClickListener {
            pageIndex += 1
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                // no-op in guest mode
                repo.syncFromServer()
            }
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


    private fun confirmClearAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear notifications")
            .setMessage("This will remove all notifications on this device. Continue?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    repo.clear()
                }
                // reset UI
                pageIndex = 0
                loadPage(reset = true)
            }
            .show()
    }
}
package com.example.morp_prj.data.db

/**
 * Local sync state for a task.
 * - PENDING: needs to be uploaded (new/edited locally)
 * - SYNCED: last local version is on server
 * - ERROR: last sync attempt failed
 */
enum class SyncState {
    PENDING,
    SYNCED,
    ERROR
}


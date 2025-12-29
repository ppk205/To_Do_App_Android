package com.example.morp_prj.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams")
    fun getAllTeams(): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE id = :teamId")
    fun getTeamById(teamId: String): TeamEntity?

    @Query("SELECT * FROM teams WHERE isPinned = 1")
    fun getPinnedTeams(): List<TeamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(team: TeamEntity)

    @Query("DELETE FROM teams")
    fun clearAll()
}
package com.mindflow.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.SearchFilters
import com.mindflow.app.ui.navigation.MindFlowDestinations

object NoteSearchQueryBuilder {
    fun build(filters: SearchFilters): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val clauses = mutableListOf<String>()

        if (filters.archivedOnly) {
            clauses += "isArchived = 1"
        } else if (!filters.includeArchived) {
            clauses += "isArchived = 0"
        }

        val trimmedQuery = filters.query.trim()
        if (trimmedQuery.isNotEmpty()) {
            clauses += "(topic LIKE ? OR content LIKE ? OR aiSummary LIKE ? OR aiKeyPoints LIKE ?)"
            val queryArg = "%$trimmedQuery%"
            args += queryArg
            args += queryArg
            args += queryArg
            args += queryArg
        }

        filters.tag?.let { selectedTag ->
            NoteTagCodec.likePattern(selectedTag)?.let { pattern ->
                clauses += "tags LIKE ?"
                args += pattern
            }
        }

        filters.folderKey?.let {
            if (it == MindFlowDestinations.UNCATEGORIZED_FOLDER) {
                clauses += "folderKey IS NULL"
            } else {
                clauses += "folderKey = ?"
                args += it
            }
        }

        filters.status?.let {
            clauses += "status = ?"
            args += it.name
        }

        filters.timeRange.startFrom()?.let {
            clauses += "createdAt >= ?"
            args += it
        }

        val sql = buildString {
            append("SELECT * FROM notes")
            if (clauses.isNotEmpty()) {
                append(" WHERE ")
                append(clauses.joinToString(" AND "))
            }
            append(" ORDER BY updatedAt DESC")
        }

        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}

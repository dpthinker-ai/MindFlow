package com.mindflow.app.data.local

import androidx.room.TypeConverter
import com.mindflow.app.data.model.FolderSource
import com.mindflow.app.data.model.KnowledgeTrust
import com.mindflow.app.data.model.NoteStatus
import com.mindflow.app.data.model.NoteTagCodec
import com.mindflow.app.data.model.TagSource
import com.mindflow.app.data.model.TopicSource

class MindFlowConverters {
    @TypeConverter
    fun toNoteStatus(value: String?): NoteStatus? = value?.let(NoteStatus::valueOf)

    @TypeConverter
    fun fromNoteStatus(value: NoteStatus?): String? = value?.name

    @TypeConverter
    fun toTopicSource(value: String?): TopicSource? = value?.let(TopicSource::valueOf)

    @TypeConverter
    fun fromTopicSource(value: TopicSource?): String? = value?.name

    @TypeConverter
    fun toFolderSource(value: String?): FolderSource? = value?.let(FolderSource::valueOf)

    @TypeConverter
    fun fromFolderSource(value: FolderSource?): String? = value?.name

    @TypeConverter
    fun toTagSource(value: String?): TagSource? = value?.let(TagSource::valueOf)

    @TypeConverter
    fun fromTagSource(value: TagSource?): String? = value?.name

    @TypeConverter
    fun toKnowledgeTrust(value: String?): KnowledgeTrust? = value?.let(KnowledgeTrust::valueOf)

    @TypeConverter
    fun fromKnowledgeTrust(value: KnowledgeTrust?): String? = value?.name

    @TypeConverter
    fun toTags(value: String?): List<String> = NoteTagCodec.decode(value)

    @TypeConverter
    fun fromTags(value: List<String>?): String = NoteTagCodec.encode(value.orEmpty())
}

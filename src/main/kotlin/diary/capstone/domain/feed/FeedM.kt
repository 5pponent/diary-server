package diary.capstone.domain.feed

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document("feed")
data class FeedM(
    @Id
    var id: ObjectId,

    var writer: Long,

    var content: String,
    var images: MutableList<ImageDto> = mutableListOf(),

    var likedUsers: MutableList<Long> = mutableListOf(),

    var comments: MutableList<CommentDto> = mutableListOf(),

    var showScope: String,
    var createTime: LocalDateTime
)

data class ImageDto(
    var originalName: String,
    var source: String,
    var description: String
)

data class CommentDto(
    var writer: Long,

    var content: String,
    var layer: Int = 1,
    var children: MutableList<CommentDto> = mutableListOf(),

    var createTime: LocalDateTime
)
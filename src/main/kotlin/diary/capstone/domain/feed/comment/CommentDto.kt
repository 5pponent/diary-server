package diary.capstone.domain.feed.comment

import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserSimpleResponse
import org.springframework.data.domain.Page
import javax.validation.constraints.NotBlank

data class CommentRequestForm(
    @field:NotBlank
    var content: String
)

data class CommentResponse(
    var id: Long?,
    var writer: UserSimpleResponse,
    var content: String,
    var layer: Int,
    var parentId: Long,
    var childCount: Long,
    var likeCount: Long,
    var isLiked: Boolean,
    var createTime: String,
) {
    constructor(comment: Comment, user: User): this(
        id = comment.id,
        writer = UserSimpleResponse(comment.writer, user),
        content = comment.content,
        layer = comment.layer,
        parentId = comment.parent?.let { it.id } ?: 0L,
        childCount = comment.children.size.toLong(),
        likeCount = comment.likes
            .count { it.comment.id == comment.id }.toLong(),
        isLiked = comment.likes
            .any { it.comment.id == comment.id && it.user.id == user.id },
        createTime = comment.createTime,
    )
    constructor(comment: Comment, isFollowed: Boolean, childCount: Long, likeCount: Long, isLiked: Boolean): this(
        id = comment.id,
        writer = UserSimpleResponse(comment.writer, isFollowed),
        content = comment.content,
        layer = comment.layer,
        parentId = comment.parent?.let { it.id } ?: 0L,
        childCount = childCount,
        likeCount = likeCount,
        isLiked = isLiked,
        createTime = comment.createTime,
    )
}

data class CommentPagedResponse(
    var currentPage: Int,
    var totalPages: Int,
    var totalElements: Long,
    var comments: List<CommentResponse>
) {
    constructor(comments: Page<Comment>, user: User): this(
        currentPage = comments.number + 1,
        totalPages = comments.totalPages,
        totalElements = comments.totalElements,
        comments = comments.content
            .map { CommentResponse(it, user) }
    )
    constructor(comments: Page<CommentResponse>): this(
        currentPage = comments.number + 1,
        totalPages = comments.totalPages,
        totalElements = comments.totalElements,
        comments = comments.content
    )
}
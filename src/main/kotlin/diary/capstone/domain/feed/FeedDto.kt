package diary.capstone.domain.feed

import diary.capstone.domain.file.FileResponse
import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserSimpleResponse
import org.springframework.data.domain.Page
import org.springframework.web.multipart.MultipartFile
import java.time.format.DateTimeFormatter
import javax.validation.constraints.Pattern

data class FeedCreateForm(
    var content: String,

    // 이미지와 설명은 일대일 대응, 설명이 없을 경우 빈 문자열로 전송
    var images: List<MultipartFile> = listOf(),
    var descriptions: List<String> = listOf(),

    @field:Pattern(
        regexp = "^($SHOW_ALL|$SHOW_FOLLOWERS|$SHOW_ME)$",
        message = "$SHOW_ALL, $SHOW_FOLLOWERS, $SHOW_ME 로만 입력 가능합니다."
    )
    var showScope: String
)

data class FeedUpdateForm(
    var content: String,

    // 업로드 된 파일 ID 리스트
    var images: List<Long> = listOf(),
    var descriptions: List<String> = listOf(),

    @field:Pattern(
        regexp = "^($SHOW_ALL|$SHOW_FOLLOWERS|$SHOW_ME)$",
        message = "$SHOW_ALL, $SHOW_FOLLOWERS, $SHOW_ME 로만 입력 가능합니다."
    )
    var showScope: String
)

data class FeedResponseM(
    var id: String,
    var writer: UserSimpleResponse,
    var content: String,
    var images: List<ImageDto>,
    var commentCount: Int,
    var likeCount: Int,
    var isLiked: Boolean,
    var showScope: String,
    var createTime: String
) {
    constructor(feed: FeedM, writer: UserSimpleResponse, loginUserId: Long): this(
        id = feed.id.toHexString(),
        writer = writer,
        content = feed.content,
        images = feed.images,
        commentCount = feed.comments.size,
        likeCount = feed.likedUsers.size,
        isLiked = feed.likedUsers.contains(loginUserId),
        showScope = feed.showScope,
        createTime = feed.createTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    )
}

data class FeedResponse(
    var id: Long,
    var writer: UserSimpleResponse,
    var content: String,
    var files: List<FileResponse>,
    var commentCount: Long,
    var likeCount: Long,
    var isLiked: Boolean,
    var showScope: String,
    var createTime: String
) {
    constructor(feed: Feed, user: User): this(
        id = feed.id!!,
        writer = UserSimpleResponse(feed.writer, user),
        content = feed.content,
        files = feed.files
            .sortedBy { it.sequence }
            .map { FileResponse(it) },
        commentCount = feed.comments.size.toLong(),
        likeCount = feed.likes
            .count { it.feed.id == feed.id }.toLong(),
        isLiked = feed.likes // 조회하는 유저의 해당 피드 좋아요 유무
            .any { it.feed.id == feed.id && it.user.id == user.id },
        showScope = feed.showScope,
        createTime = feed.createTime
    )
    constructor(feed: Feed, isFollowed: Boolean, commentCount: Long, likeCount: Long, isLiked: Boolean): this(
        id = feed.id!!,
        writer = UserSimpleResponse(feed.writer, isFollowed),
        content = feed.content,
        files = feed.files
            .sortedBy { it.sequence }
            .map { FileResponse(it) },
        commentCount = commentCount,
        likeCount = likeCount,
        isLiked = isLiked,
        showScope = feed.showScope,
        createTime = feed.createTime
    )
}

data class FeedPagedResponse(
    var currentPage: Int,
    var totalPages: Int,
    var feeds: List<FeedResponse>
) {
    constructor(feeds: Page<FeedResponse>): this(
        currentPage = feeds.number + 1,
        totalPages = feeds.totalPages,
        feeds = feeds.content
    )
}
package diary.capstone.domain.feed

import diary.capstone.auth.Auth
import diary.capstone.domain.feed.comment.CommentPagedResponse
import diary.capstone.domain.feed.comment.CommentRequestForm
import diary.capstone.domain.feed.comment.CommentResponse
import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserPagedResponse
import diary.capstone.domain.user.UserSimpleResponse
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.*
import springfox.documentation.annotations.ApiIgnore
import javax.validation.Valid

@ApiOperation("피드 관련 API")
@Auth
@RestController
@RequestMapping("/feed")
class FeedController(private val feedService: FeedService) {

    @ApiOperation(
        value = "피드 생성",
        notes = "images 와 descriptions 의 수는 같게 하여 요청\n " +
                "한 이미지에 설명글이 없을 경우 해당 images 의 descriptions 는 빈 문자열로 요청"
    )
    @PostMapping
    fun createFeed(@Valid @ModelAttribute form: FeedCreateForm, @ApiIgnore user: User) =
        FeedResponse(feedService.createFeed(form, user), user)

    @ApiOperation(
        value = "피드 목록 조회",
        notes = "* userid: 해당 유저가 작성한 피드 목록 조회\n " +
                "* keyword?: 해당 키워드로 피드 목록 조회\n "
    )
    @GetMapping
    fun getFeeds(
        @RequestParam(name = "userid") userId: Long,
        @RequestParam(name = "keyword", required = false) keyword: String?,
        @RequestParam(name = "lastId", required = false) lastId: Long?,
        @ApiIgnore user: User
    ): List<FeedResponse> = feedService.getFeeds(userId, keyword, user)

    @GetMapping("/all")
    fun getShowAllFeeds(
        @RequestParam(name = "lastId", required = false) lastId: Long?,
        @ApiIgnore user: User
    ): List<FeedResponse> = feedService.getShowAllFeeds(user, lastId)

    @ApiOperation(value = "피드 상세 조회")
    @GetMapping("/{feedId}")
    fun getFeed(@PathVariable("feedId") feedId: Long, @ApiIgnore user: User) =
        FeedResponse(feedService.getFeed(feedId), user)

    @ApiOperation(value = "피드 수정")
    @PutMapping("/{feedId}")
    fun updateFeed(
        @PathVariable("feedId") feedId: Long,
        @Valid @RequestBody form: FeedUpdateForm,
        @ApiIgnore user: User
    ) = FeedResponse(feedService.updateFeed(feedId, form, user), user)

    @ApiOperation(value = "피드 삭제")
    @DeleteMapping("/{feedId}")
    fun deleteFeed(@PathVariable("feedId") feedId: Long, @ApiIgnore user: User) =
        feedService.deleteFeed(feedId, user)
}

@ApiOperation("댓글 관련 API")
@Auth
@RestController
@RequestMapping("/feed/{feedId}/comment")
class CommentController(private val feedService: FeedService) {

    @ApiOperation(value = "루트 댓글 생성")
    @PostMapping
    fun createRootComment(
        @PathVariable("feedId") feedId: Long,
        @Valid @RequestBody form: CommentRequestForm,
        @ApiIgnore user: User
    ) = CommentResponse(feedService.createRootComment(feedId, form, user), user)

    @ApiOperation(value = "대댓글 생성")
    @PostMapping("/{commentId}")
    fun createChildComment(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @Valid @RequestBody form: CommentRequestForm,
        @ApiIgnore user: User
    ) = CommentResponse(feedService.createChildComment(feedId, commentId, form, user), user)

    @ApiOperation(value = "해당 피드의 루트 댓글만 조회",)
    @GetMapping
    fun getRootComments(
        @PathVariable("feedId") feedId: Long,
        @RequestParam(name = "lastId", required = false) lastId: Long?,
        @ApiIgnore loginUser: User
    ): List<CommentResponse> = feedService.getRootComments(feedId, loginUser, lastId)

    @ApiOperation(value = "해당 댓글의 대댓글 목록 조회")
    @GetMapping("/{commentId}")
    fun getChildComments(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @RequestParam(name = "lastId", required = false) lastId: Long?,
        @ApiIgnore user: User
    ): List<CommentResponse> = feedService.getChildComments(feedId, commentId, user, lastId)

    @ApiOperation(value = "댓글 수정")
    @PutMapping("/{commentId}")
    fun updateComment(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @Valid @RequestBody form: CommentRequestForm,
        @ApiIgnore user: User
    ) = CommentResponse(feedService.updateComment(feedId, commentId, form, user), user)

    @ApiOperation(value = "댓글 삭제")
    @DeleteMapping("/{commentId}")
    fun deleteComment(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @ApiIgnore user: User
    ) = feedService.deleteComment(feedId, commentId, user)
}

@ApiOperation("피드 좋아요 관련 API")
@Auth
@RestController
@RequestMapping("/feed/{feedId}/like")
class FeedLikeController(private val feedService: FeedService) {

    @ApiOperation(value = "해당 피드를 좋아요한 유저 목록")
    @GetMapping
    fun getFeedLikeUsers(
        @PathVariable("feedId") feedId: Long,
        @RequestParam(name = "lastId", required = false) lastId: Long?,
        @ApiIgnore user: User
    ): List<UserSimpleResponse> = feedService.getFeedLikes(feedId, user, lastId)

    @ApiOperation(value = "해당 피드 좋아요 등록")
    @PostMapping
    fun likeFeed(@PathVariable("feedId") feedId: Long, @ApiIgnore user: User) =
        FeedResponse(feedService.likeFeed(feedId, user), user)

    @ApiOperation(value = "해당 피드 좋아요 취소")
    @DeleteMapping
    fun cancelLikeFeed(@PathVariable("feedId") feedId: Long, @ApiIgnore user: User) =
        FeedResponse(feedService.cancelLikeFeed(feedId, user), user)
}

@ApiOperation("댓글 좋아요 관런 API")
@Auth
@RestController
@RequestMapping("/feed/{feedId}/comment/{commentId}/like")
class CommentLikeController(private val feedService: FeedService) {

    @ApiOperation(value = "해당 댓글 좋아요 등록")
    @PostMapping
    fun likeComment(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @ApiIgnore user: User
    ) = CommentResponse(feedService.likeComment(feedId, commentId, user), user)

    @ApiOperation(value = "해당 댓글 좋아요 취소")
    @DeleteMapping
    fun cancelLikeComment(
        @PathVariable("feedId") feedId: Long,
        @PathVariable("commentId") commentId: Long,
        @ApiIgnore user: User
    ) = CommentResponse(feedService.cancelLikeComment(feedId, commentId, user), user)
}
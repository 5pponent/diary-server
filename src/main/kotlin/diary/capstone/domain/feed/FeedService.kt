package diary.capstone.domain.feed

import diary.capstone.domain.feed.comment.Comment
import diary.capstone.domain.feed.comment.CommentLike
import diary.capstone.domain.feed.comment.CommentRequestForm
import diary.capstone.domain.file.FileService
import diary.capstone.domain.notice.NoticeRequest
import diary.capstone.domain.notice.NoticeService
import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserService
import diary.capstone.util.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional
class FeedService(
    private val feedRepository: FeedRepository,
    private val qFeedRepository: QFeedRepository,
    private val userService: UserService,
    private val fileService: FileService,
    private val noticeService: NoticeService
) {

    // 피드의 사진을 저장
    private fun setAndSaveFiles(feed: Feed, images: List<MultipartFile>, descriptions: List<String>) {
        if (images.size == descriptions.size) {
            feed.files.forEach { fileService.deleteFile(it) }
            // 이미지 파일들과 설명들을 합쳐서 하나의 리스트로 묶고 순회
            var seq = 1
            images.zip(descriptions) { file, desc ->
                feed.files.add(fileService.uploadFile(file, desc).setFeed(feed).setSequence(seq++))
            }
        } else throw FeedException("$INVALID_FEED_FORM 파일: ${images.size} / 설명: ${descriptions.size}")
    }

    // 피드 작성
    fun createFeed(form: FeedCreateForm, loginUser: User): Feed =
        feedRepository.save(
            Feed(
                writer = loginUser,
                content = form.content,
                showScope = form.showScope
            )
        ).let {
            setAndSaveFiles(it, form.images, form.descriptions)
            
            // 작성자의 팔로워들에게 알림 전송
            noticeService.sendNotices(
                loginUser.follower.map { follow ->
                    NoticeRequest(
                        receiver = follow.user.id!!,
                        type = "FEED/${it.id}",
                        content = "${loginUser.name}|님이 새로운 피드를 등록했습니다."
                    )
                }
            )
            it
        }

    // 전체 공개 피드 조회
    @Transactional(readOnly = true)
    fun getShowAllFeeds(lastFeedId: Long?, loginUser: User) =
        qFeedRepository.findFeedsByShowScope(SHOW_ALL, loginUser, lastFeedId)

    // 피드 목록 검색 (페이징)
    @Transactional(readOnly = true)
    fun getFeeds(pageable: Pageable, userId: Long, keyword: String?, loginUser: User): Page<FeedResponse> =
        keyword?.let {
            qFeedRepository.findFeedsByUserIdAndKeyword(pageable, userId, keyword, loginUser)
        } ?: qFeedRepository.findFeedsByUserId(pageable, userId, loginUser)

    // 피드 하나의 상세 정보
    @Transactional(readOnly = true)
    fun getFeed(feedId: Long): Feed =
        feedRepository.findById(feedId).orElseThrow { throw FeedException(FEED_NOT_FOUND) }

    @Transactional(readOnly = true)
    fun getFeedLikes(pageable: Pageable, feedId: Long): List<User> =
        getFeed(feedId).let { feed -> feed.likes.map { it.user } }

    // 피드 좋아요 등록
    fun likeFeed(feedId: Long, loginUser: User): Feed =
        getFeed(feedId).let { feed ->
            if (feed.likes.none { it.user.id == loginUser.id })
                feed.likes.add(FeedLike(feed = feed, user = loginUser))
            else throw FeedLikeException(ALREADY_LIKED_FEED)

            if (feed.writer.id != loginUser.id) {
                noticeService.sendNotice(
                    NoticeRequest(
                        receiver = feed.writer.id!!,
                        type = "FEED/${feed.id}",
                        content = "${loginUser.name}|님이 내 피드에 좋아요를 눌렀습니다."
                    )
                )
            }

            feed
        }

    // 피드 좋아요 취소
    fun cancelLikeFeed(feedId: Long, loginUser: User): Feed =
        getFeed(feedId).let { feed ->
            feed.likes.remove(
                feed.likes.find { it.user.id == loginUser.id }
            )
            feed
        }

    // 피드 수정
    fun updateFeed(feedId: Long, form: FeedUpdateForm, loginUser: User): Feed =
        getFeed(feedId).let { feed ->
            feedPermissionCheck(feed, loginUser)
            if (form.images.size != form.descriptions.size)
                throw FeedException("$INVALID_FEED_FORM 파일: ${form.images.size} / 설명: ${form.descriptions.size}")

            feed.update(form.content, form.showScope)

            // 업데이트된 파일 중 존재하지 않는 기존 파일 삭제
            feed.files.forEach {
                if (!form.images.contains(it.id)) fileService.deleteFile(it)
            }

            // 기존 피드 파일 비우기
            feed.files.clear()

            // 받은 파일 순서대로 피드 파일에 추가
            var seq = 1
            form.images.zip(form.descriptions) { imageId, desc ->
                var image = fileService.getFile(imageId).updateDesc(desc).setSequence(seq++)
                feed.files.add(image.setFeed(feed))
            }

            feed
        }

    // 피드 삭제
    fun deleteFeed(feedId: Long, loginUser: User) =
        getFeed(feedId).let { feed ->
            feedPermissionCheck(feed, loginUser)
            feed.files.forEach { fileService.deleteFile(it) }
            feedRepository.delete(feed)
        }

    // 피드 접근 권한 체크
    private fun feedPermissionCheck(feed: Feed, loginUser: User) {
        if (feed.writer.id != loginUser.id) throw FeedException(FEED_ACCESS_DENIED)
    }

    private fun getComment(feedId: Long, commentId: Long): Comment =
        getFeed(feedId).comments
            .find { it.id == commentId } ?: throw CommentException(COMMENT_NOT_FOUND)

    // 새 루트 댓글 생성
    fun createRootComment(feedId: Long, form: CommentRequestForm, loginUser: User): Comment =
        getFeed(feedId).let { feed ->
            val comment = Comment(
                feed = feed,
                writer = loginUser,
                content = form.content
            )
            feed.comments.add(comment)

            // 피드 작성자에게 알림 전송(본인이 작성한 피드가 아닐 때)
            if (loginUser.id != feed.writer.id) {
                noticeService.sendNotice(
                    NoticeRequest(
                        receiver = feed.writer.id!!,
                        type = "FEED/${feed.id}",
                        content = "${loginUser.name}|님이 내 피드에 댓글을 작성했습니다."
                    )
                )
            }

            comment
        }

    // 대댓글 생성
    fun createChildComment(feedId: Long, parentId: Long, form: CommentRequestForm, loginUser: User): Comment =
        getFeed(feedId).let { feed ->
            getComment(feedId, parentId).let { parentComment ->
                val comment = Comment(
                    feed = feed,
                    writer = loginUser,
                    content = form.content,
                    parent = parentComment,
                    layer = parentComment.layer + 1
                )
                parentComment.children.add(comment)
                
                // 피드 작성자와 작성한 대댓글의 바로 상위 부모 댓글 작성자에게 알림 전송
                if (feed.writer.id != loginUser.id) {
                    noticeService.sendNotice(
                        NoticeRequest(
                            receiver = feed.writer.id!!,
                            type = "FEED/${feed.id}",
                            content = "${loginUser.name}|님이 내 피드에 답글을 작성했습니다."
                        )
                    )
                }
                if (comment.parent!!.writer.id != loginUser.id) {
                    noticeService.sendNotice(
                        NoticeRequest(
                            receiver = comment.parent!!.writer.id!!,
                            type = "FEED/${feed.id}",
                            content = "${loginUser.name}|님이 내 댓글에 답글을 작성했습니다."
                        )
                    )
                }
                
                comment
            }
        }

    /**
     * 댓글 페이징 조회
     * id 오름차순으로 정렬 (등록일이 빠른 댓글이 위로)
     */
    // 모든 루트 댓글 조회 (부모 댓글 x, 로그인한 유저가 쓴 댓글 제외)
    @Transactional(readOnly = true)
    fun getRootComments(feedId: Long, pageable: Pageable, loginUser: User): Page<Comment> =
        getPagedObject(pageable,
            getFeed(feedId).comments
                .filterRootComments()
                .filterNotSpecificUserComments(loginUser.id!!)
                .sortedByDescending { it.id }
        )

    // 해당 피드의 내가 쓴 루트 댓글만 조회 (부모 댓글 x, 로그인한 유저가 쓴 댓글만)
    @Transactional(readOnly = true)
    fun getMyComments(feedId: Long, pageable: Pageable, loginUser: User): Page<Comment> =
        getPagedObject(pageable,
            getFeed(feedId).comments
                .filterRootComments()
                .filterSpecificUserComments(loginUser.id!!)
                .sortedBy { it.id }
        )

    // 해당 댓글의 대댓글들 조회 (해당 댓글의 자식 댓글들만)
    @Transactional(readOnly = true)
    fun getChildComments(feedId: Long, commentId: Long, pageable: Pageable): Page<Comment> =
        getPagedObject(pageable,
            getFeed(feedId).comments
                .filterChildComments(commentId)
                .sortedBy { it.id }
        )

    // 댓글 수정
    fun updateComment(feedId: Long, commentId: Long, form: CommentRequestForm, loginUser: User): Comment =
        getComment(feedId, commentId).let {
            commentPermissionCheck(it, loginUser)
            it.update(form.content)
        }

    // 댓글 삭제
    fun deleteComment(feedId: Long, commentId: Long, loginUser: User) =
        getFeed(feedId).let {
            getComment(feedId, commentId).let { comment ->
                commentPermissionCheck(comment, loginUser)
                it.comments.remove(comment)
            }
        }

    // 댓글 좋아요 등록
    fun likeComment(feedId: Long, commentId: Long, loginUser: User): Comment =
        getComment(feedId, commentId).let { comment ->
            if (comment.likes.none { it.user.id == loginUser.id })
                comment.likes.add(CommentLike(user = loginUser, comment = comment))
            else throw CommentLikeException(ALREADY_LIKED_COMMENT)
            comment
        }

    // 댓글 좋아요 취소
    fun cancelLikeComment(feedId: Long, commentId: Long, loginUser: User): Comment =
        getComment(feedId, commentId).let { comment ->
            comment.likes.remove(
                comment.likes.find { it.user.id == loginUser.id }
            )
            comment
        }

    // 댓글 접근 권한 체크
    private fun commentPermissionCheck(comment: Comment, loginUser: User) {
        if (comment.writer.id != loginUser.id) throw CommentException(COMMENT_ACCESS_DENIED)
    }
}
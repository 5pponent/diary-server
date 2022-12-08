package diary.capstone.domain.feed

import com.querydsl.jpa.impl.JPAQueryFactory
import diary.capstone.domain.feed.comment.CommentResponse
import diary.capstone.domain.user.UserService
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@SpringBootTest
@Transactional
internal class FeedServiceTest {

    @Autowired lateinit var userService: UserService
    @Autowired lateinit var qFeedRepository: QFeedRepository
    @Autowired lateinit var jpaQueryFactory: JPAQueryFactory
    @Autowired lateinit var feedRepository: FeedRepository
    @Autowired lateinit var feedRepositoryM: FeedRepositoryM

    /**
     * - files, likes, comments 가 해당 엔티티.feed_id IN (result 피드 id 목록 pageSize 만큼) Batch 쿼리로 처리
     * - isFollowed 를 얻기 위해 로그인 유저의 following 정보를 불러오는 데에 일반 쿼리 처리
     * - select Follow from Follow following where following.user_id = 로그인 유저 ID;
     * - 피드 페이징 목록: 2(페이징) + 1(following) + 1(files) + 1(likes) + 1(comments) = 6개 쿼리가 실행
     */
    private fun feedTestResult(feedList: Page<FeedResponse>) {
        println("currentPage: " + (feedList.number + 1))
        println("totalPages: " + feedList.totalPages)
        println("totalElements: " + feedList.totalElements)
        feedList.content.forEach {
            println("Feed (${it.id}) =====================================================")
            println("files: ${it.files.map { file -> file.id }}")
            println("likeCount: ${it.likeCount}")
            println("isLiked: ${it.isLiked}")
            println("commentCount: ${it.commentCount}")
            println("isFollowed = ${it.isFollowed}")
        }
    }

    /**
     * - children: children.parent_id IN (result 댓글 id 목록 pageSize 만큼) Batch
     * - likes: likes.comment_id IN (result 댓글 id 목록 pageSize 만큼) Batch
     * - isFollowed 를 얻기 위해 로그인 유저의 following 정보를 불러오는 데에 일반 쿼리 처리
     * - select Follow from Follow following where following.user_id = 로그인 유저 ID;
     * - 댓글 페이징 목록: 2(페이징) + 1(following) + 1(children) + 1(likes) = 5개 쿼리 실행
     */
    private fun commentTestResult(commentList: Page<CommentResponse>) {
        println("currentPage: " + (commentList.number + 1))
        println("totalPages: " + commentList.totalPages)
        println("totalElements: " + commentList.totalElements)
        commentList.content.forEach {
            println("Comment (${it.id}) =====================================================")
            println("${it.content}")
            println("childCount = ${it.childCount}")
            println("isFollowed = ${it.isFollowed}")
            println("isLiked = ${it.isLiked}")
            println("likeCount = ${it.likeCount}")
            println("parentId = ${it.parentId}")
        }
    }

    @Test @DisplayName("특정 유저의 피드 페이징 조회")
    fun getUserFeeds() {
        val loginUser = userService.getUserById(1L)
        val pageable = PageRequest.of(0, 10)
        val result = qFeedRepository.findFeedsByUserId(pageable, 6L, loginUser)

        feedTestResult(result)
    }

    @Test @DisplayName("전체 공개 피드 모두 조회")
    fun getFeedsAll() {
        val loginUser = userService.getUserById(1L)
        val pageable = PageRequest.of(0, 10)
        val result = qFeedRepository.findFeedsByShowScope(pageable, SHOW_ALL, loginUser)

        feedTestResult(result)
    }

    @Test @DisplayName("피드 검색")
    fun searchFeedsByUserAndKeyword() {
        val loginUser = userService.getUserById(1L)
        val keyword = "저"
        val pageable = PageRequest.of(0, 2)
        val result = qFeedRepository.findFeedsByUserIdAndKeyword(pageable, loginUser.id!!, keyword, loginUser)

        feedTestResult(result)
    }

    @Test @DisplayName("피드 좋아요 목록 조회")
    fun getFeedLikes() {
        val loginUser = userService.getUserById(1L)
        val feedId = 1L
        val pageable = PageRequest.of(0, 3)
        val likedUsers = qFeedRepository.findFeedLikeUsers(pageable, feedId, loginUser)
        println("피드 좋아요 수: ${likedUsers.totalElements}")

        // FeedLike 페이징 쿼리 2개 + 로그인 유저의 following 쿼리 1개 = 3개 쿼리 실행
        likedUsers.forEach { println(it.name + ": " + it.image?.source) }
    }
    
    @Test @DisplayName("루트 댓글 목록 조회")
    fun getRootComments() {
        val loginUser = userService.getUserById(1L)
        val feedId = 193L
        val pageable = PageRequest.of(0, 10)
        val result = qFeedRepository.findRootCommentsByFeedId(pageable, feedId, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("특정 유저 루트 댓글 목록 조회")
    fun getMyComments() {
        val loginUser = userService.getUserById(1L)
        val feedId = 139L
        val pageable = PageRequest.of(0, 1)
        val result = qFeedRepository.findRootCommentsByFeedIdAndUserId(pageable, feedId, loginUser.id!!, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("대댓글 목록 조회")
    fun getChildComments() {
        val loginUser = userService.getUserById(1L)
        val commentId = 224L
        val pageable = PageRequest.of(0, 1)
        val result = qFeedRepository.findChildCommentsByParentCommentId(pageable, commentId, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("댓글 좋아요 수 조회")
    fun getCommentLikes() {
        val commentId = 226L

        val result = qFeedRepository.getCommentLikeCount(commentId)
        println("$commentId LikeCount: $result")
    }

    @Test
    fun feedMigration() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        feedRepositoryM.deleteAll()
        val feeds = feedRepository.findAll()
        feeds.forEach { feed ->
            feedRepositoryM.save(
                FeedM(
                    id = ObjectId(Timestamp.valueOf(LocalDateTime.parse(feed.createTime, formatter))),
                    writer = feed.writer.id!!,
                    content = feed.content,
                    likedUsers = feed.likes.map { it.user.id!! }.toMutableList(),
                    comments = feed.comments.map { c1 ->
                        CommentM(
                            c1.writer.id!!,
                            c1.content,
                            c1.layer,
                            c1.children.map { c2 -> CommentM(
                                c2.writer.id!!,
                                c2.content,
                                c2.layer,
                                c2.children.map { c3 -> CommentM(
                                    c3.writer.id!!,
                                    c3.content,
                                    c3.layer,
                                    c3.children.map { c4 -> CommentM(
                                        c4.writer.id!!,
                                        c4.content,
                                        c4.layer,
                                        c4.children.map { c5 -> CommentM(
                                            c5.writer.id!!,
                                            c5.content,
                                            c5.layer,
                                            mutableListOf(),
                                            LocalDateTime.parse(c5.createTime, formatter)
                                        ) }.toMutableList(),
                                        LocalDateTime.parse(c4.createTime, formatter)
                                    ) }.toMutableList(),
                                    LocalDateTime.parse(c3.createTime, formatter)
                                ) }.toMutableList(),
                                LocalDateTime.parse(c2.createTime, formatter)
                            ) }.toMutableList(),
                            LocalDateTime.parse(c1.createTime, formatter)
                        )
                    }.toMutableList(),
                    images = feed.files.map { FileM(it.originalName, it.source, it.description) }.toMutableList(),
                    showScope = feed.showScope,
                    createTime = LocalDateTime.parse(feed.createTime, formatter),
                )
            )
        }
    }
}
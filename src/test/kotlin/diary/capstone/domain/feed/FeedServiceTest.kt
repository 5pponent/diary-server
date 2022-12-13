package diary.capstone.domain.feed

import com.querydsl.jpa.impl.JPAQueryFactory
import diary.capstone.domain.feed.comment.CommentResponse
import diary.capstone.domain.user.UserService
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.*

@SpringBootTest
@Transactional
internal class FeedServiceTest {

    @Autowired lateinit var userService: UserService
    @Autowired lateinit var feedService: FeedService
    @Autowired lateinit var qFeedRepository: QFeedRepository
    @Autowired lateinit var jpaQueryFactory: JPAQueryFactory

    /**
     * - files, likes, comments 가 해당 엔티티.feed_id IN (result 피드 id 목록 pageSize 만큼) Batch 쿼리로 처리
     * - isFollowed 를 얻기 위해 로그인 유저의 following 정보를 불러오는 데에 일반 쿼리 처리
     * - select Follow from Follow following where following.user_id = 로그인 유저 ID;
     * - 피드 페이징 목록: 2(페이징) + 1(following) + 1(files) + 1(likes) + 1(comments) = 6개 쿼리가 실행
     */
    private fun feedTestResult(feedList: List<FeedResponse>) {
        feedList.forEach {
            println("Feed (${it.id}) =====================================================")
            println("files: ${it.files.map { file -> file.id }}")
            println("likeCount: ${it.likeCount}")
            println("isLiked: ${it.isLiked}")
            println("commentCount: ${it.commentCount}")
        }
    }

    /**
     * - children: children.parent_id IN (result 댓글 id 목록 pageSize 만큼) Batch
     * - likes: likes.comment_id IN (result 댓글 id 목록 pageSize 만큼) Batch
     * - isFollowed 를 얻기 위해 로그인 유저의 following 정보를 불러오는 데에 일반 쿼리 처리
     * - select Follow from Follow following where following.user_id = 로그인 유저 ID;
     * - 댓글 페이징 목록: 2(페이징) + 1(following) + 1(children) + 1(likes) = 5개 쿼리 실행
     */
    private fun commentTestResult(commentList: List<CommentResponse>) {
        commentList.forEach {
            println("Comment (${it.id}) =====================================================")
            println(it.content)
            println("childCount = ${it.childCount}")
            println("isLiked = ${it.isLiked}")
            println("likeCount = ${it.likeCount}")
            println("parentId = ${it.parentId}")
        }
    }

    @Test @DisplayName("특정 유저의 피드 페이징 조회")
    fun getUserFeeds() {
        val loginUser = userService.getUserById(1L)
        val result = qFeedRepository.findFeedsByUserId(6L, loginUser)

        feedTestResult(result)
    }

    @Test @DisplayName("전체 공개 피드 모두 조회 - NoOffset")
    fun getFeedsAll() {
        val loginUser = userService.getUserById(6L)
        val result = qFeedRepository.findShowAllFeeds(loginUser)
        println("${result.size} rows paged")
        result.forEach { println("${it.id} | ${it.writer}") }
    }

    @Test @DisplayName("피드 검색")
    fun searchFeedsByUserAndKeyword() {
        val loginUser = userService.getUserById(1L)
        val keyword = "저"
        val result = qFeedRepository.findFeedsByUserIdAndKeyword(loginUser.id!!, keyword, loginUser)

        feedTestResult(result)
    }

    @Test @DisplayName("피드 좋아요 목록 조회")
    fun getFeedLikes() {
        val loginUser = userService.getUserById(1L)
        val feedId = 1L
        val likedUsers = qFeedRepository.findFeedLikeUsers(feedId, loginUser)

        // FeedLike 페이징 쿼리 2개 + 로그인 유저의 following 쿼리 1개 = 3개 쿼리 실행
        likedUsers.forEach { println(it.name + ": " + it.image?.source) }
    }
    
    @Test @DisplayName("루트 댓글 목록 조회")
    fun getRootComments() {
        val loginUser = userService.getUserById(1L)
        val feedId = 193L
        val result = qFeedRepository.findRootCommentsByFeedId(feedId, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("특정 유저 루트 댓글 목록 조회")
    fun getMyComments() {
        val loginUser = userService.getUserById(1L)
        val feedId = 139L
        val result = qFeedRepository.findRootCommentsByFeedIdAndUserId(feedId, loginUser.id!!, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("대댓글 목록 조회")
    fun getChildComments() {
        val loginUser = userService.getUserById(1L)
        val commentId = 224L
        val result = qFeedRepository.findChildCommentsByParentCommentId(commentId, loginUser)

        commentTestResult(result)
    }

    @Test @DisplayName("댓글 좋아요 수 조회")
    fun getCommentLikes() {
        val commentId = 226L

        val result = qFeedRepository.getCommentLikeCount(commentId)
        println("$commentId LikeCount: $result")
    }
}
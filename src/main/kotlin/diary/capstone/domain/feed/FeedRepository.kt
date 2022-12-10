package diary.capstone.domain.feed

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import diary.capstone.config.FEED_PAGE_SIZE
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import diary.capstone.domain.feed.QFeed.feed
import diary.capstone.domain.feed.QFeedLike.feedLike
import diary.capstone.domain.feed.comment.CommentResponse
import diary.capstone.domain.feed.comment.QComment.comment
import diary.capstone.domain.feed.comment.QCommentLike.commentLike
import diary.capstone.domain.user.QUser.user
import diary.capstone.domain.file.QFile.file
import diary.capstone.domain.user.QFollow.follow
import diary.capstone.domain.user.QUserRepository
import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserSimpleResponse
import diary.capstone.util.getPagedObject

interface FeedRepository: JpaRepository<Feed, Long> {
    fun findByShowScope(pageable: Pageable, showScope: String): Page<Feed>
}

data class FeedInfo(
    var commentCount: Long,
    var likeCount: Long,
    var isLiked: Boolean,
    var isFollowed: Boolean? = null
)

@Repository
class QFeedRepository(
    private val jpaQueryFactory: JPAQueryFactory,
    private val qUserRepository: QUserRepository
) {

    /**
     * @param loginUserId 로그인한 유저 Id
     * @return 피드의 댓글, 좋아요 개수 및 좋아요 여부 반환
     */
    private fun getFeedInfos(loginUserId: Long, feedIds: List<Long>, writerIds: List<Long>? = null): List<FeedInfo> {
        val commentCount = jpaQueryFactory.select(comment.id.count()).from(feed)
            .leftJoin(comment).on(feed.id.eq(comment.feed.id))
            .groupBy(feed.id).having(feed.id.`in`(feedIds))
            .orderBy(feed.id.desc())
            .fetch()

        val likeCount = jpaQueryFactory.select(feedLike.count()).from(feed)
            .leftJoin(feedLike).on(feed.id.eq(feedLike.feed.id))
            .groupBy(feed.id).having(feed.id.`in`(feedIds))
            .orderBy(feed.id.desc())
            .fetch()

        val likedFeeds = jpaQueryFactory.selectFrom(feedLike)
            .where(feedLike.user.id.eq(loginUserId), feedLike.feed.id.`in`(feedIds)).fetch()
            .map { it.feed.id }.toList()

        val followedUsers = writerIds?.let {
            jpaQueryFactory.selectFrom(follow)
                .where(follow.user.id.eq(loginUserId), follow.target.id.`in`(writerIds)).fetch()
                .map { it.target.id }.toList()
        }

        val feedInfos = mutableListOf<FeedInfo>()
        var idx = 0
        feedIds.forEach { feedId ->
            feedInfos.add(FeedInfo(commentCount[idx], likeCount[idx], likedFeeds.contains(feedId)))
            idx++
        }
        writerIds?.zip(feedInfos) { writerId, feedInfo ->
            feedInfo.isFollowed = followedUsers!!.contains(writerId)
        }
        return feedInfos
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @param showScope const [SHOW_ALL | SHOW_FOLLOWERS | SHOW_ME]
     * @return 해당 공개 범위의 피드 리스트(NoOffset 페이징)
     */
    fun findFeedsByShowScope(showScope: String, loginUser: User, lastFeedId: Long? = null): List<FeedResponse> {
        val feeds = jpaQueryFactory
            .selectFrom(feed).distinct()
            .leftJoin(feed.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(
                lastFeedId?.let { feed.id.lt(it) },
                feed.showScope.eq(showScope)
            )
            .orderBy(feed.id.desc())
            .limit(FEED_PAGE_SIZE.toLong())
            .fetch()

        val feedInfos = getFeedInfos(loginUser.id!!, feeds.map { it.id!! }, feeds.map { it.writer.id!! })

        return feeds.zip(feedInfos) { feed, feedInfo ->
            FeedResponse(feed, feedInfo.isFollowed!!, feedInfo.commentCount, feedInfo.likeCount, feedInfo.isLiked)
        }
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 유저의 피드 페이징 목록, Feed.id DESC
     */
    fun findFeedsByUserId(pageable: Pageable, userId: Long, loginUser: User): Page<FeedResponse> {
        val isFollowed = if (loginUser.id == userId) true else qUserRepository.getIsFollowed(loginUser.id!!, userId)

        val expr = feed.writer.id.eq(userId).and(
            feed.showScope.eq(SHOW_ALL).or(
                if (isFollowed) feed.showScope.eq(SHOW_FOLLOWERS) else null
            ).or(
                if (loginUser.id == userId) feed.showScope.eq(SHOW_ME) else null
            )
        )

        val feeds = jpaQueryFactory
            .selectFrom(feed).distinct()
            .leftJoin(feed.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(feed.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val totalSize = jpaQueryFactory
            .select(feed.count()).from(feed).where(expr).fetchOne()

        val feedInfos = getFeedInfos(loginUser.id!!, feeds.map { it.id!! })

        return getPagedObject(
            pageable,
            feeds.zip(feedInfos) { feed, feedInfo ->
                FeedResponse(feed, isFollowed, feedInfo.commentCount, feedInfo.likeCount, feedInfo.isLiked)
            },
            totalSize!!
        )
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @param pageable
     * @param userId
     * @param keyword 피드 내용 또는 파일 설명 검색 키워드
     * @return 검색 결과 피드 페이징 목록, Feed.id DESC
     */
    fun findFeedsByUserIdAndKeyword(pageable: Pageable, userId: Long, keyword: String, loginUser: User): Page<FeedResponse> {
        val isFollowed = qUserRepository.getIsFollowed(loginUser.id!!, userId)

        val expr = feed.writer.id.eq(userId).and(
            file.description.like("%$keyword%").or(feed.content.like("%$keyword%"))
        ).and(
            feed.showScope.eq(SHOW_ALL).or(
                if (isFollowed) feed.showScope.eq(SHOW_FOLLOWERS) else null
            ).or(
                if (loginUser.id == userId) feed.showScope.eq(SHOW_ME) else null
            )
        )

        val feeds = jpaQueryFactory
            .selectFrom(feed).distinct()
            .leftJoin(feed.files, file)
            .leftJoin(feed.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(feed.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val totalSize = jpaQueryFactory
            .select(feed.count()).from(feed)
            .leftJoin(feed.files, file)
            .where(expr)
            .fetchOne()

        val feedInfos = getFeedInfos(userId, feeds.map { it.id!! })

        return getPagedObject(
            pageable,
            feeds.zip(feedInfos) { feed, feedInfo ->
                FeedResponse(feed, isFollowed, feedInfo.commentCount, feedInfo.likeCount, feedInfo.isLiked)
            },
            totalSize!!
        )
    }

    /**
     * @return 해당 피드의 좋아요 수(Long)
     */
    fun getFeedLikeCount(feedId: Long): Long = jpaQueryFactory
        .select(feedLike.count()).from(feedLike).where(feedLike.feed.id.eq(feedId)).fetchFirst()

    /**
     * @return 해당 피드의 좋아요 한 유저 페이징 목록, FeedLike.id ASC
     */
    fun findFeedLikeUsers(pageable: Pageable, feedId: Long, loginUser: User): Page<UserSimpleResponse> {
        val likedUsers = jpaQueryFactory
            .selectFrom(feedLike).distinct()
            .leftJoin(feedLike.user, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(feedLike.feed.id.eq(feedId))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
            .map { it.user }

        return getPagedObject(pageable, likedUsers.map { UserSimpleResponse(it, loginUser) }, getFeedLikeCount(feedId))
    }

    /**
     * @param expr QueryDSL where 절에 들어갈 표현식
     * @return 해당 expr 에 대한 댓글 수(Long)
     */
    fun getCommentCount(expr: Predicate): Long = jpaQueryFactory
        .select(comment.count()).from(comment).where(expr).fetchOne()!!

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 피드의 루트 댓글 페이징 목록, Comment.id DESC
     */
    fun findRootCommentsByFeedId(pageable: Pageable, feedId: Long, loginUser: User): Page<CommentResponse> {
        val expr = comment.feed.id.eq(feedId).and(comment.parent.isNull)

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(comment.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return getPagedObject(pageable, comments.map { CommentResponse(it, loginUser) }, getCommentCount(expr))
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 피드의 특정 유저의 루트 댓글 페이징 목록, Comment.id DESC
     */
    fun findRootCommentsByFeedIdAndUserId(pageable: Pageable, feedId: Long, userId: Long, loginUser: User): Page<CommentResponse> {
        val expr = comment.feed.id.eq(feedId).and(comment.parent.isNull).and(comment.writer.id.eq(userId))

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(comment.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return getPagedObject(pageable, comments.map { CommentResponse(it, loginUser) }, getCommentCount(expr))
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 댓글의 대댓글 페이징 목록, Comment.id DESC
     */
    fun findChildCommentsByParentCommentId(pageable: Pageable, commentId: Long, loginUser: User): Page<CommentResponse> {
        val expr = comment.parent.id.eq(commentId)

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr)
            .orderBy(comment.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val totalSize = getCommentCount(expr)

        return getPagedObject(pageable, comments.map { CommentResponse(it, loginUser) }, totalSize)
    }

    /**
     * @return 해당 댓글의 좋아요 수(Long)
     */
    fun getCommentLikeCount(commentId: Long): Long = jpaQueryFactory
        .select(commentLike.count()).from(commentLike).where(commentLike.comment.id.eq(commentId)).fetchFirst()
}
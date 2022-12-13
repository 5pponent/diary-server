package diary.capstone.domain.feed

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import diary.capstone.config.COMMENT_PAGE_SIZE
import diary.capstone.config.FEED_LIKE_PAGE_SIZE
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
import diary.capstone.domain.user.QUserRepository
import diary.capstone.domain.user.User
import diary.capstone.domain.user.UserSimpleResponse
import diary.capstone.util.getPagedObject

interface FeedRepository: JpaRepository<Feed, Long> {
}

data class FeedInfo(
    var commentCount: Long,
    var likeCount: Long,
    var isLiked: Boolean,
    var isFollowed: Boolean? = null
)

data class CommentInfo(
    var childCount: Long,
    var likeCount: Long,
    var isLiked: Boolean,
    var isFollowed: Boolean
)

@Repository
class QFeedRepository(
    private val jpaQueryFactory: JPAQueryFactory,
    private val qUserRepository: QUserRepository
) {

    /**
     * @return 피드의 댓글, 좋아요 개수, 좋아요 여부, 작성자 팔로우 여부 반환
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
            .map { it.feed.id!! }

        val followedUsers = writerIds?.let {
            qUserRepository.getIsFollowed(loginUserId, writerIds)
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
     * @return 공개 범위가 모두 공개인 피드 목록, Feed.id DESC
     */
    fun findShowAllFeeds(loginUser: User, lastId: Long? = null): List<FeedResponse> {
        val feeds = jpaQueryFactory
            .selectFrom(feed).distinct()
            .leftJoin(feed.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(
                lastId?.let { feed.id.lt(it) },
                feed.showScope.eq(SHOW_ALL)
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
     * @return 해당 유저가 작성한 피드 목록, Feed.id DESC
     */
    fun findFeedsByUserId(userId: Long, loginUser: User, lastId: Long? = null): List<FeedResponse> {
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
            .where(expr, lastId?.let { feed.id.lt(it) })
            .orderBy(feed.id.desc())
            .limit(FEED_PAGE_SIZE.toLong())
            .fetch()

        val feedInfos = getFeedInfos(loginUser.id!!, feeds.map { it.id!! })

        return feeds.zip(feedInfos) { feed, feedInfo ->
            FeedResponse(feed, isFollowed, feedInfo.commentCount, feedInfo.likeCount, feedInfo.isLiked)
        }
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 검색 결과 피드 목록, Feed.id DESC
     */
    fun findFeedsByUserIdAndKeyword(userId: Long, keyword: String, loginUser: User, lastId: Long? = null): List<FeedResponse> {
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
            .where(expr, lastId?.let { feed.id.lt(it) })
            .orderBy(feed.id.desc())
            .limit(FEED_PAGE_SIZE.toLong())
            .fetch()

        val feedInfos = getFeedInfos(userId, feeds.map { it.id!! })

        return feeds.zip(feedInfos) { feed, feedInfo ->
            FeedResponse(feed, isFollowed, feedInfo.commentCount, feedInfo.likeCount, feedInfo.isLiked)
        }
    }

    /**
     * @return 해당 피드의 좋아요 수(Long)
     */
    fun getFeedLikeCount(feedId: Long): Long = jpaQueryFactory
        .select(feedLike.count()).from(feedLike).where(feedLike.feed.id.eq(feedId)).fetchFirst()

    /**
     * @return 해당 피드의 좋아요 한 유저 목록, FeedLike.id ASC
     */
    fun findFeedLikeUsers(feedId: Long, loginUser: User, lastId: Long? = null): List<UserSimpleResponse> {
        val likedUsers = jpaQueryFactory
            .selectFrom(feedLike).distinct()
            .leftJoin(feedLike.user, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(feedLike.feed.id.eq(feedId), lastId?.let { feedLike.id.lt(it) })
            .limit(FEED_LIKE_PAGE_SIZE.toLong())
            .fetch()
            .map { it.user }

        val followedUserIds = qUserRepository.getIsFollowed(loginUser.id!!, likedUsers.map { it.id!! })

        return likedUsers.map { UserSimpleResponse(it, followedUserIds.contains(it.id)) }
    }

    /**
     * @return 댓글의 대댓글 개수, 좋아요 수, 좋아요 여부, 작성자 팔로우 여부 반환
     */
    private fun getCommentInfos(loginUserId: Long, commentIds: List<Long>, writerIds: List<Long>): MutableList<CommentInfo> {
        val childCount = jpaQueryFactory.select(comment.parent.id, comment.count()).from(comment)
            .groupBy(comment.parent.id).having(comment.parent.id.`in`(commentIds))
            .orderBy(comment.parent.id.desc())
            .fetch().associate { Pair(it[comment.parent.id], it[comment.count()]) }
            .let { result ->
                commentIds.map { result[it] ?: 0L }
            }

        val likeCount = jpaQueryFactory.select(commentLike.count()).from(comment)
            .leftJoin(commentLike).on(comment.id.eq(commentLike.comment.id))
            .groupBy(comment.id).having(comment.id.`in`(commentIds))
            .orderBy(comment.id.desc())
            .fetch()

        val likedComments = jpaQueryFactory.selectFrom(commentLike)
            .where(commentLike.user.id.eq(loginUserId), commentLike.comment.id.`in`(commentIds)).fetch()
            .map { it.comment.id!! }

        val followedUsers = qUserRepository.getIsFollowed(loginUserId, writerIds)

        val commentInfos = mutableListOf<CommentInfo>()
        var idx = 0
        commentIds.forEach { cId ->
            commentInfos.add(
                CommentInfo(childCount[idx], likeCount[idx], likedComments.contains(cId), followedUsers.contains(cId))
            )
            idx++
        }
        return commentInfos
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 피드의 루트 댓글 목록, Comment.id DESC
     */
    fun findRootCommentsByFeedId(feedId: Long, loginUser: User, lastId: Long? = null): List<CommentResponse> {
        val expr = comment.feed.id.eq(feedId).and(comment.parent.isNull)

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr, lastId?.let { comment.id.lt(it) })
            .orderBy(comment.id.desc())
            .limit(COMMENT_PAGE_SIZE.toLong())
            .fetch()

        val commentInfos = getCommentInfos(loginUser.id!!, comments.map { it.id!! }, comments.map { it.writer.id!! })

        return comments.zip(commentInfos) { comment, commentInfo ->
            CommentResponse(
                comment, commentInfo.isFollowed, commentInfo.childCount, commentInfo.likeCount, commentInfo.isLiked
            )
        }
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 피드의 특정 유저의 루트 댓글 목록, Comment.id DESC
     */
    fun findRootCommentsByFeedIdAndUserId(feedId: Long, userId: Long, loginUser: User, lastId: Long? = null): List<CommentResponse> {
        val expr = comment.feed.id.eq(feedId).and(comment.parent.isNull).and(comment.writer.id.eq(userId))

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr, lastId?.let { comment.id.lt(it) })
            .orderBy(comment.id.desc())
            .limit(COMMENT_PAGE_SIZE.toLong())
            .fetch()

        val commentInfos = getCommentInfos(loginUser.id!!, comments.map { it.id!! }, comments.map { it.writer.id!! })

        return comments.zip(commentInfos) { comment, commentInfo ->
            CommentResponse(
                comment, commentInfo.isFollowed, commentInfo.childCount, commentInfo.likeCount, commentInfo.isLiked
            )
        }
    }

    /**
     * JOIN FETCH: writer, writer.profileImage
     * @return 해당 댓글의 대댓글 목록, Comment.id DESC
     */
    fun findChildCommentsByParentCommentId(commentId: Long, loginUser: User, lastId: Long? = null): List<CommentResponse> {
        val expr = comment.parent.id.eq(commentId)

        val comments = jpaQueryFactory
            .selectFrom(comment).distinct()
            .leftJoin(comment.writer, user).fetchJoin()
            .leftJoin(user.profileImage).fetchJoin()
            .where(expr, lastId?.let { comment.id.lt(it) })
            .orderBy(comment.id.desc())
            .limit(COMMENT_PAGE_SIZE.toLong())
            .fetch()

        val commentInfos = getCommentInfos(loginUser.id!!, comments.map { it.id!! }, comments.map { it.writer.id!! })

        return comments.zip(commentInfos) { comment, commentInfo ->
            CommentResponse(
                comment, commentInfo.isFollowed, commentInfo.childCount, commentInfo.likeCount, commentInfo.isLiked
            )
        }
    }

    /**
     * @param expr QueryDSL where clause에 들어갈 표현식
     * @return 해당 expr 에 대한 댓글 수(Long)
     */
    fun getCommentCount(expr: Predicate): Long = jpaQueryFactory
        .select(comment.count()).from(comment).where(expr).fetchFirst()

    /**
     * @return 해당 댓글의 좋아요 수(Long)
     */
    fun getCommentLikeCount(commentId: Long): Long = jpaQueryFactory
        .select(commentLike.count()).from(commentLike).where(commentLike.comment.id.eq(commentId)).fetchFirst()
}
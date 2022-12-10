package diary.capstone.domain.feed

import diary.capstone.config.FEED_PAGE_SIZE
import diary.capstone.domain.feed.comment.Comment
import diary.capstone.domain.file.File
import diary.capstone.domain.user.User
import diary.capstone.util.BaseTimeEntity
import org.hibernate.annotations.BatchSize
import javax.persistence.*

// 공개 범위 Domains
const val SHOW_ALL = "all"
const val SHOW_FOLLOWERS = "follower"
const val SHOW_ME = "me"

@Entity
class Feed(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var writer: User,

    @Lob
    var content: String,

    @BatchSize(size = FEED_PAGE_SIZE)
    @OneToMany(mappedBy = "feedFile", cascade = [CascadeType.ALL], orphanRemoval = true)
    var files: MutableList<File> = mutableListOf(),

    @OneToMany(mappedBy = "feed", cascade = [CascadeType.ALL], orphanRemoval = true)
    var comments: MutableList<Comment> = mutableListOf(),

    @OneToMany(mappedBy = "feed", cascade = [CascadeType.ALL], orphanRemoval = true)
    var likes: MutableList<FeedLike> = mutableListOf(),

    // 피드 공개 범위
    var showScope: String

): BaseTimeEntity() {
    fun update(
        content: String? = null,
        showScope: String? = null
    ) {
        content?.let { this.content = content }
        showScope?.let { this.showScope = showScope }
    }
}

@Entity
class FeedLike(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_like_id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User, // 좋아요 누른 유저

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id")
    var feed: Feed, // 좋아요 한 피드
)
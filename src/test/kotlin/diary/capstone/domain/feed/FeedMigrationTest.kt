/*
package diary.capstone.domain.feed

import diary.capstone.config.FEED_PAGE_SIZE
import diary.capstone.domain.user.QUserRepository
import diary.capstone.domain.user.UserRepository
import org.assertj.core.api.Assertions
import org.bson.types.ObjectId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.transaction.Transactional

@SpringBootTest
@Transactional
class FeedMigrationTest {

    @Autowired
    lateinit var userRepository: UserRepository
    @Autowired
    lateinit var qUserRepository: QUserRepository
    @Autowired
    lateinit var feedRepository: FeedRepository
    @Autowired
    lateinit var feedRepositoryM: FeedRepositoryM
    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    @Test @DisplayName("피드/댓글 마이그레이션")
    fun feedMigration() {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        feedRepositoryM.deleteAll()
        feedRepository.findAll().let {
            it.forEach { feed ->
                feedRepositoryM.save(
                    FeedM(
                        id = ObjectId(Timestamp.valueOf(LocalDateTime.parse(feed.createTime, formatter))),
                        writer = feed.writer.id!!,
                        content = feed.content,
                        likedUsers = feed.likes.map { it.user.id!! }.toMutableList(),
                        comments = feed.comments.map { c1 ->
                            CommentDto(
                                c1.writer.id!!,
                                c1.content,
                                c1.layer,
                                c1.children.map { c2 -> CommentDto(
                                    c2.writer.id!!,
                                    c2.content,
                                    c2.layer,
                                    c2.children.map { c3 -> CommentDto(
                                        c3.writer.id!!,
                                        c3.content,
                                        c3.layer,
                                        c3.children.map { c4 -> CommentDto(
                                            c4.writer.id!!,
                                            c4.content,
                                            c4.layer,
                                            c4.children.map { c5 -> CommentDto(
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
                        images = feed.files.map { ImageDto(it.originalName, it.source, it.description) }.toMutableList(),
                        showScope = feed.showScope,
                        createTime = LocalDateTime.parse(feed.createTime, formatter),
                    )
                )
            }
        }
    }

    @Test @DisplayName("피드 생성")
    fun createFeed() {
        val saved = feedRepositoryM.save(
            FeedM(
                id = ObjectId(Date()),
                writer = 1L,
                content = "Test feed content",
                showScope = "all",
                createTime = LocalDateTime.now().withNano(0)
            )
        )
        Assertions.assertThat(feedRepositoryM.findAll()).contains(saved)
        feedRepositoryM.delete(saved)
    }

    @Test @DisplayName("피드 조회 - 유저 페이징")
    fun getFeedsByUser() {
        val pageable = PageRequest.of(0, 10)
        val user = userRepository.findById(1L).get()
        val query = Query().addCriteria(Criteria.where("writer").`is`(user.id!!))
            .with(Sort.by(Sort.Direction.DESC, "id"))
            .with(pageable)
        val feeds = mongoTemplate.find(query, FeedM::class.java)
        val writers = qUserRepository.getUsersAndIsFollowed(user.id!!, feeds.map { it.writer })
        println("${feeds.size}, ${writers.size}")
        feeds.forEach { feed ->
            println(FeedResponseM(feed, writers.find { it.id == feed.writer }!!, user.id!!))
        }
    }

    @Test @DisplayName("피드 조회 - 전체 공개 페이징")
    fun getAllFeeds() {
        val loginUser = userRepository.findById(1L)
        val pageable = PageRequest.of(0, FEED_PAGE_SIZE)
        val query = Query().addCriteria(Criteria.where("showScope").`is`(SHOW_ALL))
            .with(Sort.by(Sort.Direction.DESC, "id"))
            .with(pageable)
        mongoTemplate.find(query, FeedM::class.java).let { result ->
            println("${result.size} rows found")
            result.forEach { println("${it.id} ${it.writer} ${it.createTime} ${it.showScope}") }
        }
    }

    @Test @DisplayName("피드 조회 - 댓글 페이징")
    fun getFeedWithCommentPaged() {
        val pageable = PageRequest.of(0, 1)
        val criteria = Criteria.where("id").`is`("6357bb20a371d447d279abd2")
        val query = Query().addCriteria(criteria)

        mongoTemplate.findOne(query, FeedM::class.java)!!.let {
            println(it.id)
            it.comments.forEach { c -> println(c) }
        }
    }
}*/

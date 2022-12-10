package diary.capstone.domain.user

import diary.capstone.config.MESSAGE_MAX_LENGTH
import diary.capstone.config.NAME_MAX_LENGTH
import diary.capstone.domain.chat.ChatSessionUser
import diary.capstone.domain.feed.Feed
import diary.capstone.domain.user.feedline.FeedLine
import diary.capstone.domain.file.File
import diary.capstone.domain.notice.Notice
import diary.capstone.domain.occupation.Occupation
import diary.capstone.util.BaseTimeEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.persistence.*

@Entity
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    var id: Long? = null,

    var email: String,
    var password: String,

    @Column(length = NAME_MAX_LENGTH)
    var name: String,

    @Column(length = MESSAGE_MAX_LENGTH)
    var message: String = "",

    // 최근 접속 ip, 로그인 대기 상태
    var ip: String = "",
    var loginWait: Boolean = false,

    var lastLogin: String = "",
    var darkMode: Boolean = false,
    
    // 프로필 공개 여부
    var profileShow: Boolean = true,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupation_id")
    var occupation: Occupation? = null, // 직종
    var interests: String = "", // 관심 분야(직종 이름들): ,로 구분하여 3개까지

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_id")
    var profileImage: File? = null,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var feedLines: MutableList<FeedLine> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var following: MutableList<Follow> = mutableListOf(),

    @OneToMany(mappedBy = "target", cascade = [CascadeType.ALL], orphanRemoval = true)
    var follower: MutableList<Follow> = mutableListOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var chatSession: MutableList<ChatSessionUser> = mutableListOf(),

    @OneToMany(mappedBy = "receiver", cascade = [CascadeType.ALL], orphanRemoval = true)
    var notices: MutableList<Notice> = mutableListOf(),

): BaseTimeEntity() {
    fun update(
        password: String? = null,
        name: String? = null,
        message: String? = null,
        email: String? = null,
        ip: String? = null,
        loginWaiting: Boolean? = null,
        darkMode: Boolean? = null,
        profileShow: Boolean? = null,
        occupation: Occupation? = null,
        interests: String? = null,
        profileImage: File? = null
    ): User {
        password?.let { this.password = password }
        name?.let { this.name = name }
        message?.let { this.message = message }
        email?.let { this.email = email }
        ip?.let { this.ip = ip }
        loginWaiting?.let { this.loginWait = loginWaiting }
        darkMode?.let { this.darkMode = darkMode }
        profileShow?.let { this.profileShow = profileShow }
        occupation?.let { this.occupation = occupation }
        interests?.let { this.interests = interests }
        profileImage?.let { this.profileImage = profileImage }
        return this
    }

    /**
     * interests 프로퍼티는 String 타입으로 여러 직종을 ,로 구분하여 저장.
     * 해당 프로퍼티를 조회할 땐 아래 메소드를 사용하여 파싱 후 조회해야함
     */
    fun getInterests(): List<String> {
        return if (this.interests == "") listOf() else this.interests.split(",")
    }

    fun addNotice(notice: Notice) { this.notices.add(notice) }

    fun setLastLogin(): User {
        this.lastLogin = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return this
    }
}

@Entity
class Follow(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "follow_id")
    var id: Long? = null,

    // 팔로우 한 유저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User,

    // 팔로우 대상 유저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    var target: User,
)
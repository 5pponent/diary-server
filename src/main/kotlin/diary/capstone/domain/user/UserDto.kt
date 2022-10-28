package diary.capstone.domain.user

import diary.capstone.config.MESSAGE_MAX_LENGTH
import diary.capstone.config.NAME_MAX_LENGTH
import diary.capstone.config.PASSWORD_CREATE_POLICY
import diary.capstone.domain.feed.feedline.FeedLine
import diary.capstone.domain.file.ProfileImageFileResponse
import org.hibernate.validator.constraints.Length
import org.intellij.lang.annotations.RegExp
import org.springframework.data.domain.Page
import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern

data class LoginForm(
    @field:NotBlank
    @field:Email
    var email: String,

    @field:NotBlank
    var password: String
)

data class MailAuthLoginForm(
    @field:NotBlank
    @field:Email
    var email: String,

    @field:NotBlank
    var password: String,

    @field:NotBlank
    var code: String
)

// 해당 메일에 대한 인증 요청
data class AuthMailForm(@field:NotBlank @field:Email var email: String)

// 해당 메일에 대한 인증 코드와 함께 요청
data class AuthCodeForm(@field:NotBlank @field:Email var email: String, @field:NotBlank var code: String)

// 로그인 성공 시 토큰 응답
data class TokenResponse(var token: String)

data class JoinForm(
    @field:NotBlank
    @field:Email
    var email: String,

    @field:NotBlank
    @field:Pattern(regexp = PASSWORD_CREATE_POLICY)
    var password: String,

    @field:NotBlank
    var passwordCheck: String,

    @field:NotBlank
    var name: String
) {
    fun checkPassword(): Boolean = this.password == this.passwordCheck
}

data class PasswordUpdateForm(
    @field:NotBlank
    var currentPassword: String,

    @field:NotBlank
    @field:Pattern(regexp = PASSWORD_CREATE_POLICY)
    var newPassword: String,

    @field:NotBlank
    var newPasswordCheck: String
) {
    fun checkPassword(): Boolean = this.newPassword == this.newPasswordCheck
}

data class UserNameUpdateForm(
    @field:NotBlank
    @field:Length(min = 1, max = NAME_MAX_LENGTH)
    var name: String
)

data class UserMessageUpdateForm(
    @field:Length(min = 0, max = MESSAGE_MAX_LENGTH)
    var message: String = ""
)

data class UserOccupationUpdateForm(var occupation: String?)

data class UserInterestsUpdateForm(var interests: List<String>)

data class UserDeleteForm(var password: String)

data class UserSimpleResponse(
    var id: Long,
    var name: String,
    var image: ProfileImageFileResponse?,
    var isFollowed: Boolean
) {
    constructor(user: User, me: User): this(
        id = user.id!!,
        name = user.name,
        image = user.profileImage?.let { ProfileImageFileResponse(it) },
        isFollowed = me.following
            .any { it.target.id == user.id }
    )
}

data class FeedLineResponse(var id: Long, var title: String) {
    constructor(feedLine: FeedLine): this(
        id = feedLine.id!!,
        title = feedLine.title
    )
}

data class UserDetailResponse(
    var id: Long,
    var image: ProfileImageFileResponse?,
    var name: String,
    var message: String,
    var email: String,
    var occupation: String?,
    var interests: List<String>,
    var followingCount: Int,
    var followerCount: Int,
    var isFollowed: Boolean
) {
    constructor(user: User, me: User): this(
        id = user.id!!,
        image = user.profileImage?.let { ProfileImageFileResponse(it) },
        name = user.name,
        message = user.message,
        email = user.email,
        occupation = user.occupation?.name,
        interests = user.getInterests(),
        followingCount = user.following.count(),
        followerCount = user.follower.count(),
        isFollowed = me.following
            .any { it.target.id == user.id }
    )
}

data class UserPagedResponse(
    var currentPage: Int,
    var totalPages: Int,
    var totalElements: Long,
    var users: List<UserSimpleResponse>
) {
    constructor(users: Page<User>, me: User): this(
        currentPage = users.number + 1,
        totalPages = users.totalPages,
        totalElements = users.totalElements,
        users = users.content
            .map { UserSimpleResponse(it, me) }
            .toList()
    )
}
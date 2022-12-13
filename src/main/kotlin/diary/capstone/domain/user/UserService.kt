package diary.capstone.domain.user

import diary.capstone.auth.AuthManager
import diary.capstone.auth.JwtProvider
import diary.capstone.config.INTERESTS_LIMIT

import diary.capstone.domain.file.FileService
import diary.capstone.domain.mail.MailService
import diary.capstone.domain.occupation.INTERESTS_EXCEEDED
import diary.capstone.domain.occupation.OCCUPATION_NOT_FOUND
import diary.capstone.domain.occupation.OccupationException
import diary.capstone.domain.occupation.OccupationService
import diary.capstone.util.getIp
import diary.capstone.util.getPagedObject
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val authManager: AuthManager,
    private val mailService: MailService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider
) {

    // 로그인, IP 다르면 메일 인증 로그인으로 해야 함
    @Transactional
    fun login(form: LoginForm, request: HttpServletRequest): String {
        // 이메일/비밀번호 검증
        val user = userRepository.findByEmail(form.email) ?: throw AuthException(LOGIN_FAILED)
        if (!passwordEncoder.matches(form.password, user.password)) throw AuthException(LOGIN_FAILED)

        /**
         * 다른 ip로 접속 시 or 로그인 하려는 유저가 로그인 대기 상태일 시
         * 인증 코드 생성 -> 인증 메일 발송 -> 인증 예외 발생
         */
        if (!passwordEncoder.matches(request.getIp(), user.ip) || user.loginWait) {
            user.update(loginWaiting = true)
            mailService.sendLoginAuthMail(authManager.generateCode(user.id!!.toString()), user.email)
            throw AuthException(MAIL_AUTH_REQUIRED)
        }

        return jwtProvider.createToken(user.setLastLogin().email)
    }

    // 메일 인증 로그인
    @Transactional
    fun mailAuthenticationLogin(form: MailAuthLoginForm, request: HttpServletRequest): String {
        val user = userRepository.findByEmail(form.email) ?: throw AuthException(LOGIN_FAILED)
        if (!passwordEncoder.matches(form.password, user.password)) throw AuthException(LOGIN_FAILED)

        // 인증 코드 일치 확인, 불일치 시 예외 발생
        if (form.code == authManager.getAuthCode(user.id!!.toString())) {
            // 최근 접속 IP를 현재 접속 IP로 수정, 로그인 대기 상태 해제 후 로그인
            authManager.removeUsedAuthCode(user.id!!.toString())
            user.update(ip = passwordEncoder.encode(request.getIp()), loginWaiting = false)
                .setLastLogin()
            return jwtProvider.createToken(form.email)
        }
        else throw AuthException(AUTH_CODE_MISMATCH)
    }
    
    // 이메일 인증 메일 발송
    fun sendEmailAuthMail(form: AuthMailForm) {
        if (userRepository.existsByEmail(form.email)) throw UserException(DUPLICATE_EMAIL)
        mailService.sendEmailAuthMail(authManager.generateCode(form.email), form.email)
    }

    // 이메일 인증 메일 확인
    fun checkEmailAuthMail(form: AuthCodeForm) {
        if (form.code == authManager.getAuthCode(form.email)) {
            // 해당 메일을 인증된 메일에 추가
            authManager.removeUsedAuthCode(form.email)
            authManager.addAuthenticatedEmail(form.email)
        }
        else throw AuthException(AUTH_CODE_MISMATCH)
    }

    // 회원 가입 (인증받은 이메일로 요청해야 함)
    @Transactional
    fun join(form: JoinForm, request: HttpServletRequest): String {
        if (!form.checkPassword()) throw UserException(PASSWORD_MISMATCH)
        if (userRepository.existsByEmail(form.email)) throw UserException(DUPLICATE_EMAIL)
        if (!authManager.emails.contains(form.email)) throw UserException(MAIL_AUTH_REQUIRED)
        authManager.emails.remove(form.email)

        userRepository.save(
            User(
                email = form.email,
                password = passwordEncoder.encode(form.password),
                name = form.name,
                ip = passwordEncoder.encode(request.getIp())
            ).setLastLogin()
        ).let {
            return jwtProvider.createToken(it.email)
        }
    }
}

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val occupationService: OccupationService,
    private val fileService: FileService,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional(readOnly = true)
    fun getUserById(userId: Long): User =
        userRepository.findById(userId).orElseThrow { throw UserException(USER_NOT_FOUND) }

    // 이름, 이메일로 유저 검색
    @Transactional(readOnly = true)
    fun searchUser(pageable: Pageable, keyword: String): Page<User> =
        keyword.let {
            if (it.isNotBlank()) userRepository.searchAllByNameOrEmail(pageable, it, it)
            else throw UserException(USER_NOT_FOUND)
        }

    // 내가 팔로우 중인 유저 목록
    @Transactional(readOnly = true)
    fun getFollowing(pageable: Pageable, userId: Long): Page<User> =
        getPagedObject(pageable,
            getUserById(userId).following
                .map { it.target }
                .sortedBy { it.name }
        )

    // 나를 팔로우 중인 유저 목록
    @Transactional(readOnly = true)
    fun getFollowers(pageable: Pageable, userId: Long): Page<User> =
        getPagedObject(pageable,
            getUserById(userId).follower
                .map { it.user }
                .sortedBy { it.name }
        )

    // 해당 유저 팔로우
    fun followUser(userId: Long, loginUser: User) {
        // 자기 자신은 팔로우 불가능
        if (loginUser.id == userId) throw UserException(FOLLOW_TARGET_INVALID)
        
        getUserById(userId).let { targetUser ->
            // 이미 팔로우 한 대상은 팔로우 불가능
            if (loginUser.following.none { it.target.id == targetUser.id })
                loginUser.following.add(Follow(user = loginUser, target = targetUser))
            else throw UserException(ALREADY_FOLLOWED)
        }
    }

    // 해당 유저 팔로우 취소
    fun unfollowUser(userId: Long, loginUser: User) =
        loginUser.following.remove(
            loginUser.following
                .find { it.user.id == loginUser.id && it.target.id == getUserById(userId).id }
        )

    // 내 이름 수정
    fun updateUserName(form: UserNameUpdateForm, loginUser: User): User =
        loginUser.update(name = form.name)

    // 오늘의 한 마디 수정
    fun updateUserMessage(form: UserMessageUpdateForm, loginUser: User): User =
        loginUser.update(message = form.message)

    // 내 직종 수정
    fun updateUserOccupation(form: UserOccupationUpdateForm, loginUser: User): User =
        form.occupation?.let {
            loginUser.update(occupation = occupationService.getOccupation(it))
        } ?: run { loginUser.update(occupation = null) }

    // 내 관심 분야 수정
    fun updateUserInterests(form: UserInterestsUpdateForm, loginUser: User): User {
        if (form.interests.size > INTERESTS_LIMIT) throw OccupationException(INTERESTS_EXCEEDED)
        // 해당 직종이 존재하는지 확인
        form.interests.forEach {
            if (!occupationService.isExists(it)) throw OccupationException("[$it] $OCCUPATION_NOT_FOUND")
        }
        return loginUser.update(
            interests = form.interests.joinToString(",")
        )
    }

    // 프로필 사진 수정
    fun updateProfileImage(image: MultipartFile, loginUser: User): User {
        // 등록된 프로필 사진이 있다면 원래 사진 삭제 후 새 사진 저장
        loginUser.profileImage?.let { fileService.deleteFile(it) }
        return loginUser.update(profileImage = fileService.uploadFile(image))
    }

    // 기본 프로필 사진으로 변경
    fun deleteProfileImage(loginUser: User): User {
        loginUser.profileImage?.let { fileService.deleteFile(it) }
        return loginUser.update(profileImage = null)
    }

    // 프로필 공개 여부 변경
    fun updateUserProfileShow(isShown: Boolean, loginUser: User): User =
        loginUser.update(profileShow = isShown)

    // 비밀번호 수정
    fun updatePassword(form: PasswordUpdateForm, loginUser: User) {
        if (!form.checkPassword()) throw UserException(NEW_PASSWORD_MISMATCH)
        if (passwordEncoder.matches(form.currentPassword, loginUser.password))
            loginUser.update(password = passwordEncoder.encode(form.newPassword))
        else throw UserException(CURRENT_PASSWORD_MISMATCH)
    }

    // 회원 탈퇴(비밀번호와 함께 요청)
    // TODO 추후에 아예 삭제할지, 유예 기간을 설정할지 생각해봐야됨
    fun deleteUser(form: UserDeleteForm, loginUser: User) {
        loginUser.profileImage?.let { fileService.deleteFile(it) }
        // TODO 피드, 댓글, 피드좋아요, 댓글좋아요 삭제 로직
        userRepository.delete(loginUser)
    }
}
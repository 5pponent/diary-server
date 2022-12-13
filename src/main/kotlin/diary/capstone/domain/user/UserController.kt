package diary.capstone.domain.user

import diary.capstone.auth.Auth
import diary.capstone.config.INTERESTS_LIMIT
import diary.capstone.config.PASSWORD_CREATE_POLICY
import io.swagger.annotations.ApiOperation
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import springfox.documentation.annotations.ApiIgnore
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

@ApiOperation("인증 관련 API")
@RestController
@RequestMapping("/auth")
class LoginController(private val loginService: LoginService) {

    @ApiOperation(
        value = "로그인",
        notes = "이메일, 비밀번호로 인증, 성공 시 액세스 토큰 발급\n " +
                "로그인하려는 유저의 IP가 이전 접근 IP와 다를 경우 메일 인증 코드 발송,\n " +
                "이후 /mail-auth-login 을 통한 로그인 필요\n " +
                "메일 인증 코드 재발송 필요 시 해당 API 재요청"
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody form: LoginForm, request: HttpServletRequest) =
        TokenResponse(loginService.login(form, request))

    @ApiOperation(value = "메일 인증하여 로그인", notes = "기존 로그인 폼에 메일 인증 코드를 포함하여 로그인")
    @PostMapping("/mail-auth-login")
    fun mailAuthenticationLogin(@Valid @RequestBody form: MailAuthLoginForm, request: HttpServletRequest) =
        TokenResponse(loginService.mailAuthenticationLogin(form, request))
    
    @ApiOperation(
        value = "회원가입 전 메일 검증",
        notes = "회원가입 시 사용하려는 메일이 유효한지 확인하기 위해 해당 이메일로 인증 코드 발송\n " +
                "이후 /mail-auth-check 를 사용하여 해당 이메일을 인증 \n " +
                "해당 요청 시 이메일은 데이터베이스에 등록되어있지 않아야 함."
    )
    @PostMapping("/mail-auth")
    fun authenticationEmail(@Valid @RequestBody form: AuthMailForm) =
        loginService.sendEmailAuthMail(form)

    @ApiOperation(
        value = "인증 코드를 통해 이메일 검증",
        notes = "발송된 인증 코드와 인증받을 이메일을 요청하여 해당 이메일을 사용 가능한 상태로 저장\n " +
                "/join 을 통한 회원 가입 시, 해당 요청을 통해 검증된 이메일만 입력 가능"
    )
    @PostMapping("/mail-auth-check")
    fun checkEmailAuthMail(@Valid @RequestBody form: AuthCodeForm) =
        loginService.checkEmailAuthMail(form)

    @ApiOperation(
        value = "회원가입",
        notes = "/mail-auth-check 를 통해 검증된 이메일을 포함한 회원가입 폼으로 회원가입 시도\n " +
                "password 와 passwordCheck 는 일치해야 한다.\n" +
                "비밀번호 정규식: $PASSWORD_CREATE_POLICY"
    )
    @PostMapping("/join")
    fun join(@Valid @RequestBody form: JoinForm, request: HttpServletRequest) =
        TokenResponse(loginService.join(form, request))
}

@ApiOperation("유저 관련 API")
@Auth
@RestController
@RequestMapping("/user")
class UserController(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder
) {
    @ApiOperation(value = "유저 정보 조회/유저 검색")
    @GetMapping
    fun getMyInfo(
        @PageableDefault(size = 15) pageable: Pageable,
        @RequestParam("keyword", required = false) keyword: String?,
        @ApiIgnore user: User
    ) = keyword?.let {
        UserPagedResponse(userService.searchUser(pageable, it), user)
    } ?: UserDetailResponse(user, user)

    @ApiOperation(value = "특정 유저의 유저 정보 조회")
    @GetMapping("/{userId}")
    fun getUser(@PathVariable("userId") userId: Long, @ApiIgnore user: User) =
        UserDetailResponse(userService.getUserById(userId), user)

    @ApiOperation(value = "해당 유저가 팔로우한 유저 목록 조회")
    @GetMapping("/{userId}/following")
    fun getFollowing(
        @PageableDefault pageable: Pageable,
        @PathVariable("userId") userId: Long,
        @ApiIgnore user: User
    ) = UserPagedResponse(userService.getFollowing(pageable, userId), user)

    @ApiOperation(value = "해당 유저의 팔로워 목록 조회")
    @GetMapping("/{userId}/follower")
    fun getFollowers(
        @PageableDefault pageable: Pageable,
        @PathVariable("userId") userId: Long,
        @ApiIgnore user: User
    ) = UserPagedResponse(userService.getFollowers(pageable, userId), user)

    @ApiOperation(value = "해당 유저 팔로우")
    @PostMapping("/{userId}/follow")
    fun followUser(@PathVariable("userId") userId: Long, @ApiIgnore user: User) =
        userService.followUser(userId, user)

    @ApiOperation(value = "해당 유저 팔로우 취소")
    @DeleteMapping("/{userId}/follow")
    fun unfollowUser(@PathVariable("userId") userId: Long, @ApiIgnore user: User) =
        userService.unfollowUser(userId, user)

    @ApiOperation(value = "내 이름 수정")
    @PutMapping("/name")
    fun updateUserName(@Valid @RequestBody form: UserNameUpdateForm, @ApiIgnore user: User) =
        UserDetailResponse(userService.updateUserName(form, user), user)

    @ApiOperation(value = "오늘의 한 마디 수정")
    @PutMapping("/message")
    fun updateUserMessage(@Valid @RequestBody form: UserMessageUpdateForm, @ApiIgnore user: User) =
        UserDetailResponse(userService.updateUserMessage(form, user), user)

    @ApiOperation(value = "내 직종 수정")
    @PutMapping("/occupation")
    fun updateUserOccupation(@Valid @RequestBody form: UserOccupationUpdateForm, @ApiIgnore user: User) =
        UserDetailResponse(userService.updateUserOccupation(form, user), user)
    
    @ApiOperation(value = "내 관심 분야 수정", notes = "관심 분야는 최대 ${INTERESTS_LIMIT}개 까지 수정 가능")
    @PutMapping("/interests")
    fun updateUserInterests(@RequestBody form: UserInterestsUpdateForm, @ApiIgnore user: User) =
        UserDetailResponse(userService.updateUserInterests(form, user), user)

    @ApiOperation(value = "내 프로필 사진 수정")
    @PutMapping("/profile-image")
    fun updateUserProfileImage(
        @RequestPart("image") image: MultipartFile,
        @ApiIgnore user: User
    ) = UserDetailResponse(userService.updateProfileImage(image, user), user)

    @ApiOperation(value = "프로필 사진 삭제(기본 사진으로 변경)")
    @DeleteMapping("/profile-image")
    fun deleteUserProfileImage(@ApiIgnore user: User) =
        UserDetailResponse(userService.deleteProfileImage(user), user)

    @ApiOperation(value = "프로필 공개 여부 설정")
    @PutMapping("/profile-show")
    fun updateUserProfileShow(@RequestBody isShown: Boolean, @ApiIgnore user: User) =
        UserDetailResponse(userService.updateUserProfileShow(isShown, user), user)

    @ApiOperation(value = "내 비밀번호 변경", notes = "비밀번호 정규식: $PASSWORD_CREATE_POLICY")
    @PutMapping("/password")
    fun updateUserPassword(@Valid @RequestBody form: PasswordUpdateForm, @ApiIgnore user: User) =
        userService.updatePassword(form, user)

    @ApiOperation(value = "회원 탈퇴", notes = "!! 현재 회원 탈퇴시 데이터베이스에서 바로 해당 유저를 삭제")
    @DeleteMapping
    fun deleteUser(@RequestBody form: UserDeleteForm, @ApiIgnore user: User, request: HttpServletRequest) {
        if (passwordEncoder.matches(form.password, user.password))
            userService.deleteUser(form, user)
        else throw UserException(PASSWORD_MISMATCH)
    }
}
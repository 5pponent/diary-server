package diary.capstone.domain.occupation

import javax.validation.constraints.NotBlank

data class OccupationRequestForm(
    @field:NotBlank
    var name: String
)